package com.patchouli.deepseekwidget.net

import java.math.BigDecimal

/**
 * Bucket di costo restituito dall'API organizzazione di OpenAI.
 * Gli estremi sono epoch in secondi, allineati al bucket richiesto (1d o 1h), in UTC.
 */
data class CostBucket(
    val startTsUtc: Long,
    val endTsUtc: Long,
    val amount: BigDecimal,
    val currency: String
)

/** Pagina di risultati, con cursore per la pagina successiva. */
data class CostPage(
    val buckets: List<CostBucket>,
    val nextPage: String?,
    /** Quanti risultati sono stati scartati: amount assente o valuta diversa da USD. */
    val skipped: Int
)

sealed class CostsResult {
    data class Success(val buckets: List<CostBucket>) : CostsResult()
    data object MissingKey : CostsResult()
    /** 401: la key non e' valida. */
    data object Unauthorized : CostsResult()
    /** 403: key valida ma senza scope api.usage.read (serve una Admin key). */
    data object Forbidden : CostsResult()
    data class NetworkError(val detail: String) : CostsResult()
    data class Unexpected(val detail: String) : CostsResult()
}
