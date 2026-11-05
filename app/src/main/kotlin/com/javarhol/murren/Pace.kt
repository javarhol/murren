package com.javarhol.murren

import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.roundToInt

/**
 * The pace clock: a smoothed share of time spent off the phone.
 *
 * It's an exponential moving average with a 45-minute half-life, so it ticks
 * down about a minute per minute while you scroll something not on your
 * allowlist, and starts climbing back the moment you put the phone down.
 */
object Pace {
    private const val HALF_LIFE_MS = 45 * 60_000.0
    private val DECAY = ln(2.0) / HALF_LIFE_MS

    fun fraction(p: Prefs, now: Long): Double {
        if (p.paceAt == 0L) return 1.0
        val target = if (p.paceOn) 0.0 else 1.0
        val elapsed = (now - p.paceAt).coerceAtLeast(0)
        return target + (p.pace - target) * exp(-DECAY * elapsed)
    }

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

    /** Switch between on the phone and off it, settling the average up to now. */
    fun setOnPhone(p: Prefs, now: Long, on: Boolean) {
        if (p.paceAt != 0L && p.paceOn == on) return
        p.pace = fraction(p, now).toFloat()
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
