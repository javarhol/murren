package com.javarhol.murren

import kotlin.math.roundToInt

/**
 * The pace clock: minutes on the phone in the last 60 minutes, exactly what
 * "/hr" says. It rises a minute per minute while you're in something not on
 * your allowlist, and falls as those minutes roll out of the hour, so an hour
 * away always reads 0.
 */
object Pace {
    private const val WINDOW_MS = 60 * 60_000L

    private data class Span(val start: Long, val end: Long)

    private fun spans(p: Prefs): List<Span> =
        p.onLog.split(';').filter { it.isNotBlank() }.mapNotNull { row ->
            val f = row.split(',').mapNotNull { it.toLongOrNull() }
            if (f.size == 2) Span(f[0], f[1]) else null
        }

    /** Time on the phone (ms) within the hour before [now], including a stretch still in progress. */
    private fun onInLastHour(p: Prefs, now: Long): Long {
        val from = now - WINDOW_MS
        val open = if (p.paceOn && p.paceAt > 0) listOf(Span(p.paceAt, now)) else emptyList()
        return (spans(p) + open).sumOf { (minOf(it.end, now) - maxOf(it.start, from)).coerceAtLeast(0) }
    }

    /** Share of the last hour spent off the phone, 0..1. */
    fun fraction(p: Prefs, now: Long): Double = 1 - onInLastHour(p, now).toDouble() / WINDOW_MS

    fun minutesPerHour(p: Prefs, now: Long): Int = (fraction(p, now) * 60).roundToInt()

    /** What the clock shows: minutes per hour and today's projection, on or off the phone. */
    data class Reading(val minutesPerHour: Int, val projectedToday: Long, val word: String)

    fun reading(p: Prefs, now: Long): Reading {
        val off = projectedOffToday(p, now)
        return if (p.showTimeOn) {
            Reading(60 - minutesPerHour(p, now), (wakingSpan(p, now) - off).coerceAtLeast(0), "on")
        } else {
            Reading(minutesPerHour(p, now), off, "off")
        }
    }

    private fun dayEnd(p: Prefs, now: Long) = Clock.nextAt(if (p.quietStart != p.quietEnd) p.quietStart else 0, now)

    /** Today's waking hours, from the start of the day to tonight's quiet hours. */
    private fun wakingSpan(p: Prefs, now: Long): Long {
        val day = p.day(p.dayKey)
        return if (day.dayStart == 0L) 0 else dayEnd(p, now) - day.dayStart
    }

    /**
     * Where the dot sits on the pacer line, from 0 (left, by the phone) to 1
     * (right, by the mountain): the share of the hour you're off the phone, so
     * the line is the ring unrolled and both always agree.
     */
    fun track(onPerHour: Double): Float = (1 - onPerHour / 60).coerceIn(0.0, 1.0).toFloat()

    fun onPerHour(p: Prefs, now: Long): Double = (1 - fraction(p, now)) * 60

    /** Switch between on the phone and off it, logging the stretch on it that just ended. */
    fun setOnPhone(p: Prefs, now: Long, on: Boolean) {
        if (p.paceAt != 0L && p.paceOn == on) return
        if (p.paceOn && p.paceAt > 0 && now > p.paceAt) {
            val kept = spans(p).filter { it.end > now - WINDOW_MS } + Span(p.paceAt, now)
            p.onLog = kept.joinToString(";") { "${it.start},${it.end}" }
        }
        p.paceAt = now
        p.paceOn = on
    }

    /** Time off the phone so far today, plus the rest of the waking day at the current pace. */
    fun projectedOffToday(p: Prefs, now: Long): Long {
        val day = p.day(p.dayKey)
        if (day.dayStart == 0L) return 0
        val currentSession = if (p.sessionOpen && p.paceOn) now - p.sessionStart else 0
        val offSoFar = (now - day.dayStart - day.compulsiveMs - currentSession).coerceAtLeast(0)
        return offSoFar + ((dayEnd(p, now) - now) * fraction(p, now)).toLong()
    }
}
