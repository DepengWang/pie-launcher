package com.kj2431.pielauncher.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.view.WindowManager
import com.kj2431.pielauncher.R
import com.kj2431.pielauncher.prefs.Prefs
import com.kj2431.pielauncher.root.RootHelper

/**
 * Foreground service that keeps the launcher alive. In normal overlay mode it
 * owns the edge windows (see [PieOverlay]); in accessibility overlay mode the
 * windows are owned by [PieAccessibilityService] instead.
 */
class PieLauncherService : Service() {

    /** Only used in normal overlay mode; accessibility mode lives in [PieAccessibilityService]. */
    private var overlay: PieOverlay? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        startInForeground()
        applyWindowMode()
        if (Prefs.rootActionsEnabled(this)) RootHelper.prewarm()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        applyWindowMode()   // pick up Settings changes
        if (Prefs.rootActionsEnabled(this)) RootHelper.prewarm()
        return START_STICKY
    }

    /**
     * Rebuilds the edges for the current mode. In accessibility mode this
     * service draws nothing itself; the connected accessibility service (if
     * any) is asked to refresh its own TYPE_ACCESSIBILITY_OVERLAY windows.
     */
    private fun applyWindowMode() {
        overlay?.removeEdges(); overlay = null
        if (Prefs.windowMode(this) == Prefs.WINDOW_MODE_OVERLAY) {
            overlay = PieOverlay(this, overlayType()).also { it.addEdges() }
        }
        PieAccessibilityService.instance?.refreshOverlay()
    }

    private fun overlayType(): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE

    private fun startInForeground() {
        val channelId = "pie_launcher_running"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(
                NotificationChannel(channelId, getString(R.string.service_channel), NotificationManager.IMPORTANCE_MIN)
            )
        }
        val n: Notification = Notification.Builder(this, channelId)
            .setContentTitle(getString(R.string.service_running))
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .setOngoing(true)
            .build()
        startForeground(1, n)
    }

    override fun onDestroy() {
        overlay?.removeEdges(); overlay = null
        PieAccessibilityService.instance?.refreshOverlay()
        RootHelper.shutdown()
        super.onDestroy()
    }
}
