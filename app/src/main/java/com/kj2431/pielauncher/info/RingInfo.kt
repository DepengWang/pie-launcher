package com.kj2431.pielauncher.info

import android.app.ActivityManager
import android.content.Context
import com.kj2431.pielauncher.service.PieNotificationListener
import java.io.File
import java.util.Locale

/**
 * Small status lines shown around the outside of the pie (LMT style). Each
 * call is a cheap snapshot taken when the pie opens; nothing here reads the
 * content of other apps.
 */
object RingInfo {

    const val TIME = "time"
    const val NOTIFICATIONS = "notifications"
    const val CPU = "cpu"
    const val MEMORY = "memory"

    /** Count from [PieNotificationListener]; it only ever stores a number. */
    fun notifications(): String {
        val n = PieNotificationListener.count ?: return "Notifications: grant access"
        return when (n) {
            0 -> "No notifications"
            1 -> "1 notification"
            else -> "$n notifications"
        }
    }

    /** Highest current core frequency plus the online core range, e.g. "CPU 1958 MHz | Core 0-7 online". */
    fun cpu(): String? {
        val online = readFile("/sys/devices/system/cpu/online") ?: return null
        val maxKHz = onlineCores(online).mapNotNull { core ->
            readFile("/sys/devices/system/cpu/cpu$core/cpufreq/scaling_cur_freq")?.toLongOrNull()
        }.maxOrNull()
        val freq = maxKHz?.let { "CPU ${it / 1000} MHz | " } ?: "CPU | "
        return freq + "Core $online online"
    }

    fun memory(ctx: Context): String {
        val am = ctx.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val mi = ActivityManager.MemoryInfo().also { am.getMemoryInfo(it) }
        return "RAM used ${gb(mi.totalMem - mi.availMem)} | free ${gb(mi.availMem)}"
    }

    private fun gb(bytes: Long) = String.format(Locale.US, "%.1f GB", bytes / 1_073_741_824.0)

    private fun readFile(path: String): String? =
        runCatching { File(path).readText().trim() }.getOrNull()?.takeIf { it.isNotEmpty() }

    /** Parses the kernel's cpu list format, e.g. "0-3,6". */
    private fun onlineCores(spec: String): List<Int> = spec.split(",").flatMap { part ->
        val bounds = part.split("-").mapNotNull { it.trim().toIntOrNull() }
        when (bounds.size) {
            1 -> listOf(bounds[0])
            2 -> (bounds[0]..bounds[1]).toList()
            else -> emptyList()
        }
    }
}
