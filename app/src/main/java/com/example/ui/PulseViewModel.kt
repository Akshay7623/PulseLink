package com.example.ui

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.PulseApplication
import com.example.data.local.entity.NotificationEntity
import com.example.data.local.entity.PeerEntity
import com.example.model.DeviceTelemetry
import com.example.model.NotificationPayload
import com.example.model.PeerDevice
import com.example.model.PeerNotificationFeed
import com.example.model.RemoteActionRequest
import com.example.model.TelemetryStreamInterval
import com.example.network.NetworkResult
import com.example.notification.DeviceNotificationStore
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

    private var telemetryStreamJob: Job? = null
    private var localTelemetryTickerJob: Job? = null

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

        // Auto-start discovery scan
        startScanning()
    }

    fun refreshLocalTelemetry() {
        _localTelemetry.value = telemetryCollector.collectTelemetry(_customDeviceName.value)
        // Picks up the user granting or revoking Notification access in system Settings
        _hasNotificationAccess.value = DeviceNotificationStore.hasNotificationAccess(app)
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
        if (_selectedPeer.value?.ip != newIp) {
            _peerNotificationFeed.value = null
        }
    }

    private suspend fun refreshPeerNotifications(ip: String, port: Int) {
        val result = pulseClient.fetchNotifications(ip, port)
        // Drop responses that arrive after the user switched to another peer
        if (result is NetworkResult.Success && _selectedPeer.value?.ip == ip) {
            _peerNotificationFeed.value = result.data
        }
    }

    fun toggleNotificationSharing(enabled: Boolean) {
        DeviceNotificationStore.setSharingEnabled(app, enabled)
        _isSharingNotifications.value = enabled
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
