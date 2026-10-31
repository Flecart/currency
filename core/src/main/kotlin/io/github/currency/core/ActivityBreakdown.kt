package io.github.currency.core

import java.math.BigDecimal

data class ActivityBreakdown(val activityId: Long, val recordedMs: Long, val countedMs: Long, val account: Account)

/** Recorded time includes pauses/overlaps; credited time uses the already allocated ledger. */
fun activityBreakdown(snapshot: Snapshot, ledger: PricedLedger, from: Long, until: Long,
    completedThrough: Long): List<ActivityBreakdown> {
    val start = maxOf(from, ledger.trial.activatedAt)
    if (until <= start) return emptyList()
    return snapshot.records.filter { it.end <= completedThrough && it.end > it.start && it.end > start && it.start < until }
        .groupBy { it.activityId }.map { (id, records) ->
            val totals = records.fold(Totals()) { sum, record -> sum + ledger.session(record.id) }
            ActivityBreakdown(id,
                records.sumOf { minOf(it.end, until) - maxOf(it.start, start) },
                totals.workMs + totals.sideMs + totals.choresMs + totals.spendingMs,
                records.fold(Account(BigDecimal.ZERO, BigDecimal.ZERO)) { sum, record -> sum + ledger.sessionAccount(record.id) })
        }.sortedWith(compareByDescending<ActivityBreakdown> { it.account.balance.abs() }.thenBy { it.activityId })
}
