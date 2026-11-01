package com.example.model

/**
 * Real-time telemetry snapshot of a mobile device
 */
data class DeviceTelemetry(
    val deviceName: String = "",
    val deviceModel: String = "",
    val manufacturer: String = "",
    val androidVersion: String = "",
    val batteryLevel: Int = 0,
    val isCharging: Boolean = false,
    val chargePlugType: String = "Unplugged", // "AC", "USB", "Wireless", "Unplugged"
    val batteryHealth: String = "Good",
    val batteryTempCelsius: Float = 0f,
    val batteryVoltageMv: Int = 0,
    val networkType: String = "Unknown", // "5G (NR)", "4G (LTE)", "3G (HSPA)", "Wi-Fi", "Ethernet", "Disconnected"
    val networkDetails: String = "",
    val carrierName: String = "",
    val signalStrengthPercent: Int = 0,
    val ipAddress: String = "",
    val totalRamMb: Long = 0,
    val availableRamMb: Long = 0,
    val totalStorageGb: Double = 0.0,
    val freeStorageGb: Double = 0.0,
    val uptimeMinutes: Long = 0,
    val isScreenOn: Boolean = true,
    // The Bluetooth headset a phone call would use: its name, empty when none is connected,
    // null when the device runs an older PulseLink that does not say
    val bluetoothHeadset: String? = null,
    // The device's own Bluetooth name while it takes the call sound connection over Bluetooth,
    // empty while it does not, null when it runs an older PulseLink that cannot
    val bluetoothSoundHost: String? = null,
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * Notification payload transferred between mobile sender and receiver
 */
data class NotificationPayload(
    val id: String = java.util.UUID.randomUUID().toString(),
    val senderDeviceId: String = "",
    val senderDeviceName: String = "",
    val senderIp: String = "",
    val title: String = "",
    val message: String = "",
    val category: String = "ALERT", // "ALERT", "MESSAGE", "BATTERY_WARNING", "FIND_PHONE", "CUSTOM"
    val priority: String = "HIGH",  // "HIGH", "NORMAL", "LOW"
    val vibrate: Boolean = true,
    val playSound: Boolean = true,
    val telemetrySnapshot: DeviceTelemetry? = null,
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * Remote action request sent to peer device
 */
data class RemoteActionRequest(
    val actionType: String = "", // "PING", "RING_ALARM", "STOP_ALARM", "COPY_CLIPBOARD", "VIBRATE_TEST", "REQUEST_TELEMETRY"
    val senderName: String = "",
    val payload: String = "",
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * Response for remote action
 */
data class RemoteActionResponse(
    val success: Boolean = true,
    val message: String = "",
    val telemetry: DeviceTelemetry? = null,
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * A system notification (WhatsApp, SMS, call, etc.) captured on a device
 */
data class DeviceNotification(
    val key: String = "",
    val packageName: String = "",
    val appName: String = "",
    val title: String = "",
    val text: String = "",
    val category: String = "", // android.app.Notification category, e.g. "msg", "call", "missed_call"
    val postTime: Long = System.currentTimeMillis(),
    val isChat: Boolean = false, // chats reuse one notification per conversation, so each update is a new message
    val canReply: Boolean = false, // the posting app put a reply box on it (WhatsApp, SMS)
    val messages: List<ChatMessage> = emptyList(), // the conversation's unread messages, oldest first
    // CallState.RINGING or ONGOING for a phone call a peer may answer or end, empty for anything else
    val callState: String = ""
)

/**
 * Where a phone call stands on the device it is on
 */
object CallState {
    const val RINGING = "ringing"
    const val ONGOING = "ongoing"
}

/**
 * Outcome of asking a device to place, answer or end a phone call
 */
enum class CallResult(val reason: String) {
    OK(""),
    DISABLED("disabled"), // "Allow Remote Calls" is off on that device
    NO_PERMISSION("permission"), // that device has not granted PulseLink the Phone permission
    INVALID_NUMBER("invalid"),
    NO_CALL("no_call"), // nothing was ringing or in progress
    NOT_PAIRED("pairing"), // the request was not signed with that device's pairing code
    RELAY_OFF("relay_off"), // "Relay Call Sound" is off on that device
    RELAY_NOT_READY("relay_not_ready"), // that device lacks the microphone permission or accessibility access
    UNSUPPORTED("unsupported"), // that device runs an older PulseLink or an Android without this
    FAILED("failed");

    companion object {
        fun fromReason(reason: String): CallResult = entries.firstOrNull { it.reason == reason } ?: FAILED
    }
}

/**
 * Outcome of asking a device to let this one watch its videos
 */
enum class VideoAccess(val reason: String) {
    OK(""),
    DISABLED("disabled"), // "Share My Videos" is off on that device
    NO_PERMISSION("permission"), // that device has not granted PulseLink access to its videos
    NOT_PAIRED("pairing"), // the request was not signed with that device's pairing code
    UNSUPPORTED("unsupported"), // that device runs an older PulseLink without this
    FAILED("failed"); // that device could not be reached or did not answer properly

    companion object {
        fun fromReason(reason: String): VideoAccess = entries.firstOrNull { it.reason == reason } ?: FAILED
    }
}

/**
 * The answer to asking for access to a device's videos, with the pass to use when it was granted
 */
data class VideoSession(val access: VideoAccess, val token: String = "")

/**
 * A video stored on a device, as listed for a paired device to pick from
 */
data class SharedVideo(
    val id: Long = 0,
    val name: String = "",
    val folder: String = "",
    val durationMs: Long = 0,
    val sizeBytes: Long = 0,
    val width: Int = 0,
    val height: Int = 0,
    val dateAddedSeconds: Long = 0
) {
    val durationText: String
        get() {
            val totalSeconds = durationMs / 1000
            val hours = totalSeconds / 3600
            val minutes = totalSeconds / 60 % 60
            val seconds = totalSeconds % 60
            return if (hours > 0) "%d:%02d:%02d".format(hours, minutes, seconds) else "%d:%02d".format(minutes, seconds)
        }

    val sizeText: String
        get() = when {
            sizeBytes >= 1_000_000_000 -> "%.1f GB".format(sizeBytes / 1_000_000_000.0)
            sizeBytes >= 1_000_000 -> "%d MB".format(sizeBytes / 1_000_000)
            else -> "%d KB".format(sizeBytes / 1_000)
        }

    // Named after the shorter side, so a video filmed upright reads the same as a wide one
    val qualityText: String
        get() {
            val shortSide = minOf(width, height)
            return when {
                shortSide >= 2160 -> "4K"
                shortSide > 0 -> "${shortSide}p"
                else -> ""
            }
        }
}

/**
 * What this device knows about the videos on the device it is connected to
 */
data class PeerVideoLibrary(
    val access: VideoAccess? = null, // null until the first answer
    val isLoading: Boolean = false,
    val token: String = "", // sent along when fetching thumbnails
    val videos: List<SharedVideo> = emptyList()
)

/**
 * One message of a chat notification
 */
data class ChatMessage(
    val sender: String = "",
    val text: String = "",
    val time: Long = 0,
    val fromMe: Boolean = false // written by the user of the device the chat is on
) {
    // Group chats name who wrote each message; a one-to-one chat already has that name as its title
    fun displayText(chatTitle: String): String = when {
        fromMe -> "You: $text"
        sender.isBlank() || sender == chatTitle -> text
        else -> "$sender: $text"
    }
}

/**
 * Device notifications shared by a peer, along with the peer's sharing status
 */
data class PeerNotificationFeed(
    val isSupported: Boolean = true, // false when the peer runs an older PulseLink without this feature
    val sharingEnabled: Boolean = false,
    val accessGranted: Boolean = false,
    val notifications: List<DeviceNotification> = emptyList()
)

/**
 * Refresh interval choices for the real-time telemetry stream
 */
object TelemetryStreamInterval {
    const val DEFAULT_SECONDS = 2
    const val MIN_SECONDS = 1
    const val MAX_SECONDS = 3600
    val PRESET_SECONDS = listOf(2, 5, 10, 20, 30, 60)
}

/**
 * Representation of a discovered or saved peer device
 */
data class PeerDevice(
    val id: String,
    val name: String,
    val ip: String,
    val port: Int = 8765,
    val model: String = "",
    val lastSeen: Long = System.currentTimeMillis(),
    val isOnline: Boolean = false,
    val isFavorite: Boolean = false,
    val lastTelemetry: DeviceTelemetry? = null
)
