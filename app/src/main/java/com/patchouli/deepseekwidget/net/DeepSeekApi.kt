package com.patchouli.deepseekwidget.net

import org.json.JSONObject
import java.io.IOException
import java.math.BigDecimal
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL

/**
 * Unica chiamata di rete dell'app: GET /user/balance.
 * La API key non viene mai loggata.
 */
class DeepSeekApi(
    private val connectTimeoutMs: Int = 15_000,
    private val readTimeoutMs: Int = 20_000
) {

    fun fetch(apiKey: String): ApiResult {
        var connection: HttpURLConnection? = null
        return try {
            connection = (URL(ENDPOINT).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = connectTimeoutMs
                readTimeout = readTimeoutMs
                setRequestProperty("Authorization", "Bearer $apiKey")
                setRequestProperty("Accept", "application/json")
                useCaches = false
                // La key non deve mai seguire un redirect verso un altro host.
                instanceFollowRedirects = false
            }

            val code = connection.responseCode
            if (code == 401 || code == 403) return ApiResult.Unauthorized
            if (code !in 200..299) return ApiResult.NetworkError("HTTP $code")

            val body = connection.inputStream.bufferedReader().use { it.readText() }
            parse(body)
        } catch (e: SocketTimeoutException) {
            ApiResult.NetworkError("timeout")
        } catch (e: IOException) {
            ApiResult.NetworkError("rete non disponibile")
        } catch (e: Exception) {
            ApiResult.Unexpected(e.javaClass.simpleName)
        } finally {
            connection?.disconnect()
        }
    }

    private fun parse(body: String): ApiResult {
        return try {
            val json = JSONObject(body)
            val infos = json.optJSONArray("balance_infos") ?: return ApiResult.Unexpected("balance_infos")
            if (infos.length() == 0) return ApiResult.Unexpected("balance_infos vuoto")

            // La soglia e' in USD: preferiamo l'entry USD se presente.
            var info = infos.optJSONObject(0) ?: return ApiResult.Unexpected("balance_infos")
            for (i in 0 until infos.length()) {
                val candidate = infos.optJSONObject(i) ?: continue
                if (candidate.optString("currency").equals("USD", ignoreCase = true)) {
                    info = candidate
                    break
                }
            }

            val total = info.optString("total_balance", "").toBigDecimalOrNull()
                ?: return ApiResult.Unexpected("total_balance")
            val granted = info.optString("granted_balance", "0").toBigDecimalOrNull()
                ?: BigDecimal.ZERO
            val topped = info.optString("topped_up_balance", "0").toBigDecimalOrNull()
                ?: BigDecimal.ZERO

            ApiResult.Success(
                Balance(
                    total = total,
                    granted = granted,
                    topped = topped,
                    currency = info.optString("currency", "USD"),
                    isAvailable = json.optBoolean("is_available", true)
                )
            )
        } catch (e: Exception) {
            ApiResult.Unexpected("JSON non valido")
        }
    }

    companion object {
        const val ENDPOINT = "https://api.deepseek.com/user/balance"
    }
}
