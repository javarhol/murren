package com.javarhol.murren

/**
 * Learns quiet hours from when you actually sleep: the longest overnight
 * stretch the phone sits untouched. Learning from phone use instead would let
 * late-night scrolling push quiet hours later and shelter the habit.
 */
object Sleep {
    // Shorter sessions (checking the time at 3 am) don't end a night's rest.
    private const val MIN_BREAK_MS = 2 * 60_000L
    private const val MIN_NIGHT_MS = 3 * 60 * 60_000L
    private const val NIGHTS_KEPT = 14
    const val NIGHTS_NEEDED = 3
    private const val DAY_MIN = 24 * 60

    data class Night(val key: String, val start: Int, val end: Int, val lengthMs: Long)

    /** Call when a session ends. The rest before it, if long and overnight, is a night. */
    fun onSession(p: Prefs, start: Long, end: Long) {
        if (end - start < MIN_BREAK_MS) return
        if (p.restStart in 1 until start) record(p, p.restStart, start)
        p.restStart = end
    }

    private fun record(p: Prefs, from: Long, to: Long) {
        val length = to - from
        if (length < MIN_NIGHT_MS) return
        // The middle of the rest must fall in the night, 21:00–10:00.
        val mid = Clock.minuteOfDay(from + length / 2)
        if (mid in 10 * 60 until 21 * 60) return
        val night = Night(Clock.dateKey(to), Clock.minuteOfDay(from), Clock.minuteOfDay(to), length)
        val existing = nights(p)
        // Keep only the longest rest per morning.
        if (existing.any { it.key == night.key && it.lengthMs >= length }) return
        p.nights = (existing.filter { it.key != night.key } + night).sortedBy { it.key }.takeLast(NIGHTS_KEPT)
            .joinToString(";") { "${it.key},${it.start},${it.end},${it.lengthMs}" }
    }

    fun nights(p: Prefs): List<Night> =
        p.nights.split(';').filter { it.isNotBlank() }.mapNotNull { row ->
            val f = row.split(',')
            if (f.size != 4) return@mapNotNull null
            Night(f[0], f[1].toIntOrNull() ?: return@mapNotNull null, f[2].toIntOrNull() ?: return@mapNotNull null,
                f[3].toLongOrNull() ?: return@mapNotNull null)
        }

    /** Median bedtime and wake time, or null until enough nights are recorded. */
    fun learned(p: Prefs): Pair<Int, Int>? {
        val all = nights(p)
        if (all.size < NIGHTS_NEEDED) return null
        return medianMinute(all.map { it.start }) to medianMinute(all.map { it.end })
    }

    /** Apply the learned window, if learning is on and there's enough data. */
    fun relearn(p: Prefs) {
        if (!p.autoQuiet) return
        val (start, end) = learned(p) ?: return
        p.quietStart = start
        p.quietEnd = end
    }

    // Times are measured from noon so a median across midnight doesn't wrap.
    private fun medianMinute(minutes: List<Int>): Int {
        val fromNoon = minutes.map { (it - 12 * 60 + DAY_MIN) % DAY_MIN }.sorted()
        val n = fromNoon.size
        val median = if (n % 2 == 1) fromNoon[n / 2] else (fromNoon[n / 2 - 1] + fromNoon[n / 2]) / 2
        val rounded = (median + 2) / 5 * 5
        return (rounded + 12 * 60) % DAY_MIN
    }
}
