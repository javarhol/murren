package com.javarhol.murren

import android.content.ComponentName
import android.content.Context
import android.service.quicksettings.TileService

/**
 * Zack Zack: a phone-down sprint you start yourself, like a tempo interval.
 * Get to the end without using the phone and you get the zack-zack buzz.
 * Allowed apps and quick checks don't break it; anything else ends it there,
 * recorded as how far you got.
 */
object Sprint {
    val CHOICES = listOf(15, 25, 45)

    data class Run(val start: Long, val end: Long, val planned: Long) {
        val completed get() = end >= planned
    }

    fun active(p: Prefs, now: Long) = p.sprintEnd > 0 && now < p.sprintEnd + 5_000

    private fun unitMs(p: Prefs) = if (p.testMode) 1_000L else 60_000L

    fun start(ctx: Context, minutes: Int) {
        val p = Prefs(ctx)
        val now = Clock.now()
        p.sprintMinutes = minutes
        p.sprintStart = now
        p.sprintEnd = now + minutes * unitMs(p)
        p.hiddenDay = "" // you asked for it, so show it
        Alarms.set(ctx, Alarms.SPRINT_END, p.sprintEnd)
        refresh(ctx)
    }

    /** The end alarm: you made it. */
    fun onEnd(ctx: Context) {
        val p = Prefs(ctx)
        if (p.sprintEnd == 0L) return
        val now = Clock.now()
        if (now < p.sprintEnd - 5_000) {
            Alarms.set(ctx, Alarms.SPRINT_END, p.sprintEnd) // stale alarm
            return
        }
        finish(ctx, p, p.sprintEnd)
        Buzz.zackZack(ctx)
    }

    /** A compulsive unlock that started during the sprint ends it at that moment. */
    fun onCompulsive(ctx: Context, at: Long) {
        val p = Prefs(ctx)
        if (p.sprintEnd > 0 && at in p.sprintStart until p.sprintEnd) finish(ctx, p, at)
    }

    /** Stopped from the tile or the app. */
    fun stop(ctx: Context) {
        val p = Prefs(ctx)
        if (p.sprintEnd > 0) finish(ctx, p, minOf(Clock.now(), p.sprintEnd))
    }

    private fun finish(ctx: Context, p: Prefs, end: Long) {
        val day = p.day(p.dayKey)
        day.sprints = (day.sprints.split(';').filter { it.isNotBlank() } + "${p.sprintStart},$end,${p.sprintEnd}")
            .joinToString(";")
        p.sprintStart = 0
        p.sprintEnd = 0
        Alarms.cancel(ctx, Alarms.SPRINT_END)
        refresh(ctx)
    }

    fun runs(day: Day): List<Run> =
        day.sprints.split(';').filter { it.isNotBlank() }.mapNotNull { row ->
            val f = row.split(',').mapNotNull { it.toLongOrNull() }
            if (f.size == 3) Run(f[0], f[1], f[2]) else null
        }

    /** "18/25": minutes done of minutes planned. */
    fun describe(run: Run): String {
        val done = (run.end - run.start + 30_000) / 60_000
        val planned = (run.planned - run.start + 30_000) / 60_000
        return if (run.completed) "$planned ✓" else "$done/$planned"
    }

    private fun refresh(ctx: Context) {
        Notifier.showPace(ctx, force = true)
        TileService.requestListeningState(ctx, ComponentName(ctx, SprintTile::class.java))
    }
}
