package com.example.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "notifications")
data class NotificationEntity(
    @PrimaryKey val id: String,
    val type: String, // "SENT" or "RECEIVED"
    val senderDeviceId: String,
    val senderDeviceName: String,
    val senderIp: String,
    val title: String,
    val message: String,
    val category: String,
    val priority: String,
    val timestamp: Long,
    val isRead: Boolean = false,
    val telemetryJson: String? = null // Serialized snapshot if present
)

@Entity(tableName = "peer_devices")
data class PeerEntity(
    @PrimaryKey val ip: String,
    val deviceName: String,
    val deviceModel: String = "",
    val port: Int = 8765,
    val lastSeen: Long = System.currentTimeMillis(),
    val isFavorite: Boolean = false,
    val lastTelemetryJson: String? = null
)
