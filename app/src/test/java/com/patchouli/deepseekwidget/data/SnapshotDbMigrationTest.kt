package com.patchouli.deepseekwidget.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SnapshotDbMigrationTest {

    @Test
    fun `upgrade da v1 aggiunge provider con default deepseek`() {
        val sql = SnapshotDbMigration.upgradeStatements(1)

        assertTrue(sql.any { it.contains("ALTER TABLE snapshots") })
        assertTrue(sql.any { it.contains("ADD COLUMN provider") })
        assertTrue(sql.any { it.contains("DEFAULT 'deepseek'") })
    }

    @Test
    fun `upgrade da v1 crea cost_buckets con indice su provider start ed end`() {
        val sql = SnapshotDbMigration.upgradeStatements(1)

        assertTrue(sql.any { it.contains("CREATE TABLE IF NOT EXISTS cost_buckets") })
        assertTrue(sql.any { it.contains("amount_text TEXT NOT NULL") })
        assertTrue(
            sql.any {
                it.contains("CREATE UNIQUE INDEX") &&
                    it.contains("cost_buckets(provider, start_ts_utc, end_ts_utc)")
            }
        )
    }

    @Test
    fun `upgrade da v2 ricrea cost_buckets preservando le righe`() {
        val sql = SnapshotDbMigration.upgradeStatements(2)

        assertTrue(sql.any { it.contains("CREATE TABLE cost_buckets_new") })
        assertTrue(
            sql.any {
                it.contains("INSERT OR REPLACE INTO cost_buckets_new") &&
                    it.contains("SELECT id, provider, start_ts_utc, end_ts_utc, amount_text, currency") &&
                    it.contains("FROM cost_buckets")
            }
        )
        assertTrue(sql.any { it.contains("DROP TABLE cost_buckets") })
        assertTrue(sql.any { it.contains("RENAME TO cost_buckets") })
        assertTrue(
            sql.any {
                it.contains("CREATE UNIQUE INDEX") &&
                    it.contains("(provider, start_ts_utc, end_ts_utc)")
            }
        )
        assertTrue(sql.none { it.contains("ALTER TABLE snapshots") })
    }

    @Test
    fun `database gia alla versione corrente non esegue upgrade`() {
        assertEquals(
            emptyList<String>(),
            SnapshotDbMigration.upgradeStatements(SnapshotDbMigration.VERSION)
        )
    }

    @Test
    fun `schema nuovo contiene provider e cost_buckets`() {
        val sql = SnapshotDbMigration.createStatements().joinToString("\n")

        assertTrue(sql.contains("provider TEXT NOT NULL DEFAULT 'deepseek'"))
        assertTrue(sql.contains("CREATE TABLE IF NOT EXISTS cost_buckets"))
    }
}
