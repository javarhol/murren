package com.javarhol.murren

import android.content.Context
import android.media.AudioAttributes
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

    fun reward(ctx: Context) = play(ctx, REWARD_TIMINGS, REWARD_AMPLITUDES)

    fun morning(ctx: Context) = play(ctx, MORNING_TIMINGS, MORNING_AMPLITUDES)

    private fun play(ctx: Context, timings: LongArray, amplitudes: IntArray) {
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
        // Alarm usage so the reward still arrives when the ringer is on silent.
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
