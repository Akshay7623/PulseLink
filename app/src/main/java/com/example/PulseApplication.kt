package com.example

import android.app.Application
import android.content.Context
import com.example.data.local.PulseDatabase
import com.example.data.repository.PulseRepository
import com.example.network.PulseClient
import com.example.network.PulseDiscovery
import com.example.network.PulseServer
import com.example.notification.PulseNotificationManager
import com.example.telemetry.TelemetryCollector

class PulseApplication : Application() {

    lateinit var database: PulseDatabase
        private set
    lateinit var repository: PulseRepository
        private set
    lateinit var telemetryCollector: TelemetryCollector
        private set
    lateinit var notificationManager: PulseNotificationManager
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
        telemetryCollector = TelemetryCollector(this)
        notificationManager = PulseNotificationManager(this)
        pulseServer = PulseServer(this, repository, telemetryCollector, notificationManager)
        pulseClient = PulseClient()
        pulseDiscovery = PulseDiscovery(this, telemetryCollector)

        // Automatically start server on default port 8765
        startPulseServer()
    }

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
