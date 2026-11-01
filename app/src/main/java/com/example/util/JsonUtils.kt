package com.example.util

import com.example.model.ChatMessage
import com.example.model.DeviceNotification
import com.example.model.DeviceTelemetry
import com.example.model.NotificationPayload
import com.example.model.PeerNotificationFeed
import com.example.model.RemoteActionRequest
import com.example.model.RemoteActionResponse
import com.example.model.SharedVideo
import org.json.JSONArray
import org.json.JSONObject

object JsonUtils {

    fun telemetryToJson(telemetry: DeviceTelemetry): JSONObject {
        val json = JSONObject()
        json.put("deviceName", telemetry.deviceName)
        json.put("deviceModel", telemetry.deviceModel)
        json.put("manufacturer", telemetry.manufacturer)
        json.put("androidVersion", telemetry.androidVersion)
        json.put("batteryLevel", telemetry.batteryLevel)
        json.put("isCharging", telemetry.isCharging)
        json.put("chargePlugType", telemetry.chargePlugType)
        json.put("batteryHealth", telemetry.batteryHealth)
        json.put("batteryTempCelsius", telemetry.batteryTempCelsius.toDouble())
        json.put("batteryVoltageMv", telemetry.batteryVoltageMv)
        json.put("networkType", telemetry.networkType)
        json.put("networkDetails", telemetry.networkDetails)
        json.put("carrierName", telemetry.carrierName)
        json.put("signalStrengthPercent", telemetry.signalStrengthPercent)
        json.put("ipAddress", telemetry.ipAddress)
        json.put("totalRamMb", telemetry.totalRamMb)
        json.put("availableRamMb", telemetry.availableRamMb)
        json.put("totalStorageGb", telemetry.totalStorageGb)
        json.put("freeStorageGb", telemetry.freeStorageGb)
        json.put("uptimeMinutes", telemetry.uptimeMinutes)
        json.put("isScreenOn", telemetry.isScreenOn)
        telemetry.bluetoothHeadset?.let { json.put("bluetoothHeadset", it) }
        telemetry.bluetoothSoundHost?.let { json.put("bluetoothSoundHost", it) }
        json.put("timestamp", telemetry.timestamp)
        return json
    }

    fun jsonToTelemetry(json: JSONObject): DeviceTelemetry {
        return DeviceTelemetry(
            deviceName = json.optString("deviceName", "Unknown Device"),
            deviceModel = json.optString("deviceModel", ""),
            manufacturer = json.optString("manufacturer", ""),
            androidVersion = json.optString("androidVersion", ""),
            batteryLevel = json.optInt("batteryLevel", 0),
            isCharging = json.optBoolean("isCharging", false),
            chargePlugType = json.optString("chargePlugType", "Unplugged"),
            batteryHealth = json.optString("batteryHealth", "Good"),
            batteryTempCelsius = json.optDouble("batteryTempCelsius", 0.0).toFloat(),
            batteryVoltageMv = json.optInt("batteryVoltageMv", 0),
            networkType = json.optString("networkType", "Unknown"),
            networkDetails = json.optString("networkDetails", ""),
            carrierName = json.optString("carrierName", ""),
            signalStrengthPercent = json.optInt("signalStrengthPercent", 0),
            ipAddress = json.optString("ipAddress", ""),
            totalRamMb = json.optLong("totalRamMb", 0L),
            availableRamMb = json.optLong("availableRamMb", 0L),
            totalStorageGb = json.optDouble("totalStorageGb", 0.0),
            freeStorageGb = json.optDouble("freeStorageGb", 0.0),
            uptimeMinutes = json.optLong("uptimeMinutes", 0L),
            isScreenOn = json.optBoolean("isScreenOn", true),
            bluetoothHeadset = if (json.has("bluetoothHeadset")) json.optString("bluetoothHeadset", "") else null,
            bluetoothSoundHost = if (json.has("bluetoothSoundHost")) json.optString("bluetoothSoundHost", "") else null,
            timestamp = json.optLong("timestamp", System.currentTimeMillis())
        )
    }

