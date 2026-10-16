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
    val dimPercent: Int            // background dim while the pie is open, 0 = off
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
    private val gapDeg = 3f
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
    private val timePaint   = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        typeface = android.graphics.Typeface.create("sans-serif-thin", android.graphics.Typeface.NORMAL)
        setShadowLayer(dp(2f), 0f, 0f, Color.BLACK)   // readable over any app
    }
    private val infoPaint   = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE; textSize = dp(13f)
        setShadowLayer(dp(2f), 0f, 0f, Color.BLACK)
    }
    /** Ring info captured when the pie opens: big clock + small lines after it. */
    private var timeText: String = ""
    private var infoLines: List<String> = emptyList()
    private val infoPath = Path()

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
        buildInfo()
        activeIndex = -1; longArmed = false
        expandedReady = false
        mode = Mode.PIE
        openAt = System.currentTimeMillis()
        onExpand()
        invalidate()
    }

    private fun reset() {
        handler.removeCallbacks(armLong)
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

        // Previous-style open animation: grow + fade in place (ease-out).
        val t = if (config.animMs <= 0) 1f
                else ((System.currentTimeMillis() - openAt).toFloat() / config.animMs).coerceIn(0f, 1f)
        val ease = 1f - (1f - t) * (1f - t)
        val oR = outerRadius * (0.6f + 0.4f * ease)
        val iR = innerRadius * (0.6f + 0.4f * ease)
        val layer = canvas.saveLayerAlpha(null, (255 * ease).toInt())
        if (config.dimPercent > 0) canvas.drawColor(Color.argb(config.dimPercent * 255 / 100, 0, 0, 0))

        val per = arcTotal / items.size
        val outer = RectF(cx - oR, cy - oR, cx + oR, cy + oR)
        val inner = RectF(cx - iR, cy - iR, cx + iR, cy + iR)

        items.forEachIndexed { i, item ->
            val showLong = i == activeIndex && longArmed
            val a0 = arcStart + dir * (i * per) + dir * (gapDeg / 2f)
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

            val mid = Math.toRadians((arcStart + dir * ((i + 0.5f) * per)).toDouble())
            val r = (iR + oR) / 2f
            val lx = cx + (r * cos(mid)).toFloat()
            val ly = cy + (r * sin(mid)).toFloat()

            val faceIcon = if (showLong) (item.longIcon ?: item.icon) else item.icon
            val faceLabel = if (showLong) (item.longLabel ?: item.label) else item.label

            val drawIcon = config.showIcon && faceIcon != null
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
        drawRingInfo(canvas, cx, cy, oR)
        canvas.restoreToCount(layer)
        if (t < 1f) postInvalidateOnAnimation()
    }

    private fun buildInfo() {
        val want = config.ringInfo
        timeText = if (RingInfo.TIME in want)
            android.text.format.DateFormat.getTimeFormat(context).format(java.util.Date()) else ""
        // Index 0 sits closest to the ring; later lines stack outward (as in LMT).
        infoLines = listOfNotNull(
            if (RingInfo.MEMORY in want) RingInfo.memory(context) else null,
            if (RingInfo.CPU in want) RingInfo.cpu() else null,
            if (RingInfo.NOTIFICATIONS in want) RingInfo.notifications() else null
        )
    }

    /**
     * LMT-style ring info, curved along the outside of the ring: a large thin
     * clock from the start of the fan, then a block of small lines with a
     * shared start angle whose total height matches the clock's digit height.
     * The arc runs clockwise (glyph tops pointing away from the pie) unless its
     * middle lies below the center, in which case it runs counter-clockwise so
     * the text isn't upside down.
     */
    private fun drawRingInfo(canvas: Canvas, cx: Float, cy: Float, oR: Float) {
        if (timeText.isEmpty() && infoLines.isEmpty()) return
        val mid = arcStart + dir * arcTotal / 2f
        val lo = minOf(arcStart, arcStart + dir * arcTotal)
        val clockwise = sin(Math.toRadians(mid.toDouble())) < 0.1
        val base = oR + dp(8f) + borderPaint.strokeWidth / 2f
        // Radial distance from [base] to the glyphs' foot; counter-clockwise
        // text grows toward the pie, so its baseline sits a cap height further out.
        fun baseline(offset: Float, cap: Float) = base + offset + if (clockwise) 0f else cap

        // Height of the band the text occupies = clock digit height.
        timePaint.textSize = oR * 0.5f
        val band = capHeight(timePaint)
        val timeR = baseline(0f, band)
        val timeAng = if (timeText.isEmpty()) 0f else timePaint.measureText(timeText) / timeR

        // Small lines sized so the block fills the band (~1.35 line spacing).
        // Line 0 sits nearest the ring; later lines stack outward.
        val n = infoLines.size
        infoPaint.textSize = (band / (0.72f + 1.35f * (n - 1).coerceAtLeast(0))).coerceIn(dp(10f), dp(16f))
        val cap = capHeight(infoPaint)
        val step = if (n > 1) (band - cap) / (n - 1) else 0f
        val first = if (n > 1) 0f else (band - cap) / 2f
        val lineR = List(n) { baseline(first + it * step, cap) }
        val lineAng = List(n) { infoPaint.measureText(infoLines[it]) / lineR[it] }
        val blockAng = lineAng.maxOrNull() ?: 0f
        val gapAng = if (timeAng > 0f && n > 0) dp(10f) / timeR else 0f

        // As in LMT, the small block goes on the end of the arc that is higher
        // on screen (where it reads best), aligned against the clock. If the
        // path starts at the top (left edge), the block comes first.
        val startDeg = if (clockwise) lo else lo + arcTotal
        val endDeg = if (clockwise) lo + arcTotal else lo
        val linesFirst = sin(Math.toRadians(startDeg.toDouble())) <
            sin(Math.toRadians(endDeg.toDouble())) - 0.1
        val lead = Math.toRadians(4.0).toFloat()   // positions along the arc, in radians

        val timeAt = if (linesFirst) lead + blockAng + gapAng else lead
        if (timeText.isNotEmpty()) drawOnArc(canvas, timeText, timePaint, cx, cy, timeR, lo, clockwise, timeAt)
        infoLines.forEachIndexed { i, line ->
            val at = if (linesFirst) lead + blockAng - lineAng[i]   // right-aligned, ending at the clock
                     else lead + timeAng + gapAng                    // left-aligned, starting after it
            drawOnArc(canvas, line, infoPaint, cx, cy, lineR[i], lo, clockwise, at)
        }
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
