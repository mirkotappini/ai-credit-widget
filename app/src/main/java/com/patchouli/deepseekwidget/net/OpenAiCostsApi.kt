package com.patchouli.deepseekwidget.net

import java.io.IOException
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import java.net.URLEncoder

/**
 * Client per i costi dell'organizzazione OpenAI.
 *
 * Unica fonte possibile: OpenAI non espone il saldo residuo, solo i costi.
 * Richiede una Admin key (`sk-admin-...`) con scope `api.usage.read`: una
 * project key (`sk-proj-...`) risponde 403 con lo scope mancante, che qui
 * diventa [CostsResult.Forbidden] per distinguerlo da una key invalida.
 *
 * L'endpoint accetta **solo** `bucket_width=1d`: con `1h` risponde 400, quindi la
 * larghezza del bucket non e' un parametro (e' fissata in [buildUrl]).
 *
 * La key non viene mai loggata.
 */
class OpenAiCostsApi(
    private val endpoint: String = DEFAULT_ENDPOINT,
    private val connectTimeoutMs: Int = 15_000,
    private val readTimeoutMs: Int = 20_000,
    private val maxPages: Int = 12
) {

    /**
     * Scarica i bucket di costo giornalieri (UTC) a partire da [startTimeSec]
     * (epoch secondi). [limit] e' il numero massimo di bucket per pagina.
     */
    fun fetch(
        adminKey: String,
        startTimeSec: Long,
        limit: Int? = null
    ): CostsResult {
        val all = ArrayList<CostBucket>()
        var page: String? = null
        var pages = 0

        while (pages < maxPages) {
            val url = buildUrl(startTimeSec, limit, page)
            val response = request(url, adminKey)
            when (response) {
                is HttpOutcome.Failure -> return response.result
                is HttpOutcome.Body -> {
                    val parsed = try {
                        OpenAiCostsParser.parsePage(response.text)
                    } catch (e: Exception) {
                        return CostsResult.Unexpected("risposta inattesa: ${e.javaClass.simpleName}")
                    }
                    all.addAll(parsed.buckets)
                    pages++
                    val next = parsed.nextPage
                    if (next == null || next == page) break
                    page = next
                }
            }
        }

        return CostsResult.Success(all)
    }

    private sealed class HttpOutcome {
        data class Body(val text: String) : HttpOutcome()
        data class Failure(val result: CostsResult) : HttpOutcome()
    }

    private fun request(url: String, adminKey: String): HttpOutcome {
        var connection: HttpURLConnection? = null
        return try {
            connection = (URL(url).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = connectTimeoutMs
                readTimeout = readTimeoutMs
                setRequestProperty("Authorization", "Bearer $adminKey")
                setRequestProperty("Accept", "application/json")
                useCaches = false
                // La key non deve seguire un redirect verso un altro host.
                instanceFollowRedirects = false
            }

            when (val code = connection.responseCode) {
                401 -> HttpOutcome.Failure(CostsResult.Unauthorized)
                403 -> HttpOutcome.Failure(CostsResult.Forbidden)
                in 200..299 -> HttpOutcome.Body(
                    connection.inputStream.bufferedReader().use { it.readText() }
                )
                else -> HttpOutcome.Failure(
                    CostsResult.Unexpected(
                        OpenAiCostsParser.errorDetail(
                            code,
                            connection.errorStream?.bufferedReader()?.use { it.readText() }
                        )
                    )
                )
            }
        } catch (e: SocketTimeoutException) {
            HttpOutcome.Failure(CostsResult.NetworkError("timeout"))
        } catch (e: IOException) {
            HttpOutcome.Failure(CostsResult.NetworkError("rete non disponibile"))
        } catch (e: Exception) {
            HttpOutcome.Failure(CostsResult.Unexpected(e.javaClass.simpleName))
        } finally {
            connection?.disconnect()
        }
    }

    private fun buildUrl(startTimeSec: Long, limit: Int?, page: String?): String {
        val params = StringBuilder()
            .append("start_time=").append(startTimeSec)
            .append("&bucket_width=1d")
        if (limit != null) params.append("&limit=").append(limit)
        if (page != null) params.append("&page=").append(encode(page))
        return "$endpoint?$params"
    }

    private fun encode(value: String): String = URLEncoder.encode(value, "UTF-8")

    companion object {
        const val DEFAULT_ENDPOINT = "https://api.openai.com/v1/organization/costs"
    }
}
