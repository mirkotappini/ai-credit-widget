package com.patchouli.deepseekwidget.net

import org.json.JSONObject
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Instant

/**
 * Parser puro di `GET /v1/organizations/cost_report`.
 *
 * Anthropic restituisce `amount` come stringa decimale in centesimi: va
 * divisa per 100 e conservata con scala 6 (arrotondamento a 2 solo in UI).
 * Piu' results nello stesso bucket sono breakdown e vanno sommati.
 */
object AnthropicCostsParser {

    private const val USD = "USD"
    private val CENTS = BigDecimal("100")
    private const val AMOUNT_SCALE = 6

    fun parsePage(body: String): CostPage {
        val json = JSONObject(body)
        val data = json.optJSONArray("data")
            ?: throw IllegalArgumentException("campo data assente")

        val buckets = ArrayList<CostBucket>(data.length())
        var skipped = 0

        for (i in 0 until data.length()) {
            val bucketJson = data.optJSONObject(i) ?: continue
            val start = bucketJson.optString("starting_at", "")
                .let { parseInstant(it) } ?: continue
            val end = bucketJson.optString("ending_at", "")
                .let { parseInstant(it) } ?: continue
            if (end <= start) continue

            val results = bucketJson.optJSONArray("results")
            var amount = BigDecimal.ZERO
            if (results != null) {
                for (r in 0 until results.length()) {
                    val result = results.optJSONObject(r) ?: continue
                    if (!result.has("amount") || result.isNull("amount")) {
                        skipped++
                        continue
                    }
                    val rawCurrency = result.optString("currency", USD)
                    if (!rawCurrency.equals(USD, ignoreCase = true)) {
                        skipped++
                        continue
                    }
                    val rawAmount = result.opt("amount")
                    val cents = when (rawAmount) {
                        is Number -> rawAmount.toString().toBigDecimalOrNull()
                        is String -> rawAmount.toBigDecimalOrNull()
                        else -> null
                    }
                    if (cents == null) {
                        skipped++
                        continue
                    }
                    val dollars = cents.divide(CENTS, AMOUNT_SCALE, RoundingMode.HALF_UP)
                    amount = amount.add(dollars)
                }
            }

            buckets.add(
                CostBucket(
                    startTsUtc = start,
                    endTsUtc = end,
                    amount = amount.setScale(AMOUNT_SCALE, RoundingMode.HALF_UP),
                    currency = ProviderCurrency.USD
                )
            )
        }

        val nextPage = json.optString("next_page", "")
            .takeIf { it.isNotBlank() && !it.equals("null", true) }
        return CostPage(buckets = buckets, nextPage = nextPage, skipped = skipped)
    }

    private fun parseInstant(value: String): Long? =
        runCatching { Instant.parse(value).epochSecond }.getOrNull()

    /** Evita stringhe sparse nel modello senza cambiare il tipo pubblico. */
    private object ProviderCurrency {
        const val USD = "USD"
    }
}
