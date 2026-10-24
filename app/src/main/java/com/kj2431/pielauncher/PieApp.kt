package com.kj2431.pielauncher

import android.app.Application
import android.os.Build
import androidx.appcompat.app.AppCompatDelegate
import com.kj2431.pielauncher.prefs.Prefs
import com.kj2431.pielauncher.service.AccessibilityGuard
import com.kj2431.pielauncher.ui.LanguageHelper
import com.kj2431.pielauncher.ui.ThemeHelper

class PieApp : Application() {
    override fun onCreate() {
        super.onCreate()
        AppCompatDelegate.setDefaultNightMode(ThemeHelper.nightMode(this))
        // Android 13+ keeps the per-app language itself; before that it lasts only per process.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) LanguageHelper.apply(Prefs.language(this))
        // Every process start (app opened, boot, service restart) is a chance to undo a switch-off.
        AccessibilityGuard.ensure(this)
    }
}
