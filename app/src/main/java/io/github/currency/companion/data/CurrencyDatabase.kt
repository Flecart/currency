package io.github.currency.companion.data

import androidx.room.*
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import io.github.currency.core.*

@Entity(tableName = "activities")
data class ActivityRow(@PrimaryKey val id: Long, val name: String, val kind: String, val present: Boolean = true, @ColumnInfo(defaultValue = "0") val archived: Boolean = false)
@Entity(tableName = "records")
data class RecordRow(@PrimaryKey val id: Long, val activityId: Long, val start: Long, val end: Long, val tagIds: String)
@Entity(tableName = "tags")
data class TagRow(@PrimaryKey val id: Long, val name: String)
@Entity(tableName = "trial")
data class TrialRow(
    @PrimaryKey val id: Int = 1, val activatedAt: Long, val baselineStart: Long, val baselineEnd: Long,
    val baselineWorkMs: Long, val baselineLeisureMs: Long,
    @ColumnInfo(defaultValue = "0") val baselineSideMs: Long = 0,
    @ColumnInfo(defaultValue = "'UTC'") val zoneId: String = java.time.ZoneId.systemDefault().id,
) {
    fun domain() = Trial(activatedAt, baselineStart, baselineEnd, Totals(baselineWorkMs, baselineLeisureMs, baselineSideMs), zoneId)
}
@Entity(tableName = "sync")
data class SyncRow(
    @PrimaryKey val id: Int = 1, val uri: String, val documentName: String,
    val hash: String, val lastRead: Long, val lastChanged: Long,
)

@Dao
interface CurrencyDao {
    @Query("SELECT * FROM activities") suspend fun activities(): List<ActivityRow>
    @Query("SELECT * FROM records ORDER BY start") suspend fun records(): List<RecordRow>
    @Query("SELECT * FROM tags") suspend fun tags(): List<TagRow>
    @Query("SELECT * FROM trial WHERE id = 1") suspend fun trial(): TrialRow?
    @Query("SELECT * FROM sync WHERE id = 1") suspend fun sync(): SyncRow?
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun putActivities(rows: List<ActivityRow>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun putRecords(rows: List<RecordRow>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun putTags(rows: List<TagRow>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun putTrial(row: TrialRow)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun putSync(row: SyncRow)
    @Query("DELETE FROM records") suspend fun clearRecords()
    @Query("DELETE FROM tags") suspend fun clearTags()
    @Query("UPDATE activities SET present = 0") suspend fun markActivitiesAbsent()
}

@Database(entities = [ActivityRow::class, RecordRow::class, TagRow::class, TrialRow::class, SyncRow::class], version = 3, exportSchema = true)
abstract class CurrencyDatabase : RoomDatabase() {
    abstract fun dao(): CurrencyDao
    companion object {
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE trial ADD COLUMN zoneId TEXT NOT NULL DEFAULT 'UTC'")
                db.execSQL("UPDATE trial SET zoneId = ?", arrayOf(java.time.ZoneId.systemDefault().id))
                for (kind in listOf(Kind.COOKING, Kind.TRAVEL, Kind.FRIENDS, Kind.SLEEP)) {
                    db.execSQL("UPDATE activities SET kind = ? WHERE lower(trim(name)) = ?",
                        arrayOf(kind.name, kind.name.lowercase(java.util.Locale.ROOT)))
                }
            }
        }

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE activities ADD COLUMN archived INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE trial ADD COLUMN baselineSideMs INTEGER NOT NULL DEFAULT 0")
                db.execSQL("UPDATE activities SET kind = 'SIDE' WHERE lower(trim(name)) IN ('esplorazioni', 'sides')")
                db.execSQL("UPDATE activities SET kind = 'WORK' WHERE lower(trim(name)) IN ('thesis', 'oxford', 'zhijing', 'aria')")
                // Re-read archive flags even when the source document has not changed.
                db.execSQL("UPDATE sync SET hash = ''")
            }
        }
    }
}

data class StoredState(val snapshot: Snapshot, val kinds: Map<Long, Kind>, val trial: Trial?, val sync: SyncRow?, val activities: List<ActivityRow>)

class CurrencyRepository(private val db: CurrencyDatabase) {
    suspend fun load(): StoredState = db.withTransaction {
        val dao = db.dao()
        val activities = dao.activities()
        StoredState(
            snapshot = Snapshot(
                activities.filter { it.present }.map { Activity(it.id, it.name, archived = it.archived) },
                dao.records().map { row -> Record(row.id, row.activityId, row.start, row.end,
                    row.tagIds.split(',').filter { it.isNotEmpty() }.map(String::toLong).toSet()) },
                dao.tags().associate { it.id to it.name },
            ),
            kinds = activities.associate { it.id to Kind.valueOf(it.kind) },
            trial = dao.trial()?.domain(), sync = dao.sync(), activities = activities,
        )
    }

    /** Whole accepted snapshots replace records in one transaction, so edits and deletions reconcile. */
    suspend fun accept(snapshot: Snapshot, hash: String, uri: String, documentName: String, now: Long) = db.withTransaction {
        val dao = db.dao()
        val previous = dao.sync()
        val oldKinds = dao.activities().associate { it.id to Kind.valueOf(it.kind) }
        val kinds = mappings(snapshot, oldKinds)
        if (previous?.hash != hash) {
            dao.markActivitiesAbsent()
            dao.putActivities(snapshot.activities.map { ActivityRow(it.id, it.name, kinds.getValue(it.id).name, archived = it.archived) })
            dao.clearRecords()
            dao.clearTags()
            dao.putTags(snapshot.tags.map { TagRow(it.key, it.value) })
            dao.putRecords(snapshot.records.map { RecordRow(it.id, it.activityId, it.start, it.end, it.tags.sorted().joinToString(",")) })
        }
        if (dao.trial() == null) {
            val trial = Economy.calibrate(snapshot, kinds, now)
            dao.putTrial(TrialRow(activatedAt = trial.activatedAt, baselineStart = trial.baselineStart,
                baselineEnd = trial.baselineEnd, baselineWorkMs = trial.baseline.workMs, baselineLeisureMs = trial.baseline.leisureMs, baselineSideMs = trial.baseline.sideMs, zoneId = trial.zoneId))
        }
        dao.putSync(SyncRow(uri = uri, documentName = documentName, hash = hash, lastRead = now,
            lastChanged = if (previous?.hash == hash) previous.lastChanged else now))
    }
}
