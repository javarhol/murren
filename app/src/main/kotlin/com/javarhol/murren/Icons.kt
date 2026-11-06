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

    /** The pacer line's moving dot: a filled circle with a white rim. */
    fun dot(color: Int): Icon {
        val bitmap = Bitmap.createBitmap(SIZE, SIZE, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val c = SIZE / 2f
        canvas.drawCircle(c, c, SIZE * 0.46f, paint())
        canvas.drawCircle(c, c, SIZE * 0.34f, paint().apply { this.color = color })
        return Icon.createWithBitmap(bitmap)
    }

    /** A small phone, for the "on the phone" end of the pacer line. */
    fun phone(): Icon {
        val bitmap = Bitmap.createBitmap(SIZE, SIZE, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val w = SIZE * 0.46f
        val h = SIZE * 0.8f
        val left = (SIZE - w) / 2
        val top = (SIZE - h) / 2
        val body = paint().apply { style = Paint.Style.STROKE; strokeWidth = SIZE * 0.09f }
        canvas.drawRoundRect(left, top, left + w, top + h, SIZE * 0.1f, SIZE * 0.1f, body)
        canvas.drawCircle(SIZE / 2f, top + h - SIZE * 0.12f, SIZE * 0.045f, paint())
        return Icon.createWithBitmap(bitmap)
    }

    /** The ring in color, for the notification's large icon. */
    fun gaugeColored(charge: Float, color: Int): Icon {
        val bitmap = Bitmap.createBitmap(SIZE, SIZE, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val c = SIZE / 2f
        val stroke = SIZE * 0.16f
        val r = c - stroke / 2 - SIZE * 0.04f
        val p = paint().apply { style = Paint.Style.STROKE; strokeWidth = stroke; strokeCap = Paint.Cap.ROUND }
        p.color = color
        p.alpha = 70
        canvas.drawCircle(c, c, r, p)
        p.alpha = 255
        canvas.drawArc(android.graphics.RectF(c - r, c - r, c + r, c + r), -90f, 360f * charge.coerceIn(0.01f, 1f), false, p)
        return Icon.createWithBitmap(bitmap)
    }

    /** The ring with the number inside, for when Android can't show text beside the icon. */
    fun gaugeWithNumber(charge: Float, value: Int): Icon = ring(charge, number = value.toString())

    private fun ring(charge: Float, number: String?): Icon {
        val bitmap = Bitmap.createBitmap(SIZE, SIZE, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val c = SIZE / 2f
        // A thick ring alone; a thinner one when a number has to fit inside.
        val stroke = if (number == null) SIZE * 0.19f else SIZE * 0.11f
        val r = c - stroke / 2 - SIZE * 0.02f
        val oval = android.graphics.RectF(c - r, c - r, c + r, c + r)
        val p = paint().apply { style = Paint.Style.STROKE; strokeWidth = stroke }
        p.alpha = 80
        canvas.drawCircle(c, c, r, p)
        p.alpha = 255
        canvas.drawArc(oval, -90f, 360f * charge.coerceIn(0f, 1f), false, p)
        if (number != null) {
            val t = paint().apply {
                typeface = Typeface.create("sans-serif-condensed", Typeface.BOLD)
                textAlign = Paint.Align.CENTER
                textSize = SIZE * 0.52f
            }
            t.textSize *= minOf(1f, (2 * r - stroke) * 0.92f / t.measureText(number))
            canvas.drawText(number, c, c - (t.descent() + t.ascent()) / 2f, t)
        }
        return Icon.createWithBitmap(bitmap)
    }
}
