package com.example.call

import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import android.util.Log

/**
 * The Bluetooth headset or earbuds a phone call on this device would be heard and spoken through.
 */
object BluetoothHeadset {

    private const val TAG = "BluetoothHeadset"

    // The kinds that carry both directions of a call; a music-only Bluetooth speaker is not among them
    private val CALL_TYPES = buildSet {
        add(AudioDeviceInfo.TYPE_BLUETOOTH_SCO)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) add(AudioDeviceInfo.TYPE_HEARING_AID)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) add(AudioDeviceInfo.TYPE_BLE_HEADSET)
    }

    /**
     * @return the name of the connected headset, or null when there is none
     */
    fun connectedName(context: Context): String? {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return null
        return try {
            audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
                .firstOrNull { it.type in CALL_TYPES }
                ?.let { it.productName?.toString()?.trim().orEmpty().ifEmpty { "Bluetooth headset" } }
        } catch (e: Exception) {
            Log.e(TAG, "Error reading audio devices", e)
            null
        }
    }
}
