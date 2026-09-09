package io.github.currency.companion

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.currency.companion.data.*
import io.github.currency.core.*
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RepositoryTest {
    private lateinit var db: CurrencyDatabase
    private lateinit var repository: CurrencyRepository
    private val now = 40L * 86_400_000
    private val sample get() = Snapshot(listOf(Activity(1, "Research", setOf("Work")), Activity(2, "svago")),
        listOf(Record(1, 1, now - 6_000_000, now - 3_000_000), Record(2, 2, now - 3_000_000, now)), emptyMap())

    @Before fun setup() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), CurrencyDatabase::class.java).build()
        repository = CurrencyRepository(db)
    }
    @After fun close() { db.close() }

    @Test fun importIsIdempotentAndTrialDoesNotRecalibrate() = runBlocking {
        repository.accept(sample, "one", "content://test/backup", "backup", now)
        val initial = repository.load()
        repository.accept(sample, "one", "content://test/backup", "backup", now + 1000)
        val second = repository.load()
        assertEquals(2, second.snapshot.records.size)
        assertEquals(initial.trial, second.trial)
        assertEquals(now, second.sync!!.lastChanged)
        assertEquals(now + 1000, second.sync!!.lastRead)
        assertEquals(Totals(), Economy.totals(second.snapshot, second.kinds, second.trial!!.activatedAt, now + 1000))
    }

    @Test fun editedAndDeletedRecordsReplaceSnapshotWithoutChangingTheRate() = runBlocking {
        repository.accept(sample, "one", "content://test/backup", "backup", now)
        val trial = repository.load().trial
        val earning = Record(3, 1, now, now + 3_000_000)
        repository.accept(sample.copy(records = sample.records + earning), "two", "content://test/backup", "backup", now + 3_000_000)
        var stored = repository.load()
        assertEquals(Totals(3_000_000, 0), Economy.totals(stored.snapshot, stored.kinds, now, now + 3_000_000))
        repository.accept(sample.copy(records = sample.records + earning.copy(end = now + 900_000)), "three", "content://test/backup", "backup", now + 3_000_000)
        stored = repository.load()
        assertEquals(Totals(900_000, 0), Economy.totals(stored.snapshot, stored.kinds, now, now + 3_000_000))
        repository.accept(sample, "four", "content://test/backup", "backup", now + 4_000_000)
        stored = repository.load()
        assertEquals(2, stored.snapshot.records.size)
        assertEquals(trial, stored.trial)
    }

    @Test fun renamedActivityKeepsItsKindAndNewWorkIsDiscovered() = runBlocking {
        repository.accept(sample, "one", "content://test/backup", "backup", now)
        val updated = sample.copy(activities = listOf(Activity(1, "Renamed"), Activity(2, "Fun"), Activity(3, "New", setOf("Work"))))
        repository.accept(updated, "two", "content://test/backup", "backup", now + 1)
        val kinds = repository.load().kinds
        assertEquals(Kind.WORK, kinds[1])
        assertEquals(Kind.LEISURE, kinds[2])
        assertEquals(Kind.WORK, kinds[3])
    }
}
