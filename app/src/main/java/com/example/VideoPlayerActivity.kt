package com.example

import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.os.Bundle
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.Tracks
import androidx.media3.common.VideoSize
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.upstream.DefaultLoadErrorHandlingPolicy
import androidx.media3.ui.PlayerView
import com.example.model.VideoAccess
import com.example.network.PulseClient
import com.example.security.StreamTokens
import com.example.ui.theme.PulseLinkTheme
import kotlinx.coroutines.launch

/**
 * Plays a video stored on the connected device, fetching only as much of it as is needed. Skipping
 * asks the other phone for that part of the file, and the position is remembered for next time.
 */
@UnstableApi
class VideoPlayerActivity : ComponentActivity() {

    private val app get() = application as PulseApplication

    // The other phone may be far away on a slow network, so this waits longer than the defaults
    private val httpFactory = DefaultHttpDataSource.Factory()
        .setConnectTimeoutMs(CONNECT_TIMEOUT_MS)
        .setReadTimeoutMs(READ_TIMEOUT_MS)

    private lateinit var player: ExoPlayer
    private var targetIp = ""
    private var targetPort = 0
    private var videoId = 0L
    private var hasMediaItem = false

    // What went wrong, shown over the video with a button to try again
    private val problem = mutableStateOf<String?>(null)
    private val areControlsVisible = mutableStateOf(true)

    private val positions by lazy { getSharedPreferences(PREFS_POSITIONS, Context.MODE_PRIVATE) }
    private val positionKey get() = "$targetIp:$videoId"

    private val playerListener = object : Player.Listener {
        override fun onPlayerError(error: PlaybackException) {
            problem.value = when (error.errorCode) {
                PlaybackException.ERROR_CODE_DECODER_INIT_FAILED,
                PlaybackException.ERROR_CODE_DECODER_QUERY_FAILED,
                PlaybackException.ERROR_CODE_DECODING_FAILED,
                PlaybackException.ERROR_CODE_DECODING_FORMAT_EXCEEDS_CAPABILITIES,
                PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED,
                PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED,
                PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED ->
                    "This phone cannot play this video's format."
                else -> "Lost the connection to the other phone."
            }
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            if (playbackState == Player.STATE_ENDED) {
                // Next time it starts from the beginning
                positions.edit().remove(positionKey).apply()
            }
        }

        override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
            if (playWhenReady) {
                window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            } else {
                window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            }
        }

        // Turns the screen to suit the video, whichever way the phone's rotation lock is set
        override fun onVideoSizeChanged(videoSize: VideoSize) {
            if (videoSize.width == 0 || videoSize.height == 0) return
            requestedOrientation = if (videoSize.width >= videoSize.height) {
                ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            } else {
                ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
            }
        }

        override fun onTracksChanged(tracks: Tracks) {
            // The player carries on with what it can play, so say why part of the video is missing
            listOf(C.TRACK_TYPE_AUDIO to "sound", C.TRACK_TYPE_VIDEO to "picture").forEach { (type, name) ->
                if (tracks.containsType(type) && !tracks.isTypeSupported(type)) {
                    Toast.makeText(this@VideoPlayerActivity, "This phone cannot play this video's $name format", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        WindowCompat.getInsetsController(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }

        targetIp = intent.getStringExtra(EXTRA_IP).orEmpty()
        targetPort = intent.getIntExtra(EXTRA_PORT, 8765)
        videoId = intent.getLongExtra(EXTRA_VIDEO_ID, 0L)
        val title = intent.getStringExtra(EXTRA_TITLE).orEmpty()

        player = ExoPlayer.Builder(this)
            .setMediaSourceFactory(
                DefaultMediaSourceFactory(httpFactory)
                    .setLoadErrorHandlingPolicy(DefaultLoadErrorHandlingPolicy(LOAD_RETRIES))
            )
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                    .build(),
                true
            )
            .setHandleAudioBecomingNoisy(true)
            .build()
        player.addListener(playerListener)

        setContent {
            PulseLinkTheme {
                val problemText by problem
                val showControls by areControlsVisible

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black)
                ) {
                    AndroidView(
                        factory = { context ->
                            PlayerView(context).apply {
                                player = this@VideoPlayerActivity.player
                                setShowBuffering(PlayerView.SHOW_BUFFERING_ALWAYS)
                                setShowSubtitleButton(true)
                                setShowNextButton(false)
                                setShowPreviousButton(false)
                                setControllerVisibilityListener(
                                    PlayerView.ControllerVisibilityListener { visibility ->
                                        areControlsVisible.value = visibility == android.view.View.VISIBLE
                                    }
                                )
                            }
                        },
                        modifier = Modifier.fillMaxSize()
                    )

                    if (showControls) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .safeDrawingPadding()
                                .padding(horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(onClick = { finish() }) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = "Back",
                                    tint = Color.White
                                )
                            }
                            Text(
                                text = title,
                                color = Color.White,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    problemText?.let { text ->
                        Column(
                            modifier = Modifier
                                .align(Alignment.Center)
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text(text = text, color = Color.White, textAlign = TextAlign.Center)
                            Button(onClick = { lifecycleScope.launch { connectAndPlay() } }) {
                                Text("Try Again")
                            }
                        }
                    }
                }
            }
        }

        lifecycleScope.launch { connectAndPlay() }
    }

