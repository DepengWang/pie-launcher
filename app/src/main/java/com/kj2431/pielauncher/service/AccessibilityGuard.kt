package com.kj2431.pielauncher.service

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import com.kj2431.pielauncher.prefs.Prefs
import com.kj2431.pielauncher.root.RootHelper
import kotlin.concurrent.thread

/**
 * Turns [PieAccessibilityService] back on when the system switches it off
 * (OEM battery managers, "clean all" in recents, security sweeps), if the user
 * opted in with [Prefs.keepAccessibility].
 *
 * Writing the secure setting needs WRITE_SECURE_SETTINGS, which is granted once
 * through root (or by hand with adb); after that no root is used. A force-stop
 * kills this process too, so that case recovers only the next time the process
 * starts (opening the app, or a reboot unless the app is still in stopped state).
 */
object AccessibilityGuard {

    private const val TAG = "AccessibilityGuard"
    private const val DEBOUNCE_MS = 1000L
    private const val RECONNECT_GAP_MS = 1500L

    private val main = Handler(Looper.getMainLooper())
    /** Set while [reconnect] has the service off on purpose, so [ensure] doesn't race it. */
    @Volatile private var reconnecting = false
    private var observer: ContentObserver? = null

    fun grantCommand(ctx: Context) =
        "pm grant ${ctx.packageName} ${Manifest.permission.WRITE_SECURE_SETTINGS}"

    fun isEnabled(ctx: Context): Boolean {
        val raw = Settings.Secure.getString(ctx.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
            ?: return false
        val me = component(ctx)
        return raw.split(':').any { ComponentName.unflattenFromString(it) == me }
    }

    fun canWrite(ctx: Context): Boolean =
        ctx.checkSelfPermission(Manifest.permission.WRITE_SECURE_SETTINGS) == PackageManager.PERMISSION_GRANTED

    /** Grants WRITE_SECURE_SETTINGS through su. Blocking; call off the main thread. */
    fun grantViaRoot(ctx: Context): Boolean =
        canWrite(ctx) || (RootHelper.exec(grantCommand(ctx)) && canWrite(ctx))

    /**
     * Adds our service to the enabled list, keeping the others. Returns true if
     * it is enabled afterwards. Needs [canWrite]; safe on any thread.
     */
    fun enable(ctx: Context): Boolean {
        if (isEnabled(ctx)) return true
        if (!canWrite(ctx)) return false
        return try {
            val cr = ctx.contentResolver
            val others = Settings.Secure.getString(cr, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
                .orEmpty().split(':').filter { it.isNotBlank() }
            val list = (others + component(ctx).flattenToString()).joinToString(":")
            Settings.Secure.putString(cr, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES, list)
            Settings.Secure.putInt(cr, Settings.Secure.ACCESSIBILITY_ENABLED, 1)
            Log.i(TAG, "accessibility service re-enabled")
            true
        } catch (e: SecurityException) {
            Log.w(TAG, "could not write secure settings", e); false
        }
    }

    /**
     * Switches our service off and on again so the system issues a fresh window
     * token for its overlay windows. Blocking; call off the main thread. Writes
     * the setting directly when [canWrite], otherwise through root.
     */
    fun reconnect(ctx: Context): Boolean {
        val app = ctx.applicationContext
        val me = component(app)
        val others = Settings.Secure.getString(app.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
            .orEmpty().split(':').filter { it.isNotBlank() && ComponentName.unflattenFromString(it) != me }
        reconnecting = true
        try {
            if (!putServices(app, others, others.isNotEmpty())) return false
            Thread.sleep(RECONNECT_GAP_MS)
            return putServices(app, others + me.flattenToString(), true)
        } finally {
            reconnecting = false
        }
    }

    private fun putServices(ctx: Context, list: List<String>, on: Boolean): Boolean {
        val joined = list.joinToString(":")
        if (canWrite(ctx)) return try {
            val cr = ctx.contentResolver
            Settings.Secure.putString(cr, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES, joined)
            Settings.Secure.putInt(cr, Settings.Secure.ACCESSIBILITY_ENABLED, if (on) 1 else 0)
            true
        } catch (e: SecurityException) {
            Log.w(TAG, "could not write secure settings", e); false
        }
        return RootHelper.exec(
            "settings put secure ${Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES} '$joined'",
            "settings put secure ${Settings.Secure.ACCESSIBILITY_ENABLED} ${if (on) 1 else 0}"
        )
    }

    /**
     * Re-enables the service if the user asked for that. Falls back to a root
     * grant only when root actions are on, so it never pops an unexpected su prompt.
     */
    fun ensure(ctx: Context) {
        val app = ctx.applicationContext
        if (reconnecting || !Prefs.keepAccessibility(app) || isEnabled(app)) return
        if (canWrite(app)) { enable(app); return }
        if (Prefs.rootActionsEnabled(app)) thread(name = "a11y-guard") {
            if (grantViaRoot(app)) enable(app)
        }
    }

    /** Watches the enabled-services setting so a switch-off is undone right away. */
    fun watch(ctx: Context) {
        if (observer != null) return
        val app = ctx.applicationContext
        val recheck = Runnable { ensure(app) }
        observer = object : ContentObserver(main) {
            override fun onChange(selfChange: Boolean) {
                main.removeCallbacks(recheck)
                main.postDelayed(recheck, DEBOUNCE_MS)
            }
        }.also {
            app.contentResolver.registerContentObserver(
                Settings.Secure.getUriFor(Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES), false, it
            )
        }
    }

    fun unwatch(ctx: Context) {
        observer?.let { ctx.applicationContext.contentResolver.unregisterContentObserver(it) }
        observer = null
    }

    private fun component(ctx: Context) = ComponentName(ctx, PieAccessibilityService::class.java)
}
