package com.example.notification

import android.content.Context
import androidx.core.app.NotificationManagerCompat
import com.example.model.DeviceNotification

/**
 * In-memory log of this device's recent system notifications, plus the user's sharing preference.
 * Nothing is written to disk; the log is rebuilt by PulseNotificationListener after a restart.
 */
object DeviceNotificationStore {

    private const val MAX_ITEMS = 50
    private const val PREFS_NAME = "pulselink_prefs"
    private const val KEY_SHARE_NOTIFICATIONS = "share_device_notifications"

    private var items: List<DeviceNotification> = emptyList()

    @Synchronized
    fun add(item: DeviceNotification) {
        // An app re-posting the same content only refreshes its timestamp
        val withoutDuplicate = items.filterNot {
            it.key == item.key && it.title == item.title && it.text == item.text
        }
        items = (withoutDuplicate + item).sortedByDescending { it.postTime }.take(MAX_ITEMS)
    }

    @Synchronized
    fun snapshot(): List<DeviceNotification> = items

    fun isSharingEnabled(context: Context): Boolean {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_SHARE_NOTIFICATIONS, false)
    }

    fun setSharingEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_SHARE_NOTIFICATIONS, enabled)
            .apply()
    }

    fun hasNotificationAccess(context: Context): Boolean {
        return NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName)
    }
}
