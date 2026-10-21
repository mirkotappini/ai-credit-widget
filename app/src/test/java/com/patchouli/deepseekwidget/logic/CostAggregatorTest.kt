package com.patchouli.deepseekwidget.logic

import com.patchouli.deepseekwidget.net.CostBucket
import org.junit.Assert.assertEquals
import org.junit.Test
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset

class CostAggregatorTest {

    private fun bucket(
        startIso: String,
        endIso: String,
        amount: String
    ): CostBucket = CostBucket(
        startTsUtc = Instant.parse(startIso).epochSecond,
        endTsUtc = Instant.parse(endIso).epochSecond,
        amount = BigDecimal(amount),
        currency = "USD"
    )

    @Test
    fun `bucket orario a cavallo della mezzanotte locale conta solo la quota`() {
        // Asia/Kolkata e' UTC+5:30: la mezzanotte locale cade alle 18:30 UTC,
        // quindi il bucket 18:00-19:00 UTC e' per meta' nel giorno precedente.
        val zone = ZoneId.of("Asia/Kolkata")
        val now = Instant.parse("2024-01-15T12:00:00Z").toEpochMilli()
        val buckets = listOf(
            bucket("2024-01-14T17:00:00Z", "2024-01-14T18:00:00Z", "9.99"),
            bucket("2024-01-14T18:00:00Z", "2024-01-14T19:00:00Z", "2.00"),
            bucket("2024-01-14T19:00:00Z", "2024-01-14T20:00:00Z", "3.00")
        )

        val spent = CostAggregator.spentTodayLocal(buckets, now, zone)

        // 2.00 * 30/60 + 3.00 = 4.00; il bucket delle 17:00 e' fuori dal giorno.
        assertEquals(BigDecimal("4.00"), spent)
    }

    @Test
    fun `giorno senza bucket vale zero`() {
        val zone = ZoneOffset.UTC
        val now = Instant.parse("2024-01-15T12:00:00Z").toEpochMilli()
        val buckets = listOf(
            bucket("2024-01-09T12:00:00Z", "2024-01-09T13:00:00Z", "1.00")
        )

        val byDay = CostAggregator.spentLast7DaysLocal(buckets, now, zone)

        assertEquals(0, BigDecimal.ZERO.compareTo(byDay[LocalDate.of(2024, 1, 10)]))
        assertEquals(0, BigDecimal.ZERO.compareTo(byDay[LocalDate.of(2024, 1, 15)]))
    }

    @Test
    fun `somma i sette giorni locali incluso oggi`() {
        val zone = ZoneOffset.UTC
        val now = Instant.parse("2024-01-15T12:00:00Z").toEpochMilli()
        val buckets = listOf(
            bucket("2024-01-08T12:00:00Z", "2024-01-08T13:00:00Z", "99.00"),
            bucket("2024-01-09T12:00:00Z", "2024-01-09T13:00:00Z", "1.00"),
            bucket("2024-01-15T12:00:00Z", "2024-01-15T13:00:00Z", "2.50")
        )

        val byDay = CostAggregator.spentLast7DaysLocal(buckets, now, zone)

        assertEquals(
            listOf(
                LocalDate.of(2024, 1, 9),
                LocalDate.of(2024, 1, 10),
                LocalDate.of(2024, 1, 11),
                LocalDate.of(2024, 1, 12),
                LocalDate.of(2024, 1, 13),
                LocalDate.of(2024, 1, 14),
                LocalDate.of(2024, 1, 15)
            ),
            byDay.keys.toList()
        )
        assertEquals(BigDecimal("1.00"), byDay[LocalDate.of(2024, 1, 9)])
        assertEquals(BigDecimal("2.50"), byDay[LocalDate.of(2024, 1, 15)])
        assertEquals(
            BigDecimal("3.50"),
            byDay.values.reduce { acc, value -> acc.add(value) }
        )
    }

    @Test
    fun `mese corrente UTC somma solo i bucket giornalieri del mese`() {
        val now = Instant.parse("2024-01-15T12:00:00Z").toEpochMilli()
        val buckets = listOf(
            bucket("2023-12-31T00:00:00Z", "2024-01-01T00:00:00Z", "9.99"),
            bucket("2024-01-01T00:00:00Z", "2024-01-02T00:00:00Z", "1.10"),
            bucket("2024-01-02T00:00:00Z", "2024-01-03T00:00:00Z", "2.20"),
            bucket("2024-02-01T00:00:00Z", "2024-02-02T00:00:00Z", "8.88")
        )

        val spent = CostAggregator.spentCurrentMonthUtc(buckets, now)

        assertEquals(BigDecimal("3.30"), spent)
    }
}
