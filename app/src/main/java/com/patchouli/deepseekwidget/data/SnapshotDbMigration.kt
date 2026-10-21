package com.patchouli.deepseekwidget.data

/**
 * SQL di schema/upgrade del database locale, isolato da Android per poterlo
 * verificare nei test JVM.
 */
object SnapshotDbMigration {

    const val VERSION = 3

    const val SNAPSHOTS_TABLE = "snapshots"
    const val COST_BUCKETS_TABLE = "cost_buckets"

    val CREATE_SNAPSHOTS: String =
        """
        CREATE TABLE $SNAPSHOTS_TABLE (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            ts INTEGER NOT NULL,
            total TEXT NOT NULL,
            granted TEXT NOT NULL,
            topped TEXT NOT NULL,
            provider TEXT NOT NULL DEFAULT 'deepseek'
        )
        """.trimIndent()

    val CREATE_COST_BUCKETS: String =
        """
        CREATE TABLE IF NOT EXISTS $COST_BUCKETS_TABLE (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            provider TEXT NOT NULL,
            start_ts_utc INTEGER NOT NULL,
            end_ts_utc INTEGER NOT NULL,
            amount_text TEXT NOT NULL,
            currency TEXT NOT NULL
        )
        """.trimIndent()

    val COST_BUCKETS_UNIQUE_INDEX: String =
        "CREATE UNIQUE INDEX IF NOT EXISTS idx_cost_buckets_provider_start " +
            "ON $COST_BUCKETS_TABLE(provider, start_ts_utc, end_ts_utc)"

    /** Statement eseguiti su un database nuovo (versione corrente). */
    fun createStatements(): List<String> = listOf(
        CREATE_SNAPSHOTS,
        "CREATE INDEX IF NOT EXISTS idx_snapshots_ts ON $SNAPSHOTS_TABLE(ts)",
        CREATE_COST_BUCKETS,
        COST_BUCKETS_UNIQUE_INDEX
    )

    /**
     * Statement di upgrade per una versione precedente.
     * - v1: aggiunge la colonna provider agli snapshot e crea cost_buckets.
     * - v2: ricrea cost_buckets preservando le righe, per il nuovo vincolo
     *   univoco (provider, start_ts_utc, end_ts_utc). Senza end_ts il bucket 1d
     *   sovrascriveva quello orario delle 00:00 UTC.
     */
    fun upgradeStatements(oldVersion: Int): List<String> {
        if (oldVersion >= VERSION) return emptyList()
        val statements = mutableListOf<String>()
        if (oldVersion < 2) {
            statements +=
                "ALTER TABLE $SNAPSHOTS_TABLE ADD COLUMN provider TEXT NOT NULL DEFAULT 'deepseek'"
            statements += CREATE_COST_BUCKETS
            statements += COST_BUCKETS_UNIQUE_INDEX
        } else if (oldVersion < 3) {
            statements += "DROP TABLE IF EXISTS ${COST_BUCKETS_TABLE}_new"
            statements += """
                CREATE TABLE ${COST_BUCKETS_TABLE}_new (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    provider TEXT NOT NULL,
                    start_ts_utc INTEGER NOT NULL,
                    end_ts_utc INTEGER NOT NULL,
                    amount_text TEXT NOT NULL,
                    currency TEXT NOT NULL
                )
            """.trimIndent()
            statements += """
                INSERT OR REPLACE INTO ${COST_BUCKETS_TABLE}_new
                    (id, provider, start_ts_utc, end_ts_utc, amount_text, currency)
                SELECT id, provider, start_ts_utc, end_ts_utc, amount_text, currency
                FROM $COST_BUCKETS_TABLE
            """.trimIndent()
            statements += "DROP TABLE $COST_BUCKETS_TABLE"
            statements += "ALTER TABLE ${COST_BUCKETS_TABLE}_new RENAME TO $COST_BUCKETS_TABLE"
            statements += COST_BUCKETS_UNIQUE_INDEX
        }
        return statements
    }
}
