package io.github.currency.core

import java.math.BigDecimal
import java.math.MathContext
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.TreeSet

data class Totals(
    val workMs: Long = 0, val leisureMs: Long = 0, val sideMs: Long = 0,
    val cookingMs: Long = 0, val travelMs: Long = 0, val friendsMs: Long = 0, val sleepMs: Long = 0, val choresMs: Long = 0,
) {
    val spendingMs get() = leisureMs + cookingMs + travelMs + friendsMs + sleepMs
    operator fun plus(other: Totals) = Totals(workMs + other.workMs, leisureMs + other.leisureMs,
        sideMs + other.sideMs, cookingMs + other.cookingMs, travelMs + other.travelMs,
        friendsMs + other.friendsMs, sleepMs + other.sleepMs, choresMs + other.choresMs)
}
data class Trial(val activatedAt: Long, val baselineStart: Long, val baselineEnd: Long, val baseline: Totals,
    val zoneId: String = ZoneId.systemDefault().id) {
    val provisional get() = (baseline.workMs == 0L && baseline.sideMs == 0L) || baseline.leisureMs == 0L
    val endsAt get() = activatedAt + Duration.ofDays(14).toMillis()
}
data class Account(val earned: BigDecimal, val spent: BigDecimal) {
    val balance: BigDecimal get() = earned - spent
    val workMinutesToZero: BigDecimal get() = if (balance.signum() < 0) balance.negate().multiply(BigDecimal(50)) else BigDecimal.ZERO
}
/** Allocated durations use the same sweep as the balance; masked records have zero credit. */
data class Ledger(val totals: Totals = Totals(), val sessions: Map<Long, Totals> = emptyMap())

object Economy {
    private val creditMs = BigDecimal(3_000_000)
    private val context = MathContext.DECIMAL128
    private val challenge = BigDecimal("1.05")
    private val nightAllowanceMs = Duration.ofHours(9).toMillis()

    fun totals(snapshot: Snapshot, kinds: Map<Long, Kind>, from: Long, until: Long,
        zone: ZoneId = ZoneId.systemDefault(), completedThrough: Long = until): Totals =
        ledger(snapshot, kinds, from, until, zone, completedThrough).totals

    /** Spending wins over earning; equal prices use earliest start, then record ID. */
    fun ledger(snapshot: Snapshot, kinds: Map<Long, Kind>, from: Long, until: Long,
        zone: ZoneId = ZoneId.systemDefault(), completedThrough: Long = until): Ledger {
        if (until <= from) return Ledger()
        val completed = snapshot.records.filter { it.end <= completedThrough && it.end > it.start }
        // Keep pre-window sleep as context so midnight and trial activation cannot reset the allowance.
        val sleepCharges = sleepCharges(completed.filter { kinds[it.activityId] == Kind.SLEEP }, zone)
        data class Event(val time: Long, val record: Record? = null, val starts: Boolean = false, val sleepDelta: Int = 0)
        val events = mutableListOf<Event>()
        val sessions = linkedMapOf<Long, Totals>()
        for (record in completed) {
            if (record.end <= from || record.start >= until) continue
            sessions[record.id] = Totals()
            val kind = kinds[record.activityId] ?: Kind.NEUTRAL
            val paused = record.tags.any { snapshot.tags[it]?.trim()?.equals("Pausa", true) == true }
            if (kind == Kind.NEUTRAL || (paused && kind in setOf(Kind.WORK, Kind.SIDE, Kind.CHORES))) continue
            events += Event(maxOf(record.start, from), record, true)
            events += Event(minOf(record.end, until), record)
        }
        for ((start, end) in sleepCharges) {
            if (end <= from || start >= until) continue
            events += Event(maxOf(start, from), sleepDelta = 1)
            events += Event(minOf(end, until), sleepDelta = -1)
        }
        val order = compareBy<Record>({ it.start }, { it.id })
        val active = Kind.entries.associateWith { TreeSet(order) }
        var chargedSleep = 0
        var previous = from
        for ((time, changes) in events.groupBy { it.time }.toSortedMap()) {
            val ms = time - previous
            val candidates = active.filter { (kind, rows) -> rows.isNotEmpty() && (kind != Kind.SLEEP || chargedSleep > 0) }
            val winner = candidates.keys.maxWithOrNull(compareBy<Kind> { priority(it) }
                .thenByDescending { candidates.getValue(it).first().start }
                .thenByDescending { candidates.getValue(it).first().id })
            if (winner != null && ms > 0) {
                val id = active.getValue(winner).first().id
                sessions[id] = sessions.getValue(id) + durationFor(winner, ms)
            }
            for (change in changes) {
                chargedSleep += change.sleepDelta
                change.record?.let { record ->
                    val rows = active.getValue(kinds.getValue(record.activityId))
                    if (change.starts) rows.add(record) else rows.remove(record)
                }
            }
            previous = time
        }
        return Ledger(sessions.values.fold(Totals(), Totals::plus), sessions)
    }

