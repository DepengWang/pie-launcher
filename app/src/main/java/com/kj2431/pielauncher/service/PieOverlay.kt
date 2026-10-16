package com.kj2431.pielauncher.service

import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.graphics.drawable.Drawable
import android.os.Build
import android.util.DisplayMetrics
import android.view.Gravity
import android.view.WindowManager
import androidx.core.content.ContextCompat
import com.kj2431.pielauncher.R
import com.kj2431.pielauncher.action.ActionId
import com.kj2431.pielauncher.action.ActionRunner
import com.kj2431.pielauncher.model.PieItem
import com.kj2431.pielauncher.prefs.Prefs
import com.kj2431.pielauncher.view.PieConfig
import com.kj2431.pielauncher.view.PieEdgeView

/**
 * Owns one window per enabled edge. Each window is a thin activation strip
 * while idle and expands to full screen while its donut pie is open, so the
 * whole press-drag-release gesture stays in one window.
 *
 * [ctx] must be the component that is allowed to add [windowType]: the
 * foreground service for TYPE_APPLICATION_OVERLAY, or the *connected*
 * AccessibilityService for TYPE_ACCESSIBILITY_OVERLAY (its WindowManager
 * carries the accessibility connection token).
 */
class PieOverlay(private val ctx: Context, private val windowType: Int) {

    private val wm = ctx.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val views = mutableListOf<PieEdgeView>()
    private val stripParams = HashMap<PieEdgeView, WindowManager.LayoutParams>()

    val isShowing: Boolean get() = views.isNotEmpty()

    // ---- Edge windows -------------------------------------------------------
    fun addEdges() {
        val metrics: DisplayMetrics = ctx.resources.displayMetrics
        val density = metrics.density
        val thicknessPx = (Prefs.thicknessDp(ctx) * density).toInt()
        val lengthFrac = Prefs.lengthPercent(ctx) / 100f
        val centerFrac = Prefs.centerPercent(ctx) / 100f
        val pieColors = resolveColors()

        Prefs.edges(ctx).forEach { edge ->
            lateinit var view: PieEdgeView
            val cfg = PieConfig(
                showIcon = Prefs.showIcon(ctx),
                showText = Prefs.showText(ctx),
                responseMs = Prefs.responseMs(ctx),
                animMs = Prefs.animationMs(ctx),
                longPressMs = Prefs.longPressMs(ctx),
                hapticMs = Prefs.hapticMs(ctx),
                bgColor = pieColors.bg,
                primaryColor = pieColors.primary,
                longColor = pieColors.longc,
                innerRadiusDp = Prefs.innerRadiusDp(ctx).toFloat(),
                outerRadiusDp = Prefs.outerRadiusDp(ctx).toFloat(),
                borderColor = pieColors.border,
                borderWidthDp = Prefs.borderWidthDp(ctx).toFloat(),
                ringInfo = Prefs.ringInfo(ctx),
                dimPercent = Prefs.dimPercent(ctx)
            )
            view = PieEdgeView(
                ctx, edge,
                showHint = Prefs.showActivationBars(ctx) || OverlaySettings.preview,
                config = cfg,
                itemsProvider = { buildItems() },
                onExpand = { expand(view) },
                onCollapse = { collapse(view) }
            )

            val vertical = edge == Prefs.Edge.LEFT || edge == Prefs.Edge.RIGHT
            // Size = length% of the edge; centred at centerFrac of the edge.
            val edgeLen = if (vertical) metrics.heightPixels else metrics.widthPixels
            val sizePx = (edgeLen * lengthFrac).toInt().coerceAtLeast(thicknessPx)
            val offset = (edgeLen * centerFrac - sizePx / 2f).toInt().coerceIn(0, edgeLen - sizePx)

            val lp = WindowManager.LayoutParams(
                if (vertical) thicknessPx else sizePx,
                if (vertical) sizePx else thicknessPx,
                windowType,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or imeFlag(),
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = edgeGravity(edge) or (if (vertical) Gravity.TOP else Gravity.START)
                if (vertical) y = offset else x = offset
            }

            runCatching { wm.addView(view, lp); views += view; stripParams[view] = lp }
        }
    }

    fun removeEdges() {
        views.forEach { runCatching { wm.removeView(it) } }
        views.clear(); stripParams.clear()
    }

