package com.example

import android.app.Application
import android.bluetooth.BluetoothAdapter
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.core.content.ContextCompat
import coil.ImageLoader
import coil.ImageLoaderFactory
import com.example.call.BluetoothSoundLink
import com.example.call.CallAudioRelay
import com.example.call.CallController
import com.example.data.local.PulseDatabase
import com.example.data.repository.PulseRepository
import com.example.media.VideoLibrary
import com.example.network.PulseClient
import com.example.network.PulseDiscovery
import com.example.network.PulseServer
import com.example.notification.NotificationForwarder
import com.example.notification.PulseNotificationManager
import com.example.security.Pairing
import com.example.security.StreamTokens
import com.example.telemetry.TelemetryCollector

class PulseApplication : Application(), ImageLoaderFactory {

    lateinit var database: PulseDatabase
        private set
    lateinit var repository: PulseRepository
        private set
    lateinit var telemetryCollector: TelemetryCollector
        private set
    lateinit var notificationManager: PulseNotificationManager
        private set
    lateinit var notificationForwarder: NotificationForwarder
        private set
    lateinit var callController: CallController
        private set
    lateinit var pairing: Pairing
        private set
    lateinit var bluetoothSoundLink: BluetoothSoundLink
        private set
    lateinit var callAudioRelay: CallAudioRelay
        private set
    lateinit var videoLibrary: VideoLibrary
        private set
    lateinit var streamTokens: StreamTokens
        private set
    lateinit var pulseServer: PulseServer
        private set
    lateinit var pulseClient: PulseClient
        private set
    lateinit var pulseDiscovery: PulseDiscovery
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this

        database = PulseDatabase.getInstance(this)
        repository = PulseRepository(database.pulseDao())
        telemetryCollector = TelemetryCollector(this) { bluetoothSoundLink.hostName() }
        notificationManager = PulseNotificationManager(this)
        pulseClient = PulseClient()
        notificationForwarder = NotificationForwarder(this, pulseClient, telemetryCollector) { pulseServer.serverPort.value }
        callController = CallController(this)
        pairing = Pairing(this)
        bluetoothSoundLink = BluetoothSoundLink(this, pairing)
        callAudioRelay = CallAudioRelay(this, callController, pulseClient, bluetoothSoundLink)
        videoLibrary = VideoLibrary(this)
        streamTokens = StreamTokens()
        pulseServer = PulseServer(
            this, repository, telemetryCollector, notificationManager, notificationForwarder, callController,
            pairing, callAudioRelay, videoLibrary, streamTokens
        )
        pulseDiscovery = PulseDiscovery(this, telemetryCollector)

        // Automatically start server on default port 8765
        startPulseServer()

        // The call sound is also taken over Bluetooth, which can only start once Bluetooth is switched on
        callAudioRelay.syncBluetoothHost()
        ContextCompat.registerReceiver(
            this,
            object : BroadcastReceiver() {
                override fun onReceive(context: Context, intent: Intent) = callAudioRelay.syncBluetoothHost()
            },
            IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED),
            ContextCompat.RECEIVER_EXPORTED
        )
    }

    // Video thumbnails come from the other phone, which works on a hotspot with no internet. Left to
    // itself the image loader would not even try while Android reports the phone as offline.
    override fun newImageLoader(): ImageLoader = ImageLoader.Builder(this)
        .networkObserverEnabled(false)
        .build()

    fun startPulseServer(port: Int = 8765, customName: String? = null) {
        val name = customName ?: getSharedPreferences("pulselink_prefs", Context.MODE_PRIVATE)
            .getString("custom_device_name", null)
            ?: telemetryCollector.getDeviceDisplayName()

        pulseServer.startServer(port, name)
        pulseDiscovery.registerService(port, name)
    }

    companion object {
        lateinit var instance: PulseApplication
            private set
    }
}
