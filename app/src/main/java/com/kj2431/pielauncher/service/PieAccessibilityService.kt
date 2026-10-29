package com.kj2431.pielauncher.service

import android.accessibilityservice.AccessibilityService
import android.content.res.Configuration
import android.os.Handler
import android.os.Looper
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import com.kj2431.pielauncher.prefs.Prefs

/**
 * Optional, user-enabled service. We don't read screen content — we only need a
 * bound AccessibilityService so the action runner can call performGlobalAction()
 * for things like Back, Recents, Notifications, Power menu and Split screen.
 *
 * In "Accessibility overlay" window mode it also owns the edge windows, added as
 * TYPE_ACCESSIBILITY_OVERLAY through this service's own WindowManager. Those
 * stay visible over apps that set HIDE_NON_SYSTEM_OVERLAY_WINDOWS (e.g. the
 * OnePlus dialer), which hides TYPE_APPLICATION_OVERLAY windows. The windows
 * exist only while the service is connected; nothing is restarted on our side.
 */
class PieAccessibilityService : AccessibilityService() {

    private var overlay: PieOverlay? = null
    private var layoutKey = ""
    private val onSettingsChanged: () -> Unit = { refreshOverlay() }
    private val main = Handler(Looper.getMainLooper())
    private val relayout = Runnable {
        overlay?.relayout()
        scheduleHealthCheck()
    }
    private val healthCheck = object : Runnable {
        override fun run() {
            OverlayHealer.check(this@PieAccessibilityService)
            main.postDelayed(this, HEALTH_CHECK_PERIOD_MS)
        }
    }

    override fun onServiceConnected() {
        instance = this
        layoutKey = PieOverlay.layoutKey(resources.configuration)
        refreshOverlay()
        OverlaySettings.register(this, onSettingsChanged)
    }
    /** Strips depend on the edge lengths and slice labels on the language; rebuild when either changes. */
    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        if (PieOverlay.layoutKey(newConfig) != layoutKey) {
            layoutKey = PieOverlay.layoutKey(newConfig)
            // Not right away: the camera flips the screen several times in a row, and windows
            // added in the middle of a turn come out with that turn's rotation stuck on them.
            main.removeCallbacks(relayout)
            main.postDelayed(relayout, RELAYOUT_SETTLE_MS)
        }
    }
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    override fun onInterrupt() {}
    override fun onUnbind(intent: android.content.Intent?): Boolean {
        tearDown()
        return super.onUnbind(intent)
    }
    override fun onDestroy() {
        tearDown()
        super.onDestroy()
    }

    /**
     * Shows the edges iff the launcher is switched on and accessibility mode is
     * selected; otherwise removes them. Must be called on the main thread.
     */
    fun refreshOverlay() {
        main.removeCallbacks(healthCheck)
        main.removeCallbacks(relayout)
        overlay?.removeEdges(); overlay = null
        if (instance !== this) return
        if (Prefs.serviceEnabled(this) &&
            Prefs.windowMode(this) == Prefs.WINDOW_MODE_ACCESSIBILITY
        ) {
            overlay = PieOverlay(this, WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY)
                .also { it.addEdges() }
            scheduleHealthCheck()
        }
    }

    /** Debounced: waits for the screen to stop turning before looking at the input windows. */
    private fun scheduleHealthCheck() {
        main.removeCallbacks(healthCheck)
        main.postDelayed(healthCheck, HEALTH_CHECK_DELAY_MS)
    }

    private fun tearDown() {
        main.removeCallbacks(healthCheck)
        main.removeCallbacks(relayout)
        OverlaySettings.unregister(onSettingsChanged)
        if (instance === this) instance = null
        overlay?.removeEdges(); overlay = null
    }

    companion object {
        @Volatile var instance: PieAccessibilityService? = null
            private set
        val isAvailable: Boolean get() = instance != null
        private const val RELAYOUT_SETTLE_MS = 700L
        private const val HEALTH_CHECK_DELAY_MS = 1000L
        private const val HEALTH_CHECK_PERIOD_MS = 60_000L
    }
}
