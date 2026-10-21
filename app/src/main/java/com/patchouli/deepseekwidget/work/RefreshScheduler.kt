package com.patchouli.deepseekwidget.work

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.patchouli.deepseekwidget.data.Prefs
import com.patchouli.deepseekwidget.provider.Provider
import java.util.concurrent.TimeUnit

object RefreshScheduler {

    private const val PERIODIC_WORK = "deepseek_periodic_refresh"
    private const val ONE_SHOT_WORK = "deepseek_manual_refresh"
    private const val TAG = "deepseek_refresh"

    private val constraints = Constraints.Builder()
        .setRequiredNetworkType(NetworkType.CONNECTED)
        .build()

    fun schedulePeriodic(context: Context) {
        val interval = Prefs(context).getIntervalMinutes().coerceAtLeast(15)
        val request = PeriodicWorkRequestBuilder<RefreshWorker>(
            interval.toLong(),
            TimeUnit.MINUTES
        )
            .setConstraints(constraints)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .addTag(TAG)
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            PERIODIC_WORK,
            ExistingPeriodicWorkPolicy.UPDATE,
            request
        )
    }

    fun cancel(context: Context) {
        WorkManager.getInstance(context).apply {
            cancelUniqueWork(PERIODIC_WORK)
            cancelUniqueWork(ONE_SHOT_WORK)
        }
    }

    fun refreshNow(context: Context, provider: Provider? = null) {
        val request = OneTimeWorkRequestBuilder<RefreshWorker>()
            .setConstraints(constraints)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .addTag(TAG)
            .apply {
                if (provider != null) {
                    setInputData(
                        workDataOf(RefreshWorker.KEY_PROVIDER to provider.storageKey)
                    )
                }
            }
            .build()

        WorkManager.getInstance(context).enqueueUniqueWork(
            ONE_SHOT_WORK,
            ExistingWorkPolicy.REPLACE,
            request
        )
    }
}
