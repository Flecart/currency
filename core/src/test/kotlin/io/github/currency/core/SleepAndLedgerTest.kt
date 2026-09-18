package io.github.currency.core

import org.junit.Assert.*
import org.junit.Test
import java.math.BigDecimal
import java.time.Duration
import java.time.LocalDateTime
import java.time.ZoneId

class SleepAndLedgerTest {
    private val zone = ZoneId.of("Europe/Rome")
    private val hour = Duration.ofHours(1).toMillis()
    private val activities = listOf(Activity(1, "Sleep"), Activity(2, "Cooking"), Activity(3, "Travel"),
        Activity(4, "Friends"), Activity(5, "Thesis"), Activity(6, "svago"), Activity(7, "sides"))
    private fun at(time: String) = LocalDateTime.parse(time).atZone(zone).toInstant().toEpochMilli()
    private fun record(id: Long, start: String, end: String, activity: Long = 1) = Record(id, activity, at(start), at(end))
    private fun snapshot(vararg records: Record) = Snapshot(activities, records.toList(), emptyMap())
    private fun totals(s: Snapshot, from: Long = at("2026-01-01T00:00"), until: Long = at("2027-01-01T00:00")) =
        Economy.totals(s, mappings(s), from, until, zone)
    private fun credit(expected: String, actual: BigDecimal) = assertEquals(0, BigDecimal(expected).compareTo(actual))

    @Test fun `small costs follow the calibrated svago price and override Work category`() {
        for ((name, kind) in listOf(" cooking " to Kind.COOKING, "TRAVEL" to Kind.TRAVEL,
            "Friends" to Kind.FRIENDS, " PEOPLE " to Kind.FRIENDS, "Sleep" to Kind.SLEEP)) {
            assertEquals(kind, classify(Activity(99, name, setOf("Work"))))
        }
        val trial = Trial(0, 0, 0, Totals(workMs = 6_000_000, leisureMs = 3_000_000))
        val result = Economy.account(Totals(cookingMs = 3_000_000, travelMs = 3_000_000,
            friendsMs = 3_000_000, sleepMs = 3_000_000), trial)
        credit("1.68", result.spent)
        val s = snapshot(Record(1, 2, 0, 3_000_000, setOf(1))).copy(tags = mapOf(1L to "Pausa"))
        assertEquals(3_000_000L, totals(s, 0, 3_000_000).cookingMs)
    }

    @Test fun `ordinary sleep is free and only excess above nine hours costs`() {
        for (hours in listOf(8L, 9L, 10L)) {
            val start = at("2026-09-08T22:00")
            val s = snapshot(Record(1, 1, start, start + hours * hour))
            assertEquals((hours - 9).coerceAtLeast(0) * hour, totals(s).sleepMs)
        }
    }

    @Test fun `afternoon boundaries charge only the intersecting time`() {
        val s = snapshot(record(1, "2026-09-08T11:00", "2026-09-08T19:00"))
        assertEquals(6 * hour, totals(s).sleepMs)
        val nap = snapshot(record(1, "2026-09-08T13:00", "2026-09-08T14:00"))
        credit("0.30", Economy.account(totals(nap), Trial(0, 0, 0, Totals())).spent)
    }

    @Test fun `split nights gaps and duplicates share one allowance without counting naps`() {
        val s = snapshot(record(1, "2026-09-08T13:00", "2026-09-08T14:00"),
            record(2, "2026-09-08T21:00", "2026-09-09T02:00"),
            record(3, "2026-09-08T23:00", "2026-09-09T02:00"),
            record(4, "2026-09-09T03:00", "2026-09-09T08:00"),
            record(5, "2026-09-09T22:00", "2026-09-10T07:00"))
        assertEquals(2 * hour, totals(s).sleepMs)
        val ledger = Economy.ledger(s, mappings(s), at("2026-09-08T00:00"), at("2026-09-11T00:00"), zone)
        assertEquals(hour, ledger.sessions.getValue(1).sleepMs)
        assertEquals(hour, ledger.sessions.getValue(4).sleepMs)
        assertEquals(0L, ledger.sessions.getValue(3).sleepMs)
        assertEquals(0L, ledger.sessions.getValue(5).sleepMs)
    }

    @Test fun `today and activation windows retain sleep before their cutoff`() {
        val s = snapshot(record(1, "2026-09-08T20:00", "2026-09-09T02:00"),
            record(2, "2026-09-09T03:00", "2026-09-09T07:00"))
        val end = at("2026-09-09T12:00")
        assertEquals(hour, totals(s, at("2026-09-09T00:00"), end).sleepMs)
        assertEquals(hour, totals(s, at("2026-09-09T04:00"), end).sleepMs)
        assertEquals(hour / 2, totals(s, at("2026-09-09T06:30"), end).sleepMs)
    }

