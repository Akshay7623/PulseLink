package com.example.notification

import android.content.Context
import androidx.core.app.NotificationManagerCompat
import com.example.model.DeviceNotification

/**
 * The system notifications showing on this device right now, plus the user's sharing preferences.
 * Nothing is written to disk; PulseNotificationListener fills it again after a restart.
 */
object DeviceNotificationStore {

    private const val MAX_ITEMS = 50
    private const val PREFS_NAME = "pulselink_prefs"
    private const val KEY_SHARE_NOTIFICATIONS = "share_device_notifications"
    private const val KEY_PEER_ALERTS = "peer_notification_alerts"

    // One entry per notification, so an update (call ringing, then ongoing) replaces what was there
    private val active = mutableMapOf<String, DeviceNotification>()

    /**
     * @return false when an app merely re-posted a notification that is still showing unchanged
     */
    @Synchronized
    fun add(item: DeviceNotification): Boolean {
        val previous = active.put(item.key, item)
        return previous == null || previous.title != item.title || previous.text != item.text ||
            previous.messages != item.messages || previous.callState != item.callState
    }

    /**
     * @return false when the notification was not being tracked
     */
    @Synchronized
    fun remove(key: String): Boolean = active.remove(key) != null

    @Synchronized
    fun clear() {
        active.clear()
    }

    @Synchronized
    fun snapshot(): List<DeviceNotification> = active.values.sortedByDescending { it.postTime }.take(MAX_ITEMS)

    @Synchronized
    fun activeKeys(): List<String> = active.keys.toList()

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

    // Whether a peer's notifications pop up on this device as they arrive
    fun isPeerAlertsEnabled(context: Context): Boolean {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_PEER_ALERTS, true)
    }

    fun setPeerAlertsEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_PEER_ALERTS, enabled)
            .apply()
    }

    fun hasNotificationAccess(context: Context): Boolean {
        return NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName)
    }
}
