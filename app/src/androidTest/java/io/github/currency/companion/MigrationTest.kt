package io.github.currency.companion

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import io.github.currency.companion.data.*
import io.github.currency.core.*
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class MigrationTest {
    @Test fun versionTwoUpgradeAddsCostsWithoutChangingTheTrialPrice() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "migration-v2-test.db"
        context.deleteDatabase(name)
        val path = context.getDatabasePath(name)
        path.parentFile!!.mkdirs()
        val schema = JSONObject(InstrumentationRegistry.getInstrumentation().context.assets
            .open("currency-v2.json").bufferedReader().use { it.readText() }).getJSONObject("database")
        SQLiteDatabase.openOrCreateDatabase(path, null).use { old ->
            val entities = schema.getJSONArray("entities")
            for (i in 0 until entities.length()) {
                val entity = entities.getJSONObject(i)
                old.execSQL(entity.getString("createSql").replace("\${TABLE_NAME}", entity.getString("tableName")))
            }
            val setup = schema.getJSONArray("setupQueries")
            for (i in 0 until setup.length()) old.execSQL(setup.getString(i))
            old.execSQL("INSERT INTO activities VALUES (1, ' Cooking ', 'NEUTRAL', 1, 0), (2, 'TRAVEL', 'WORK', 1, 0), (3, 'Friends', 'NEUTRAL', 1, 1), (4, 'Sleep', 'NEUTRAL', 1, 0), (5, 'Renamed project', 'SIDE', 1, 0)")
            old.execSQL("INSERT INTO records VALUES (1, 1, 1000, 3001000, '')")
            old.execSQL("INSERT INTO trial VALUES (1, 1000, 0, 1000, 3000000, 3000000, 6000000)")
            old.execSQL("INSERT INTO sync VALUES (1, 'content://test/backup', 'backup', 'same', 1000, 1000)")
            old.version = 2
        }
        val db = Room.databaseBuilder(context, CurrencyDatabase::class.java, name)
            .addMigrations(CurrencyDatabase.MIGRATION_2_3).build()
        try {
            val stored = CurrencyRepository(db).load()
            assertEquals(Totals(3_000_000, 3_000_000, 6_000_000), stored.trial!!.baseline)
            assertEquals(1000L, stored.trial!!.activatedAt)
            assertEquals(java.time.ZoneId.systemDefault().id, stored.trial!!.zoneId)
            assertEquals(listOf(Kind.COOKING, Kind.TRAVEL, Kind.FRIENDS, Kind.SLEEP, Kind.SIDE),
                (1L..5L).map { stored.kinds[it] })
            assertTrue(stored.activities.single { it.id == 3L }.archived)
            assertEquals("same", stored.sync!!.hash)
            assertEquals("content://test/backup", stored.sync!!.uri)
            val credits = Economy.account(Economy.totals(stored.snapshot, stored.kinds, 1000, 3001000), stored.trial!!)
            assertEquals(0, java.math.BigDecimal("0.1575").compareTo(credits.spent))
        } finally {
            db.close()
            context.deleteDatabase(name)
        }
    }

    @Test fun upgradePreservesTrialAndRecordsAndRefreshesArchiveFlags() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "migration-test.db"
        context.deleteDatabase(name)
        val path = context.getDatabasePath(name)
        path.parentFile!!.mkdirs()
        val schema = JSONObject(InstrumentationRegistry.getInstrumentation().context.assets
            .open("currency-v1.json").bufferedReader().use { it.readText() }).getJSONObject("database")
        SQLiteDatabase.openOrCreateDatabase(path, null).use { old ->
            val entities = schema.getJSONArray("entities")
            for (i in 0 until entities.length()) {
                val entity = entities.getJSONObject(i)
                old.execSQL(entity.getString("createSql").replace("\${TABLE_NAME}", entity.getString("tableName")))
            }
            val setup = schema.getJSONArray("setupQueries")
            for (i in 0 until setup.length()) old.execSQL(setup.getString(i))
            old.execSQL("INSERT INTO activities VALUES (1, 'sides', 'NEUTRAL', 1), (2, 'oxford', 'NEUTRAL', 1)")
            old.execSQL("INSERT INTO records VALUES (1, 1, 1000, 3001000, '')")
            old.execSQL("INSERT INTO trial VALUES (1, 1000, 0, 1000, 3000000, 3000000)")
            old.execSQL("INSERT INTO sync VALUES (1, 'content://test/backup', 'backup', 'same', 1000, 1000)")
            old.version = 1
        }
        val db = Room.databaseBuilder(context, CurrencyDatabase::class.java, name)
            .addMigrations(CurrencyDatabase.MIGRATION_1_2, CurrencyDatabase.MIGRATION_2_3).build()
        try {
            val repository = CurrencyRepository(db)
            val stored = repository.load()
            assertEquals(1000L, stored.trial!!.activatedAt)
            assertEquals(Totals(3_000_000, 3_000_000), stored.trial!!.baseline)
            assertEquals("content://test/backup", stored.sync!!.uri)
            assertEquals(Kind.SIDE, stored.kinds[1])
            assertEquals(Kind.WORK, stored.kinds[2])
            assertEquals(1, stored.snapshot.records.size)
            assertEquals("", stored.sync!!.hash)
            val changed = stored.snapshot.copy(activities = stored.snapshot.activities.map { it.copy(archived = true) })
            repository.accept(changed, "same", stored.sync!!.uri, "backup", 3001000)
            val imported = repository.load()
            assertTrue(imported.snapshot.activities.all { it.archived })
            assertEquals(stored.trial, imported.trial)
            assertEquals(0, java.math.BigDecimal("0.25").compareTo(Economy.account(
                Economy.totals(imported.snapshot, imported.kinds, 1000, 3001000), imported.trial!!).earned))
        } finally {
            db.close()
            context.deleteDatabase(name)
        }
    }
}
