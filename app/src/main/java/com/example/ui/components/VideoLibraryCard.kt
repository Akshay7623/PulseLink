package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.model.PeerDevice
import com.example.model.PeerVideoLibrary
import com.example.model.SharedVideo
import com.example.model.VideoAccess
import com.example.network.PulseClient
import com.example.security.StreamTokens
import com.example.ui.theme.EmeraldCharging
import com.example.ui.theme.RoseAlert

/**
 * The top of the Videos tab: which phone the videos are on, what is stopping them from showing, and
 * a search box. The videos themselves are separate items of the page's list, so a phone with
 * hundreds of them still scrolls smoothly.
 */
@Composable
fun VideoLibraryCard(
    peer: PeerDevice?,
    library: PeerVideoLibrary,
    isPaired: Boolean,
    search: String,
    onSearchChange: (String) -> Unit,
    onRefresh: () -> Unit,
    onEditPairingCode: () -> Unit,
    onSelectPeerClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "REMOTE VIDEOS",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        ),
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = if (peer != null) "Videos on ${peer.name}" else "No Peer Connected",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (peer != null) {
                    if (library.isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier
                                .padding(12.dp)
                                .size(24.dp),
                            strokeWidth = 2.dp
                        )
                    } else {
                        IconButton(
                            onClick = onRefresh,
                            modifier = Modifier.testTag("videos_refresh_btn")
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = "Refresh Videos")
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            if (peer == null) {
                VideoLibraryNotice(
                    text = "Connect a mobile device to watch the videos stored on it",
                    buttonText = "Discover Nearby Mobile Devices",
                    onButtonClick = onSelectPeerClick,
                    buttonTag = "videos_select_peer_button"
                )
                return@Column
            }

            // The other phone only lets in a device that knows its pairing code
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
                    modifier = Modifier.testTag("videos_pairing_code_btn")
                ) {
                    Text(if (isPaired) "Change" else "Enter code")
                }
            }

            val notice = when (library.access) {
                VideoAccess.DISABLED ->
                    "Turn on \"Share My Videos\" in PulseLink settings on ${peer.name}."
                VideoAccess.NO_PERMISSION ->
                    "${peer.name} has not given PulseLink access to its videos. Open its PulseLink settings and allow it."
                VideoAccess.NOT_PAIRED ->
                    if (isPaired) {
                        "This pairing code does not match. Check the code under \"Share My Videos\" in PulseLink settings on ${peer.name}."
                    } else {
                        "Enter the code shown under \"Share My Videos\" in PulseLink settings on ${peer.name}."
                    }
                VideoAccess.UNSUPPORTED -> "Update PulseLink on ${peer.name} to watch its videos."
                VideoAccess.FAILED ->
                    "Could not reach ${peer.name}. Check that it is switched on and online, with PulseLink's Background Listener Service on."
                VideoAccess.OK -> if (library.videos.isEmpty() && !library.isLoading) "No videos found on ${peer.name}." else null
                null -> null
            }

            if (notice != null) {
                Spacer(modifier = Modifier.height(6.dp))
                VideoLibraryNotice(text = notice)
            }

            if (library.access == VideoAccess.OK && library.videos.isNotEmpty()) {
                Text(
                    text = (if (library.videos.size == 1) "1 video" else "${library.videos.size} videos") +
                        ". Watching uses data on both phones, up to the size of the file.",
                    fontSize = 11.sp,
                    lineHeight = 15.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(10.dp))
                OutlinedTextField(
                    value = search,
                    onValueChange = onSearchChange,
                    placeholder = { Text("Search by name or folder") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("videos_search_input")
                )
            }
        }
    }
}

@Composable
private fun VideoLibraryNotice(
    text: String,
    buttonText: String? = null,
    onButtonClick: () -> Unit = {},
    buttonTag: String = ""
) {
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
                imageVector = Icons.Default.VideoLibrary,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(36.dp)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            if (buttonText != null) {
                Spacer(modifier = Modifier.height(12.dp))
                FilledTonalButton(
                    onClick = onButtonClick,
                    modifier = Modifier.testTag(buttonTag)
                ) {
                    Text(buttonText)
                }
            }
        }
    }
}

/**
 * One video on the connected device. Tapping it starts playing.
 */
@Composable
fun VideoRow(
    video: SharedVideo,
    peer: PeerDevice,
    token: String,
    onPlay: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onPlay)
            .testTag("video_row_${video.id}"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Row(
            modifier = Modifier.padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .width(112.dp)
                    .height(64.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.PlayCircle,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(PulseClient.videoThumbnailUrl(peer.ip, peer.port, video.id))
                        .addHeader(StreamTokens.HEADER, token)
                        .crossfade(true)
                        .build(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = video.name,
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = listOf(video.durationText, video.sizeText, video.qualityText)
                        .filter { it.isNotEmpty() }
                        .joinToString(" · "),
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (video.folder.isNotEmpty()) {
                    Text(
                        text = video.folder,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}
