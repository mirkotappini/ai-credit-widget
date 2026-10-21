package com.patchouli.deepseekwidget.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.RemoteViews
import androidx.core.content.ContextCompat
import com.patchouli.deepseekwidget.MainActivity
import com.patchouli.deepseekwidget.R
import com.patchouli.deepseekwidget.data.Prefs
import com.patchouli.deepseekwidget.data.Snapshot
import com.patchouli.deepseekwidget.data.SnapshotDb
import com.patchouli.deepseekwidget.logic.SpendCalculator
import com.patchouli.deepseekwidget.provider.Provider
import com.patchouli.deepseekwidget.util.Format
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

object WidgetUpdater {

    fun updateAll(context: Context) {
        val manager = AppWidgetManager.getInstance(context) ?: return
        val smallIds = manager.getAppWidgetIds(
            ComponentName(context, Widget2x2Provider::class.java)
        )
        if (smallIds.isNotEmpty()) {
            manager.updateAppWidget(smallIds, buildSmall(context, refreshing = false))
        }
        val largeIds = manager.getAppWidgetIds(
            ComponentName(context, Widget4x2Provider::class.java)
        )
        if (largeIds.isNotEmpty()) {
            manager.updateAppWidget(largeIds, buildLarge(context, refreshing = false))
        }
    }

    /** Aggiorna subito lo stato "aggiorno…" prima di accodare il lavoro. */
    fun markRefreshing(context: Context) {
        val manager = AppWidgetManager.getInstance(context) ?: return
        val smallIds = manager.getAppWidgetIds(
            ComponentName(context, Widget2x2Provider::class.java)
        )
        if (smallIds.isNotEmpty()) {
            manager.updateAppWidget(smallIds, buildSmall(context, refreshing = true))
        }
        val largeIds = manager.getAppWidgetIds(
            ComponentName(context, Widget4x2Provider::class.java)
        )
        if (largeIds.isNotEmpty()) {
            manager.updateAppWidget(largeIds, buildLarge(context, refreshing = true))
        }
    }

