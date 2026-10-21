package com.patchouli.deepseekwidget.logic

import com.patchouli.deepseekwidget.data.Snapshot
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Calcolo locale dello speso. Non inventa dati:
 * - considera solo snapshot dello stesso giorno locale;
 * - somma i delta positivi (saldo che scende) tra snapshot consecutivi;
 * - delta negativi (ricariche) valgono 0;
 * - con un solo snapshot del giorno la spesa e' 0.
 */
object SpendCalculator {

    fun spentOnDay(
        snapshots: List<Snapshot>,
        day: LocalDate,
        zone: ZoneId = ZoneId.systemDefault()
    ): BigDecimal {
        val daySnapshots = snapshots
            .filter { it.dayIn(zone) == day }
            .sortedWith(compareBy<Snapshot> { it.ts }.thenBy { it.id })
        if (daySnapshots.size < 2) return BigDecimal.ZERO

        var spent = BigDecimal.ZERO
        for (i in 1 until daySnapshots.size) {
            val previous = daySnapshots[i - 1].total
            val current = daySnapshots[i].total
            val delta = previous.subtract(current)
            if (delta.signum() > 0) spent = spent.add(delta)
        }
        return spent
    }

    fun spentToday(
        snapshots: List<Snapshot>,
        nowMillis: Long,
        zone: ZoneId = ZoneId.systemDefault()
    ): BigDecimal {
        val today = Instant.ofEpochMilli(nowMillis).atZone(zone).toLocalDate()
        return spentOnDay(snapshots, today, zone)
    }

    /** Spesa per ogni giorno richiesto (anche giorni senza snapshot => 0). */
    fun spendByDay(
        snapshots: List<Snapshot>,
        days: List<LocalDate>,
        zone: ZoneId = ZoneId.systemDefault()
    ): Map<LocalDate, BigDecimal> {
        val byDay = snapshots.groupBy { it.dayIn(zone) }
        return days.associateWith { day ->
            spentOnDay(byDay[day].orEmpty(), day, zone)
        }
    }

    private fun Snapshot.dayIn(zone: ZoneId): LocalDate =
        Instant.ofEpochMilli(ts).atZone(zone).toLocalDate()
}
