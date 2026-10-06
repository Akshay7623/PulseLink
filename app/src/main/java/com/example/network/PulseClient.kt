package com.example.network

import com.example.model.DeviceTelemetry
import com.example.model.NotificationPayload
import com.example.model.RemoteActionRequest
import com.example.model.RemoteActionResponse
import com.example.util.JsonUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.InetSocketAddress
import java.net.Socket
import java.net.URL
import java.nio.charset.StandardCharsets

sealed class NetworkResult<out T> {
    data class Success<T>(val data: T, val latencyMs: Long = 0) : NetworkResult<T>()
    data class Error(val message: String, val throwable: Throwable? = null) : NetworkResult<Nothing>()
}

class PulseClient {

    suspend fun sendNotification(
        targetIp: String,
        targetPort: Int,
        payload: NotificationPayload
    ): NetworkResult<String> = withContext(Dispatchers.IO) {
        val startTime = System.currentTimeMillis()
        try {
            val url = URL("http://$targetIp:$targetPort/api/notify")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 6000
                readTimeout = 6000
                doOutput = true
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
            }

            val jsonString = JsonUtils.notificationToJson(payload).toString()
            OutputStreamWriter(conn.outputStream, StandardCharsets.UTF_8).use { writer ->
                writer.write(jsonString)
                writer.flush()
            }

            val code = conn.responseCode
            val latency = System.currentTimeMillis() - startTime
            if (code == 200) {
                val response = conn.inputStream.bufferedReader().use { it.readText() }
                NetworkResult.Success(response, latency)
            } else {
                NetworkResult.Error("HTTP error $code: ${conn.responseMessage}")
            }
        } catch (e: Exception) {
            NetworkResult.Error(e.localizedMessage ?: "Failed to connect to $targetIp:$targetPort", e)
        }
    }

    suspend fun fetchTelemetry(
        targetIp: String,
        targetPort: Int
    ): NetworkResult<DeviceTelemetry> = withContext(Dispatchers.IO) {
        val startTime = System.currentTimeMillis()
        try {
            val url = URL("http://$targetIp:$targetPort/api/telemetry")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 5000
                readTimeout = 5000
                setRequestProperty("Accept", "application/json")
            }

            val code = conn.responseCode
            val latency = System.currentTimeMillis() - startTime
            if (code == 200) {
                val responseText = conn.inputStream.bufferedReader().use { it.readText() }
                val json = JSONObject(responseText)
                val telemetry = JsonUtils.jsonToTelemetry(json)
                NetworkResult.Success(telemetry, latency)
            } else {
                NetworkResult.Error("Failed to fetch telemetry (HTTP $code)")
            }
        } catch (e: Exception) {
            NetworkResult.Error(e.localizedMessage ?: "Cannot reach $targetIp:$targetPort", e)
        }
    }

    suspend fun sendAction(
        targetIp: String,
        targetPort: Int,
        request: RemoteActionRequest
    ): NetworkResult<RemoteActionResponse> = withContext(Dispatchers.IO) {
        val startTime = System.currentTimeMillis()
        try {
            val url = URL("http://$targetIp:$targetPort/api/action")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 6000
                readTimeout = 6000
                doOutput = true
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
            }

            val jsonString = JsonUtils.actionRequestToJson(request).toString()
            OutputStreamWriter(conn.outputStream, StandardCharsets.UTF_8).use { writer ->
                writer.write(jsonString)
                writer.flush()
            }

            val code = conn.responseCode
            val latency = System.currentTimeMillis() - startTime
            if (code == 200) {
                val responseText = conn.inputStream.bufferedReader().use { it.readText() }
                val json = JSONObject(responseText)
                val response = JsonUtils.jsonToActionResponse(json)
                NetworkResult.Success(response, latency)
            } else {
                NetworkResult.Error("Action failed (HTTP $code)")
            }
        } catch (e: Exception) {
            NetworkResult.Error(e.localizedMessage ?: "Failed to execute action on $targetIp", e)
        }
    }

    suspend fun pingPeer(
        targetIp: String,
        targetPort: Int
    ): NetworkResult<Pair<String, Long>> = withContext(Dispatchers.IO) {
        val startTime = System.currentTimeMillis()
        try {
            val url = URL("http://$targetIp:$targetPort/api/ping")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 3000
                readTimeout = 3000
            }

            val code = conn.responseCode
            val latency = System.currentTimeMillis() - startTime
            if (code == 200) {
                val responseText = conn.inputStream.bufferedReader().use { it.readText() }
                val json = JSONObject(responseText)
                val name = json.optString("deviceName", targetIp)
                NetworkResult.Success(Pair(name, latency), latency)
            } else {
                NetworkResult.Error("Ping failed (HTTP $code)")
            }
        } catch (e: Exception) {
            NetworkResult.Error(e.localizedMessage ?: "Cannot ping $targetIp", e)
        }
    }
}
