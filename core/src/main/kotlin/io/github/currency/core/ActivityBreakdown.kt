package io.github.currency.core

data class ActivityBreakdown(val activityId: Long, val recordedMs: Long, val countedMs: Long, val account: Account)

/** Recorded time includes pauses/overlaps; credited time uses the already allocated ledger. */
fun activityBreakdown(snapshot: Snapshot, ledger: Ledger, trial: Trial, from: Long, until: Long,
    completedThrough: Long): List<ActivityBreakdown> {
    val start = maxOf(from, trial.activatedAt)
    if (until <= start) return emptyList()
    return snapshot.records.filter { it.end <= completedThrough && it.end > it.start && it.end > start && it.start < until }
        .groupBy { it.activityId }.map { (id, records) ->
            val totals = records.fold(Totals()) { sum, record -> sum + (ledger.sessions[record.id] ?: Totals()) }
            ActivityBreakdown(id,
                records.sumOf { minOf(it.end, until) - maxOf(it.start, start) },
                totals.workMs + totals.sideMs + totals.choresMs + totals.spendingMs,
                Economy.account(totals, trial))
        }.sortedWith(compareByDescending<ActivityBreakdown> { it.account.balance.abs() }.thenBy { it.activityId })
}
