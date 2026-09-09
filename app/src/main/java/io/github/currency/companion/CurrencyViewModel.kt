package io.github.currency.companion

import android.app.Application
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import io.github.currency.companion.data.*
import io.github.currency.core.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import java.security.MessageDigest

data class ScreenState(val stored: StoredState? = null, val loading: Boolean = true, val syncing: Boolean = false, val error: String? = null)

class CurrencyViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as CurrencyApplication
    private val mutable = MutableStateFlow(ScreenState())
    val state = mutable.asStateFlow()
    private val mutex = Mutex()
    private var pendingUri: Uri? = null

    suspend fun refreshLoop() {
        mutable.value = mutable.value.copy(stored = withContext(Dispatchers.IO) { app.repository.load() }, loading = false)
        while (currentCoroutineContext().isActive) {
            val started = android.os.SystemClock.elapsedRealtime()
            refresh()
            val elapsed = android.os.SystemClock.elapsedRealtime() - started
            delay(maxOf(1_000, 60_000 - elapsed))
        }
    }

    suspend fun connect(uri: Uri) {
        try {
            getApplication<Application>().contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            pendingUri = uri
            refresh()
        } catch (_: SecurityException) {
            mutable.value = mutable.value.copy(error = "Read access could not be saved. Choose a local document using the system file picker.")
        }
    }

    fun openStt() {
        runCatching { app.gateway.openStt() }.onFailure { mutable.value = mutable.value.copy(error = it.message) }
    }

    suspend fun refresh() {
        if (!mutex.tryLock()) return
        try {
            val current = mutable.value.stored
            val uri = pendingUri ?: current?.sync?.uri?.let(Uri::parse) ?: return
            mutable.value = mutable.value.copy(syncing = true, error = null)
            withContext(Dispatchers.IO) {
                app.gateway.requestBackup()
                delay(1_500) // Give STT a head start; never interpret this as proof of completion.
                val backup = StableBackupReader().read { app.gateway.read(uri) }
                if (current?.trial == null && backup.snapshot.records.isEmpty()) {
                    throw BackupException("This backup contains no completed records. Choose STT’s full automatic backup, including records.")
                }
                val hash = MessageDigest.getInstance("SHA-256").digest(backup.bytes).joinToString("") { "%02x".format(it) }
                app.repository.accept(backup.snapshot, hash, uri.toString(), app.gateway.documentName(uri), System.currentTimeMillis())
            }
            val updated = withContext(Dispatchers.IO) { app.repository.load() }
            pendingUri = null
            mutable.value = mutable.value.copy(stored = updated)
            // Drop old access only after successfully replacing the source; the trial is preserved.
            current?.sync?.uri?.takeIf { it != uri.toString() }?.let { old ->
                runCatching { app.contentResolver.releasePersistableUriPermission(Uri.parse(old), Intent.FLAG_GRANT_READ_URI_PERMISSION) }
            }
        } catch (e: CancellationException) { throw e
        } catch (_: SecurityException) {
            mutable.value = mutable.value.copy(error = "Access to the backup was revoked. Reconnect the same backup file; your balance and trial are preserved.")
        } catch (e: Exception) {
            mutable.value = mutable.value.copy(error = e.message ?: "Could not read the backup. Your previous balance is preserved.")
        } finally {
            mutable.value = mutable.value.copy(syncing = false)
            mutex.unlock()
        }
    }
}
