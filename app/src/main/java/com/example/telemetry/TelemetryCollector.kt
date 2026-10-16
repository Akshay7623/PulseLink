package com.example.telemetry

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.example.call.BluetoothHeadset
import android.net.wifi.WifiInfo
import android.net.wifi.WifiManager
import android.os.BatteryManager
import android.os.Build
import android.os.Environment
import android.os.PowerManager
import android.os.StatFs
import android.os.SystemClock
import android.telephony.TelephonyManager
import com.example.model.DeviceTelemetry
import java.net.Inet4Address
import java.net.NetworkInterface
import java.util.Locale

class TelemetryCollector(
    private val context: Context,
    // This device's Bluetooth name while it takes the call sound connection over Bluetooth, empty otherwise
    private val bluetoothSoundHost: () -> String = { "" }
) {

    fun collectTelemetry(customDeviceName: String? = null): DeviceTelemetry {
        val batteryInfo = getBatteryInfo()
        val networkInfo = getNetworkInfo()
        val memoryInfo = getMemoryInfo()
        val storageInfo = getStorageInfo()
        val isScreenOn = getScreenState()
        val uptimeMinutes = SystemClock.elapsedRealtime() / (1000 * 60)

        val deviceName = customDeviceName ?: getDeviceDisplayName()

        return DeviceTelemetry(
            deviceName = deviceName,
            deviceModel = "${Build.MANUFACTURER.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }} ${Build.MODEL}",
            manufacturer = Build.MANUFACTURER,
            androidVersion = "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})",
            batteryLevel = batteryInfo.level,
            isCharging = batteryInfo.isCharging,
            chargePlugType = batteryInfo.plugType,
            batteryHealth = batteryInfo.health,
            batteryTempCelsius = batteryInfo.tempCelsius,
            batteryVoltageMv = batteryInfo.voltageMv,
            networkType = networkInfo.type,
            networkDetails = networkInfo.details,
            carrierName = networkInfo.carrier,
            signalStrengthPercent = networkInfo.signalStrengthPercent,
            ipAddress = getLocalIpAddress(),
            totalRamMb = memoryInfo.totalMb,
            availableRamMb = memoryInfo.availMb,
            totalStorageGb = storageInfo.totalGb,
            freeStorageGb = storageInfo.freeGb,
            uptimeMinutes = uptimeMinutes,
            isScreenOn = isScreenOn,
            // Empty rather than null: this device can tell, and none is connected
            bluetoothHeadset = BluetoothHeadset.connectedName(context).orEmpty(),
            bluetoothSoundHost = bluetoothSoundHost(),
            timestamp = System.currentTimeMillis()
        )
    }

    fun getDeviceDisplayName(): String {
        val model = Build.MODEL
        val brand = Build.MANUFACTURER.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
        return if (model.startsWith(brand, ignoreCase = true)) {
            model
        } else {
            "$brand $model"
        }
    }

    private data class BatteryData(
        val level: Int,
        val isCharging: Boolean,
        val plugType: String,
        val health: String,
        val tempCelsius: Float,
        val voltageMv: Int
    )

    private fun getBatteryInfo(): BatteryData {
        val ifilter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        val batteryStatus: Intent? = context.registerReceiver(null, ifilter)

        val level: Int = batteryStatus?.let { intent ->
            val rawLevel = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
            val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
            if (rawLevel >= 0 && scale > 0) {
                ((rawLevel.toFloat() / scale.toFloat()) * 100).toInt()
            } else 0
        } ?: 0

        val status: Int = batteryStatus?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                status == BatteryManager.BATTERY_STATUS_FULL

        val chargePlug: Int = batteryStatus?.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1) ?: -1
        val plugType = when (chargePlug) {
            BatteryManager.BATTERY_PLUGGED_AC -> "AC Fast Charger"
            BatteryManager.BATTERY_PLUGGED_USB -> "USB Cable"
            BatteryManager.BATTERY_PLUGGED_WIRELESS -> "Wireless Dock"
            else -> if (isCharging) "Charging" else "Unplugged"
        }

        val healthCode = batteryStatus?.getIntExtra(BatteryManager.EXTRA_HEALTH, BatteryManager.BATTERY_HEALTH_UNKNOWN) ?: 0
        val health = when (healthCode) {
            BatteryManager.BATTERY_HEALTH_GOOD -> "Good"
            BatteryManager.BATTERY_HEALTH_OVERHEAT -> "Overheating"
            BatteryManager.BATTERY_HEALTH_DEAD -> "Degraded"
            BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE -> "Over Voltage"
            BatteryManager.BATTERY_HEALTH_COLD -> "Cold"
            else -> "Normal"
        }

        val tempRaw = batteryStatus?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) ?: 0
        val tempCelsius = tempRaw / 10.0f

        val voltageMv = batteryStatus?.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 0) ?: 0

        return BatteryData(level, isCharging, plugType, health, tempCelsius, voltageMv)
    }

    private data class NetworkData(
        val type: String,
        val details: String,
        val carrier: String,
        val signalStrengthPercent: Int
    )

    private fun getNetworkInfo(): NetworkData {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        val activeNetwork = cm?.activeNetwork
        val caps = activeNetwork?.let { cm.getNetworkCapabilities(it) }

        val telephonyManager = context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
        val carrier = telephonyManager?.networkOperatorName?.ifBlank { "Mobile Operator" } ?: "Mobile Network"

        if (caps == null) {
            return NetworkData(
                type = "Disconnected",
                details = "No active internet / LAN connection",
                carrier = "None",
                signalStrengthPercent = 0
            )
        }

        if (caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
            val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
            val wifiInfo: WifiInfo? = wifiManager?.connectionInfo
            val linkSpeed = wifiInfo?.linkSpeed ?: -1
            val freq = wifiInfo?.frequency ?: 0
            val band = when {
                freq >= 5925 -> "6 GHz (Wi-Fi 6E/7)"
                freq >= 4900 -> "5 GHz"
                freq > 0 -> "2.4 GHz"
                else -> "Wi-Fi"
            }
            val speedText = if (linkSpeed > 0) " • $linkSpeed Mbps" else ""
            val rssi = wifiInfo?.rssi ?: -60
            val signalPercent = calculateRssiPercent(rssi)

            return NetworkData(
                type = "Wi-Fi ($band)",
                details = "Connected to Wi-Fi$speedText",
                carrier = "Wi-Fi Network",
                signalStrengthPercent = signalPercent
            )
        }

        if (caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)) {
            // Determine 5G vs 4G LTE vs 3G
            val is5G = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                // Check downstream bandwidth: 5G NR typically reports > 100 Mbps or 5G capability
                caps.linkDownstreamBandwidthKbps > 120_000
            } else false

            val netType = if (is5G) "5G (NR)" else "4G (LTE)"
            val bandwidthMbps = caps.linkDownstreamBandwidthKbps / 1000
            val bandwidthStr = if (bandwidthMbps > 0) " • ~$bandwidthMbps Mbps link" else ""

            return NetworkData(
                type = netType,
                details = "$carrier$bandwidthStr",
                carrier = carrier,
                signalStrengthPercent = 85
            )
        }

        if (caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)) {
            return NetworkData(
                type = "Ethernet",
                details = "Wired Connection",
                carrier = "LAN",
                signalStrengthPercent = 100
            )
        }

        return NetworkData(
            type = "Active Network",
            details = "Connected",
            carrier = carrier,
            signalStrengthPercent = 75
        )
    }

    private fun calculateRssiPercent(rssi: Int): Int {
        val minRssi = -100
        val maxRssi = -50
        return when {
            rssi <= minRssi -> 0
            rssi >= maxRssi -> 100
            else -> ((rssi - minRssi) * 100) / (maxRssi - minRssi)
        }
    }

    private data class MemoryData(val totalMb: Long, val availMb: Long)

    private fun getMemoryInfo(): MemoryData {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        val memoryInfo = ActivityManager.MemoryInfo()
        am?.getMemoryInfo(memoryInfo)
        val totalMb = memoryInfo.totalMem / (1024 * 1024)
        val availMb = memoryInfo.availMem / (1024 * 1024)
        return MemoryData(totalMb, availMb)
    }

    private data class StorageData(val totalGb: Double, val freeGb: Double)

    private fun getStorageInfo(): StorageData {
        return try {
            val path = Environment.getDataDirectory()
            val stat = StatFs(path.path)
            val blockSize = stat.blockSizeLong
            val totalBlocks = stat.blockCountLong
            val availableBlocks = stat.availableBlocksLong

            val totalGb = String.format(Locale.US, "%.1f", (totalBlocks * blockSize) / (1024.0 * 1024.0 * 1024.0)).toDouble()
            val freeGb = String.format(Locale.US, "%.1f", (availableBlocks * blockSize) / (1024.0 * 1024.0 * 1024.0)).toDouble()
            StorageData(totalGb, freeGb)
        } catch (e: Exception) {
            StorageData(64.0, 32.0)
        }
    }

    private fun getScreenState(): Boolean {
        val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        return pm?.isInteractive ?: true
    }

    fun getLocalIpAddress(): String {
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val iface = interfaces.nextElement()
                if (iface.isLoopback || !iface.isUp) continue
                val addresses = iface.inetAddresses
                while (addresses.hasMoreElements()) {
                    val addr = addresses.nextElement()
                    if (addr is Inet4Address && !addr.isLoopbackAddress) {
                        val host = addr.hostAddress ?: ""
                        if (host.startsWith("192.168.") || host.startsWith("10.") || host.startsWith("172.")) {
                            return host
                        }
                    }
                }
            }
        } catch (e: Exception) {
            // fallback
        }
        return "127.0.0.1"
    }
}
