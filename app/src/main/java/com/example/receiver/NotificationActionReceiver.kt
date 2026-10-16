package com.example.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.RemoteInput
import com.example.PulseApplication
import com.example.model.CallResult
import com.example.network.NetworkResult
import com.example.notification.PulseNotificationManager
import com.example.util.JsonUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

class NotificationActionReceiver : BroadcastReceiver() {

    companion object {
        const val ACTION_ACKNOWLEDGE = "com.example.pulselink.ACTION_ACKNOWLEDGE"
        const val ACTION_STOP_ALARM = "com.example.pulselink.ACTION_STOP_ALARM"
        const val ACTION_DISMISS_ON_PEER = "com.example.pulselink.ACTION_DISMISS_ON_PEER"
        const val ACTION_REPLY_ON_PEER = "com.example.pulselink.ACTION_REPLY_ON_PEER"
        const val ACTION_ANSWER_ON_PEER = "com.example.pulselink.ACTION_ANSWER_ON_PEER"
        const val ACTION_END_CALL_ON_PEER = "com.example.pulselink.ACTION_END_CALL_ON_PEER"
        const val EXTRA_PEER_IP = "com.example.pulselink.PEER_IP"
        const val EXTRA_PEER_PORT = "com.example.pulselink.PEER_PORT"
        const val EXTRA_PEER_KEY = "com.example.pulselink.PEER_KEY"
        const val EXTRA_PEER_NAME = "com.example.pulselink.PEER_NAME"
        const val EXTRA_PEER_ITEM = "com.example.pulselink.PEER_ITEM"
        const val KEY_REPLY_TEXT = "com.example.pulselink.REPLY_TEXT"
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
            ACTION_DISMISS_ON_PEER -> {
                // The user swiped away a paired device's notification, so clear the original over there too
                val app = context.applicationContext as? PulseApplication ?: return
                val ip = intent.getStringExtra(EXTRA_PEER_IP) ?: return
                val key = intent.getStringExtra(EXTRA_PEER_KEY) ?: return
                val port = intent.getIntExtra(EXTRA_PEER_PORT, 8765)

                val pendingResult = goAsync()
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        app.pulseClient.dismissNotification(ip, port, key)
                    } finally {
                        pendingResult.finish()
                    }
                }
            }
            ACTION_ANSWER_ON_PEER, ACTION_END_CALL_ON_PEER -> {
                // The user picked up or ended a paired device's phone call from its copy here
                val app = context.applicationContext as? PulseApplication ?: return
                val ip = intent.getStringExtra(EXTRA_PEER_IP) ?: return
                val port = intent.getIntExtra(EXTRA_PEER_PORT, 8765)
                val name = intent.getStringExtra(EXTRA_PEER_NAME) ?: ip
                val isAnswer = intent.action == ACTION_ANSWER_ON_PEER
                val key = app.pairing.partnerKey()

                val pendingResult = goAsync()
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        val result = if (isAnswer) app.pulseClient.answerCall(ip, port, key) else app.pulseClient.endCall(ip, port, key)
                        // When it works the copy changes by itself, so only a failure needs saying
                        if (result !is NetworkResult.Success || result.data != CallResult.OK) {
                            val message = if (isAnswer) "Could not answer on $name" else "Could not end the call on $name"
                            withContext(Dispatchers.Main) { Toast.makeText(app, message, Toast.LENGTH_SHORT).show() }
                        }
                    } finally {
                        pendingResult.finish()
                    }
                }
            }
            ACTION_REPLY_ON_PEER -> {
                // The user typed an answer under a paired device's chat, so send it from over there
                val app = context.applicationContext as? PulseApplication ?: return
                val ip = intent.getStringExtra(EXTRA_PEER_IP) ?: return
                val port = intent.getIntExtra(EXTRA_PEER_PORT, 8765)
                val name = intent.getStringExtra(EXTRA_PEER_NAME) ?: ip
                val item = intent.getStringExtra(EXTRA_PEER_ITEM)
                    ?.let { JsonUtils.jsonToDeviceNotification(JSONObject(it)) } ?: return
                val text = RemoteInput.getResultsFromIntent(intent)
                    ?.getCharSequence(KEY_REPLY_TEXT)?.toString()?.trim().orEmpty()
                if (text.isEmpty()) return

                val pendingResult = goAsync()
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        val result = app.pulseClient.replyToNotification(ip, port, item.key, text)
                        val sent = result is NetworkResult.Success && result.data
                        app.notificationManager.showPeerDeviceReply(
                            ip, port, name, item, if (sent) text else "Not sent: $text"
                        )
                    } finally {
                        pendingResult.finish()
                    }
                }
            }
        }
    }
}
