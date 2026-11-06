package com.javarhol.murren

import kotlin.math.roundToInt

/** The finish card: facts about one day. No streaks. */
object Summary {
    fun text(p: Prefs, key: String, now: Long): String {
        val day = p.day(key)
        if (day.dayStart == 0L) return "Nothing yet."
        val end = if (day.dayEnd > 0) day.dayEnd else now
        val awake = (end - day.dayStart).coerceAtLeast(1)
        val onPhone = minOf(day.compulsiveMs, awake)
        val longest = maxOf(day.longestMs, end - day.lastCompulsiveEnd)
        val avg = (onPhone / 60_000.0 / (awake / 3_600_000.0)).roundToInt()

        val lines = mutableListOf<String>()
        if (day.overnight == Day.OVERNIGHT_CLEAN) lines += "Phone-free overnight ✓"
        lines += if (p.showTimeOn) {
            "${Clock.formatDuration(onPhone)} on phone · average $avg/hr"
        } else {
            "${Clock.formatDuration(awake - onPhone)} phone-free · average ${60 - avg}/hr off"
        }

        // The stretch still running counts as a split, as does the one the finish line closed.
        val openSplit = if (day.dayEnd == 0L && end - day.lastCompulsiveEnd >= 5 * 60_000L) 1 else 0
        val splits = History.splits(day).size + openSplit
        if (splits > 0) lines += "$splits splits · longest ${Clock.formatDuration(longest)}"
        if (History.negativeSplit(day)) lines += "Negative split ✓ (less phone in the second half)"

        History.ghost(p, key)?.let { ghost ->
            val minute = History.dayMinute(end)
            val today = History.cumulative(History.hourly(day), minute)
            val diff = (ghost.at(minute) - ghost.at(History.dayMinute(day.dayStart)) - today) / 60_000
            lines += when {
                diff > 0 -> "$diff min under ${ghost.label}"
                diff < 0 -> "${-diff} min over ${ghost.label}"
                else -> "Even with ${ghost.label}"
            }
        }

        val extras = listOfNotNull(
            day.peopleMs.takeIf { it >= 60_000 }?.let { "+${Clock.formatDuration(it)} for people" },
            day.quick.takeIf { it > 0 }?.let { "$it quick checks" },
        )
        if (extras.isNotEmpty()) lines += extras.joinToString(" · ")
        if (day.buzzes > 0) lines += "Unlocked right after a split buzz: ${day.buzzUnlocks} of ${day.buzzes}"
        return lines.joinToString("\n")
    }
}
