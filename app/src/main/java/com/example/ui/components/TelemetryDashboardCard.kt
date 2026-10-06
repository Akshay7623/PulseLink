package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SdStorage
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Vibration
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.DeviceTelemetry
import com.example.model.PeerDevice
import com.example.model.PeerNotificationFeed
import com.example.model.TelemetryStreamInterval
import com.example.ui.theme.CyanAccent
import com.example.ui.theme.EmeraldGlow
import com.example.ui.theme.RoseAlert

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TelemetryDashboardCard(
    peer: PeerDevice?,
    telemetry: DeviceTelemetry?,
    isFetching: Boolean,
    isStreaming: Boolean,
    latencyMs: Long?,
    onRefresh: () -> Unit,
    onToggleStream: (Boolean) -> Unit,
    onRemoteAction: (actionType: String, payload: String) -> Unit,
    onSelectPeerClick: () -> Unit,
    notificationFeed: PeerNotificationFeed?,
    streamIntervalSeconds: Int,
    onStreamIntervalChange: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    var showClipboardDialog by remember { mutableStateOf(false) }
    var showIntervalDialog by remember { mutableStateOf(false) }
    val isCustomInterval = streamIntervalSeconds !in TelemetryStreamInterval.PRESET_SECONDS

    if (showIntervalDialog) {
        StreamIntervalDialog(
            currentSeconds = streamIntervalSeconds,
            onDismiss = { showIntervalDialog = false },
            onConfirm = onStreamIntervalChange
        )
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            // Header: Peer Info + Live Indicator
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "PEER DEVICE TELEMETRY",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        ),
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = peer?.name ?: "No Peer Connected",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    if (peer != null) {
                        Text(
                            text = "IP: ${peer.ip}:${peer.port} • ${telemetry?.deviceModel ?: peer.model}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                if (peer != null) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (latencyMs != null) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(EmeraldGlow.copy(alpha = 0.15f))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = "${latencyMs}ms",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = EmeraldGlow
                                )
                            }
                            Spacer(modifier = Modifier.width(6.dp))
                        }

                        IconButton(
                            onClick = onRefresh,
                            modifier = Modifier.testTag("refresh_telemetry_btn")
                        ) {
                            if (isFetching) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Default.Refresh,
                                    contentDescription = "Refresh Telemetry",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            if (peer == null) {
                // Empty State
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Default.Sensors,
                            contentDescription = "No Peer",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(36.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Connect a mobile device to see live charging % and 5G/4G stats in real time",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        FilledTonalButton(
                            onClick = onSelectPeerClick,
                            modifier = Modifier.testTag("select_peer_button")
                        ) {
                            Text("Discover Nearby Mobile Devices")
                        }
                    }
                }
            } else {
                // Live Stream Switch + Update Interval Selector
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(if (isStreaming) EmeraldGlow else Color.Gray)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = "Real-Time Telemetry Stream",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = if (isStreaming) "Updating every ${streamIntervalSeconds}s live" else "Manual refresh mode",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Switch(
                            checked = isStreaming,
                            onCheckedChange = onToggleStream,
                            modifier = Modifier.testTag("stream_toggle")
                        )
                    }

                    FlowRow(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 2.dp, bottom = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        TelemetryStreamInterval.PRESET_SECONDS.forEach { seconds ->
                            StreamIntervalPill(
                                label = "${seconds}s",
                                isSelected = streamIntervalSeconds == seconds,
                                onClick = { onStreamIntervalChange(seconds) }
                            )
                        }
                        StreamIntervalPill(
                            label = if (isCustomInterval) "Custom (${streamIntervalSeconds}s)" else "Custom",
                            isSelected = isCustomInterval,
                            onClick = { showIntervalDialog = true },
                            modifier = Modifier.testTag("stream_interval_custom")
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                if (telemetry != null) {
                    // Battery and Network side-by-side or stacked
                    BatteryGaugeCard(
                        telemetry = telemetry,
                        titlePrefix = "${peer.name.take(12)} Battery"
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    NetworkBadgeCard(
                        telemetry = telemetry,
                        titlePrefix = "${peer.name.take(12)} Network"
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // The peer's own app notifications (WhatsApp, SMS, calls)
                    PeerNotificationsCard(
                        feed = notificationFeed,
                        titlePrefix = "${peer.name.take(12)} Notifications"
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // System RAM & Storage Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // RAM Box
                        Surface(
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.Memory,
                                        contentDescription = "RAM",
                                        tint = CyanAccent,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "RAM Available",
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "${telemetry.availableRamMb} MB",
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "of ${telemetry.totalRamMb} MB total",
                                    fontSize = 10.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        // Storage Box
                        Surface(
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.SdStorage,
                                        contentDescription = "Storage",
                                        tint = EmeraldGlow,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "Free Storage",
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "${telemetry.freeStorageGb} GB",
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "of ${telemetry.totalStorageGb} GB total",
                                    fontSize = 10.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Remote Device Controls
                    Text(
                        text = "REMOTE DEVICE ACTIONS",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        ),
                        color = MaterialTheme.colorScheme.primary
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = { onRemoteAction("RING_ALARM", "") },
                            modifier = Modifier.testTag("action_ring_btn")
                        ) {
                            Icon(Icons.Default.VolumeUp, contentDescription = "Ring", modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Ring Phone")
                        }

                        OutlinedButton(
                            onClick = { onRemoteAction("VIBRATE_TEST", "") },
                            modifier = Modifier.testTag("action_vibrate_btn")
                        ) {
                            Icon(Icons.Default.Vibration, contentDescription = "Vibrate", modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Buzz")
                        }

                        OutlinedButton(
                            onClick = { onRemoteAction("PING", "") },
                            modifier = Modifier.testTag("action_ping_btn")
                        ) {
                            Icon(Icons.Default.Speed, contentDescription = "Ping", modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Ping")
                        }
                    }
                } else {
                    // Loading telemetry
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(modifier = Modifier.size(32.dp))
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                text = "Fetching real-time data from ${peer.name}...",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StreamIntervalPill(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(
                if (isSelected) CyanAccent.copy(alpha = 0.2f)
                else MaterialTheme.colorScheme.surface
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            fontSize = 12.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
            color = if (isSelected) CyanAccent else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
