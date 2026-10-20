package com.kj2431.pielauncher.service

import android.accessibilityservice.AccessibilityService
import android.content.res.Configuration
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
    private var orientation = Configuration.ORIENTATION_UNDEFINED
    private val onSettingsChanged: () -> Unit = { refreshOverlay() }

    override fun onServiceConnected() {
        instance = this
        orientation = resources.configuration.orientation
        refreshOverlay()
        OverlaySettings.register(this, onSettingsChanged)
    }
    /** Strip size and position depend on the current edge lengths; re-lay them out on rotation. */
    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        if (newConfig.orientation != orientation) {
            orientation = newConfig.orientation
            refreshOverlay()
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
        overlay?.removeEdges(); overlay = null
        if (instance !== this) return
        if (Prefs.serviceEnabled(this) &&
            Prefs.windowMode(this) == Prefs.WINDOW_MODE_ACCESSIBILITY
        ) {
            overlay = PieOverlay(this, WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY)
                .also { it.addEdges() }
        }
    }

    private fun tearDown() {
        OverlaySettings.unregister(onSettingsChanged)
        if (instance === this) instance = null
        overlay?.removeEdges(); overlay = null
    }

    companion object {
        @Volatile var instance: PieAccessibilityService? = null
            private set
        val isAvailable: Boolean get() = instance != null
    }
}
