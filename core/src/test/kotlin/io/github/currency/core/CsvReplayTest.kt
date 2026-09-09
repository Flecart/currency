package io.github.currency.core

import org.junit.Assert.*
import org.junit.Test
import java.time.ZoneId

class CsvReplayTest {
    @Test fun `CSV reader handles quoted commas newlines and uses timestamps instead of rounded minutes`() {
        val csv = "activity name,time started,time ended,comment,categories,record tags,duration,duration minutes\n" +
            "\"Work, A\",2026-09-01 10:00:00,2026-09-01 10:00:31,\"line one\nline two\",Work,\"lab1, Admin\",0:0:31,0\n"
        val snapshot = CsvReplay.read(csv, ZoneId.of("Europe/Rome"))
        assertEquals("Work, A", snapshot.activities.single().name)
        assertEquals(2, snapshot.records.single().tags.size)
        assertEquals(31_000, snapshot.records.single().end - snapshot.records.single().start)
    }
    @Test fun `explicit zone handles daylight saving elapsed time`() {
        val csv = "activity name,time started,time ended,comment,categories,record tags\n" +
            "Work,2026-03-29 01:30:00,2026-03-29 03:30:00,,Work,\n"
        val record = CsvReplay.read(csv, ZoneId.of("Europe/Rome")).records.single()
        assertEquals(3_600_000, record.end - record.start)
    }
}
