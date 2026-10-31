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
    private const val CHANNEL_PACE = "pace"
    private const val CHANNEL_SUMMARY = "summary"
    private const val OLD_CHANNEL_SERVICE = "service"
    const val ID_PACE = 1
    private const val ID_SUMMARY = 2
    private const val FIVE_MIN = 5 * 60_000L

    // Last pace notification posted, so ticks only re-post when something visible changed.
    private var lastShown: String? = null

    fun createChannels(ctx: Context) {
        val nm = ctx.getSystemService(NotificationManager::class.java)!!
        nm.deleteNotificationChannel(OLD_CHANNEL_SERVICE)
        nm.createNotificationChannel(
            // LOW, not MIN: MIN hides the status bar icon and can't be promoted to a Live Update.
            NotificationChannel(CHANNEL_PACE, "Pace clock", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Your pace in the status bar and on the lock screen. Never makes a sound."
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                setShowBadge(false)
            },
        )
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_SUMMARY, "Evening summary", NotificationManager.IMPORTANCE_LOW).apply {
                setShowBadge(false)
            },
        )
    }

    private fun openApp(ctx: Context): PendingIntent =
        PendingIntent.getActivity(
            ctx, 0, Intent(ctx, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE,
        )

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

        val minutes = Pace.minutesPerHour(p, now)
        builder
            .setContentTitle("You're on pace for $minutes min off phone / hour")
            .setContentText("On track for about ${Clock.formatDuration(roundedProjection(p, now))} off phone today")
        if (canPromote(ctx)) {
            // Live Update: the status bar chip shows the mark with "54/hr" beside it.
            builder
                .setSmallIcon(Icons.mountain())
                .setShortCriticalText("$minutes/hr")
                .setRequestPromotedOngoing(true)
        } else {
            builder.setSmallIcon(Icons.number(minutes))
        }
        return builder.build()
    }

    private fun roundedProjection(p: Prefs, now: Long) = Pace.projectedOffToday(p, now) / FIVE_MIN * FIVE_MIN

    /** Re-post the pace notification if what it shows has changed. */
    fun showPace(ctx: Context, force: Boolean = false) {
        val p = Prefs(ctx)
        if (!p.enabled) return
        val now = Clock.now()
        val key = if (Clock.inQuiet(p, now)) {
            "quiet"
        } else {
            "${Pace.minutesPerHour(p, now)}|${roundedProjection(p, now)}|${canPromote(ctx)}"
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
            .setContentTitle("Today")
            .setContentText(text.lineSequence().first())
            .setStyle(Notification.BigTextStyle().bigText(text))
            .setContentIntent(openApp(ctx))
            .setAutoCancel(true)
            .build()
        ctx.getSystemService(NotificationManager::class.java)!!.notify(ID_SUMMARY, notification)
    }
}
