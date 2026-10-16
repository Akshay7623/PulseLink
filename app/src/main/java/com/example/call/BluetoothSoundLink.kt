package com.example.call

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothClass
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothServerSocket
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import com.example.call.BluetoothSoundState.Status
import com.example.model.CallResult
import com.example.security.Pairing
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.Closeable
import java.io.EOFException
import java.io.IOException
import java.util.UUID

/**
 * Whether the call sound this phone asks for would travel over Bluetooth, for the user to read
 */
data class BluetoothSoundState(
    val status: Status = Status.OFF,
    val partner: String = "" // the paired device the sound would go through, when READY
) {
    enum class Status {
        OFF, // the user has not asked for Bluetooth
        NO_PERMISSION, // this phone has not been given the Nearby devices permission
        BLUETOOTH_OFF,
        PEER_OUTDATED, // the other phone runs an older PulseLink without this
        PEER_NOT_ACCEPTING, // the other phone is not taking Bluetooth connections right now
        NOT_PAIRED, // the two phones are not paired in Android's Bluetooth settings
        READY
    }
}

/**
 * Carries a call's sound straight between two phones that are paired in Android's Bluetooth
 * settings, in place of Wi-Fi. Android lets no ordinary app make a phone show up as a Bluetooth
 * headset on another phone, so this is PulseLink's own connection between its two copies: the phone
 * the call is on still listens to and plays the call the way the relay does.
 */
class BluetoothSoundLink(private val context: Context, private val pairing: Pairing) {