    private fun buildSmall(context: Context, refreshing: Boolean): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_2x2)
        val state = readState(context, refreshing)
        val noData = context.getString(R.string.widget_no_data)
        val snapshot = state.snapshot

        val total = if (state.valuesVisible && snapshot != null) {
            Format.moneyWidget2x2(snapshot.total)
        } else {
            noData
        }
        val today = if (state.valuesVisible) {
            context.getString(
                R.string.widget_today,
                Format.moneyWidget2x2(state.todaySpent)
            )
        } else {
            context.getString(R.string.widget_today, noData)
        }
        views.setTextViewText(R.id.widget_total, total)
        views.setTextViewText(R.id.widget_today, today)
        views.setTextViewText(
            R.id.widget_label_balance,
            context.getString(
                if (state.status == WidgetStatus.MISSING) R.string.widget_label_deepseek
                else R.string.widget_label_balance
            )
        )

        val status = smallStatus(context, state)
        if (status == null) {
            views.setViewVisibility(R.id.widget_status, View.GONE)
        } else {
            views.setViewVisibility(R.id.widget_status, View.VISIBLE)
            views.setTextViewText(R.id.widget_status, status)
            views.setTextColor(
                R.id.widget_status,
                ContextCompat.getColor(context, statusColor(state.status))
            )
        }
        applyDot(context, views, state)

        val description = if (state.status == WidgetStatus.MISSING) {
            context.getString(R.string.widget_cd_missing_2x2)
        } else if (state.valuesVisible && snapshot != null) {
            context.getString(
                R.string.widget_cd_2x2,
                Format.money(snapshot.total),
                Format.money(state.todaySpent),
                status.orEmpty()
            )
        } else {
            context.getString(
                R.string.widget_cd_2x2,
                noData,
                noData,
                status.orEmpty()
            )
        }
        views.setContentDescription(R.id.widget_root, description.trim())
        views.setOnClickPendingIntent(
            R.id.widget_root,
            if (state.tapOpensSettings) {
                settingsPendingIntent(context, 200)
            } else {
                refreshPendingIntent(context, 100)
            }
        )
        return views
    }

    private fun buildLarge(context: Context, refreshing: Boolean): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_4x2)
        val state = readState(context, refreshing)
        val noData = context.getString(R.string.widget_no_data)
        val snapshot = state.snapshot

        val total = if (state.valuesVisible && snapshot != null) {
            Format.moneyWidget4x2(snapshot.total)
        } else {
            noData
        }
        val granted = if (state.valuesVisible && snapshot != null) {
            Format.moneyWidgetMetric(snapshot.granted)
        } else {
            noData
        }
        val topped = if (state.valuesVisible && snapshot != null) {
            Format.moneyWidgetMetric(snapshot.topped)
        } else {
            noData
        }
        val today = if (state.valuesVisible) {
            Format.moneyWidgetMetric(state.todaySpent)
        } else {
            noData
        }
        views.setTextViewText(R.id.widget_total, total)
        views.setTextViewText(R.id.widget_granted, granted)
        views.setTextViewText(R.id.widget_topped, topped)
        views.setTextViewText(R.id.widget_today, today)

        val updated = largeStatus(context, state)
        views.setTextViewText(R.id.widget_updated, updated)
        views.setTextColor(
            R.id.widget_updated,
            ContextCompat.getColor(context, statusColor(state.status))
        )
        applyDot(context, views, state)

        val description = if (state.status == WidgetStatus.MISSING) {
            context.getString(R.string.widget_cd_missing_4x2)
        } else if (state.valuesVisible && snapshot != null) {
            context.getString(
                R.string.widget_cd_4x2,
                Format.money(snapshot.total),
                Format.money(snapshot.granted),
                Format.money(snapshot.topped),
                Format.money(state.todaySpent),
                updated
            )
        } else {
            context.getString(
                R.string.widget_cd_4x2,
                noData,
                noData,
                noData,
                noData,
                updated
            )
        }
        views.setContentDescription(R.id.widget_root, description.trim())
        views.setOnClickPendingIntent(
            R.id.widget_root,
            if (state.tapOpensSettings) {
                settingsPendingIntent(context, 201)
            } else {
                refreshPendingIntent(context, 101)
            }
        )
        return views
    }

    private fun applyDot(context: Context, views: RemoteViews, state: WidgetState) {
        val dot = state.dotRes
        if (dot == null) {
            views.setViewVisibility(R.id.widget_dot, View.GONE)
        } else {
            views.setViewVisibility(R.id.widget_dot, View.VISIBLE)
            views.setImageViewResource(R.id.widget_dot, dot)
        }
    }

    private fun smallStatus(context: Context, state: WidgetState): String? = when (state.status) {
        WidgetStatus.NONE -> null
        WidgetStatus.MISSING -> context.getString(R.string.widget_status_missing_deepseek)
        WidgetStatus.AUTH -> context.getString(R.string.widget_status_auth)
        WidgetStatus.OFFLINE -> if (state.snapshot == null) {
            context.getString(R.string.widget_status_tap)
        } else {
            context.getString(R.string.widget_status_offline)
        }
        WidgetStatus.UNEXPECTED -> context.getString(R.string.widget_status_unexpected)
        WidgetStatus.BELOW -> context.getString(R.string.widget_status_below)
        WidgetStatus.REFRESHING -> context.getString(R.string.widget_status_refreshing)
        WidgetStatus.STALE -> context.getString(R.string.widget_status_stale)
        WidgetStatus.STALE_YESTERDAY -> context.getString(R.string.widget_status_stale_yesterday)
        WidgetStatus.NO_DATA -> context.getString(R.string.widget_status_tap)
    }

    private fun largeStatus(context: Context, state: WidgetState): String {
        val timeLabel = when {
            state.lastSuccess <= 0L -> null
            isToday(state.lastSuccess) -> Format.shortTime(state.lastSuccess)
            isYesterday(state.lastSuccess) -> context.getString(
                R.string.widget_updated_yesterday,
                Format.shortTime(state.lastSuccess)
            ).removePrefix("agg. ")
            else -> Format.updatedDate(state.lastSuccess)
        }
        return when (state.status) {
            WidgetStatus.REFRESHING -> context.getString(R.string.widget_status_refreshing)
            WidgetStatus.MISSING -> context.getString(R.string.widget_status_missing_deepseek_large)
            WidgetStatus.AUTH -> context.getString(R.string.widget_status_auth)
            WidgetStatus.OFFLINE -> if (timeLabel == null) {
                context.getString(R.string.widget_updated_offline_nodata)
            } else {
                context.getString(R.string.widget_updated_offline, timeLabel)
            }
            WidgetStatus.UNEXPECTED -> context.getString(R.string.widget_status_unexpected)
            WidgetStatus.BELOW -> if (timeLabel == null) {
                context.getString(R.string.widget_status_below)
            } else {
                context.getString(R.string.widget_updated_below, timeLabel)
            }
            WidgetStatus.STALE -> if (timeLabel == null) {
                context.getString(R.string.widget_status_stale)
            } else {
                context.getString(R.string.widget_updated_stale, timeLabel)
            }
            WidgetStatus.STALE_YESTERDAY -> if (state.lastSuccess > 0) {
                context.getString(
                    R.string.widget_updated_yesterday,
                    Format.shortTime(state.lastSuccess)
                )
            } else {
                context.getString(R.string.widget_status_stale_yesterday)
            }
            WidgetStatus.NO_DATA -> context.getString(R.string.widget_status_tap)
            WidgetStatus.NONE -> when {
                state.lastSuccess <= 0L -> context.getString(R.string.widget_status_tap)
                isToday(state.lastSuccess) -> context.getString(
                    R.string.widget_updated,
                    Format.shortTime(state.lastSuccess)
                )
                isYesterday(state.lastSuccess) -> context.getString(
                    R.string.widget_updated_yesterday,
                    Format.shortTime(state.lastSuccess)
                )
                else -> context.getString(
                    R.string.widget_updated_older,
                    Format.updatedDate(state.lastSuccess)
                )
            }
        }
    }

    private fun statusColor(status: WidgetStatus): Int = when (status) {
        WidgetStatus.REFRESHING -> R.color.ds_color_accent
        WidgetStatus.AUTH, WidgetStatus.UNEXPECTED -> R.color.ds_color_error
        WidgetStatus.MISSING, WidgetStatus.OFFLINE, WidgetStatus.BELOW,
        WidgetStatus.STALE, WidgetStatus.STALE_YESTERDAY, WidgetStatus.NO_DATA ->
            R.color.ds_color_warning
        WidgetStatus.NONE -> R.color.ds_color_text_tertiary
    }

    private fun readState(context: Context, refreshing: Boolean): WidgetState {
        val prefs = Prefs(context)
        val db = SnapshotDb.getInstance(context)
        val snapshot = db.latest()
        val today = LocalDate.now()
        val spent = SpendCalculator.spentOnDay(
            db.since(Format.startOfDayMillis(today)),
            today
        )
        val lastSuccess = prefs.getLastSuccessTs()
        val error = prefs.getLastError()
        val hasKey = prefs.hasApiKey()
        val below = snapshot != null && snapshot.total < prefs.getThreshold()
        val stale = lastSuccess == 0L ||
            System.currentTimeMillis() - lastSuccess > prefs.getIntervalMinutes() * 2L * 60_000L

        val status = when {
            refreshing -> WidgetStatus.REFRESHING
            !hasKey -> WidgetStatus.MISSING
            error == Prefs.ERROR_MISSING_KEY -> WidgetStatus.MISSING
            error == Prefs.ERROR_AUTH -> WidgetStatus.AUTH
            error == Prefs.ERROR_NETWORK -> WidgetStatus.OFFLINE
            error == Prefs.ERROR_UNEXPECTED -> WidgetStatus.UNEXPECTED
            below -> WidgetStatus.BELOW
            snapshot == null -> WidgetStatus.NO_DATA
            lastSuccess == 0L -> WidgetStatus.NO_DATA
            stale && !isToday(lastSuccess) -> WidgetStatus.STALE_YESTERDAY
            stale -> WidgetStatus.STALE
            else -> WidgetStatus.NONE
        }

        val valuesVisible = snapshot != null &&
            hasKey &&
            error != Prefs.ERROR_AUTH &&
            error != Prefs.ERROR_MISSING_KEY

        val dot = when (status) {
            WidgetStatus.NONE -> null
            WidgetStatus.REFRESHING -> R.drawable.dot_accent
            WidgetStatus.AUTH, WidgetStatus.UNEXPECTED -> R.drawable.dot_error
            WidgetStatus.MISSING, WidgetStatus.OFFLINE, WidgetStatus.BELOW,
            WidgetStatus.STALE, WidgetStatus.STALE_YESTERDAY, WidgetStatus.NO_DATA ->
                R.drawable.dot_warning
        }

        return WidgetState(
            snapshot = snapshot,
            todaySpent = spent,
            lastSuccess = lastSuccess,
            status = status,
            dotRes = dot,
            valuesVisible = valuesVisible,
            tapOpensSettings = status == WidgetStatus.MISSING || status == WidgetStatus.AUTH
        )
    }

    private fun isToday(ts: Long): Boolean =
        Instant.ofEpochMilli(ts).atZone(ZoneId.systemDefault()).toLocalDate() == LocalDate.now()

    private fun isYesterday(ts: Long): Boolean =
        Instant.ofEpochMilli(ts).atZone(ZoneId.systemDefault()).toLocalDate() ==
            LocalDate.now().minusDays(1)

    private fun refreshPendingIntent(context: Context, requestCode: Int): PendingIntent {
        val intent = Intent(context, WidgetRefreshReceiver::class.java).apply {
            action = WidgetRefreshReceiver.ACTION_REFRESH
        }
        return PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun settingsPendingIntent(context: Context, requestCode: Int): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            putExtra(MainActivity.EXTRA_SCREEN, MainActivity.SCREEN_SETTINGS)
            putExtra(MainActivity.EXTRA_PROVIDER, Provider.DEEPSEEK.storageKey)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        return PendingIntent.getActivity(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private data class WidgetState(
        val snapshot: Snapshot?,
        val todaySpent: BigDecimal,
        val lastSuccess: Long,
        val status: WidgetStatus,
        val dotRes: Int?,
        val valuesVisible: Boolean,
        val tapOpensSettings: Boolean
    )

    private enum class WidgetStatus {
        NONE,
        MISSING,
        AUTH,
        OFFLINE,
        UNEXPECTED,
        BELOW,
        REFRESHING,
        STALE,
        STALE_YESTERDAY,
        NO_DATA
    }
}
