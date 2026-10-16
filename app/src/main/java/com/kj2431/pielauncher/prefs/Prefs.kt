package com.kj2431.pielauncher.prefs

import android.content.Context
import androidx.preference.PreferenceManager

/**
 * Single source of truth for user-configurable behaviour.
 *
 * Defaults requested for v0.1:
 *   - Activation edges        -> LEFT and RIGHT      (key: pref_edges)
 *   - Activation thickness    -> 30 dp               (key: pref_thickness)
 *   - Activation gravity      -> user-selectable      (key: pref_gravity, default CENTER)
 */
object Prefs {

    const val KEY_EDGES      = "pref_edges"
    const val KEY_THICKNESS  = "pref_thickness"
    const val KEY_CENTER_PCT = "pref_center_pct"
    const val KEY_LENGTH_PCT = "pref_length_pct"
    const val KEY_ENABLED    = "pref_service_enabled"
    const val KEY_ROOT       = "pref_root_actions"
    const val KEY_SHOW_BARS  = "pref_show_bars"
    const val KEY_LABEL_MODE = "pref_label_mode"   // both | icon | text
    const val KEY_RESPONSE   = "pref_response_ms"
    const val KEY_ANIM       = "pref_anim_ms"
    const val KEY_LONGPRESS  = "pref_longpress_ms"
    const val KEY_HAPTIC     = "pref_haptic_ms"
    const val KEY_PIE_COLOR  = "pref_pie_color"      // click / primary
    const val KEY_PIE_BG     = "pref_pie_bg_color"   // resting background
    const val KEY_PIE_LONG   = "pref_pie_long_color" // long-press
    const val KEY_THEME      = "pref_theme"        // system | light | dark | amoled
    const val KEY_BELOW_KB   = "pref_below_keyboard"
    const val KEY_WINDOW_MODE = "pref_window_mode" // overlay | accessibility
    const val KEY_INNER_RADIUS = "pref_inner_radius_dp"
    const val KEY_PIE_BORDER = "pref_pie_border_color"
    const val KEY_BORDER_DP  = "pref_pie_border_dp"
    const val KEY_CENTER_INFO = "pref_center_info"  // legacy single choice: none | time | battery | both
    const val KEY_RING_INFO  = "pref_ring_info"    // set of RingInfo.* ids
    const val KEY_DIM_PCT    = "pref_dim_pct"
    const val KEY_SLICE_GAP  = "pref_slice_gap_deg"
    const val KEY_INFO_INTERVAL = "pref_info_interval_s"
    const val KEY_OUTER_RADIUS = "pref_outer_radius_dp"
    const val KEY_APPS       = "pref_pie_apps"   // legacy, kept for migration
    const val KEY_SLICES     = "pref_pie_slices" // ordered tokens: "app:<pkg>", "act:<ID>", "act:<ID>|<arg>"

    // ---- Defaults -----------------------------------------------------------
    val DEFAULT_EDGES: Set<String> = setOf(Edge.LEFT.key, Edge.RIGHT.key)
    const val DEFAULT_THICKNESS_DP = 30
    const val DEFAULT_CENTER_PCT = 50   // strip centred at 50% of the edge
    const val DEFAULT_LENGTH_PCT = 60   // strip covers 60% of the edge
    const val DEFAULT_INNER_RADIUS_DP = 48
    const val DEFAULT_OUTER_RADIUS_DP = 140
    /** Thinnest ring we allow, so slices always stay hittable. */
    const val MIN_RING_DP = 32
    const val WINDOW_MODE_OVERLAY = "overlay"             // TYPE_APPLICATION_OVERLAY (legacy default)
    const val WINDOW_MODE_ACCESSIBILITY = "accessibility" // TYPE_ACCESSIBILITY_OVERLAY

    enum class Edge(val key: String) {
        LEFT("left"), RIGHT("right"), TOP("top"), BOTTOM("bottom");
        companion object { fun from(key: String): Edge? = entries.firstOrNull { it.key == key } }
    }

    private fun sp(ctx: Context) = PreferenceManager.getDefaultSharedPreferences(ctx)

    fun edges(ctx: Context): List<Edge> =
        sp(ctx).getStringSet(KEY_EDGES, DEFAULT_EDGES)!!
            .mapNotNull { Edge.from(it) }
            .ifEmpty { DEFAULT_EDGES.mapNotNull { Edge.from(it) } }

    fun thicknessDp(ctx: Context): Int = sp(ctx).getInt(KEY_THICKNESS, DEFAULT_THICKNESS_DP)
    fun centerPercent(ctx: Context): Int = sp(ctx).getInt(KEY_CENTER_PCT, DEFAULT_CENTER_PCT)
    fun lengthPercent(ctx: Context): Int = sp(ctx).getInt(KEY_LENGTH_PCT, DEFAULT_LENGTH_PCT)
    fun serviceEnabled(ctx: Context): Boolean = sp(ctx).getBoolean(KEY_ENABLED, false)
    fun rootActionsEnabled(ctx: Context): Boolean = sp(ctx).getBoolean(KEY_ROOT, false)
    fun showActivationBars(ctx: Context): Boolean = sp(ctx).getBoolean(KEY_SHOW_BARS, false)

