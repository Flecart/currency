package io.github.currency.core

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

class SummaryPeriodTest {
    private val zone = ZoneId.of("Europe/Rome")
    private fun at(value: String) = LocalDateTime.parse(value).atZone(zone).toInstant().toEpochMilli()

    @Test fun `calendar periods include leap months year changes and daylight saving`() {
        val now = at("2026-09-18T12:00")
        assertEquals(at("2026-09-18T00:00") to now, SummaryPeriod.TODAY.window(now, zone))
        assertEquals(at("2026-09-12T00:00") to now, SummaryPeriod.LAST_DAYS.window(now, zone, 7))
        assertEquals(at("2026-09-14T00:00") to now, SummaryPeriod.THIS_WEEK.window(now, zone))
        assertEquals(at("2026-09-07T00:00") to at("2026-09-14T00:00"), SummaryPeriod.LAST_WEEK.window(now, zone))
        assertEquals(at("2026-09-01T00:00") to now, SummaryPeriod.THIS_MONTH.window(now, zone))
        assertEquals(at("2024-02-01T00:00") to at("2024-03-01T00:00"),
            SummaryPeriod.LAST_MONTH.window(at("2024-03-15T12:00"), zone))
        assertEquals(at("2025-12-01T00:00") to at("2026-01-01T00:00"),
            SummaryPeriod.LAST_MONTH.window(at("2026-01-15T12:00"), zone))
        val spring = SummaryPeriod.LAST_DAYS.window(at("2026-03-30T00:00"), zone, 2)
        assertEquals(23 * 3_600_000L, spring.second - spring.first)
        assertThrows(IllegalArgumentException::class.java) { SummaryPeriod.LAST_DAYS.window(now, zone, 0) }
    }

    @Test fun `historical summaries clip completed sessions at both ends and retain sleep context`() {
        val activities = listOf(Activity(1, "Thesis"), Activity(2, "Sleep"))
        val snapshot = Snapshot(activities, listOf(
            Record(1, 1, at("2026-08-31T23:00"), at("2026-09-01T01:00")),
            Record(2, 2, at("2026-08-30T20:00"), at("2026-08-31T02:00")),
            Record(3, 2, at("2026-08-31T03:00"), at("2026-08-31T07:00")),
            Record(4, 1, at("2026-08-31T10:00"), at("2026-10-01T01:00"))
        ), emptyMap())
        val now = at("2026-09-18T12:00")
        val from = at("2026-08-31T00:00")
        val until = at("2026-09-01T00:00")
        val totals = Economy.totals(snapshot, mappings(snapshot), from, until, zone, now)
        assertEquals(Totals(workMs = 3_600_000, sleepMs = 3_600_000), totals)
        val ledger = Economy.ledger(snapshot, mappings(snapshot), from, until, zone, now)
        assertEquals(totals, ledger.sessions.values.fold(Totals(), Totals::plus))
        assertEquals(Totals(), Economy.totals(snapshot, mappings(snapshot), now, until, zone, now))
    }
}
