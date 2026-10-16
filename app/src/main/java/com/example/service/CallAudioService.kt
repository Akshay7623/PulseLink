package com.example.service

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.PulseApplication
import com.example.notification.PulseNotificationManager

/**
 * Runs on the device the user talks on while a paired device's call sound is relayed here, so the
 * microphone and speaker keep working when the screen turns off or the app is left.
 */
class CallAudioService : Service() {

    companion object {
        private const val TAG = "CallAudioService"
        private const val ACTION_START = "com.example.pulselink.callaudio.START"
        private const val ACTION_STOP = "com.example.pulselink.callaudio.STOP"
        private const val EXTRA_IP = "ip"
        private const val EXTRA_PORT = "port"
        private const val EXTRA_NAME = "name"
        private const val EXTRA_BLUETOOTH_NAME = "bluetooth_name"
        private const val NOTIFICATION_ID = 8889

        /**
         * Must be called while the app is on screen: Android refuses the microphone otherwise.
         * @param peerBluetoothName the peer's Bluetooth name as it reports it, null when it reports none
         */
        fun start(context: Context, ip: String, port: Int, peerName: String, peerBluetoothName: String?) {
            val intent = Intent(context, CallAudioService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_IP, ip)
                putExtra(EXTRA_PORT, port)
                putExtra(EXTRA_NAME, peerName)
                putExtra(EXTRA_BLUETOOTH_NAME, peerBluetoothName)
            }
            try {
                ContextCompat.startForegroundService(context, intent)
            } catch (e: Exception) {
                Log.e(TAG, "Error starting call sound", e)
            }
        }

        fun stop(context: Context) {
            (context.applicationContext as? PulseApplication)?.callAudioRelay?.stopClient()
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val app = application as? PulseApplication
        val ip = intent?.getStringExtra(EXTRA_IP)

        if (intent?.action != ACTION_START || app == null || ip == null) {
            // "Stop" on the notification, or a restart with nothing to do
            app?.callAudioRelay?.stopClient()
            stopSelf()
            return START_NOT_STICKY
        }

        val peerName = intent.getStringExtra(EXTRA_NAME) ?: ip
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                startForeground(NOTIFICATION_ID, notification(peerName), ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
            } else {
                startForeground(NOTIFICATION_ID, notification(peerName))
            }
        } catch (e: Exception) {
            // No microphone permission, or started while the app was not on screen
            Log.e(TAG, "Call sound cannot run in the background", e)
            stopSelf()
            return START_NOT_STICKY
        }

        app.callAudioRelay.startClient(
            ip = ip,
            port = intent.getIntExtra(EXTRA_PORT, 8765),
            peerName = peerName,
            pairingKey = app.pairing.partnerKey(),
            peerBluetoothName = intent.getStringExtra(EXTRA_BLUETOOTH_NAME),
            onFinished = { stopSelf() }
        )
        return START_NOT_STICKY
    }

    private fun notification(peerName: String): Notification {
        val openApp = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).apply { flags = Intent.FLAG_ACTIVITY_SINGLE_TOP },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val stop = PendingIntent.getService(
            this,
            1,
            Intent(this, CallAudioService::class.java).apply { action = ACTION_STOP },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, PulseNotificationManager.CHANNEL_SERVICE_ID)
            .setSmallIcon(android.R.drawable.stat_sys_phone_call)
            .setContentTitle("Call sound from $peerName")
            .setContentText("This phone's microphone and speaker are on the call")
            .setOngoing(true)
            .setContentIntent(openApp)
            .addAction(android.R.drawable.ic_delete, "Stop", stop)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }
}
