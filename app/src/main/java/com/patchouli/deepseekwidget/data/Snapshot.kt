package com.patchouli.deepseekwidget.data

import java.math.BigDecimal

/**
 * Uno snapshot del saldo DeepSeek, persistito localmente.
 * I valori decimali sono conservati come stringhe nel DB per non perdere precisione.
 */
data class Snapshot(
    val id: Long,
    val ts: Long,
    val total: BigDecimal,
    val granted: BigDecimal,
    val topped: BigDecimal
)
