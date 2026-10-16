package com.kj2431.pielauncher.service

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

/**
 * Optional, user-granted (Settings > Notification access). Used only to count
 * dismissable notifications for the pie's ring info — titles, text and extras
 * are never read or stored.
 */
class PieNotificationListener : NotificationListenerService() {

    override fun onListenerConnected() { recount() }
    override fun onListenerDisconnected() { count = null }
    override fun onNotificationPosted(sbn: StatusBarNotification?) { recount() }
    override fun onNotificationRemoved(sbn: StatusBarNotification?) { recount() }

    private fun recount() {
        count = runCatching {
            activeNotifications.count { sbn ->
                sbn.isClearable && sbn.packageName != packageName &&
                    sbn.notification.flags and Notification.FLAG_GROUP_SUMMARY == 0
            }
        }.getOrNull()
    }

    companion object {
        /** Current count, or null while the listener isn't connected. */
        @Volatile var count: Int? = null
            private set
    }
}
