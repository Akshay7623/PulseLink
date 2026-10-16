package com.example.call

import android.Manifest
import android.annotation.SuppressLint
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import android.media.audiofx.AcousticEchoCanceler
import android.media.audiofx.NoiseSuppressor
import android.os.Build
import android.os.Process
import android.provider.Settings
import android.util.Log
import androidx.core.content.ContextCompat
import com.example.model.CallResult
import com.example.model.CallState
import com.example.network.PulseClient
import com.example.service.PulseAccessibilityService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.EOFException
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference
import kotlin.concurrent.thread
import kotlin.math.abs

/**
 * What the call sound relay is doing on the device the user talks on
 */
data class RelayState(
    val status: Status = Status.OFF,
    val message: String = "", // how the last attempt ended, for the user to read
    val viaBluetooth: Boolean = false // the sound travels over Bluetooth rather than Wi-Fi, while ACTIVE
) {
    enum class Status { OFF, CONNECTING, ACTIVE }
}

/**
 * Carries the sound of a phone call between the device the call is on (the host) and the device
 * the user talks on. Android lets no ordinary app tap a call, so the host keeps the call on its
 * loudspeaker, listens to it with its microphone, and plays the user's voice out loud for the call
 * to pick up. Expect echo and a short delay. Between the two devices the sound travels over Wi-Fi,
 * or over Bluetooth when they are paired there and the user asked for it.
 */
