package com.kj2431.pielauncher.ui

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import com.kj2431.pielauncher.prefs.Prefs

/**
 * App language ("system", "zh-CN" or "en"), applied as a per-app locale. On
 * Android 13+ the system stores it and applies it to the whole process, the
 * overlay services included (they rebuild on the resulting configuration
 * change); older releases only switch the app's own screens.
 */
object LanguageHelper {

    fun apply(value: String) = AppCompatDelegate.setApplicationLocales(
        if (value == "system") LocaleListCompat.getEmptyLocaleList()
        else LocaleListCompat.forLanguageTags(value)
    )

    /** The language in effect, as a pref value; it can also be changed in the system's app-language settings. */
    fun current(ctx: Context): String {
        val locales = AppCompatDelegate.getApplicationLocales()
        if (locales.isEmpty) return "system"
        return when (locales[0]?.language) {
            "zh" -> "zh-CN"
            "en" -> "en"
            else -> Prefs.language(ctx)
        }
    }
}
