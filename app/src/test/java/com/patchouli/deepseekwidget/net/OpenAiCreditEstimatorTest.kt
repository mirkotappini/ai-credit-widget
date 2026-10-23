package com.patchouli.deepseekwidget.net

import com.patchouli.deepseekwidget.repo.OpenAiCreditEstimator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal
import java.time.Instant

class OpenAiCreditEstimatorTest {

    private val anchorTs = Instant.parse("2026-10-22T15:30:00Z").toEpochMilli()
    private val midnight = Instant.parse("2026-10-22T00:00:00Z").epochSecond
    private val nextMidnight = Instant.parse("2026-10-23T00:00:00Z").epochSecond

    private fun assertAmount(expected: String, actual: BigDecimal) {
        assertEquals(0, BigDecimal(expected).compareTo(actual))
    }

    private fun bucket(startSec: Long, amount: String): CostBucket =
        CostBucket(
            startTsUtc = startSec,
            endTsUtc = startSec + 86_400L,
            amount = BigDecimal(amount),
            currency = "usd"
        )

    @Test
    fun `il giorno dell ancora non viene scalato`() {
        val buckets = listOf(
            bucket(midnight - 86_400L, "5.00"),
            bucket(midnight, "1.25")
        )

        val estimate = OpenAiCreditEstimator.estimateUsd(
            anchorUsd = BigDecimal("20.00"),
            anchorTs = anchorTs,
            buckets = buckets
        )

        assertAmount("20.00", estimate)
    }

    @Test
    fun `scala i bucket dal giorno successivo in poi`() {
        val buckets = listOf(
            bucket(midnight, "1.25"),
            bucket(nextMidnight, "2.50"),
            bucket(nextMidnight + 86_400L, "1.00")
        )

        val estimate = OpenAiCreditEstimator.estimateUsd(
            anchorUsd = BigDecimal("20.00"),
            anchorTs = anchorTs,
            buckets = buckets
        )

        assertAmount("16.50", estimate)
    }

    @Test
    fun `ancora a cavallo di mezzanotte utc`() {
        val lateAnchor = Instant.parse("2026-10-22T23:59:59Z").toEpochMilli()
        val buckets = listOf(
            bucket(midnight, "1.25"),
            bucket(nextMidnight, "2.50")
        )

        val estimate = OpenAiCreditEstimator.estimateUsd(
            anchorUsd = BigDecimal("20.00"),
            anchorTs = lateAnchor,
            buckets = buckets
        )

        assertAmount("17.50", estimate)
    }

    @Test
    fun `senza costi la stima coincide con l ancora`() {
        val estimate = OpenAiCreditEstimator.estimateUsd(
            anchorUsd = BigDecimal("20.00"),
            anchorTs = anchorTs,
            buckets = emptyList()
        )

        assertAmount("20.00", estimate)
    }

    @Test
    fun `la stima negativa non viene clampata`() {
        val estimate = OpenAiCreditEstimator.estimateUsd(
            anchorUsd = BigDecimal("1.00"),
            anchorTs = anchorTs,
            buckets = listOf(bucket(nextMidnight, "2.50"))
        )

        assertAmount("-1.50", estimate)
    }

    @Test
    fun `mezzanotte utc ignora l ora dell ancora`() {
        assertEquals(midnight, OpenAiCreditEstimator.utcMidnightSec(anchorTs))
        assertEquals(nextMidnight, OpenAiCreditEstimator.utcNextMidnightSec(anchorTs))
        assertEquals(
            Instant.parse("2026-10-23T00:00:00Z").toEpochMilli() / 1000L,
            OpenAiCreditEstimator.utcNextMidnightSec(
                Instant.parse("2026-10-22T00:00:00Z").toEpochMilli()
            )
        )
    }

    @Test
    fun `ancora valida solo tra oggi e trenta giorni utc fa`() {
        val now = Instant.parse("2026-10-22T15:30:00Z").toEpochMilli()

        assertTrue(OpenAiCreditEstimator.isAnchorUsable(now, now))
        assertTrue(
            OpenAiCreditEstimator.isAnchorUsable(
                Instant.parse("2026-10-22T00:00:00Z").toEpochMilli(),
                now
            )
        )
        // Stesso giorno UTC di 30 giorni fa: ammesso anche se sono passate
        // piu' di 720 ore.
        assertTrue(
            OpenAiCreditEstimator.isAnchorUsable(
                Instant.parse("2026-09-22T15:29:59Z").toEpochMilli(),
                now
            )
        )
        assertFalse(
            OpenAiCreditEstimator.isAnchorUsable(
                Instant.parse("2026-09-21T23:59:59Z").toEpochMilli(),
                now
            )
        )
        assertFalse(OpenAiCreditEstimator.isAnchorUsable(now + 1, now))
        assertFalse(OpenAiCreditEstimator.isAnchorUsable(0L, now))
    }
}
