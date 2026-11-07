package com.javarhol.murren

import android.content.Context
import android.content.res.Configuration
import android.graphics.Typeface
import android.os.Build

/**
 * Colors and type. On Android 12+ the colors are Material You's, taken from
 * the phone's wallpaper and theme, so Mürren matches the rest of the system in
 * light and dark; older phones get the dusk palette from the Mürren photo.
 */
data class Palette(
    val bg: Int, // surface
    val ink: Int, // on surface
    val dim: Int, // on surface variant
    val line: Int, // outline variant
    val track: Int, // surface container
    val dusk: Int, // primary
    val onDusk: Int, // on primary
    val glow: Int, // tertiary
)

object Style {
    private val DARK = Palette(
        bg = 0xFF0B0F14.toInt(), ink = 0xFFF2F5F7.toInt(), dim = 0xFF8A97A3.toInt(), line = 0xFF232B34.toInt(),
        track = 0xFF1D252E.toInt(), dusk = 0xFF6F93AD.toInt(), onDusk = 0xFF071018.toInt(), glow = 0xFFF2934E.toInt(),
    )
    private val LIGHT = Palette(
        bg = 0xFFF4F6F8.toInt(), ink = 0xFF0E1419.toInt(), dim = 0xFF5E6B76.toInt(), line = 0xFFDCE2E7.toInt(),
        track = 0xFFE1E7EC.toInt(), dusk = 0xFF3F6A88.toInt(), onDusk = 0xFFFFFFFF.toInt(), glow = 0xFFD9692A.toInt(),
    )

    private fun isNight(ctx: Context) =
        ctx.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES

    fun palette(ctx: Context): Palette {
        val night = isNight(ctx)
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return if (night) DARK else LIGHT
        fun c(id: Int) = ctx.getColor(id)
        // Material 3's tonal roles over the system palettes.
        return if (night) {
            Palette(
                bg = c(android.R.color.system_neutral1_900),
                ink = c(android.R.color.system_neutral1_100),
                dim = c(android.R.color.system_neutral2_200),
                line = c(android.R.color.system_neutral2_700),
                track = c(android.R.color.system_neutral2_800),
                dusk = c(android.R.color.system_accent1_200),
                onDusk = c(android.R.color.system_accent1_800),
                glow = c(android.R.color.system_accent3_200),
            )
        } else {
            Palette(
                bg = c(android.R.color.system_neutral1_10),
                ink = c(android.R.color.system_neutral1_900),
                dim = c(android.R.color.system_neutral2_700),
                line = c(android.R.color.system_neutral2_200),
                track = c(android.R.color.system_neutral2_100),
                dusk = c(android.R.color.system_accent1_600),
                onDusk = c(android.R.color.system_accent1_0),
                glow = c(android.R.color.system_accent3_600),
            )
        }
    }

    /** A mid-tone accent that reads on both light and dark notification cards. */
    fun notificationAccent(ctx: Context): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ctx.getColor(android.R.color.system_accent1_400)
        } else {
            0xFF6F93AD.toInt()
        }

    /** The system font, bold, for the numbers. */
    @Suppress("UNUSED_PARAMETER")
    fun numerals(ctx: Context): Typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
}
