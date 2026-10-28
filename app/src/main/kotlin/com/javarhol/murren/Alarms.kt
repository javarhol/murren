package com.javarhol.murren

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build

object Alarms {
    const val REWARD = "com.javarhol.murren.REWARD"
    const val QUIET_START = "com.javarhol.murren.QUIET_START"
    const val QUIET_END = "com.javarhol.murren.QUIET_END"
    private val ALL = listOf(REWARD, QUIET_START, QUIET_END)

    private fun pending(ctx: Context, action: String): PendingIntent =
        PendingIntent.getBroadcast(
            ctx,
            ALL.indexOf(action),
            Intent(ctx, AlarmReceiver::class.java).setAction(action),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    fun canBePrecise(ctx: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            ctx.getSystemService(AlarmManager::class.java)!!.canScheduleExactAlarms()

    fun set(ctx: Context, action: String, at: Long) {
        val am = ctx.getSystemService(AlarmManager::class.java)!!
        if (canBePrecise(ctx)) {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pending(ctx, action))
        } else {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pending(ctx, action))
        }
    }

    fun cancel(ctx: Context, action: String) {
        ctx.getSystemService(AlarmManager::class.java)!!.cancel(pending(ctx, action))
    }

    fun cancelAll(ctx: Context) = ALL.forEach { cancel(ctx, it) }

    fun scheduleQuiet(ctx: Context, p: Prefs, now: Long) {
        if (p.quietStart == p.quietEnd) {
            cancel(ctx, QUIET_START)
            cancel(ctx, QUIET_END)
            return
        }
        set(ctx, QUIET_START, Clock.nextAt(p.quietStart, now))
        set(ctx, QUIET_END, Clock.nextAt(p.quietEnd, now))
    }
}
