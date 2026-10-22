package com.patchouli.deepseekwidget.provider

/** Tipo di metrica esposta dal provider. */
enum class MetricType {
    /** Saldo residuo (DeepSeek /user/balance). */
    BALANCE,

    /** Solo costo aggregato (OpenAI organization costs). */
    COST
}

/**
 * Provider supportati. DeepSeek resta il default per compatibilita' con widget
 * e schermate esistenti.
 */
enum class Provider(
    val label: String,
    val metricType: MetricType,
    val currency: String
) {
    DEEPSEEK("DeepSeek", MetricType.BALANCE, "USD"),
    OPENAI("OpenAI", MetricType.COST, "USD"),
    CLAUDE("Claude", MetricType.COST, "USD");

    /** Valore persistito in prefs/DB: nome lowercase stabile. */
    val storageKey: String get() = name.lowercase()

    companion object {
        fun fromStorage(raw: String?): Provider =
            entries.firstOrNull { it.name.equals(raw, ignoreCase = true) } ?: DEEPSEEK
    }
}