    private val prefs = context.getSharedPreferences("pulselink_prefs", Context.MODE_PRIVATE)
    private val scope = CoroutineScope(Dispatchers.IO + Job())
    private val adapter: BluetoothAdapter?
        get() = (context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter

    @Volatile private var serverSocket: BluetoothServerSocket? = null
    private var hostJob: Job? = null

    /** Whether the call sound this phone asks for should come over Bluetooth when it can. */
    var isPreferred: Boolean
        get() = prefs.getBoolean(KEY_SOUND_OVER_BLUETOOTH, false)
        set(value) = prefs.edit().putBoolean(KEY_SOUND_OVER_BLUETOOTH, value).apply()

    // Android 12 and later ask the user ("Nearby devices"); before that Bluetooth came with the install
    fun hasPermission(): Boolean {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
    }

    private fun isBluetoothOn(): Boolean = try {
        adapter?.isEnabled == true
    } catch (e: SecurityException) {
        false
    }

    /**
     * @return this phone's Bluetooth name while it accepts a call sound connection, empty while it does not
     */
    @SuppressLint("MissingPermission")
    fun hostName(): String {
        if (serverSocket == null) return ""
        return try {
            adapter?.name.orEmpty().ifEmpty { Build.MODEL }
        } catch (e: SecurityException) {
            ""
        }
    }

    /**
     * Starts accepting the other phone's connection for a call's sound. Does nothing while already
     * accepting, and stops while Bluetooth is off or not permitted.
     * @param refusal why this device will not relay its call sound right now, or CallResult.OK
     * @param onJoined relays the call over an accepted connection, and returns when that is over
     */
    @Synchronized
    @SuppressLint("MissingPermission")
    fun startHost(refusal: () -> CallResult, onJoined: (AudioLink) -> Unit) {
        if (!hasPermission() || !isBluetoothOn()) {
            stopHost()
            return
        }
        if (hostJob?.isActive == true) return

        hostJob = scope.launch {
            var server: BluetoothServerSocket? = null
            try {
                server = adapter?.listenUsingRfcommWithServiceRecord(SERVICE_NAME, SERVICE_UUID) ?: return@launch
                serverSocket = server
                while (isActive) {
                    val socket = server.accept()
                    scope.launch { serve(socket, refusal, onJoined) }
                }
            } catch (e: IOException) {
                // Bluetooth was switched off, or stopHost closed the socket
            } catch (e: SecurityException) {
                Log.e(TAG, "Bluetooth is not permitted", e)
            } finally {
                server?.let { closeQuietly(it) }
                if (serverSocket === server) serverSocket = null
            }
        }
    }

    @Synchronized
    fun stopHost() {
        hostJob?.cancel()
        hostJob = null
        serverSocket?.let { closeQuietly(it) }
        serverSocket = null
    }

    private fun serve(socket: BluetoothSocket, refusal: () -> CallResult, onJoined: (AudioLink) -> Unit) {
        val link = AudioLink.overBluetooth(socket)
        // A Bluetooth connection has no read timeout, so one that never proves itself is closed from here
        val guard = scope.launch {
            delay(HANDSHAKE_TIMEOUT_MS)
            link.close()
        }
        try {
            val nonce = pairing.newChallenge()
            val result = hostHandshake(
                link = link,
                nonce = nonce,
                verify = { proof -> pairing.verify(nonce, proof, SIGNED_METHOD, SIGNED_PATH, "") },
                refusal = refusal
            )
            if (result == CallResult.OK) {
                guard.cancel()
                onJoined(link)
            } else {
                // Closing at once could cut the answer off before it is read, so the other phone hangs up first
                link.input.read()
            }
        } catch (e: IOException) {
            // The other phone went away
        } catch (e: Exception) {
            Log.e(TAG, "Error relaying call sound over Bluetooth", e)
        } finally {
            guard.cancel()
            link.close()
        }
    }

    /**
     * @param hostName the other phone's Bluetooth name as it reports it: empty while it is not
     * accepting, null when it runs an older PulseLink
     */
    fun state(hostName: String?): BluetoothSoundState = when {
        !isPreferred -> BluetoothSoundState(Status.OFF)
        !hasPermission() -> BluetoothSoundState(Status.NO_PERMISSION)
        !isBluetoothOn() -> BluetoothSoundState(Status.BLUETOOTH_OFF)
        hostName == null -> BluetoothSoundState(Status.PEER_OUTDATED)
        hostName.isEmpty() -> BluetoothSoundState(Status.PEER_NOT_ACCEPTING)
        else -> candidates(hostName).firstOrNull()
            ?.let { BluetoothSoundState(Status.READY, nameOf(it).ifEmpty { hostName }) }
            ?: BluetoothSoundState(Status.NOT_PAIRED)
    }

    /**
     * Connects to the other phone and proves this one knows its pairing code.
     * @return the connection, or null when there is none to be had over Bluetooth, which leaves
     * Wi-Fi to try
     */
    fun connect(hostName: String?, pairingKey: ByteArray): AudioLink? {
        return candidates(hostName).take(MAX_DEVICES_TRIED).firstNotNullOfOrNull { connectTo(it, pairingKey) }
    }

    // The paired devices that may be the other phone, likeliest first
    @SuppressLint("MissingPermission")
    private fun candidates(hostName: String?): List<BluetoothDevice> {
        if (hostName.isNullOrEmpty() || !hasPermission() || !isBluetoothOn()) return emptyList()
        return try {
            val paired = adapter?.bondedDevices.orEmpty()
            // A phone renamed since the two were paired is still found, as one of the few paired phones
            paired.filter { nameOf(it).equals(hostName, ignoreCase = true) }.ifEmpty {
                paired.filter { it.bluetoothClass?.majorDeviceClass == BluetoothClass.Device.Major.PHONE }
            }
        } catch (e: SecurityException) {
            emptyList()
        }
    }

    @SuppressLint("MissingPermission")
    private fun nameOf(device: BluetoothDevice): String = try {
        device.name.orEmpty()
    } catch (e: SecurityException) {
        ""
    }

    @SuppressLint("MissingPermission")
    private fun connectTo(device: BluetoothDevice, pairingKey: ByteArray): AudioLink? {
        val socket = try {
            device.createRfcommSocketToServiceRecord(SERVICE_UUID)
        } catch (e: Exception) {
            return null
        }
        // Neither connecting nor reading gives up by itself, so a phone out of reach is given up on from here
        val guard = scope.launch {
            delay(CONNECT_TIMEOUT_MS)
            closeQuietly(socket)
        }
        return try {
            socket.connect()
            val link = AudioLink.overBluetooth(socket)
            val answer = clientHandshake(link, pairingKey)
            if (answer != CallResult.OK) throw IOException("Refused: ${answer.reason}")
            link
        } catch (e: Exception) {
            Log.i(TAG, "No call sound over Bluetooth through ${nameOf(device)}: ${e.message}")
            closeQuietly(socket)
            null
        } finally {
            guard.cancel()
        }
    }

    private fun closeQuietly(closeable: Closeable) {
        try {
            closeable.close()
        } catch (e: IOException) {
            // already closed
        }
    }

    companion object {
        private const val TAG = "BluetoothSoundLink"
        private const val KEY_SOUND_OVER_BLUETOOTH = "call_sound_over_bluetooth"
        private const val SERVICE_NAME = "PulseLink call sound"
        private val SERVICE_UUID: UUID = UUID.fromString("731db92d-5ae2-4c01-b09b-654e12aa47eb")

        const val FRAME_CHALLENGE = 10
        const val FRAME_PROOF = 11
        const val FRAME_ANSWER = 12

        // What the proof is signed over, so it fits this connection and no Wi-Fi request
        const val SIGNED_METHOD = "BLUETOOTH"
        const val SIGNED_PATH = "/audio/stream"

        private const val HANDSHAKE_TIMEOUT_MS = 6000L
        private const val CONNECT_TIMEOUT_MS = 8000L
        private const val MAX_DEVICES_TRIED = 2

        /**
         * The opening exchange on the phone the call is on: it hands out a one-time value and reads
         * back the proof that the other phone knows this one's pairing code.
         * @param verify whether the proof fits the one-time value
         * @param refusal why this device will not relay its call sound right now, or CallResult.OK
         * @return what the other phone was told
         */
        fun hostHandshake(
            link: AudioLink,
            nonce: String,
            verify: (proof: String) -> Boolean,
            refusal: () -> CallResult
        ): CallResult {
            CallAudioRelay.writeFrame(link.output, FRAME_CHALLENGE, nonce.toByteArray())
            val (type, proof) = CallAudioRelay.readFrame(link.input) ?: throw EOFException()
            val result = if (type == FRAME_PROOF && verify(String(proof))) refusal() else CallResult.NOT_PAIRED
            CallAudioRelay.writeFrame(link.output, FRAME_ANSWER, result.reason.toByteArray())
            return result
        }

        /**
         * The opening exchange on the phone the user talks on: it signs the host's one-time value
         * with the host's pairing code.
         * @return what the host answered
         */
        fun clientHandshake(link: AudioLink, pairingKey: ByteArray): CallResult {
            val (type, nonce) = CallAudioRelay.readFrame(link.input) ?: throw EOFException()
            if (type != FRAME_CHALLENGE) throw IOException("Not a PulseLink call sound connection")
            val proof = Pairing.sign(pairingKey, String(nonce), SIGNED_METHOD, SIGNED_PATH, "")
            CallAudioRelay.writeFrame(link.output, FRAME_PROOF, proof.toByteArray())

            val (answerType, reason) = CallAudioRelay.readFrame(link.input) ?: throw EOFException()
            if (answerType != FRAME_ANSWER) throw IOException("Not a PulseLink call sound connection")
            return CallResult.fromReason(String(reason))
        }
    }
}
