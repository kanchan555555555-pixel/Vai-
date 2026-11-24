package com.example.network

import android.content.Context
import android.net.wifi.WifiManager
import android.util.Xml
import com.example.model.DiscoveredTv
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.xmlpull.v1.XmlPullParser
import java.io.StringReader
import java.net.DatagramPacket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.MulticastSocket
import java.net.NetworkInterface
import java.net.SocketTimeoutException
import java.net.URL
import java.net.HttpURLConnection

/**
 * Real UPnP / DLNA SSDP discovery over local Hotspot & Wi-Fi LAN interfaces.
 */
object SsdpDlnaDiscovery {

    private const val SSDP_ADDR = "239.255.255.250"
    private const val SSDP_PORT = 1900

    private val SEARCH_TARGETS = listOf(
        "urn:schemas-upnp-org:device:MediaRenderer:1",
        "urn:schemas-upnp-org:service:AVTransport:1"
    )

    suspend fun discoverTvs(context: Context, timeoutMs: Int = 3500): List<DiscoveredTv> =
        withContext(Dispatchers.IO) {
            val wifi = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
            val lock = wifi?.createMulticastLock("HotCastSsdpLock")?.apply {
                setReferenceCounted(true)
                runCatching { acquire() }
            }

            val locations = linkedSetOf<String>()
            try {
                val ifaces = HotspotNetworkMonitor.getActiveMulticastInterfaces()
                if (ifaces.isEmpty()) {
                    sendSearchOnInterface(null, timeoutMs, locations)
                } else {
                    for (iface in ifaces) {
                        sendSearchOnInterface(iface, timeoutMs / ifaces.size.coerceAtLeast(1), locations)
                    }
                }
            } finally {
                runCatching {
                    if (lock?.isHeld == true) lock.release()
                }
            }

            val discovered = mutableListOf<DiscoveredTv>()
            val seenUdns = mutableSetOf<String>()
            for (loc in locations) {
                val tv = fetchAndParseDeviceXml(loc)
                if (tv != null && seenUdns.add(tv.udn)) {
                    discovered.add(tv)
                }
            }
            discovered
        }

    suspend fun probeLocationUrl(urlString: String): DiscoveredTv? = withContext(Dispatchers.IO) {
        val normalized = if (urlString.startsWith("http://") || urlString.startsWith("https://")) {
            urlString.trim()
        } else {
            "http://${urlString.trim()}"
        }
        fetchAndParseDeviceXml(normalized)
    }

    private fun sendSearchOnInterface(
        iface: NetworkInterface?,
        listenMs: Int,
        locationsOut: MutableSet<String>
    ) {
        val socket = MulticastSocket(null).apply {
            reuseAddress = true
            soTimeout = 600
            if (iface != null) {
                runCatching { networkInterface = iface }
            }
            bind(InetSocketAddress(0))
        }

        socket.use { sock ->
            val group = InetAddress.getByName(SSDP_ADDR)
            for (st in SEARCH_TARGETS) {
                val query = buildString {
                    append("M-SEARCH * HTTP/1.1\r\n")
                    append("HOST: $SSDP_ADDR:$SSDP_PORT\r\n")
                    append("MAN: \"ssdp:discover\"\r\n")
                    append("MX: 2\r\n")
                    append("ST: $st\r\n")
                    append("USER-AGENT: HotCastTV/1.0 UPnP/1.1\r\n\r\n")
                }.toByteArray(Charsets.US_ASCII)

                val packet = DatagramPacket(query, query.size, group, SSDP_PORT)
                runCatching { sock.send(packet) }
            }

            val deadline = System.currentTimeMillis() + listenMs.coerceAtLeast(1200)
            val buf = ByteArray(4096)
            while (System.currentTimeMillis() < deadline) {
                try {
                    val resp = DatagramPacket(buf, buf.size)
                    sock.receive(resp)
                    val text = String(resp.data, 0, resp.length, Charsets.UTF_8)
                    val location = extractHeader(text, "LOCATION")
                    if (!location.isNullOrBlank()) {
                        locationsOut.add(location.trim())
                    }
                } catch (_: SocketTimeoutException) {
                    // keep listening until deadline
                } catch (_: Exception) {
                    break
                }
            }
        }
    }

