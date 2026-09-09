package io.github.currency.core

import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction

class BackupException(message: String) : IllegalArgumentException(message)

/** Reads STT's tab-separated backup, not its lossy CSV export. No STT code is embedded. */
object BackupParser {
    const val MAX_BYTES = 32 * 1024 * 1024
    private val ancillaryRows = setOf(
        "recordShortcut", "recordShortcutToRecordTag", "typeToRecordTag", "typeToDefaultTag",
        "activityFilter", "activitySuggestion", "favouriteComment", "typeToFavouriteComment",
        "favouriteColor", "favouriteIcon", "recordTypeGoal", "complexRule", "favRecordsFilters",
        "favRecordsFilter", "scheduledReminder", "activityReminderOverride", "activityReminderRule",
    )
    private val accountingRows = setOf("recordType", "record", "category", "typeCategory", "recordTag", "recordToRecordTag")

    fun parse(bytes: ByteArray): Snapshot {
        checkBackup(bytes.size <= MAX_BYTES, "Backup exceeds the supported 32 MB limit.")
        val text = try {
            Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bytes)).toString().removePrefix("\uFEFF")
        } catch (_: Exception) { throw BackupException("Backup is not valid UTF-8.") }
        checkBackup(text.endsWith('\n'), "Backup is incomplete; waiting for STT to finish writing.")
        val lines = text.split('\n')
        checkBackup(lines.first().trimEnd('\r') == "app simple time tracker", "Choose an STT backup file, not a CSV export.")
        val activities = linkedMapOf<Long, String>()
        val archived = mutableSetOf<Long>()
        val categories = linkedMapOf<Long, String>()
        val tags = linkedMapOf<Long, String>()
        val records = linkedMapOf<Long, Record>()
        val activityCategories = mutableListOf<Pair<Long, Long>>()
        val recordTags = mutableListOf<Pair<Long, Long>>()
        var sawSettings = false
        lines.drop(1).forEachIndexed { index, raw ->
            if (raw.isBlank()) return@forEachIndexed
            val parts = raw.trimEnd('\r').split('\t')
            val type = parts[0]
            fun value(i: Int) = parts.getOrNull(i) ?: throw BackupException("Incomplete $type row at line ${index + 2}.")
            fun id(i: Int): Long = value(i).toLongOrNull()?.takeIf { it > 0 }
                ?: throw BackupException("Invalid identifier at line ${index + 2}.")
            fun unique(map: MutableMap<Long, String>, key: Long, name: String) {
                checkBackup(name.isNotBlank(), "An activity, category, or tag has no name.")
                checkBackup(map.put(key, name) == null, "Duplicate $type identifier in backup.")
            }
            checkBackup(!(sawSettings && type in accountingRows), "Unsupported backup section order.")
            when (type) {
                "recordType" -> {
                    checkBackup(parts.size >= 6, "Incomplete activity row.")
                    unique(activities, id(1), value(2))
                    checkBackup(value(5) in setOf("0", "1"), "Invalid activity archive flag.")
                    if (value(5) == "1") archived += id(1)
                }
                "category" -> { checkBackup(parts.size >= 4, "Incomplete category row."); unique(categories, id(1), value(2)) }
                "recordTag" -> { checkBackup(parts.size >= 5, "Incomplete tag row."); unique(tags, id(1), value(3)) }
                "typeCategory" -> activityCategories += id(1) to id(2)
                "recordToRecordTag" -> recordTags += id(1) to id(2)
                "record" -> {
                    checkBackup(parts.size >= 7, "Incomplete record row.")
                    val record = Record(id(1), id(2), id(3), id(4))
                    checkBackup(record.end >= record.start, "A record ends before it starts.")
                    checkBackup(records.put(record.id, record) == null, "Duplicate record identifier in backup.")
                    val legacyTag = value(6)
                    if (legacyTag.isNotBlank() && legacyTag != "0") recordTags += record.id to id(6)
                }
                "prefs" -> { checkBackup(parts.size >= 3 && value(1).isNotBlank(), "Incomplete settings section."); sawSettings = true }
                in ancillaryRows -> checkBackup(parts.size >= 2, "Incomplete $type row.")
                else -> throw BackupException("Unsupported STT backup section: $type.")
            }
        }
        // STT's automatic backup uses Save.Standard, which omits preferences.
        // There is no end-of-snapshot marker; stable reads are checked separately.
        checkBackup(activities.isNotEmpty(), "Backup contains no activities.")
        activityCategories.forEach { (a, c) -> checkBackup(a in activities && c in categories, "Incomplete activity/category references.") }
        records.values.forEach { checkBackup(it.activityId in activities, "A record refers to an unknown activity.") }
        recordTags.forEach { (r, t) -> checkBackup(r in records && t in tags, "Incomplete record/tag references.") }
        val categoryNames = activityCategories.groupBy({ it.first }, { categories.getValue(it.second) })
        val tagIds = recordTags.groupBy({ it.first }, { it.second })
        return Snapshot(
            activities.map { (id, name) -> Activity(id, name, categoryNames[id].orEmpty().toSet(), id in archived) },
            records.values.map { it.copy(tags = tagIds[it.id].orEmpty().toSet()) }, tags,
        )
    }

    private fun checkBackup(condition: Boolean, message: String) { if (!condition) throw BackupException(message) }
}
