package com.javarhol.murren

import android.content.Context
import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.view.View
import kotlin.math.abs

/**
 * The hero: your pace inside a ring that drains like a battery as you use the
 * phone and refills while you're away.
 */
class HeroRing(
    context: Context,
    private val charge: Float,
    private val number: String,
    private val label: String,
    private val pal: Palette,
) : View(context) {
    private val d = resources.displayMetrics.density

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val size = minOf(MeasureSpec.getSize(widthMeasureSpec), (260 * d).toInt())
        setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), size)
    }

    override fun onDraw(canvas: Canvas) {
        val size = height.toFloat()
        val stroke = 14 * d
        val cx = width / 2f
        val cy = size / 2f
        val r = size / 2f - stroke
        val oval = RectF(cx - r, cy - r, cx + r, cy + r)
        val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = stroke
            strokeCap = Paint.Cap.ROUND
        }
        ring.color = pal.track
        canvas.drawCircle(cx, cy, r, ring)
        ring.color = pal.dusk
        canvas.drawArc(oval, -90f, 360f * charge.coerceIn(0.01f, 1f), false, ring)
        val tick = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = pal.ink; strokeWidth = 2 * d }
        canvas.drawLine(cx, cy - r - stroke / 2 - 2 * d, cx, cy - r + stroke / 2 + 2 * d, tick)

        val num = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = pal.ink
            typeface = Style.numerals(context)
            textAlign = Paint.Align.CENTER
            textSize = size * 0.45f
        }
        canvas.drawText(number, cx, cy + num.textSize * 0.30f, num)
        val small = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = pal.dim
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
            textSize = 11.5f * d
            letterSpacing = 0.18f
        }
        canvas.drawText(label, cx, cy + size * 0.26f, small)
    }
}

/**
 * Like a running watch's pacer bar: the bubble slides left when you're under
 * your usual day and right when you're over. Position carries the verdict;
 * there's deliberately no red.
 */
class PacerBar(context: Context, private val minutesUnder: Long, private val pal: Palette) : View(context) {
    private val d = resources.displayMetrics.density

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), (40 * d).toInt())
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val cy = height / 2f
        val h = 10 * d
        val text = when {
            minutesUnder > 0 -> "−$minutesUnder"
            minutesUnder < 0 -> "+${abs(minutesUnder)}"
            else -> "0"
        }
        val label = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = Style.numerals(context)
            textSize = 22 * d
            textAlign = Paint.Align.CENTER
            color = pal.onDusk
        }
        val bubbleW = label.measureText(text) + 26 * d
        val bubbleH = 32 * d
        val pos = (0.5f - minutesUnder / 60f).coerceIn(0f, 1f)
        val x = bubbleW / 2 + (w - bubbleW) * pos

        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.color = pal.track
        canvas.drawRoundRect(0f, cy - h / 2, w, cy + h / 2, h / 2, h / 2, paint)
        paint.color = pal.dusk
        canvas.drawRoundRect(0f, cy - h / 2, x, cy + h / 2, h / 2, h / 2, paint)
        paint.color = pal.bg
        canvas.drawRoundRect(
            x - bubbleW / 2 - 3 * d, cy - bubbleH / 2 - 3 * d, x + bubbleW / 2 + 3 * d, cy + bubbleH / 2 + 3 * d,
            bubbleH, bubbleH, paint,
        )
        paint.color = pal.dusk
        canvas.drawRoundRect(x - bubbleW / 2, cy - bubbleH / 2, x + bubbleW / 2, cy + bubbleH / 2, bubbleH, bubbleH, paint)
        canvas.drawText(text, x, cy + label.textSize * 0.36f, label)
    }
}

/**
 * Today's running total of time on phone against the ghost of your usual day,
 * like racing your own past effort on a route.
 */
class GhostChart(
    context: Context,
    private val from: Int,
    private val to: Int,
    private val now: Int,
    private val today: (Int) -> Long,
    private val ghost: (Int) -> Long,
    private val pal: Palette,
) : View(context) {
    private val d = resources.displayMetrics.density

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), (110 * d).toInt())
    }

    override fun onDraw(canvas: Canvas) {
        val span = (to - from).coerceAtLeast(60)
        val pad = 6 * d
        val w = width - 2 * pad
        val h = height - 2 * pad
        val todayEnd = now.coerceIn(from, to)
        val top = maxOf(30 * 60_000L, today(todayEnd) - today(from), ghost(to) - ghost(from)).toFloat() * 1.1f
        fun px(m: Int) = pad + w * (m - from) / span
        fun py(ms: Long) = pad + h - h * (ms / top)

        canvas.drawLine(pad, pad + h, pad + w, pad + h, Paint().apply { color = pal.line; strokeWidth = d })
        fun line(f: (Int) -> Long, until: Int, paint: Paint) {
            val path = Path()
            var m = from
            path.moveTo(px(m), py(f(m) - f(from)))
            while (m < until) {
                m = minOf(until, m + 10)
                path.lineTo(px(m), py(f(m) - f(from)))
            }
            canvas.drawPath(path, paint)
        }
        line(
            ghost, to,
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE; strokeWidth = 2 * d; color = pal.dim
                pathEffect = DashPathEffect(floatArrayOf(6 * d, 5 * d), 0f)
            },
        )
        line(
            today, todayEnd,
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE; strokeWidth = 3.5f * d; color = pal.dusk; strokeCap = Paint.Cap.ROUND
            },
        )
        canvas.drawCircle(
            px(todayEnd), py(today(todayEnd) - today(from)), 5 * d,
            Paint(Paint.ANTI_ALIAS_FLAG).apply { color = pal.glow },
        )
    }
}
