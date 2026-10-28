package com.javarhol.murren

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager

object Notifier {
    private const val CHANNEL_SERVICE = "service"
    private const val CHANNEL_SUMMARY = "summary"
    const val ID_SERVICE = 1
    private const val ID_SUMMARY = 2

    fun createChannels(ctx: Context) {
        val nm = ctx.getSystemService(NotificationManager::class.java)!!
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_SERVICE, "Running quietly", NotificationManager.IMPORTANCE_MIN).apply {
                description = "Required by Android to keep Mürren running. Safe to hide."
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

    fun service(ctx: Context): Notification =
        Notification.Builder(ctx, CHANNEL_SERVICE)
            .setSmallIcon(android.R.drawable.ic_lock_idle_lock)
            .setContentTitle("Mürren")
            .setContentText("Running quietly")
            .setOngoing(true)
            .setContentIntent(openApp(ctx))
            .build()

    fun postSummary(ctx: Context, key: String) {
        if (ctx.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        val text = Summary.text(Prefs(ctx), key, Clock.now())
        val notification = Notification.Builder(ctx, CHANNEL_SUMMARY)
            .setSmallIcon(android.R.drawable.ic_lock_idle_lock)
            .setContentTitle("Today")
            .setContentText(text.lineSequence().first())
            .setStyle(Notification.BigTextStyle().bigText(text))
            .setContentIntent(openApp(ctx))
            .setAutoCancel(true)
            .build()
        ctx.getSystemService(NotificationManager::class.java)!!.notify(ID_SUMMARY, notification)
    }
}
