package com.example

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.Dialpad
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Radar
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material.icons.filled.WifiTethering
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.call.CallController
import com.example.media.VideoLibrary
import com.example.model.PeerDevice
import com.example.security.Pairing
import com.example.ui.PulseViewModel
import com.example.ui.components.DeviceSettingsDialog
import com.example.ui.components.DialPadCard
import com.example.ui.components.DirectIpDialog
import com.example.ui.components.PairingCodeDialog
import com.example.ui.components.DiscoveryRadar
import com.example.ui.components.HotspotGuideDialog
import com.example.ui.components.LocalDeviceCard
import com.example.ui.components.NotificationComposer
import com.example.ui.components.NotificationHistoryList
import com.example.ui.components.TelemetryDashboardCard
import com.example.ui.components.VideoLibraryCard
import com.example.ui.components.VideoRow
import com.example.ui.theme.CyanAccent
import com.example.ui.theme.ElectricIndigo
import com.example.ui.theme.EmeraldGlow
import com.example.ui.theme.PulseLinkTheme
import com.example.ui.theme.RoseAlert
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val viewModel: PulseViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            PulseLinkTheme {
                PulseLinkApp(viewModel = viewModel)
            }
        }
    }
}

enum class PulseTab(val label: String, val icon: androidx.compose.ui.graphics.vector.ImageVector) {
    TELEMETRY("Live Data", Icons.Default.Sensors),
    SEND_NOTIFY("Send", Icons.Default.Campaign),
    CALL("Call", Icons.Default.Dialpad),
    VIDEOS("Videos", Icons.Default.VideoLibrary),
    INBOX("Inbox", Icons.Default.History),
    DEVICES("Radar", Icons.Default.Radar)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PulseLinkApp(viewModel: PulseViewModel) {
    val context = LocalContext.current
    var selectedTab by remember { mutableStateOf(PulseTab.TELEMETRY) }

    val localTelemetry by viewModel.localTelemetry.collectAsStateWithLifecycle()
    val selectedPeer by viewModel.selectedPeer.collectAsStateWithLifecycle()
    val peerTelemetry by viewModel.peerTelemetry.collectAsStateWithLifecycle()
    val isFetchingPeerTelemetry by viewModel.isFetchingPeerTelemetry.collectAsStateWithLifecycle()
    val isStreamingTelemetry by viewModel.isStreamingTelemetry.collectAsStateWithLifecycle()
    val streamIntervalSeconds by viewModel.streamIntervalSeconds.collectAsStateWithLifecycle()
    val latencyMs by viewModel.peerLatencyMs.collectAsStateWithLifecycle()
    val discoveredPeers by viewModel.discoveredPeers.collectAsStateWithLifecycle()
    val savedPeers by viewModel.savedPeers.collectAsStateWithLifecycle()
    val notifications by viewModel.notificationsList.collectAsStateWithLifecycle()
    val isServerRunning by viewModel.isServerRunning.collectAsStateWithLifecycle()
    val serverPort by viewModel.serverPort.collectAsStateWithLifecycle()
    val serverLogs by viewModel.serverLogs.collectAsStateWithLifecycle()
    val isScanning by viewModel.isScanning.collectAsStateWithLifecycle()
    val isSendingNotification by viewModel.isSendingNotification.collectAsStateWithLifecycle()
    val lastSendResult by viewModel.lastSendResult.collectAsStateWithLifecycle()
    val isBackgroundServiceRunning by viewModel.isBackgroundServiceRunning.collectAsStateWithLifecycle()
    val peerNotificationFeed by viewModel.peerNotificationFeed.collectAsStateWithLifecycle()
    val isSharingNotifications by viewModel.isSharingNotifications.collectAsStateWithLifecycle()
    val hasNotificationAccess by viewModel.hasNotificationAccess.collectAsStateWithLifecycle()
    val isPeerAlertsEnabled by viewModel.isPeerAlertsEnabled.collectAsStateWithLifecycle()
    val isRemoteCallsAllowed by viewModel.isRemoteCallsAllowed.collectAsStateWithLifecycle()
    val hasPhonePermission by viewModel.hasPhonePermission.collectAsStateWithLifecycle()
    val partnerPairingCode by viewModel.partnerPairingCode.collectAsStateWithLifecycle()
    val isPairingCodeNeeded by viewModel.isPairingCodeNeeded.collectAsStateWithLifecycle()
    val callSoundState by viewModel.callSoundState.collectAsStateWithLifecycle()
    val isHearingCallsHere by viewModel.isHearingCallsHere.collectAsStateWithLifecycle()
    val isCallLoudspeakerOn by viewModel.isCallLoudspeakerOn.collectAsStateWithLifecycle()
    val isRelayingCallSound by viewModel.isRelayingCallSound.collectAsStateWithLifecycle()
    val hasMicPermission by viewModel.hasMicPermission.collectAsStateWithLifecycle()
    val isAccessibilityOn by viewModel.isAccessibilityOn.collectAsStateWithLifecycle()
    val hasBluetoothPermission by viewModel.hasBluetoothPermission.collectAsStateWithLifecycle()
    val bluetoothSound by viewModel.bluetoothSound.collectAsStateWithLifecycle()
    val isSharingVideos by viewModel.isSharingVideos.collectAsStateWithLifecycle()
    val hasVideoPermission by viewModel.hasVideoPermission.collectAsStateWithLifecycle()
    val peerVideos by viewModel.peerVideos.collectAsStateWithLifecycle()

    var videoSearch by rememberSaveable { mutableStateOf("") }
    var showDirectIpDialog by remember { mutableStateOf(false) }
    var showHotspotGuideDialog by remember { mutableStateOf(false) }
    var showSettingsDialog by remember { mutableStateOf(false) }

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    // Request POST_NOTIFICATIONS runtime permission on Android 13+
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { _ -> }

    // Asked for only when the user turns on "Allow Remote Calls"
    val phonePermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { granted ->
        viewModel.refreshLocalTelemetry()
        if (granted.values.any { !it }) {
            scope.launch {
                snackbarHostState.showSnackbar("Allow Phone in Settings > Apps > PulseLink > Permissions to use remote calls")
            }
        }
    }

    // Asked for only when the user turns on the call sound relay, on either phone
    val micPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        viewModel.refreshLocalTelemetry()
        if (!granted) {
            scope.launch {
                snackbarHostState.showSnackbar("Allow Microphone in Settings > Apps > PulseLink > Permissions to relay call sound")
            }
        }
    }

