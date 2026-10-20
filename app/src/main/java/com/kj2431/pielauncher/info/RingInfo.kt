package com.kj2431.pielauncher.info

import android.app.ActivityManager
import android.app.KeyguardManager
import android.content.Context
import android.media.AudioManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.BatteryManager
import android.text.format.DateFormat
import com.kj2431.pielauncher.R
import com.kj2431.pielauncher.service.PieNotificationListener
import java.io.File
import java.util.Calendar
import java.util.Locale

/**
 * Status text shown around the outside of the pie, modelled on LMT's
 * PieStatusInfo: a big clock plus pages of three small lines that rotate
 * while the pie is open. Each call is a cheap snapshot; nothing here reads
 * the content of other apps (notifications are only counted).
 */
object RingInfo {

    /** Pref values for [com.kj2431.pielauncher.prefs.Prefs.ringInfo]. */
    const val TIME = "time"
    const val OVERVIEW = "overview"   // date, battery + ringer, network
    const val SYSTEM = "system"       // notification count, CPU, memory
    const val NOTIFICATIONS = "notifications"  // one page per recent notification

    /** Big clock text and its small suffix ("24h", "AM" or "PM"), as in LMT. */
    fun clock(ctx: Context): Pair<String, String> {
        val c = Calendar.getInstance()
        return if (DateFormat.is24HourFormat(ctx)) {
            String.format(Locale.US, "%02d:%02d", c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE)) to "24h"
        } else {
            val h = c.get(Calendar.HOUR).let { if (it == 0) 12 else it }
            String.format(Locale.US, "%02d:%02d", h, c.get(Calendar.MINUTE)) to
                if (c.get(Calendar.AM_PM) == Calendar.AM) "AM" else "PM"
        }
    }

    /** Lines of one page, outermost first. */
    fun overview(ctx: Context): List<String> = listOf(date(ctx), battery(ctx) + " | " + ringer(ctx), network(ctx))

    fun system(ctx: Context): List<String> = listOfNotNull(notifications(ctx), cpu(ctx), memory(ctx))

    /**
     * One page per recent notification: time, app, title + text (LMT style,
     * cut at 25 characters). Empty while the device is locked, so the pie
     * never shows notification content on the lock screen.
     */
    fun notificationPages(ctx: Context): List<List<String>> {
        val km = ctx.getSystemService(KeyguardManager::class.java)
        if (km == null || km.isKeyguardLocked) return emptyList()
        return PieNotificationListener.recent.map { n ->
            listOf(clockOf(ctx, n.postTime), cut(n.app), cut(n.text))
        }
    }

    private fun clockOf(ctx: Context, millis: Long): String =
        DateFormat.getTimeFormat(ctx).format(java.util.Date(millis))

    private fun cut(s: String) = if (s.length > 25) s.substring(0, 24) + "..." else s

    private fun date(ctx: Context): String {
        val c = Calendar.getInstance()
        val y = String.format(Locale.US, "%04d", c.get(Calendar.YEAR))
        val m = String.format(Locale.US, "%02d", c.get(Calendar.MONTH) + 1)
        val d = String.format(Locale.US, "%02d", c.get(Calendar.DAY_OF_MONTH))
        val order = runCatching { DateFormat.getDateFormatOrder(ctx) }.getOrNull() ?: charArrayOf('y', 'M', 'd')
        return order.map { when (it) { 'y' -> y; 'M' -> m; else -> d } }.joinToString(" | ")
    }

    private fun battery(ctx: Context): String {
        val bm = ctx.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        val pct = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        return ctx.getString(R.string.ring_battery, if (pct < 0) "?" else "$pct%") + if (bm.isCharging) " ⚡" else ""
    }

    private fun ringer(ctx: Context): String {
        val am = ctx.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
            ?: return ctx.getString(R.string.ring_ringer)
        return when (am.ringerMode) {
            AudioManager.RINGER_MODE_SILENT -> ctx.getString(R.string.ring_ringer_silent)
            AudioManager.RINGER_MODE_VIBRATE -> ctx.getString(R.string.ring_ringer_vibrate)
            else -> ctx.getString(R.string.ring_ringer_volume, am.getStreamVolume(AudioManager.STREAM_RING))
        }
    }

    /** "Connected | WIFI | -46 dBm". Looks through a VPN to the network it rides on. */
    private fun network(ctx: Context): String {
        val notConnected = ctx.getString(R.string.ring_not_connected)
        val cm = ctx.getSystemService(ConnectivityManager::class.java) ?: return notConnected
        val active = cm.activeNetwork?.let { cm.getNetworkCapabilities(it) } ?: return notConnected
        @Suppress("DEPRECATION")
        val caps = if (!active.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) active
            else cm.allNetworks.mapNotNull { cm.getNetworkCapabilities(it) }.firstOrNull {
                !it.hasTransport(NetworkCapabilities.TRANSPORT_VPN) &&
                    it.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            } ?: active
        fun via(type: String) = ctx.getString(R.string.ring_connected_via, type)
        return when {
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> via("WIFI") +
                caps.signalStrength.takeIf { it != NetworkCapabilities.SIGNAL_STRENGTH_UNSPECIFIED }
                    ?.let { " | $it dBm" }.orEmpty()
            caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> via("MOBILE")
            caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> via("ETHERNET")
            else -> ctx.getString(R.string.ring_connected)
        }
    }

    /** Count from [PieNotificationListener]; it only ever stores a number. */
    private fun notifications(ctx: Context): String {
        val n = PieNotificationListener.count ?: return ctx.getString(R.string.ring_notif_grant)
        return if (n == 0) ctx.getString(R.string.ring_notif_none)
            else ctx.resources.getQuantityString(R.plurals.ring_notif_count, n, n)
    }

    /** Highest current core frequency plus the online core range, e.g. "1958MHz | Core 0-7 active". */
    private fun cpu(ctx: Context): String? {
        val online = readFile("/sys/devices/system/cpu/online") ?: return null
        val maxKHz = onlineCores(online).mapNotNull { core ->
            readFile("/sys/devices/system/cpu/cpu$core/cpufreq/scaling_cur_freq")?.toLongOrNull()
        }.maxOrNull()
        return (maxKHz?.let { "${it / 1000}MHz | " } ?: "") + ctx.getString(R.string.ring_cpu_cores, online)
    }

    private fun memory(ctx: Context): String {
        val am = ctx.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val mi = ActivityManager.MemoryInfo().also { am.getMemoryInfo(it) }
        return ctx.getString(R.string.ring_memory, gb(mi.totalMem - mi.availMem), gb(mi.availMem))
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
