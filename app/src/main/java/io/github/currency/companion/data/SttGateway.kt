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
    }

    fun requestBackup() {
        try { context.packageManager.getPackageInfo(PACKAGE, 0) }
        catch (_: android.content.pm.PackageManager.NameNotFoundException) {
            throw IOException("Simple Time Tracker is not installed. Install STT, then reopen Currency.")
        }
        // STT 1.61 moved external actions from NotificationReceiver to ExternalNotificationReceiver,
        // so resolve the receiver instead of naming its class.
        val receiver = context.packageManager.queryBroadcastReceivers(Intent(ACTION).setPackage(PACKAGE), 0)
            .map { it.activityInfo }.firstOrNull { it.exported && it.enabled }
            ?: throw IOException("This STT version does not expose automatic backup requests. Update STT and try again.")
        context.sendBroadcast(Intent(ACTION).setComponent(ComponentName(receiver.packageName, receiver.name)))
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
