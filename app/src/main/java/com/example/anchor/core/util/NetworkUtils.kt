package com.example.anchor.core.util

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import java.net.Inet4Address
import java.net.NetworkInterface

object NetworkUtils {

    /**
     * Gets the device's local IPv4 address on the Wi-Fi network.
     * Returns null if not connected to Wi-Fi or unable to determine IP.
     */
    fun getLocalIpAddress(context: Context): String? {
        try {
            val wifiManager = context.applicationContext
                .getSystemService(Context.WIFI_SERVICE) as WifiManager

            val wifiInfo = wifiManager.connectionInfo
            val ipInt = wifiInfo.ipAddress

            if (ipInt != 0) {
                return String.format(
                    "%d.%d.%d.%d",
                    ipInt and 0xff,
                    ipInt shr 8 and 0xff,
                    ipInt shr 16 and 0xff,
                    ipInt shr 24 and 0xff
                )
            }

            // Fallback: iterate through network interfaces
            return getIpFromNetworkInterfaces()
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }
    }

    /**
     * Fallback method to get IP from network interfaces.
     */
    private fun getIpFromNetworkInterfaces(): String? {
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val networkInterface = interfaces.nextElement()

                // Skip loopback and down interfaces
                if (networkInterface.isLoopback || !networkInterface.isUp) continue

                // Prefer wlan interfaces
                val name = networkInterface.name.lowercase()
                if (!name.startsWith("wlan") && !name.startsWith("eth")) continue

                val addresses = networkInterface.inetAddresses
                while (addresses.hasMoreElements()) {
                    val address = addresses.nextElement()
                    if (!address.isLoopbackAddress && address is Inet4Address) {
                        return address.hostAddress
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return null
    }

    /**
     * Checks if the device is connected to a Wi-Fi network.
     */
    fun isConnectedToWifi(context: Context): Boolean {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
        return capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
    }

    /**
     * Gets the current Wi-Fi SSID (network name).
     * Note: Requires location permission on Android 8.0+ to get actual SSID.
     */
    fun getWifiSsid(context: Context): String? {
        return try {
            val wifiManager = context.applicationContext
                .getSystemService(Context.WIFI_SERVICE) as WifiManager
            val wifiInfo = wifiManager.connectionInfo
            wifiInfo.ssid?.removeSurrounding("\"")
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Builds the full server URL for sharing.
     */
    fun getServerUrl(context: Context, port: Int = 8080): String? {
        val ip = getLocalIpAddress(context) ?: return null
        return "http://$ip:$port"
    }
}