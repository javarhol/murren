package com.javarhol.murren

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Typeface
import android.graphics.drawable.Icon

/** Status bar icons, drawn as white-on-transparent masks as Android requires. */
object Icons {
    private const val SIZE = 128

    private fun paint() = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }

    /** The pace as a number, filling the icon. */
    fun number(value: Int): Icon {
        val bitmap = Bitmap.createBitmap(SIZE, SIZE, Bitmap.Config.ARGB_8888)
        val text = value.toString()
        val paint = paint().apply {
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
            textSize = SIZE.toFloat()
        }
        // Shrink until it fits the width, then center vertically.
        paint.textSize *= minOf(1f, SIZE * 0.98f / paint.measureText(text))
        val baseline = SIZE / 2f - (paint.descent() + paint.ascent()) / 2f
        Canvas(bitmap).drawText(text, SIZE / 2f, baseline, paint)
        return Icon.createWithBitmap(bitmap)
    }

    /** Eiger, Mönch and Jungfrau: the same silhouette as the app icon. */
    fun mountain(): Icon {
        val bitmap = Bitmap.createBitmap(SIZE, SIZE, Bitmap.Config.ARGB_8888)
        // Map the launcher icon's 108-unit coordinates onto the bitmap, centered on the peaks.
        val k = SIZE / 72f
        fun x(v: Float) = (v - 55f) * k + SIZE / 2f
        fun y(v: Float) = (v - 52.5f) * k + SIZE / 2f
        val path = Path().apply {
            moveTo(x(20f), y(76f))
            lineTo(x(36f), y(42f)) // Eiger
            lineTo(x(44f), y(55f))
            quadTo(x(50f), y(36f), x(55f), y(36f)) // Mönch
            quadTo(x(60f), y(36f), x(62f), y(44f))
            lineTo(x(71f), y(29f)) // Jungfrau
            lineTo(x(79f), y(46f))
            lineTo(x(83f), y(44f))
            lineTo(x(90f), y(76f))
            close()
        }
        Canvas(bitmap).drawPath(path, paint())
        return Icon.createWithBitmap(bitmap)
    }
}
