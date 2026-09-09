package io.github.currency.core

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay

data class StableBackup(val bytes: ByteArray, val snapshot: Snapshot)

/** Structural validation plus separated identical reads; this is not an STT completion acknowledgement. */
class StableBackupReader(private val pause: suspend (Long) -> Unit = { delay(it) }) {
    suspend fun read(readDocument: suspend () -> ByteArray): StableBackup {
        var lastFailure: Exception = BackupException("The backup is still changing. Keeping the previous balance.")
        repeat(5) { attempt ->
            try {
                val first = readDocument()
                BackupParser.parse(first)
                pause(1_000)
                val second = readDocument()
                if (first.contentEquals(second)) return StableBackup(second, BackupParser.parse(second))
                lastFailure = BackupException("The backup is still changing. Keeping the previous balance.")
            } catch (e: CancellationException) { throw e
            } catch (e: SecurityException) { throw e
            } catch (e: Exception) { lastFailure = e }
            if (attempt < 4) pause(1_000L * (attempt + 1))
        }
        throw lastFailure
    }
}
