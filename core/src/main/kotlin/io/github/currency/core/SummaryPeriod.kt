package io.github.currency.core

import java.time.DayOfWeek
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters

enum class SummaryPeriod(val label: String) {
    TODAY("Today"), LAST_DAYS("Last N days"), THIS_WEEK("This week"),
    LAST_WEEK("Last week"), THIS_MONTH("This month"), LAST_MONTH("Last month");

    /** Calendar boundaries in the trial zone; rolling days include today. End is exclusive. */
    fun window(now: Long, zone: ZoneId, days: Int = 7): Pair<Long, Long> {
        require(days in 1..3650)
        val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
        val monday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        val month = today.withDayOfMonth(1)
        val start = when (this) {
            TODAY -> today
            LAST_DAYS -> today.minusDays(days.toLong() - 1)
            THIS_WEEK -> monday
            LAST_WEEK -> monday.minusWeeks(1)
            THIS_MONTH -> month
            LAST_MONTH -> month.minusMonths(1)
        }
        val end = when (this) {
            LAST_WEEK -> monday.atStartOfDay(zone).toInstant().toEpochMilli()
            LAST_MONTH -> month.atStartOfDay(zone).toInstant().toEpochMilli()
            else -> now
        }
        return start.atStartOfDay(zone).toInstant().toEpochMilli() to end
    }
}
