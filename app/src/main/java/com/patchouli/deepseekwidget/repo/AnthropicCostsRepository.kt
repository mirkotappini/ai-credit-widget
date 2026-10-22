package com.patchouli.deepseekwidget.repo

import android.content.Context
import com.patchouli.deepseekwidget.data.Prefs
import com.patchouli.deepseekwidget.data.SnapshotDb
import com.patchouli.deepseekwidget.net.AnthropicCostsApi
import com.patchouli.deepseekwidget.net.CostBucket
import com.patchouli.deepseekwidget.net.CostsResult
import com.patchouli.deepseekwidget.provider.Provider
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit

/**
 * Refresh dei costi Claude: solo bucket giornalieri UTC del mese corrente.
 * Nessun endpoint di saldo. La key non viene mai loggata e nessuna richiesta
 * parte se manca.
 */
class AnthropicCostsRepository(
    context: Context,
    private val api: AnthropicCostsApi = AnthropicCostsApi(),
    private val clock: () -> Long = System::currentTimeMillis
) {

    private val prefs = Prefs(context.applicationContext)
    private val db = SnapshotDb.getInstance(context.applicationContext)

    fun refresh(): CostsResult {
        val now = clock()
        val nowSec = now / 1000L
        prefs.setLastAttemptTs(now, Provider.CLAUDE)

        val adminKey = prefs.getApiKey(Provider.CLAUDE)
        if (adminKey.isNullOrBlank()) {
            prefs.setLastError(Prefs.ERROR_MISSING_KEY, Provider.CLAUDE)
            return CostsResult.MissingKey
        }

        val start = currentMonthStartUtc(now)
        val result = api.fetch(adminKey, start, limit = 31, bucketWidth = "1d")
        if (result !is CostsResult.Success) return recordFailure(result)

        db.upsertCostBuckets(Provider.CLAUDE, result.buckets)
        db.pruneCostBuckets(
            Provider.CLAUDE,
            nowSec - TimeUnit.DAYS.toSeconds(RETENTION_DAYS)
        )
        prefs.setLastSuccessTs(now, Provider.CLAUDE)
        prefs.setLastError(null, Provider.CLAUDE)
        return result
    }

    fun dailyBucketsSince(sinceTsUtc: Long): List<CostBucket> =
        db.costBucketsSince(Provider.CLAUDE, sinceTsUtc).filter {
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
        prefs.setLastError(code, Provider.CLAUDE)
        return result
    }

    private fun currentMonthStartUtc(nowMillis: Long): String {
        val instant = Instant.ofEpochMilli(nowMillis)
            .atZone(ZoneOffset.UTC)
            .toLocalDate()
            .withDayOfMonth(1)
            .atStartOfDay(ZoneOffset.UTC)
            .toInstant()
        return DateTimeFormatter.ISO_INSTANT.format(instant)
    }

    companion object {
        private const val RETENTION_DAYS = 400L
    }
}
