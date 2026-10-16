package com.example.notification

import android.content.Context
import com.example.model.DeviceNotification
import com.example.network.NetworkResult
import com.example.network.PulseClient
import com.example.telemetry.TelemetryCollector
import com.example.util.JsonUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import org.json.JSONObject

/**
 * Pushes this device's notifications to the peers that subscribed to them, so they can show each one
 * as it arrives and drop it when it goes away. Nothing is sent unless the user turned on
 * "Share My Notifications".
 */
class NotificationForwarder(
    private val context: Context,
    private val pulseClient: PulseClient,
    private val telemetryCollector: TelemetryCollector,
    // Where a peer can reach this device back, e.g. to dismiss a notification
    private val serverPort: () -> Int
) {
    private val scope = CoroutineScope(Dispatchers.IO + Job())
    private val prefs = context.getSharedPreferences("pulselink_prefs", Context.MODE_PRIVATE)

    // When each subscriber first stopped answering, cleared again by its next successful delivery
    private val unreachableSince = mutableMapOf<String, Long>()

    // A single queue keeps events in order: "ringing" must reach the peer before "ongoing"
    private val queue = Channel<JSONObject>(QUEUE_SIZE, BufferOverflow.DROP_OLDEST)

    init {
        scope.launch {
            for (event in queue) {
                subscribers().forEach { deliver(it, event) }
            }
        }
    }

    @Synchronized
    fun subscribe(ip: String, port: Int) {
        val target = "$ip:$port"
        unreachableSince.remove(target)
        // Newest last, so a full list drops whoever subscribed longest ago
        saveSubscribers((subscribers() - target + target).takeLast(MAX_SUBSCRIBERS))
    }

    @Synchronized
    fun unsubscribe(ip: String, port: Int) {
        saveSubscribers(subscribers() - "$ip:$port")
    }

    /**
     * Tells the subscribed peers that this device's notifications changed.
     * @param alertItem the notification the peers should pop up, or null when one only went away
     * or changed without making a sound here
     */
    fun forwardChange(alertItem: DeviceNotification? = null) {
        if (!DeviceNotificationStore.isSharingEnabled(context) || subscribers().isEmpty()) return
        queue.trySend(
            JsonUtils.notificationPushToJson(deviceName(), serverPort(), alertItem, DeviceNotificationStore.activeKeys())
        )
    }

    private suspend fun deliver(target: String, event: JSONObject) {
        val ip = target.substringBeforeLast(":")
        val port = target.substringAfterLast(":").toIntOrNull() ?: return
        val result = pulseClient.pushNotificationEvent(ip, port, event)

        synchronized(this) {
            when (result) {
                is NetworkResult.Success -> {
                    unreachableSince.remove(target)
                    // The peer answers false once its user turned alerts off
                    if (!result.data) saveSubscribers(subscribers() - target)
                }
                is NetworkResult.Error -> {
                    // A peer that is briefly out of reach keeps its place; one that never comes back is dropped
                    val since = unreachableSince.getOrPut(target) { System.currentTimeMillis() }
                    if (System.currentTimeMillis() - since > GIVE_UP_AFTER_MS) {
                        unreachableSince.remove(target)
                        saveSubscribers(subscribers() - target)
                    }
                }
            }
        }
    }

    private fun deviceName(): String {
        return prefs.getString("custom_device_name", null) ?: telemetryCollector.getDeviceDisplayName()
    }

    private fun subscribers(): List<String> {
        return prefs.getString(KEY_SUBSCRIBERS, "").orEmpty().split(",").filter { it.isNotBlank() }
    }

    private fun saveSubscribers(targets: List<String>) {
        prefs.edit().putString(KEY_SUBSCRIBERS, targets.joinToString(",")).apply()
    }

    companion object {
        private const val KEY_SUBSCRIBERS = "notification_subscribers"
        private const val MAX_SUBSCRIBERS = 5
        private const val QUEUE_SIZE = 64
        private const val GIVE_UP_AFTER_MS = 24 * 60 * 60 * 1000L
    }
}