    /** Grow the edge window to full screen so the donut + gesture have room. */
    private fun expand(view: PieEdgeView) {
        val full = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            windowType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or imeFlag(),
            PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.TOP or Gravity.START; x = 0; y = 0 }
        runCatching { wm.updateViewLayout(view, full) }
    }

    /** Shrink back to the thin activation strip once the gesture ends. */
    private fun collapse(view: PieEdgeView) {
        stripParams[view]?.let { runCatching { wm.updateViewLayout(view, it) } }
    }

    private data class PieColors(val bg: Int, val primary: Int, val longc: Int, val border: Int)

    /** Resolves the three slice colors; "system" slots fall back to system defaults. */
    private fun resolveColors(): PieColors {
        val d = systemDefaults()
        fun pick(pref: String, def: Int): Int =
            if (pref == "system") def
            else runCatching { android.graphics.Color.parseColor(pref) }.getOrDefault(def)
        return PieColors(
            bg = pick(Prefs.pieBgColor(ctx), d.bg),
            primary = pick(Prefs.pieColor(ctx), d.primary),
            longc = pick(Prefs.pieLongColor(ctx), d.longc),
            border = pick(Prefs.pieBorderColor(ctx), d.border)
        )
    }

    /**
     * Three defaults pulled from the device's dynamic palette (Android 12+).
     * Sorted by luminance: the darkest becomes the resting background, the
     * brightest the click highlight, the middle the long-press color.
     */
    private fun systemDefaults(): PieColors {
        if (Build.VERSION.SDK_INT >= 31) {
            val c = listOf(
                ContextCompat.getColor(ctx, android.R.color.system_accent1_700),
                ContextCompat.getColor(ctx, android.R.color.system_accent2_500),
                ContextCompat.getColor(ctx, android.R.color.system_accent3_300)
            ).sortedBy { androidx.core.graphics.ColorUtils.calculateLuminance(it) }
            return PieColors(bg = c[0], longc = c[1], primary = c[2], border = c[2])
        }
        val primary = resolveThemePrimary()
        return PieColors(bg = 0xFF262626.toInt(), primary = primary, longc = 0xFFEF9F27.toInt(), border = primary)
    }

    private fun resolveThemePrimary(): Int {
        val themed = androidx.appcompat.view.ContextThemeWrapper(ctx, R.style.Theme_PieLauncher)
        val tv = android.util.TypedValue()
        return if (themed.theme.resolveAttribute(com.google.android.material.R.attr.colorPrimary, tv, true)) {
            if (tv.resourceId != 0) ContextCompat.getColor(themed, tv.resourceId) else tv.data
        } else 0xFF009688.toInt()
    }

    // ---- Slice building -----------------------------------------------------
    private fun buildItems(): List<PieItem> {
        val items = Prefs.slices(ctx).mapNotNull { token -> sliceFromToken(token) }
        if (items.isNotEmpty()) return items
        return listOf(
            PieItem("Home", ContextCompat.getDrawable(ctx, ActionId.HOME.iconRes)) { ActionRunner.run(ctx, ActionId.HOME) },
            PieItem("Back", ContextCompat.getDrawable(ctx, ActionId.BACK.iconRes)) { ActionRunner.run(ctx, ActionId.BACK) },
            PieItem("Recents", ContextCompat.getDrawable(ctx, ActionId.RECENTS.iconRes)) { ActionRunner.run(ctx, ActionId.RECENTS) },
            PieItem("Notifs", ContextCompat.getDrawable(ctx, ActionId.NOTIFICATIONS.iconRes)) { ActionRunner.run(ctx, ActionId.NOTIFICATIONS) }
        )
    }

    /**
     * Token grammar: PRIMARY[##LONG] where each part is "app:<pkg>" or
     * "act:<ID>[|arg]". PRIMARY supplies the label + icon + short action; LONG
     * (optional) supplies the long-press action.
     */
    private fun sliceFromToken(token: String): PieItem? {
        val primary = resolve(token.substringBefore("##")) ?: return null
        val long = if (token.contains("##")) resolve(token.substringAfter("##")) else null
        return PieItem(primary.label, primary.icon, long?.label, long?.icon, long?.action, primary.action)
    }

    private data class Resolved(val label: String, val icon: Drawable?, val action: () -> Unit)

    private fun resolve(sub: String): Resolved? = when {
        sub.startsWith("app:") -> {
            val pm = ctx.packageManager
            val pkg = sub.removePrefix("app:")
            val launch = pm.getLaunchIntentForPackage(pkg) ?: return null
            val label = runCatching {
                pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
            }.getOrDefault(pkg)
            val icon = runCatching { pm.getApplicationIcon(pkg) }.getOrNull()
            Resolved(label, icon) { runCatching { ctx.startActivity(launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } }
        }
        sub.startsWith("act:") -> {
            val body = sub.removePrefix("act:")
            val id = runCatching { ActionId.valueOf(body.substringBefore("|")) }.getOrNull() ?: return null
            val arg = if (body.contains("|")) body.substringAfter("|") else null
            Resolved(id.label, ContextCompat.getDrawable(ctx, id.iconRes)) { ActionRunner.run(ctx, id, arg) }
        }
        else -> null
    }

    // ---- Helpers ------------------------------------------------------------
    /**
     * When "Pie behind keyboard" is on, FLAG_ALT_FOCUSABLE_IM inverts a
     * non-focusable window's relationship to the IME so it sits BEHIND the
     * keyboard instead of in front of it.
     */
    private fun imeFlag(): Int =
        if (Prefs.belowKeyboard(ctx)) WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM else 0

    private fun edgeGravity(edge: Prefs.Edge): Int = when (edge) {
        Prefs.Edge.LEFT -> Gravity.START
        Prefs.Edge.RIGHT -> Gravity.END
        Prefs.Edge.TOP -> Gravity.TOP
        Prefs.Edge.BOTTOM -> Gravity.BOTTOM
    }
}
