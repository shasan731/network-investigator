package com.shasan731.networkinvestigator.platform

import android.content.Context
import android.annotation.SuppressLint
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import android.os.Build

data class NetworkSnapshot(val transport: String, val validated: Boolean, val captivePortal: Boolean, val metered: Boolean, val vpn: Boolean, val addresses: List<String>, val dnsServers: List<String>, val gateway: String?, val wifi: WifiSnapshot?)
data class WifiSnapshot(val ssid: String?, val bssid: String?, val rssi: Int?, val frequencyMhz: Int?, val band: String?, val channel: Int?, val linkSpeedMbps: Int?, val receiveLinkSpeedMbps: Int?, val transmitLinkSpeedMbps: Int?, val securityType: String?, val networkId: Int?)
data class NearbyWifiNetwork(val ssid: String, val bssid: String, val rssi: Int, val frequencyMhz: Int, val channel: Int, val band: String, val capabilities: String)

object NetworkSnapshotReader {
    // The OS intentionally returns redacted Wi-Fi fields without runtime permission; the
    // caller exposes a point-of-use permission action and treats redacted fields as unknown.
    @SuppressLint("MissingPermission")
    @Suppress("DEPRECATION")
    fun read(context: Context): NetworkSnapshot? {
        val cm = context.getSystemService(ConnectivityManager::class.java)
        val network = cm.activeNetwork ?: return null
        val caps = cm.getNetworkCapabilities(network) ?: return null
        val props = cm.getLinkProperties(network)
        val transport = buildList {
            if (caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) add("Wi-Fi")
            if (caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)) add("Mobile")
            if (caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)) add("Ethernet")
            if (caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) add("VPN")
        }.joinToString(" + ").ifEmpty { "Other" }
        val wifi = if (caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) runCatching {
            val info = context.applicationContext.getSystemService(WifiManager::class.java).connectionInfo
            val frequency = info.frequency.takeIf { it > 0 }
            WifiSnapshot(info.ssid?.takeUnless { it == "<unknown ssid>" }, info.bssid?.takeUnless { it == "02:00:00:00:00:00" }, info.rssi.takeIf { it > -127 }, frequency, frequency?.let { when (it) { in 2400..2500 -> "2.4 GHz"; in 4900..5900 -> "5 GHz"; in 5925..7125 -> "6 GHz"; else -> "Other" } }, frequency?.let(::channel), info.linkSpeed.takeIf { it >= 0 }, if (Build.VERSION.SDK_INT >= 29) info.rxLinkSpeedMbps.takeIf { it >= 0 } else null, if (Build.VERSION.SDK_INT >= 29) info.txLinkSpeedMbps.takeIf { it >= 0 } else null, if (Build.VERSION.SDK_INT >= 31) when (info.currentSecurityType) { android.net.wifi.WifiInfo.SECURITY_TYPE_OPEN -> "Open"; android.net.wifi.WifiInfo.SECURITY_TYPE_WEP -> "WEP"; android.net.wifi.WifiInfo.SECURITY_TYPE_PSK -> "WPA/WPA2 PSK"; android.net.wifi.WifiInfo.SECURITY_TYPE_EAP -> "Enterprise"; android.net.wifi.WifiInfo.SECURITY_TYPE_SAE -> "WPA3 SAE"; android.net.wifi.WifiInfo.SECURITY_TYPE_OWE -> "OWE"; else -> "Unknown" } else null, info.networkId.takeIf { it >= 0 })
        }.getOrNull() else null
        return NetworkSnapshot(transport, caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED), caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_CAPTIVE_PORTAL), cm.isActiveNetworkMetered, caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN), props?.linkAddresses.orEmpty().map { it.toString() }, props?.dnsServers.orEmpty().map { it.hostAddress }, props?.routes?.firstOrNull { it.isDefaultRoute }?.gateway?.hostAddress, wifi)
    }
    @SuppressLint("MissingPermission") @Suppress("DEPRECATION")
    fun requestNearbyScan(context: Context): Boolean = context.applicationContext.getSystemService(WifiManager::class.java).startScan()
    @SuppressLint("MissingPermission")
    fun nearbyWifi(context: Context): List<NearbyWifiNetwork> = context.applicationContext.getSystemService(WifiManager::class.java).scanResults.map { result -> val frequency = result.frequency; NearbyWifiNetwork(result.SSID.orEmpty().ifBlank { "Hidden network" }, result.BSSID.orEmpty(), result.level, frequency, channel(frequency), when (frequency) { in 2400..2500 -> "2.4 GHz"; in 4900..5900 -> "5 GHz"; in 5925..7125 -> "6 GHz"; else -> "Other" }, result.capabilities.orEmpty()) }.sortedByDescending { it.rssi }
    private fun channel(frequency: Int): Int = when (frequency) { 2484 -> 14; in 2412..2472 -> (frequency - 2407) / 5; in 5000..5895 -> (frequency - 5000) / 5; in 5955..7115 -> (frequency - 5950) / 5; else -> 0 }
}
