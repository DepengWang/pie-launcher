package com.kj2431.pielauncher

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.kj2431.pielauncher.ui.ThemeHelper
import androidx.appcompat.widget.Toolbar
import androidx.core.content.ContextCompat
import com.kj2431.pielauncher.ui.EdgeToEdge
import com.kj2431.pielauncher.prefs.Prefs
import com.kj2431.pielauncher.service.PieAccessibilityService
import com.kj2431.pielauncher.service.PieLauncherService

/**
 * Control surface: grant overlay permission, start/stop the service, open Settings.
 * Handles Android 15 edge-to-edge so all content sits below the header.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var status: TextView
    private lateinit var toggle: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ThemeHelper.applyActivityTheme(this)
        setContentView(R.layout.activity_main)
        setSupportActionBar(findViewById<Toolbar>(R.id.toolbar))

        // Edge-to-edge handled by the shared helper (future-proof, no opt-out flag).
        EdgeToEdge.setup(this, findViewById(R.id.root))

        status = findViewById(R.id.status)
        toggle = findViewById(R.id.toggle)
        findViewById<Button>(R.id.openSettings).setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
        toggle.setOnClickListener { onToggle() }
    }

    override fun onResume() { super.onResume(); render() }

    private fun onToggle() {
        if (!accessibilityMode() && !canDrawOverlays()) { requestOverlayPermission(); return }
        val newState = !Prefs.serviceEnabled(this)
        androidx.preference.PreferenceManager.getDefaultSharedPreferences(this)
            .edit().putBoolean(Prefs.KEY_ENABLED, newState).apply()
        val intent = Intent(this, PieLauncherService::class.java)
        if (newState) ContextCompat.startForegroundService(this, intent) else stopService(intent)
        render()
    }

    private fun render() {
        val overlay = canDrawOverlays()
        val a11yMode = accessibilityMode()
        val on = Prefs.serviceEnabled(this) && (a11yMode || overlay)
        status.text = buildString {
            appendLine("Overlay permission: ${if (overlay) "granted" else "NOT granted"}")
            appendLine("Service: ${if (on) "running" else "stopped"}")
            appendLine("Window mode: ${if (a11yMode) "accessibility overlay" else "normal overlay"}")
            if (a11yMode) appendLine("Accessibility service: " +
                if (PieAccessibilityService.isAvailable) "connected" else "NOT connected (edges hidden)")
            append("Edges: ${Prefs.edges(this@MainActivity).joinToString { it.key }}  ")
            append("| ${Prefs.thicknessDp(this@MainActivity)}dp | center ${Prefs.centerPercent(this@MainActivity)}%")
        }
        toggle.text = if (on) getString(R.string.stop) else getString(R.string.start)
    }

    private fun accessibilityMode(): Boolean =
        Prefs.windowMode(this) == Prefs.WINDOW_MODE_ACCESSIBILITY

    private fun canDrawOverlays(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.canDrawOverlays(this)

    private fun requestOverlayPermission() {
        startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
    }
}
