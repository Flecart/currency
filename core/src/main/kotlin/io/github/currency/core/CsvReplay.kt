package io.github.currency.core

import java.io.File
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** Local analysis utility only; private CSVs are never packaged in the Android app. */
fun main(args: Array<String>) {
    require(args.isNotEmpty() && args[0].isNotBlank()) { "Pass -Pcsv=/absolute/path/to/stt.csv [-Pzone=Europe/Rome]" }
    val snapshot = CsvReplay.read(File(args[0]).readText(), ZoneId.of(args.getOrElse(1) { "Europe/Rome" }))
    val end = snapshot.records.maxOf { it.end }
    val kinds = mappings(snapshot)
    val trial = Economy.calibrate(snapshot, kinds, end)
    val account = Economy.account(trial.baseline, trial)
    println("Records: ${snapshot.records.size}; activities: ${snapshot.activities.size}")
    println("28-day baseline work hours: ${trial.baseline.workMs / 3_600_000.0}")
    println("28-day baseline svago hours: ${trial.baseline.leisureMs / 3_600_000.0}")
    println("Work credits: ${account.earned}")
    println("Calibrated spending: ${account.spent}")
    println("Svago minutes per credit: ${Economy.leisureMinutesPerCredit(trial)}")
    println("Historical net (simulation only): ${account.balance}")
    println("Opening real trial balance: 0")
}

object CsvReplay {
    fun read(text: String, zone: ZoneId): Snapshot {
        val rows = parseCsv(text.removePrefix("\uFEFF"))
        require(rows.isNotEmpty()) { "CSV is empty" }
        val header = rows.first()
        fun column(name: String) = header.indexOf(name).also { require(it >= 0) { "Missing CSV column: $name" } }
        val activityColumn = column("activity name")
        val startColumn = column("time started")
        val endColumn = column("time ended")
        val categoriesColumn = column("categories")
        val tagsColumn = column("record tags")
        val names = linkedMapOf<String, Long>()
        val tags = linkedMapOf<String, Long>()
        val categories = mutableMapOf<Long, MutableSet<String>>()
        val format = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
        val records = rows.drop(1).filter { it.any(String::isNotBlank) }.mapIndexed { index, row ->
            require(row.size == header.size) { "Wrong number of CSV fields on row ${index + 2}" }
            val activity = names.getOrPut(row[activityColumn]) { names.size + 1L }
            categories.getOrPut(activity) { mutableSetOf() }.addAll(row[categoriesColumn].split(',').map(String::trim).filter(String::isNotEmpty))
            val tagIds = row[tagsColumn].split(',').map(String::trim).filter(String::isNotEmpty).map { name ->
                tags.getOrPut(name) { tags.size + 1L }
            }.toSet()
            val start = LocalDateTime.parse(row[startColumn], format).atZone(zone).toInstant().toEpochMilli()
            val end = LocalDateTime.parse(row[endColumn], format).atZone(zone).toInstant().toEpochMilli()
            require(end >= start) { "CSV record ends before it starts" }
            Record(index + 1L, activity, start, end, tagIds)
        }
        return Snapshot(names.map { (name, id) -> Activity(id, name, categories[id].orEmpty()) }, records, tags.entries.associate { it.value to it.key })
    }

    private fun parseCsv(text: String): List<List<String>> {
        val result = mutableListOf<List<String>>()
        var row = mutableListOf<String>()
        val field = StringBuilder()
        var quoted = false
        var index = 0
        while (index < text.length) {
            val c = text[index]
            when {
                c == '"' && quoted && text.getOrNull(index + 1) == '"' -> { field.append('"'); index++ }
                c == '"' -> quoted = !quoted
                c == ',' && !quoted -> { row += field.toString(); field.clear() }
                c == '\n' && !quoted -> { row += field.toString().trimEnd('\r'); field.clear(); result += row; row = mutableListOf() }
                else -> field.append(c)
            }
            index++
        }
        require(!quoted) { "Unclosed quoted CSV field" }
        if (field.isNotEmpty() || row.isNotEmpty()) { row += field.toString(); result += row }
        return result
    }
}
