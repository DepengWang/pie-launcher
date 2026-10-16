package com.kj2431.pielauncher.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import com.kj2431.pielauncher.prefs.Prefs

/** Restarts the overlay after reboot or an app update, but only if the user had it enabled. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val restart = intent.action == Intent.ACTION_BOOT_COMPLETED ||
            intent.action == Intent.ACTION_MY_PACKAGE_REPLACED
        if (restart && Prefs.serviceEnabled(context)) {
            ContextCompat.startForegroundService(
                context, Intent(context, PieLauncherService::class.java)
            )
        }
    }
}
