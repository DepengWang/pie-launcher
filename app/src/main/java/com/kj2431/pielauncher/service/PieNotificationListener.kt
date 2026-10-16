package com.kj2431.pielauncher.service

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

/**
 * Optional, user-granted (Settings > Notification access). Feeds the pie's
 * ring info: the count of dismissable notifications and, for the optional
 * notification pages, a short in-memory summary (app, time, title + text) of
 * the newest few. Nothing is written to disk or sent anywhere.
 */
class PieNotificationListener : NotificationListenerService() {

    /** One notification as shown on a ring info page (LMT style). */
    data class Summary(val app: String, val postTime: Long, val text: String)

    override fun onListenerConnected() { refresh() }
    override fun onListenerDisconnected() { count = null; recent = emptyList() }
    override fun onNotificationPosted(sbn: StatusBarNotification?) { refresh() }
    override fun onNotificationRemoved(sbn: StatusBarNotification?) { refresh() }

    private fun refresh() {
        val shown = runCatching {
            activeNotifications.filter { sbn ->
                sbn.isClearable && sbn.packageName != packageName &&
                    sbn.notification.flags and Notification.FLAG_GROUP_SUMMARY == 0
            }
        }.getOrNull()
        count = shown?.size
        recent = shown.orEmpty().sortedByDescending { it.postTime }.take(MAX_RECENT).map { summarize(it) }
    }

    private fun summarize(sbn: StatusBarNotification): Summary {
        val app = runCatching {
            packageManager.getApplicationLabel(packageManager.getApplicationInfo(sbn.packageName, 0)).toString()
        }.getOrDefault(sbn.packageName)
        val extras = sbn.notification.extras
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()?.trim().orEmpty()
        val body = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()?.trim().orEmpty()
        val text = listOf(title, body).filter { it.isNotEmpty() }.joinToString(": ")
            .ifEmpty { sbn.notification.tickerText?.toString().orEmpty() }
        return Summary(app, sbn.postTime, text)
    }

    companion object {
        private const val MAX_RECENT = 5   // as LMT

        /** Current count, or null while the listener isn't connected. */
        @Volatile var count: Int? = null
            private set

        /** Newest first; empty while the listener isn't connected. */
        @Volatile var recent: List<Summary> = emptyList()
            private set
    }
}
