package com.javarhol.murren

import kotlin.math.roundToInt

/** The finish card: facts about one day as short label + value. No streaks. */
object Summary {
    fun rows(p: Prefs, key: String, now: Long): List<Pair<String, String>> {
        val day = p.day(key)
        if (day.dayStart == 0L) return listOf("Today" to "–")
        val end = if (day.dayEnd > 0) day.dayEnd else now
        val awake = (end - day.dayStart).coerceAtLeast(1)
        val onPhone = minOf(if (key == p.dayKey && day.dayEnd == 0L) History.todaySoFar(p, now) else History.dayTotal(day), awake)
        val longest = maxOf(day.longestMs, end - day.lastCompulsiveEnd)
        val avg = (onPhone / 60_000.0 / (awake / 3_600_000.0)).roundToInt()

        val rows = mutableListOf<Pair<String, String>>()
        if (day.overnight == Day.OVERNIGHT_CLEAN) rows += "Night" to "phone-free ✓"
        rows += if (p.showTimeOn) {
            "On phone" to "${Clock.formatDuration(onPhone)} · avg $avg/hr"
        } else {
            "Off phone" to "${Clock.formatDuration(awake - onPhone)} · avg ${60 - avg}/hr"
        }

        // The stretch still running counts as a split, as does the one the finish line closed.
        val openSplit = if (day.dayEnd == 0L && end - day.lastCompulsiveEnd >= 5 * 60_000L) 1 else 0
        val splits = History.splits(day).size + openSplit
        if (splits > 0) rows += "Splits" to "$splits · longest ${Clock.formatClock(longest)}"
        if (History.negativeSplit(day)) rows += "Negative split" to "less phone in the 2nd half ✓"
        val sprints = Sprint.runs(day)
        if (sprints.isNotEmpty()) rows += "Zack Zack" to sprints.joinToString(" · ") { Sprint.describe(it) }

        History.ghost(p, key)?.let { ghost ->
            val minute = History.dayMinute(end)
            val today = History.cumulative(History.hourly(day), minute)
            val under = (ghost.at(minute) - ghost.at(History.dayMinute(day.dayStart)) - today) / 60_000
            rows += "Vs usual" to "${Notifier.delta(under)} min"
        }

        if (day.peopleMs >= 60_000) rows += "People" to Clock.formatDuration(day.peopleMs)
        if (day.quick > 0) rows += "Quick checks" to "${day.quick}"
        if (day.buzzes > 0) rows += "Buzz → unlock" to "${day.buzzUnlocks} of ${day.buzzes}"
        return rows
    }

    fun text(p: Prefs, key: String, now: Long): String =
        rows(p, key, now).joinToString("\n") { (label, line) -> "$label: $line" }
}
