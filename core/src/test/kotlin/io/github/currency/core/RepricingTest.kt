package io.github.currency.core

import org.junit.Assert.*
import org.junit.Test
import java.math.BigDecimal

class RepricingTest {
    private val hour = 3_600_000L
    private fun same(expected: String, actual: BigDecimal) =
        assertEquals(0, BigDecimal(expected).compareTo(actual.setScale(10, java.math.RoundingMode.HALF_UP)))

    @Test fun `recalibration prices later sessions only and keeps earlier credits`() {
        val snapshot = Snapshot(listOf(Activity(1, "Thesis"), Activity(2, "Svago"), Activity(3, "Cooking")), listOf(
            Record(1, 1, 0, 10 * hour), Record(2, 2, 10 * hour, 20 * hour),
            Record(3, 2, 19 * hour, 21 * hour), Record(4, 3, 30 * hour, 32 * hour)), emptyMap())
        val kinds = mappings(snapshot)
        // 50 svago minutes per credit before repricing: baseline work equals svago / 1.05.
        val trial = Trial(0, 0, 0, Totals(workMs = 100 * hour, leisureMs = 105 * hour), "UTC")
        val repriced = Economy.recalibrate(snapshot, kinds, trial, 20 * hour)
        assertEquals(Repricing(20 * hour, 0, Totals(workMs = 10 * hour, leisureMs = 10 * hour)), repriced.repricing)
        same("47.6190476190", Economy.leisureMinutesPerCredit(repriced))
        same("50", Economy.leisureMinutesPerCredit(repriced.original()))

        val ledger = Economy.priced(snapshot, kinds, 0, 40 * hour, repriced)
        assertEquals(Economy.totals(snapshot, kinds, 0, 40 * hour, java.time.ZoneId.of("UTC")), ledger.totals)
        // Ten svago hours at the old price, then one svago hour and two cooking hours at the new one.
        same("12", ledger.account().earned)
        same("13.89", ledger.account().spent)
        // Record 3's overlap belongs to the earlier record 2, leaving one hour after repricing.
        same("-1.26", ledger.sessionAccount(3).balance)
        same("-12", ledger.sessionAccount(2).balance)
        same("0.63", ledger.account { it.copy(workMs = 0, leisureMs = 0) }.spent)
        assertEquals(Ledger(), Economy.priced(snapshot, kinds, 0, 10 * hour, repriced).after)
    }
}
