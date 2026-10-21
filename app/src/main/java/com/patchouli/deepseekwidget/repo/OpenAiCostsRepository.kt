package com.patchouli.deepseekwidget.repo

import android.content.Context
import com.patchouli.deepseekwidget.data.Prefs
import com.patchouli.deepseekwidget.data.SnapshotDb
import com.patchouli.deepseekwidget.net.CostBucket
import com.patchouli.deepseekwidget.net.CostsResult
import com.patchouli.deepseekwidget.net.OpenAiCostsApi
import com.patchouli.deepseekwidget.provider.Provider
import java.time.Instant
import java.time.ZoneOffset
import java.util.concurrent.TimeUnit

/**
 * Refresh dei costi OpenAI. Scarica 1h per le ultime 48h e 1d per il mese UTC
 * corrente, salva i bucket e pota oltre 400 giorni. La key non viene mai
 * loggata e nessuna chiamata parte se manca.
 */
class OpenAiCostsRepository(
    context: Context,
    private val api: OpenAiCostsApi = OpenAiCostsApi(),
    private val clock: () -> Long = System::currentTimeMillis
) {

    private val prefs = Prefs(context.applicationContext)
    private val db = SnapshotDb.getInstance(context.applicationContext)

    fun refresh(): CostsResult {
        val now = clock()
        val nowSec = now / 1000L
        prefs.setLastAttemptTs(now, Provider.OPENAI)

        val adminKey = prefs.getApiKey(Provider.OPENAI)
        if (adminKey.isNullOrBlank()) {
            prefs.setLastError(Prefs.ERROR_MISSING_KEY, Provider.OPENAI)
            return CostsResult.MissingKey
        }

        // 48 bucket orari allineati all'ora UTC.
        val hourlyStart = (nowSec / 3600L - 48L) * 3600L
        val hourly = api.fetch(adminKey, hourlyStart, "1h")
        if (hourly !is CostsResult.Success) return recordFailure(hourly)

        // Il mese corrente serve per la metrica UTC; si estende a 32 giorni per
        // coprire grafico 14 giorni e storico 30 giorni a cavallo del mese.
        val monthlyStart = currentMonthStartUtcSec(now)
        val dailyStart = minOf(monthlyStart, ((nowSec - 32L * 86_400L) / 86_400L) * 86_400L)
        val daily = api.fetch(adminKey, dailyStart, "1d")
        if (daily !is CostsResult.Success) return recordFailure(daily)

        // Prima gli orari, poi i giornalieri: a parita' di start_ts_utc (UTC
        // midnight) il giornaliero vince, cosi' il mese resta completo.
        db.upsertCostBuckets(Provider.OPENAI, hourly.buckets)
        db.upsertCostBuckets(Provider.OPENAI, daily.buckets)
        db.pruneCostBuckets(
            Provider.OPENAI,
            nowSec - TimeUnit.DAYS.toSeconds(RETENTION_DAYS)
        )

        prefs.setLastSuccessTs(now, Provider.OPENAI)
        prefs.setLastError(null, Provider.OPENAI)
        return CostsResult.Success(hourly.buckets + daily.buckets)
    }

    fun hourlyBucketsSince(sinceTsUtc: Long): List<CostBucket> =
        db.costBucketsSince(Provider.OPENAI, sinceTsUtc).filter {
            val width = it.endTsUtc - it.startTsUtc
            width > 0L && width <= 7_200L
        }

    fun dailyBucketsSince(sinceTsUtc: Long): List<CostBucket> =
        db.costBucketsSince(Provider.OPENAI, sinceTsUtc).filter {
            val width = it.endTsUtc - it.startTsUtc
            width in 82_800L..90_000L
        }

    private fun recordFailure(result: CostsResult): CostsResult {
        val code = when (result) {
            CostsResult.MissingKey -> Prefs.ERROR_MISSING_KEY
            CostsResult.Unauthorized -> Prefs.ERROR_AUTH
            CostsResult.Forbidden -> Prefs.ERROR_FORBIDDEN
            is CostsResult.NetworkError -> Prefs.ERROR_NETWORK
            is CostsResult.Unexpected -> Prefs.ERROR_UNEXPECTED
            is CostsResult.Success -> return result
        }
        prefs.setLastError(code, Provider.OPENAI)
        return result
    }

    private fun currentMonthStartUtcSec(nowMillis: Long): Long =
        Instant.ofEpochMilli(nowMillis)
            .atZone(ZoneOffset.UTC)
            .toLocalDate()
            .withDayOfMonth(1)
            .atStartOfDay(ZoneOffset.UTC)
            .toInstant()
            .epochSecond

    companion object {
        private const val RETENTION_DAYS = 400L
    }
}
