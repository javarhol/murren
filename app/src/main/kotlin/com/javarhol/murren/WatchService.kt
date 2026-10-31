package com.javarhol.murren

import android.app.KeyguardManager
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager

/**
 * Listens for unlocks and screen-off, and runs the pace clock. Screen events
 * only reach running, registered receivers.
 */
class WatchService : Service() {
    private val handler = Handler(Looper.getMainLooper())

    // While unlocked, check every few seconds whether you're in an allowed app.
    private val tick = object : Runnable {
        override fun run() {
            updatePace()
            handler.postDelayed(this, TICK_MS)
        }
    }

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context, intent: Intent) {
            when (intent.action) {
                Intent.ACTION_USER_PRESENT -> {
                    Engine.onUnlock(ctx)
                    startTicking()
                }
                Intent.ACTION_SCREEN_OFF -> {
                    stopTicking()
                    Pace.setOnPhone(Prefs(ctx), Clock.now(), on = false)
                    Engine.onScreenOff(ctx)
                    Notifier.showPace(ctx)
                }
                // Lock screen: show the pace you've earned back while away.
                Intent.ACTION_SCREEN_ON -> Notifier.showPace(ctx)
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        Notifier.createChannels(this)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(Notifier.ID_PACE, Notifier.pace(this), ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(Notifier.ID_PACE, Notifier.pace(this))
        }
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_USER_PRESENT)
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_SCREEN_ON)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(screenReceiver, filter, RECEIVER_EXPORTED)
        } else {
            registerReceiver(screenReceiver, filter)
        }
        Engine.start(this)
        Pace.setOnPhone(Prefs(this), Clock.now(), on = false)
        if (isUnlocked()) startTicking()
        Notifier.showPace(this, force = true)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    override fun onDestroy() {
        stopTicking()
        unregisterReceiver(screenReceiver)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun isUnlocked(): Boolean =
        getSystemService(PowerManager::class.java)!!.isInteractive &&
            !getSystemService(KeyguardManager::class.java)!!.isKeyguardLocked

    private fun startTicking() {
        handler.removeCallbacks(tick)
        handler.post(tick)
    }

    private fun stopTicking() = handler.removeCallbacks(tick)

    private fun updatePace() {
        val p = Prefs(this)
        val now = Clock.now()
        if (Clock.inQuiet(p, now)) return
        // No answer from usage stats means the same app is still in front; keep the last call.
        val app = Judge.foregroundApp(this, now)
        val on = when {
            !Judge.hasUsageAccess(this) -> true
            app != null -> !Judge.isAllowed(this, p, app)
            else -> p.paceOn
        }
        Pace.setOnPhone(p, now, on)
        Notifier.showPace(this)
    }

    companion object {
        private const val TICK_MS = 10_000L

        fun start(ctx: Context) {
            ctx.startForegroundService(Intent(ctx, WatchService::class.java))
        }

        fun stop(ctx: Context) {
            ctx.stopService(Intent(ctx, WatchService::class.java))
            Alarms.cancelAll(ctx)
        }
    }
}
