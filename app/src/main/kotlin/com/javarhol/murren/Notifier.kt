package com.javarhol.murren

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build

object Notifier {
    private const val CHANNEL_PACE = "pace_clock"
    private const val CHANNEL_SUMMARY = "summary"
    private val OLD_CHANNELS = listOf("service", "pace")
    const val ID_PACE = 1
    private const val ID_SUMMARY = 2
    private const val FIVE_MIN = 5 * 60_000L
    private const val EXTRA_REQUEST_PROMOTED_ONGOING = "android.requestPromotedOngoing"
    const val ACTION_START = "com.javarhol.murren.START_DAY"
    const val ACTION_FINISH = "com.javarhol.murren.FINISH_DAY"

    // Last pace notification posted, so ticks only re-post when something visible changed.
    private var lastShown: String? = null

    fun createChannels(ctx: Context) {
        val nm = ctx.getSystemService(NotificationManager::class.java)!!
        OLD_CHANNELS.forEach { nm.deleteNotificationChannel(it) }
        nm.createNotificationChannel(
            // DEFAULT, with sound and vibration off: LOW and MIN count as "silent", which the
            // lock screen hides by default. Channel importance is fixed once created.
            NotificationChannel(CHANNEL_PACE, "Pace clock", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "Your pace in the status bar and on the lock screen. Never makes a sound."
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                setSound(null, null)
                enableVibration(false)
                enableLights(false)
                setShowBadge(false)
            },
        )
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_SUMMARY, "Finish summary", NotificationManager.IMPORTANCE_LOW).apply {
                setShowBadge(false)
            },
        )
    }

    private fun openApp(ctx: Context): PendingIntent =
        PendingIntent.getActivity(
            ctx, 0, Intent(ctx, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE,
        )

    private fun action(ctx: Context, action: String, code: Int, title: String): Notification.Action {
        val pi = PendingIntent.getBroadcast(
            ctx, code, Intent(ctx, AlarmReceiver::class.java).setAction(action),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return Notification.Action.Builder(Icons.mountain(), title, pi).build()
    }

    /** "4 min under your usual Thursday", or null until there's a usual day to compare with. */
    fun ghostLine(p: Prefs, now: Long): String? {
        val ghost = History.ghost(p, p.dayKey) ?: return null
        val start = History.dayMinute(p.day(p.dayKey).dayStart)
        val usual = ghost.at(History.dayMinute(now)) - ghost.at(start)
        val diff = (usual - History.todaySoFar(p, now)) / 60_000
        return when {
            diff > 0 -> "$diff min under ${ghost.label}"
            diff < 0 -> "${-diff} min over ${ghost.label}"
            else -> "Even with ${ghost.label}"
        }
    }

    fun canPromote(ctx: Context): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.BAKLAVA &&
            ctx.getSystemService(NotificationManager::class.java)!!.canPostPromotedNotifications()

    fun pace(ctx: Context): Notification {
        val p = Prefs(ctx)
        val now = Clock.now()
        val builder = Notification.Builder(ctx, CHANNEL_PACE)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setContentIntent(openApp(ctx))

        if (Clock.inQuiet(p, now)) {
            return builder
                .setSmallIcon(Icons.mountain())
                .setContentTitle("Quiet hours")
                .setContentText("Goodnight. Your pace clock is back at ${Clock.formatMinute(p.quietEnd)}.")
                .build()
        }

        val r = Pace.reading(p, now)
        val minutes = r.minutesPerHour
        val day = p.day(p.dayKey)

        if (day.started == Day.NOT_STARTED) {
            // Live Updates are for activities you start, so the day waits for you.
            return builder
                .setSmallIcon(Icons.mountain())
                .setContentTitle("Start today when you're ready")
                .setContentText("Your pace goes live in the status bar: $minutes/hr right now.")
                .addAction(action(ctx, ACTION_START, 10, "Start today"))
                .build()
        }

        val split = Clock.formatDuration(now - day.lastCompulsiveEnd)
        builder
            .setContentTitle("You're on pace for $minutes min ${r.word} phone / hour")
            .setContentText(listOfNotNull(ghostLine(p, now), "split $split").joinToString(" · "))
        if (day.started == Day.FINISHED_EARLY) {
            return builder.setSmallIcon(Icons.mountain()).build()
        }
        builder.addAction(action(ctx, ACTION_FINISH, 11, "Finish for today"))
        val charge = Pace.fraction(p, now).toFloat()
        if (canPromote(ctx)) {
            // Live Update: the chip shows the draining ring with the number beside it.
            builder
                .setSmallIcon(Icons.gauge(charge))
                .setShortCriticalText("$minutes")
            // Builder.setRequestPromotedOngoing isn't in the API 36 SDK; it only sets this extra.
            builder.extras.putBoolean(EXTRA_REQUEST_PROMOTED_ONGOING, true)
        } else {
            builder.setSmallIcon(Icons.gaugeWithNumber(charge, minutes))
        }
        return builder.build()
    }

    /** Re-post the pace notification if what it shows has changed. */
    fun showPace(ctx: Context, force: Boolean = false) {
        val p = Prefs(ctx)
        if (!p.enabled) return
        val now = Clock.now()
        val key = if (Clock.inQuiet(p, now)) {
            "quiet"
        } else {
            val r = Pace.reading(p, now)
            val day = p.day(p.dayKey)
            val split = (now - day.lastCompulsiveEnd) / FIVE_MIN
            "${r.word}|${r.minutesPerHour}|${day.started.coerceIn(-1, 1)}|${ghostLine(p, now)}|$split|${canPromote(ctx)}"
        }
        if (!force && key == lastShown) return
        lastShown = key
        ctx.getSystemService(NotificationManager::class.java)!!.notify(ID_PACE, pace(ctx))
    }

    fun postSummary(ctx: Context, key: String) {
        if (ctx.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        val text = Summary.text(Prefs(ctx), key, Clock.now())
        val notification = Notification.Builder(ctx, CHANNEL_SUMMARY)
            .setSmallIcon(Icons.mountain())
            .setContentTitle("Finish")
            .setContentText(text.lineSequence().first())
            .setStyle(Notification.BigTextStyle().bigText(text))
            .setContentIntent(openApp(ctx))
            .setAutoCancel(true)
            .build()
        ctx.getSystemService(NotificationManager::class.java)!!.notify(ID_SUMMARY, notification)
    }
}
