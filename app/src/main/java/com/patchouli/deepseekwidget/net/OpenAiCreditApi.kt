package com.patchouli.deepseekwidget.net

import java.io.IOException
import java.math.BigDecimal
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL

/**
 * Esito della lettura del credito residuo OpenAI.
 *
 * Distinguere 401 e 403 serve alla UI: con una project key (`sk-proj-...`)
 * l'endpoint risponde 403 e non ha senso riprovare a ogni refresh, mentre una
 * key invalida (401) richiede di essere sostituita.
 */
sealed class CreditResult {
    data class Success(
        val availableUsd: BigDecimal,
        val grantedUsd: BigDecimal?,
        val usedUsd: BigDecimal?
    ) : CreditResult()

    /** 401: la key non e' valida. */
    data object Unauthorized : CreditResult()

    /** 403: key valida ma senza i permessi necessari (serve una Admin key). */
    data object Forbidden : CreditResult()

    /** Endpoint assente, risposta non riconosciuta o errore HTTP diverso da 401/403/404. */
    data class Unavailable(val detail: String) : CreditResult()

    /** Timeout o rete non disponibile. */
    data object NetworkError : CreditResult()

    companion object {
        /** Limite concordato per i dettagli mostrabili in UI. */
        const val MAX_DETAIL = 120
    }
}

/**
 * Client del credito residuo OpenAI.
 *
 * L'endpoint storico `dashboard/billing/credit_grants` e' stato ritirato per
 * alcune organizzazioni ma risponde ancora ad altre; se manca (404) si ritenta
 * una sola volta sul percorso `/v1`. Non segue redirect, cosi' la key non viene
 * inoltrata ad altri host, e non logga mai la key.
 */
class OpenAiCreditApi(
    private val endpoint: String = DEFAULT_ENDPOINT,
    private val fallbackEndpoint: String = FALLBACK_ENDPOINT,
    private val connectTimeoutMs: Int = 15_000,
    private val readTimeoutMs: Int = 20_000
) {

    fun fetch(adminKey: String): CreditResult {
        return try {
            when (val first = request(endpoint, adminKey)) {
                is HttpOutcome.Body -> parse(first.text)
                is HttpOutcome.Failure -> first.result
                HttpOutcome.NotFound -> fetchFallback(adminKey)
            }
        } catch (_: Exception) {
            // Nessuna eccezione deve risalire al chiamante.
            unavailable("errore inatteso")
        }
    }

    private fun fetchFallback(adminKey: String): CreditResult {
        if (fallbackEndpoint == endpoint) return unavailable("HTTP 404")
        return when (val second = request(fallbackEndpoint, adminKey)) {
            is HttpOutcome.Body -> parse(second.text)
            is HttpOutcome.Failure -> second.result
            HttpOutcome.NotFound -> unavailable("HTTP 404")
        }
    }

    private fun parse(body: String): CreditResult = try {
        OpenAiCreditParser.parse(body)
    } catch (_: Exception) {
        unavailable("formato inatteso")
    }

    private sealed class HttpOutcome {
        data class Body(val text: String) : HttpOutcome()
        data class Failure(val result: CreditResult) : HttpOutcome()
        data object NotFound : HttpOutcome()
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
                401 -> HttpOutcome.Failure(CreditResult.Unauthorized)
                403 -> HttpOutcome.Failure(CreditResult.Forbidden)
                404 -> HttpOutcome.NotFound
                in 200..299 -> HttpOutcome.Body(
                    connection.inputStream.bufferedReader().use { it.readText() }
                )
                else -> HttpOutcome.Failure(unavailable("HTTP $code"))
            }
        } catch (e: SocketTimeoutException) {
            HttpOutcome.Failure(CreditResult.NetworkError)
        } catch (e: IOException) {
            HttpOutcome.Failure(CreditResult.NetworkError)
        } catch (e: Exception) {
            HttpOutcome.Failure(unavailable("errore inatteso"))
        } finally {
            connection?.disconnect()
        }
    }

    private fun unavailable(detail: String): CreditResult.Unavailable =
        CreditResult.Unavailable(detail.take(CreditResult.MAX_DETAIL))

    companion object {
        const val DEFAULT_ENDPOINT =
            "https://api.openai.com/dashboard/billing/credit_grants"
        const val FALLBACK_ENDPOINT =
            "https://api.openai.com/v1/dashboard/billing/credit_grants"
    }
}
