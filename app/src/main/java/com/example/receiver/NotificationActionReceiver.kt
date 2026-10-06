package com.example.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationManagerCompat
import com.example.notification.PulseNotificationManager

class NotificationActionReceiver : BroadcastReceiver() {

    companion object {
        const val ACTION_ACKNOWLEDGE = "com.example.pulselink.ACTION_ACKNOWLEDGE"
        const val ACTION_STOP_ALARM = "com.example.pulselink.ACTION_STOP_ALARM"
    }

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACTION_STOP_ALARM -> {
                val notificationManager = PulseNotificationManager(context)
                notificationManager.stopAlarm()
            }
            ACTION_ACKNOWLEDGE -> {
                val notificationId = intent.getStringExtra("NOTIFICATION_ID")
                notificationId?.hashCode()?.let {
                    NotificationManagerCompat.from(context).cancel(it)
                }
            }
        }
    }
}
