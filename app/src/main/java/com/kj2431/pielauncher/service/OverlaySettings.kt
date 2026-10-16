package com.kj2431.pielauncher.service

import android.content.Context
import android.content.SharedPreferences
import android.os.Handler
import android.os.Looper
import androidx.preference.PreferenceManager
import com.kj2431.pielauncher.prefs.Prefs

/**
 * In-process signal that the edge windows should be rebuilt: fires when any
 * setting changes or when the Settings screen toggles [preview]. Bursts are
 * coalesced so a run of changes rebuilds only once. Main thread only.
 */
object OverlaySettings {

    private const val DEBOUNCE_MS = 120L
    /** Keys whose change is handled elsewhere and must not trigger a rebuild. */
    private val ignored = setOf(Prefs.KEY_ENABLED)

    private val main = Handler(Looper.getMainLooper())
    private val listeners = mutableSetOf<() -> Unit>()
    private val fire = Runnable { listeners.toList().forEach { it() } }
    private var prefs: SharedPreferences? = null
    // Held strongly: SharedPreferences only keeps a weak reference to it.
    private val prefListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key !in ignored) changed()
    }

    /** While true (Settings is open), activation strips are drawn even if hidden. */
    var preview = false
        set(value) { if (field != value) { field = value; changed() } }

    fun register(ctx: Context, listener: () -> Unit) {
        if (prefs == null) {
            prefs = PreferenceManager.getDefaultSharedPreferences(ctx.applicationContext)
                .also { it.registerOnSharedPreferenceChangeListener(prefListener) }
        }
        listeners += listener
    }

    fun unregister(listener: () -> Unit) {
        listeners -= listener
        if (listeners.isEmpty()) {
            main.removeCallbacks(fire)
            prefs?.unregisterOnSharedPreferenceChangeListener(prefListener)
            prefs = null
        }
    }

    private fun changed() {
        main.removeCallbacks(fire)
        main.postDelayed(fire, DEBOUNCE_MS)
    }
}
