package com.javarhol.murren

import kotlin.math.roundToInt

/** The finish card: facts about one day as glyph + few words. No streaks. */
object Summary {
    fun rows(p: Prefs, key: String, now: Long): List<Pair<String, String>> {
        val day = p.day(key)
        if (day.dayStart == 0L) return listOf("·" to "–")
        val end = if (day.dayEnd > 0) day.dayEnd else now
        val awake = (end - day.dayStart).coerceAtLeast(1)
        val onPhone = minOf(day.compulsiveMs, awake)
        val longest = maxOf(day.longestMs, end - day.lastCompulsiveEnd)
        val avg = (onPhone / 60_000.0 / (awake / 3_600_000.0)).roundToInt()

        val rows = mutableListOf<Pair<String, String>>()
        if (day.overnight == Day.OVERNIGHT_CLEAN) rows += "☾" to "✓"
        rows += if (p.showTimeOn) {
            "◔" to "${Clock.formatDuration(onPhone)}   ⌀ $avg/hr"
        } else {
            "◕" to "${Clock.formatDuration(awake - onPhone)} off   ⌀ ${60 - avg}/hr"
        }

        // The stretch still running counts as a split, as does the one the finish line closed.
        val openSplit = if (day.dayEnd == 0L && end - day.lastCompulsiveEnd >= 5 * 60_000L) 1 else 0
        val splits = History.splits(day).size + openSplit
        if (splits > 0) rows += "▮" to "$splits splits   ↔ ${Clock.formatClock(longest)}"
        if (History.negativeSplit(day)) rows += "↘" to "negative split"
        val sprints = Sprint.runs(day)
        if (sprints.isNotEmpty()) rows += "Z" to sprints.joinToString("   ") { Sprint.describe(it) }

        History.ghost(p, key)?.let { ghost ->
            val minute = History.dayMinute(end)
            val today = History.cumulative(History.hourly(day), minute)
            val under = (ghost.at(minute) - ghost.at(History.dayMinute(day.dayStart)) - today) / 60_000
            rows += "±" to "${Notifier.delta(under)} vs usual"
        }

        val extras = listOfNotNull(
            day.peopleMs.takeIf { it >= 60_000 }?.let { "+${Clock.formatDuration(it)} people" },
            day.quick.takeIf { it > 0 }?.let { "$it quick" },
        )
        if (extras.isNotEmpty()) rows += "+" to extras.joinToString("   ")
        if (day.buzzes > 0) rows += "↺" to "${day.buzzUnlocks}/${day.buzzes} buzz → unlock"
        return rows
    }

    fun text(p: Prefs, key: String, now: Long): String =
        rows(p, key, now).joinToString("\n") { (glyph, line) -> "$glyph  $line" }
}