    private fun priority(kind: Kind): Int = when (kind) {
        Kind.LEISURE -> 6
        Kind.COOKING, Kind.TRAVEL, Kind.SLEEP -> 5
        Kind.FRIENDS -> 4
        Kind.WORK -> 3
        Kind.SIDE -> 2
        Kind.CHORES -> 1
        Kind.NEUTRAL -> 0
    }

    private fun durationFor(kind: Kind, ms: Long): Totals = when (kind) {
        Kind.WORK -> Totals(workMs = ms)
        Kind.SIDE -> Totals(sideMs = ms)
        Kind.CHORES -> Totals(choresMs = ms)
        Kind.LEISURE -> Totals(leisureMs = ms)
        Kind.COOKING -> Totals(cookingMs = ms)
        Kind.TRAVEL -> Totals(travelMs = ms)
        Kind.FRIENDS -> Totals(friendsMs = ms)
        Kind.SLEEP -> Totals(sleepMs = ms)
        Kind.NEUTRAL -> Totals()
    }

    private fun sleepCharges(records: List<Record>, zone: ZoneId): List<Pair<Long, Long>> {
        // Union first: simultaneous sleep timers must not consume the nightly allowance twice.
        val merged = mutableListOf<Pair<Long, Long>>()
        for (record in records.sortedBy { it.start }) {
            val last = merged.lastOrNull()
            if (last != null && record.start <= last.second) {
                merged[merged.lastIndex] = last.first to maxOf(last.second, record.end)
            } else merged += record.start to record.end
        }
        val sleptByNight = mutableMapOf<LocalDate, Long>()
        val charged = mutableListOf<Pair<Long, Long>>()
        for ((start, end) in merged) {
            var cursor = start
            while (cursor < end) {
                val local = Instant.ofEpochMilli(cursor).atZone(zone)
                val date = local.toLocalDate()
                val afternoon = local.hour in 12 until 18
                val boundary = when {
                    local.hour < 12 -> date.atTime(12, 0)
                    afternoon -> date.atTime(18, 0)
                    else -> date.plusDays(1).atTime(12, 0)
                }.atZone(zone).toInstant().toEpochMilli()
                val stop = minOf(end, boundary)
                if (afternoon) charged += cursor to stop else {
                    val night = if (local.hour < 12) date.minusDays(1) else date
                    val slept = sleptByNight[night] ?: 0L
                    val free = (nightAllowanceMs - slept).coerceAtLeast(0)
                    if (stop - cursor > free) charged += (cursor + free) to stop
                    sleptByNight[night] = slept + stop - cursor
                }
                cursor = stop
            }
        }
        return charged
    }

    fun calibrate(snapshot: Snapshot, kinds: Map<Long, Kind>, activatedAt: Long): Trial {
        val start = activatedAt - Duration.ofDays(28).toMillis()
        // The original work/svago baseline remains the price anchor; small costs are added afterward.
        val baselineKinds = kinds.mapValues { (_, kind) -> if (kind in setOf(Kind.WORK, Kind.SIDE, Kind.LEISURE)) kind else Kind.NEUTRAL }
        return Trial(activatedAt, start, activatedAt, totals(snapshot, baselineKinds, start, activatedAt))
    }

    private fun weightedWork(totals: Totals): BigDecimal = BigDecimal(totals.workMs) + BigDecimal(totals.sideMs).multiply(BigDecimal("0.25"))
    private fun weightedSpending(totals: Totals): BigDecimal = BigDecimal(totals.leisureMs) +
        BigDecimal(totals.cookingMs + totals.travelMs + totals.sleepMs).multiply(BigDecimal("0.25")) +
        BigDecimal(totals.friendsMs).multiply(BigDecimal("0.05"))

    fun account(totals: Totals, trial: Trial): Account {
        val earned = (weightedWork(totals) + BigDecimal(totals.choresMs).multiply(BigDecimal("0.10"))).divide(creditMs, context)
        // Preserve the reference ratio; don't round the rate per minute or per record.
        val spent = if (trial.provisional) weightedSpending(totals).divide(creditMs, context) else
            weightedSpending(totals).multiply(weightedWork(trial.baseline)).multiply(challenge)
                .divide(BigDecimal(trial.baseline.leisureMs).multiply(creditMs), context)
        return Account(earned, spent)
    }

    fun leisureMinutesPerCredit(trial: Trial): BigDecimal = if (trial.provisional) BigDecimal(50) else
        BigDecimal(trial.baseline.leisureMs).multiply(BigDecimal(50))
            .divide(weightedWork(trial.baseline).multiply(challenge), context)
}
