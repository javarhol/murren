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
    private const val ACCENT = 0xFF6F93AD.toInt() // dusk blue
    private const val EXTRA_REQUEST_PROMOTED_ONGOING = "android.requestPromotedOngoing"
    const val ACTION_HIDE = "com.javarhol.murren.HIDE_PACE"

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

    /** Swiping the notification away hides it until tomorrow. */
    private fun onSwipe(ctx: Context): PendingIntent =
        PendingIntent.getBroadcast(
            ctx, 12, Intent(ctx, AlarmReceiver::class.java).setAction(ACTION_HIDE),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    /** Minutes under (+) or over (−) your usual day so far, or null until there's a usual day. */
    fun minutesUnderUsual(p: Prefs, now: Long): Long? {
        val ghost = History.ghost(p, p.dayKey) ?: return null
        val start = History.dayMinute(p.day(p.dayKey).dayStart)
        val usual = ghost.at(History.dayMinute(now)) - ghost.at(start)
        return (usual - History.todaySoFar(p, now)) / 60_000
    }

    /** "−4" under your usual day, "+6" over: a running watch's ahead/behind. */
    fun delta(under: Long) = when {
        under > 0 -> "−$under"
        under < 0 -> "+${-under}"
        else -> "±0"
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
            .setDeleteIntent(onSwipe(ctx))

        if (Clock.inQuiet(p, now)) {
            return builder
                .setSmallIcon(Icons.mountain())
                .setContentTitle("☾  ${Clock.formatMinute(p.quietEnd)}")
                .build()
        }

        val r = Pace.reading(p, now)
        val minutes = r.minutesPerHour
        val charge = Pace.fraction(p, now).toFloat()
        val split = now - p.day(p.dayKey).lastCompulsiveEnd
        builder
            .setContentTitle(if (p.showTimeOn) "$minutes min/hr" else "$minutes min/hr off")
            .setContentText(
                listOfNotNull(minutesUnderUsual(p, now)?.let { "${delta(it)} vs usual" }, "split ${Clock.formatClock(split)}")
                    .joinToString("   "),
            )
            .setLargeIcon(Icons.gaugeColored(charge, ACCENT))
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
        if (p.hiddenDay == p.dayKey && !force) return // you swiped it away today
        val now = Clock.now()
        val key = if (Clock.inQuiet(p, now)) {
            "quiet"
        } else {
            val r = Pace.reading(p, now)
            val day = p.day(p.dayKey)
            val split = (now - day.lastCompulsiveEnd) / 60_000
            "${r.word}|${r.minutesPerHour}|${minutesUnderUsual(p, now)}|$split|${canPromote(ctx)}"
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