class CallAudioRelay(
    private val context: Context,
    private val callController: CallController,
    private val pulseClient: PulseClient,
    private val bluetoothLink: BluetoothSoundLink
) {
    private val prefs = context.getSharedPreferences("pulselink_prefs", Context.MODE_PRIVATE)
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val scope = CoroutineScope(Dispatchers.IO + Job())

    private val _state = MutableStateFlow(RelayState())
    val state: StateFlow<RelayState> = _state.asStateFlow()

    @Volatile private var hostLink: AudioLink? = null
    @Volatile private var clientLink: AudioLink? = null
    @Volatile private var stopRequested = false

    /** Whether this device relays the sound of its own calls to a paired device that asks for it. */
    var isHostEnabled: Boolean
        get() = prefs.getBoolean(KEY_RELAY_CALL_SOUND, false)
        set(value) {
            prefs.edit().putBoolean(KEY_RELAY_CALL_SOUND, value).apply()
            syncBluetoothHost()
        }

    /** Whether calls the user starts or answers from here bring their sound along. */
    var isClientEnabled: Boolean
        get() = prefs.getBoolean(KEY_HEAR_CALLS_HERE, false)
        set(value) = prefs.edit().putBoolean(KEY_HEAR_CALLS_HERE, value).apply()

    /** Play the call through this device's loudspeaker instead of its earpiece. */
    var isLoudspeakerOn: Boolean = false
        set(value) {
            field = value
            if (_state.value.status == RelayState.Status.ACTIVE) applyLoudspeaker(value)
        }

    fun hasMicPermission(): Boolean {
        return ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
    }

    // Android 10 and later mute the microphone of ordinary apps during a call; an accessibility
    // service is the one kind of app that keeps it
    fun isAccessibilityOn(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return true
        val enabled = Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES).orEmpty()
        val mine = ComponentName(context, PulseAccessibilityService::class.java)
        return enabled.split(':').any { ComponentName.unflattenFromString(it) == mine }
    }

    /**
     * @return why this device will not relay its call sound right now, or CallResult.OK
     */
    fun hostRefusal(): CallResult = when {
        !callController.isEnabled -> CallResult.DISABLED
        !callController.hasPermission() -> CallResult.NO_PERMISSION
        !isHostEnabled -> CallResult.RELAY_OFF
        !hasMicPermission() || !isAccessibilityOn() -> CallResult.RELAY_NOT_READY
        else -> CallResult.OK
    }

    /**
     * Has this device accept the call sound connection over Bluetooth as well as over Wi-Fi for as
     * long as it relays its calls. Worth calling again whenever Bluetooth or its permission may
     * have changed.
     */
    fun syncBluetoothHost() {
        if (isHostEnabled) {
            bluetoothLink.startHost(refusal = ::hostRefusal, onJoined = { runHost(it) })
        } else {
            bluetoothLink.stopHost()
        }
    }

    /**
     * Relays the call on this device over an accepted connection, until the call or the connection
     * ends. The microphone is passed on only while a call is in progress.
     * @param inCall whether a call is in progress on this device
     */
    fun runHost(
        link: AudioLink,
        inCall: () -> Boolean = { callController.callState() == CallState.ONGOING }
    ) {
        // The newest request wins: the user may be reconnecting
        hostLink?.close()
        hostLink = link

        val musicVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
        try {
            // The call has to hear what is played here, so play it as loud as the device goes
            setMusicVolume(audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC))

            var lastInCall = System.currentTimeMillis()
            var hadCall = false
            val nowInCall = AtomicBoolean(false)
            val lastRemoteVoice = AtomicLong(0)

            relay(
                link = link,
                // The one source an accessibility service may record while a call is on
                recordSource = MediaRecorder.AudioSource.VOICE_RECOGNITION,
                playUsage = AudioAttributes.USAGE_MEDIA,
                // Cleaning up the microphone would strip out the very loudspeaker sound being relayed
                cleanMicrophone = false,
                keepGoing = {
                    val now = System.currentTimeMillis()
                    val isInCall = inCall()
                    nowInCall.set(isInCall)
                    if (isInCall) {
                        lastInCall = now
                        hadCall = true
                    }
                    when {
                        // A call that was only just dialled or answered gets a moment to show up
                        !hadCall && now - lastInCall > NO_CALL_GRACE_MS -> CallResult.NO_CALL.reason
                        hadCall && now - lastInCall > CALL_OVER_GRACE_MS -> REASON_CALL_ENDED
                        else -> null
                    }
                },
                // The user's own voice coming out of the loudspeaker would travel straight back to them
                muteCapture = { !nowInCall.get() || System.currentTimeMillis() - lastRemoteVoice.get() < ECHO_HOLD_MS },
                mutePlayback = { !nowInCall.get() },
                onRemoteAudio = { pcm, length ->
                    if (loudness(pcm, length) > VOICE_LEVEL) lastRemoteVoice.set(System.currentTimeMillis())
                }
            )
        } finally {
            setMusicVolume(musicVolume)
            if (hostLink === link) hostLink = null
        }
    }

    /**
     * Connects this device to the sound of the call on a paired device, so the user can hear it and
     * talk. Does nothing when a connection is already up.
     * @param peerBluetoothName the paired device's Bluetooth name as it reports it, to find it
     * among this device's paired Bluetooth devices
     */
    fun startClient(
        ip: String,
        port: Int,
        peerName: String,
        pairingKey: ByteArray?,
        peerBluetoothName: String? = null,
        onFinished: () -> Unit = {}
    ) {
        if (_state.value.status != RelayState.Status.OFF) return
        stopRequested = false
        _state.value = RelayState(RelayState.Status.CONNECTING)

        scope.launch {
            var message = "Call sound ended"
            try {
                if (!hasMicPermission()) {
                    message = "Allow the Microphone permission to talk on this phone"
                    return@launch
                }
                var link = if (bluetoothLink.isPreferred && pairingKey != null) {
                    bluetoothLink.connect(peerBluetoothName, pairingKey)
                } else null
                if (link == null) {
                    // Not reached over Bluetooth, or refused there: Wi-Fi gives the answer the user gets to read
                    val stream = pulseClient.openAudioStream(ip, port, pairingKey)
                    link = stream.link
                    if (link == null) {
                        message = refusalMessage(stream.result, peerName)
                        return@launch
                    }
                }
                clientLink = link

                audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
                applyLoudspeaker(isLoudspeakerOn)
                _state.value = RelayState(RelayState.Status.ACTIVE, viaBluetooth = link.isBluetooth)

                val endedBy = relay(
                    link = link,
                    recordSource = MediaRecorder.AudioSource.VOICE_COMMUNICATION,
                    playUsage = AudioAttributes.USAGE_VOICE_COMMUNICATION,
                    // Takes this device's own playback back out of what its microphone hears
                    cleanMicrophone = true,
                    keepGoing = { if (stopRequested) REASON_STOPPED else null },
                    muteCapture = { false },
                    mutePlayback = { false },
                    onRemoteAudio = { _, _ -> }
                )
                message = when (endedBy) {
                    CallResult.NO_CALL.reason -> "No call in progress on $peerName"
                    REASON_CALL_ENDED -> "Call ended on $peerName"
                    else -> "Call sound ended"
                }
            } catch (e: Exception) {
                Log.e(TAG, "Call sound relay failed", e)
                message = "Call sound stopped: connection to $peerName lost"
            } finally {
                clientLink = null
                audioManager.mode = AudioManager.MODE_NORMAL
                applyLoudspeaker(false)
                _state.value = RelayState(RelayState.Status.OFF, message)
                onFinished()
            }
        }
    }

    fun stopClient() {
        stopRequested = true
        clientLink?.close()
    }

    private fun refusalMessage(result: CallResult, peerName: String): String = when (result) {
        CallResult.NOT_PAIRED -> "Enter $peerName's pairing code first"
        CallResult.DISABLED -> "Turn on \"Allow Remote Calls\" in PulseLink settings on $peerName"
        CallResult.NO_PERMISSION -> "$peerName has not given PulseLink the Phone permission"
        CallResult.RELAY_OFF -> "Turn on \"Relay Call Sound\" in PulseLink settings on $peerName"
        CallResult.RELAY_NOT_READY -> "$peerName still needs the Microphone permission and PulseLink turned on in Accessibility"
        CallResult.UNSUPPORTED -> "Update PulseLink on $peerName to relay call sound"
        else -> "Could not reach $peerName for the call sound"
    }

    @Suppress("DEPRECATION")
    private fun applyLoudspeaker(on: Boolean) {
        try {
            audioManager.isSpeakerphoneOn = on
        } catch (e: Exception) {
            Log.e(TAG, "Error switching loudspeaker", e)
        }
    }

    private fun setMusicVolume(volume: Int) {
        try {
            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, volume, 0)
        } catch (e: Exception) {
            Log.e(TAG, "Error setting volume", e)
        }
    }

    /**
     * Sends what the microphone hears and plays what arrives, until either side ends it.
     * @param keepGoing checked twice a second: null to carry on, or the reason to end with
     * @return the reason it ended, empty when the connection simply closed
     */
    @SuppressLint("MissingPermission")
    private fun relay(
        link: AudioLink,
        recordSource: Int,
        playUsage: Int,
        cleanMicrophone: Boolean,
        keepGoing: () -> String?,
        muteCapture: () -> Boolean,
        mutePlayback: () -> Boolean,
        onRemoteAudio: (ByteArray, Int) -> Unit
    ): String {
        val input = link.input
        val output = link.output

        val recordBuffer = maxOf(
            AudioRecord.getMinBufferSize(SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT),
            FRAME_BYTES * 4
        )
        val record = AudioRecord(recordSource, SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, recordBuffer)
        val track = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder().setUsage(playUsage).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(SAMPLE_RATE)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setBufferSizeInBytes(
                maxOf(
                    AudioTrack.getMinBufferSize(SAMPLE_RATE, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT),
                    FRAME_BYTES * 6
                )
            )
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()
        val echoCanceler = if (cleanMicrophone && AcousticEchoCanceler.isAvailable()) {
            AcousticEchoCanceler.create(record.audioSessionId)
        } else null
        val noiseSuppressor = if (cleanMicrophone && NoiseSuppressor.isAvailable()) {
            NoiseSuppressor.create(record.audioSessionId)
        } else null

        val endedBy = AtomicReference("")
        val running = AtomicBoolean(true)
        val lastHeard = AtomicLong(System.currentTimeMillis())

        try {
            echoCanceler?.enabled = true
            noiseSuppressor?.enabled = true
            record.startRecording()
            track.play()

            val player = thread(name = "PulseRelayPlayer") {
                Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO)
                try {
                    while (running.get()) {
                        val (type, payload) = readFrame(input) ?: break
                        lastHeard.set(System.currentTimeMillis())
                        if (type == FRAME_END) {
                            endedBy.set(String(payload))
                            break
                        }
                        onRemoteAudio(payload, payload.size)
                        // Frames that piled up behind a network hiccup are dropped, so the delay does not grow
                        val isStale = input.available() > FRAME_BYTES * MAX_QUEUED_FRAMES
                        if (!isStale && !mutePlayback()) track.write(payload, 0, payload.size)
                    }
                } catch (e: IOException) {
                    // The other side went away
                } finally {
                    running.set(false)
                }
            }

            Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO)
            val pcm = ByteArray(FRAME_BYTES)
            val silence = ByteArray(FRAME_BYTES)
            var frames = 0
            while (running.get()) {
                val read = record.read(pcm, 0, FRAME_BYTES)
                if (read <= 0) break

                if (frames++ % FRAMES_PER_CHECK == 0) {
                    // A Bluetooth connection has no read timeout, so a peer gone quiet is noticed here
                    if (System.currentTimeMillis() - lastHeard.get() > AudioLink.SILENT_PEER_TIMEOUT_MS) break

                    val reason = keepGoing()
                    if (reason != null) {
                        endedBy.set(reason)
                        writeFrame(output, FRAME_END, reason.toByteArray())
                        break
                    }
                }
                writeFrame(output, FRAME_AUDIO, if (muteCapture()) silence else pcm, read)
            }
            running.set(false)
            link.close()
            player.join(1000)
        } catch (e: IOException) {
            // The other side went away
        } finally {
            running.set(false)
            link.close()
            runCatching { record.stop() }
            record.release()
            runCatching { track.stop() }
            track.release()
            echoCanceler?.release()
            noiseSuppressor?.release()
        }
        return endedBy.get()
    }

    companion object {
        private const val TAG = "CallAudioRelay"
        private const val KEY_RELAY_CALL_SOUND = "relay_call_sound"
        private const val KEY_HEAR_CALLS_HERE = "hear_calls_here"

        const val SAMPLE_RATE = 16_000
        const val FRAME_BYTES = 640 // 20 ms of 16-bit mono sound
        const val FRAME_AUDIO = 1
        const val FRAME_END = 2

        private const val MAX_FRAME_BYTES = 4096
        private const val MAX_QUEUED_FRAMES = 8
        private const val FRAMES_PER_CHECK = 25
        private const val NO_CALL_GRACE_MS = 8000L
        private const val CALL_OVER_GRACE_MS = 1500L
        private const val ECHO_HOLD_MS = 350L
        private const val VOICE_LEVEL = 1500
        private const val REASON_STOPPED = "stopped"
        private const val REASON_CALL_ENDED = "call_ended"

        fun writeFrame(output: OutputStream, type: Int, payload: ByteArray, length: Int = payload.size) {
            val frame = ByteArray(3 + length)
            frame[0] = type.toByte()
            frame[1] = (length shr 8).toByte()
            frame[2] = length.toByte()
            System.arraycopy(payload, 0, frame, 3, length)
            output.write(frame)
            output.flush()
        }

        /**
         * @return the frame's type and content, or null when the stream ended between frames
         */
        fun readFrame(input: InputStream): Pair<Int, ByteArray>? {
            val type = input.read()
            if (type == -1) return null
            val high = input.read()
            val low = input.read()
            if (high == -1 || low == -1) throw EOFException()

            val length = (high shl 8) or low
            if (length > MAX_FRAME_BYTES) throw IOException("Frame of $length bytes is too large")
            val payload = ByteArray(length)
            var filled = 0
            while (filled < length) {
                val read = input.read(payload, filled, length - filled)
                if (read == -1) throw EOFException()
                filled += read
            }
            return type to payload
        }

        /** @return the loudest sample, 0 to 32767, of 16-bit little-endian sound */
        fun loudness(pcm: ByteArray, length: Int = pcm.size): Int {
            var peak = 0
            var i = 0
            while (i + 1 < length) {
                val sample = (pcm[i + 1].toInt() shl 8) or (pcm[i].toInt() and 0xFF)
                peak = maxOf(peak, abs(sample))
                i += 2
            }
            return peak.coerceAtMost(Short.MAX_VALUE.toInt())
        }
    }
}
