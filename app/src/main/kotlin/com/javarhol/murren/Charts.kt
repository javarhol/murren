package com.javarhol.murren

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.util.TypedValue
import android.view.View

private const val ACCENT = 0xFF6F93AD.toInt() // dusk blue from the icon
private const val ACCENT_SOFT = 0x556F93AD

private fun Context.textColor(): Int {
    val tv = TypedValue()
    theme.resolveAttribute(android.R.attr.textColorPrimary, tv, true)
    return if (tv.resourceId != 0) getColor(tv.resourceId) else tv.data
}

/**
 * Like a running watch's pacer bar: a track, your usual band, and a marker for
 * your pace. Position carries the verdict; there's deliberately no red.
 */
class PaceBar(context: Context, private val pace: Int, private val usual: Int?) : View(context) {
    private val d = resources.displayMetrics.density
    private val fg = context.textColor()

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), (36 * d).toInt())
    }

    override fun onDraw(canvas: Canvas) {
        val max = maxOf(20, (usual ?: 0) * 2, pace + 5).toFloat()
        val w = width.toFloat()
        val r = 6 * d
        val cy = height / 2f
        fun x(v: Float) = r + (w - 2 * r) * (v / max).coerceIn(0f, 1f)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        paint.color = fg
        paint.alpha = 40
        canvas.drawRoundRect(0f, cy - r, w, cy + r, r, r, paint)
        usual?.let {
            paint.color = ACCENT_SOFT
            canvas.drawRoundRect(x(it * 0.75f) - r, cy - r, x(it * 1.25f) + r, cy + r, r, r, paint)
        }
        paint.color = ACCENT
        paint.alpha = 255
        canvas.drawCircle(x(pace.toFloat()), cy, r * 2.2f, paint)
        paint.color = Color.WHITE
        canvas.drawCircle(x(pace.toFloat()), cy, r * 0.9f, paint)
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
    private val ghost: ((Int) -> Long)?,
) : View(context) {
    private val d = resources.displayMetrics.density
    private val fg = context.textColor()

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), (150 * d).toInt())
    }

    override fun onDraw(canvas: Canvas) {
        val span = (to - from).coerceAtLeast(60)
        val pad = 4 * d
        val w = width - 2 * pad
        val h = height - 2 * pad
        val todayEnd = now.coerceIn(from, to)
        val top = maxOf(
            30 * 60_000L,
            today(todayEnd) - today(from),
            ghost?.let { it(to) - it(from) } ?: 0L,
        ).toFloat() * 1.1f
        fun px(m: Int) = pad + w * (m - from) / span
        fun py(ms: Long) = pad + h - h * (ms / top)

        val axis = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = fg; alpha = 50; strokeWidth = d }
        canvas.drawLine(pad, pad + h, pad + w, pad + h, axis)

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
        ghost?.let {
            line(
                it, to,
                Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    style = Paint.Style.STROKE; strokeWidth = 2 * d; color = fg; alpha = 90
                    pathEffect = android.graphics.DashPathEffect(floatArrayOf(6 * d, 5 * d), 0f)
                },
            )
        }
        line(
            today, todayEnd,
            Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 3 * d; color = ACCENT },
        )
        val dot = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = ACCENT }
        canvas.drawCircle(px(todayEnd), py(today(todayEnd) - today(from)), 4.5f * d, dot)
    }
}
