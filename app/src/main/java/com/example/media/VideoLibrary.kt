package com.example.media

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.net.wifi.WifiManager
import android.os.Build
import android.os.ParcelFileDescriptor
import android.os.PowerManager
import android.provider.MediaStore
import android.util.Log
import android.util.Size
import androidx.core.content.ContextCompat
import com.example.model.SharedVideo
import com.example.model.VideoAccess
import java.io.ByteArrayOutputStream

/**
 * The videos on this device that a paired device may watch. Nothing is shared until the user turns
 * on "Share My Videos" here and grants the video permission. Files are only ever read, never changed.
 */
class VideoLibrary(private val context: Context) {

    private val prefs = context.getSharedPreferences("pulselink_prefs", Context.MODE_PRIVATE)

    // A stream has to keep going with the screen off, which is how a phone left somewhere usually is
    private val wakeLock = (context.getSystemService(Context.POWER_SERVICE) as PowerManager)
        .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "PulseLink:videoStream")

    @Suppress("DEPRECATION")
    private val wifiLock = (context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager)
        .createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, "PulseLink:videoStream")

    var isSharingEnabled: Boolean
        get() = prefs.getBoolean(KEY_SHARE_VIDEOS, false)
        set(value) = prefs.edit().putBoolean(KEY_SHARE_VIDEOS, value).apply()

    fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, PERMISSION) == PackageManager.PERMISSION_GRANTED

    // Why a paired device may not watch videos right now, or null when it may
    fun refusal(): VideoAccess? = when {
        !isSharingEnabled -> VideoAccess.DISABLED
        !hasPermission() -> VideoAccess.NO_PERMISSION
        else -> null
    }

    /**
     * @return every video on this device. The device showing them decides how to group and sort them.
     */
    fun list(): List<SharedVideo> {
        val projection = arrayOf(
            MediaStore.Video.Media._ID,
            MediaStore.Video.Media.DISPLAY_NAME,
            MediaStore.Video.Media.BUCKET_DISPLAY_NAME,
            MediaStore.Video.Media.DURATION,
            MediaStore.Video.Media.SIZE,
            MediaStore.Video.Media.WIDTH,
            MediaStore.Video.Media.HEIGHT,
            MediaStore.Video.Media.DATE_ADDED,
            MediaStore.Video.Media.BUCKET_ID
        )
        val videos = mutableListOf<SharedVideo>()
        context.contentResolver.query(
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
            projection,
            null,
            null,
            "${MediaStore.Video.Media.DATE_ADDED} DESC"
        )?.use { cursor ->
            while (cursor.moveToNext()) {
                videos += SharedVideo(
                    id = cursor.getLong(0),
                    name = cursor.getString(1).orEmpty(),
                    folder = cursor.getString(2).orEmpty(),
                    durationMs = cursor.getLong(3),
                    sizeBytes = cursor.getLong(4),
                    width = cursor.getInt(5),
                    height = cursor.getInt(6),
                    dateAddedSeconds = cursor.getLong(7),
                    folderId = cursor.getLong(8)
                )
            }
        }
        return videos
    }

    /**
     * @return the video's file opened for reading, or null when it is gone. The caller closes it.
     */
    fun open(id: Long): ParcelFileDescriptor? = try {
        context.contentResolver.openFileDescriptor(uriOf(id), "r")
    } catch (e: Exception) {
        Log.e(TAG, "Error opening video $id", e)
        null
    }

    fun mimeType(id: Long): String = context.contentResolver.getType(uriOf(id)) ?: "application/octet-stream"

    /**
     * @return a small JPEG picture of the video, or null when Android cannot make one
     */
    fun thumbnail(id: Long): ByteArray? = try {
        val bitmap = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            context.contentResolver.loadThumbnail(uriOf(id), Size(THUMBNAIL_WIDTH, THUMBNAIL_HEIGHT), null)
        } else {
            @Suppress("DEPRECATION")
            MediaStore.Video.Thumbnails.getThumbnail(
                context.contentResolver,
                id,
                MediaStore.Video.Thumbnails.MINI_KIND,
                null
            )
        }
        bitmap?.let {
            ByteArrayOutputStream().use { jpeg ->
                it.compress(Bitmap.CompressFormat.JPEG, THUMBNAIL_QUALITY, jpeg)
                jpeg.toByteArray()
            }
        }
    } catch (e: Exception) {
        Log.w(TAG, "No thumbnail for video $id: ${e.message}")
        null
    }

    /**
     * Keeps the CPU and Wi-Fi running at full speed for as long as a stream lasts.
     */
    inline fun <T> whileStreaming(stream: () -> T): T {
        holdAwake(true)
        try {
            return stream()
        } finally {
            holdAwake(false)
        }
    }

    @PublishedApi
    internal fun holdAwake(hold: Boolean) {
        // Both locks count, so overlapping streams keep them until the last one ends
        if (hold) {
            wakeLock.acquire()
            wifiLock.acquire()
        } else {
            wifiLock.release()
            wakeLock.release()
        }
    }

    private fun uriOf(id: Long): Uri = ContentUris.withAppendedId(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, id)

    companion object {
        private const val TAG = "VideoLibrary"
        private const val KEY_SHARE_VIDEOS = "share_videos"
        private const val THUMBNAIL_WIDTH = 320
        private const val THUMBNAIL_HEIGHT = 180
        private const val THUMBNAIL_QUALITY = 75

        val PERMISSION: String = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_VIDEO
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }
    }
}
