package com.patchouli.deepseekwidget.logic

import com.patchouli.deepseekwidget.net.CostBucket
import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset

/**
 * Aggregazione dei cost bucket OpenAI, pura e testabile su JVM.
 *
 * I bucket orari sono allineati all'ora UTC: un bucket puo' quindi scavallare
 * la mezzanotte locale (fusi non interi). In quel caso si conta solo la quota
 * proporzionale all'overlap: (overlapMs / bucketMs) * amount.
 *
 * Internamente non si arrotonda; la scala 2 HALF_UP si applica solo al valore
 * restituito. Nessun double nel calcolo monetario.
 */
object CostAggregator {

    private const val HOUR_MS = 3_600_000L
    private const val DAY_MS = 86_400_000L
    private val DIVISION_CONTEXT = MathContext.DECIMAL128

    /** Somma degli effettivi bucket orari che intersecano il giorno locale corrente. */
    fun spentTodayLocal(
        buckets: List<CostBucket>,
        nowMillis: Long,
        zone: ZoneId = ZoneId.systemDefault()
    ): BigDecimal {
        val today = Instant.ofEpochMilli(nowMillis).atZone(zone).toLocalDate()
        return spentOnLocalDay(buckets, today, zone)
    }

    /** Spesa per ciascuno dei 7 giorni locali che terminano oggi (giorni vuoti = 0). */
    fun spentLast7DaysLocal(
        buckets: List<CostBucket>,
        nowMillis: Long,
        zone: ZoneId = ZoneId.systemDefault()
    ): Map<LocalDate, BigDecimal> {
        val today = Instant.ofEpochMilli(nowMillis).atZone(zone).toLocalDate()
        val first = today.minusDays(6)
        val result = LinkedHashMap<LocalDate, BigDecimal>(7)
        var day = first
        while (!day.isAfter(today)) {
            result[day] = spentOnLocalDay(buckets, day, zone)
            day = day.plusDays(1)
        }
        return result
    }

    /** Somma dei bucket giornalieri UTC del mese corrente UTC. */
    fun spentCurrentMonthUtc(buckets1d: List<CostBucket>, nowMillis: Long): BigDecimal {
        val todayUtc = Instant.ofEpochMilli(nowMillis).atZone(ZoneOffset.UTC).toLocalDate()
        val monthStart = todayUtc.withDayOfMonth(1)
        val startMs = monthStart.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        val endMs = monthStart.plusMonths(1)
            .atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

        var total = BigDecimal.ZERO
        for (bucket in buckets1d) {
            if (!isDaily(bucket)) continue
            val bucketStartMs = bucket.startTsUtc * 1000L
            if (bucketStartMs < startMs || bucketStartMs >= endMs) continue
            total = total.add(bucket.amount)
        }
        return total.setScale(2, RoundingMode.HALF_UP)
    }

    private fun spentOnLocalDay(
        buckets: List<CostBucket>,
        day: LocalDate,
        zone: ZoneId
    ): BigDecimal {
        val dayStartMs = day.atStartOfDay(zone).toInstant().toEpochMilli()
        val dayEndMs = day.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()

        var total = BigDecimal.ZERO
        for (bucket in buckets) {
            if (!isHourly(bucket)) continue
            val bucketStartMs = bucket.startTsUtc * 1000L
            val bucketEndMs = bucket.endTsUtc * 1000L
            val bucketMs = bucketEndMs - bucketStartMs
            if (bucketMs <= 0L) continue

            val overlapStart = maxOf(bucketStartMs, dayStartMs)
            val overlapEnd = minOf(bucketEndMs, dayEndMs)
            if (overlapEnd <= overlapStart) continue

            val overlapMs = BigDecimal.valueOf(overlapEnd - overlapStart)
            val share = bucket.amount
                .multiply(overlapMs)
                .divide(BigDecimal.valueOf(bucketMs), DIVISION_CONTEXT)
            total = total.add(share)
        }
        return total.setScale(2, RoundingMode.HALF_UP)
    }

    /**
     * Il DB condivide la tabella tra 1h e 1d: distinguiamo i bucket dalla
     * durata per non sommare due volte lo stesso periodo.
     */
    private fun isHourly(bucket: CostBucket): Boolean {
        val width = bucket.endTsUtc - bucket.startTsUtc
        return width > 0L && width <= 2L * HOUR_MS / 1000L
    }

    private fun isDaily(bucket: CostBucket): Boolean {
        val width = bucket.endTsUtc - bucket.startTsUtc
        return width >= DAY_MS / 1000L - 3_600L && width <= DAY_MS / 1000L + 3_600L
    }
}
