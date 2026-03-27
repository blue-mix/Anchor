package com.example.anchor.core.util

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import java.net.Inet4Address
import java.net.NetworkInterface

/**
 * Network utilities — IP address resolution, connectivity checks, and URL helpers.
 *
 * All methods are safe to call from any thread.
 */
object NetworkUtils {

    // ── IP address resolution ─────────────────────────────────

    /**
     * Returns the device's local IPv4 address on the active Wi-Fi network.
     *
     * Resolution order:
     * 1. WifiManager connection info (fastest, most reliable on Wi-Fi)
     * 2. Network interface enumeration (fallback for hotspot / USB tethering)
     *
     * Returns null when not connected or when the address cannot be determined.
     */
    fun getLocalIpAddress(context: Context): String? {
        return getIpFromWifiManager(context)
            ?: getIpFromNetworkInterfaces()
    }

    /**
     * Reads the IP integer from [WifiManager] and converts it to a dotted string.
     * Returns null when WifiManager reports 0 (not connected / no info).
     */
    private fun getIpFromWifiManager(context: Context): String? {
        return try {
            val wifiManager = context.applicationContext
                .getSystemService(Context.WIFI_SERVICE) as WifiManager

            @Suppress("DEPRECATION")
            val ipInt = wifiManager.connectionInfo?.ipAddress ?: 0

            if (ipInt == 0) return null

            String.format(
                "%d.%d.%d.%d",
                ipInt and 0xff,
                ipInt shr 8 and 0xff,
                ipInt shr 16 and 0xff,
                ipInt shr 24 and 0xff
            )
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Enumerates all network interfaces looking for an IPv4 address on a
     * wlan or ethernet interface.  Used as a fallback for environments where
     * WifiManager returns 0 (e.g., USB tethering, hotspot-connected clients).
     */
    private fun getIpFromNetworkInterfaces(): String? {
        return try {
            val interfaces = NetworkInterface.getNetworkInterfaces() ?: return null

            interfaces.asSequence()
                .filter { iface ->
                    iface.isUp &&
                            !iface.isLoopback &&
                            iface.name.lowercase().let { n ->
                                n.startsWith("wlan") || n.startsWith("eth") ||
                                        n.startsWith("rmnet") || n.startsWith("swlan")
                            }
                }
                .flatMap { iface -> iface.inetAddresses.asSequence() }
                .filterIsInstance<Inet4Address>()
                .firstOrNull { !it.isLoopbackAddress }
                ?.hostAddress
        } catch (e: Exception) {
            null
        }
    }

    // ── Connectivity ──────────────────────────────────────────

    /**
     * Returns true when the device has an active Wi-Fi transport.
     */
    fun isConnectedToWifi(context: Context): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        return caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
    }

    /**
     * Returns true when the device has any validated internet connection
     * (Wi-Fi or cellular).
     */
    fun isNetworkAvailable(context: Context): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }

    /**
     * Returns the current Wi-Fi SSID, or null if unavailable.
     *
     * Note: Requires ACCESS_FINE_LOCATION (Android 8.0+) or
     * ACCESS_NETWORK_STATE to get the real SSID; without it the system
     * returns a placeholder string.
     */
    fun getWifiSsid(context: Context): String? {
        return try {
            val wm = context.applicationContext
                .getSystemService(Context.WIFI_SERVICE) as WifiManager
            @Suppress("DEPRECATION")
            wm.connectionInfo?.ssid?.removeSurrounding("\"")
                ?.takeIf { it.isNotBlank() && it != "<unknown ssid>" }
        } catch (e: Exception) {
            null
        }
    }

    // ── URL helpers ───────────────────────────────────────────

    /**
     * Builds the base server URL for sharing (e.g. "http://192.168.1.5:8080").
     * Returns null when the local IP cannot be determined.
     */
    fun getServerUrl(context: Context, port: Int = 8080): String? {
        val ip = getLocalIpAddress(context) ?: return null
        return buildServerUrl(ip, port)
    }

    /**
     * Builds a server URL from a known [ipAddress] and [port].
     */
    fun buildServerUrl(ipAddress: String, port: Int = 8080): String =
        "http://$ipAddress:$port"

    /**
     * Returns true if [url] looks like a plausible HTTP/HTTPS URL.
     */
    fun isValidUrl(url: String): Boolean =
        url.startsWith("http://") || url.startsWith("https://")
}