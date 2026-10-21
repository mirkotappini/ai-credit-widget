package com.patchouli.deepseekwidget.data

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.patchouli.deepseekwidget.net.CostBucket
import com.patchouli.deepseekwidget.provider.Provider
import java.math.BigDecimal

class SnapshotDb private constructor(context: Context) :
    SQLiteOpenHelper(context.applicationContext, DB_NAME, null, SnapshotDbMigration.VERSION) {

    override fun onCreate(db: SQLiteDatabase) {
        SnapshotDbMigration.createStatements().forEach(db::execSQL)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        SnapshotDbMigration.upgradeStatements(oldVersion).forEach(db::execSQL)
    }

    // --- Snapshot DeepSeek ----------------------------------------------

    @Synchronized
    fun insert(ts: Long, total: BigDecimal, granted: BigDecimal, topped: BigDecimal): Long {
        val values = ContentValues().apply {
            put("ts", ts)
            put("total", total.toPlainString())
            put("granted", granted.toPlainString())
            put("topped", topped.toPlainString())
            put("provider", Provider.DEEPSEEK.storageKey)
        }
        return writableDatabase.insert(SnapshotDbMigration.SNAPSHOTS_TABLE, null, values)
    }

    @Synchronized
    fun latest(): Snapshot? {
        readableDatabase.query(
            SnapshotDbMigration.SNAPSHOTS_TABLE, null, null, null, null, null, "ts DESC, id DESC", "1"
        ).use { cursor ->
            return if (cursor.moveToFirst()) read(cursor) else null
        }
    }

    @Synchronized
    fun since(sinceTs: Long): List<Snapshot> {
        val result = ArrayList<Snapshot>()
        readableDatabase.query(
            SnapshotDbMigration.SNAPSHOTS_TABLE, null, "ts >= ?", arrayOf(sinceTs.toString()),
            null, null, "ts ASC, id ASC"
        ).use { cursor ->
            while (cursor.moveToNext()) result.add(read(cursor))
        }
        return result
    }

    @Synchronized
    fun all(): List<Snapshot> = since(0L)

    @Synchronized
    fun count(): Long {
        readableDatabase.rawQuery(
            "SELECT COUNT(*) FROM ${SnapshotDbMigration.SNAPSHOTS_TABLE}", null
        ).use { cursor ->
            return if (cursor.moveToFirst()) cursor.getLong(0) else 0L
        }
    }

    @Synchronized
    fun pruneOlderThan(ts: Long): Int =
        writableDatabase.delete(
            SnapshotDbMigration.SNAPSHOTS_TABLE, "ts < ?", arrayOf(ts.toString())
        )

    // --- Cost bucket OpenAI ---------------------------------------------

    /**
     * Inserisce o sostituisce i bucket del provider. L'indice univoco
     * (provider, start_ts_utc) fa scattare il replace sui duplicati.
     * Ritorna il numero di righe scritte.
     */
    @Synchronized
    fun upsertCostBuckets(provider: Provider, buckets: List<CostBucket>): Int {
        val db = writableDatabase
        var written = 0
        db.beginTransaction()
        try {
            for (bucket in buckets) {
                val values = ContentValues().apply {
                    put("provider", provider.storageKey)
                    put("start_ts_utc", bucket.startTsUtc)
                    put("end_ts_utc", bucket.endTsUtc)
                    put("amount_text", bucket.amount.toPlainString())
                    put("currency", bucket.currency)
                }
                val row = db.insertWithOnConflict(
                    SnapshotDbMigration.COST_BUCKETS_TABLE,
                    null,
                    values,
                    SQLiteDatabase.CONFLICT_REPLACE
                )
                if (row != -1L) written++
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
        return written
    }

    /** Bucket che intersecano l'istante richiesto (end_ts_utc > sinceTs). */
    @Synchronized
    fun costBucketsSince(provider: Provider, sinceTs: Long): List<CostBucket> {
        val result = ArrayList<CostBucket>()
        readableDatabase.query(
            SnapshotDbMigration.COST_BUCKETS_TABLE,
            arrayOf("start_ts_utc", "end_ts_utc", "amount_text", "currency"),
            "provider = ? AND end_ts_utc > ?",
            arrayOf(provider.storageKey, sinceTs.toString()),
            null,
            null,
            "start_ts_utc ASC"
        ).use { cursor ->
            while (cursor.moveToNext()) {
                result.add(
                    CostBucket(
                        startTsUtc = cursor.getLong(cursor.getColumnIndexOrThrow("start_ts_utc")),
                        endTsUtc = cursor.getLong(cursor.getColumnIndexOrThrow("end_ts_utc")),
                        amount = cursor.getDecimal("amount_text"),
                        currency = cursor.getString(cursor.getColumnIndexOrThrow("currency"))
                    )
                )
            }
        }
        return result
    }

    @Synchronized
    fun pruneCostBuckets(provider: Provider, beforeTs: Long): Int =
        writableDatabase.delete(
            SnapshotDbMigration.COST_BUCKETS_TABLE,
            "provider = ? AND start_ts_utc < ?",
            arrayOf(provider.storageKey, beforeTs.toString())
        )

    private fun read(cursor: Cursor): Snapshot = Snapshot(
        id = cursor.getLong(cursor.getColumnIndexOrThrow("id")),
        ts = cursor.getLong(cursor.getColumnIndexOrThrow("ts")),
        total = cursor.getDecimal("total"),
        granted = cursor.getDecimal("granted"),
        topped = cursor.getDecimal("topped")
    )

    private fun Cursor.getDecimal(column: String): BigDecimal {
        val raw = getString(getColumnIndexOrThrow(column))
        return raw.toBigDecimalOrNull() ?: BigDecimal.ZERO
    }

    companion object {
        private const val DB_NAME = "snapshots.db"

        @Volatile
        private var instance: SnapshotDb? = null

        fun getInstance(context: Context): SnapshotDb =
            instance ?: synchronized(this) {
                instance ?: SnapshotDb(context).also { instance = it }
            }
    }
}
