package com.example.ui

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.PulseApplication
import com.example.call.BluetoothSoundState
import com.example.call.RelayState
import com.example.data.local.entity.NotificationEntity
import com.example.data.local.entity.PeerEntity
import com.example.model.CallResult
import com.example.model.DeviceNotification
import com.example.model.DeviceTelemetry
import com.example.model.NotificationPayload
import com.example.model.PeerDevice
import com.example.model.PeerNotificationFeed
import com.example.model.PeerVideoLibrary
import com.example.model.RemoteActionRequest
import com.example.model.TelemetryStreamInterval
import com.example.model.VideoAccess
import com.example.model.VideoSort
import com.example.model.VideoSortField
import com.example.network.NetworkResult
import com.example.notification.DeviceNotificationStore
import com.example.security.Pairing
import com.example.service.CallAudioService
import com.example.service.PulseForegroundService
import com.example.util.JsonUtils
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.json.JSONObject

class PulseViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as PulseApplication
    private val repository = app.repository
    private val telemetryCollector = app.telemetryCollector
    private val pulseServer = app.pulseServer
    private val pulseClient = app.pulseClient
    private val pulseDiscovery = app.pulseDiscovery

    val isServerRunning: StateFlow<Boolean> = pulseServer.isServerRunning
    val serverPort: StateFlow<Int> = pulseServer.serverPort
    val serverLogs: StateFlow<List<String>> = pulseServer.serverLog
    val isScanning: StateFlow<Boolean> = pulseDiscovery.isScanning
    val discoveredPeers: StateFlow<List<PeerDevice>> = pulseDiscovery.discoveredPeers

    val notificationsList: StateFlow<List<NotificationEntity>> = repository.allNotifications
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val savedPeers: StateFlow<List<PeerEntity>> = repository.allPeers
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _localTelemetry = MutableStateFlow(telemetryCollector.collectTelemetry())
    val localTelemetry: StateFlow<DeviceTelemetry> = _localTelemetry.asStateFlow()

    private val _selectedPeer = MutableStateFlow<PeerDevice?>(null)
    val selectedPeer: StateFlow<PeerDevice?> = _selectedPeer.asStateFlow()

    private val _peerTelemetry = MutableStateFlow<DeviceTelemetry?>(null)
    val peerTelemetry: StateFlow<DeviceTelemetry?> = _peerTelemetry.asStateFlow()

    private val _isFetchingPeerTelemetry = MutableStateFlow(false)
    val isFetchingPeerTelemetry: StateFlow<Boolean> = _isFetchingPeerTelemetry.asStateFlow()

    private val _isStreamingTelemetry = MutableStateFlow(false)
    val isStreamingTelemetry: StateFlow<Boolean> = _isStreamingTelemetry.asStateFlow()

    private val _streamIntervalSeconds = MutableStateFlow(
        application.getSharedPreferences("pulselink_prefs", Context.MODE_PRIVATE)
            .getInt("stream_interval_seconds", TelemetryStreamInterval.DEFAULT_SECONDS)
    )
    val streamIntervalSeconds: StateFlow<Int> = _streamIntervalSeconds.asStateFlow()

    private val _isSendingNotification = MutableStateFlow(false)
    val isSendingNotification: StateFlow<Boolean> = _isSendingNotification.asStateFlow()

    private val _lastSendResult = MutableStateFlow<String?>(null)
    val lastSendResult: StateFlow<String?> = _lastSendResult.asStateFlow()

    private val _peerLatencyMs = MutableStateFlow<Long?>(null)
    val peerLatencyMs: StateFlow<Long?> = _peerLatencyMs.asStateFlow()

    private val _customDeviceName = MutableStateFlow("")
    val customDeviceName: StateFlow<String> = _customDeviceName.asStateFlow()

    private val _isBackgroundServiceRunning = MutableStateFlow(false)
    val isBackgroundServiceRunning: StateFlow<Boolean> = _isBackgroundServiceRunning.asStateFlow()

    private val _peerNotificationFeed = MutableStateFlow<PeerNotificationFeed?>(null)
    val peerNotificationFeed: StateFlow<PeerNotificationFeed?> = _peerNotificationFeed.asStateFlow()

    private val _isSharingNotifications = MutableStateFlow(DeviceNotificationStore.isSharingEnabled(application))
    val isSharingNotifications: StateFlow<Boolean> = _isSharingNotifications.asStateFlow()

    private val _hasNotificationAccess = MutableStateFlow(DeviceNotificationStore.hasNotificationAccess(application))
    val hasNotificationAccess: StateFlow<Boolean> = _hasNotificationAccess.asStateFlow()

    private val _isPeerAlertsEnabled = MutableStateFlow(DeviceNotificationStore.isPeerAlertsEnabled(application))
    val isPeerAlertsEnabled: StateFlow<Boolean> = _isPeerAlertsEnabled.asStateFlow()

    private val _isRemoteCallsAllowed = MutableStateFlow(app.callController.isEnabled)
    val isRemoteCallsAllowed: StateFlow<Boolean> = _isRemoteCallsAllowed.asStateFlow()

    private val _hasPhonePermission = MutableStateFlow(app.callController.hasPermission())
    val hasPhonePermission: StateFlow<Boolean> = _hasPhonePermission.asStateFlow()

    // This phone's own pairing code, to be typed into the phone that controls its calls
    val myPairingCode: String = Pairing.format(app.pairing.myCode)

    private val _partnerPairingCode = MutableStateFlow(app.pairing.partnerCode)
    val partnerPairingCode: StateFlow<String> = _partnerPairingCode.asStateFlow()

    private val _isPairingCodeNeeded = MutableStateFlow(false)
    val isPairingCodeNeeded: StateFlow<Boolean> = _isPairingCodeNeeded.asStateFlow()

    val callSoundState: StateFlow<RelayState> = app.callAudioRelay.state

    private val _isHearingCallsHere = MutableStateFlow(app.callAudioRelay.isClientEnabled)
    val isHearingCallsHere: StateFlow<Boolean> = _isHearingCallsHere.asStateFlow()

    private val _isCallLoudspeakerOn = MutableStateFlow(app.callAudioRelay.isLoudspeakerOn)
    val isCallLoudspeakerOn: StateFlow<Boolean> = _isCallLoudspeakerOn.asStateFlow()

    private val _isRelayingCallSound = MutableStateFlow(app.callAudioRelay.isHostEnabled)
    val isRelayingCallSound: StateFlow<Boolean> = _isRelayingCallSound.asStateFlow()

    private val _hasMicPermission = MutableStateFlow(app.callAudioRelay.hasMicPermission())
    val hasMicPermission: StateFlow<Boolean> = _hasMicPermission.asStateFlow()

    private val _isAccessibilityOn = MutableStateFlow(app.callAudioRelay.isAccessibilityOn())
    val isAccessibilityOn: StateFlow<Boolean> = _isAccessibilityOn.asStateFlow()

    private val _hasBluetoothPermission = MutableStateFlow(app.bluetoothSoundLink.hasPermission())
    val hasBluetoothPermission: StateFlow<Boolean> = _hasBluetoothPermission.asStateFlow()

    // Whether the call sound asked for from this phone would travel over Bluetooth, and why not
    private val _bluetoothSound = MutableStateFlow(app.bluetoothSoundLink.state(null))
    val bluetoothSound: StateFlow<BluetoothSoundState> = _bluetoothSound.asStateFlow()

    private val _isSharingVideos = MutableStateFlow(app.videoLibrary.isSharingEnabled)
    val isSharingVideos: StateFlow<Boolean> = _isSharingVideos.asStateFlow()

    private val _hasVideoPermission = MutableStateFlow(app.videoLibrary.hasPermission())
    val hasVideoPermission: StateFlow<Boolean> = _hasVideoPermission.asStateFlow()

    private val _peerVideos = MutableStateFlow(PeerVideoLibrary())
    val peerVideos: StateFlow<PeerVideoLibrary> = _peerVideos.asStateFlow()

    private val _videoSort = MutableStateFlow(
        application.getSharedPreferences("pulselink_prefs", Context.MODE_PRIVATE).let { prefs ->
            VideoSort(
                sortBy = VideoSortField.entries.firstOrNull { it.name == prefs.getString("video_sort_field", null) }
                    ?: VideoSortField.DATE,
                ascending = prefs.getBoolean("video_sort_ascending", false)
            )
        }
    )
    val videoSort: StateFlow<VideoSort> = _videoSort.asStateFlow()

    private var telemetryStreamJob: Job? = null
    private var localTelemetryTickerJob: Job? = null
    private var peerVideosJob: Job? = null

    init {
        val prefs = application.getSharedPreferences("pulselink_prefs", Context.MODE_PRIVATE)
        val savedName = prefs.getString("custom_device_name", null) ?: telemetryCollector.getDeviceDisplayName()
        _customDeviceName.value = savedName

        // Periodically refresh local telemetry
        localTelemetryTickerJob = viewModelScope.launch {
            while (isActive) {
                refreshLocalTelemetry()
                delay(3000)
            }
        }

        // The peer pushes a change the moment its notifications change, so the list stays live
        viewModelScope.launch {
            pulseServer.peerNotificationChanges.collect { ip ->
                val peer = _selectedPeer.value
                if (peer?.ip == ip) refreshPeerNotifications(peer.ip, peer.port)
            }
        }

        // Auto-start discovery scan
        startScanning()
    }

    fun refreshLocalTelemetry() {
        _localTelemetry.value = telemetryCollector.collectTelemetry(_customDeviceName.value)
        // Picks up the user granting or revoking Notification access in system Settings
        _hasNotificationAccess.value = DeviceNotificationStore.hasNotificationAccess(app)
        _hasPhonePermission.value = app.callController.hasPermission()
        _hasMicPermission.value = app.callAudioRelay.hasMicPermission()
        _isAccessibilityOn.value = app.callAudioRelay.isAccessibilityOn()
        _hasBluetoothPermission.value = app.bluetoothSoundLink.hasPermission()
        _bluetoothSound.value = app.bluetoothSoundLink.state(_peerTelemetry.value?.bluetoothSoundHost)
        _hasVideoPermission.value = app.videoLibrary.hasPermission()
        // Picks up the Nearby devices permission being granted while "Relay Call Sound" is already on
        app.callAudioRelay.syncBluetoothHost()
    }

    fun updateDeviceName(newName: String) {
        val trimmed = newName.trim()
        if (trimmed.isNotBlank()) {
            _customDeviceName.value = trimmed
            app.getSharedPreferences("pulselink_prefs", Context.MODE_PRIVATE)
                .edit()
                .putString("custom_device_name", trimmed)
                .apply()

            refreshLocalTelemetry()
            app.startPulseServer(serverPort.value, trimmed)
        }
    }

    fun selectPeer(peer: PeerDevice) {
        clearNotificationFeedIfPeerChanged(peer.ip)
        _selectedPeer.value = peer
        fetchPeerTelemetry(peer.ip, peer.port)
    }

    fun selectPeerByIp(ip: String, port: Int = 8765, name: String = "Manual Peer") {
        val peer = PeerDevice(id = ip, name = name, ip = ip, port = port, isOnline = true)
        clearNotificationFeedIfPeerChanged(ip)
        _selectedPeer.value = peer
        fetchPeerTelemetry(ip, port)
    }

    // Never show one device's notifications under another device's name
    private fun clearNotificationFeedIfPeerChanged(newIp: String) {
        val previous = _selectedPeer.value
        if (previous?.ip != newIp) {
            _peerNotificationFeed.value = null
            peerVideosJob?.cancel()
            _peerVideos.value = PeerVideoLibrary()
            // Alerts follow the selected peer, so the one being left should stop pushing here
            if (previous != null) {
                viewModelScope.launch {
                    pulseClient.unsubscribeFromNotifications(previous.ip, previous.port, serverPort.value)
                }
            }
        }
    }

    private suspend fun refreshPeerNotifications(ip: String, port: Int) {
        val result = pulseClient.fetchNotifications(ip, port)
        // Drop responses that arrive after the user switched to another peer
        if (result is NetworkResult.Success && _selectedPeer.value?.ip == ip) {
            _peerNotificationFeed.value = result.data
        }
    }

    /**
     * Clears one of the peer's notifications on the peer itself, after the user swiped it away here.
     */
    fun dismissPeerNotification(item: DeviceNotification) {
        val peer = _selectedPeer.value ?: return
        // Gone from this screen right away; the peer's next push confirms it
        _peerNotificationFeed.value = _peerNotificationFeed.value?.let { feed ->
            feed.copy(notifications = feed.notifications.filterNot { it.key == item.key })
        }
        app.notificationManager.cancelPeerDeviceNotification(peer.ip, item.key)

        viewModelScope.launch {
            val result = pulseClient.dismissNotification(peer.ip, peer.port, item.key)
            if (result !is NetworkResult.Success || !result.data) {
                _lastSendResult.value = "❌ Could not remove it on ${peer.name}"
                refreshPeerNotifications(peer.ip, peer.port)
            }
        }
    }

    /**
     * Answers one of the peer's chats from here; the peer sends it through the chat app's own reply box.
     */
    fun replyToPeerNotification(item: DeviceNotification, text: String) {
        val peer = _selectedPeer.value ?: return
        val reply = text.trim()
        if (reply.isEmpty()) return

        viewModelScope.launch {
            val result = pulseClient.replyToNotification(peer.ip, peer.port, item.key, reply)
            if (result is NetworkResult.Success && result.data) {
                _lastSendResult.value = "💬 Reply sent to ${item.title.ifBlank { item.appName }}"
            } else {
                _lastSendResult.value = "❌ Could not reply on ${peer.name}"
                refreshPeerNotifications(peer.ip, peer.port)
            }
        }
    }

    /**
     * Has the peer call a number from its SIM. The call happens on the peer; with "Hear and talk on
     * this phone" on, its sound is relayed here.
     */
    fun dialOnPeer(number: String, speaker: Boolean) {
        val peer = _selectedPeer.value ?: return
        viewModelScope.launch {
            // A Bluetooth headset on the peer carries the call by itself. The relay is for a call on
            // the peer's loudspeaker, which it listens to, so such a call has to start there.
            val onHeadset = peerHasHeadset(peer) && !speaker
            val relaySound = _isHearingCallsHere.value && !onHeadset
            val result = pulseClient.dialNumber(peer.ip, peer.port, number, speaker || relaySound, app.pairing.partnerKey())
            reportCallResult(peer, result, "📞 ${peer.name} is calling $number")
            if (relaySound && result.isOk()) startCallSound()
        }
    }

    /**
     * Asks the peer again which Bluetooth headset, if any, its calls would use.
     */
    fun refreshPeerHeadset() {
        val peer = _selectedPeer.value ?: return
        viewModelScope.launch { peerHasHeadset(peer) }
    }

    // Asked afresh each time: earbuds come and go without the peer telling anyone
    private suspend fun peerHasHeadset(peer: PeerDevice): Boolean {
        val result = pulseClient.fetchTelemetry(peer.ip, peer.port)
        if (result is NetworkResult.Success && _selectedPeer.value?.ip == peer.ip) _peerTelemetry.value = result.data
        return result is NetworkResult.Success && !result.data.bluetoothHeadset.isNullOrEmpty()
    }

    fun answerCallOnPeer() {
        val peer = _selectedPeer.value ?: return
        viewModelScope.launch {
            val relaySound = _isHearingCallsHere.value && !peerHasHeadset(peer)
            val result = pulseClient.answerCall(peer.ip, peer.port, app.pairing.partnerKey())
            reportCallResult(peer, result, "📞 Call answered on ${peer.name}")
            if (relaySound && result.isOk()) startCallSound()
        }
    }

    /**
     * Hangs up the peer's call in progress, or declines the one ringing there.
     */
    fun endCallOnPeer() {
        val peer = _selectedPeer.value ?: return
        viewModelScope.launch {
            val result = pulseClient.endCall(peer.ip, peer.port, app.pairing.partnerKey())
            reportCallResult(peer, result, "📞 Call ended on ${peer.name}")
            if (result.isOk()) stopCallSound()
        }
    }

    private fun NetworkResult<CallResult>.isOk() = this is NetworkResult.Success && data == CallResult.OK

    private fun reportCallResult(peer: PeerDevice, result: NetworkResult<CallResult>, successMessage: String) {
        _lastSendResult.value = when (result) {
            is NetworkResult.Error -> "❌ Could not reach ${peer.name}"
            is NetworkResult.Success -> when (result.data) {
                CallResult.OK -> successMessage
                CallResult.DISABLED -> "❌ Turn on \"Allow Remote Calls\" in PulseLink settings on ${peer.name}"
                CallResult.NO_PERMISSION -> "❌ ${peer.name} has not given PulseLink the Phone permission"
                CallResult.INVALID_NUMBER -> "❌ Enter a phone number of 3 to 15 digits"
                CallResult.NO_CALL -> "No call to act on at ${peer.name} right now"
                CallResult.NOT_PAIRED -> "❌ Enter the pairing code shown in PulseLink settings on ${peer.name}"
                CallResult.UNSUPPORTED -> "❌ Update PulseLink on ${peer.name} to control its calls"
                CallResult.RELAY_OFF, CallResult.RELAY_NOT_READY, CallResult.FAILED -> "❌ ${peer.name} could not do that"
            }
        }
        // Ask for the code right away rather than leaving the user to find where it goes
        if (result is NetworkResult.Success && result.data == CallResult.NOT_PAIRED) _isPairingCodeNeeded.value = true
    }

    /**
     * Brings the sound of the peer's call to this phone. Has to be started while the app is on screen.
     */
    fun startCallSound() {
        val peer = _selectedPeer.value ?: return
        CallAudioService.start(app, peer.ip, peer.port, peer.name, _peerTelemetry.value?.bluetoothSoundHost)
    }

    fun stopCallSound() {
        CallAudioService.stop(app)
    }

    // Whether calls started or answered from this phone bring their sound along
    fun toggleHearCallsHere(enabled: Boolean) {
        app.callAudioRelay.isClientEnabled = enabled
        _isHearingCallsHere.value = enabled
        if (!enabled) stopCallSound()
    }

    fun toggleCallLoudspeaker(enabled: Boolean) {
        app.callAudioRelay.isLoudspeakerOn = enabled
        _isCallLoudspeakerOn.value = enabled
    }

    // Whether the call sound comes over Bluetooth when the two phones are paired there, with Wi-Fi as the fallback
    fun toggleBluetoothSound(enabled: Boolean) {
        app.bluetoothSoundLink.isPreferred = enabled
        refreshLocalTelemetry()
    }

    /**
     * Remembers the pairing code of the phone whose calls are controlled from here.
     */
    fun setPartnerPairingCode(code: String) {
        app.pairing.partnerCode = code
        _partnerPairingCode.value = app.pairing.partnerCode
        _isPairingCodeNeeded.value = false
    }

    fun askForPairingCode(show: Boolean) {
        _isPairingCodeNeeded.value = show
    }

    /**
     * Asks the connected device again which videos it has. What is showing stays until the answer
     * comes, so opening the tab again does not make the list flicker.
     */
    fun refreshPeerVideos() {
        val peer = _selectedPeer.value ?: return
        peerVideosJob?.cancel()
        peerVideosJob = viewModelScope.launch {
            _peerVideos.value = _peerVideos.value.copy(isLoading = true)
            val session = pulseClient.openVideoSession(peer.ip, peer.port, app.pairing.partnerKey())
            if (session.access != VideoAccess.OK) {
                _peerVideos.value = PeerVideoLibrary(access = session.access)
                return@launch
            }
            _peerVideos.value = when (val result = pulseClient.fetchVideos(peer.ip, peer.port, session.token)) {
                is NetworkResult.Success -> PeerVideoLibrary(
                    access = VideoAccess.OK,
                    token = session.token,
                    videos = result.data
                )
                is NetworkResult.Error -> PeerVideoLibrary(access = VideoAccess.FAILED)
            }
        }
    }

    fun setVideoSort(sort: VideoSort) {
        _videoSort.value = sort
        app.getSharedPreferences("pulselink_prefs", Context.MODE_PRIVATE)
            .edit()
            .putString("video_sort_field", sort.sortBy.name)
            .putBoolean("video_sort_ascending", sort.ascending)
            .apply()
    }

    // Lets a paired device watch the videos on this phone
    fun toggleVideoSharing(enabled: Boolean) {
        app.videoLibrary.isSharingEnabled = enabled
        _isSharingVideos.value = enabled
        if (!enabled) app.streamTokens.clear()
    }

    // Lets a paired device place, answer and end calls on this phone
    fun toggleRemoteCalls(enabled: Boolean) {
        app.callController.isEnabled = enabled
        _isRemoteCallsAllowed.value = enabled
    }

    // Lets a paired device hear and talk on this phone's calls
    fun toggleCallSoundRelay(enabled: Boolean) {
        app.callAudioRelay.isHostEnabled = enabled
        _isRelayingCallSound.value = enabled
    }

    fun toggleNotificationSharing(enabled: Boolean) {
        DeviceNotificationStore.setSharingEnabled(app, enabled)
        _isSharingNotifications.value = enabled
    }

    fun togglePeerAlerts(enabled: Boolean) {
        DeviceNotificationStore.setPeerAlertsEnabled(app, enabled)
        _isPeerAlertsEnabled.value = enabled

        val peer = _selectedPeer.value ?: return
        viewModelScope.launch {
            if (enabled) {
                pulseClient.subscribeToNotifications(peer.ip, peer.port, serverPort.value)
            } else {
                pulseClient.unsubscribeFromNotifications(peer.ip, peer.port, serverPort.value)
            }
        }
    }

    fun fetchPeerTelemetry(ip: String, port: Int = 8765) {
        viewModelScope.launch {
            _isFetchingPeerTelemetry.value = true
            when (val result = pulseClient.fetchTelemetry(ip, port)) {
                is NetworkResult.Success -> {
                    _peerTelemetry.value = result.data
                    _peerLatencyMs.value = result.latencyMs
                    // Update peer record in database
                    repository.saveOrUpdatePeer(
                        ip = ip,
                        name = result.data.deviceName,
                        model = result.data.deviceModel,
                        port = port,
                        telemetry = result.data
                    )
                    refreshPeerNotifications(ip, port)
                    // Ask the peer to push its notifications here as they arrive
                    if (_isPeerAlertsEnabled.value) {
                        pulseClient.subscribeToNotifications(ip, port, serverPort.value)
                    }
                }
                is NetworkResult.Error -> {
                    _peerLatencyMs.value = null
                    _lastSendResult.value = "Telemetry fetch failed: ${result.message}"
                }
            }
            _isFetchingPeerTelemetry.value = false
        }
    }

    fun toggleTelemetryStream(enabled: Boolean) {
        _isStreamingTelemetry.value = enabled
        telemetryStreamJob?.cancel()

        if (enabled) {
            val peer = _selectedPeer.value ?: return
            telemetryStreamJob = viewModelScope.launch {
                while (isActive && _isStreamingTelemetry.value) {
                    val currentPeer = _selectedPeer.value ?: break
                    val res = pulseClient.fetchTelemetry(currentPeer.ip, currentPeer.port)
                    if (res is NetworkResult.Success) {
                        _peerTelemetry.value = res.data
                        _peerLatencyMs.value = res.latencyMs
                        refreshPeerNotifications(currentPeer.ip, currentPeer.port)
                    }
                    delay(_streamIntervalSeconds.value * 1000L) // Stream update at the chosen interval
                }
            }
        }
    }

    fun setStreamInterval(seconds: Int) {
        val clamped = seconds.coerceIn(TelemetryStreamInterval.MIN_SECONDS, TelemetryStreamInterval.MAX_SECONDS)
        _streamIntervalSeconds.value = clamped
        app.getSharedPreferences("pulselink_prefs", Context.MODE_PRIVATE)
            .edit()
            .putInt("stream_interval_seconds", clamped)
            .apply()

        // Restart a running stream so the new interval applies right away
        if (_isStreamingTelemetry.value) {
            toggleTelemetryStream(true)
        }
    }

    fun sendNotification(
        targetIp: String,
        targetPort: Int = 8765,
        title: String,
        message: String,
        category: String = "ALERT",
        priority: String = "HIGH",
        vibrate: Boolean = true,
        playSound: Boolean = true,
        attachMyTelemetry: Boolean = true
    ) {
        viewModelScope.launch {
            _isSendingNotification.value = true
            _lastSendResult.value = null

            val myTelemetry = if (attachMyTelemetry) _localTelemetry.value else null
            val payload = NotificationPayload(
                senderDeviceId = _localTelemetry.value.ipAddress,
                senderDeviceName = _customDeviceName.value,
                senderIp = _localTelemetry.value.ipAddress,
                title = title.trim(),
                message = message.trim(),
                category = category,
                priority = priority,
                vibrate = vibrate,
                playSound = playSound,
                telemetrySnapshot = myTelemetry,
                timestamp = System.currentTimeMillis()
            )

            when (val result = pulseClient.sendNotification(targetIp, targetPort, payload)) {
                is NetworkResult.Success -> {
                    _lastSendResult.value = "✅ Delivered to $targetIp (${result.latencyMs}ms)"
                    // Save to local DB as SENT
                    repository.saveNotification(payload, "SENT")
                    // Save target to peers
                    repository.saveOrUpdatePeer(
                        ip = targetIp,
                        name = _selectedPeer.value?.name ?: "Target Device",
                        port = targetPort
                    )
                }
                is NetworkResult.Error -> {
                    _lastSendResult.value = "❌ Delivery Failed: ${result.message}"
                }
            }
            _isSendingNotification.value = false
        }
    }

    fun sendRemoteAction(
        targetIp: String,
        targetPort: Int = 8765,
        actionType: String,
        payload: String = ""
    ) {
        viewModelScope.launch {
            val req = RemoteActionRequest(
                actionType = actionType,
                senderName = _customDeviceName.value,
                payload = payload,
                timestamp = System.currentTimeMillis()
            )

            when (val result = pulseClient.sendAction(targetIp, targetPort, req)) {
                is NetworkResult.Success -> {
                    _lastSendResult.value = "⚡ ${result.data.message} (${result.latencyMs}ms)"
                    result.data.telemetry?.let {
                        _peerTelemetry.value = it
                    }
                }
                is NetworkResult.Error -> {
                    _lastSendResult.value = "❌ Action failed: ${result.message}"
                }
            }
        }
    }

    fun pingPeer(ip: String, port: Int = 8765) {
        viewModelScope.launch {
            when (val res = pulseClient.pingPeer(ip, port)) {
                is NetworkResult.Success -> {
                    _peerLatencyMs.value = res.latencyMs
                    _lastSendResult.value = "🟢 Peer '${res.data.first}' is reachable (${res.latencyMs}ms)"
                }
                is NetworkResult.Error -> {
                    _peerLatencyMs.value = null
                    _lastSendResult.value = "🔴 Peer unreachable: ${res.message}"
                }
            }
        }
    }

    fun startScanning() {
        pulseDiscovery.startDiscovery()
    }

    fun stopScanning() {
        pulseDiscovery.stopDiscovery()
    }

    fun toggleServer(enable: Boolean, port: Int = 8765) {
        if (enable) {
            pulseServer.startServer(port, _customDeviceName.value)
            pulseDiscovery.registerService(port, _customDeviceName.value)
        } else {
            pulseServer.stopServer()
            pulseDiscovery.unregisterService()
        }
    }

    fun toggleBackgroundService(enable: Boolean) {
        _isBackgroundServiceRunning.value = enable
        if (enable) {
            PulseForegroundService.startService(getApplication())
        } else {
            PulseForegroundService.stopService(getApplication())
        }
    }

    fun markNotificationRead(id: String) {
        viewModelScope.launch { repository.markAsRead(id) }
    }

    fun markAllNotificationsRead() {
        viewModelScope.launch { repository.markAllAsRead() }
    }

    fun deleteNotification(id: String) {
        viewModelScope.launch { repository.deleteNotification(id) }
    }

    fun clearAllNotifications() {
        viewModelScope.launch { repository.clearAllNotifications() }
    }

    fun toggleFavoritePeer(ip: String, isFavorite: Boolean) {
        viewModelScope.launch { repository.setPeerFavorite(ip, isFavorite) }
    }

    fun deletePeer(ip: String) {
        viewModelScope.launch { repository.removePeer(ip) }
    }

    fun clearSendStatus() {
        _lastSendResult.value = null
    }

    override fun onCleared() {
        super.onCleared()
        localTelemetryTickerJob?.cancel()
        telemetryStreamJob?.cancel()
        pulseDiscovery.stopDiscovery()
    }
}
