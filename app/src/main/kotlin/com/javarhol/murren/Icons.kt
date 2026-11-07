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

    // The Jungfrau as seen from Mürren, in the launcher icon's 108-unit coordinates.
    val JUNGFRAU = floatArrayOf(
        0f, 55.8f, 10f, 55.8f, 27.9f, 49.3f, 32.3f, 49.5f, 47.8f, 36.2f, 52.2f, 34.1f,
        57.5f, 34.5f, 61.6f, 37f, 88.6f, 68.7f, 91.3f, 70.3f, 97.7f, 67.9f, 108f, 67.9f,
    )

    /** A round badge: the Jungfrau inside a ring, like the app icon. */
    fun mountain(): Icon {
        val bitmap = Bitmap.createBitmap(SIZE, SIZE, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        // The app icon's visible 72-unit circle (18..90) fills the bitmap.
        val k = SIZE / 72f
        val c = SIZE / 2f
        fun x(v: Float) = (v - 18f) * k
        fun y(v: Float) = (v - 18f + 6f) * k // nudged down to leave sky inside the ring
        val peak = Path().apply {
            moveTo(x(0f), SIZE.toFloat())
            for (i in JUNGFRAU.indices step 2) lineTo(x(JUNGFRAU[i]), y(JUNGFRAU[i + 1]))
            lineTo(x(108f), SIZE.toFloat())
            close()
        }
        canvas.save()
        canvas.clipPath(Path().apply { addCircle(c, c, 27f * k, Path.Direction.CW) })
        canvas.drawPath(peak, paint())
        canvas.restore()
        canvas.drawCircle(
            c, c, 32.5f * k,
            paint().apply {
                style = Paint.Style.STROKE
                strokeWidth = 5f * k
            },
        )
        return Icon.createWithBitmap(bitmap)
    }

    /** A battery-like ring: full when you've been away, draining as you use the phone. */
    fun gauge(charge: Float): Icon = ring(charge, number = null)

    /** Zack Zack: a Z drawn as a bolt, same shape as the Quick Settings tile. */
    fun zack(): Icon {
        val bitmap = Bitmap.createBitmap(SIZE, SIZE, Bitmap.Config.ARGB_8888)
        val pts = floatArrayOf(5.5f, 3.5f, 19f, 3.5f, 10.5f, 12f, 17.5f, 12f, 6.5f, 21.5f, 9.5f, 14.5f, 4f, 14.5f, 12.5f, 6.5f, 5.5f, 6.5f)
        val k = SIZE / 24f
        val path = Path().apply {
            moveTo(pts[0] * k, pts[1] * k)
            for (i in 2 until pts.size step 2) lineTo(pts[i] * k, pts[i + 1] * k)
            close()
        }
        Canvas(bitmap).drawPath(path, paint())
        return Icon.createWithBitmap(bitmap)
    }

    /** The ring with the number inside, for when Android can't show text beside the icon. */
    fun gaugeWithNumber(charge: Float, value: Int): Icon = ring(charge, number = value.toString())

    /** The same ring and number in color, for the notification's large icon. */
    fun ringColored(charge: Float, value: Int, color: Int): Icon = ring(charge, value.toString(), color)

    private fun ring(charge: Float, number: String?, color: Int? = null): Icon {
        val bitmap = Bitmap.createBitmap(SIZE, SIZE, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val c = SIZE / 2f
        // A thick ring alone; a thinner one when a number has to fit inside.
        val stroke = if (number == null) SIZE * 0.19f else SIZE * 0.11f
        val r = c - stroke / 2 - SIZE * 0.02f
        val oval = android.graphics.RectF(c - r, c - r, c + r, c + r)
        val p = paint().apply { style = Paint.Style.STROKE; strokeWidth = stroke }
        color?.let { p.color = it }
        p.alpha = 80
        canvas.drawCircle(c, c, r, p)
        p.alpha = 255
        canvas.drawArc(oval, -90f, 360f * charge.coerceIn(0f, 1f), false, p)
        if (number != null) {
            val t = paint().apply {
                typeface = Typeface.DEFAULT_BOLD
                textAlign = Paint.Align.CENTER
                textSize = SIZE * 0.52f
            }
            color?.let { t.color = it }
            t.textSize *= minOf(1f, (2 * r - stroke) * 0.92f / t.measureText(number))
            canvas.drawText(number, c, c - (t.descent() + t.ascent()) / 2f, t)
        }
        return Icon.createWithBitmap(bitmap)
    }
}
