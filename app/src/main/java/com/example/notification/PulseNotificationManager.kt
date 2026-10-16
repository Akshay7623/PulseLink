package com.example.notification

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.Ringtone
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.RemoteInput
import com.example.MainActivity
import com.example.R
import com.example.model.CallState
import com.example.model.DeviceNotification
import com.example.model.NotificationPayload
import com.example.receiver.NotificationActionReceiver
import com.example.util.JsonUtils
import java.util.concurrent.ConcurrentHashMap

class PulseNotificationManager(private val context: Context) {

    companion object {
        const val CHANNEL_HIGH_ID = "channel_pulselink_urgent"
        const val CHANNEL_NORMAL_ID = "channel_pulselink_normal"
        const val CHANNEL_SERVICE_ID = "channel_pulselink_service"
        const val CHANNEL_PEER_DEVICE_ID = "channel_pulselink_peer_device"
        private const val PEER_DEVICE_TAG = "peer_device"
        private const val PEER_DEVICE_REQUEST_CODE = 7777
        private const val WAKE_SCREEN_MS = 5000L
        private var activeRingtone: Ringtone? = null
    }

    private val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    // What each paired device (by IP) last reported as still showing
    private val peerActiveKeys = ConcurrentHashMap<String, Set<String>>()

    init {
        createNotificationChannels()
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // High priority channel with vibration and sound
            val highChannel = NotificationChannel(
                CHANNEL_HIGH_ID,
                "PulseLink Urgent Alerts",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Urgent notifications from paired mobile devices"
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 400, 200, 400, 200, 600)
                setShowBadge(true)
            }

            // Normal priority channel
            val normalChannel = NotificationChannel(
                CHANNEL_NORMAL_ID,
                "PulseLink Messages",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Standard notifications and telemetry updates"
                enableVibration(true)
            }

            // Foreground Service channel
            val serviceChannel = NotificationChannel(
                CHANNEL_SERVICE_ID,
                "PulseLink Background Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Keeps connection open for instant peer notifications"
                setShowBadge(false)
            }

            // A paired device's own app notifications (WhatsApp, SMS, calls), shown as they arrive
            val peerDeviceChannel = NotificationChannel(
                CHANNEL_PEER_DEVICE_ID,
                "Peer Device Notifications",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "App notifications forwarded from a paired device"
                enableVibration(true)
            }

