package io.github.currency.core

import org.junit.Assert.*
import org.junit.Test
import java.math.BigDecimal
import java.time.ZoneId

class ActivityBreakdownTest {
    @Test fun `activity rows reconcile credits while showing overlapping paused and free time`() {
        val snapshot = Snapshot(listOf(Activity(1, "Thesis"), Activity(2, "Cooking", archived = true),
            Activity(3, "Exercise"), Activity(4, "Thesis")), listOf(
            Record(1, 1, 0, 6_000_000), Record(2, 2, 3_000_000, 6_000_000),
            Record(3, 1, 6_000_000, 9_000_000, setOf(1)),
            Record(4, 3, 0, 3_000_000), Record(5, 4, 9_000_000, 12_000_000),
            Record(6, 1, 0, 20_000_000)), mapOf(1L to "Pausa"))
        val trial = Trial(1_000_000, 0, 0, Totals())
        val ledger = Economy.ledger(snapshot, mappings(snapshot), trial.activatedAt, 10_000_000, ZoneId.of("UTC"), 12_000_000)
        val rows = activityBreakdown(snapshot, ledger, trial, 0, 10_000_000, 12_000_000)
        val byId = rows.associateBy { it.activityId }
        assertEquals(8_000_000L, byId.getValue(1).recordedMs)
        assertEquals(2_000_000L, byId.getValue(1).countedMs)
        assertEquals(0, BigDecimal("-0.25").compareTo(byId.getValue(2).account.balance))
        assertEquals(0L, byId.getValue(3).countedMs)
        assertEquals(1_000_000L, byId.getValue(4).countedMs)
        val total = Economy.account(ledger.totals, trial)
        fun rounded(value: BigDecimal) = value.setScale(12, java.math.RoundingMode.HALF_UP)
        assertEquals(rounded(total.earned), rounded(rows.fold(BigDecimal.ZERO) { sum, row -> sum + row.account.earned }))
        assertEquals(rounded(total.spent), rounded(rows.fold(BigDecimal.ZERO) { sum, row -> sum + row.account.spent }))
        assertEquals(emptyList<ActivityBreakdown>(), activityBreakdown(snapshot, ledger, trial, 0, trial.activatedAt, 12_000_000))
    }
}
