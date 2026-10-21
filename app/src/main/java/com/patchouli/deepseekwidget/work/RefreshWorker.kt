package com.patchouli.deepseekwidget.work

import android.content.Context
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.patchouli.deepseekwidget.data.Prefs
import com.patchouli.deepseekwidget.net.ApiResult
import com.patchouli.deepseekwidget.net.CostsResult
import com.patchouli.deepseekwidget.provider.Provider
import com.patchouli.deepseekwidget.repo.BalanceRepository
import com.patchouli.deepseekwidget.repo.OpenAiCostsRepository

class RefreshWorker(
    appContext: Context,
    params: WorkerParameters
) : Worker(appContext, params) {

    override fun doWork(): Result {
        // Il widget forza DeepSeek: il percorso OpenAI non tocca snapshot
        // DeepSeek ne' WidgetUpdater.
        val forced = inputData.getString(KEY_PROVIDER)?.let { Provider.fromStorage(it) }
        val provider = forced ?: Prefs(applicationContext).getActiveProvider()
        return if (provider == Provider.OPENAI) refreshOpenAi() else refreshDeepSeek()
    }

    private fun refreshDeepSeek(): Result =
        when (BalanceRepository(applicationContext).refresh()) {
            is ApiResult.NetworkError -> retryOrGiveUp()
            else -> Result.success()
        }

    private fun refreshOpenAi(): Result =
        when (OpenAiCostsRepository(applicationContext).refresh()) {
            is CostsResult.NetworkError -> retryOrGiveUp()
            else -> Result.success()
        }

    private fun retryOrGiveUp(): Result =
        if (runAttemptCount < MAX_NETWORK_RETRIES) Result.retry() else Result.success()

    companion object {
        const val KEY_PROVIDER = "refresh_provider"
        private const val MAX_NETWORK_RETRIES = 4
    }
}
