package com.example.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.example.model.HotspotStatus
import java.net.Inet4Address
import java.net.NetworkInterface

object HotspotNetworkMonitor {

    fun inspectLocalNetwork(context: Context): HotspotStatus {
        val candidates = mutableListOf<Pair<String, String>>() // (interfaceName, ip)

        runCatching {
            val interfaces = NetworkInterface.getNetworkInterfaces()?.toList().orEmpty()
            for (iface in interfaces) {
                if (!iface.isUp || iface.isLoopback || iface.isVirtual) continue
                val name = iface.name.lowercase()
                // Skip cellular rmnet / tun / dummy interfaces
                if (name.startsWith("rmnet") || name.startsWith("ccmni") ||
                    name.startsWith("tun") || name.startsWith("dummy")
                ) {
                    continue
                }
                for (addr in iface.inetAddresses) {
                    if (addr is Inet4Address && !addr.isLoopbackAddress) {
                        val ip = addr.hostAddress ?: continue
                        if (isPrivateLanIp(ip)) {
                            candidates.add(iface.name to ip)
                        }
                    }
                }
            }
        }

        if (candidates.isEmpty()) {
            return HotspotStatus(isNetworkReady = false)
        }

        // Prioritize hotspot AP interfaces and classic Android hotspot subnets (192.168.43.x, 192.168.137.x)
        val prioritized = candidates.sortedByDescending { (iface, ip) ->
            scoreInterface(iface.lowercase(), ip)
        }

        val best = prioritized.first()
        val bestScore = scoreInterface(best.first.lowercase(), best.second)
        val isLikelyHotspot = bestScore >= 50 || isWifiOrTetherConnected(context)

        return HotspotStatus(
            isNetworkReady = true,
            isLikelyHotspot = bestScore >= 50,
            primaryIp = best.second,
            interfaceName = best.first,
            allLocalIps = prioritized.map { it.second }.distinct()
        )
    }

    fun getActiveMulticastInterfaces(): List<NetworkInterface> {
        val result = mutableListOf<NetworkInterface>()
        runCatching {
            val interfaces = NetworkInterface.getNetworkInterfaces()?.toList().orEmpty()
            for (iface in interfaces) {
                if (!iface.isUp || iface.isLoopback || !iface.supportsMulticast()) continue
                val name = iface.name.lowercase()
                if (name.startsWith("rmnet") || name.startsWith("ccmni") || name.startsWith("tun")) continue
                val hasIpv4 = iface.inetAddresses.toList().any { it is Inet4Address && !it.isLoopbackAddress }
                if (hasIpv4) result.add(iface)
            }
        }
        return result
    }

    private fun scoreInterface(name: String, ip: String): Int {
        var score = 0
        if (name.contains("ap") || name.contains("swlan") || name.contains("softap") ||
            name.contains("wlan1") || name.contains("hotspot") || name.contains("rndis")
        ) {
            score += 80
        }
        if (ip.startsWith("192.168.43.") || ip.startsWith("192.168.137.") || ip.startsWith("192.168.49.")) {
            score += 60
        }
        if (ip.endsWith(".1")) {
            score += 20
        }
        if (name.startsWith("wlan") || name.startsWith("eth")) {
            score += 30
        }
        return score
    }

    private fun isPrivateLanIp(ip: String): Boolean {
        return ip.startsWith("192.168.") ||
            ip.startsWith("10.") ||
            ip.startsWith("172.")
    }

    private fun isWifiOrTetherConnected(context: Context): Boolean {
        return runCatching {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
                ?: return false
            val active = cm.activeNetwork ?: return false
            val caps = cm.getNetworkCapabilities(active) ?: return false
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
        }.getOrDefault(false)
    }
}
