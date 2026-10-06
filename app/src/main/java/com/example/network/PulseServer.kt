package com.example.network

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.example.data.repository.PulseRepository
import com.example.notification.PulseNotificationManager
import com.example.telemetry.TelemetryCollector
import com.example.util.JsonUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStream
import java.net.ServerSocket
import java.net.Socket
import java.nio.charset.StandardCharsets

class PulseServer(
    private val context: Context,
    private val repository: PulseRepository,
    private val telemetryCollector: TelemetryCollector,
    private val notificationManager: PulseNotificationManager
) {
    private val scope = CoroutineScope(Dispatchers.IO + Job())
    private var serverSocket: ServerSocket? = null
    private var serverJob: Job? = null

    private val _isServerRunning = MutableStateFlow(false)
    val isServerRunning: StateFlow<Boolean> = _isServerRunning.asStateFlow()

    private val _serverPort = MutableStateFlow(8765)
    val serverPort: StateFlow<Int> = _serverPort.asStateFlow()

    private val _serverLog = MutableStateFlow<List<String>>(emptyList())
    val serverLog: StateFlow<List<String>> = _serverLog.asStateFlow()

    private val _incomingCount = MutableStateFlow(0)
    val incomingCount: StateFlow<Int> = _incomingCount.asStateFlow()

    fun startServer(port: Int = 8765, customDeviceName: String? = null) {
        if (_isServerRunning.value) return

        _serverPort.value = port
        serverJob = scope.launch {
            try {
                serverSocket = ServerSocket(port).apply {
                    reuseAddress = true
                }
                _isServerRunning.value = true
                addLog("🟢 Pulse Server started on port $port (IP: ${telemetryCollector.getLocalIpAddress()})")

                while (isActive && !serverSocket!!.isClosed) {
                    val clientSocket = serverSocket!!.accept()
                    launch {
                        handleClient(clientSocket, customDeviceName)
                    }
                }
            } catch (e: Exception) {
                if (isActive) {
                    addLog("⚠️ Server stopped or error: ${e.message}")
                }
            } finally {
                _isServerRunning.value = false
            }
        }
    }

    fun stopServer() {
        try {
            serverSocket?.close()
            serverSocket = null
            serverJob?.cancel()
            _isServerRunning.value = false
            addLog("🔴 Pulse Server stopped")
        } catch (e: Exception) {
            Log.e("PulseServer", "Error stopping server", e)
        }
    }

    private suspend fun handleClient(socket: Socket, customDeviceName: String?) {
        try {
            socket.use { s ->
                s.soTimeout = 10000
                val reader = BufferedReader(InputStreamReader(s.getInputStream(), StandardCharsets.UTF_8))
                val output = s.getOutputStream()

                val requestLine = reader.readLine() ?: return
                val parts = requestLine.split(" ")
                if (parts.size < 2) return

                val method = parts[0].uppercase()
                val path = parts[1]

                // Read headers
                var contentLength = 0
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    if (line.isNullOrEmpty()) break
                    val headerLower = line!!.lowercase()
                    if (headerLower.startsWith("content-length:")) {
                        contentLength = headerLower.substringAfter(":").trim().toIntOrNull() ?: 0
                    }
                }

                // Read body if exists
                val body = if (contentLength > 0) {
                    val buffer = CharArray(contentLength)
                    var read = 0
                    while (read < contentLength) {
                        val r = reader.read(buffer, read, contentLength - read)
                        if (r == -1) break
                        read += r
                    }
                    String(buffer, 0, read)
                } else ""

                val clientIp = s.inetAddress.hostAddress ?: "Unknown"

                when {
                    method == "GET" && path.startsWith("/api/ping") -> {
                        val responseJson = JSONObject().apply {
                            put("pong", true)
                            put("deviceName", customDeviceName ?: telemetryCollector.getDeviceDisplayName())
                            put("deviceModel", android.os.Build.MODEL)
                            put("timestamp", System.currentTimeMillis())
                        }
                        sendJsonResponse(output, 200, responseJson.toString())
                    }

                    method == "GET" && path.startsWith("/api/telemetry") -> {
                        val telemetry = telemetryCollector.collectTelemetry(customDeviceName)
                        val responseJson = JsonUtils.telemetryToJson(telemetry)
                        sendJsonResponse(output, 200, responseJson.toString())
                        addLog("📊 Telemetry sent to $clientIp")
                    }

                    method == "POST" && path.startsWith("/api/notify") -> {
                        val json = JSONObject(body)
                        val payload = JsonUtils.jsonToNotification(json)

                        // Save peer & notification to DB
                        repository.saveNotification(payload, "RECEIVED")
                        repository.saveOrUpdatePeer(
                            ip = clientIp,
                            name = payload.senderDeviceName,
                            telemetry = payload.telemetrySnapshot
                        )

                        // Trigger notification
                        withContext(Dispatchers.Main) {
                            notificationManager.showReceivedNotification(payload)
                        }

                        _incomingCount.value += 1
                        addLog("📬 Notification received from ${payload.senderDeviceName} ($clientIp): \"${payload.title}\"")

                        val responseJson = JSONObject().apply {
                            put("success", true)
                            put("message", "Notification delivered successfully")
                            put("timestamp", System.currentTimeMillis())
                        }
                        sendJsonResponse(output, 200, responseJson.toString())
                    }

                    method == "POST" && path.startsWith("/api/action") -> {
                        val json = JSONObject(body)
                        val request = JsonUtils.jsonToActionRequest(json)

                        addLog("⚡ Action '${request.actionType}' from ${request.senderName} ($clientIp)")

                        var resultMsg = "Action executed"
                        when (request.actionType) {
                            "PING" -> {
                                resultMsg = "Pong from ${customDeviceName ?: telemetryCollector.getDeviceDisplayName()}"
                            }
                            "RING_ALARM" -> {
                                withContext(Dispatchers.Main) {
                                    notificationManager.triggerAlarm(request.senderName)
                                }
                                resultMsg = "Ringing alert triggered"
                            }
                            "STOP_ALARM" -> {
                                withContext(Dispatchers.Main) {
                                    notificationManager.stopAlarm()
                                }
                                resultMsg = "Alarm silenced"
                            }
                            "VIBRATE_TEST" -> {
                                notificationManager.triggerVibration()
                                resultMsg = "Vibration triggered"
                            }
                            "COPY_CLIPBOARD" -> {
                                withContext(Dispatchers.Main) {
                                    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                                    val clip = ClipData.newPlainText("PulseLink Shared Text", request.payload)
                                    cm?.setPrimaryClip(clip)
                                }
                                notificationManager.triggerVibration()
                                resultMsg = "Copied text to clipboard"
                            }
                        }

                        val currentTel = telemetryCollector.collectTelemetry(customDeviceName)
                        val actionResp = com.example.model.RemoteActionResponse(
                            success = true,
                            message = resultMsg,
                            telemetry = currentTel
                        )
                        sendJsonResponse(output, 200, JsonUtils.actionResponseToJson(actionResp).toString())
                    }

                    else -> {
                        sendJsonResponse(output, 404, "{\"error\": \"Not Found\"}")
                    }
                }
            }
        } catch (e: Exception) {
            // Log error
            Log.e("PulseServer", "Error handling client request", e)
        }
    }

    private fun sendJsonResponse(output: OutputStream, statusCode: Int, jsonBody: String) {
        val statusText = when (statusCode) {
            200 -> "OK"
            404 -> "Not Found"
            else -> "Error"
        }
        val bytes = jsonBody.toByteArray(StandardCharsets.UTF_8)
        val headers = "HTTP/1.1 $statusCode $statusText\r\n" +
                "Content-Type: application/json; charset=utf-8\r\n" +
                "Content-Length: ${bytes.size}\r\n" +
                "Access-Control-Allow-Origin: *\r\n" +
                "Connection: close\r\n\r\n"

        output.write(headers.toByteArray(StandardCharsets.UTF_8))
        output.write(bytes)
        output.flush()
    }

    private fun addLog(msg: String) {
        val timestamp = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date())
        val entry = "[$timestamp] $msg"
        val current = _serverLog.value.toMutableList()
        if (current.size > 50) current.removeAt(0)
        current.add(entry)
        _serverLog.value = current
    }
}
