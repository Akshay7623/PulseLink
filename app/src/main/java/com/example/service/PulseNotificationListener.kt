package com.example.service

import android.app.Notification
import android.content.pm.ApplicationInfo
import android.os.Bundle
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import androidx.core.os.BundleCompat
import com.example.model.DeviceNotification
import com.example.notification.DeviceNotificationStore

/**
 * Captures this device's system notifications (WhatsApp, SMS, calls, etc.) so a paired
 * device can view them. Only active once the user grants Notification access in Settings.
 */
class PulseNotificationListener : NotificationListenerService() {

    override fun onListenerConnected() {
        super.onListenerConnected()
        try {
            activeNotifications?.forEach { record(it) }
        } catch (e: Exception) {
            Log.e(TAG, "Error reading active notifications", e)
        }
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        sbn?.let { record(it) }
    }

    private fun record(sbn: StatusBarNotification) {
        try {
            if (sbn.packageName == packageName) return
            val notification = sbn.notification ?: return

            // Group summaries duplicate the individual notifications they bundle
            if ((notification.flags and Notification.FLAG_GROUP_SUMMARY) != 0) return
            // Skip persistent status items (media, downloads, USB) but keep ongoing calls
            if (sbn.isOngoing && notification.category != Notification.CATEGORY_CALL) return

            val extras = notification.extras ?: return
            val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()?.trim().orEmpty()
            val text = (extras.getCharSequence(Notification.EXTRA_TEXT)
                ?: extras.getCharSequence(Notification.EXTRA_BIG_TEXT))?.toString()?.trim().orEmpty()
            if (title.isEmpty() && text.isEmpty()) return

            DeviceNotificationStore.add(
                DeviceNotification(
                    key = sbn.key,
                    packageName = sbn.packageName,
                    appName = resolveAppName(sbn.packageName, extras),
                    title = title,
                    text = text.take(MAX_TEXT_LENGTH),
                    category = notification.category.orEmpty(),
                    postTime = sbn.postTime
                )
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error recording notification", e)
        }
    }

    private fun resolveAppName(pkg: String, extras: Bundle): String {
        return try {
            // The system attaches the posting app's info to every notification
            val appInfo = BundleCompat.getParcelable(extras, EXTRA_APP_INFO, ApplicationInfo::class.java)
                ?: packageManager.getApplicationInfo(pkg, 0)
            packageManager.getApplicationLabel(appInfo).toString()
        } catch (e: Exception) {
            pkg
        }
    }

    companion object {
        private const val TAG = "PulseNotifListener"
        private const val EXTRA_APP_INFO = "android.appInfo"
        private const val MAX_TEXT_LENGTH = 500
    }
}
