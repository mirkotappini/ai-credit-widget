package com.patchouli.deepseekwidget.widget

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.patchouli.deepseekwidget.provider.Provider
import com.patchouli.deepseekwidget.work.RefreshScheduler

class WidgetRefreshReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == ACTION_REFRESH) {
            WidgetUpdater.markRefreshing(context.applicationContext)
            RefreshScheduler.refreshNow(context.applicationContext, Provider.DEEPSEEK)
        }
    }

    companion object {
        const val ACTION_REFRESH = "com.patchouli.deepseekwidget.action.REFRESH"
    }
}
