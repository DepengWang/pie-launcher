package com.kj2431.pielauncher

import android.content.ClipData
import android.content.ClipboardManager
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatDelegate
import androidx.preference.ListPreference
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.SwitchPreferenceCompat
import com.kj2431.pielauncher.prefs.ConfigBackup
import com.kj2431.pielauncher.prefs.Prefs
import com.kj2431.pielauncher.service.AccessibilityGuard
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.concurrent.thread
import com.kj2431.pielauncher.ui.LanguageHelper
import com.kj2431.pielauncher.ui.ThemeHelper

class SettingsFragment : PreferenceFragmentCompat() {

    private val exportFile = registerForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) exportTo(uri)
    }
    private val importFile = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) confirmImport(uri)
    }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.root_preferences, rootKey)

        findPreference<Preference>(KEY_EXPORT)?.setOnPreferenceClickListener {
            val date = SimpleDateFormat("yyyyMMdd-HHmm", Locale.US).format(Date())
            exportFile.launch("pie-launcher-$date.json")
            true
        }
        findPreference<Preference>(KEY_IMPORT)?.setOnPreferenceClickListener {
            // Some file managers label .json as text/plain or octet-stream.
            importFile.launch(arrayOf("application/json", "text/plain", "application/octet-stream"))
            true
        }

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

        // Keep accessibility on: get WRITE_SECURE_SETTINGS (root, else ask for
        // a one-time adb grant) and turn the service on right away.
        findPreference<SwitchPreferenceCompat>(Prefs.KEY_KEEP_A11Y)?.setOnPreferenceChangeListener { pref, value ->
            if (value == true) enableKeepAccessibility(pref as SwitchPreferenceCompat)
            true
        }

        // Language: show what is actually in effect (it may have been changed in
        // system settings), and apply a new choice right away; AppCompat
        // recreates the open screens itself.
        findPreference<ListPreference>(Prefs.KEY_LANGUAGE)?.apply {
            val current = LanguageHelper.current(requireContext())
            if (value != current) value = current
            setOnPreferenceChangeListener { _, value ->
                LanguageHelper.apply(value as String)
                true
            }
        }
    }

    private fun enableKeepAccessibility(pref: SwitchPreferenceCompat) {
        val ctx = requireContext().applicationContext
        thread(name = "a11y-grant") {
            val ok = AccessibilityGuard.grantViaRoot(ctx) && AccessibilityGuard.enable(ctx)
            activity?.runOnUiThread {
                if (!isAdded) return@runOnUiThread
                if (ok) Toast.makeText(ctx, R.string.a11y_guard_on, Toast.LENGTH_SHORT).show()
                else { pref.isChecked = false; showGrantHelp() }
            }
        }
    }

    private fun showGrantHelp() {
        val cmd = "adb shell " + AccessibilityGuard.grantCommand(requireContext())
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.a11y_guard_need_grant_title)
            .setMessage(getString(R.string.a11y_guard_need_grant_msg, cmd))
            .setPositiveButton(R.string.a11y_guard_copy) { _, _ ->
                val cm = requireContext().getSystemService(ClipboardManager::class.java)
                cm.setPrimaryClip(ClipData.newPlainText("adb", cmd))
                Toast.makeText(requireContext(), R.string.a11y_guard_copied, Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun exportTo(uri: Uri) {
        val ctx = requireContext()
        val msg = runCatching { ConfigBackup.export(ctx, uri) }.fold(
            onSuccess = { getString(R.string.backup_exported, it) },
            onFailure = { getString(R.string.backup_export_failed, it.message ?: it.javaClass.simpleName) }
        )
        Toast.makeText(ctx, msg, Toast.LENGTH_LONG).show()
    }

    private fun confirmImport(uri: Uri) {
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.pref_import_title)
            .setMessage(R.string.backup_import_confirm)
            .setPositiveButton(R.string.backup_import_action) { _, _ -> importFrom(uri) }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun importFrom(uri: Uri) {
        val activity = requireActivity()
        val result = runCatching { ConfigBackup.import(activity, uri) }
        val msg = result.fold(
            onSuccess = { getString(R.string.backup_imported, it) },
            onFailure = {
                when (it) {
                    is ConfigBackup.InvalidFileException -> getString(R.string.backup_invalid)
                    is ConfigBackup.TooNewException -> getString(R.string.backup_too_new)
                    else -> getString(R.string.backup_import_failed, it.message ?: it.javaClass.simpleName)
                }
            }
        )
        Toast.makeText(activity, msg, Toast.LENGTH_LONG).show()
        if (result.isFailure) return
        // The running overlay rebuilds from the pref change on its own; this
        // screen, the theme and the language need applying by hand.
        AppCompatDelegate.setDefaultNightMode(ThemeHelper.nightMode(activity))
        LanguageHelper.apply(Prefs.language(activity))
        activity.recreate()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val dm = resources.displayMetrics
        val maxContent = (560 * dm.density).toInt()
        val side = ((dm.widthPixels - maxContent) / 2).coerceAtLeast((16 * dm.density).toInt())
        listView.clipToPadding = false
        listView.setPadding(side, listView.paddingTop, side, listView.paddingBottom)
    }

    private companion object {
        const val KEY_EXPORT = "pref_export_config"
        const val KEY_IMPORT = "pref_import_config"
    }
}
