package com.javarhol.murren

import android.app.NotificationManager
import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.os.Build
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

/** The rewards. Vibration only: nothing ever appears on screen. */
object Buzz {
    // Timings alternate off/on (ms); amplitudes 0–255. A soft double tap that swells.
    private val REWARD_TIMINGS = longArrayOf(0, 70, 110, 70, 110, 320)
    private val REWARD_AMPLITUDES = intArrayOf(0, 110, 0, 160, 0, 255)

    // Morning: three rising taps and a long, warm finish.
    private val MORNING_TIMINGS = longArrayOf(0, 150, 150, 150, 150, 150, 150, 600)
    private val MORNING_AMPLITUDES = intArrayOf(0, 60, 0, 120, 0, 180, 0, 255)

    /** [preview] plays even when silenced, for the "Feel the buzzes" buttons. */
    fun reward(ctx: Context, preview: Boolean = false) = play(ctx, REWARD_TIMINGS, REWARD_AMPLITUDES, preview)

    fun morning(ctx: Context, preview: Boolean = false) = play(ctx, MORNING_TIMINGS, MORNING_AMPLITUDES, preview)

    /** Silent ringer or Do Not Disturb means no buzzes. */
    fun isSilenced(ctx: Context): Boolean {
        val ringer = ctx.getSystemService(AudioManager::class.java)!!.ringerMode
        val filter = ctx.getSystemService(NotificationManager::class.java)!!.currentInterruptionFilter
        return ringer == AudioManager.RINGER_MODE_SILENT || filter > NotificationManager.INTERRUPTION_FILTER_ALL
    }

    private fun play(ctx: Context, timings: LongArray, amplitudes: IntArray, preview: Boolean) {
        if (!preview && isSilenced(ctx)) return
        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ctx.getSystemService(VibratorManager::class.java)!!.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            ctx.getSystemService(Vibrator::class.java)!!
        }
        if (!vibrator.hasVibrator()) return
        val effect = if (vibrator.hasAmplitudeControl()) {
            VibrationEffect.createWaveform(timings, amplitudes, -1)
        } else {
            VibrationEffect.createWaveform(timings, -1)
        }
        // Silence is checked above; alarm usage keeps a background vibration from being dropped.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            vibrator.vibrate(effect, VibrationAttributes.createForUsage(VibrationAttributes.USAGE_ALARM))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(
                effect,
                AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).build(),
            )
        }
    }
}
