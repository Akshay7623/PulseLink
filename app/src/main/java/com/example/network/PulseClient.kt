package com.example.network

import com.example.call.AudioLink
import com.example.model.CallResult
import com.example.model.DeviceTelemetry
import com.example.model.NotificationPayload
import com.example.model.PeerNotificationFeed
import com.example.model.RemoteActionRequest
import com.example.model.RemoteActionResponse
import com.example.security.Pairing
import com.example.util.JsonUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStream
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.InetSocketAddress
import java.net.Socket
import java.net.URL
import java.nio.charset.StandardCharsets

sealed class NetworkResult<out T> {
    data class Success<T>(val data: T, val latencyMs: Long = 0) : NetworkResult<T>()
    data class Error(
        val message: String,
        val throwable: Throwable? = null,
        val httpCode: Int? = null // set when the peer answered, but not with success
    ) : NetworkResult<Nothing>()
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

    suspend fun fetchNotifications(
        targetIp: String,
        targetPort: Int
    ): NetworkResult<PeerNotificationFeed> = withContext(Dispatchers.IO) {
        try {
            val url = URL("http://$targetIp:$targetPort/api/notifications")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 5000
                readTimeout = 5000
                setRequestProperty("Accept", "application/json")
            }

            when (val code = conn.responseCode) {
                200 -> {
                    val responseText = conn.inputStream.bufferedReader().use { it.readText() }
                    NetworkResult.Success(JsonUtils.jsonToNotificationFeed(JSONObject(responseText)))
                }
                // Peer runs an older PulseLink that has no notifications endpoint
                404 -> NetworkResult.Success(PeerNotificationFeed(isSupported = false))
                else -> NetworkResult.Error("Failed to fetch notifications (HTTP $code)")
            }
        } catch (e: Exception) {
            NetworkResult.Error(e.localizedMessage ?: "Cannot reach $targetIp:$targetPort", e)
        }
    }

    /**
     * Asks a peer to push its notifications to this device's server as they arrive.
     */
    suspend fun subscribeToNotifications(
        targetIp: String,
        targetPort: Int,
        replyPort: Int
    ): NetworkResult<JSONObject> {
        return postJson(targetIp, targetPort, "/api/notifications/subscribe", JSONObject().put("port", replyPort))
    }

    suspend fun unsubscribeFromNotifications(
        targetIp: String,
        targetPort: Int,
        replyPort: Int
    ): NetworkResult<JSONObject> {
        return postJson(targetIp, targetPort, "/api/notifications/unsubscribe", JSONObject().put("port", replyPort))
    }

    /**
     * Clears one of the peer's own notifications on the peer itself. The result is false when the peer
     * did not do it (sharing turned off, or no notification access).
     */
    suspend fun dismissNotification(
        targetIp: String,
        targetPort: Int,
        key: String
    ): NetworkResult<Boolean> {
        val body = JSONObject().put("key", key)
        return when (val result = postJson(targetIp, targetPort, "/api/notifications/dismiss", body, timeoutMs = 3000)) {
            is NetworkResult.Success -> NetworkResult.Success(result.data.optBoolean("dismissed", false))
            is NetworkResult.Error -> result
        }
    }

    /**
     * Answers one of the peer's chats through the reply box of its notification. The result is false
     * when the peer did not send it (sharing turned off, the notification is gone, or it has no reply box).
     */
    suspend fun replyToNotification(
        targetIp: String,
        targetPort: Int,
        key: String,
        text: String
    ): NetworkResult<Boolean> {
        val body = JSONObject().put("key", key).put("text", text)
        return when (val result = postJson(targetIp, targetPort, "/api/notifications/reply", body)) {
            is NetworkResult.Success -> NetworkResult.Success(result.data.optBoolean("replied", false))
            is NetworkResult.Error -> result
        }
    }

    /**
     * Has the peer call a number from its own SIM. The call and its sound stay on the peer.
     */
    suspend fun dialNumber(
        targetIp: String,
        targetPort: Int,
        number: String,
        speaker: Boolean,
        pairingKey: ByteArray?
    ): NetworkResult<CallResult> {
        val body = JSONObject().put("number", number).put("speaker", speaker)
        return callRequest(targetIp, targetPort, "/api/call/dial", body, pairingKey)
    }

    suspend fun answerCall(targetIp: String, targetPort: Int, pairingKey: ByteArray?): NetworkResult<CallResult> {
        return callRequest(targetIp, targetPort, "/api/call/answer", JSONObject(), pairingKey)
    }

    /**
     * Hangs up the peer's call in progress, or declines the one ringing there.
     */
    suspend fun endCall(targetIp: String, targetPort: Int, pairingKey: ByteArray?): NetworkResult<CallResult> {
        return callRequest(targetIp, targetPort, "/api/call/end", JSONObject(), pairingKey)
    }

    /**
     * @param pairingKey from the peer's pairing code, null when the user has not entered it yet
     */
    private suspend fun callRequest(
        targetIp: String,
        targetPort: Int,
        path: String,
        body: JSONObject,
        pairingKey: ByteArray?
    ): NetworkResult<CallResult> {
        if (pairingKey == null) return NetworkResult.Success(CallResult.NOT_PAIRED)

        // The peer hands out a one-time value, and signing it proves the code without sending it
        val nonce = when (val challenge = postJson(targetIp, targetPort, "/api/auth/challenge", JSONObject())) {
            is NetworkResult.Success -> challenge.data.optString("nonce", "")
            is NetworkResult.Error -> return callFailure(challenge)
        }
        val headers = mapOf(
            Pairing.HEADER_NONCE to nonce,
            Pairing.HEADER_SIGNATURE to Pairing.sign(pairingKey, nonce, "POST", path, body.toString())
        )

        return when (val result = postJson(targetIp, targetPort, path, body, headers = headers)) {
            is NetworkResult.Success -> NetworkResult.Success(
                if (result.data.optBoolean("ok", false)) CallResult.OK
                else CallResult.fromReason(result.data.optString("reason", ""))
            )
            is NetworkResult.Error -> callFailure(result)
        }
    }

    private fun callFailure(error: NetworkResult.Error): NetworkResult<CallResult> = when (error.httpCode) {
        403 -> NetworkResult.Success(CallResult.NOT_PAIRED)
        // Peer runs an older PulseLink that has no call control
        404 -> NetworkResult.Success(CallResult.UNSUPPORTED)
        else -> error
    }

    /**
     * A connection to the peer that carries the sound of its phone call both ways, or the reason
     * the peer would not open one.
     */
    class AudioStream(val link: AudioLink?, val result: CallResult)

    suspend fun openAudioStream(
        targetIp: String,
        targetPort: Int,
        pairingKey: ByteArray?
    ): AudioStream = withContext(Dispatchers.IO) {
        if (pairingKey == null) return@withContext AudioStream(null, CallResult.NOT_PAIRED)

        val path = "/api/audio/stream"
        val body = "{}"
        val nonce = when (val challenge = postJson(targetIp, targetPort, "/api/auth/challenge", JSONObject())) {
            is NetworkResult.Success -> challenge.data.optString("nonce", "")
            is NetworkResult.Error -> return@withContext AudioStream(
                null,
                if (challenge.httpCode == 404) CallResult.UNSUPPORTED else CallResult.FAILED
            )
        }

        // The answer has to be read off the same connection the sound then travels on, so this
        // request is written by hand instead of going through HttpURLConnection
        val socket = Socket()
        try {
            socket.connect(InetSocketAddress(targetIp, targetPort), 5000)
            socket.soTimeout = 6000
            val request = "POST $path HTTP/1.1\r\n" +
                "Host: $targetIp:$targetPort\r\n" +
                "Content-Type: application/json; charset=utf-8\r\n" +
                "Content-Length: ${body.length}\r\n" +
                "${Pairing.HEADER_NONCE}: $nonce\r\n" +
                "${Pairing.HEADER_SIGNATURE}: ${Pairing.sign(pairingKey, nonce, "POST", path, body)}\r\n\r\n" +
                body
            socket.getOutputStream().apply {
                write(request.toByteArray(StandardCharsets.UTF_8))
                flush()
            }

            val (status, answer) = readRawResponse(socket.getInputStream())
            val result = when {
                status == 403 -> CallResult.NOT_PAIRED
                status == 404 -> CallResult.UNSUPPORTED
                status != 200 -> CallResult.FAILED
                answer.optBoolean("ok", false) -> CallResult.OK
                else -> CallResult.fromReason(answer.optString("reason", ""))
            }
            if (result == CallResult.OK) return@withContext AudioStream(AudioLink.overWifi(socket), result)

            socket.close()
            AudioStream(null, result)
        } catch (e: Exception) {
            runCatching { socket.close() }
            AudioStream(null, CallResult.FAILED)
        }
    }

    // Reads exactly one response and not a byte more: what follows it on the connection is sound
    private fun readRawResponse(input: InputStream): Pair<Int, JSONObject> {
        val head = StringBuilder()
        while (!head.endsWith("\r\n\r\n")) {
            val byte = input.read()
            if (byte == -1) throw IOException("Connection closed before the answer")
            head.append(byte.toChar())
            if (head.length > 8192) throw IOException("Answer headers too long")
        }

        val lines = head.toString().split("\r\n")
        val status = lines.first().split(" ").getOrNull(1)?.toIntOrNull() ?: 0
        val length = lines.firstOrNull { it.lowercase().startsWith("content-length:") }
            ?.substringAfter(":")?.trim()?.toIntOrNull() ?: 0

        val body = ByteArray(length)
        var filled = 0
        while (filled < length) {
            val read = input.read(body, filled, length - filled)
            if (read == -1) throw IOException("Connection closed inside the answer")
            filled += read
        }
        val json = runCatching { JSONObject(String(body, StandardCharsets.UTF_8)) }.getOrDefault(JSONObject())
        return status to json
    }

    /**
     * Delivers one notification event to a subscribed peer. The result is false when the peer
     * no longer wants them.
     */
    suspend fun pushNotificationEvent(
        targetIp: String,
        targetPort: Int,
        event: JSONObject
    ): NetworkResult<Boolean> {
        return when (val result = postJson(targetIp, targetPort, "/api/notifications/push", event, timeoutMs = 3000)) {
            is NetworkResult.Success -> NetworkResult.Success(result.data.optBoolean("accepted", true))
            is NetworkResult.Error -> result
        }
    }

    private suspend fun postJson(
        targetIp: String,
        targetPort: Int,
        path: String,
        body: JSONObject,
        timeoutMs: Int = 6000,
        headers: Map<String, String> = emptyMap()
    ): NetworkResult<JSONObject> = withContext(Dispatchers.IO) {
        try {
            val url = URL("http://$targetIp:$targetPort$path")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = timeoutMs
                readTimeout = timeoutMs
                doOutput = true
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
                headers.forEach { (name, value) -> setRequestProperty(name, value) }
            }

            OutputStreamWriter(conn.outputStream, StandardCharsets.UTF_8).use { writer ->
                writer.write(body.toString())
                writer.flush()
            }

            val code = conn.responseCode
            if (code == 200) {
                val responseText = conn.inputStream.bufferedReader().use { it.readText() }
                NetworkResult.Success(JSONObject(responseText))
            } else {
                NetworkResult.Error("HTTP error $code: ${conn.responseMessage}", httpCode = code)
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
