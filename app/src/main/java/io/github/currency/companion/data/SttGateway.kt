package io.github.currency.companion.data

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import io.github.currency.core.BackupParser
import java.io.IOException

class SttGateway(private val context: Context) {
    companion object {
        const val PACKAGE = "com.razeeman.util.simpletimetracker"
        const val ACTION = "$PACKAGE.ACTION_EXTERNAL_AUTOMATIC_BACKUP"
        val RECEIVER = ComponentName(PACKAGE, "com.example.util.simpletimetracker.feature_notification.recevier.NotificationReceiver")
    }

    fun requestBackup() {
        try { context.packageManager.getPackageInfo(PACKAGE, 0) }
        catch (_: android.content.pm.PackageManager.NameNotFoundException) {
            throw IOException("Simple Time Tracker is not installed. Install STT, then reopen Currency.")
        }
        val intent = Intent(ACTION).setComponent(RECEIVER)
        val receivers = context.packageManager.queryBroadcastReceivers(intent, 0)
        if (receivers.none { it.activityInfo.exported && it.activityInfo.enabled }) {
            throw IOException("This STT version does not expose automatic backup requests. Update STT and try again.")
        }
        context.sendBroadcast(intent)
    }

    fun read(uri: Uri): ByteArray = context.contentResolver.openInputStream(uri)?.use { stream ->
        // Bound allocation even if a document provider advertises a misleading file size.
        val out = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        while (true) {
            val count = stream.read(buffer)
            if (count < 0) break
            if (out.size() + count > BackupParser.MAX_BYTES) throw IOException("Backup exceeds the supported 32 MB limit.")
            out.write(buffer, 0, count)
        }
        out.toByteArray()
    } ?: throw IOException("The backup file is unavailable. Check its location in STT.")

    fun documentName(uri: Uri): String = runCatching {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
        }
    }.getOrNull() ?: "STT backup"

    fun openStt() {
        val launch = context.packageManager.getLaunchIntentForPackage(PACKAGE)
            ?: throw IOException("Install Simple Time Tracker first.")
        context.startActivity(launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}
