package com.patchouli.deepseekwidget.logic

import com.patchouli.deepseekwidget.data.Snapshot
import org.junit.Assert.assertEquals
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDate
import java.time.ZoneId

class SpendCalculatorTest {

    private val zone: ZoneId = ZoneId.of("Europe/Rome")
    private val day: LocalDate = LocalDate.of(2026, 10, 21)

    private fun snapshot(hour: Int, total: String): Snapshot {
        val ts = day.atTime(hour, 0).atZone(zone).toInstant().toEpochMilli()
        return Snapshot(
            id = 0,
            ts = ts,
            total = BigDecimal(total),
            granted = BigDecimal.ZERO,
            topped = BigDecimal(total)
        )
    }

    private fun snapshotOnDate(date: LocalDate, hour: Int, total: String): Snapshot {
        val ts = date.atTime(hour, 0).atZone(zone).toInstant().toEpochMilli()
        return Snapshot(0, ts, BigDecimal(total), BigDecimal.ZERO, BigDecimal(total))
    }

    private fun assertMoney(expected: String, actual: BigDecimal) {
        assertEquals(0, BigDecimal(expected).compareTo(actual))
    }

    @Test
    fun `somma i delta positivi dello stesso giorno`() {
        val snapshots = listOf(
            snapshot(8, "100.00"),
            snapshot(12, "90.00"),
            snapshot(18, "85.00")
        )
        assertMoney("15.00", SpendCalculator.spentOnDay(snapshots, day, zone))
    }

    @Test
    fun `le ricariche non contano come spesa`() {
        val snapshots = listOf(
            snapshot(8, "100.00"),
            snapshot(10, "90.00"),   // speso 10
            snapshot(12, "120.00"),  // ricarica: delta negativo, clamp a 0
            snapshot(15, "110.00")   // speso 10
        )
        assertMoney("20.00", SpendCalculator.spentOnDay(snapshots, day, zone))
    }

    @Test
    fun `con un solo snapshot la spesa e zero`() {
        val snapshots = listOf(snapshot(9, "42.00"))
        assertMoney("0.00", SpendCalculator.spentOnDay(snapshots, day, zone))
    }

    @Test
    fun `senza snapshot la spesa e zero`() {
        assertMoney("0.00", SpendCalculator.spentOnDay(emptyList(), day, zone))
    }

    @Test
    fun `non mescola i delta tra giorni diversi`() {
        val yesterday = day.minusDays(1)
        val snapshots = listOf(
            snapshotOnDate(yesterday, 23, "100.00"),
            snapshotOnDate(day, 8, "80.00"),
            snapshotOnDate(day, 12, "75.00")
        )
        assertMoney("5.00", SpendCalculator.spentOnDay(snapshots, day, zone))
    }

    @Test
    fun `spentToday usa il giorno locale corrente`() {
        val yesterday = day.minusDays(1)
        val snapshots = listOf(
            snapshotOnDate(yesterday, 20, "100.00"),
            snapshotOnDate(day, 8, "90.00")
        )
        val now = day.atTime(12, 0).atZone(zone).toInstant().toEpochMilli()
        assertMoney("0.00", SpendCalculator.spentToday(snapshots, now, zone))
    }

    @Test
    fun `spendByDay include i giorni senza attivita`() {
        val snapshots = listOf(
            snapshotOnDate(day.minusDays(2), 8, "100.00"),
            snapshotOnDate(day.minusDays(2), 12, "95.00")
        )
        val days = listOf(day.minusDays(2), day.minusDays(1), day)
        val spend = SpendCalculator.spendByDay(snapshots, days, zone)
        assertMoney("5.00", spend.getValue(day.minusDays(2)))
        assertMoney("0.00", spend.getValue(day.minusDays(1)))
        assertMoney("0.00", spend.getValue(day))
    }
}
