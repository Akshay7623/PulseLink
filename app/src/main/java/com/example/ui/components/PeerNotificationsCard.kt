package com.example.ui.components

import android.app.Notification
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.CallState
import com.example.model.DeviceNotification
import com.example.model.PeerNotificationFeed
import com.example.ui.theme.CyanAccent
import com.example.ui.theme.EmeraldCharging
import com.example.ui.theme.EmeraldGlow
import com.example.ui.theme.RoseAlert
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private const val COLLAPSED_PEER_NOTIFICATIONS = 5

/**
 * Shows the app notifications (WhatsApp, SMS, calls, etc.) currently on the peer device.
 * Swiping one away removes it on the peer too, a chat can be answered from here, and a phone
 * call can be picked up or ended.
 */
@Composable
fun PeerNotificationsCard(
    feed: PeerNotificationFeed?,
    onDismiss: (DeviceNotification) -> Unit,
    onReply: (DeviceNotification, String) -> Unit,
    onAnswerCall: () -> Unit,
    onEndCall: () -> Unit,
    titlePrefix: String = "Notifications",
    modifier: Modifier = Modifier
) {
    var showAll by remember { mutableStateOf(false) }
    val notifications = feed?.notifications.orEmpty()
    val visibleNotifications = if (showAll) notifications else notifications.take(COLLAPSED_PEER_NOTIFICATIONS)

    val statusMessage = when {
        feed == null -> "Waiting for notifications from this device..."
        !feed.isSupported -> "Update PulseLink on this device to see its notifications."
        !feed.sharingEnabled -> "Notification sharing is off on this device. Turn on \"Share My Notifications\" in its PulseLink settings."
        !feed.accessGranted -> "This device has not granted PulseLink notification access yet."
        notifications.isEmpty() -> "No notifications on this device right now"
        else -> null
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
        ),
        // Flat: a shadow would show through the translucent container as an inner box
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Top Row: Title + Count Pill
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Notifications,
                        contentDescription = "Notifications",
                        tint = CyanAccent,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = titlePrefix,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(CyanAccent.copy(alpha = 0.15f))
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "${notifications.size}",
                        color = CyanAccent,
                        fontWeight = FontWeight.Black,
                        fontSize = 12.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            if (statusMessage != null) {
                Text(
                    text = statusMessage,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                // What is on the peer right now, newest first
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    visibleNotifications.forEach { item ->
                        // Keyed so a row's swipe state never carries over to the notification that takes its place
                        key(item.key) {
                            SwipeToDismissRow(onDismiss = { onDismiss(item) }) {
                                DeviceNotificationRow(
                                    item = item,
                                    onReply = { onReply(item, it) },
                                    onAnswerCall = onAnswerCall,
                                    onEndCall = onEndCall
                                )
                            }
                        }
                    }
                }

                if (notifications.size > COLLAPSED_PEER_NOTIFICATIONS) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = if (showAll) "Show less" else "Show all ${notifications.size}",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { showAll = !showAll }
                            .padding(horizontal = 4.dp, vertical = 4.dp)
                            .testTag("peer_notifications_toggle_all")
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SwipeToDismissRow(
    onDismiss: () -> Unit,
    content: @Composable () -> Unit
) {
    val dismissState = rememberSwipeToDismissBoxState()

    LaunchedEffect(dismissState.currentValue) {
        if (dismissState.currentValue != SwipeToDismissBoxValue.Settled) onDismiss()
    }

    SwipeToDismissBox(
        state = dismissState,
        backgroundContent = {},
        modifier = Modifier.testTag("peer_notification_row")
    ) {
        content()
    }
}

@Composable
private fun DeviceNotificationRow(
    item: DeviceNotification,
    onReply: (String) -> Unit,
    onAnswerCall: () -> Unit,
    onEndCall: () -> Unit
) {
    val isCall = item.category == Notification.CATEGORY_CALL || item.category == Notification.CATEGORY_MISSED_CALL
    val accentColor = if (isCall) EmeraldGlow else CyanAccent
    val timeFormatted = remember(item.postTime) {
        SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault()).format(Date(item.postTime))
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
            // Header Row: Type Icon + App Name + Timestamp
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = if (isCall) Icons.Default.Call else Icons.Default.Notifications,
                    contentDescription = if (isCall) "Call" else "Notification",
                    tint = accentColor,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = item.appName.ifBlank { item.packageName },
                    modifier = Modifier.weight(1f),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = accentColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = timeFormatted,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Title & Text (e.g. contact name and message)
            if (item.title.isNotBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = item.title,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // A chat lists all of its unread messages; anything else has its one line of text
            if (item.messages.size > 1) {
                Spacer(modifier = Modifier.height(2.dp))
                item.messages.forEach { message ->
                    Text(
                        text = message.displayText(item.title),
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            } else if (item.text.isNotBlank()) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = item.text,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
            }

            if (item.canReply) {
                Spacer(modifier = Modifier.height(8.dp))
                ReplyField(
                    hint = "Reply to ${item.title.ifBlank { item.appName }}",
                    onSend = onReply
                )
            }

            // A phone call on the peer that it lets this device pick up or end
            if (item.callState.isNotEmpty()) {
                val isRinging = item.callState == CallState.RINGING
                Spacer(modifier = Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (isRinging) {
                        Button(
                            onClick = onAnswerCall,
                            colors = ButtonDefaults.buttonColors(containerColor = EmeraldCharging, contentColor = Color.White),
                            modifier = Modifier.testTag("peer_call_answer_btn")
                        ) {
                            Icon(Icons.Default.Call, contentDescription = "Answer", modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Answer")
                        }
                    }
                    OutlinedButton(
                        onClick = onEndCall,
                        modifier = Modifier.testTag("peer_call_end_btn")
                    ) {
                        Icon(Icons.Default.CallEnd, contentDescription = "End call", tint = RoseAlert, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(if (isRinging) "Decline" else "Hang up")
                    }
                }
            }
        }
    }
}

@Composable
private fun ReplyField(hint: String, onSend: (String) -> Unit) {
    var reply by remember { mutableStateOf("") }
    val focusManager = LocalFocusManager.current
    val send = {
        if (reply.isNotBlank()) {
            onSend(reply)
            reply = ""
            focusManager.clearFocus()
        }
    }

    OutlinedTextField(
        value = reply,
        onValueChange = { reply = it },
        placeholder = {
            Text(text = hint, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        },
        textStyle = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp),
        maxLines = 3,
        shape = RoundedCornerShape(12.dp),
        keyboardOptions = KeyboardOptions(
            capitalization = KeyboardCapitalization.Sentences,
            imeAction = ImeAction.Send
        ),
        keyboardActions = KeyboardActions(onSend = { send() }),
        trailingIcon = {
            IconButton(
                onClick = send,
                enabled = reply.isNotBlank(),
                modifier = Modifier.testTag("peer_notification_reply_send")
            ) {
                Icon(
                    imageVector = Icons.Default.Send,
                    contentDescription = "Send reply",
                    tint = if (reply.isNotBlank()) CyanAccent else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                    modifier = Modifier.size(18.dp)
                )
            }
        },
        modifier = Modifier
            .fillMaxWidth()
            .testTag("peer_notification_reply_input")
    )
}
