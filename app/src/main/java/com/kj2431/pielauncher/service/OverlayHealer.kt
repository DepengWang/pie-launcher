package com.kj2431.pielauncher.service

import android.content.Context
import android.hardware.display.DisplayManager
import android.os.PowerManager
import android.util.Log
import android.view.Display
import android.view.Surface
import com.kj2431.pielauncher.prefs.Prefs
import com.kj2431.pielauncher.root.RootHelper
import kotlin.concurrent.thread

/**
 * Repairs edge windows whose input transform is stuck rotated.
 *
 * On OxygenOS the window token of an accessibility overlay can keep a stale
 * rotation after the screen turns: the window manager still reports the right
 * frame, but the input dispatcher (and the surface) treat the strip as rotated
 * 90 degrees, so it sits sideways or off-screen and nothing triggers. Re-adding
 * the windows keeps the same token and doesn't help; only a new accessibility
 * connection does, so that is what [check] does when it sees the symptom.
 *
 * The symptom is only visible to root (`dumpsys input`), so this does nothing
 * unless root actions are on. Only upright portrait is judged, where a healthy
 * edge window is ROT_0. Seen in the field: the camera flips the screen several
 * times while it starts, and the return to portrait leaves ROT_270 behind. The
 * service calls it after every relayout and then once a minute, because the
 * stale rotation can also show up without a configuration change reaching us.
 */
object OverlayHealer {

    private const val TAG = "OverlayHealer"

    /**
     * Each landscape camera shot can break the windows once, so repairs must be
     * allowed back to back; the cap only stops a loop if a reconnect never cures it.
     */
    private const val HEAL_WINDOW_MS = 120_000L
    private const val MAX_HEALS_IN_WINDOW = 4

    /** Counts our edge windows that the input dispatcher has rotated (it prints ROT_90, ROT_180, ROT_270). */
    private val PROBE = """
        dumpsys input | grep -A1 -E 'name=[0-9a-f]+ com\.kj2431\.pielauncher, ' | grep -cE 'transform \(ROT_(90|180|270)\)'; true
    """.trimIndent()

    private val healTimes = ArrayDeque<Long>()
    private var running = false

    /** Call after the edges were (re)added and the screen has settled. Main thread. */
    fun check(ctx: Context) {
        val app = ctx.applicationContext
        if (running || !Prefs.rootActionsEnabled(app)) return
        if (app.getSystemService(PowerManager::class.java).isInteractive.not()) return
        // Only upright portrait is judged: there a healthy window is ROT_0, while in landscape
        // the dispatcher legitimately shows ROT_270 for the same windows (seen as false alarms).
        val display = app.getSystemService(DisplayManager::class.java).getDisplay(Display.DEFAULT_DISPLAY)
        if (display?.rotation != Surface.ROTATION_0) return
        running = true
        thread(name = "overlay-health") {
            try {
                val bad = RootHelper.query(PROBE)?.trim()?.toIntOrNull() ?: return@thread
                if (bad == 0) return@thread
                Log.w(TAG, "input transform rotated on $bad windows")
                val now = System.currentTimeMillis()
                healTimes.removeAll { now - it > HEAL_WINDOW_MS }
                if (healTimes.size >= MAX_HEALS_IN_WINDOW) {
                    Log.w(TAG, "skipping reconnect (${healTimes.size} in the last ${HEAL_WINDOW_MS / 1000}s)")
                    return@thread
                }
                healTimes += now
                Log.w(TAG, "reconnect ${if (AccessibilityGuard.reconnect(app)) "done" else "failed"}")
            } finally {
                running = false
            }
        }
    }
}
