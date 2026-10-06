package com.example.util

import com.example.model.DeviceTelemetry
import com.example.model.NotificationPayload
import com.example.model.RemoteActionRequest
import com.example.model.RemoteActionResponse
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
}
