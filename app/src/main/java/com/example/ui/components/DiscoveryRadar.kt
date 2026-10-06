package com.example.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.CellTower
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Radar
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Router
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.WifiTethering
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.entity.PeerEntity
import com.example.model.PeerDevice
import com.example.ui.theme.Amber5G
import com.example.ui.theme.CyanAccent
import com.example.ui.theme.ElectricIndigo
import com.example.ui.theme.EmeraldGlow
import com.example.ui.theme.RoseAlert

@Composable
fun DiscoveryRadar(
    isScanning: Boolean,
    discoveredPeers: List<PeerDevice>,
    savedPeers: List<PeerEntity>,
    selectedPeer: PeerDevice?,
    onStartScan: () -> Unit,
    onSelectPeer: (PeerDevice) -> Unit,
    onDirectIpClick: () -> Unit,
    onHotspotGuideClick: () -> Unit,
    onPingPeer: (ip: String, port: Int) -> Unit,
    onDeleteSavedPeer: (ip: String) -> Unit,
    onToggleFavorite: (ip: String, isFav: Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "radarPulse")
    val radarScale1 by infiniteTransition.animateFloat(
        initialValue = 0.8f,
        targetValue = 1.4f,
        animationSpec = infiniteRepeatable(
            animation = tween(1800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "radar1"
    )
    val radarAlpha1 by infiniteTransition.animateFloat(
        initialValue = 0.6f,
        targetValue = 0.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "alpha1"
    )

    Column(modifier = modifier.fillMaxWidth()) {
        // Radar Banner Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Animated Radar Visual
                Box(
                    modifier = Modifier
                        .size(100.dp)
                        .padding(10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    if (isScanning) {
                        Box(
                            modifier = Modifier
                                .size(80.dp)
                                .scale(radarScale1)
                                .clip(CircleShape)
                                .background(CyanAccent.copy(alpha = radarAlpha1))
                        )
                    }

                    Box(
                        modifier = Modifier
                            .size(60.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .border(2.dp, CyanAccent.copy(alpha = 0.8f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Radar,
                            contentDescription = "Radar",
                            tint = CyanAccent,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }

                Text(
                    text = if (isScanning) "Searching LAN & Hotspot..." else "LAN & Hotspot Discovery",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "Finds another phone running PulseLink on the same Wi-Fi or Hotspot",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Actions: Scan + Direct IP + Hotspot Guide
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilledTonalButton(
                        onClick = onStartScan,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("radar_scan_btn")
                    ) {
                        if (isScanning) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Scanning...")
                        } else {
                            Icon(Icons.Default.Refresh, contentDescription = "Scan", modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Scan Network")
                        }
                    }

                    OutlinedButton(
                        onClick = onDirectIpClick,
                        modifier = Modifier.testTag("direct_ip_btn")
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "Add IP", modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("+ IP")
                    }

                    OutlinedButton(
                        onClick = onHotspotGuideClick,
                        modifier = Modifier.testTag("hotspot_guide_btn")
                    ) {
                        Icon(Icons.Default.WifiTethering, contentDescription = "Hotspot", modifier = Modifier.size(16.dp))
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Discovered Devices Section
        Text(
            text = "DISCOVERED PEERS (${discoveredPeers.size})",
            style = MaterialTheme.typography.labelSmall.copy(
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            ),
            color = MaterialTheme.colorScheme.primary
        )

        Spacer(modifier = Modifier.height(8.dp))

        if (discoveredPeers.isEmpty()) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "No other phones detected on this subnet yet.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "Ensure both phones are on the same Wi-Fi or one phone turns on Mobile Hotspot and the other connects to it.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                discoveredPeers.forEach { peer ->
                    val isSelected = selectedPeer?.ip == peer.ip
                    PeerItemCard(
                        name = peer.name,
                        ip = peer.ip,
                        port = peer.port,
                        isSelected = isSelected,
                        onSelect = { onSelectPeer(peer) },
                        onPing = { onPingPeer(peer.ip, peer.port) }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Saved Devices Section
        if (savedPeers.isNotEmpty()) {
            Text(
                text = "SAVED & PAST DEVICES (${savedPeers.size})",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                ),
                color = MaterialTheme.colorScheme.primary
            )

            Spacer(modifier = Modifier.height(8.dp))

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                savedPeers.forEach { entity ->
                    val isSelected = selectedPeer?.ip == entity.ip
                    PeerItemCard(
                        name = entity.deviceName,
                        ip = entity.ip,
                        port = entity.port,
                        isSelected = isSelected,
                        isFavorite = entity.isFavorite,
                        onSelect = {
                            onSelectPeer(
                                PeerDevice(
                                    id = entity.ip,
                                    name = entity.deviceName,
                                    ip = entity.ip,
                                    port = entity.port,
                                    model = entity.deviceModel,
                                    isFavorite = entity.isFavorite
                                )
                            )
                        },
                        onPing = { onPingPeer(entity.ip, entity.port) },
                        onDelete = { onDeleteSavedPeer(entity.ip) },
                        onToggleFavorite = { onToggleFavorite(entity.ip, !entity.isFavorite) }
                    )
                }
            }
        }
    }
}

@Composable
private fun PeerItemCard(
    name: String,
    ip: String,
    port: Int,
    isSelected: Boolean,
    isFavorite: Boolean = false,
    onSelect: () -> Unit,
    onPing: () -> Unit,
    onDelete: (() -> Unit)? = null,
    onToggleFavorite: (() -> Unit)? = null
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onSelect() },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected)
                CyanAccent.copy(alpha = 0.15f)
            else
                MaterialTheme.colorScheme.surface
        ),
        border = if (isSelected) androidx.compose.foundation.BorderStroke(1.5.dp, CyanAccent) else null,
        // Flat when selected: a shadow would show through the translucent container as an inner box
        elevation = CardDefaults.cardElevation(defaultElevation = if (isSelected) 0.dp else 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(if (isSelected) CyanAccent else ElectricIndigo.copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.PhoneAndroid,
                        contentDescription = "Device",
                        tint = if (isSelected) Color.Black else ElectricIndigo,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = name,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        if (isSelected) {
                            Spacer(modifier = Modifier.width(6.dp))
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(CyanAccent.copy(alpha = 0.2f))
                                    .padding(horizontal = 4.dp, vertical = 1.dp)
                            ) {
                                Text(
                                    text = "ACTIVE",
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Black,
                                    color = CyanAccent
                                )
                            }
                        }
                    }
                    Text(
                        text = "$ip:$port",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onPing, modifier = Modifier.size(32.dp)) {
                    Icon(
                        imageVector = Icons.Default.Speed,
                        contentDescription = "Ping",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                }

                if (onToggleFavorite != null) {
                    IconButton(onClick = onToggleFavorite, modifier = Modifier.size(32.dp)) {
                        Icon(
                            imageVector = if (isFavorite) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                            contentDescription = "Favorite",
                            tint = if (isFavorite) Amber5G else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                if (onDelete != null) {
                    IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Delete",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }
    }
}
