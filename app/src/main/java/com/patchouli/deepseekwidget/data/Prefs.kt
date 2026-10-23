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

    enum class KeyState {
        /** Nessuna chiave salvata. */
        MISSING,

        /** Chiave salvata e decifrabile su questo dispositivo. */
        READABLE,

        /** Ciphertext presente ma non decifrabile: il Keystore non ha piu' la chiave. */
        UNREADABLE
    }

    fun keyState(provider: Provider): KeyState {
        val ciphertext = prefs.getString(cipherKey(provider), null) ?: return KeyState.MISSING
        val iv = prefs.getString(ivKey(provider), null) ?: return KeyState.MISSING
        val plain = KeyStoreCrypto.decrypt(iv, ciphertext) ?: return KeyState.UNREADABLE
        return if (plain.isBlank()) KeyState.MISSING else KeyState.READABLE
    }

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
            if (provider == Provider.OPENAI) resetOpenAiCreditForKeyChange()
            return true
        }
        val encrypted = KeyStoreCrypto.encrypt(plaintext.trim()) ?: return false
        prefs.edit()
            .putString(ivKey(provider), encrypted.ivBase64)
            .putString(cipherKey(provider), encrypted.ciphertextBase64)
            .apply()
        if (provider == Provider.OPENAI) resetOpenAiCreditForKeyChange()
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

    // --- Credito residuo OpenAI (v1.1.4) --------------------------------
    // Solo OpenAI. Gli importi sono salvati come stringa decimale canonica
    // (BigDecimal.toPlainString) e riletti con toBigDecimalOrNull, cosi' un
    // valore corrotto diventa null invece di far fallire il chiamante.

    /** Stato di autenticazione dell'endpoint credito, persistito tra i refresh. */
    enum class CreditAuthState {
        NONE,
        UNAUTHORIZED,
        FORBIDDEN
    }

    // Ancora manuale: importo dichiarato dall'utente + data di riferimento.

    fun getOpenAiCreditAnchorUsd(): BigDecimal? =
        decimalOrNull(KEY_OPENAI_CREDIT_ANCHOR_USD)

    fun getOpenAiCreditAnchorTs(): Long =
        prefs.getLong(KEY_OPENAI_CREDIT_ANCHOR_TS, 0L)

    /** Salva l'ancora; [amountUsd] null o [refTs] non positiva rimuovono il campo. */
    fun setOpenAiCreditAnchor(amountUsd: BigDecimal?, refTs: Long) {
        val editor = prefs.edit()
        if (amountUsd == null) {
            editor.remove(KEY_OPENAI_CREDIT_ANCHOR_USD)
        } else {
            editor.putString(KEY_OPENAI_CREDIT_ANCHOR_USD, amountUsd.toPlainString())
        }
        if (refTs <= 0L) {
            editor.remove(KEY_OPENAI_CREDIT_ANCHOR_TS)
        } else {
            editor.putLong(KEY_OPENAI_CREDIT_ANCHOR_TS, refTs)
        }
        editor.apply()
    }

    fun clearOpenAiCreditAnchor() {
        prefs.edit()
            .remove(KEY_OPENAI_CREDIT_ANCHOR_USD)
            .remove(KEY_OPENAI_CREDIT_ANCHOR_TS)
            .apply()
    }

    // Cache dell'ultimo valore reale letto dall'endpoint.

    fun getOpenAiCreditRealUsd(): BigDecimal? =
        decimalOrNull(KEY_OPENAI_CREDIT_REAL_USD)

    fun getOpenAiCreditRealGrantedUsd(): BigDecimal? =
        decimalOrNull(KEY_OPENAI_CREDIT_REAL_GRANTED_USD)

    fun getOpenAiCreditRealUsedUsd(): BigDecimal? =
        decimalOrNull(KEY_OPENAI_CREDIT_REAL_USED_USD)

    fun getOpenAiCreditRealTs(): Long =
        prefs.getLong(KEY_OPENAI_CREDIT_REAL_TS, 0L)

    fun setOpenAiCreditReal(
        availableUsd: BigDecimal,
        grantedUsd: BigDecimal?,
        usedUsd: BigDecimal?,
        ts: Long
    ) {
        val editor = prefs.edit()
            .putString(KEY_OPENAI_CREDIT_REAL_USD, availableUsd.toPlainString())
            .putLong(KEY_OPENAI_CREDIT_REAL_TS, ts)
        if (grantedUsd == null) {
            editor.remove(KEY_OPENAI_CREDIT_REAL_GRANTED_USD)
        } else {
            editor.putString(KEY_OPENAI_CREDIT_REAL_GRANTED_USD, grantedUsd.toPlainString())
        }
        if (usedUsd == null) {
            editor.remove(KEY_OPENAI_CREDIT_REAL_USED_USD)
        } else {
            editor.putString(KEY_OPENAI_CREDIT_REAL_USED_USD, usedUsd.toPlainString())
        }
        editor.apply()
    }

    fun clearOpenAiCreditReal() {
        prefs.edit()
            .remove(KEY_OPENAI_CREDIT_REAL_USD)
            .remove(KEY_OPENAI_CREDIT_REAL_GRANTED_USD)
            .remove(KEY_OPENAI_CREDIT_REAL_USED_USD)
            .remove(KEY_OPENAI_CREDIT_REAL_TS)
            .apply()
    }

    // Cooldown dopo un tentativo fallito, per non martellare l'endpoint.

    fun getOpenAiCreditFailedTs(): Long =
        prefs.getLong(KEY_OPENAI_CREDIT_FAILED_TS, 0L)

    fun setOpenAiCreditFailedTs(ts: Long) {
        prefs.edit().putLong(KEY_OPENAI_CREDIT_FAILED_TS, ts).apply()
    }

    fun clearOpenAiCreditFailed() {
        prefs.edit().remove(KEY_OPENAI_CREDIT_FAILED_TS).apply()
    }

    /**
     * True se e' passato abbastanza tempo dall'ultimo fallimento (o non c'e'
     * stato un fallimento). Con orologio spostato indietro si ritenta: meglio
     * una richiesta in piu' che restare bloccati per sempre.
     */
    fun shouldRetryOpenAiCredit(
        now: Long = System.currentTimeMillis(),
        cooldownMs: Long = OPENAI_CREDIT_RETRY_MS
    ): Boolean {
        val failed = getOpenAiCreditFailedTs()
        if (failed <= 0L || now < failed) return true
        return now - failed >= cooldownMs
    }

    fun getOpenAiCreditAuthState(): CreditAuthState =
        when (prefs.getString(KEY_OPENAI_CREDIT_AUTH_STATE, null)) {
            STATE_UNAUTHORIZED -> CreditAuthState.UNAUTHORIZED
            STATE_FORBIDDEN -> CreditAuthState.FORBIDDEN
            else -> CreditAuthState.NONE
        }

    fun setOpenAiCreditAuthState(state: CreditAuthState) {
        val editor = prefs.edit()
        when (state) {
            CreditAuthState.NONE -> editor.remove(KEY_OPENAI_CREDIT_AUTH_STATE)
            CreditAuthState.UNAUTHORIZED ->
                editor.putString(KEY_OPENAI_CREDIT_AUTH_STATE, STATE_UNAUTHORIZED)
            CreditAuthState.FORBIDDEN ->
                editor.putString(KEY_OPENAI_CREDIT_AUTH_STATE, STATE_FORBIDDEN)
        }
        editor.apply()
    }

    fun clearOpenAiCreditAuthState() {
        prefs.edit().remove(KEY_OPENAI_CREDIT_AUTH_STATE).apply()
    }

    /** Cancella ancora, cache, cooldown e stato di autenticazione del credito OpenAI. */
    fun clearOpenAiCredit() {
        prefs.edit()
            .remove(KEY_OPENAI_CREDIT_ANCHOR_USD)
            .remove(KEY_OPENAI_CREDIT_ANCHOR_TS)
            .remove(KEY_OPENAI_CREDIT_REAL_USD)
            .remove(KEY_OPENAI_CREDIT_REAL_GRANTED_USD)
            .remove(KEY_OPENAI_CREDIT_REAL_USED_USD)
            .remove(KEY_OPENAI_CREDIT_REAL_TS)
            .remove(KEY_OPENAI_CREDIT_FAILED_TS)
            .remove(KEY_OPENAI_CREDIT_AUTH_STATE)
            .apply()
    }

    private fun decimalOrNull(key: String): BigDecimal? =
        prefs.getString(key, null)?.toBigDecimalOrNull()

    /**
     * Una nuova key OpenAI puo' appartenere a un'altra organizzazione: invalida
     * stato auth, cooldown e cache reale. L'ancora manuale resta, e' un dato
     * inserito dall'utente e non dipende dalla key.
     */
    private fun resetOpenAiCreditForKeyChange() {
        prefs.edit()
            .remove(KEY_OPENAI_CREDIT_AUTH_STATE)
            .remove(KEY_OPENAI_CREDIT_FAILED_TS)
            .remove(KEY_OPENAI_CREDIT_REAL_USD)
            .remove(KEY_OPENAI_CREDIT_REAL_GRANTED_USD)
            .remove(KEY_OPENAI_CREDIT_REAL_USED_USD)
            .remove(KEY_OPENAI_CREDIT_REAL_TS)
            .apply()
    }

    // --- Chiavi prefs ----------------------------------------------------

    private fun ivKey(provider: Provider): String = when (provider) {
        Provider.DEEPSEEK -> KEY_IV
        Provider.OPENAI -> KEY_OPENAI_IV
    }

    private fun cipherKey(provider: Provider): String = when (provider) {
        Provider.DEEPSEEK -> KEY_CIPHERTEXT
        Provider.OPENAI -> KEY_OPENAI_CIPHERTEXT
    }

    private fun lastSuccessKey(provider: Provider): String = when (provider) {
        Provider.DEEPSEEK -> KEY_LAST_SUCCESS_TS
        Provider.OPENAI -> KEY_OPENAI_LAST_SUCCESS_TS
    }

    private fun lastAttemptKey(provider: Provider): String = when (provider) {
        Provider.DEEPSEEK -> KEY_LAST_ATTEMPT_TS
        Provider.OPENAI -> KEY_OPENAI_LAST_ATTEMPT_TS
    }

    private fun lastErrorKey(provider: Provider): String = when (provider) {
        Provider.DEEPSEEK -> KEY_LAST_ERROR
        Provider.OPENAI -> KEY_OPENAI_LAST_ERROR
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
        private const val KEY_BELOW_THRESHOLD = "below_threshold"

        private const val DEFAULT_THRESHOLD = "10.00"
        val ALLOWED_INTERVALS = intArrayOf(15, 30, 60)

        const val ERROR_MISSING_KEY = "missing_key"
        const val ERROR_AUTH = "auth"
        const val ERROR_FORBIDDEN = "forbidden"
        const val ERROR_NETWORK = "network"
        const val ERROR_UNEXPECTED = "unexpected"

        // Credito residuo OpenAI (v1.1.4).
        private const val KEY_OPENAI_CREDIT_ANCHOR_USD = "openai_credit_anchor_usd"
        private const val KEY_OPENAI_CREDIT_ANCHOR_TS = "openai_credit_anchor_ts"
        private const val KEY_OPENAI_CREDIT_REAL_USD = "openai_credit_real_usd"
        private const val KEY_OPENAI_CREDIT_REAL_GRANTED_USD =
            "openai_credit_real_granted_usd"
        private const val KEY_OPENAI_CREDIT_REAL_USED_USD = "openai_credit_real_used_usd"
        private const val KEY_OPENAI_CREDIT_REAL_TS = "openai_credit_real_ts"
        private const val KEY_OPENAI_CREDIT_FAILED_TS = "openai_credit_failed_ts"
        private const val KEY_OPENAI_CREDIT_AUTH_STATE = "openai_credit_auth_state"
        private const val STATE_UNAUTHORIZED = "unauthorized"
        private const val STATE_FORBIDDEN = "forbidden"

        /**
         * Eta' oltre la quale un valore reale e' considerato vecchio dalla UI.
         * Non e' una scadenza: il valore resta valido e viene comunque mostrato.
         */
        const val OPENAI_CREDIT_STALE_MS = 36L * 60L * 60L * 1000L

        /** Dopo un fallimento non si ritenta l'endpoint prima di 24h. */
        const val OPENAI_CREDIT_RETRY_MS = 24L * 60L * 60L * 1000L
    }
}
