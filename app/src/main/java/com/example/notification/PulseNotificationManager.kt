package com.example.notification

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
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.example.MainActivity
import com.example.R
import com.example.model.NotificationPayload
import com.example.receiver.NotificationActionReceiver

class PulseNotificationManager(private val context: Context) {

    companion object {
        const val CHANNEL_HIGH_ID = "channel_pulselink_urgent"
        const val CHANNEL_NORMAL_ID = "channel_pulselink_normal"
        const val CHANNEL_SERVICE_ID = "channel_pulselink_service"
        private var activeRingtone: Ringtone? = null
    }

    private val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

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

            notificationManager.createNotificationChannel(highChannel)
            notificationManager.createNotificationChannel(normalChannel)
            notificationManager.createNotificationChannel(serviceChannel)
        }
    }

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
