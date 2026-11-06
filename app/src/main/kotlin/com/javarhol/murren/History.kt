package com.javarhol.murren

import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Splits (phone-free stretches) and the "ghost": your own usual day, built from
 * the same weekday in recent weeks. Time on phone is kept per clock hour so any
 * day can be replayed as a running total.
 */
object History {
    private const val MIN_SPLIT_MS = 5 * 60_000L
    private const val HOUR_MS = 60 * 60_000L
    private const val DAY_ORIGIN_MIN = 4 * 60 // days are drawn from 04:00

    data class Split(val start: Long, val end: Long, val rungs: Int) {
        val length get() = end - start
    }

    // --- Splits ---

    fun addSplit(day: Day, start: Long, end: Long, rungs: Int) {
        if (end - start < MIN_SPLIT_MS) return
        day.splits = (day.splits.split(';').filter { it.isNotBlank() } + "$start,$end,$rungs").joinToString(";")
    }

    fun splits(day: Day): List<Split> =
        day.splits.split(';').filter { it.isNotBlank() }.mapNotNull { row ->
            val f = row.split(',').mapNotNull { it.toLongOrNull() }
            if (f.size == 3) Split(f[0], f[1], f[2].toInt()) else null
        }

    // --- Hourly time on phone ---

    fun hourly(day: Day): LongArray {
        val parts = day.hourly.split(',').mapNotNull { it.toLongOrNull() }
        return if (parts.size == 24) parts.toLongArray() else LongArray(24)
    }

    fun addOnTime(day: Day, start: Long, end: Long) {
        val buckets = hourly(day)
        var t = start
        while (t < end) {
            val until = minOf(end, Clock.nextHour(t))
            buckets[Clock.hourOf(t)] += until - t
            t = until
        }
        day.hourly = buckets.joinToString(",")
    }

    /** Minutes of the day counted from 04:00, so evenings past midnight stay in order. */
    fun dayMinute(ms: Long) = (Clock.minuteOfDay(ms) - DAY_ORIGIN_MIN + 1440) % 1440

    /** Running total of time on phone (ms) by [minute] (counted from 04:00). */
    fun cumulative(buckets: LongArray, minute: Int): Long {
        var sum = 0L
        for (i in 0 until 24) {
            val bucket = buckets[(i + DAY_ORIGIN_MIN / 60) % 24]
            val from = i * 60
            when {
                minute >= from + 60 -> sum += bucket
                minute > from -> sum += bucket * (minute - from) / 60
            }
        }
        return sum
    }

    /** Today's running total right now, including the session in progress. */
    fun todaySoFar(p: Prefs, now: Long): Long {
        val open = if (p.sessionOpen && p.paceOn) now - p.sessionStart else 0
        return cumulative(hourly(p.day(p.dayKey)), dayMinute(now)) + open
    }

    // --- The ghost: your usual day ---

    data class Ghost(val label: String, val days: List<LongArray>) {
        /** Median running total across reference days at [minute]. */
        fun at(minute: Int): Long {
            val v = days.map { cumulative(it, minute) }.sorted()
            val n = v.size
            return if (n % 2 == 1) v[n / 2] else (v[n / 2 - 1] + v[n / 2]) / 2
        }
    }

    /** Same weekday over the last 4 weeks, or the last 7 days if there aren't 2 of those yet. */
    fun ghost(p: Prefs, key: String): Ghost? {
        val date = runCatching { LocalDate.parse(key) }.getOrNull() ?: return null
        val earlier = p.dayKeys().filter { it < key }.filter { p.day(it).hourly.isNotBlank() }
        val sameWeekday = earlier.filter { LocalDate.parse(it).dayOfWeek == date.dayOfWeek }.take(4)
        val weekday = date.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.getDefault())
        val (label, refs) = when {
            sameWeekday.size >= 2 -> "your usual $weekday" to sameWeekday
            earlier.size >= 2 -> "your usual day" to earlier.take(7)
            else -> return null
        }
        return Ghost(label, refs.map { hourly(p.day(it)) })
    }

    /** Your usual pace (min on phone per hour) by this time of day, or null without a ghost. */
    fun usualPace(p: Prefs, ghost: Ghost?, now: Long): Int? {
        ghost ?: return null
        val day = p.day(p.dayKey)
        val start = dayMinute(day.dayStart)
        val minute = dayMinute(now)
        val elapsedMin = minute - start
        if (elapsedMin < 30) return null
        val ms = ghost.at(minute) - ghost.at(start)
        return (ms / 60_000.0 / (elapsedMin / 60.0)).roundToInt()
    }

    /** Second half of the waking day had less time on phone than the first. */
    fun negativeSplit(day: Day): Boolean {
        if (day.dayStart == 0L || day.dayEnd == 0L) return false
        val buckets = hourly(day)
        val a = dayMinute(day.dayStart)
        val b = dayMinute(day.dayEnd).let { if (it <= a) it + 1440 else it }.coerceAtMost(1439)
        val mid = (a + b) / 2
        val first = cumulative(buckets, mid) - cumulative(buckets, a)
        val second = cumulative(buckets, b) - cumulative(buckets, mid)
        return first > 0 && second < first
    }
}