    /** Ordered list of package names the user assigned to pie slices. */
    fun pieApps(ctx: Context): List<String> =
        sp(ctx).getString(KEY_APPS, "")!!
            .split("\n")
            .map { it.trim() }
            .filter { it.isNotEmpty() }

    fun setPieApps(ctx: Context, packages: List<String>) {
        sp(ctx).edit().putString(KEY_APPS, packages.joinToString("\n")).apply()
    }

    fun labelMode(ctx: Context): String = sp(ctx).getString(KEY_LABEL_MODE, "both") ?: "both"
    fun showIcon(ctx: Context): Boolean = labelMode(ctx) != "text"
    fun showText(ctx: Context): Boolean = labelMode(ctx) != "icon"
    fun responseMs(ctx: Context): Long = sp(ctx).getInt(KEY_RESPONSE, 0).toLong()
    fun animationMs(ctx: Context): Long = sp(ctx).getInt(KEY_ANIM, 150).toLong()
    fun longPressMs(ctx: Context): Long = sp(ctx).getInt(KEY_LONGPRESS, 400).toLong()
    fun hapticMs(ctx: Context): Long = sp(ctx).getInt(KEY_HAPTIC, 10).toLong()
    fun pieColor(ctx: Context): String = sp(ctx).getString(KEY_PIE_COLOR, "system") ?: "system"
    fun pieBgColor(ctx: Context): String = sp(ctx).getString(KEY_PIE_BG, "system") ?: "system"
    fun pieLongColor(ctx: Context): String = sp(ctx).getString(KEY_PIE_LONG, "system") ?: "system"
    fun themeMode(ctx: Context): String = sp(ctx).getString(KEY_THEME, "system") ?: "system"
    fun belowKeyboard(ctx: Context): Boolean = sp(ctx).getBoolean(KEY_BELOW_KB, false)
    fun pieBorderColor(ctx: Context): String = sp(ctx).getString(KEY_PIE_BORDER, "#FFFFFFFF") ?: "#FFFFFFFF"
    fun borderWidthDp(ctx: Context): Int = sp(ctx).getInt(KEY_BORDER_DP, 1)
    fun dimPercent(ctx: Context): Int = sp(ctx).getInt(KEY_DIM_PCT, 0)
    fun sliceGapDeg(ctx: Context): Int = sp(ctx).getInt(KEY_SLICE_GAP, 3)
    fun infoIntervalSec(ctx: Context): Int = sp(ctx).getInt(KEY_INFO_INTERVAL, 3).coerceAtLeast(1)
    /**
     * Ring info parts to show: "time", "overview", "system", "notifications".
     * Older per-line values (memory / cpu) map to "system"; falls back to the
     * legacy single-choice setting.
     */
    fun ringInfo(ctx: Context): Set<String> {
        sp(ctx).getStringSet(KEY_RING_INFO, null)?.let { set ->
            return set.map { if (it == "memory" || it == "cpu") "system" else it }.toSet()
        }
        return when (sp(ctx).getString(KEY_CENTER_INFO, "none")) {
            "time", "both" -> setOf("time")
            else -> emptySet()
        }
    }
    fun innerRadiusDp(ctx: Context): Int = sp(ctx).getInt(KEY_INNER_RADIUS, DEFAULT_INNER_RADIUS_DP)
    /** Outer radius, pushed out if needed so the ring is at least [MIN_RING_DP] wide. */
    fun outerRadiusDp(ctx: Context): Int =
        sp(ctx).getInt(KEY_OUTER_RADIUS, DEFAULT_OUTER_RADIUS_DP)
            .coerceAtLeast(innerRadiusDp(ctx) + MIN_RING_DP)
    fun windowMode(ctx: Context): String =
        if (sp(ctx).getString(KEY_WINDOW_MODE, WINDOW_MODE_OVERLAY) == WINDOW_MODE_ACCESSIBILITY)
            WINDOW_MODE_ACCESSIBILITY else WINDOW_MODE_OVERLAY

    // ---- Unified slice list -------------------------------------------------
    /** Ordered slice tokens. Migrates from the legacy app list on first read. */
    fun slices(ctx: Context): List<String> {
        val raw = sp(ctx).getString(KEY_SLICES, null)
        if (raw == null) {
            val migrated = pieApps(ctx).map { "app:$it" }
            if (migrated.isNotEmpty()) setSlices(ctx, migrated)
            return migrated
        }
        return raw.split("\n").map { it.trim() }.filter { it.isNotEmpty() }
    }

    fun setSlices(ctx: Context, tokens: List<String>) {
        sp(ctx).edit().putString(KEY_SLICES, tokens.joinToString("\n")).apply()
    }

    fun addSlice(ctx: Context, token: String) = setSlices(ctx, slices(ctx) + token)
}