    fun notificationToJson(payload: NotificationPayload): JSONObject {
        val json = JSONObject()
        json.put("id", payload.id)
        json.put("senderDeviceId", payload.senderDeviceId)
        json.put("senderDeviceName", payload.senderDeviceName)
        json.put("senderIp", payload.senderIp)
        json.put("title", payload.title)
        json.put("message", payload.message)
        json.put("category", payload.category)
        json.put("priority", payload.priority)
        json.put("vibrate", payload.vibrate)
        json.put("playSound", payload.playSound)
        json.put("timestamp", payload.timestamp)
        payload.telemetrySnapshot?.let {
            json.put("telemetrySnapshot", telemetryToJson(it))
        }
        return json
    }

    fun jsonToNotification(json: JSONObject): NotificationPayload {
        val telemetryObj = json.optJSONObject("telemetrySnapshot")
        val telemetry = if (telemetryObj != null) jsonToTelemetry(telemetryObj) else null

        return NotificationPayload(
            id = json.optString("id", java.util.UUID.randomUUID().toString()),
            senderDeviceId = json.optString("senderDeviceId", ""),
            senderDeviceName = json.optString("senderDeviceName", "Remote Device"),
            senderIp = json.optString("senderIp", ""),
            title = json.optString("title", "Pulse Notification"),
            message = json.optString("message", ""),
            category = json.optString("category", "ALERT"),
            priority = json.optString("priority", "HIGH"),
            vibrate = json.optBoolean("vibrate", true),
            playSound = json.optBoolean("playSound", true),
            telemetrySnapshot = telemetry,
            timestamp = json.optLong("timestamp", System.currentTimeMillis())
        )
    }

    fun actionRequestToJson(request: RemoteActionRequest): JSONObject {
        val json = JSONObject()
        json.put("actionType", request.actionType)
        json.put("senderName", request.senderName)
        json.put("payload", request.payload)
        json.put("timestamp", request.timestamp)
        return json
    }

    fun jsonToActionRequest(json: JSONObject): RemoteActionRequest {
        return RemoteActionRequest(
            actionType = json.optString("actionType", "PING"),
            senderName = json.optString("senderName", "Remote Device"),
            payload = json.optString("payload", ""),
            timestamp = json.optLong("timestamp", System.currentTimeMillis())
        )
    }

    fun actionResponseToJson(response: RemoteActionResponse): JSONObject {
        val json = JSONObject()
        json.put("success", response.success)
        json.put("message", response.message)
        json.put("timestamp", response.timestamp)
        response.telemetry?.let {
            json.put("telemetry", telemetryToJson(it))
        }
        return json
    }

    fun jsonToActionResponse(json: JSONObject): RemoteActionResponse {
        val telemetryObj = json.optJSONObject("telemetry")
        val telemetry = if (telemetryObj != null) jsonToTelemetry(telemetryObj) else null
        return RemoteActionResponse(
            success = json.optBoolean("success", true),
            message = json.optString("message", ""),
            telemetry = telemetry,
            timestamp = json.optLong("timestamp", System.currentTimeMillis())
        )
    }

    fun deviceNotificationToJson(item: DeviceNotification): JSONObject {
        val json = JSONObject()
        json.put("key", item.key)
        json.put("packageName", item.packageName)
        json.put("appName", item.appName)
        json.put("title", item.title)
        json.put("text", item.text)
        json.put("category", item.category)
        json.put("postTime", item.postTime)
        json.put("isChat", item.isChat)
        json.put("canReply", item.canReply)
        val messages = JSONArray()
        item.messages.forEach { message ->
            messages.put(
                JSONObject()
                    .put("sender", message.sender)
                    .put("text", message.text)
                    .put("time", message.time)
                    .put("fromMe", message.fromMe)
            )
        }
        json.put("messages", messages)
        json.put("callState", item.callState)
        return json
    }

