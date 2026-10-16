package com.example.call

import android.bluetooth.BluetoothSocket
import java.io.Closeable
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.Socket

/**
 * The two-way connection a call's sound travels on, over Wi-Fi or over Bluetooth.
 */
class AudioLink(
    val input: InputStream,
    val output: OutputStream,
    val isBluetooth: Boolean,
    private val connection: Closeable
) : Closeable {

    override fun close() {
        try {
            connection.close()
        } catch (e: IOException) {
            // already closed
        }
    }

    companion object {
        const val SILENT_PEER_TIMEOUT_MS = 5000

        fun overWifi(socket: Socket): AudioLink {
            socket.soTimeout = SILENT_PEER_TIMEOUT_MS
            socket.tcpNoDelay = true
            return AudioLink(socket.getInputStream(), socket.getOutputStream(), false, socket)
        }

        fun overBluetooth(socket: BluetoothSocket): AudioLink {
            return AudioLink(socket.inputStream, socket.outputStream, true, socket)
        }
    }
}