    private fun extractHeader(response: String, headerName: String): String? {
        val lines = response.split("\r\n", "\n")
        for (line in lines) {
            val idx = line.indexOf(':')
            if (idx > 0 && line.substring(0, idx).trim().equals(headerName, ignoreCase = true)) {
                return line.substring(idx + 1).trim()
            }
        }
        return null
    }

    private fun fetchAndParseDeviceXml(locationUrl: String): DiscoveredTv? {
        return runCatching {
            val url = URL(locationUrl)
            val conn = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = 2500
                readTimeout = 2500
                requestMethod = "GET"
            }
            val xml = conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            conn.disconnect()
            parseDeviceDescription(locationUrl, url, xml)
        }.getOrNull()
    }

    private fun parseDeviceDescription(locationUrl: String, baseUrl: URL, xml: String): DiscoveredTv? {
        val parser = Xml.newPullParser().apply {
            setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
            setInput(StringReader(xml))
        }

        var friendlyName = ""
        var manufacturer = ""
        var modelName = ""
        var udn = locationUrl
        var urlBase = "${baseUrl.protocol}://${baseUrl.authority}"

        var inService = false
        var currentServiceType = ""
        var currentControlUrl = ""
        var avTransportControl: String? = null
        var renderingControl: String? = null
        var currentTag = ""

        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            when (event) {
                XmlPullParser.START_TAG -> {
                    currentTag = parser.name.orEmpty()
                    if (currentTag.equals("service", ignoreCase = true)) {
                        inService = true
                        currentServiceType = ""
                        currentControlUrl = ""
                    }
                }

                XmlPullParser.TEXT -> {
                    val text = parser.text?.trim().orEmpty()
                    if (text.isNotEmpty()) {
                        if (!inService) {
                            when {
                                currentTag.equals("friendlyName", true) && friendlyName.isEmpty() ->
                                    friendlyName = text
                                currentTag.equals("manufacturer", true) && manufacturer.isEmpty() ->
                                    manufacturer = text
                                currentTag.equals("modelName", true) && modelName.isEmpty() ->
                                    modelName = text
                                currentTag.equals("UDN", true) ->
                                    udn = text
                                currentTag.equals("URLBase", true) ->
                                    urlBase = text.trimEnd('/')
                            }
                        } else {
                            when {
                                currentTag.equals("serviceType", true) ->
                                    currentServiceType = text
                                currentTag.equals("controlURL", true) ->
                                    currentControlUrl = text
                            }
                        }
                    }
                }

                XmlPullParser.END_TAG -> {
                    val tag = parser.name.orEmpty()
                    if (tag.equals("service", ignoreCase = true)) {
                        if (currentServiceType.contains("AVTransport", ignoreCase = true) &&
                            currentControlUrl.isNotEmpty()
                        ) {
                            avTransportControl = resolveControlUrl(urlBase, baseUrl, currentControlUrl)
                        } else if (currentServiceType.contains("RenderingControl", ignoreCase = true) &&
                            currentControlUrl.isNotEmpty()
                        ) {
                            renderingControl = resolveControlUrl(urlBase, baseUrl, currentControlUrl)
                        }
                        inService = false
                    }
                    currentTag = ""
                }
            }
            event = parser.next()
        }

        val avUrl = avTransportControl ?: return null
        return DiscoveredTv(
            udn = udn,
            friendlyName = friendlyName.ifBlank { baseUrl.host },
            manufacturer = manufacturer.ifBlank { "Smart TV" },
            modelName = modelName.ifBlank { "DLNA Renderer" },
            host = baseUrl.host,
            locationUrl = locationUrl,
            avTransportControlUrl = avUrl,
            renderingControlUrl = renderingControl
        )
    }

    private fun resolveControlUrl(urlBase: String, location: URL, controlUrl: String): String {
        if (controlUrl.startsWith("http://") || controlUrl.startsWith("https://")) {
            return controlUrl
        }
        return if (controlUrl.startsWith("/")) {
            "$urlBase$controlUrl"
        } else {
            URL(location, controlUrl).toString()
        }
    }
}
