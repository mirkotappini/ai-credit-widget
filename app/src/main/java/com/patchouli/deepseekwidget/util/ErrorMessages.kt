package com.patchouli.deepseekwidget.util

import android.content.Context
import com.patchouli.deepseekwidget.R
import com.patchouli.deepseekwidget.data.Prefs

object ErrorMessages {

    fun message(context: Context, code: String?): String? = when (code) {
        Prefs.ERROR_MISSING_KEY -> context.getString(R.string.state_no_key_title)
        Prefs.ERROR_AUTH -> context.getString(R.string.state_auth_title)
        Prefs.ERROR_NETWORK -> context.getString(R.string.error_offline_title)
        Prefs.ERROR_UNEXPECTED -> context.getString(R.string.error_unexpected)
        else -> null
    }
}
