package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CallReceived
import androidx.compose.material.icons.filled.CallMade
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
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
import com.example.data.local.entity.NotificationEntity
import com.example.model.DeviceTelemetry
import com.example.ui.theme.Amber5G
import com.example.ui.theme.CyanAccent
import com.example.ui.theme.ElectricIndigo
import com.example.ui.theme.EmeraldGlow
import com.example.ui.theme.RoseAlert
import com.example.util.JsonUtils
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun NotificationHistoryList(
    notifications: List<NotificationEntity>,
    onDeleteNotification: (String) -> Unit,
    onClearAll: () -> Unit,
    onMarkRead: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedFilter by remember { mutableStateOf("ALL") }
    var showClearDialog by remember { mutableStateOf(false) }

    val filteredList = when (selectedFilter) {
        "RECEIVED" -> notifications.filter { it.type == "RECEIVED" }
        "SENT" -> notifications.filter { it.type == "SENT" }
        "URGENT" -> notifications.filter { it.priority == "HIGH" }
        else -> notifications
    }

    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = { Text("Clear All Notifications?") },
            text = { Text("This will permanently remove all sent and received notification logs.") },
            confirmButton = {
                TextButton(onClick = {
                    onClearAll()
                    showClearDialog = false
                }) {
                    Text("Clear All", color = RoseAlert)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    Column(modifier = modifier.fillMaxWidth()) {
        // Top Filter Bar & Clear Action
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("ALL" to "All", "RECEIVED" to "Received", "SENT" to "Sent", "URGENT" to "🚨 Urgent").forEach { (key, label) ->
                    FilterChip(
                        selected = selectedFilter == key,
                        onClick = { selectedFilter = key },
                        label = { Text(label, fontSize = 11.sp) }
                    )
                }
            }

            if (notifications.isNotEmpty()) {
                IconButton(
                    onClick = { showClearDialog = true },
                    modifier = Modifier.testTag("clear_history_btn")
                ) {
                    Icon(
                        imageVector = Icons.Default.DeleteSweep,
                        contentDescription = "Clear All History",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        if (filteredList.isEmpty()) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 24.dp),
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.Default.Inbox,
                        contentDescription = "No Notifications",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(36.dp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "No notifications in this filter",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            // Plain Column: this list is already hosted inside the screen's LazyColumn
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                filteredList.forEach { item ->
                    key(item.id) {
                        NotificationItemCard(
                            item = item,
                            onDelete = { onDeleteNotification(item.id) },
                            onMarkRead = { onMarkRead(item.id) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun NotificationItemCard(
    item: NotificationEntity,
    onDelete: () -> Unit,
    onMarkRead: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val isReceived = item.type == "RECEIVED"
    val isUnreadReceived = !item.isRead && isReceived
    val isUrgent = item.priority == "HIGH"
    val timeFormatted = remember(item.timestamp) {
        SimpleDateFormat("MMM dd, HH:mm:ss", Locale.getDefault()).format(Date(item.timestamp))
    }

    val attachedTelemetry: DeviceTelemetry? = remember(item.telemetryJson) {
        if (!item.telemetryJson.isNullOrBlank()) {
            try {
                JsonUtils.jsonToTelemetry(JSONObject(item.telemetryJson))
            } catch (e: Exception) {
                null
            }
        } else null
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isUnreadReceived)
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f)
            else
                MaterialTheme.colorScheme.surface
        ),
        // Flat when unread: a shadow would show through the translucent container as an inner box
        elevation = CardDefaults.cardElevation(defaultElevation = if (isUnreadReceived) 0.dp else 1.dp)
    ) {
        Column(
            modifier = Modifier
                .clickable {
                    expanded = !expanded
                    if (!item.isRead) onMarkRead()
                }
                .padding(14.dp)
        ) {
            // Header Row: Type Icon + Sender/Recipient + Timestamp + Delete
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(
                                if (isReceived) CyanAccent.copy(alpha = 0.2f)
                                else ElectricIndigo.copy(alpha = 0.2f)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (isReceived) Icons.Default.CallReceived else Icons.Default.CallMade,
                            contentDescription = item.type,
                            tint = if (isReceived) CyanAccent else ElectricIndigo,
                            modifier = Modifier.size(16.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = if (isReceived) item.senderDeviceName else "To: ${item.senderIp}",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            if (isUrgent) {
                                Spacer(modifier = Modifier.width(6.dp))
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(RoseAlert.copy(alpha = 0.2f))
                                        .padding(horizontal = 4.dp, vertical = 1.dp)
                                ) {
                                    Text(
                                        text = "URGENT",
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Black,
                                        color = RoseAlert
                                    )
                                }
                            }
                        }

                        Text(
                            text = timeFormatted,
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Delete",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Title & Message
            Text(
                text = item.title,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = item.message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // Attached Telemetry Capsule
            if (attachedTelemetry != null) {
                Spacer(modifier = Modifier.height(10.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "🔋 ${attachedTelemetry.batteryLevel}% (${if (attachedTelemetry.isCharging) "⚡ Charging" else "Discharging"})",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = if (attachedTelemetry.isCharging) EmeraldGlow else MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "📶 ${attachedTelemetry.networkType}",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = CyanAccent
                            )
                        }

                        Icon(
                            imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = "Expand",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }

                AnimatedVisibility(visible = expanded) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp)
                    ) {
                        Text(
                            text = "Attached Telemetry Snapshot Details:",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "• Model: ${attachedTelemetry.deviceModel}\n" +
                                    "• Provider / Detail: ${attachedTelemetry.networkDetails}\n" +
                                    "• Temp: ${attachedTelemetry.batteryTempCelsius}°C • Voltage: ${attachedTelemetry.batteryVoltageMv}mV\n" +
                                    "• RAM Free: ${attachedTelemetry.availableRamMb} MB / ${attachedTelemetry.totalRamMb} MB\n" +
                                    "• Storage Free: ${attachedTelemetry.freeStorageGb} GB / ${attachedTelemetry.totalStorageGb} GB",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            lineHeight = 16.sp
                        )
                    }
                }
            }
        }
    }
}