            notificationManager.createNotificationChannel(highChannel)
            notificationManager.createNotificationChannel(normalChannel)
            notificationManager.createNotificationChannel(serviceChannel)
            notificationManager.createNotificationChannel(peerDeviceChannel)
        }
    }

    /**
     * Shows a notification that arrived on a paired device as a regular notification on this one.
     * @param replyLine what to show under it once the user answered it from here
     */
    fun showPeerDeviceNotification(
        senderIp: String,
        senderPort: Int,
        senderName: String,
        item: DeviceNotification,
        replyLine: String? = null
    ) {
        val id = peerDeviceNotificationId(senderIp, item.key)

        val contentIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val contentPendingIntent = PendingIntent.getActivity(
            context,
            PEER_DEVICE_REQUEST_CODE,
            contentIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Swiping it away here also clears the original on the device it came from
        val dismissIntent = Intent(context, NotificationActionReceiver::class.java).apply {
            action = NotificationActionReceiver.ACTION_DISMISS_ON_PEER
            putExtra(NotificationActionReceiver.EXTRA_PEER_IP, senderIp)
            putExtra(NotificationActionReceiver.EXTRA_PEER_PORT, senderPort)
            putExtra(NotificationActionReceiver.EXTRA_PEER_KEY, item.key)
        }
        val dismissPendingIntent = PendingIntent.getBroadcast(
            context,
            id,
            dismissIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val appName = item.appName.ifBlank { item.packageName }
        val isCall = item.category == Notification.CATEGORY_CALL || item.category == Notification.CATEGORY_MISSED_CALL
        // A chat opens up to all of its unread messages, the way it does on the device it came from
        val expandedText = if (item.messages.size > 1) {
            item.messages.joinToString("\n") { it.displayText(item.title) }
        } else {
            item.text
        }

        val builder = NotificationCompat.Builder(context, CHANNEL_PEER_DEVICE_ID)
            .setSmallIcon(if (isCall) android.R.drawable.stat_sys_phone_call else android.R.drawable.stat_notify_chat)
            .setContentTitle(item.title.ifBlank { appName })
            .setContentText(item.text)
            .setSubText("$appName • $senderName")
            .setStyle(NotificationCompat.BigTextStyle().bigText(expandedText))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(contentPendingIntent)
            .setDeleteIntent(dismissPendingIntent)
            .setAutoCancel(true)
            // A call going from ringing to ongoing should not buzz twice, but every chat message should
            .setOnlyAlertOnce(!item.isChat || replyLine != null)
            // Remembered so the copy can be dropped once the original is gone from that device
            .addExtras(Bundle().apply {
                putString(NotificationActionReceiver.EXTRA_PEER_IP, senderIp)
                putString(NotificationActionReceiver.EXTRA_PEER_KEY, item.key)
            })

        if (item.canReply) {
            // Typing under the copy answers the chat on the device it came from
            val replyIntent = Intent(context, NotificationActionReceiver::class.java).apply {
                action = NotificationActionReceiver.ACTION_REPLY_ON_PEER
                putExtra(NotificationActionReceiver.EXTRA_PEER_IP, senderIp)
                putExtra(NotificationActionReceiver.EXTRA_PEER_PORT, senderPort)
                putExtra(NotificationActionReceiver.EXTRA_PEER_NAME, senderName)
                putExtra(NotificationActionReceiver.EXTRA_PEER_ITEM, JsonUtils.deviceNotificationToJson(item).toString())
            }
            // Mutable, so the system can add what the user typed
            val mutableFlag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0
            val replyPendingIntent = PendingIntent.getBroadcast(
                context,
                id,
                replyIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or mutableFlag
            )
            val remoteInput = RemoteInput.Builder(NotificationActionReceiver.KEY_REPLY_TEXT)
                .setLabel("Reply to ${item.title.ifBlank { appName }}")
                .build()
            builder.addAction(
                NotificationCompat.Action.Builder(android.R.drawable.ic_menu_send, "Reply", replyPendingIntent)
                    .addRemoteInput(remoteInput)
                    .build()
            )
        }
        replyLine?.let { builder.setRemoteInputHistory(arrayOf<CharSequence>(it)) }

        // A phone call on the paired device can be picked up or ended from its copy here
        fun callAction(action: String, label: String) = NotificationCompat.Action.Builder(
            android.R.drawable.stat_sys_phone_call,
            label,
            PendingIntent.getBroadcast(
                context,
                id,
                Intent(context, NotificationActionReceiver::class.java).apply {
                    this.action = action
                    putExtra(NotificationActionReceiver.EXTRA_PEER_IP, senderIp)
                    putExtra(NotificationActionReceiver.EXTRA_PEER_PORT, senderPort)
                    putExtra(NotificationActionReceiver.EXTRA_PEER_NAME, senderName)
                },
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        ).build()
        when (item.callState) {
            CallState.RINGING -> {
                builder.addAction(callAction(NotificationActionReceiver.ACTION_ANSWER_ON_PEER, "Answer"))
                builder.addAction(callAction(NotificationActionReceiver.ACTION_END_CALL_ON_PEER, "Decline"))
            }
            CallState.ONGOING -> {
                builder.addAction(callAction(NotificationActionReceiver.ACTION_END_CALL_ON_PEER, "Hang up"))
            }
        }

        // A silent update (a call going from ringing to ongoing, or the user's own answer) leaves the screen alone
        val alerts = replyLine == null && (item.isChat ||
            notificationManager.activeNotifications.none { it.tag == PEER_DEVICE_TAG && it.id == id })

        try {
            NotificationManagerCompat.from(context).notify(PEER_DEVICE_TAG, id, builder.build())
            if (alerts) wakeScreen()
        } catch (e: SecurityException) {
            // Permission not granted yet
        }
    }

    // Many phones (Samsung among them) keep the screen dark for a notification, so light it up the way chat apps do
    private fun wakeScreen() {
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return
        if (powerManager.isInteractive) return

        // Stay dark when the user turned on Do Not Disturb or silenced these notifications
        if (notificationManager.currentInterruptionFilter != NotificationManager.INTERRUPTION_FILTER_ALL) return
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            notificationManager.getNotificationChannel(CHANNEL_PEER_DEVICE_ID)?.importance == NotificationManager.IMPORTANCE_NONE
        ) return

        try {
            @Suppress("DEPRECATION")
            powerManager.newWakeLock(
                PowerManager.SCREEN_BRIGHT_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP,
                "PulseLink:peerNotification"
            ).acquire(WAKE_SCREEN_MS)
        } catch (e: Exception) {
            // ignore
        }
    }

    /**
     * Shows the outcome of answering a paired device's chat from its copy here, which also ends the
     * "sending" state of the copy's reply box.
     */
    fun showPeerDeviceReply(
        senderIp: String,
        senderPort: Int,
        senderName: String,
        item: DeviceNotification,
        replyLine: String
    ) {
        val id = peerDeviceNotificationId(senderIp, item.key)
        // Swiped away in the meantime: do not bring it back
        if (notificationManager.activeNotifications.none { it.tag == PEER_DEVICE_TAG && it.id == id }) return

        showPeerDeviceNotification(senderIp, senderPort, senderName, item, replyLine)
        // The peer may have cleared the original while the answer was still on its way back
        if (peerActiveKeys[senderIp]?.contains(item.key) == false) cancelPeerDeviceNotification(senderIp, item.key)
    }

    fun cancelPeerDeviceNotification(senderIp: String, key: String) {
        NotificationManagerCompat.from(context).cancel(PEER_DEVICE_TAG, peerDeviceNotificationId(senderIp, key))
    }

    /**
     * Drops the copies of a paired device's notifications that are no longer showing on that device.
     */
    fun retainPeerDeviceNotifications(senderIp: String, activeKeys: Set<String>) {
        peerActiveKeys[senderIp] = activeKeys
        notificationManager.activeNotifications
            .filter { it.tag == PEER_DEVICE_TAG }
            .filter { it.notification.extras.getString(NotificationActionReceiver.EXTRA_PEER_IP) == senderIp }
            .filter { it.notification.extras.getString(NotificationActionReceiver.EXTRA_PEER_KEY) !in activeKeys }
            .forEach { notificationManager.cancel(PEER_DEVICE_TAG, it.id) }
    }

    // The same notification updating on the peer replaces its copy here instead of stacking
    private fun peerDeviceNotificationId(senderIp: String, key: String): Int = "$senderIp|$key".hashCode()

    fun showReceivedNotification(payload: NotificationPayload) {
        val channelId = if (payload.priority == "HIGH") CHANNEL_HIGH_ID else CHANNEL_NORMAL_ID

        // Intent to open MainActivity on click
        val contentIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("OPEN_TAB", "INBOX")
            putExtra("NOTIFICATION_ID", payload.id)
        }
        val contentPendingIntent = PendingIntent.getActivity(
            context,
            payload.id.hashCode(),
            contentIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Action: Acknowledge / Reply
        val ackIntent = Intent(context, NotificationActionReceiver::class.java).apply {
            action = NotificationActionReceiver.ACTION_ACKNOWLEDGE
            putExtra("NOTIFICATION_ID", payload.id)
            putExtra("SENDER_IP", payload.senderIp)
        }
        val ackPendingIntent = PendingIntent.getBroadcast(
            context,
            payload.id.hashCode() + 1,
            ackIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.stat_notify_chat)
            .setContentTitle("⚡ [${payload.senderDeviceName}] ${payload.title}")
            .setContentText(payload.message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(
                buildString {
                    append(payload.message)
                    payload.telemetrySnapshot?.let { tel ->
                        append("\n\n📱 Peer Status: 🔋 ${tel.batteryLevel}% (${if (tel.isCharging) "⚡ Charging" else "Discharging"}) | 📶 ${tel.networkType}")
                    }
                }
            ))
            .setPriority(if (payload.priority == "HIGH") NotificationCompat.PRIORITY_HIGH else NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(contentPendingIntent)
            .setAutoCancel(true)
            .addAction(android.R.drawable.ic_menu_send, "Acknowledge", ackPendingIntent)

        if (payload.vibrate) {
            triggerVibration()
        }

        try {
            NotificationManagerCompat.from(context).notify(payload.id.hashCode(), builder.build())
        } catch (e: SecurityException) {
            // Permission not granted yet
        }
    }

    fun triggerAlarm(senderName: String) {
        stopAlarm()
        try {
            val alertSoundUri: Uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
            val ringtone = RingtoneManager.getRingtone(context, alertSoundUri)
            ringtone?.audioAttributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ALARM)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
            ringtone?.play()
            activeRingtone = ringtone

            triggerVibration(longArrayOf(0, 500, 300, 500, 300, 1000))

            // Show persistent notification to dismiss alarm
            val stopIntent = Intent(context, NotificationActionReceiver::class.java).apply {
                action = NotificationActionReceiver.ACTION_STOP_ALARM
            }
            val stopPendingIntent = PendingIntent.getBroadcast(
                context,
                9999,
                stopIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val builder = NotificationCompat.Builder(context, CHANNEL_HIGH_ID)
                .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
                .setContentTitle("🚨 Remote Ring Alert from $senderName")
                .setContentText("Your device is ringing remotely. Tap to silence.")
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setOngoing(true)
                .addAction(android.R.drawable.ic_delete, "Silence Alarm", stopPendingIntent)

            NotificationManagerCompat.from(context).notify(9999, builder.build())
        } catch (e: Exception) {
            // ignore
        }
    }

    fun stopAlarm() {
        try {
            activeRingtone?.stop()
            activeRingtone = null
            NotificationManagerCompat.from(context).cancel(9999)
        } catch (e: Exception) {
            // ignore
        }
    }

    fun triggerVibration(pattern: LongArray = longArrayOf(0, 250, 150, 250)) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                vibratorManager?.defaultVibrator?.vibrate(
                    VibrationEffect.createWaveform(pattern, -1)
                )
            } else {
                @Suppress("DEPRECATION")
                val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator?.vibrate(VibrationEffect.createWaveform(pattern, -1))
                } else {
                    @Suppress("DEPRECATION")
                    vibrator?.vibrate(pattern, -1)
                }
            }
        } catch (e: Exception) {
            // ignore
        }
    }
}
