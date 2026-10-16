package com.kj2431.pielauncher.view

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.MotionEvent
import android.view.View
import com.kj2431.pielauncher.info.RingInfo
import com.kj2431.pielauncher.model.PieItem
import com.kj2431.pielauncher.prefs.Prefs.Edge
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/** Tunables read once from Prefs by the service and handed to the view. */
data class PieConfig(
    val showIcon: Boolean,
    val showText: Boolean,
    val responseMs: Long,
    val animMs: Long,
    val longPressMs: Long,
    val hapticMs: Long,
    val bgColor: Int,
    val primaryColor: Int,
    val longColor: Int,
    val innerRadiusDp: Float,
    val outerRadiusDp: Float,
    val borderColor: Int,
    val borderWidthDp: Float,      // 0 = no border
    val ringInfo: Set<String>,     // RingInfo.* ids shown around the ring
    val dimPercent: Int,           // background dim while the pie is open, 0 = off
    val sliceGapDeg: Float,        // gap between slices; 0 = touching, split by the border
    val infoIntervalMs: Long       // how often the ring info page rotates
)

/**
 * One window per screen edge. Idle = thin activation strip (invisible unless
 * [showHint]). On press it expands to full screen and draws a half-DONUT fanning
 * inward, growing in place (simple time-based ease-out — no positional motion).
 * Drag to a slice, lift to select; lift on nothing dismisses. Holding past the
 * long-press time arms the slice's long action AND swaps its face to the long
 * icon/label.
 */
