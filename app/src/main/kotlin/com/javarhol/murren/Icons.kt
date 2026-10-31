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

    // The Jungfrau as seen from Mürren, in the launcher icon's 108-unit coordinates.
    private val JUNGFRAU = floatArrayOf(
        0f, 55.8f, 10f, 55.8f, 27.9f, 49.3f, 32.3f, 49.5f, 47.8f, 36.2f, 52.2f, 34.1f,
        57.5f, 34.5f, 61.6f, 37f, 88.6f, 68.7f, 91.3f, 70.3f, 97.7f, 67.9f, 108f, 67.9f,
    )

    /** The Jungfrau: the same silhouette as the app icon. */
    fun mountain(): Icon {
        val bitmap = Bitmap.createBitmap(SIZE, SIZE, Bitmap.Config.ARGB_8888)
        val k = SIZE / 96f
        fun x(v: Float) = (v - 6f) * k
        fun y(v: Float) = (v - 22f) * k
        val path = Path().apply {
            moveTo(x(0f), SIZE.toFloat())
            for (i in JUNGFRAU.indices step 2) lineTo(x(JUNGFRAU[i]), y(JUNGFRAU[i + 1]))
            lineTo(x(108f), SIZE.toFloat())
            close()
        }
        Canvas(bitmap).drawPath(path, paint())
        return Icon.createWithBitmap(bitmap)
    }
}
