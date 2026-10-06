package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.NetworkWifi
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.WifiTethering
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.TelemetryStreamInterval
import com.example.ui.theme.CyanAccent
import com.example.ui.theme.ElectricIndigo
import com.example.ui.theme.EmeraldGlow

@Composable
fun DirectIpDialog(
    onDismiss: () -> Unit,
    onConnect: (ip: String, port: Int, name: String) -> Unit
) {
    var ip by remember { mutableStateOf("192.168.") }
    var portStr by remember { mutableStateOf("8765") }
    var name by remember { mutableStateOf("Target Phone") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Devices, contentDescription = "Add IP", tint = CyanAccent)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Connect Peer via IP")
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "Enter the IP address of the target mobile device shown on its screen:",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                OutlinedTextField(
                    value = ip,
                    onValueChange = { ip = it },
                    label = { Text("Peer IP Address") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("manual_ip_input")
                )

                OutlinedTextField(
                    value = portStr,
                    onValueChange = { portStr = it },
                    label = { Text("Port (Default: 8765)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Device Nickname (Optional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val port = portStr.toIntOrNull() ?: 8765
                    if (ip.isNotBlank()) {
                        onConnect(ip.trim(), port, name.trim())
                        onDismiss()
                    }
                },
                modifier = Modifier.testTag("manual_ip_connect_btn")
            ) {
                Text("Connect & Fetch")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun HotspotGuideDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.WifiTethering, contentDescription = "Hotspot", tint = EmeraldGlow)
                Spacer(modifier = Modifier.width(8.dp))
                Text("How to Connect 2 Mobiles")
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "PulseLink enables direct real-time communication between 2 mobile phones:",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold
                )

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                ) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = "Option A: Same Wi-Fi Network",
                            fontWeight = FontWeight.Bold,
                            color = CyanAccent,
                            fontSize = 12.sp
                        )
                        Text(
                            text = "1. Connect Phone A & Phone B to the same Wi-Fi router.\n2. Tap 'Scan Network' to auto-detect, or enter Phone B's IP directly.",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Spacer(modifier = Modifier.height(4.dp))

                        Text(
                            text = "Option B: Mobile Hotspot (No Wi-Fi router required!)",
                            fontWeight = FontWeight.Bold,
                            color = EmeraldGlow,
                            fontSize = 12.sp
                        )
                        Text(
                            text = "1. Turn ON 'Personal Hotspot' on Phone A.\n2. Connect Phone B's Wi-Fi to Phone A's hotspot.\n3. Open PulseLink on both phones. The app will immediately pair and allow instant notification sync & real-time telemetry fetching!",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = onDismiss) {
                Text("Got It!")
            }
        }
    )
}

@Composable
fun StreamIntervalDialog(
    currentSeconds: Int,
    onDismiss: () -> Unit,
    onConfirm: (seconds: Int) -> Unit
) {
    var secondsStr by remember { mutableStateOf(currentSeconds.toString()) }
    val seconds = secondsStr.toIntOrNull()
    val isValid = seconds != null &&
        seconds in TelemetryStreamInterval.MIN_SECONDS..TelemetryStreamInterval.MAX_SECONDS

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Timer, contentDescription = "Interval", tint = CyanAccent)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Custom Update Interval")
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "How often the live telemetry stream should refresh:",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                OutlinedTextField(
                    value = secondsStr,
                    onValueChange = { input -> secondsStr = input.filter { it.isDigit() }.take(4) },
                    label = { Text("Seconds (${TelemetryStreamInterval.MIN_SECONDS} - ${TelemetryStreamInterval.MAX_SECONDS})") },
                    singleLine = true,
                    isError = secondsStr.isNotEmpty() && !isValid,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("stream_interval_input")
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (seconds != null && isValid) {
                        onConfirm(seconds)
                        onDismiss()
                    }
                },
                enabled = isValid,
                modifier = Modifier.testTag("stream_interval_confirm_btn")
            ) {
                Text("Set Interval")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun DeviceSettingsDialog(
    deviceName: String,
    isServerRunning: Boolean,
    serverPort: Int,
    isBackgroundService: Boolean,
    isSharingNotifications: Boolean,
    hasNotificationAccess: Boolean,
    serverLogs: List<String>,
    onUpdateDeviceName: (String) -> Unit,
    onToggleServer: (Boolean) -> Unit,
    onToggleBackgroundService: (Boolean) -> Unit,
    onToggleNotificationSharing: (Boolean) -> Unit,
    onOpenNotificationAccess: () -> Unit,
    onDismiss: () -> Unit
) {
    var editName by remember { mutableStateOf(deviceName) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Settings, contentDescription = "Settings", tint = CyanAccent)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Device & Network Settings")
            }
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Device Name Edit
                OutlinedTextField(
                    value = editName,
                    onValueChange = { editName = it },
                    label = { Text("This Device Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                // Background Listener Service Toggle
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Background Listener Service",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "Keep receiver active even when app is closed/minimized",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = isBackgroundService,
                        onCheckedChange = onToggleBackgroundService
                    )
                }

                // Server Active Toggle
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Embedded Server (Port $serverPort)",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = if (isServerRunning) "Listening for incoming packets" else "Server stopped",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = isServerRunning,
                        onCheckedChange = onToggleServer
                    )
                }

                // Share This Device's Notifications Toggle
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Share My Notifications",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = when {
                                !isSharingNotifications -> "Let other PulseLink devices see this phone's app notifications (WhatsApp, SMS, calls)"
                                hasNotificationAccess -> "On: any device on this network can read this phone's notifications"
                                else -> "Notification access is not granted yet"
                            },
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = isSharingNotifications,
                        onCheckedChange = onToggleNotificationSharing,
                        modifier = Modifier.testTag("share_notifications_toggle")
                    )
                }

                if (isSharingNotifications && !hasNotificationAccess) {
                    OutlinedButton(
                        onClick = onOpenNotificationAccess,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("grant_notification_access_btn")
                    ) {
                        Text("Grant Notification Access")
                    }
                    Text(
                        text = "If Android says \"Restricted setting\": open Settings > Apps > PulseLink, tap the ⋮ menu, choose \"Allow restricted settings\", then try again.",
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 13.sp
                    )
                }

                // Live Server Packet Log
                Text(
                    text = "LIVE PACKET / EVENT LOGS",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.primary
                )

                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(100.dp),
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                ) {
                    if (serverLogs.isEmpty()) {
                        Box(contentAlignment = Alignment.Center) {
                            Text("No packets received yet", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    } else {
                        LazyColumn(modifier = Modifier.padding(6.dp)) {
                            items(serverLogs.reversed()) { log ->
                                Text(
                                    text = log,
                                    fontSize = 9.sp,
                                    fontFamily = FontFamily.Monospace,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    lineHeight = 12.sp
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                onUpdateDeviceName(editName)
                onDismiss()
            }) {
                Text("Save Changes")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        }
    )
}
