package io.github.currency.core

import java.math.BigDecimal
import java.math.MathContext
import java.time.Duration

data class Totals(val workMs: Long = 0, val leisureMs: Long = 0, val sideMs: Long = 0)
data class Trial(val activatedAt: Long, val baselineStart: Long, val baselineEnd: Long, val baseline: Totals) {
    val provisional get() = (baseline.workMs == 0L && baseline.sideMs == 0L) || baseline.leisureMs == 0L
    val endsAt get() = activatedAt + Duration.ofDays(14).toMillis()
}
data class Account(val earned: BigDecimal, val spent: BigDecimal) {
    val balance: BigDecimal get() = earned - spent
    val workMinutesToZero: BigDecimal get() = if (balance.signum() < 0) balance.negate().multiply(BigDecimal(50)) else BigDecimal.ZERO
}

object Economy {
    private val creditMs = BigDecimal(3_000_000)
    private val context = MathContext.DECIMAL128
    private val challenge = BigDecimal("1.05")

    /** A sweep of endpoints makes overlapping timers and splitting records economically equivalent. */
    fun totals(snapshot: Snapshot, kinds: Map<Long, Kind>, from: Long, until: Long): Totals {
        if (until <= from) return Totals()
        data class Change(val time: Long, val work: Int, val leisure: Int, val side: Int)
        val changes = mutableListOf<Change>()
        for (record in snapshot.records) {
            // Backups may include manually entered future records; don't mint for unfinished work.
            if (record.end > until) continue
            val start = maxOf(record.start, from)
            val end = minOf(record.end, until)
            if (end <= start) continue
            val paused = record.tags.any { snapshot.tags[it]?.trim()?.equals("Pausa", true) == true }
            val kind = kinds[record.activityId] ?: Kind.NEUTRAL
            val work = if (kind == Kind.WORK && !paused) 1 else 0
            val leisure = if (kind == Kind.LEISURE) 1 else 0
            val side = if (kind == Kind.SIDE && !paused) 1 else 0
            if (work + leisure + side == 0) continue
            changes += Change(start, work, leisure, side)
            changes += Change(end, -work, -leisure, -side)
        }
        var previous = from
        var activeWork = 0
        var activeLeisure = 0
        var activeSide = 0
        var workMs = 0L
        var leisureMs = 0L
        var sideMs = 0L
        for (change in changes.sortedBy { it.time }) {
            val duration = change.time - previous
            if (activeLeisure > 0) leisureMs += duration else if (activeWork > 0) workMs += duration else if (activeSide > 0) sideMs += duration
            activeWork += change.work
            activeLeisure += change.leisure
            activeSide += change.side
            previous = change.time
        }
        return Totals(workMs, leisureMs, sideMs)
    }

    fun calibrate(snapshot: Snapshot, kinds: Map<Long, Kind>, activatedAt: Long): Trial {
        val start = activatedAt - Duration.ofDays(28).toMillis()
        return Trial(activatedAt, start, activatedAt, totals(snapshot, kinds, start, activatedAt))
    }

    private fun weightedWork(totals: Totals): BigDecimal = BigDecimal(totals.workMs) + BigDecimal(totals.sideMs).multiply(BigDecimal("0.25"))

    fun account(totals: Totals, trial: Trial): Account {
        val earned = weightedWork(totals).divide(creditMs, context)
        // Preserve the reference ratio; don't round the rate per minute or per record.
        val spent = if (trial.provisional) BigDecimal(totals.leisureMs).divide(creditMs, context) else
            BigDecimal(totals.leisureMs).multiply(weightedWork(trial.baseline)).multiply(challenge)
                .divide(BigDecimal(trial.baseline.leisureMs).multiply(creditMs), context)
        return Account(earned, spent)
    }

    fun leisureMinutesPerCredit(trial: Trial): BigDecimal = if (trial.provisional) BigDecimal(50) else
        BigDecimal(trial.baseline.leisureMs).multiply(BigDecimal(50))
            .divide(weightedWork(trial.baseline).multiply(challenge), context)
}
