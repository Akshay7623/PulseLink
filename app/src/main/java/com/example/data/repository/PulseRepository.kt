package com.example.data.repository

import com.example.data.local.dao.PulseDao
import com.example.data.local.entity.NotificationEntity
import com.example.data.local.entity.PeerEntity
import com.example.model.DeviceTelemetry
import com.example.model.NotificationPayload
import com.example.util.JsonUtils
import kotlinx.coroutines.flow.Flow

class PulseRepository(private val dao: PulseDao) {

    val allNotifications: Flow<List<NotificationEntity>> = dao.getAllNotifications()
    val allPeers: Flow<List<PeerEntity>> = dao.getAllPeers()

    suspend fun saveNotification(payload: NotificationPayload, type: String) {
        val entity = NotificationEntity(
            id = payload.id,
            type = type,
            senderDeviceId = payload.senderDeviceId,
            senderDeviceName = payload.senderDeviceName,
            senderIp = payload.senderIp,
            title = payload.title,
            message = payload.message,
            category = payload.category,
            priority = payload.priority,
            timestamp = payload.timestamp,
            isRead = type == "SENT",
            telemetryJson = payload.telemetrySnapshot?.let { JsonUtils.telemetryToJson(it).toString() }
        )
        dao.insertNotification(entity)
    }

    suspend fun markAsRead(id: String) = dao.markAsRead(id)

    suspend fun markAllAsRead() = dao.markAllAsRead()

    suspend fun deleteNotification(id: String) = dao.deleteNotificationById(id)

    suspend fun clearAllNotifications() = dao.clearAllNotifications()

    suspend fun saveOrUpdatePeer(
        ip: String,
        name: String,
        model: String = "",
        port: Int = 8765,
        telemetry: DeviceTelemetry? = null
    ) {
        val existing = dao.getPeerByIp(ip)
        val entity = PeerEntity(
            ip = ip,
            deviceName = name.ifBlank { existing?.deviceName ?: "Peer Device" },
            deviceModel = model.ifBlank { existing?.deviceModel ?: "" },
            port = port,
            lastSeen = System.currentTimeMillis(),
            isFavorite = existing?.isFavorite ?: false,
            lastTelemetryJson = telemetry?.let { JsonUtils.telemetryToJson(it).toString() }
                ?: existing?.lastTelemetryJson
        )
        dao.insertOrUpdatePeer(entity)
    }

    suspend fun setPeerFavorite(ip: String, isFavorite: Boolean) {
        dao.setFavorite(ip, isFavorite)
    }

    suspend fun removePeer(ip: String) {
        dao.deletePeer(ip)
    }
}