    // Asked for only when the user wants the call sound carried over Bluetooth, on either phone
    val bluetoothPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        viewModel.refreshLocalTelemetry()
        if (!granted) {
            scope.launch {
                snackbarHostState.showSnackbar("Allow Nearby devices in Settings > Apps > PulseLink > Permissions to use Bluetooth")
            }
        }
    }

    // Asked for only when the user turns on "Share My Videos"
    val videoPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        viewModel.refreshLocalTelemetry()
        if (!granted) {
            scope.launch {
                snackbarHostState.showSnackbar("Allow Videos in Settings > Apps > PulseLink > Permissions to share videos")
            }
        }
    }

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    // The dial pad says whether the peer has a Bluetooth headset connected, so ask afresh on opening it
    LaunchedEffect(selectedTab, selectedPeer) {
        if (selectedTab == PulseTab.CALL) viewModel.refreshPeerHeadset()
    }

    // Also runs again once a pairing code is saved, so the videos appear without another tap
    LaunchedEffect(selectedTab, selectedPeer, partnerPairingCode) {
        if (selectedTab == PulseTab.VIDEOS) viewModel.refreshPeerVideos()
    }

    LaunchedEffect(lastSendResult) {
        lastSendResult?.let { msg ->
            scope.launch {
                snackbarHostState.showSnackbar(msg)
            }
        }
    }

    // Combine discovered peers + saved peers for easy dropdown
    val combinedPeersList = remember(discoveredPeers, savedPeers) {
        val list = discoveredPeers.toMutableList()
        savedPeers.forEach { saved ->
            if (list.none { it.ip == saved.ip }) {
                list.add(
                    PeerDevice(
                        id = saved.ip,
                        name = saved.deviceName,
                        ip = saved.ip,
                        port = saved.port,
                        model = saved.deviceModel,
                        isFavorite = saved.isFavorite
                    )
                )
            }
        }
        list
    }

    // Dialogs
    if (showDirectIpDialog) {
        DirectIpDialog(
            onDismiss = { showDirectIpDialog = false },
            onConnect = { ip, port, name ->
                viewModel.selectPeerByIp(ip, port, name)
                selectedTab = PulseTab.TELEMETRY
            }
        )
    }

    if (showHotspotGuideDialog) {
        HotspotGuideDialog(onDismiss = { showHotspotGuideDialog = false })
    }

    if (isPairingCodeNeeded) {
        PairingCodeDialog(
            peerName = selectedPeer?.name ?: "the other phone",
            currentCode = partnerPairingCode,
            onDismiss = { viewModel.askForPairingCode(false) },
            onSave = { viewModel.setPartnerPairingCode(it) }
        )
    }

    if (showSettingsDialog) {
        DeviceSettingsDialog(
            deviceName = localTelemetry.deviceName,
            isServerRunning = isServerRunning,
            serverPort = serverPort,
            isBackgroundService = isBackgroundServiceRunning,
            isSharingNotifications = isSharingNotifications,
            hasNotificationAccess = hasNotificationAccess,
            isPeerAlertsEnabled = isPeerAlertsEnabled,
            isRemoteCallsAllowed = isRemoteCallsAllowed,
            hasPhonePermission = hasPhonePermission,
            myPairingCode = viewModel.myPairingCode,
            isRelayingCallSound = isRelayingCallSound,
            hasMicPermission = hasMicPermission,
            isAccessibilityOn = isAccessibilityOn,
            hasBluetoothPermission = hasBluetoothPermission,
            isSharingVideos = isSharingVideos,
            hasVideoPermission = hasVideoPermission,
            serverLogs = serverLogs,
            onUpdateDeviceName = { viewModel.updateDeviceName(it) },
            onToggleServer = { viewModel.toggleServer(it, serverPort) },
            onToggleBackgroundService = { viewModel.toggleBackgroundService(it) },
            onToggleNotificationSharing = { viewModel.toggleNotificationSharing(it) },
            onOpenNotificationAccess = {
                try {
                    context.startActivity(Intent(android.provider.Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                } catch (e: Exception) {
                    scope.launch {
                        snackbarHostState.showSnackbar("Open Settings > Notification access and allow PulseLink")
                    }
                }
            },
            onTogglePeerAlerts = { viewModel.togglePeerAlerts(it) },
            onToggleRemoteCalls = { enabled ->
                viewModel.toggleRemoteCalls(enabled)
                if (enabled && !hasPhonePermission) phonePermissionLauncher.launch(CallController.PERMISSIONS)
            },
            onGrantPhonePermission = { phonePermissionLauncher.launch(CallController.PERMISSIONS) },
            onToggleCallSoundRelay = { enabled ->
                viewModel.toggleCallSoundRelay(enabled)
                if (enabled && !hasMicPermission) micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            },
            onGrantMicPermission = { micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO) },
            onOpenAccessibilitySettings = {
                try {
                    context.startActivity(Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS))
                } catch (e: Exception) {
                    scope.launch {
                        snackbarHostState.showSnackbar("Open Settings > Accessibility > Installed apps and turn on PulseLink")
                    }
                }
            },
            onGrantBluetoothPermission = { bluetoothPermissionLauncher.launch(Manifest.permission.BLUETOOTH_CONNECT) },
            onToggleVideoSharing = { enabled ->
                viewModel.toggleVideoSharing(enabled)
                if (enabled && !hasVideoPermission) videoPermissionLauncher.launch(VideoLibrary.PERMISSION)
            },
            onGrantVideoPermission = { videoPermissionLauncher.launch(VideoLibrary.PERMISSION) },
            onDismiss = { showSettingsDialog = false }
        )
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(CyanAccent.copy(alpha = 0.2f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Sensors,
                                contentDescription = "PulseLink Logo",
                                tint = CyanAccent,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "PulseLink",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Black),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Dual Mobile Notifier & 5G Telemetry",
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                actions = {
                    IconButton(
                        onClick = { showHotspotGuideDialog = true },
                        modifier = Modifier.testTag("topbar_hotspot_guide")
                    ) {
                        Icon(
                            imageVector = Icons.Default.WifiTethering,
                            contentDescription = "Hotspot Guide",
                            tint = EmeraldGlow
                        )
                    }
                    IconButton(
                        onClick = { showSettingsDialog = true },
                        modifier = Modifier.testTag("topbar_settings")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Settings",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surface,
                tonalElevation = 6.dp
            ) {
                PulseTab.values().forEach { tab ->
                    val isSelected = selectedTab == tab
                    val unreadCount = notifications.count { !it.isRead && it.type == "RECEIVED" }

                    NavigationBarItem(
                        selected = isSelected,
                        onClick = { selectedTab = tab },
                        icon = {
                            if (tab == PulseTab.INBOX && unreadCount > 0) {
                                BadgedBox(badge = {
                                    Badge(containerColor = RoseAlert) {
                                        Text("$unreadCount")
                                    }
                                }) {
                                    Icon(tab.icon, contentDescription = tab.label)
                                }
                            } else {
                                Icon(tab.icon, contentDescription = tab.label)
                            }
                        },
                        label = {
                            Text(
                                text = tab.label,
                                fontSize = 11.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                            )
                        },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = CyanAccent,
                            selectedTextColor = CyanAccent,
                            indicatorColor = CyanAccent.copy(alpha = 0.15f)
                        )
                    )
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(innerPadding)
                // The list ends above the keyboard, so a text field low on the screen scrolls into view
                .consumeWindowInsets(innerPadding)
                .imePadding(),
            contentAlignment = Alignment.TopCenter
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .widthIn(max = 680.dp)
                    .padding(horizontal = 16.dp),
                contentPadding = PaddingValues(top = 10.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Persistent Local Device Status Header Card
                item {
                    LocalDeviceCard(
                        telemetry = localTelemetry,
                        isServerRunning = isServerRunning,
                        serverPort = serverPort,
                        onEditSettingsClick = { showSettingsDialog = true }
                    )
                }

                // Dynamic Body based on Selected Tab
                when (selectedTab) {
                    PulseTab.TELEMETRY -> {
                        item {
                            TelemetryDashboardCard(
                                peer = selectedPeer,
                                telemetry = peerTelemetry,
                                isFetching = isFetchingPeerTelemetry,
                                isStreaming = isStreamingTelemetry,
                                latencyMs = latencyMs,
                                onRefresh = {
                                    selectedPeer?.let { peer ->
                                        viewModel.fetchPeerTelemetry(peer.ip, peer.port)
                                    }
                                },
                                onToggleStream = { viewModel.toggleTelemetryStream(it) },
                                onRemoteAction = { actionType, payload ->
                                    selectedPeer?.let { peer ->
                                        viewModel.sendRemoteAction(peer.ip, peer.port, actionType, payload)
                                    }
                                },
                                onSelectPeerClick = { selectedTab = PulseTab.DEVICES },
                                notificationFeed = peerNotificationFeed,
                                onDismissNotification = { viewModel.dismissPeerNotification(it) },
                                onReplyNotification = { item, text -> viewModel.replyToPeerNotification(item, text) },
                                onAnswerCall = { viewModel.answerCallOnPeer() },
                                onEndCall = { viewModel.endCallOnPeer() },
                                streamIntervalSeconds = streamIntervalSeconds,
                                onStreamIntervalChange = { viewModel.setStreamInterval(it) }
                            )
                        }
                    }

                    PulseTab.SEND_NOTIFY -> {
                        item {
                            NotificationComposer(
                                selectedPeer = selectedPeer,
                                availablePeers = combinedPeersList,
                                onSelectPeer = { viewModel.selectPeer(it) },
                                onManualIpClick = { showDirectIpDialog = true },
                                onSendNotification = { targetIp, targetPort, title, message, category, priority, vibrate, playSound, attachTel ->
                                    viewModel.sendNotification(
                                        targetIp = targetIp,
                                        targetPort = targetPort,
                                        title = title,
                                        message = message,
                                        category = category,
                                        priority = priority,
                                        vibrate = vibrate,
                                        playSound = playSound,
                                        attachMyTelemetry = attachTel
                                    )
                                },
                                isSending = isSendingNotification,
                                sendResult = lastSendResult
                            )
                        }
                    }

                    PulseTab.CALL -> {
                        item {
                            DialPadCard(
                                peer = selectedPeer,
                                isPaired = partnerPairingCode.length == Pairing.CODE_LENGTH,
                                isHearingHere = isHearingCallsHere,
                                callSound = callSoundState,
                                isLoudspeakerOn = isCallLoudspeakerOn,
                                peerHeadset = peerTelemetry?.bluetoothHeadset,
                                bluetoothSound = bluetoothSound,
                                onDial = { number, speaker -> viewModel.dialOnPeer(number, speaker) },
                                onAnswer = { viewModel.answerCallOnPeer() },
                                onHangUp = { viewModel.endCallOnPeer() },
                                onEditPairingCode = { viewModel.askForPairingCode(true) },
                                onToggleHearHere = { enabled ->
                                    viewModel.toggleHearCallsHere(enabled)
                                    if (enabled && !hasMicPermission) micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                },
                                onStartCallSound = { viewModel.startCallSound() },
                                onStopCallSound = { viewModel.stopCallSound() },
                                onToggleLoudspeaker = { viewModel.toggleCallLoudspeaker(it) },
                                onToggleBluetoothSound = { enabled ->
                                    viewModel.toggleBluetoothSound(enabled)
                                    if (enabled && !hasBluetoothPermission) {
                                        bluetoothPermissionLauncher.launch(Manifest.permission.BLUETOOTH_CONNECT)
                                    }
                                },
                                onSelectPeerClick = { selectedTab = PulseTab.DEVICES }
                            )
                        }
                    }

                    PulseTab.VIDEOS -> {
                        item {
                            VideoLibraryCard(
                                peer = selectedPeer,
                                library = peerVideos,
                                isPaired = partnerPairingCode.length == Pairing.CODE_LENGTH,
                                search = videoSearch,
                                onSearchChange = { videoSearch = it },
                                onRefresh = { viewModel.refreshPeerVideos() },
                                onEditPairingCode = { viewModel.askForPairingCode(true) },
                                onSelectPeerClick = { selectedTab = PulseTab.DEVICES }
                            )
                        }
                        selectedPeer?.let { peer ->
                            val visibleVideos = peerVideos.videos.filter {
                                videoSearch.isBlank() ||
                                    it.name.contains(videoSearch.trim(), ignoreCase = true) ||
                                    it.folder.contains(videoSearch.trim(), ignoreCase = true)
                            }
                            items(visibleVideos, key = { it.id }) { video ->
                                VideoRow(
                                    video = video,
                                    peer = peer,
                                    token = peerVideos.token,
                                    onPlay = {
                                        VideoPlayerActivity.start(context, peer.ip, peer.port, video.id, video.name)
                                    }
                                )
                            }
                        }
                    }

                    PulseTab.INBOX -> {
                        item {
                            NotificationHistoryList(
                                notifications = notifications,
                                onDeleteNotification = { viewModel.deleteNotification(it) },
                                onClearAll = { viewModel.clearAllNotifications() },
                                onMarkRead = { viewModel.markNotificationRead(it) }
                            )
                        }
                    }

                    PulseTab.DEVICES -> {
                        item {
                            DiscoveryRadar(
                                isScanning = isScanning,
                                discoveredPeers = discoveredPeers,
                                savedPeers = savedPeers,
                                selectedPeer = selectedPeer,
                                onStartScan = { viewModel.startScanning() },
                                onSelectPeer = { peer ->
                                    viewModel.selectPeer(peer)
                                    selectedTab = PulseTab.TELEMETRY
                                },
                                onDirectIpClick = { showDirectIpDialog = true },
                                onHotspotGuideClick = { showHotspotGuideDialog = true },
                                onPingPeer = { ip, port -> viewModel.pingPeer(ip, port) },
                                onDeleteSavedPeer = { viewModel.deletePeer(it) },
                                onToggleFavorite = { ip, isFav -> viewModel.toggleFavoritePeer(ip, isFav) }
                            )
                        }
                    }
                }
            }
        }
    }
}
