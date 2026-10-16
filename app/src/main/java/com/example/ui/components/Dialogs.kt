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
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.TelemetryStreamInterval
import com.example.security.Pairing
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

/**
 * Asks for the pairing code of the phone whose calls are controlled from this one.
 */
@Composable
fun PairingCodeDialog(
    peerName: String,
    currentCode: String,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit
) {
    var code by remember { mutableStateOf(Pairing.format(currentCode)) }
    // Saving it empty forgets the code
    val isComplete = Pairing.normalize(code).length == Pairing.CODE_LENGTH || code.isBlank()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Pairing code of $peerName") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "On $peerName, open PulseLink settings and turn on \"Allow Remote Calls\". The 8-character pairing code appears below it.",
                    style = MaterialTheme.typography.bodyMedium
                )
                // Kept exactly as typed: rewriting it on every key makes some keyboards drop letters.
                // Capitals, dashes and spaces are sorted out when it is saved.
                OutlinedTextField(
                    value = code,
                    onValueChange = { code = it },
                    label = { Text("Pairing code") },
                    placeholder = { Text("ABCD-EFGH") },
                    singleLine = true,
                    isError = code.isNotEmpty() && !isComplete,
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Characters,
                        autoCorrectEnabled = false
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("pairing_code_input")
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(code) },
                enabled = isComplete,
                modifier = Modifier.testTag("pairing_code_save_btn")
            ) {
                Text("Save")
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
    isPeerAlertsEnabled: Boolean,
    isRemoteCallsAllowed: Boolean,
    hasPhonePermission: Boolean,
    myPairingCode: String,
    isRelayingCallSound: Boolean,
    hasMicPermission: Boolean,
    isAccessibilityOn: Boolean,
    hasBluetoothPermission: Boolean,
    serverLogs: List<String>,
    onUpdateDeviceName: (String) -> Unit,
    onToggleServer: (Boolean) -> Unit,
    onToggleBackgroundService: (Boolean) -> Unit,
    onToggleNotificationSharing: (Boolean) -> Unit,
    onOpenNotificationAccess: () -> Unit,
    onTogglePeerAlerts: (Boolean) -> Unit,
    onToggleRemoteCalls: (Boolean) -> Unit,
    onGrantPhonePermission: () -> Unit,
    onToggleCallSoundRelay: (Boolean) -> Unit,
    onGrantMicPermission: () -> Unit,
    onOpenAccessibilitySettings: () -> Unit,
    onGrantBluetoothPermission: () -> Unit,
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
                                !isSharingNotifications -> "Let other PulseLink devices see this phone's app notifications (WhatsApp, SMS, calls) and reply to its chats"
                                hasNotificationAccess -> "On: any device on this network can read and clear this phone's notifications, and reply to its chats"
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

                // Show The Connected Device's Notifications On This Phone Toggle
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Peer Notification Alerts",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "Pop up the connected device's app notifications on this phone as they arrive. That device needs \"Share My Notifications\" on.",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = isPeerAlertsEnabled,
                        onCheckedChange = onTogglePeerAlerts,
                        modifier = Modifier.testTag("peer_alerts_toggle")
                    )
                }

                // Let Other Devices Control This Phone's Calls Toggle
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Allow Remote Calls",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = when {
                                !isRemoteCallsAllowed -> "Let other PulseLink devices dial numbers from this phone's SIM and answer or end its calls"
                                hasPhonePermission -> "On: any device on this network can make calls from this phone's SIM, and answer or end its calls"
                                else -> "Phone permission is not granted yet"
                            },
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = isRemoteCallsAllowed,
                        onCheckedChange = onToggleRemoteCalls,
                        modifier = Modifier.testTag("remote_calls_toggle")
                    )
                }

                if (isRemoteCallsAllowed && !hasPhonePermission) {
                    OutlinedButton(
                        onClick = onGrantPhonePermission,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("grant_phone_permission_btn")
                    ) {
                        Text("Grant Phone Permission")
                    }
                }

                if (isRemoteCallsAllowed) {
                    // Only a device that was given this code may use this phone's calls
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Text(
                                text = "Pairing code: $myPairingCode",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace,
                                modifier = Modifier.testTag("my_pairing_code")
                            )
                            Text(
                                text = "Type this once in the Call tab of the phone that controls this one",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    // Let The Paired Device Hear And Talk On This Phone's Calls Toggle
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Relay Call Sound",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = when {
                                    !isRelayingCallSound -> "Experimental: let the paired device hear and talk on this phone's calls. The call plays on this phone's loudspeaker."
                                    !hasMicPermission -> "Microphone permission is not granted yet"
                                    !isAccessibilityOn -> "PulseLink is not turned on in Accessibility yet. Android mutes the microphone during calls without it."
                                    else -> "On: the paired device can hear and talk on this phone's calls"
                                },
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = isRelayingCallSound,
                            onCheckedChange = onToggleCallSoundRelay,
                            modifier = Modifier.testTag("relay_call_sound_toggle")
                        )
                    }

                    if (isRelayingCallSound && !hasMicPermission) {
                        OutlinedButton(
                            onClick = onGrantMicPermission,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("grant_mic_permission_btn")
                        ) {
                            Text("Grant Microphone Permission")
                        }
                    }

                    if (isRelayingCallSound && !isAccessibilityOn) {
                        OutlinedButton(
                            onClick = onOpenAccessibilitySettings,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("open_accessibility_btn")
                        ) {
                            Text("Open Accessibility Settings")
                        }
                        Text(
                            text = "Choose PulseLink under Installed apps and turn it on. If Android says \"Restricted setting\": open Settings > Apps > PulseLink, tap the ⋮ menu, choose \"Allow restricted settings\", then try again.",
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            lineHeight = 13.sp
                        )
                    }

                    // The Same Sound Over Bluetooth Instead Of Wi-Fi
                    if (isRelayingCallSound) {
                        if (!hasBluetoothPermission) {
                            OutlinedButton(
                                onClick = onGrantBluetoothPermission,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("grant_bluetooth_permission_btn")
                            ) {
                                Text("Allow Bluetooth For Call Sound")
                            }
                        }
                        Text(
                            text = if (hasBluetoothPermission) {
                                "Bluetooth: once the two phones are paired in Android's Bluetooth settings, the paired device can carry the call sound over Bluetooth instead of Wi-Fi."
                            } else {
                                "Optional: with the Nearby devices permission the paired device can carry the call sound over Bluetooth instead of Wi-Fi, once the two phones are paired in Android's Bluetooth settings."
                            },
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            lineHeight = 13.sp
                        )
                    }
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
