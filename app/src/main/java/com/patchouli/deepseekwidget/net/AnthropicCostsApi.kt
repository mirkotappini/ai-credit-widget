package com.patchouli.deepseekwidget.net

import java.io.IOException
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import java.net.URLEncoder

/**
 * Client per i costi dell'organizzazione Claude.
 *
 * Serve una Admin key di un'organizzazione Claude Console: le key individuali
 * e le workspace key non leggono i costi. La key non viene mai loggata.
 */
class AnthropicCostsApi(
    private val endpoint: String = DEFAULT_ENDPOINT,
    private val connectTimeoutMs: Int = 15_000,
    private val readTimeoutMs: Int = 20_000,
    private val maxPages: Int = 12
) {

    /**
     * [startingAt] e' RFC3339 (es. 2026-10-01T00:00:00Z). Solo granularita'
     * giornaliera UTC: [bucketWidth] e' sempre "1d".
     */
    fun fetch(
        adminKey: String,
        startingAt: String,
        limit: Int = 31,
        bucketWidth: String = "1d"
    ): CostsResult {
        val all = ArrayList<CostBucket>()
        var page: String? = null
        var pages = 0

        while (pages < maxPages) {
            val url = buildUrl(startingAt, bucketWidth, limit, page)
            val response = request(url, adminKey)
            when (response) {
                is HttpOutcome.Failure -> return response.result
                is HttpOutcome.Body -> {
                    val parsed = try {
                        AnthropicCostsParser.parsePage(response.text)
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
                setRequestProperty("X-Api-Key", adminKey)
                setRequestProperty("anthropic-version", ANTHROPIC_VERSION)
                setRequestProperty("Accept", "application/json")
                useCaches = false
                instanceFollowRedirects = false
            }

            when (val code = connection.responseCode) {
                401 -> HttpOutcome.Failure(CostsResult.Unauthorized)
                403 -> HttpOutcome.Failure(CostsResult.Forbidden)
                in 200..299 -> HttpOutcome.Body(
                    connection.inputStream.bufferedReader().use { it.readText() }
                )
                else -> HttpOutcome.Failure(CostsResult.Unexpected("HTTP $code"))
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

    private fun buildUrl(
        startingAt: String,
        bucketWidth: String,
        limit: Int,
        page: String?
    ): String {
        val params = StringBuilder()
            .append("starting_at=").append(encode(startingAt))
            .append("&bucket_width=").append(encode(bucketWidth))
            .append("&limit=").append(limit.coerceIn(1, 31))
        if (page != null) params.append("&page=").append(encode(page))
        return "$endpoint?$params"
    }

    private fun encode(value: String): String = URLEncoder.encode(value, "UTF-8")

    companion object {
        const val DEFAULT_ENDPOINT = "https://api.anthropic.com/v1/organizations/cost_report"
        const val ANTHROPIC_VERSION = "2023-06-01"
    }
}
