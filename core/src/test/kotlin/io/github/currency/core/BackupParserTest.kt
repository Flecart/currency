package io.github.currency.core

import org.junit.Assert.*
import org.junit.Test

internal fun backup(
    recordRows: String = "record\t11\t1\t1000\t3001000\t\t\n",
    relations: String = "recordToRecordTag\t11\t10\t\n",
): String = listOf(
    "app simple time tracker",
    "recordType\t1\tResearch\ticon\t0\t0\t\t\t\t\t\t0\t",
    "recordType\t2\tsvago\ticon\t0\t0\t\t\t\t\t\t0\t",
).joinToString("\n", postfix = "\n") + recordRows + listOf(
    "category\t3\tWork\t0\t\t",
    "typeCategory\t1\t3",
    "recordTag\t10\t\tlab1\t0\t0\t\t\t0\t\t0\t",
    "recordTag\t20\t\tPausa\t0\t0\t\t\t0\t\t0\t",
).joinToString("\n", postfix = "\n") + relations + "prefs\tsome_setting\t1\n"

class BackupParserTest {
    @Test fun `reads real field positions and category tag relationships`() {
        val snapshot = BackupParser.parse(backup().toByteArray())
        assertEquals("Research", snapshot.activities.first().name)
        assertEquals(setOf("Work"), snapshot.activities.first().categories)
        assertEquals(setOf(10L), snapshot.records.single().tags)
        assertEquals("Pausa", snapshot.tags[20])
        assertEquals(Kind.WORK, classify(snapshot.activities.first()))
    }

    @Test fun `accepts standard automatic backup without optional settings`() {
        val snapshot = BackupParser.parse(backup().substringBefore("prefs").toByteArray())
        assertEquals(2, snapshot.activities.size)
        assertEquals(setOf(10L), snapshot.records.single().tags)
    }

    @Test fun `rejects CSV invalid UTF8 missing newline unknown format and duplicate IDs`() {
        listOf("activity name,time started\n", backup().trimEnd(), backup() + "futureFormat\t1\n",
            backup(recordRows = "record\t11\t1\t1000\t2000\t\t\nrecord\t11\t1\t2000\t3000\t\t\n"))
            .forEach { assertThrows(BackupException::class.java) { BackupParser.parse(it.toByteArray()) } }
        assertThrows(BackupException::class.java) { BackupParser.parse(byteArrayOf(0xC3.toByte(), 0x28)) }
    }

    @Test fun `rejects dangling activity or tag links reversed dates and data after settings`() {
        listOf(
            backup().replace("record\t11\t1", "record\t11\t999"),
            backup(relations = "recordToRecordTag\t11\t999\t\n"),
            backup().replace("1000\t3001000", "3001000\t1000"),
            backup() + "record\t22\t1\t4000\t5000\t\t\n",
        ).forEach { assertThrows(BackupException::class.java) { BackupParser.parse(it.toByteArray()) } }
    }

    @Test fun `accepts CRLF BOM empty record history and known unrelated settings`() {
        val snapshot = BackupParser.parse(("\uFEFF" + backup(recordRows = "", relations = "") + "activityReminderOverride\t1\t0\n").replace("\n", "\r\n").toByteArray())
        assertTrue(snapshot.records.isEmpty())
    }

    @Test fun `legacy tag position is understood`() {
        val snapshot = BackupParser.parse(backup(recordRows = "record\t11\t1\t1000\t3001000\t\t20\n", relations = "").toByteArray())
        assertEquals(setOf(20L), snapshot.records.single().tags)
    }
    @Test fun `activity archive flag is parsed and malformed flags fail`() {
        val archived = backup().replace("Research\ticon\t0\t0", "Research\ticon\t0\t1")
        assertTrue(BackupParser.parse(archived.toByteArray()).activities.first().archived)
        assertFalse(BackupParser.parse(backup().toByteArray()).activities.first().archived)
        assertThrows(BackupException::class.java) {
            BackupParser.parse(backup().replace("Research\ticon\t0\t0", "Research\ticon\t0\t9").toByteArray())
        }
    }

}
