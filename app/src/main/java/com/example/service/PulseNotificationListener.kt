package com.example.service

import android.app.Notification
import android.app.NotificationManager
import android.content.pm.ApplicationInfo
import android.os.Build
import android.os.Bundle
import android.os.Parcelable
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import androidx.core.os.BundleCompat
import com.example.PulseApplication
import com.example.model.ChatMessage
import com.example.model.DeviceNotification
import com.example.notification.DeviceNotificationStore
import com.example.notification.NotificationReply

/**
 * Captures this device's system notifications (WhatsApp, SMS, calls, etc.) so a paired
 * device can view them and answer its chats. Only active once the user grants Notification
 * access in Settings.
 */
class PulseNotificationListener : NotificationListenerService() {

    private val forwarder get() = (application as? PulseApplication)?.notificationForwarder

    override fun onListenerConnected() {
        super.onListenerConnected()
        instance = this
        try {
            // Start again from what is on screen now. None of it is news, so the peer gets no pop-ups,
            // only the list of what is still showing
            DeviceNotificationStore.clear()
            activeNotifications?.forEach { record(it, forward = false) }
            forwarder?.forwardChange()
        } catch (e: Exception) {
            Log.e(TAG, "Error reading active notifications", e)
        }
    }

    override fun onListenerDisconnected() {
        instance = null
        super.onListenerDisconnected()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        sbn?.let { record(it, forward = true) }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        // The peer shows only what is still on this device
        if (sbn != null && DeviceNotificationStore.remove(sbn.key)) forwarder?.forwardChange()
    }

    private fun record(sbn: StatusBarNotification, forward: Boolean) {
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

            val item = DeviceNotification(
                key = sbn.key,
                packageName = sbn.packageName,
                appName = resolveAppName(sbn.packageName, extras),
                title = title,
                text = text.take(MAX_TEXT_LENGTH),
                category = notification.category.orEmpty(),
                postTime = sbn.postTime,
                isChat = notification.category == Notification.CATEGORY_MESSAGE ||
                    extras.containsKey(Notification.EXTRA_MESSAGES),
                canReply = NotificationReply.findAction(notification) != null,
                messages = chatMessages(extras),
                // Set only for a phone call the peer is allowed to answer or end from its side
                callState = if (notification.category == Notification.CATEGORY_CALL) {
                    (application as? PulseApplication)?.callController?.callState().orEmpty()
                } else {
                    ""
                }
            )

            val isNews = DeviceNotificationStore.add(item)
            // The user's own answer joining the conversation is listed for the peer but is nothing to buzz about
            val alerts = isAlerting(sbn) && item.messages.lastOrNull()?.fromMe != true
            if (forward && isNews) forwarder?.forwardChange(alertItem = item.takeIf { alerts })
        } catch (e: Exception) {
            Log.e(TAG, "Error recording notification", e)
        }
    }

    // Silent notifications (weather, backups) are listed for the peer but should not make its phone buzz
    private fun isAlerting(sbn: StatusBarNotification): Boolean {
        val ranking = Ranking()
        val isRanked = currentRanking?.getRanking(sbn.key, ranking) == true
        return !isRanked || ranking.importance >= NotificationManager.IMPORTANCE_DEFAULT
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
        private const val MAX_MESSAGES = 10

        // Set while the system keeps this listener connected
        @Volatile
        private var instance: PulseNotificationListener? = null

        // Chat and SMS apps attach the conversation's unread messages, so the peer can read all of them
        // and not just the last
        internal fun chatMessages(extras: Bundle): List<ChatMessage> {
            return try {
                val bundles = BundleCompat.getParcelableArray(extras, Notification.EXTRA_MESSAGES, Parcelable::class.java)
                    ?: return emptyList()
                Notification.MessagingStyle.Message.getMessagesFromBundleArray(bundles)
                    .takeLast(MAX_MESSAGES)
                    .mapNotNull { message ->
                        val text = message.text?.toString()?.trim().orEmpty()
                        val person = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) message.senderPerson else null
                        @Suppress("DEPRECATION")
                        val sender = person?.name ?: message.sender
                        if (text.isEmpty()) null else ChatMessage(
                            sender = sender?.toString()?.trim().orEmpty(),
                            text = text.take(MAX_TEXT_LENGTH),
                            time = message.timestamp,
                            // A message without a sender is one this phone's user wrote
                            fromMe = person == null && sender == null
                        )
                    }
            } catch (e: Exception) {
                Log.e(TAG, "Error reading chat messages", e)
                emptyList()
            }
        }

        /**
         * Answers a chat notification on this device through its app's own reply box.
         * @return false when the notification is gone, has no reply box, or notification access is not active
         */
        fun reply(key: String, text: String): Boolean {
            val listener = instance ?: return false
            return try {
                val notification = listener.getActiveNotifications(arrayOf(key))?.firstOrNull()?.notification
                    ?: return false
                NotificationReply.send(listener, notification, text)
            } catch (e: Exception) {
                Log.e(TAG, "Error replying to notification", e)
                false
            }
        }

        /**
         * Clears a notification from this device, as if the user swiped it away here.
         * @return false when notification access is not active
         */
        fun dismiss(key: String): Boolean {
            val listener = instance ?: return false
            return try {
                listener.cancelNotification(key)
                true
            } catch (e: Exception) {
                Log.e(TAG, "Error dismissing notification", e)
                false
            }
        }
    }
}
