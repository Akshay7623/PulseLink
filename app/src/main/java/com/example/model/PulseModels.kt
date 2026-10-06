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