    fun jsonToDeviceNotification(json: JSONObject): DeviceNotification {
        val messagesJson = json.optJSONArray("messages") ?: JSONArray()
        val messages = (0 until messagesJson.length()).mapNotNull { index ->
            messagesJson.optJSONObject(index)?.let { message ->
                ChatMessage(
                    sender = message.optString("sender", ""),
                    text = message.optString("text", ""),
                    time = message.optLong("time", 0L),
                    fromMe = message.optBoolean("fromMe", false)
                )
            }
        }

        return DeviceNotification(
            key = json.optString("key", ""),
            packageName = json.optString("packageName", ""),
            appName = json.optString("appName", ""),
            title = json.optString("title", ""),
            text = json.optString("text", ""),
            category = json.optString("category", ""),
            postTime = json.optLong("postTime", System.currentTimeMillis()),
            isChat = json.optBoolean("isChat", false),
            canReply = json.optBoolean("canReply", false),
            messages = messages,
            callState = json.optString("callState", "")
        )
    }

    /**
     * Pushed to a subscribed peer whenever this device's notifications change.
     * @param item the notification to alert about, or null when one only went away or changed silently
     * @param activeKeys everything still showing, so the peer can drop whatever is no longer listed
     */
    fun notificationPushToJson(
        senderDeviceName: String,
        senderPort: Int,
        item: DeviceNotification?,
        activeKeys: List<String>
    ): JSONObject {
        val json = JSONObject()
        json.put("senderDeviceName", senderDeviceName)
        json.put("senderPort", senderPort)
        item?.let { json.put("notification", deviceNotificationToJson(it)) }
        json.put("activeKeys", JSONArray(activeKeys))
        return json
    }

    fun notificationFeedToJson(feed: PeerNotificationFeed): JSONObject {
        val items = JSONArray()
        feed.notifications.forEach { item ->
            items.put(deviceNotificationToJson(item))
        }

        val json = JSONObject()
        json.put("sharingEnabled", feed.sharingEnabled)
        json.put("accessGranted", feed.accessGranted)
        json.put("notifications", items)
        return json
    }

    fun jsonToNotificationFeed(json: JSONObject): PeerNotificationFeed {
        val items = json.optJSONArray("notifications") ?: JSONArray()
        val notifications = (0 until items.length()).mapNotNull { index ->
            items.optJSONObject(index)?.let { jsonToDeviceNotification(it) }
        }

        return PeerNotificationFeed(
            sharingEnabled = json.optBoolean("sharingEnabled", false),
            accessGranted = json.optBoolean("accessGranted", false),
            notifications = notifications
        )
    }

    fun videosToJson(videos: List<SharedVideo>): JSONObject {
        val items = JSONArray()
        videos.forEach { video ->
            items.put(
                JSONObject()
                    .put("id", video.id)
                    .put("name", video.name)
                    .put("folder", video.folder)
                    .put("folderId", video.folderId)
                    .put("durationMs", video.durationMs)
                    .put("sizeBytes", video.sizeBytes)
                    .put("width", video.width)
                    .put("height", video.height)
                    .put("dateAddedSeconds", video.dateAddedSeconds)
            )
        }
        return JSONObject().put("videos", items)
    }

    fun jsonToVideos(json: JSONObject): List<SharedVideo> {
        val items = json.optJSONArray("videos") ?: JSONArray()
        return (0 until items.length()).mapNotNull { index ->
            items.optJSONObject(index)?.let { video ->
                SharedVideo(
                    id = video.optLong("id", 0L),
                    name = video.optString("name", ""),
                    folder = video.optString("folder", ""),
                    folderId = video.optLong("folderId", 0L),
                    durationMs = video.optLong("durationMs", 0L),
                    sizeBytes = video.optLong("sizeBytes", 0L),
                    width = video.optInt("width", 0),
                    height = video.optInt("height", 0),
                    dateAddedSeconds = video.optLong("dateAddedSeconds", 0L)
                )
            }
        }
    }
}
