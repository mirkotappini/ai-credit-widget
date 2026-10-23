package com.patchouli.deepseekwidget.repo

import android.content.Context
import com.patchouli.deepseekwidget.data.Prefs
import com.patchouli.deepseekwidget.net.CostBucket
import com.patchouli.deepseekwidget.net.CreditResult
import com.patchouli.deepseekwidget.net.OpenAiCreditApi
import com.patchouli.deepseekwidget.provider.Provider
import java.math.BigDecimal
import java.time.Instant
import java.time.ZoneOffset

/** Origine del valore restituito da [OpenAiCreditRepository.current]. */
enum class CreditSource {
    /** Valore letto dall'endpoint; non scade, [CreditInfo.asOfTs] ne dichiara l'eta'. */
    REAL,

    /** Ancora manuale meno i costi OpenAI dei giorni successivi a quello dell'ancora (UTC). */
    ESTIMATE,

    /** Nessuna informazione disponibile. */
    NONE
}

/**
 * Migliore informazione disponibile sul credito residuo OpenAI.
 * [availableUsd] e' null solo con [CreditSource.NONE]. Il valore stimato puo'
 * essere negativo e non viene clampato: la UI decide come mostrarlo.
 *
 * Con [CreditSource.REAL] il valore resta valido anche quando e' vecchio: la UI
 * confronta [asOfTs] con [Prefs.OPENAI_CREDIT_STALE_MS] per la caption di eta'.
 *
 * La stima non scala la giornata UTC dell'ancora: la spesa parziale di quel
 * giorno e' gia' compresa nel valore letto dall'utente, quindi scalarla la
 * conterebbe due volte. Si sottraggono solo i giorni interamente successivi.
 */
data class CreditInfo(
    val source: CreditSource,
    val availableUsd: BigDecimal?,
    val grantedUsd: BigDecimal? = null,
    val usedUsd: BigDecimal? = null,
    /** Quando il dato e' stato letto (REAL) o calcolato (ESTIMATE/NONE), epoch ms. */
    val asOfTs: Long = 0L,
    /** Solo ESTIMATE: epoch ms della data di riferimento scelta dall'utente. */
    val anchorTs: Long? = null
)

/** Come trattare stato di autenticazione e cooldown in [OpenAiCreditRepository.refresh]. */
enum class OpenAiCreditRefreshMode {
    /** Refresh automatico: rispetta stato auth e cooldown di 24h. */
    NORMAL,

    /** Azione esplicita dell'utente: ignora cooldown e stato auth, ma richiede la key. */
    FORCE
}

/**
 * Calcolo puro della stima: ancora manuale meno i costi OpenAI con
 * `startTsUtc` >= mezzanotte UTC del giorno **successivo** all'ancora.
 *
 * L'ancora viene letta dall'utente a metà giornata: il bucket del giorno
 * dell'ancora contiene anche la spesa gia' inclusa in quel valore, quindi non
 * va scalato. Ignorando la spesa parziale del giorno di riferimento l'errore
 * massimo e' di una giornata, si autocorregge dal giorno dopo e la stima non
 * risulta mai piu' bassa del vero per un errore di modello.
 * Separato dal repository per essere testabile su JVM.
 */
object OpenAiCreditEstimator {

    fun estimateUsd(
        anchorUsd: BigDecimal,
        anchorTs: Long,
        buckets: List<CostBucket>
    ): BigDecimal {
        val since = utcNextMidnightSec(anchorTs)
        var spent = BigDecimal.ZERO
        for (bucket in buckets) {
            if (bucket.startTsUtc >= since) spent = spent.add(bucket.amount)
        }
        // Puo' essere negativo: non si clampa qui, decide la UI.
        return anchorUsd.subtract(spent)
    }

    /** Mezzanotte UTC (epoch secondi) del giorno che contiene [ts]. */
    fun utcMidnightSec(ts: Long): Long =
        Instant.ofEpochMilli(ts)
            .atZone(ZoneOffset.UTC)
            .toLocalDate()
            .atStartOfDay(ZoneOffset.UTC)
            .toInstant()
            .epochSecond

    /** Mezzanotte UTC (epoch secondi) del giorno successivo a quello di [ts]. */
    fun utcNextMidnightSec(ts: Long): Long = utcMidnightSec(ts) + SECONDS_PER_DAY

    /**
     * L'ancora e' utilizzabile solo se non e' nel futuro e il suo giorno UTC non
     * e' piu' vecchio di 30 giorni: fuori da questa finestra la stima non viene
     * mostrata. Si confrontano i giorni UTC, non le 720 ore esatte, perche' il
     * riferimento e' una data scelta dall'utente.
     */
    fun isAnchorUsable(anchorTs: Long, now: Long): Boolean {
        if (anchorTs <= 0L || anchorTs > now) return false
        val anchorDay = utcMidnightSec(anchorTs)
        val today = utcMidnightSec(now)
        return today - anchorDay <= ANCHOR_MAX_AGE_DAYS * SECONDS_PER_DAY
    }

    private const val SECONDS_PER_DAY = 86_400L
    private const val ANCHOR_MAX_AGE_DAYS = 30L
}

