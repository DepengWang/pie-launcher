package com.kj2431.pielauncher

import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatDelegate
import androidx.preference.ListPreference
import androidx.preference.PreferenceFragmentCompat
import com.kj2431.pielauncher.prefs.Prefs
import com.kj2431.pielauncher.ui.ThemeHelper

class SettingsFragment : PreferenceFragmentCompat() {
    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.root_preferences, rootKey)

        // Theme change: apply night mode + recreate so AMOLED overlay re-inflates.
        findPreference<ListPreference>(Prefs.KEY_THEME)?.setOnPreferenceChangeListener { _, value ->
            // value is the new selection; persist happens automatically after we return true,
            // but set night mode from the new value now for an immediate switch.
            val mode = when (value as String) {
                "light" -> AppCompatDelegate.MODE_NIGHT_NO
                "dark", "amoled" -> AppCompatDelegate.MODE_NIGHT_YES
                else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
            }
            AppCompatDelegate.setDefaultNightMode(mode)
            activity?.recreate()
            true
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val dm = resources.displayMetrics
        val maxContent = (560 * dm.density).toInt()
        val side = ((dm.widthPixels - maxContent) / 2).coerceAtLeast((16 * dm.density).toInt())
        listView.clipToPadding = false
        listView.setPadding(side, listView.paddingTop, side, listView.paddingBottom)
    }
}