    // Asks for a fresh pass every time, because the other phone forgets them when it restarts
    private suspend fun connectAndPlay() {
        problem.value = null
        val session = app.pulseClient.openVideoSession(targetIp, targetPort, app.pairing.partnerKey())
        if (session.access != VideoAccess.OK) {
            problem.value = when (session.access) {
                VideoAccess.DISABLED -> "\"Share My Videos\" is off on the other phone."
                VideoAccess.NO_PERMISSION -> "The other phone has not given PulseLink access to its videos."
                VideoAccess.NOT_PAIRED -> "The pairing code is wrong. Check it in the Videos tab."
                VideoAccess.UNSUPPORTED -> "Update PulseLink on the other phone to watch its videos."
                else -> "Could not reach the other phone."
            }
            return
        }

        httpFactory.setDefaultRequestProperties(mapOf(StreamTokens.HEADER to session.token))
        if (!hasMediaItem) {
            hasMediaItem = true
            player.setMediaItem(
                MediaItem.fromUri(PulseClient.videoStreamUrl(targetIp, targetPort, videoId)),
                positions.getLong(positionKey, 0L)
            )
        }
        player.prepare()
        player.play()
    }

    private fun savePosition() {
        val position = player.currentPosition
        val duration = player.duration
        // Barely started or nearly finished is not worth coming back to
        val isWorthKeeping = position > MIN_RESUME_POSITION_MS &&
            (duration == C.TIME_UNSET || position < duration - MIN_RESUME_REMAINING_MS)
        positions.edit().apply {
            if (isWorthKeeping) putLong(positionKey, position) else remove(positionKey)
        }.apply()
    }

    override fun onStop() {
        super.onStop()
        if (hasMediaItem) savePosition()
        player.pause()
    }

    override fun onDestroy() {
        super.onDestroy()
        player.removeListener(playerListener)
        player.release()
    }

    companion object {
        private const val EXTRA_IP = "ip"
        private const val EXTRA_PORT = "port"
        private const val EXTRA_VIDEO_ID = "video_id"
        private const val EXTRA_TITLE = "title"
        private const val PREFS_POSITIONS = "pulselink_video_positions"
        private const val CONNECT_TIMEOUT_MS = 15_000
        private const val READ_TIMEOUT_MS = 30_000
        private const val LOAD_RETRIES = 6
        private const val MIN_RESUME_POSITION_MS = 5_000L
        private const val MIN_RESUME_REMAINING_MS = 10_000L

        fun start(context: Context, ip: String, port: Int, videoId: Long, title: String) {
            context.startActivity(
                Intent(context, VideoPlayerActivity::class.java)
                    .putExtra(EXTRA_IP, ip)
                    .putExtra(EXTRA_PORT, port)
                    .putExtra(EXTRA_VIDEO_ID, videoId)
                    .putExtra(EXTRA_TITLE, title)
            )
        }
    }
}