/**
 * Credito residuo OpenAI.
 *
 * - [refresh] interroga l'endpoint legacy solo con una key presente, senza
 *   stato 401/403 pendente e fuori dal cooldown di 24h; salva cache e stato.
 * - [current] preferisce il valore reale in cache (anche vecchio: l'eta' e' in
 *   [CreditInfo.asOfTs]), poi la stima dall'ancora manuale valida, altrimenti
 *   NONE. Non scrive sul DB e non modifica i bucket.
 *
 * [refresh] non chiama mai [Prefs.setLastError]: i fallimenti del credito non
 * accendono banner e non cambiano l'esito di "Testa connessione". 401/403
 * restano solo stato interno per sopprimere i retry.
 *
 * Nessuna eccezione risale al chiamante e la key non viene mai loggata.
 */
class OpenAiCreditRepository(
    context: Context,
    private val api: OpenAiCreditApi = OpenAiCreditApi(),
    private val costsRepository: OpenAiCostsRepository =
        OpenAiCostsRepository(context.applicationContext),
    private val clock: () -> Long = System::currentTimeMillis
) {

    private val prefs = Prefs(context.applicationContext)

    /** Stesso limite del parser: la cache non deve contenere valori fuori scala. */
    private val maxRealUsd = BigDecimal("10000000")

    fun refresh(
        mode: OpenAiCreditRefreshMode = OpenAiCreditRefreshMode.NORMAL
    ): CreditResult {
        return try {
            val now = clock()
            val adminKey = prefs.getApiKey(Provider.OPENAI)
            if (adminKey.isNullOrBlank()) {
                return CreditResult.Unavailable("key OpenAI assente")
            }

            if (mode == OpenAiCreditRefreshMode.NORMAL) {
                when (prefs.getOpenAiCreditAuthState()) {
                    Prefs.CreditAuthState.UNAUTHORIZED -> return CreditResult.Unauthorized
                    Prefs.CreditAuthState.FORBIDDEN -> return CreditResult.Forbidden
                    Prefs.CreditAuthState.NONE -> Unit
                }
                if (!prefs.shouldRetryOpenAiCredit(now)) {
                    return CreditResult.Unavailable(
                        "endpoint non disponibile: riprovo piu' tardi"
                    )
                }
            }

            val result = api.fetch(adminKey)
            // Difesa in profondita': il parser gia' valida, ma un Success non
            // plausibile non deve mai finire nella cache reale.
            if (result is CreditResult.Success && !isValidRealUsd(result.availableUsd)) {
                prefs.setOpenAiCreditFailedTs(now)
                return CreditResult.Unavailable("formato inatteso")
            }
            when (result) {
                is CreditResult.Success -> {
                    prefs.setOpenAiCreditReal(
                        availableUsd = result.availableUsd,
                        grantedUsd = result.grantedUsd,
                        usedUsd = result.usedUsd,
                        ts = now
                    )
                    prefs.clearOpenAiCreditFailed()
                    prefs.clearOpenAiCreditAuthState()
                }

                CreditResult.Unauthorized -> {
                    // Solo stato interno: nessun setLastError, nessun banner,
                    // nessun effetto su "Testa connessione".
                    prefs.setOpenAiCreditAuthState(Prefs.CreditAuthState.UNAUTHORIZED)
                }

                CreditResult.Forbidden -> {
                    prefs.setOpenAiCreditAuthState(Prefs.CreditAuthState.FORBIDDEN)
                }

                is CreditResult.Unavailable -> prefs.setOpenAiCreditFailedTs(now)
                CreditResult.NetworkError -> prefs.setOpenAiCreditFailedTs(now)
            }
            result
        } catch (_: Exception) {
            CreditResult.Unavailable("errore interno")
        }
    }

    fun current(now: Long = clock()): CreditInfo {
        return try {
            val realUsd = prefs.getOpenAiCreditRealUsd()
            val realTs = prefs.getOpenAiCreditRealTs()
            // Nessuna scadenza: se la cache esiste si mostra sempre, con asOfTs.
            if (isValidRealUsd(realUsd) && realTs > 0L) {
                return CreditInfo(
                    source = CreditSource.REAL,
                    availableUsd = realUsd,
                    grantedUsd = prefs.getOpenAiCreditRealGrantedUsd(),
                    usedUsd = prefs.getOpenAiCreditRealUsedUsd(),
                    asOfTs = realTs
                )
            }

            val anchorUsd = prefs.getOpenAiCreditAnchorUsd()
            val anchorTs = prefs.getOpenAiCreditAnchorTs()
            if (anchorUsd != null && OpenAiCreditEstimator.isAnchorUsable(anchorTs, now)) {
                // Una sola fonte di verita': l'estimatore filtra dalla mezzanotte
                // successiva; qui si limita la query allo stesso istante.
                val since = OpenAiCreditEstimator.utcNextMidnightSec(anchorTs)
                val estimate = OpenAiCreditEstimator.estimateUsd(
                    anchorUsd = anchorUsd,
                    anchorTs = anchorTs,
                    buckets = costsRepository.dailyBucketsSince(since)
                )
                return CreditInfo(
                    source = CreditSource.ESTIMATE,
                    availableUsd = estimate,
                    asOfTs = now,
                    anchorTs = anchorTs
                )
            }

            CreditInfo(source = CreditSource.NONE, availableUsd = null, asOfTs = now)
        } catch (_: Exception) {
            CreditInfo(source = CreditSource.NONE, availableUsd = null, asOfTs = now)
        }
    }

    private fun isValidRealUsd(value: BigDecimal?): Boolean =
        value != null && value.signum() >= 0 && value <= maxRealUsd
}
