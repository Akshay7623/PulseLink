package com.example.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Backspace
import androidx.compose.material.icons.filled.BluetoothConnected
import androidx.compose.material.icons.filled.BluetoothDisabled
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.Dialpad
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.call.BluetoothSoundState
import com.example.call.RelayState
import com.example.model.PeerDevice
import com.example.ui.theme.EmeraldCharging
import com.example.ui.theme.RoseAlert

private const val MAX_NUMBER_LENGTH = 16
private const val MIN_DIGITS_TO_CALL = 3
private const val BACKSPACE = "⌫"

private val DIAL_KEYS = listOf(
    listOf("1", "2", "3"),
    listOf("4", "5", "6"),
    listOf("7", "8", "9"),
    listOf("+", "0", BACKSPACE)
)

/**
 * A dial pad that places calls from the connected device's SIM, with buttons to pick up or end a
 * call on that device. The call itself happens on that device; with "Hear and talk on this phone"
 * its sound is relayed here.
 */
@Composable
fun DialPadCard(
    peer: PeerDevice?,
    isPaired: Boolean,
    isHearingHere: Boolean,
    callSound: RelayState,
    isLoudspeakerOn: Boolean,
    peerHeadset: String?,
    bluetoothSound: BluetoothSoundState,
    onDial: (number: String, speaker: Boolean) -> Unit,
    onAnswer: () -> Unit,
    onHangUp: () -> Unit,
    onEditPairingCode: () -> Unit,
    onToggleHearHere: (Boolean) -> Unit,
    onStartCallSound: () -> Unit,
    onStopCallSound: () -> Unit,
    onToggleLoudspeaker: (Boolean) -> Unit,
    onToggleBluetoothSound: (Boolean) -> Unit,
    onSelectPeerClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var number by rememberSaveable { mutableStateOf("") }
    var speaker by rememberSaveable { mutableStateOf(false) }
    val canCall = number.count { it.isDigit() } >= MIN_DIGITS_TO_CALL

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Text(
                text = "REMOTE DIAL PAD",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                ),
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = if (peer != null) "Call from ${peer.name}" else "No Peer Connected",
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

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
                            imageVector = Icons.Default.Dialpad,
                            contentDescription = "No Peer",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(36.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Connect a mobile device to make and answer calls on it from here",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        FilledTonalButton(
                            onClick = onSelectPeerClick,
                            modifier = Modifier.testTag("dial_select_peer_button")
                        ) {
                            Text("Discover Nearby Mobile Devices")
                        }
                    }
                }
            } else {
                Text(
                    text = "${peer.name} makes the call from its SIM, and you talk through a Bluetooth headset paired with it. " +
                        "It needs \"Allow Remote Calls\" turned on in its PulseLink settings.",
                    fontSize = 11.sp,
                    lineHeight = 15.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // The other phone only obeys a device that knows its pairing code
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (isPaired) "Pairing code saved" else "Pairing code of ${peer.name} needed",
                        modifier = Modifier.weight(1f),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (isPaired) EmeraldCharging else RoseAlert
                    )
                    TextButton(
                        onClick = onEditPairingCode,
                        modifier = Modifier.testTag("dial_pairing_code_btn")
                    ) {
                        Text(if (isPaired) "Change" else "Enter code")
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                // Number being dialled
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                ) {
                    Text(
                        text = number.ifEmpty { "Enter number" },
                        modifier = Modifier
                            .padding(horizontal = 16.dp, vertical = 16.dp)
                            .testTag("dial_number"),
                        fontSize = 26.sp,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 1.sp,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = if (number.isEmpty()) {
                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        }
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    DIAL_KEYS.forEach { row ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            row.forEach { key ->
                                DialKey(
                                    key = key,
                                    onClick = {
                                        number = when {
                                            key == BACKSPACE -> number.dropLast(1)
                                            number.length >= MAX_NUMBER_LENGTH -> number
                                            // Only an international number starts with +, and only once
                                            key == "+" -> if (number.isEmpty()) "+" else number
                                            else -> number + key
                                        }
                                    },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Where the call will be heard and spoken: the peer's Bluetooth headset, or its loudspeaker.
                // A connected headset comes first. Without one the relay, when it is on, needs the
                // loudspeaker, so there is nothing to choose.
                val hasHeadset = !peerHeadset.isNullOrEmpty()
                if (!isHearingHere || hasHeadset) {
                    if (peerHeadset != null) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = if (hasHeadset) Icons.Default.BluetoothConnected else Icons.Default.BluetoothDisabled,
                                contentDescription = "Bluetooth",
                                tint = if (hasHeadset) EmeraldCharging else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (hasHeadset) {
                                    "$peerHeadset is connected to ${peer.name}: you talk and listen through it"
                                } else {
                                    "No Bluetooth headset is connected to ${peer.name}: the call will play on its loudspeaker"
                                },
                                modifier = Modifier.testTag("dial_headset_status"),
                                fontSize = 12.sp,
                                lineHeight = 16.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = if (hasHeadset) EmeraldCharging else MaterialTheme.colorScheme.onSurface
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Use loudspeaker",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "Play the call out loud on ${peer.name} even when a Bluetooth headset is connected to it",
                                fontSize = 11.sp,
                                lineHeight = 15.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = speaker,
                            onCheckedChange = { speaker = it },
                            modifier = Modifier.testTag("dial_speaker_toggle")
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Hear and talk on this phone",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = if (hasHeadset) {
                                "Not used while the call is on ${peer.name}'s Bluetooth headset, only when it is on its loudspeaker"
                            } else {
                                "Experimental: ${peer.name} plays the call on its loudspeaker and the sound is relayed here over Wi-Fi or Bluetooth"
                            },
                            fontSize = 11.sp,
                            lineHeight = 15.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = isHearingHere,
                        onCheckedChange = onToggleHearHere,
                        modifier = Modifier.testTag("dial_hear_here_toggle")
                    )
                }

                // The same relayed sound, carried straight between the two phones instead of over Wi-Fi
                if (isHearingHere) {
                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Use Bluetooth between the phones",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = bluetoothSoundHint(bluetoothSound, peer.name),
                                modifier = Modifier.testTag("dial_bluetooth_status"),
                                fontSize = 11.sp,
                                lineHeight = 15.sp,
                                color = if (bluetoothSound.status == BluetoothSoundState.Status.READY) {
                                    EmeraldCharging
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                }
                            )
                        }
                        Switch(
                            checked = bluetoothSound.status != BluetoothSoundState.Status.OFF,
                            onCheckedChange = onToggleBluetoothSound,
                            modifier = Modifier.testTag("dial_bluetooth_toggle")
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                Button(
                    onClick = { onDial(number, speaker) },
                    enabled = canCall,
                    colors = ButtonDefaults.buttonColors(containerColor = EmeraldCharging, contentColor = Color.White),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .testTag("dial_call_btn")
                ) {
                    Icon(Icons.Default.Call, contentDescription = "Call", modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Call", fontWeight = FontWeight.Bold)
                }

                Spacer(modifier = Modifier.height(16.dp))

                // A call that is already ringing or in progress on the peer
                Text(
                    text = "CALL ON ${peer.name.uppercase()}",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    ),
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = onAnswer,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("dial_answer_btn")
                    ) {
                        Icon(Icons.Default.Call, contentDescription = "Answer", tint = EmeraldCharging, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Answer")
                    }

                    OutlinedButton(
                        onClick = onHangUp,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("dial_hangup_btn")
                    ) {
                        Icon(Icons.Default.CallEnd, contentDescription = "Hang up", tint = RoseAlert, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Hang up")
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = "Answer picks up a call ringing there. Hang up ends the call, or declines one that is ringing.",
                    fontSize = 11.sp,
                    lineHeight = 15.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                if (isHearingHere) {
                    Spacer(modifier = Modifier.height(12.dp))
                    CallSoundPanel(
                        peerName = peer.name,
                        callSound = callSound,
                        isLoudspeakerOn = isLoudspeakerOn,
                        onStart = onStartCallSound,
                        onStop = onStopCallSound,
                        onToggleLoudspeaker = onToggleLoudspeaker
                    )
                }
            }
        }
    }
}

// Which way the call sound will travel, or what still stands between it and Bluetooth
private fun bluetoothSoundHint(state: BluetoothSoundState, peerName: String): String = when (state.status) {
    BluetoothSoundState.Status.OFF ->
        "Carry the call sound over Bluetooth instead of Wi-Fi when this phone and $peerName are paired in Android's Bluetooth settings"
    BluetoothSoundState.Status.NO_PERMISSION -> "Nearby devices permission is not granted yet. Using Wi-Fi."
    BluetoothSoundState.Status.BLUETOOTH_OFF -> "Bluetooth is switched off on this phone. Using Wi-Fi."
    BluetoothSoundState.Status.PEER_OUTDATED -> "Update PulseLink on $peerName to use Bluetooth. Using Wi-Fi."
    BluetoothSoundState.Status.PEER_NOT_ACCEPTING ->
        "$peerName is not taking Bluetooth connections: it needs Bluetooth on, \"Relay Call Sound\" on and the Nearby devices permission. Using Wi-Fi."
    BluetoothSoundState.Status.NOT_PAIRED ->
        "Pair this phone with $peerName in Android's Bluetooth settings. Until then the sound uses Wi-Fi."
    BluetoothSoundState.Status.READY ->
        "Paired with ${state.partner}: the call sound goes over Bluetooth, and over Wi-Fi if that fails"
}

// Whether this phone's microphone and speaker are on the peer's call, and the controls for that
@Composable
private fun CallSoundPanel(
    peerName: String,
    callSound: RelayState,
    isLoudspeakerOn: Boolean,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onToggleLoudspeaker: (Boolean) -> Unit
) {
    val isOff = callSound.status == RelayState.Status.OFF

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = when (callSound.status) {
                    RelayState.Status.ACTIVE ->
                        "On the call over ${if (callSound.viaBluetooth) "Bluetooth" else "Wi-Fi"}: this phone's microphone and speaker are live"
                    RelayState.Status.CONNECTING -> "Connecting to the call sound on $peerName..."
                    RelayState.Status.OFF -> callSound.message.ifEmpty { "Call sound is not connected" }
                },
                modifier = Modifier.testTag("call_sound_status"),
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (callSound.status == RelayState.Status.ACTIVE) EmeraldCharging else MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(
                    onClick = if (isOff) onStart else onStop,
                    modifier = Modifier.testTag("call_sound_btn")
                ) {
                    Text(if (isOff) "Connect sound" else "Stop sound")
                }
                Spacer(modifier = Modifier.weight(1f))
                Text(text = "Loudspeaker", fontSize = 12.sp)
                Spacer(modifier = Modifier.width(8.dp))
                Switch(
                    checked = isLoudspeakerOn,
                    onCheckedChange = onToggleLoudspeaker,
                    modifier = Modifier.testTag("call_sound_loudspeaker_toggle")
                )
            }
        }
    }
}

@Composable
private fun DialKey(key: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        onClick = onClick,
        modifier = modifier
            .height(56.dp)
            .testTag("dial_key_$key"),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
    ) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            if (key == BACKSPACE) {
                Icon(
                    imageVector = Icons.Default.Backspace,
                    contentDescription = "Delete digit",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(22.dp)
                )
            } else {
                Text(
                    text = key,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}
