package com.javarhol.murren

import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

object Clock {
    fun now(): Long = System.currentTimeMillis()

    private fun local(ms: Long): LocalDateTime =
        LocalDateTime.ofInstant(Instant.ofEpochMilli(ms), ZoneId.systemDefault())

    private fun LocalDateTime.millis(): Long =
        atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

    fun inQuiet(p: Prefs, now: Long): Boolean {
        val t = local(now)
        val m = t.hour * 60 + t.minute
        val s = p.quietStart
        val e = p.quietEnd
        return when {
            s == e -> false
            s < e -> m in s until e
            else -> m >= s || m < e
        }
    }

    /** The day a moment belongs to: the date of the most recent end of quiet hours. */
    fun dayKey(p: Prefs, now: Long): String {
        val t = local(now)
        val endToday = t.toLocalDate().atTime(p.quietEnd / 60, p.quietEnd % 60)
        val date = if (t.isBefore(endToday)) t.toLocalDate().minusDays(1) else t.toLocalDate()
        return date.toString()
    }

    /** Next time strictly after [from] that the clock reads [minuteOfDay]. */
    fun nextAt(minuteOfDay: Int, from: Long): Long {
        val t = local(from)
        var at = t.toLocalDate().atTime(minuteOfDay / 60, minuteOfDay % 60)
        if (!at.isAfter(t)) at = at.plusDays(1)
        return at.millis()
    }

    fun formatMinute(minuteOfDay: Int): String = "%02d:%02d".format(minuteOfDay / 60, minuteOfDay % 60)

    fun formatDuration(ms: Long): String {
        val minutes = ms / 60_000
        val h = minutes / 60
        val m = minutes % 60
        return if (h > 0) "${h}h ${m}m" else "${m}m"
    }
}