@SuppressLint("ViewConstructor")
class PieEdgeView(
    context: Context,
    val edge: Edge,
    private val showHint: Boolean,
    private val config: PieConfig,
    private val itemsProvider: () -> List<PieItem>,
    private val onExpand: () -> Unit,
    private val onCollapse: () -> Unit
) : View(context) {

    private enum class Mode { IDLE, PIE }
    private var mode = Mode.IDLE

    private val innerRadius = dp(config.innerRadiusDp)
    private val outerRadius = dp(config.outerRadiusDp)
    private val iconSize = dp(26f)
    private val gapDeg = config.sliceGapDeg
    private val arcTotal = 180f

    private val arcStart: Float
    private val dir: Float
    init {
        when (edge) {
            Edge.LEFT -> { arcStart = -90f; dir = 1f }
            Edge.RIGHT -> { arcStart = -90f; dir = -1f }
            Edge.TOP -> { arcStart = 180f; dir = -1f }
            Edge.BOTTOM -> { arcStart = 180f; dir = 1f }
        }
    }

    private val hintPaint   = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(36, 255, 255, 255) }
    private val slicePaint  = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = (config.bgColor and 0x00FFFFFF) or (228 shl 24)   // resting slices, ~90% opaque
    }
    private val activePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = config.primaryColor }
    private val longPaint   = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = config.longColor }
    private val textPaint   = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE; textSize = dp(12f); textAlign = Paint.Align.CENTER
    }
    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeJoin = Paint.Join.ROUND
        strokeWidth = dp(config.borderWidthDp)
        color = config.borderColor
    }
    // Ring info (LMT PieStatusInfo): 65 dp clock, 13 dp small text, thin faces.
    private val timePaint   = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE; textSize = dp(65f)
        typeface = android.graphics.Typeface.create("sans-serif-thin", android.graphics.Typeface.NORMAL)
        setShadowLayer(dp(2f), 0f, 0f, Color.BLACK)   // readable over any app
    }
    private val labelPaint  = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE; textSize = dp(13f)
        typeface = android.graphics.Typeface.create("sans-serif-light", android.graphics.Typeface.NORMAL)
        setShadowLayer(dp(2f), 0f, 0f, Color.BLACK)
    }
    private val infoPaint   = Paint(labelPaint)
    private var timeText = ""
    private var timeLabel = ""                         // "24h" / "AM" / "PM"
    private var infoPages: List<List<String>> = emptyList()
    private var pageIndex = 0
    private var switchAt = 0L                          // when the current page switch began, 0 = none
    private var prevLines: List<String> = emptyList()  // page being switched away from
    private val infoPath = Path()
    /** Rotates the small-text page (and refreshes the data) while the pie is open. */
    private val rotateInfo = object : Runnable {
        override fun run() {
            if (mode != Mode.PIE) return
            val old = infoPages.getOrNull(pageIndex).orEmpty()
            buildInfo()
            if (infoPages.size > 1) {
                prevLines = old
                pageIndex = (pageIndex + 1) % infoPages.size
                switchAt = System.currentTimeMillis()
            }
            invalidate()
            handler.postDelayed(this, config.infoIntervalMs)
        }
    }

    private var items: List<PieItem> = emptyList()
    private var centerRawX = 0f
    private var centerRawY = 0f
    private var activeIndex = -1
    private var longArmed = false
    private var isDown = false
    private var openAt = 0L
    private var expandedReady = false
    private var lastOriginX = Int.MIN_VALUE
    private var lastOriginY = Int.MIN_VALUE

    private val handler = Handler(Looper.getMainLooper())
    private val vibrator: Vibrator =
        if (Build.VERSION.SDK_INT >= 31)
            (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
        else @Suppress("DEPRECATION") context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator

    private val openRunnable = Runnable { if (isDown) openPie() }
    private val armLong = Runnable {
        if (mode == Mode.PIE && activeIndex in items.indices && items[activeIndex].longAction != null) {
            longArmed = true
            buzz(config.hapticMs * 2)
            invalidate()
        }
    }

    private fun dp(v: Float) = v * resources.displayMetrics.density
    private val origin = IntArray(2)

    private fun buzz(ms: Long) {
        if (ms <= 0 || !vibrator.hasVibrator()) return
        vibrator.vibrate(VibrationEffect.createOneShot(ms, VibrationEffect.DEFAULT_AMPLITUDE))
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                isDown = true
                centerRawX = event.rawX; centerRawY = event.rawY
                if (config.responseMs <= 0) openPie()
                else handler.postDelayed(openRunnable, config.responseMs)
                return true
            }
            MotionEvent.ACTION_MOVE -> if (mode == Mode.PIE) {
                val idx = sliceAt(event.rawX, event.rawY)
                if (idx != activeIndex) {
                    activeIndex = idx
                    longArmed = false
                    handler.removeCallbacks(armLong)
                    if (idx >= 0) {
                        buzz(config.hapticMs)
                        handler.postDelayed(armLong, config.longPressMs)
                    }
                    invalidate()
                }
                return true
            }
            MotionEvent.ACTION_UP -> {
                isDown = false
                handler.removeCallbacks(openRunnable)
                if (mode == Mode.PIE) {
                    handler.removeCallbacks(armLong)
                    val item = activeIndex.takeIf { it in items.indices }?.let { items[it] }
                    if (item != null) {
                        if (longArmed && item.longAction != null) item.longAction!!.invoke() else item.action()
                    }
                    reset()
                }
                return true
            }
            MotionEvent.ACTION_CANCEL -> { isDown = false; handler.removeCallbacks(openRunnable); reset(); return true }
        }
        return super.onTouchEvent(event)
    }

    private fun openPie() {
        items = itemsProvider()
        if (items.isEmpty()) return
        pageIndex = 0; switchAt = 0L; prevLines = emptyList()
        buildInfo()
        handler.removeCallbacks(rotateInfo)
        handler.postDelayed(rotateInfo, config.infoIntervalMs)
        activeIndex = -1; longArmed = false
        expandedReady = false
        mode = Mode.PIE
        openAt = System.currentTimeMillis()
        onExpand()
        invalidate()
    }

    private fun reset() {
        handler.removeCallbacks(armLong)
        handler.removeCallbacks(rotateInfo)
        mode = Mode.IDLE
        activeIndex = -1; longArmed = false; expandedReady = false
        lastOriginX = Int.MIN_VALUE; lastOriginY = Int.MIN_VALUE
        onCollapse()
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        if (mode == Mode.IDLE) {
            if (showHint) canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), hintPaint)
            return
        }
        if (items.isEmpty()) return

        // Don't draw until the window has expanded AND its on-screen position
        // has stopped moving. The size can update a frame before the origin
        // settles; drawing in that gap paints the pie at the strip's old spot
        // (the phantom flash). We don't assume the fullscreen origin is (0,0) —
        // it may be inset by the status bar — only that it's stable once settled.
        val dm = resources.displayMetrics
        if (width < dm.widthPixels / 2 || height < dm.heightPixels / 2) {
            openAt = System.currentTimeMillis()
            lastOriginX = Int.MIN_VALUE; lastOriginY = Int.MIN_VALUE
            postInvalidateOnAnimation()
            return
        }
        getLocationOnScreen(origin)
        if (origin[0] != lastOriginX || origin[1] != lastOriginY) {
            lastOriginX = origin[0]; lastOriginY = origin[1]
            openAt = System.currentTimeMillis()      // start the anim once settled
            postInvalidateOnAnimation()
            return
        }
        expandedReady = true

        val cx = centerRawX - origin[0]
        val cy = centerRawY - origin[1]

        // LMT open animation (PieMenu.animateOpen / PieStatusInfo.layout), with
        // D = the animation time setting (LMT: 2 x 80 ms):
        //   slices fan out from the first edge over D, icons fading in;
        //   clock (and background dim) fade + slide in after 4D over 3D, decelerating;
        //   small info lines fade in after 5D over 7D.
        val elapsed = System.currentTimeMillis() - openAt
        val fan = accelDecel(phase(elapsed, 0f, 1f))
        val clockIn = decel(phase(elapsed, 4f, 3f))
        val infoIn = phase(elapsed, 5f, 7f)
        val oR = outerRadius
        val iR = innerRadius
        if (config.dimPercent > 0) canvas.drawColor(Color.argb((config.dimPercent * 255 / 100 * clockIn).toInt(), 0, 0, 0))

        val per = arcTotal / items.size
        val outer = RectF(cx - oR, cy - oR, cx + oR, cy + oR)
        val inner = RectF(cx - iR, cy - iR, cx + iR, cy + iR)

        items.forEachIndexed { i, item ->
            val showLong = i == activeIndex && longArmed
            val a0 = arcStart + dir * (i * per * fan) + dir * (gapDeg / 2f)
            val seg = dir * (per - gapDeg)
            val path = Path().apply {
                arcTo(outer, a0, seg)
                arcTo(inner, a0 + seg, -seg)
                close()
            }
            val paint = when {
                showLong -> longPaint
                i == activeIndex -> activePaint
                else -> slicePaint
            }
            canvas.drawPath(path, paint)
            if (config.borderWidthDp > 0f) canvas.drawPath(path, borderPaint)

            val mid = Math.toRadians((arcStart + dir * ((i * fan + 0.5f) * per)).toDouble())
            val r = (iR + oR) / 2f
            val lx = cx + (r * cos(mid)).toFloat()
            val ly = cy + (r * sin(mid)).toFloat()

            val faceIcon = if (showLong) (item.longIcon ?: item.icon) else item.icon
            val faceLabel = if (showLong) (item.longLabel ?: item.label) else item.label

            val drawIcon = config.showIcon && faceIcon != null
            faceIcon?.alpha = (255 * fan).toInt()
            textPaint.alpha = (255 * fan).toInt()
            if (drawIcon) {
                val h = iconSize / 2f
                val yOff = if (config.showText) dp(6f) else 0f
                faceIcon!!.setBounds((lx - h).toInt(), (ly - h - yOff).toInt(),
                                     (lx + h).toInt(), (ly + h - yOff).toInt())
                faceIcon.draw(canvas)
                if (config.showText) canvas.drawText(trim(faceLabel), lx, ly + iconSize / 2f + dp(7f), textPaint)
            } else if (config.showText) {
                canvas.drawText(trim(faceLabel), lx, ly + textPaint.textSize / 3f, textPaint)
            }
        }
        val switching = drawRingInfo(canvas, cx, cy, oR, clockIn, infoIn)
        if (infoIn < 1f || switching) postInvalidateOnAnimation()
    }

    /** Progress 0..1 of a phase that starts after [delay] x D and lasts [length] x D. */
    private fun phase(elapsed: Long, delay: Float, length: Float): Float {
        val d = config.animMs.toFloat()
        if (d <= 0f) return 1f
        return ((elapsed - delay * d) / (length * d)).coerceIn(0f, 1f)
    }
    private fun accelDecel(t: Float) = (cos((t + 1) * Math.PI) / 2.0 + 0.5).toFloat()
    private fun decel(t: Float) = 1f - (1f - t) * (1f - t)

    private fun buildInfo() {
        val want = config.ringInfo
        if (RingInfo.TIME in want) RingInfo.clock(context).let { timeText = it.first; timeLabel = it.second }
        else { timeText = ""; timeLabel = "" }
        infoPages = listOfNotNull(
            if (RingInfo.OVERVIEW in want) RingInfo.overview(context) else null,
            if (RingInfo.SYSTEM in want) RingInfo.system(context) else null
        ) + if (RingInfo.NOTIFICATIONS in want) RingInfo.notificationPages(context) else emptyList()
        if (pageIndex >= infoPages.size) pageIndex = 0
    }

    /**
     * LMT-style ring info, curved along the outside of the ring and meeting at
     * the middle of the fan: the clock (with its small 24h/AM/PM suffix) on the
     * lower half, the current page of small lines on the other half, outermost
     * line first. The arc runs clockwise (glyph tops pointing away from the
     * pie) unless its middle lies below the center, in which case it runs
     * counter-clockwise so the text is not upside down.
     */
    /** Draws the ring info; returns true while a page switch is still animating. */
    private fun drawRingInfo(canvas: Canvas, cx: Float, cy: Float, oR: Float, clockIn: Float, infoIn: Float): Boolean {
        // Page switch (LMT AIAnimatorOutIn): the old page fades out sliding away
        // from the clock, then the new one slides back in and fades in. Same
        // proportions as LMT: out over the first 10% of the interval, back in
        // by 18% (300 ms + 240 ms at its 3 s period).
        val outMs = config.infoIntervalMs * 0.10f
        val endMs = config.infoIntervalMs * 0.18f
        val t = if (switchAt == 0L) Float.MAX_VALUE else (System.currentTimeMillis() - switchAt).toFloat()
        val out = when {
            t < outMs -> t / outMs
            t < endMs -> 1f - (t - outMs) / (endMs - outMs)
            else -> 0f
        }
        val lines = if (t < outMs) prevLines else infoPages.getOrNull(pageIndex).orEmpty()
        if (timeText.isEmpty() && lines.isEmpty()) return false
        val mid = arcStart + dir * arcTotal / 2f
        val lo = minOf(arcStart, arcStart + dir * arcTotal)
        val clockwise = sin(Math.toRadians(mid.toDouble())) < 0.1
        val base = oR + borderPaint.strokeWidth / 2f + dp(10f)
        // Radial position of a line's foot above [base]; counter-clockwise text
        // grows toward the pie, so its baseline sits a cap height further out.
        fun baseline(offset: Float, cap: Float) = base + offset + if (clockwise) 0f else cap

        // The clock takes whichever half of the arc is lower on screen (the
        // first half when both are level, as for top/bottom edges).
        val startDeg = if (clockwise) lo else lo + arcTotal
        val endDeg = if (clockwise) lo + arcTotal else lo
        val clockFirst = sin(Math.toRadians(endDeg.toDouble())) <= sin(Math.toRadians(startDeg.toDouble())) + 0.1
        val midAng = Math.toRadians(arcTotal / 2.0).toFloat()
        val pad = dp(5f)
        // Text in the first half ends at the middle; in the second half it starts
        // there. [away] pushes it further from the middle along the arc.
        fun draw(text: String, paint: Paint, r: Float, firstHalf: Boolean, away: Float = 0f) {
            val at = if (firstHalf) midAng * r - pad - away - paint.measureText(text) else midAng * r + pad + away
            drawOnArc(canvas, text, paint, cx, cy, r, lo, clockwise, at / r)
        }

        if (timeText.isNotEmpty() && clockIn > 0f) {
            // Clock slides one text size toward the middle; its suffix drops in radially.
            val big = timePaint.textSize
            timePaint.alpha = (255 * clockIn).toInt()
            labelPaint.alpha = (255 * clockIn).toInt()
            draw(timeText, timePaint, baseline(0f, capHeight(timePaint)), clockFirst, (1f - clockIn) * big)
            draw(timeLabel, labelPaint, baseline(big - dp(10f) + (1f - clockIn) * big, capHeight(labelPaint)), clockFirst)
        }
        if (lines.isEmpty() || infoIn <= 0f) return out > 0f
        infoPaint.alpha = (255 * infoIn * (1f - out)).toInt()
        val cap = capHeight(infoPaint)
        val lineH = infoPaint.textSize * 1.2f
        lines.forEachIndexed { i, line ->
            draw(line, infoPaint, baseline((lines.size - 1 - i) * lineH, cap), !clockFirst, out * dp(120f))
        }
        return t < endMs
    }

    private val capBounds = android.graphics.Rect()
    private fun capHeight(paint: Paint): Float {
        paint.getTextBounds("0", 0, 1, capBounds)
        return capBounds.height().toFloat()
    }

    private fun drawOnArc(
        canvas: Canvas, text: String, paint: Paint, cx: Float, cy: Float,
        r: Float, lo: Float, clockwise: Boolean, angle: Float
    ) {
        infoPath.reset()
        val oval = RectF(cx - r, cy - r, cx + r, cy + r)
        if (clockwise) infoPath.addArc(oval, lo, arcTotal)
        else infoPath.addArc(oval, lo + arcTotal, -arcTotal)
        canvas.drawTextOnPath(text, infoPath, angle * r, 0f, paint)
    }

    private fun trim(s: String) = if (s.length > 10) s.take(9) + "…" else s

    private fun sliceAt(x: Float, y: Float): Int {
        if (items.isEmpty()) return -1
        val dist = hypot((x - centerRawX).toDouble(), (y - centerRawY).toDouble())
        if (dist < innerRadius || dist > outerRadius + dp(60f)) return -1
        val deg = Math.toDegrees(atan2((y - centerRawY).toDouble(), (x - centerRawX).toDouble()))
        var rel = dir * (deg - arcStart)
        rel = ((rel % 360) + 360) % 360
        if (rel > arcTotal) return -1
        val per = arcTotal / items.size
        return (rel / per).toInt().coerceIn(0, items.size - 1)
    }
}
