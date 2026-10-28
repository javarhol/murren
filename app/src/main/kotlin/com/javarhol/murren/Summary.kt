package com.javarhol.murren

/** Facts about one day. No streaks, no comparisons. */
object Summary {
    fun text(p: Prefs, key: String, now: Long): String {
        val day = p.day(key)
        if (day.dayStart == 0L) return "Nothing yet."
        val end = if (day.dayEnd > 0) day.dayEnd else now
        val phoneFree = (end - day.dayStart - day.compulsiveMs).coerceAtLeast(0)
        val longest = maxOf(day.longestMs, end - day.lastCompulsiveEnd)

        val lines = mutableListOf<String>()
        if (day.overnight == Day.OVERNIGHT_CLEAN) lines += "Phone-free overnight ✓"
        lines += "${Clock.formatDuration(phoneFree)} phone-free · longest stretch ${Clock.formatDuration(longest)}"
        if (day.unlocks > 0) {
            val parts = listOfNotNull(
                day.people.takeIf { it > 0 }?.let { "$it for people" },
                day.quick.takeIf { it > 0 }?.let { "$it quick checks" },
                day.compulsive.takeIf { it > 0 }?.let { "$it others" },
            )
            lines += "${day.unlocks} unlocks — ${parts.joinToString(", ")}"
        }
        return lines.joinToString("\n")
    }
}
