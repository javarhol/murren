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

    /** Two peaks: the Mürren mark, for when the number is shown as text beside it. */
    fun mountain(): Icon {
        val bitmap = Bitmap.createBitmap(SIZE, SIZE, Bitmap.Config.ARGB_8888)
        val s = SIZE.toFloat()
        val path = Path().apply {
            moveTo(0.04f * s, 0.86f * s)
            lineTo(0.38f * s, 0.22f * s)
            lineTo(0.56f * s, 0.56f * s)
            lineTo(0.70f * s, 0.38f * s)
            lineTo(0.96f * s, 0.86f * s)
            close()
        }
        Canvas(bitmap).drawPath(path, paint())
        return Icon.createWithBitmap(bitmap)
    }
}
