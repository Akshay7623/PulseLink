package com.example.network

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Build
import android.util.Log
import com.example.model.PeerDevice
import com.example.telemetry.TelemetryCollector
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.InetSocketAddress
import java.net.Socket

class PulseDiscovery(
    private val context: Context,
    private val telemetryCollector: TelemetryCollector
) {
    private val nsdManager = context.getSystemService(Context.NSD_SERVICE) as? NsdManager
    private val SERVICE_TYPE = "_pulselink._tcp."

    private val _discoveredPeers = MutableStateFlow<List<PeerDevice>>(emptyList())
    val discoveredPeers: StateFlow<List<PeerDevice>> = _discoveredPeers.asStateFlow()

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    private var registrationListener: NsdManager.RegistrationListener? = null
    private var discoveryListener: NsdManager.DiscoveryListener? = null
    private val scope = CoroutineScope(Dispatchers.IO + Job())
    private var scanJob: Job? = null

    fun registerService(port: Int, deviceName: String) {
        unregisterService()
        val serviceInfo = NsdServiceInfo().apply {
            serviceName = "PulseLink_${deviceName.replace(" ", "_")}"
            serviceType = SERVICE_TYPE
            setPort(port)
        }

        registrationListener = object : NsdManager.RegistrationListener {
            override fun onServiceRegistered(NsdServiceInfo: NsdServiceInfo) {
                Log.d("PulseDiscovery", "NSD Service registered: ${NsdServiceInfo.serviceName}")
            }
            override fun onRegistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                Log.e("PulseDiscovery", "NSD Registration failed: $errorCode")
            }
            override fun onServiceUnregistered(arg0: NsdServiceInfo) {}
            override fun onUnregistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {}
        }

        try {
            nsdManager?.registerService(serviceInfo, NsdManager.PROTOCOL_DNS_SD, registrationListener)
        } catch (e: Exception) {
            Log.e("PulseDiscovery", "Failed to register NSD", e)
        }
    }

    fun unregisterService() {
        try {
            registrationListener?.let {
                nsdManager?.unregisterService(it)
                registrationListener = null
            }
        } catch (e: Exception) {
            // ignore
        }
    }

    fun startDiscovery() {
        stopDiscovery()
        _isScanning.value = true

        discoveryListener = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(regType: String) {
                Log.d("PulseDiscovery", "Service discovery started")
            }

            override fun onServiceFound(service: NsdServiceInfo) {
                if (service.serviceType == SERVICE_TYPE || service.serviceType.contains("pulselink")) {
                    resolveService(service)
                }
            }

            override fun onServiceLost(service: NsdServiceInfo) {
                val current = _discoveredPeers.value.toMutableList()
                current.removeAll { it.name.contains(service.serviceName) }
                _discoveredPeers.value = current
            }

            override fun onDiscoveryStopped(serviceType: String) {
                _isScanning.value = false
            }

            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                _isScanning.value = false
            }

            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {
                _isScanning.value = false
            }
        }

        try {
            nsdManager?.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, discoveryListener)
        } catch (e: Exception) {
            Log.e("PulseDiscovery", "Error starting NSD discovery", e)
        }

        // Parallel fast subnet scan to catch devices where mDNS multicast might be blocked by router/hotspot
        scanSubnet()
    }

    private fun resolveService(serviceInfo: NsdServiceInfo) {
        val resolveListener = object : NsdManager.ResolveListener {
            override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                Log.e("PulseDiscovery", "Resolve failed: $errorCode")
            }

            override fun onServiceResolved(resolvedService: NsdServiceInfo) {
                val host = resolvedService.host?.hostAddress ?: return
                val localIp = telemetryCollector.getLocalIpAddress()
                if (host == localIp || host == "127.0.0.1") return

                val rawName = resolvedService.serviceName
                    .removePrefix("PulseLink_")
                    .replace("_", " ")

                addPeer(
                    PeerDevice(
                        id = host,
                        name = rawName,
                        ip = host,
                        port = resolvedService.port,
                        isOnline = true
                    )
                )
            }
        }

        try {
            nsdManager?.resolveService(serviceInfo, resolveListener)
        } catch (e: Exception) {
            // resolve error
        }
    }

    fun scanSubnet(port: Int = 8765) {
        scanJob?.cancel()
        scanJob = scope.launch {
            _isScanning.value = true
            val localIp = telemetryCollector.getLocalIpAddress()
            if (localIp != "127.0.0.1" && localIp.contains(".")) {
                val prefix = localIp.substringBeforeLast(".") + "."
                val localHostNum = localIp.substringAfterLast(".").toIntOrNull() ?: -1

                // Scan 1..254 concurrently in chunks
                val ipList = (1..254).filter { it != localHostNum }.map { "$prefix$it" }
                val client = PulseClient()

                val chunks = ipList.chunked(32)
                for (chunk in chunks) {
                    chunk.map { ip ->
                        async {
                            try {
                                val socket = Socket()
                                socket.connect(InetSocketAddress(ip, port), 400)
                                socket.close()

                                // Port is open, let's ping for device name
                                val pingRes = client.pingPeer(ip, port)
                                val name = when (pingRes) {
                                    is NetworkResult.Success -> pingRes.data.first
                                    else -> "Device ($ip)"
                                }
                                addPeer(
                                    PeerDevice(
                                        id = ip,
                                        name = name,
                                        ip = ip,
                                        port = port,
                                        isOnline = true
                                    )
                                )
                            } catch (e: Exception) {
                                // Port closed or host unreachable
                            }
                        }
                    }.awaitAll()
                }
            }
            delay(1000)
            _isScanning.value = false
        }
    }

    private fun addPeer(peer: PeerDevice) {
        val current = _discoveredPeers.value.toMutableList()
        val index = current.indexOfFirst { it.ip == peer.ip }
        if (index != -1) {
            current[index] = peer
        } else {
            current.add(peer)
        }
        _discoveredPeers.value = current
    }

    fun stopDiscovery() {
        try {
            discoveryListener?.let {
                nsdManager?.stopServiceDiscovery(it)
                discoveryListener = null
            }
            scanJob?.cancel()
            _isScanning.value = false
        } catch (e: Exception) {
            // ignore
        }
    }
}
