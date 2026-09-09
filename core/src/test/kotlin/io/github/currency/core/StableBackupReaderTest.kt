package io.github.currency.core

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class StableBackupReaderTest {
    @Test fun `partial write recovers only after two identical validated reads`() = runTest {
        val good = backup().toByteArray()
        val inputs = ArrayDeque(listOf("app simple time tracker\n".toByteArray(), good, good))
        val waits = mutableListOf<Long>()
        val result = StableBackupReader { waits += it }.read { inputs.removeFirst() }
        assertEquals(1, result.snapshot.records.size)
        assertTrue(inputs.isEmpty())
        assertTrue(waits.contains(1000))
    }
    @Test fun `changing files retry and the last stable contents win`() = runTest {
        val a = backup().toByteArray()
        val b = backup().replace("Research", "Renamed").toByteArray()
        val inputs = ArrayDeque(listOf(a, b, b, b))
        assertEquals("Renamed", StableBackupReader {}.read { inputs.removeFirst() }.snapshot.activities.first().name)
    }
    @Test fun `revoked access and cancellation propagate without retry`() = runTest {
        var reads = 0
        try { StableBackupReader {}.read { reads++; throw SecurityException("revoked") }; fail() } catch (_: SecurityException) { }
        assertEquals(1, reads)
        try { StableBackupReader {}.read { throw CancellationException() }; fail() } catch (_: CancellationException) { }
    }
    @Test fun `invalid backups fail after bounded attempts`() = runTest {
        var reads = 0
        try { StableBackupReader {}.read { reads++; byteArrayOf() }; fail() } catch (_: BackupException) { }
        assertEquals(5, reads)
    }
}
