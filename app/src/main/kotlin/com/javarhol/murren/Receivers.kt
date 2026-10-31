package com.javarhol.murren

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        when (intent.action) {
            Alarms.REWARD -> Engine.onRewardAlarm(ctx)
            Alarms.QUIET_START -> Engine.onQuietStart(ctx)
            Alarms.QUIET_END -> Engine.onQuietEnd(ctx)
        }
        // Revive the watcher if Android killed it; exact alarms may start foreground services.
        if (Prefs(ctx).enabled) runCatching { WatchService.start(ctx) }
    }
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        if (Prefs(ctx).enabled) runCatching { WatchService.start(ctx) }
    }
}
