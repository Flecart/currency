package io.github.currency.core

import org.junit.Assert.*
import org.junit.Test
import java.math.BigDecimal
import java.time.Duration

class EconomyTest {
    private val activities = listOf(Activity(1, "Research", setOf("Work")), Activity(2, "svago"), Activity(3, "Exercise"), Activity(4, "Thesis"))
    private fun snapshot(vararg records: Record) = Snapshot(activities, records.toList(), mapOf(1L to "lab1", 2L to "lab2", 3L to "lab3", 4L to "Pausa", 5L to "Admin"))
    private val neutralRate = Trial(1_000, 0, 1_000, Totals())
    private fun equalCredit(expected: String, actual: BigDecimal) { assertEquals(0, BigDecimal(expected).compareTo(actual)) }

    @Test fun `fractional time credits do not require a fifty minute block`() {
        val s = snapshot(Record(1, 1, 1_000, 901_000))
        equalCredit("0.3", Economy.account(Economy.totals(s, mappings(s), 1_000, 901_000), neutralRate).balance)
    }

    @Test fun `lab tags and admin earn equally pause does not and exercise is neutral`() {
        val rows = (1L..5L).map { Record(it, 1, it * 3_000_000, (it + 1) * 3_000_000, setOf(it)) } + Record(6, 3, 20_000_000, 23_000_000)
        val s = snapshot(*rows.toTypedArray())
        equalCredit("4", Economy.account(Economy.totals(s, mappings(s), 0, 24_000_000), neutralRate).balance)
    }

    @Test fun `leisure spends past zero with no debt multiplier`() {
        val result = Economy.account(Totals(3_000_000, 9_000_000), neutralRate)
        equalCredit("-2", result.balance)
        equalCredit("100", result.workMinutesToZero)
    }

    @Test fun `pause overrides work project tags but never makes svago free`() {
        val s = snapshot(Record(1, 1, 1000, 3001000, setOf(2, 4)), Record(2, 2, 3001000, 6001000, setOf(4)))
        assertEquals(Totals(0, 3_000_000), Economy.totals(s, mappings(s), 0, 6001000))
    }

    @Test fun `overlap counts once with leisure taking precedence`() {
        val s = snapshot(Record(1, 1, 1000, 7000), Record(2, 4, 2000, 9000), Record(3, 2, 3000, 5000), Record(4, 2, 4000, 6000))
        assertEquals(Totals(5000, 3000), Economy.totals(s, mappings(s), 0, 10000))
    }

    @Test fun `splitting a record does not change its value`() {
        val whole = snapshot(Record(1, 1, 1000, 3001000))
        val split = snapshot(Record(1, 1, 1000, 1177), Record(2, 1, 1177, 3001000))
        assertEquals(Economy.totals(whole, mappings(whole), 0, 4000000), Economy.totals(split, mappings(split), 0, 4000000))
    }

    @Test fun `activation clips previous time and future records do not earn`() {
        val s = snapshot(Record(1, 1, 1000, 5000), Record(2, 1, 5000, 10000))
        assertEquals(Totals(2000, 0), Economy.totals(s, mappings(s), 3000, 6000))
    }

    @Test fun `baseline ratio charges exactly five percent more and activation is zero`() {
        val now = Duration.ofDays(40).toMillis()
        val s = snapshot(Record(1, 1, now - 6_000_000, now - 3_000_000), Record(2, 2, now - 3_000_000, now))
        val trial = Economy.calibrate(s, mappings(s), now)
        val account = Economy.account(trial.baseline, trial)
        equalCredit("1", account.earned)
        equalCredit("1.05", account.spent)
        equalCredit("0", Economy.account(Economy.totals(s, mappings(s), now, now), trial).balance)
        assertEquals(now - Duration.ofDays(28).toMillis(), trial.baselineStart)
        assertFalse(trial.provisional)
    }

    @Test fun `no usable baseline has explicit fifty-fifty fallback`() {
        assertTrue(neutralRate.provisional)
        equalCredit("50", Economy.leisureMinutesPerCredit(neutralRate))
    }

