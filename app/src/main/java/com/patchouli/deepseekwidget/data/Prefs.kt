package com.patchouli.deepseekwidget.data

import android.content.Context
import com.patchouli.deepseekwidget.provider.Provider
import com.patchouli.deepseekwidget.security.KeyStoreCrypto
import java.math.BigDecimal

/**
 * Preferenze locali. Le API key non vengono mai salvate in chiaro: in prefs
 * restano solo IV e ciphertext prodotti dall'Android Keystore.
 *
 * Ogni provider ha chiavi separate. Le chiavi storiche DeepSeek mantengono il
 * nome originale, quindi le installazioni esistenti restano valide senza
 * migrazione.
 */
class Prefs(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    // --- API key ---------------------------------------------------------

    fun hasApiKey(provider: Provider): Boolean =
        prefs.contains(ivKey(provider)) && prefs.contains(cipherKey(provider))

    fun getApiKey(provider: Provider): String? {
        val ciphertext = prefs.getString(cipherKey(provider), null) ?: return null
        val iv = prefs.getString(ivKey(provider), null) ?: return null
        val plain = KeyStoreCrypto.decrypt(iv, ciphertext) ?: return null
        return plain.ifBlank { null }
    }

    /** Salva la key cifrata. Stringa vuota rimuove la key. */
    fun setApiKey(provider: Provider, plaintext: String?): Boolean {
        if (plaintext.isNullOrBlank()) {
            prefs.edit()
                .remove(cipherKey(provider))
                .remove(ivKey(provider))
                .apply()
            return true
        }
        val encrypted = KeyStoreCrypto.encrypt(plaintext.trim()) ?: return false
        prefs.edit()
            .putString(ivKey(provider), encrypted.ivBase64)
            .putString(cipherKey(provider), encrypted.ciphertextBase64)
            .apply()
        return true
    }

    // Adattatori DeepSeek: le API storiche restano valide per widget e fragment.

    fun hasApiKey(): Boolean = hasApiKey(Provider.DEEPSEEK)

    fun getApiKey(): String? = getApiKey(Provider.DEEPSEEK)

    fun setApiKey(plaintext: String?): Boolean = setApiKey(Provider.DEEPSEEK, plaintext)

    // --- Provider attivo -------------------------------------------------

    /**
     * Provider attivo, oppure null se nessun provider e' configurato.
     * Se il provider salvato non ha piu' la key, si usa l'altro configurato;
     * con entrambi configurati e nessuna scelta salvata vince DeepSeek.
     */
    fun activeProviderOrNull(): Provider? {
        val raw = prefs.getString(KEY_ACTIVE_PROVIDER, null)
        val stored = raw?.let { Provider.fromStorage(it) }
        if (stored != null && hasApiKey(stored)) return stored
        return when {
            hasApiKey(Provider.DEEPSEEK) -> Provider.DEEPSEEK
            hasApiKey(Provider.OPENAI) -> Provider.OPENAI
            hasApiKey(Provider.CLAUDE) -> Provider.CLAUDE
            else -> null
        }
    }

    /** Compatibilita': senza provider configurato resta DeepSeek (widget e worker). */
    fun getActiveProvider(): Provider = activeProviderOrNull() ?: Provider.DEEPSEEK

    fun setActiveProvider(provider: Provider) {
        prefs.edit().putString(KEY_ACTIVE_PROVIDER, provider.storageKey).apply()
    }

    fun clearActiveProvider() {
        prefs.edit().remove(KEY_ACTIVE_PROVIDER).apply()
    }

    // --- Soglia, intervallo, notifiche ----------------------------------

    fun getThreshold(): BigDecimal {
        val raw = prefs.getString(KEY_THRESHOLD, DEFAULT_THRESHOLD) ?: DEFAULT_THRESHOLD
        return raw.toBigDecimalOrNull() ?: BigDecimal(DEFAULT_THRESHOLD)
    }

    fun setThreshold(value: BigDecimal) {
        prefs.edit().putString(KEY_THRESHOLD, value.toPlainString()).apply()
    }

    fun getIntervalMinutes(): Int {
        val value = prefs.getInt(KEY_INTERVAL, 60)
        return if (value in ALLOWED_INTERVALS) value else 60
    }

    fun setIntervalMinutes(value: Int) {
        val safe = if (value in ALLOWED_INTERVALS) value else 60
        prefs.edit().putInt(KEY_INTERVAL, safe).apply()
    }

    fun isBelowThreshold(): Boolean = prefs.getBoolean(KEY_BELOW_THRESHOLD, false)

    fun setBelowThreshold(below: Boolean) {
        prefs.edit().putBoolean(KEY_BELOW_THRESHOLD, below).apply()
    }

    // --- Stato refresh per provider -------------------------------------

    fun getLastSuccessTs(provider: Provider = Provider.DEEPSEEK): Long =
        prefs.getLong(lastSuccessKey(provider), 0L)

    fun setLastSuccessTs(ts: Long, provider: Provider = Provider.DEEPSEEK) {
        prefs.edit().putLong(lastSuccessKey(provider), ts).apply()
    }

    fun getLastAttemptTs(provider: Provider = Provider.DEEPSEEK): Long =
        prefs.getLong(lastAttemptKey(provider), 0L)

    fun setLastAttemptTs(ts: Long, provider: Provider = Provider.DEEPSEEK) {
        prefs.edit().putLong(lastAttemptKey(provider), ts).apply()
    }

    fun getLastError(provider: Provider = Provider.DEEPSEEK): String? =
        prefs.getString(lastErrorKey(provider), null)

    fun setLastError(code: String?, provider: Provider = Provider.DEEPSEEK) {
        prefs.edit().apply {
            if (code == null) remove(lastErrorKey(provider))
            else putString(lastErrorKey(provider), code)
        }.apply()
    }

    // --- Chiavi prefs ----------------------------------------------------

    private fun ivKey(provider: Provider): String = when (provider) {
        Provider.DEEPSEEK -> KEY_IV
        Provider.OPENAI -> KEY_OPENAI_IV
        Provider.CLAUDE -> KEY_CLAUDE_IV
    }

    private fun cipherKey(provider: Provider): String = when (provider) {
        Provider.DEEPSEEK -> KEY_CIPHERTEXT
        Provider.OPENAI -> KEY_OPENAI_CIPHERTEXT
        Provider.CLAUDE -> KEY_CLAUDE_CIPHERTEXT
    }

    private fun lastSuccessKey(provider: Provider): String = when (provider) {
        Provider.DEEPSEEK -> KEY_LAST_SUCCESS_TS
        Provider.OPENAI -> KEY_OPENAI_LAST_SUCCESS_TS
        Provider.CLAUDE -> KEY_CLAUDE_LAST_SUCCESS_TS
    }

    private fun lastAttemptKey(provider: Provider): String = when (provider) {
        Provider.DEEPSEEK -> KEY_LAST_ATTEMPT_TS
        Provider.OPENAI -> KEY_OPENAI_LAST_ATTEMPT_TS
        Provider.CLAUDE -> KEY_CLAUDE_LAST_ATTEMPT_TS
    }

    private fun lastErrorKey(provider: Provider): String = when (provider) {
        Provider.DEEPSEEK -> KEY_LAST_ERROR
        Provider.OPENAI -> KEY_OPENAI_LAST_ERROR
        Provider.CLAUDE -> KEY_CLAUDE_LAST_ERROR
    }

    companion object {
        private const val PREFS_NAME = "deepseek_prefs"
        private const val KEY_IV = "api_key_iv"
        private const val KEY_CIPHERTEXT = "api_key_ciphertext"
        private const val KEY_OPENAI_IV = "openai_api_key_iv"
        private const val KEY_OPENAI_CIPHERTEXT = "openai_api_key_ciphertext"
        private const val KEY_ACTIVE_PROVIDER = "active_provider"
        private const val KEY_THRESHOLD = "alert_threshold_usd"
        private const val KEY_INTERVAL = "refresh_interval_minutes"
        private const val KEY_LAST_SUCCESS_TS = "last_success_ts"
        private const val KEY_LAST_ATTEMPT_TS = "last_attempt_ts"
        private const val KEY_LAST_ERROR = "last_error"
        private const val KEY_OPENAI_LAST_SUCCESS_TS = "openai_last_success_ts"
        private const val KEY_OPENAI_LAST_ATTEMPT_TS = "openai_last_attempt_ts"
        private const val KEY_OPENAI_LAST_ERROR = "openai_last_error"
        private const val KEY_CLAUDE_IV = "claude_api_key_iv"
        private const val KEY_CLAUDE_CIPHERTEXT = "claude_api_key_ciphertext"
        private const val KEY_CLAUDE_LAST_SUCCESS_TS = "claude_last_success_ts"
        private const val KEY_CLAUDE_LAST_ATTEMPT_TS = "claude_last_attempt_ts"
        private const val KEY_CLAUDE_LAST_ERROR = "claude_last_error"
        private const val KEY_BELOW_THRESHOLD = "below_threshold"

        private const val DEFAULT_THRESHOLD = "10.00"
        val ALLOWED_INTERVALS = intArrayOf(15, 30, 60)

        const val ERROR_MISSING_KEY = "missing_key"
        const val ERROR_AUTH = "auth"
        const val ERROR_FORBIDDEN = "forbidden"
        const val ERROR_NETWORK = "network"
        const val ERROR_UNEXPECTED = "unexpected"
    }
}
