package io.github.currency.core

data class Activity(val id: Long, val name: String, val categories: Set<String> = emptySet(), val archived: Boolean = false)
data class Record(val id: Long, val activityId: Long, val start: Long, val end: Long, val tags: Set<Long> = emptySet())
data class Snapshot(val activities: List<Activity>, val records: List<Record>, val tags: Map<Long, String>)
enum class Kind { WORK, SIDE, LEISURE, COOKING, TRAVEL, FRIENDS, SLEEP, NEUTRAL }

fun classify(activity: Activity): Kind = when {
    activity.name.trim().equals("svago", ignoreCase = true) -> Kind.LEISURE
    activity.name.trim().equals("cooking", ignoreCase = true) -> Kind.COOKING
    activity.name.trim().equals("travel", ignoreCase = true) -> Kind.TRAVEL
    activity.name.trim().equals("friends", ignoreCase = true) -> Kind.FRIENDS
    activity.name.trim().equals("sleep", ignoreCase = true) -> Kind.SLEEP
    activity.name.trim().lowercase(java.util.Locale.ROOT) in setOf("esplorazioni", "sides") -> Kind.SIDE
    activity.name.trim().lowercase(java.util.Locale.ROOT) in setOf("thesis", "oxford", "zhijing", "aria") -> Kind.WORK
    activity.categories.any { it.trim().equals("Work", ignoreCase = true) } -> Kind.WORK
    else -> Kind.NEUTRAL
}

/** Keep known IDs assigned across renames and category edits during the trial. */
fun mappings(snapshot: Snapshot, existing: Map<Long, Kind> = emptyMap()): Map<Long, Kind> =
    existing + snapshot.activities.associate { it.id to (existing[it.id] ?: classify(it)) }