    @Test fun `identity mappings survive renames new work activities inherit and others are neutral`() {
        val original = mappings(snapshot())
        val renamed = Snapshot(listOf(Activity(1, "Other name"), Activity(2, "Entertainment"), Activity(8, "New research", setOf("Work")), Activity(9, "Hobby")), emptyList(), emptyMap())
        val result = mappings(renamed, original)
        assertEquals(Kind.WORK, result[1])
        assertEquals(Kind.LEISURE, result[2])
        assertEquals(Kind.WORK, result[8])
        assertEquals(Kind.NEUTRAL, result[9])
    }
    @Test fun `side activities earn a quarter even in Work and main projects earn fully`() {
        for (name in listOf("esplorazioni", " SIDES ")) assertEquals(Kind.SIDE, classify(Activity(9, name, setOf("Work"))))
        for (name in listOf("thesis", "oxford", "zhijing", "aria")) assertEquals(Kind.WORK, classify(Activity(9, name)))
        equalCredit("0.25", Economy.account(Totals(sideMs = 3_000_000), neutralRate).earned)
        val baseline = Totals(3_000_000, 3_000_000, 3_000_000)
        val result = Economy.account(baseline, Trial(1, 0, 1, baseline))
        equalCredit("1.25", result.earned)
        equalCredit("1.3125", result.spent)
    }

    @Test fun `side overlaps use highest earning rate once and respect pauses and svago`() {
        val s = Snapshot(listOf(Activity(1, "Thesis"), Activity(2, "sides"), Activity(3, "svago")),
            listOf(Record(1, 2, 1000, 11000), Record(2, 2, 2000, 10000),
                Record(3, 1, 3000, 7000), Record(4, 3, 5000, 6000)), emptyMap())
        assertEquals(Totals(3000, 1000, 6000), Economy.totals(s, mappings(s), 0, 11000))
        val paused = s.copy(records = listOf(Record(1, 2, 1000, 11000, setOf(1))), tags = mapOf(1L to " Pausa "))
        assertEquals(Totals(), Economy.totals(paused, mappings(paused), 0, 11000))
        val archived = s.copy(activities = s.activities.map { it.copy(archived = true) })
        assertEquals(Economy.totals(s, mappings(s), 0, 11000), Economy.totals(archived, mappings(archived), 0, 11000))
    }

    @Test fun `chores earn a tenth respect pauses and overlaps and do not calibrate prices`() {
        val activities = listOf(Activity(1, " CHORES ", setOf("Work")), Activity(2, "sides"), Activity(3, "svago"))
        val s = Snapshot(activities, listOf(Record(1, 1, 0, 3_000_000)), mapOf(1L to "Pausa"))
        assertEquals(Kind.CHORES, classify(activities.first()))
        val ledger = Economy.ledger(s, mappings(s), 0, 3_000_000)
        assertEquals(Totals(choresMs = 3_000_000), ledger.totals)
        equalCredit("0.1", Economy.account(ledger.totals, neutralRate).earned)
        equalCredit("0", Economy.account(ledger.totals, neutralRate).spent)
        equalCredit("0.1", Economy.account(ledger.sessions.getValue(1), neutralRate).balance)
        val paused = s.copy(records = listOf(s.records.first().copy(tags = setOf(1))))
        assertEquals(Totals(), Economy.totals(paused, mappings(paused), 0, 3_000_000))
        val overlap = s.copy(records = s.records + Record(2, 2, 0, 2_000_000) + Record(3, 3, 0, 1_000_000))
        assertEquals(Totals(sideMs = 1_000_000, leisureMs = 1_000_000, choresMs = 1_000_000),
            Economy.totals(overlap, mappings(overlap), 0, 3_000_000))
        assertEquals(Totals(), Economy.calibrate(s, mappings(s), 3_000_000).baseline)
        val calibrated = Trial(1, 0, 1, Totals(workMs = 6_000_000, leisureMs = 3_000_000))
        equalCredit("0.1", Economy.account(ledger.totals, calibrated).earned)
    }

}