    @Test fun `morning sleep charges only excess inside a reporting window`() {
        val s = snapshot(record(1, "2026-09-08T01:00", "2026-09-08T06:00"),
            record(2, "2026-09-08T06:30", "2026-09-08T12:00"))
        val cut = at("2026-09-08T11:00")
        assertEquals(hour + hour / 2, totals(s).sleepMs)
        assertEquals(hour, totals(s, from = cut).sleepMs)
    }

    @Test fun `daylight saving uses elapsed sleep hours with local afternoon boundaries`() {
        val spring = snapshot(record(1, "2026-03-28T22:00", "2026-03-29T08:00"))
        val autumn = snapshot(record(1, "2026-10-24T22:00", "2026-10-25T08:00"))
        assertEquals(0L, totals(spring).sleepMs) // Ten clock hours, nine elapsed.
        assertEquals(2 * hour, totals(autumn).sleepMs) // Ten clock hours, eleven elapsed.
    }

    @Test fun `unfinished sleep contributes neither spending nor allowance context`() {
        val s = snapshot(record(1, "2026-09-08T18:00", "2026-09-09T10:00"),
            record(2, "2026-09-08T20:00", "2026-09-09T06:00"))
        assertEquals(hour, totals(s, until = at("2026-09-09T07:00")).sleepMs)
    }

    @Test fun `ledger allocates overlapping sessions once and sums to the balance`() {
        val s = snapshot(Record(1, 5, 0, 600_000), Record(2, 5, 0, 600_000),
            Record(3, 4, 100_000, 200_000), Record(4, 2, 150_000, 300_000),
            Record(5, 3, 150_000, 300_000), Record(6, 6, 250_000, 400_000),
            Record(7, 7, 500_000, 700_000))
        val result = Economy.ledger(s, mappings(s), 0, 700_000, zone)
        assertEquals(Totals(workMs = 300_000, leisureMs = 150_000, sideMs = 100_000,
            cookingMs = 100_000, friendsMs = 50_000), result.totals)
        assertEquals(Totals(), result.sessions[2])
        assertEquals(Totals(), result.sessions[5])
        assertEquals(result.totals, result.sessions.values.fold(Totals(), Totals::plus))
        val trial = Trial(0, 0, 0, Totals())
        val sum = result.sessions.values.map { Economy.account(it, trial).balance }.fold(BigDecimal.ZERO, BigDecimal::add)
        assertEquals(Economy.account(result.totals, trial).balance.setScale(12, java.math.RoundingMode.HALF_UP),
            sum.setScale(12, java.math.RoundingMode.HALF_UP))
        assertEquals(result, Economy.ledger(s.copy(records = s.records.reversed()), mappings(s), 0, 700_000, zone))
    }

    @Test fun `splitting sleep at allowance or afternoon boundaries preserves price`() {
        val whole = snapshot(record(1, "2026-09-08T22:00", "2026-09-09T14:00"))
        val split = snapshot(record(1, "2026-09-08T22:00", "2026-09-09T07:00"),
            record(2, "2026-09-09T07:00", "2026-09-09T12:00"),
            record(3, "2026-09-09T12:00", "2026-09-09T14:00"))
        assertEquals(7 * hour, totals(whole).sleepMs)
        assertEquals(totals(whole), totals(split))
    }

    @Test fun `overlapping svago masks sleep cost but does not reset its allowance`() {
        val s = snapshot(record(1, "2026-09-08T22:00", "2026-09-09T09:00"),
            record(2, "2026-09-09T06:00", "2026-09-09T08:00", activity = 6))
        val result = totals(s)
        assertEquals(2 * hour, result.leisureMs)
        assertEquals(hour, result.sleepMs)
        val ledger = Economy.ledger(s, mappings(s), at("2026-09-08T00:00"), at("2026-09-09T12:00"), zone)
        assertEquals(hour, ledger.sessions.getValue(1).sleepMs)
    }

    @Test fun `calibration excludes small costs even when they overlap work`() {
        val s = snapshot(Record(1, 5, 0, 3_000_000), Record(2, 2, 0, 3_000_000),
            Record(3, 6, 3_000_000, 6_000_000))
        assertEquals(Totals(workMs = 3_000_000, leisureMs = 3_000_000),
            Economy.calibrate(s, mappings(s), 6_000_000).baseline)
    }
}
