package com.kj2431.pielauncher.ui

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ComposeShader
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.RadialGradient
import android.graphics.Shader
import android.graphics.SweepGradient
import android.view.MotionEvent
import android.view.View
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.sin

/**
 * Hue / saturation disc: hue runs around the circle, saturation from the
 * white centre to the rim. [brightness] only darkens the drawing so the disc
 * matches the value slider next to it.
 */
class ColorWheelView(ctx: Context) : View(ctx) {

    var hue = 0f
        private set
    var sat = 0f
        private set
    var brightness = 1f
        set(value) { field = value; invalidate() }

    /** Called with (hue, saturation) while the user drags. */
    var onPick: ((Float, Float) -> Unit)? = null

    private val density = ctx.resources.displayMetrics.density
    private val wheelPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val dimPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private var cx = 0f
    private var cy = 0f
    private var radius = 0f

    fun set(hue: Float, sat: Float) {
        this.hue = hue; this.sat = sat
        invalidate()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = MeasureSpec.getSize(widthMeasureSpec)
        val size = min(w, (260 * density).toInt())
        setMeasuredDimension(w, size)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        cx = w / 2f
        cy = h / 2f
        radius = min(w, h) / 2f - 12 * density
        val hues = IntArray(7) { Color.HSVToColor(floatArrayOf((it * 60f) % 360f, 1f, 1f)) }
        val sweep = SweepGradient(cx, cy, hues, null)
        val white = RadialGradient(cx, cy, radius, Color.WHITE, 0x00FFFFFF, Shader.TileMode.CLAMP)
        wheelPaint.shader = ComposeShader(sweep, white, PorterDuff.Mode.SRC_OVER)
    }

    override fun onDraw(canvas: Canvas) {
        canvas.drawCircle(cx, cy, radius, wheelPaint)
        dimPaint.color = Color.argb(((1f - brightness) * 255).toInt(), 0, 0, 0)
        canvas.drawCircle(cx, cy, radius, dimPaint)

        val a = Math.toRadians(hue.toDouble())
        val x = cx + (cos(a) * sat * radius).toFloat()
        val y = cy + (sin(a) * sat * radius).toFloat()
        ringPaint.strokeWidth = 4 * density
        ringPaint.color = Color.BLACK
        canvas.drawCircle(x, y, 9 * density, ringPaint)
        ringPaint.strokeWidth = 2 * density
        ringPaint.color = Color.WHITE
        canvas.drawCircle(x, y, 9 * density, ringPaint)
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                parent?.requestDisallowInterceptTouchEvent(true)
                val dx = e.x - cx
                val dy = e.y - cy
                var h = Math.toDegrees(atan2(dy, dx).toDouble()).toFloat()
                if (h < 0) h += 360f
                set(h, (hypot(dx, dy) / radius).coerceIn(0f, 1f))
                onPick?.invoke(hue, sat)
            }
        }
        return true
    }
}
