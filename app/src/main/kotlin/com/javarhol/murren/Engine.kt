package com.javarhol.murren

import android.content.Context

/**
 * The reward ladder.
 *
 * Each day you climb 30 → 60 → 120 minutes of not unlocking, with a buzz at
 * each rung; after the top rung the day goes silent. A compulsive unlock drops
 * you one rung (never to zero, never announced) and restarts the clock from
 * screen-off. Allowlisted apps and quick checks don't count against you.
 */
object Engine {
    private val LADDER_MINUTES = longArrayOf(30, 60, 120)
    val DONE = LADDER_MINUTES.size
    const val QUICK_CHECKS_PER_HOUR = 4

    enum class Verdict { PEOPLE, QUICK, COMPULSIVE }

    fun start(ctx: Context) {
        val p = Prefs(ctx)
        val now = Clock.now()
        p.sessionOpen = false
        p.pendingReward = false
        val key = Clock.dayKey(p, now)
        if (p.dayKey != key) beginDay(ctx, p, now, key) else schedule(ctx, p, now)
        Alarms.scheduleQuiet(ctx, p, now)
    }

    /** Re-arm alarms after a settings change. */
    fun reschedule(ctx: Context) {
        val p = Prefs(ctx)
        if (!p.enabled) return
        val now = Clock.now()
        schedule(ctx, p, now)
        Alarms.scheduleQuiet(ctx, p, now)
    }

    fun onUnlock(ctx: Context) {
        val p = Prefs(ctx)
        val now = Clock.now()
        if (!Clock.inQuiet(p, now) && Clock.dayKey(p, now) != p.dayKey) {
            // The quiet-end alarm was missed; start the day without a buzz.
            beginDay(ctx, p, now, Clock.dayKey(p, now))
        }
        p.sessionOpen = true
        p.sessionStart = now
    }

    fun onScreenOff(ctx: Context) {
        val p = Prefs(ctx)
        if (!p.sessionOpen) return // screen woke for a notification, never unlocked
        val now = Clock.now()
        p.sessionOpen = false
        val start = p.sessionStart
        val verdict = Judge.judge(ctx, p, start, now)

        if (Clock.inQuiet(p, now)) {
            if (verdict == Verdict.COMPULSIVE) p.overnightClean = false
            return
        }

        val day = p.day(p.dayKey)
        day.unlocks += 1
        when (verdict) {
            Verdict.PEOPLE -> day.people += 1
            Verdict.QUICK -> {
                day.quick += 1
                p.addQuickCheck(now)
            }
            Verdict.COMPULSIVE -> {
                day.compulsive += 1
                day.compulsiveMs += now - start
                day.longestMs = maxOf(day.longestMs, start - day.lastCompulsiveEnd)
                day.lastCompulsiveEnd = now
            }
        }

        if (verdict == Verdict.COMPULSIVE) {
            p.pendingReward = false
            if (p.rung < DONE) p.rung = maxOf(0, p.rung - 1)
            p.stretchStart = now
            schedule(ctx, p, now)
        } else if (p.pendingReward) {
            // The rung was earned while you were on a forgiven unlock.
            p.pendingReward = false
            reward(ctx, p, now)
        }
    }

    fun onRewardAlarm(ctx: Context) {
        val p = Prefs(ctx)
        if (!p.enabled || p.rung >= DONE) return
        val now = Clock.now()
        if (Clock.inQuiet(p, now)) return
        if (now < target(p) - 5_000) {
            schedule(ctx, p, now) // stale alarm
            return
        }
        if (p.sessionOpen) {
            // Never buzz while the phone is in your hand; decide when the screen goes off.
            p.pendingReward = true
            return
        }
        reward(ctx, p, now)
    }

    fun onQuietStart(ctx: Context) {
        val p = Prefs(ctx)
        if (!p.enabled) return
        val now = Clock.now()
        Alarms.cancel(ctx, Alarms.REWARD)
        p.pendingReward = false
        p.overnightTracked = true
        p.overnightClean = true
        p.day(p.dayKey).dayEnd = now
        Notifier.postSummary(ctx, p.dayKey)
        Notifier.showPace(ctx)
        Alarms.scheduleQuiet(ctx, p, now)
    }

    fun onQuietEnd(ctx: Context) {
        val p = Prefs(ctx)
        if (!p.enabled) return
        val now = Clock.now()
        val tracked = p.overnightTracked
        val clean = p.overnightClean
        p.overnightTracked = false
        beginDay(ctx, p, now, Clock.dayKey(p, now))
        if (tracked) {
            p.day(p.dayKey).overnight = if (clean) Day.OVERNIGHT_CLEAN else Day.OVERNIGHT_BROKEN
            if (clean) Buzz.morning(ctx)
        }
        Notifier.showPace(ctx)
        Alarms.scheduleQuiet(ctx, p, now)
    }

    fun reward(ctx: Context, p: Prefs, now: Long) {
        Buzz.reward(ctx)
        p.day(p.dayKey).buzzes += 1
        p.rung += 1
        p.stretchStart = now
        schedule(ctx, p, now)
    }

    private fun beginDay(ctx: Context, p: Prefs, now: Long, key: String) {
        val old = p.dayKey
        if (old.isNotEmpty() && old != key) {
            val prev = p.day(old)
            if (prev.dayEnd == 0L) prev.dayEnd = minOf(now, Clock.nextAt(p.quietStart, prev.dayStart))
        }
        p.dayKey = key
        p.rung = 0
        p.pendingReward = false
        p.stretchStart = now
        val day = p.day(key)
        if (day.dayStart == 0L) {
            day.dayStart = now
            day.lastCompulsiveEnd = now
        }
        schedule(ctx, p, now)
    }

    private fun unitMs(p: Prefs) = if (p.testMode) 1_000L else 60_000L

    private fun target(p: Prefs) = p.stretchStart + LADDER_MINUTES[p.rung] * unitMs(p)

    private fun schedule(ctx: Context, p: Prefs, now: Long) {
        if (!p.enabled || p.rung >= DONE || Clock.inQuiet(p, now)) {
            Alarms.cancel(ctx, Alarms.REWARD)
            return
        }
        Alarms.set(ctx, Alarms.REWARD, maxOf(target(p), now + 1_000))
    }
}
