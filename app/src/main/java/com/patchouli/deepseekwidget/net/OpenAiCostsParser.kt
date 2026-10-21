package com.patchouli.deepseekwidget.net

import org.json.JSONObject
import java.math.BigDecimal

/**
 * Parser puro della risposta `GET /v1/organization/costs`.
 * Separato dal client HTTP per essere testabile su JVM.
 *
 * Regole:
 * - un bucket con `results` vuoto o assente produce un bucket a zero solo se il
 *   bucket e' presente: i giorni senza spesa non devono sparire dal grafico;
 * - piu' `results` nello stesso bucket vengono sommati;
 * - `amount` assente, nullo o non numerico viene scartato (conteggiato in `skipped`);
 * - valuta diversa da USD viene scartata e conteggiata, per non sommare valute diverse.
 */
object OpenAiCostsParser {

    private const val USD = "usd"

    fun parsePage(body: String): CostPage {
        val json = JSONObject(body)
        val data = json.optJSONArray("data")
            ?: throw IllegalArgumentException("campo data assente")

        val buckets = ArrayList<CostBucket>(data.length())
        var skipped = 0

        for (i in 0 until data.length()) {
            val bucketJson = data.optJSONObject(i) ?: continue
            val start = bucketJson.optLong("start_time", -1L)
            val end = bucketJson.optLong("end_time", -1L)
            if (start < 0L || end <= start) continue

            val results = bucketJson.optJSONArray("results")
            var amount = BigDecimal.ZERO
            var currency = USD

            if (results != null) {
                for (r in 0 until results.length()) {
                    val result = results.optJSONObject(r) ?: continue
                    val amountJson = result.optJSONObject("amount")
                    if (amountJson == null) {
                        skipped++
                        continue
                    }
                    val rawCurrency = amountJson.optString("currency", USD)
                    if (!rawCurrency.equals(USD, ignoreCase = true)) {
                        skipped++
                        continue
                    }
                    val value = amountJson.opt("value")
                    val parsed = when (value) {
                        is Number -> BigDecimal(value.toString())
                        is String -> value.toBigDecimalOrNull()
                        else -> null
                    }
                    if (parsed == null) {
                        skipped++
                        continue
                    }
                    amount = amount.add(parsed)
                    currency = USD
                }
            }

            buckets.add(CostBucket(startTsUtc = start, endTsUtc = end, amount = amount, currency = currency))
        }

        val nextPage = json.optString("next_page", "").takeIf { it.isNotBlank() && !it.equals("null", true) }
        return CostPage(buckets = buckets, nextPage = nextPage, skipped = skipped)
    }
}
