package com.patchouli.deepseekwidget.repo

import android.content.Context
import com.patchouli.deepseekwidget.data.Prefs
import com.patchouli.deepseekwidget.data.Snapshot
import com.patchouli.deepseekwidget.data.SnapshotDb
import com.patchouli.deepseekwidget.logic.SpendCalculator
import com.patchouli.deepseekwidget.net.ApiResult
import com.patchouli.deepseekwidget.net.DeepSeekApi
import com.patchouli.deepseekwidget.notify.AlertNotifier
import com.patchouli.deepseekwidget.util.Format
import com.patchouli.deepseekwidget.widget.WidgetUpdater
import java.math.BigDecimal
import java.time.LocalDate
import java.util.concurrent.TimeUnit

class BalanceRepository(context: Context) {

    private val appContext = context.applicationContext
    private val prefs = Prefs(appContext)
    private val db = SnapshotDb.getInstance(appContext)
    private val api = DeepSeekApi()

    /** Fetch + persistenza + widget + alert. Bloccante: chiamare fuori dal main thread. */
    fun refresh(): ApiResult {
        val now = System.currentTimeMillis()
        prefs.setLastAttemptTs(now)

        val apiKey = prefs.getApiKey()
        if (apiKey.isNullOrBlank()) {
            prefs.setLastError(Prefs.ERROR_MISSING_KEY)
            WidgetUpdater.updateAll(appContext)
            return ApiResult.MissingKey
        }

        val result = api.fetch(apiKey)
        when (result) {
            is ApiResult.Success -> {
                db.insert(
                    ts = now,
                    total = result.balance.total,
                    granted = result.balance.granted,
                    topped = result.balance.topped
                )
                prefs.setLastSuccessTs(now)
                prefs.setLastError(null)
                db.pruneOlderThan(now - TimeUnit.DAYS.toMillis(RETENTION_DAYS))
                AlertNotifier.evaluate(appContext, result.balance.total)
            }
            is ApiResult.Unauthorized -> prefs.setLastError(Prefs.ERROR_AUTH)
            is ApiResult.NetworkError -> prefs.setLastError(Prefs.ERROR_NETWORK)
            is ApiResult.Unexpected -> prefs.setLastError(Prefs.ERROR_UNEXPECTED)
            ApiResult.MissingKey -> prefs.setLastError(Prefs.ERROR_MISSING_KEY)
        }

        WidgetUpdater.updateAll(appContext)
        return result
    }

    fun latestSnapshot(): Snapshot? = db.latest()

    fun todaySpent(): BigDecimal {
        val today = LocalDate.now()
        val from = Format.startOfDayMillis(today)
        return SpendCalculator.spentOnDay(db.since(from), today)
    }

    fun snapshotsForDays(days: Int): List<Snapshot> {
        val today = LocalDate.now()
        val from = Format.startOfDayMillis(today.minusDays((days - 1).toLong()))
        return db.since(from)
    }

    fun lastSuccessTs(): Long = prefs.getLastSuccessTs()

    fun lastError(): String? = prefs.getLastError()

    fun threshold(): BigDecimal = prefs.getThreshold()

    companion object {
        private const val RETENTION_DAYS = 180L
    }
}
