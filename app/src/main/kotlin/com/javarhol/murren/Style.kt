package com.javarhol.murren

import android.content.Context
import android.content.res.Configuration
import android.graphics.Typeface

/** Colors from the dusk photo over Mürren, in a dark (watch face) and a light version. */
data class Palette(
    val bg: Int,
    val ink: Int,
    val dim: Int,
    val line: Int,
    val track: Int,
    val dusk: Int,
    val onDusk: Int,
    val glow: Int,
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

    fun palette(ctx: Context): Palette {
        val night = ctx.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
        return if (night == Configuration.UI_MODE_NIGHT_YES) DARK else LIGHT
    }

    private var bold: Typeface? = null
    private var semi: Typeface? = null

    /** Barlow Condensed, a sports-display face for the numbers (SIL OFL, see assets/fonts). */
    fun numerals(ctx: Context): Typeface =
        bold ?: Typeface.createFromAsset(ctx.assets, "fonts/BarlowCondensed-Bold.ttf").also { bold = it }

    fun numeralsSemi(ctx: Context): Typeface =
        semi ?: Typeface.createFromAsset(ctx.assets, "fonts/BarlowCondensed-SemiBold.ttf").also { semi = it }
}
