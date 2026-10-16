package com.example.network

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.example.call.AudioLink
import com.example.call.CallAudioRelay
import com.example.call.CallController
import com.example.data.repository.PulseRepository
import com.example.model.CallResult
import com.example.model.PeerNotificationFeed
import com.example.notification.DeviceNotificationStore
import com.example.notification.NotificationForwarder
import com.example.notification.PulseNotificationManager
import com.example.security.Pairing
import com.example.service.PulseNotificationListener
import com.example.telemetry.TelemetryCollector
import com.example.util.JsonUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
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
    private val notificationManager: PulseNotificationManager,
    private val notificationForwarder: NotificationForwarder,
    private val callController: CallController,
    private val pairing: Pairing,
    private val callAudioRelay: CallAudioRelay
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

    // IP of a peer whose notifications just changed, so an open screen can refresh its list
    private val _peerNotificationChanges = MutableSharedFlow<String>(
        extraBufferCapacity = 16,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val peerNotificationChanges: SharedFlow<String> = _peerNotificationChanges.asSharedFlow()

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
                var nonce: String? = null
                var signature: String? = null
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    if (line.isNullOrEmpty()) break
                    val headerLower = line!!.lowercase()
                    when {
                        headerLower.startsWith("content-length:") ->
                            contentLength = headerLower.substringAfter(":").trim().toIntOrNull() ?: 0
                        headerLower.startsWith(Pairing.HEADER_NONCE.lowercase() + ":") ->
                            nonce = headerLower.substringAfter(":").trim()
                        headerLower.startsWith(Pairing.HEADER_SIGNATURE.lowercase() + ":") ->
                            signature = headerLower.substringAfter(":").trim()
                    }
                }

                // Read body if exists. Content-Length counts bytes, so chars are converted back as they are read:
                // otherwise emoji or non-English text would leave the loop waiting for bytes that never come
                val body = if (contentLength > 0) {
                    val text = StringBuilder()
                    var bytesRead = 0
                    while (bytesRead < contentLength) {
                        val c = reader.read()
                        if (c == -1) break
                        text.append(c.toChar())
                        bytesRead += utf8ByteCount(c)
                    }
                    text.toString()
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

                    method == "GET" && path.startsWith("/api/notifications") -> {
                        // Only hand out this device's notifications if the user opted in
                        val sharingEnabled = DeviceNotificationStore.isSharingEnabled(context)
                        val feed = PeerNotificationFeed(
                            sharingEnabled = sharingEnabled,
                            accessGranted = DeviceNotificationStore.hasNotificationAccess(context),
                            notifications = if (sharingEnabled) DeviceNotificationStore.snapshot() else emptyList()
                        )
                        sendJsonResponse(output, 200, JsonUtils.notificationFeedToJson(feed).toString())
                    }

                    method == "POST" && path.startsWith("/api/notifications/subscribe") -> {
                        // The peer wants this device's notifications pushed to its own server as they arrive
                        val replyPort = JSONObject(body).optInt("port", 8765)
                        notificationForwarder.subscribe(clientIp, replyPort)
                        sendJsonResponse(output, 200, "{\"subscribed\": true}")
                        addLog("🔔 $clientIp subscribed to this device's notifications")
                    }

                    method == "POST" && path.startsWith("/api/notifications/unsubscribe") -> {
                        val replyPort = JSONObject(body).optInt("port", 8765)
                        notificationForwarder.unsubscribe(clientIp, replyPort)
                        sendJsonResponse(output, 200, "{\"subscribed\": false}")
                    }

                    method == "POST" && path.startsWith("/api/notifications/dismiss") -> {
                        // A peer swiped one of this device's notifications away on its side.
                        // Only allowed while this device shares its notifications.
                        val key = JSONObject(body).optString("key", "")
                        val dismissed = key.isNotEmpty() &&
                            DeviceNotificationStore.isSharingEnabled(context) &&
                            PulseNotificationListener.dismiss(key)
                        sendJsonResponse(output, 200, JSONObject().put("dismissed", dismissed).toString())
                        if (dismissed) addLog("🧹 $clientIp dismissed a notification on this device")
                    }

                    method == "POST" && path.startsWith("/api/notifications/reply") -> {
                        // A peer answered one of this device's chats from its side.
                        // Only allowed while this device shares its notifications.
                        val json = JSONObject(body)
                        val key = json.optString("key", "")
                        val text = json.optString("text", "").trim()
                        val replied = key.isNotEmpty() && text.isNotEmpty() &&
                            DeviceNotificationStore.isSharingEnabled(context) &&
                            PulseNotificationListener.reply(key, text)
                        sendJsonResponse(output, 200, JSONObject().put("replied", replied).toString())
                        if (replied) addLog("💬 $clientIp replied to a chat on this device")
                    }

                    method == "POST" && path.startsWith("/api/notifications/push") -> {
                        // The peer's own notifications (WhatsApp, SMS, calls) changed
                        val json = JSONObject(body)
                        val accepted = DeviceNotificationStore.isPeerAlertsEnabled(context)
                        if (accepted) {
                            json.optJSONObject("notification")?.let { itemJson ->
                                notificationManager.showPeerDeviceNotification(
                                    senderIp = clientIp,
                                    senderPort = json.optInt("senderPort", 8765),
                                    senderName = json.optString("senderDeviceName", clientIp),
                                    item = JsonUtils.jsonToDeviceNotification(itemJson)
                                )
                            }
                            // Whatever is no longer showing on the peer stops showing here too
                            json.optJSONArray("activeKeys")?.let { keys ->
                                val activeKeys = (0 until keys.length()).map { keys.optString(it) }.toSet()
                                notificationManager.retainPeerDeviceNotifications(clientIp, activeKeys)
                            }
                            _peerNotificationChanges.tryEmit(clientIp)
                        }
                        // Answering false tells the sender to stop pushing here
                        sendJsonResponse(output, 200, JSONObject().put("accepted", accepted).toString())
                    }

                    method == "POST" && path.startsWith("/api/auth/challenge") -> {
                        // A one-time value the peer signs its next request with, to show it has the pairing code
                        sendJsonResponse(output, 200, JSONObject().put("nonce", pairing.newChallenge()).toString())
                    }

                    // Calls cost money and the sound relay opens a microphone, so everything below
                    // needs this device's pairing code
                    (path.startsWith("/api/call/") || path.startsWith("/api/audio/")) &&
                        !pairing.verify(nonce, signature, method, path, body) -> {
                        sendJsonResponse(output, 403, "{\"error\": \"pairing\"}")
                    }

                    method == "POST" && path.startsWith("/api/audio/stream") -> {
                        // The peer wants to hear and talk on the call this device is in. After the
                        // answer, this connection carries the sound both ways until the call ends.
                        val refusal = callAudioRelay.hostRefusal()
                        sendJsonResponse(output, 200, callResultToJson(refusal))
                        if (refusal == CallResult.OK) {
                            addLog("🎧 $clientIp joined this device's call sound")
                            callAudioRelay.runHost(AudioLink.overWifi(s))
                            addLog("🎧 Call sound to $clientIp ended")
                        }
                    }

                    // A peer controls this device's phone calls. Each one is refused unless the user
                    // turned on "Allow Remote Calls" here.
                    method == "POST" && path.startsWith("/api/call/dial") -> {
                        val json = JSONObject(body)
                        val result = callController.dial(json.optString("number", ""), json.optBoolean("speaker", true))
                        sendJsonResponse(output, 200, callResultToJson(result))
                        if (result == CallResult.OK) addLog("📞 $clientIp placed a call from this device")
                    }

                    method == "POST" && path.startsWith("/api/call/answer") -> {
                        val result = callController.answer()
                        sendJsonResponse(output, 200, callResultToJson(result))
                        if (result == CallResult.OK) addLog("📞 $clientIp answered a call on this device")
                    }

                    method == "POST" && path.startsWith("/api/call/end") -> {
                        val result = callController.end()
                        sendJsonResponse(output, 200, callResultToJson(result))
                        if (result == CallResult.OK) addLog("📞 $clientIp ended a call on this device")
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

    private fun callResultToJson(result: CallResult): String {
        return JSONObject().put("ok", result == CallResult.OK).put("reason", result.reason).toString()
    }

    // Bytes one UTF-16 unit takes in UTF-8; the two halves of a surrogate pair add up to its 4 bytes
    private fun utf8ByteCount(c: Int): Int = when {
        c < 0x80 -> 1
        c < 0x800 -> 2
        c in 0xD800..0xDFFF -> 2
        else -> 3
    }

    private fun sendJsonResponse(output: OutputStream, statusCode: Int, jsonBody: String) {
        val statusText = when (statusCode) {
            200 -> "OK"
            403 -> "Forbidden"
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
