package com.patchouli.deepseekwidget.net

import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener
import java.math.BigDecimal

/**
 * Parser puro delle risposte di `credit_grants`, separato dal client HTTP per
 * essere testabile su JVM.
 *
 * Formati accettati:
 * - riepilogo: `{"total_available":12.34,"total_granted":18,"total_used":5.66}`;
 * - elenco: `{"data":[{"grant_amount":10,"used_amount":2.5}, ...]}`, sommando
 *   `grant_amount - used_amount` di ogni grant;
 * - array nudo di grant, per tolleranza verso varianti dell'endpoint.
 *
 * Gli importi possono essere numeri JSON o stringhe (anche con prefisso `$`).
 * Il valore disponibile deve essere >= 0 e <= 10.000.000; una currency esposta
 * diversa da USD fa scartare tutta la risposta. HTML, body vuoto, JSON senza
 * campi utili e valori fuori scala producono [CreditResult.Unavailable] con
 * dettaglio `formato inatteso`: il parser non lancia mai eccezioni.
 */
object OpenAiCreditParser {

    private const val UNEXPECTED = "formato inatteso"
    private const val TOTAL_AVAILABLE = "total_available"
    private const val TOTAL_GRANTED = "total_granted"
    private const val TOTAL_USED = "total_used"
    private const val GRANT_AMOUNT = "grant_amount"
    private const val USED_AMOUNT = "used_amount"
    private const val CURRENCY = "currency"
    private const val USD = "usd"

    /** Limite difensivo concordato con la spec per il credito reale. */
    private val MAX_USD = BigDecimal("10000000")

    fun parse(body: String): CreditResult {
        if (body.isBlank()) return unavailable()

        val value = try {
            JSONTokener(body).nextValue()
        } catch (_: Exception) {
            return unavailable()
        }

        return try {
            when (value) {
                is JSONObject -> parseObject(value)
                is JSONArray -> parseGrants(value)
                else -> unavailable()
            }
        } catch (_: Exception) {
            unavailable()
        }
    }

    private fun parseObject(json: JSONObject): CreditResult {
        if (!currencyIsUsd(json)) return unavailable()

        val available = json.amount(TOTAL_AVAILABLE)
        val granted = json.amount(TOTAL_GRANTED)
        val used = json.amount(TOTAL_USED)

        if (available != null) {
            return success(available, granted, used)
        }
        // Riepilogo senza total_available: se ci sono entrambi i totali si puo'
        // ricavare il disponibile senza inventare valori.
        if (granted != null && used != null) {
            return success(granted.subtract(used), granted, used)
        }

        val data = json.optJSONArray("data")
        if (data != null) return parseGrants(data)

        // Variante in cui l'oggetto rappresenta un singolo grant.
        val singleGrant = json.amount(GRANT_AMOUNT)
        if (singleGrant != null) {
            val singleUsed = if (json.has(USED_AMOUNT) && !json.isNull(USED_AMOUNT)) {
                json.amount(USED_AMOUNT) ?: return unavailable()
            } else {
                BigDecimal.ZERO
            }
            return success(singleGrant.subtract(singleUsed), singleGrant, singleUsed)
        }

        return unavailable()
    }

    private fun parseGrants(data: JSONArray): CreditResult {
        var granted = BigDecimal.ZERO
        var used = BigDecimal.ZERO
        var valid = 0

        for (i in 0 until data.length()) {
            val grant = data.optJSONObject(i) ?: continue
            // Valuta diversa da USD: si scarta tutta la risposta, non solo il
            // singolo grant, per non mescolare valute.
            if (!currencyIsUsd(grant)) return unavailable()
            // Un grant senza importo non e' utilizzabile; uno con used_amount
            // malformato viene scartato per non sottostimare il consumo.
            val grantAmount = grant.amount(GRANT_AMOUNT) ?: continue
            val usedAmount = if (grant.has(USED_AMOUNT) && !grant.isNull(USED_AMOUNT)) {
                grant.amount(USED_AMOUNT) ?: continue
            } else {
                BigDecimal.ZERO
            }
            granted = granted.add(grantAmount)
            used = used.add(usedAmount)
            valid++
        }

        if (valid == 0) return unavailable()
        return success(granted.subtract(used), granted, used)
    }

    /**
     * Il valore reale deve essere un importo USD plausibile: numerico, >= 0 e
     * <= 10.000.000. Un valore fuori scala viene scartato senza cache, cosi'
     * un errore dell'endpoint non diventa un numero mostrato in Dashboard.
     */
    private fun success(
        available: BigDecimal,
        granted: BigDecimal?,
        used: BigDecimal?
    ): CreditResult {
        if (available.signum() < 0 || available > MAX_USD) return unavailable()
        return CreditResult.Success(available, granted, used)
    }

    /** Currency assente o null e' ammessa; se presente deve essere USD. */
    private fun currencyIsUsd(json: JSONObject): Boolean {
        if (!json.has(CURRENCY) || json.isNull(CURRENCY)) return true
        val raw = json.opt(CURRENCY) as? String ?: return false
        return raw.trim().equals(USD, ignoreCase = true)
    }

    private fun JSONObject.amount(name: String): BigDecimal? {
        if (!has(name) || isNull(name)) return null
        return parseAmount(opt(name))
    }

    private fun parseAmount(value: Any?): BigDecimal? = when (value) {
        is Number -> runCatching { BigDecimal(value.toString()) }.getOrNull()
        is String -> value.trim()
            .removePrefix("$")
            .trim()
            .toBigDecimalOrNull()
        else -> null
    }

    private fun unavailable(): CreditResult.Unavailable =
        CreditResult.Unavailable(UNEXPECTED)
}
