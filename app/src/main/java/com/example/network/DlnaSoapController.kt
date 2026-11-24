package com.example.network

import com.example.model.DiscoveredTv
import com.example.model.LocalMediaItem
import com.example.model.MediaKind
import com.example.model.PlaybackState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

/**
 * Real UPnP AVTransport & RenderingControl SOAP 1.1 client for Smart TVs.
 */
object DlnaSoapController {

    private const val AV_TRANSPORT = "urn:schemas-upnp-org:service:AVTransport:1"
    private const val RENDERING_CONTROL = "urn:schemas-upnp-org:service:RenderingControl:1"

    data class DlnaPositionInfo(
        val positionMs: Long,
        val durationMs: Long
    )

    suspend fun castMedia(
        tv: DiscoveredTv,
        mediaUrl: String,
        item: LocalMediaItem
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val didl = buildDidlMetadata(mediaUrl, item)
            val setUriBody = """
                <u:SetAVTransportURI xmlns:u="$AV_TRANSPORT">
                  <InstanceID>0</InstanceID>
                  <CurrentURI>${escapeXml(mediaUrl)}</CurrentURI>
                  <CurrentURIMetaData>${escapeXml(didl)}</CurrentURIMetaData>
                </u:SetAVTransportURI>
            """.trimIndent()

            sendSoapAction(tv.avTransportControlUrl, AV_TRANSPORT, "SetAVTransportURI", setUriBody)
            play(tv).getOrThrow()
        }
    }

    suspend fun play(tv: DiscoveredTv): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val body = """
                <u:Play xmlns:u="$AV_TRANSPORT">
                  <InstanceID>0</InstanceID>
                  <Speed>1</Speed>
                </u:Play>
            """.trimIndent()
            sendSoapAction(tv.avTransportControlUrl, AV_TRANSPORT, "Play", body)
            Unit
        }
    }

    suspend fun pause(tv: DiscoveredTv): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val body = """
                <u:Pause xmlns:u="$AV_TRANSPORT">
                  <InstanceID>0</InstanceID>
                </u:Pause>
            """.trimIndent()
            sendSoapAction(tv.avTransportControlUrl, AV_TRANSPORT, "Pause", body)
            Unit
        }
    }

    suspend fun stop(tv: DiscoveredTv): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val body = """
                <u:Stop xmlns:u="$AV_TRANSPORT">
                  <InstanceID>0</InstanceID>
                </u:Stop>
            """.trimIndent()
            sendSoapAction(tv.avTransportControlUrl, AV_TRANSPORT, "Stop", body)
            Unit
        }
    }

    suspend fun seek(tv: DiscoveredTv, positionMs: Long): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val target = formatHms(positionMs)
            val body = """
                <u:Seek xmlns:u="$AV_TRANSPORT">
                  <InstanceID>0</InstanceID>
                  <Unit>REL_TIME</Unit>
                  <Target>$target</Target>
                </u:Seek>
            """.trimIndent()
            sendSoapAction(tv.avTransportControlUrl, AV_TRANSPORT, "Seek", body)
            Unit
        }
    }

    suspend fun setVolume(tv: DiscoveredTv, volume: Int): Result<Unit> = withContext(Dispatchers.IO) {
        val ctrlUrl = tv.renderingControlUrl ?: return@withContext Result.success(Unit)
        runCatching {
            val clamped = volume.coerceIn(0, 100)
            val body = """
                <u:SetVolume xmlns:u="$RENDERING_CONTROL">
                  <InstanceID>0</InstanceID>
                  <Channel>Master</Channel>
                  <DesiredVolume>$clamped</DesiredVolume>
                </u:SetVolume>
            """.trimIndent()
            sendSoapAction(ctrlUrl, RENDERING_CONTROL, "SetVolume", body)
            Unit
        }
    }

    suspend fun setMute(tv: DiscoveredTv, muted: Boolean): Result<Unit> = withContext(Dispatchers.IO) {
        val ctrlUrl = tv.renderingControlUrl ?: return@withContext Result.success(Unit)
        runCatching {
            val flag = if (muted) "1" else "0"
            val body = """
                <u:SetMute xmlns:u="$RENDERING_CONTROL">
                  <InstanceID>0</InstanceID>
                  <Channel>Master</Channel>
                  <DesiredMute>$flag</DesiredMute>
                </u:SetMute>
            """.trimIndent()
            sendSoapAction(ctrlUrl, RENDERING_CONTROL, "SetMute", body)
            Unit
        }
    }

    suspend fun getPositionInfo(tv: DiscoveredTv): DlnaPositionInfo? = withContext(Dispatchers.IO) {
        runCatching {
            val body = """
                <u:GetPositionInfo xmlns:u="$AV_TRANSPORT">
                  <InstanceID>0</InstanceID>
                </u:GetPositionInfo>
            """.trimIndent()
            val resp = sendSoapAction(tv.avTransportControlUrl, AV_TRANSPORT, "GetPositionInfo", body)
            val relTime = extractTagValue(resp, "RelTime")
            val trackDur = extractTagValue(resp, "TrackDuration")
            DlnaPositionInfo(
                positionMs = parseHms(relTime),
                durationMs = parseHms(trackDur)
            )
        }.getOrNull()
    }

    suspend fun getTransportState(tv: DiscoveredTv): PlaybackState? = withContext(Dispatchers.IO) {
        runCatching {
            val body = """
                <u:GetTransportInfo xmlns:u="$AV_TRANSPORT">
                  <InstanceID>0</InstanceID>
                </u:GetTransportInfo>
            """.trimIndent()
            val resp = sendSoapAction(tv.avTransportControlUrl, AV_TRANSPORT, "GetTransportInfo", body)
            when (extractTagValue(resp, "CurrentTransportState").uppercase()) {
                "PLAYING" -> PlaybackState.PLAYING
                "PAUSED_PLAYBACK", "PAUSED" -> PlaybackState.PAUSED
                "TRANSITIONING" -> PlaybackState.LOADING
                "STOPPED", "NO_MEDIA_PRESENT" -> PlaybackState.STOPPED
                else -> null
            }
        }.getOrNull()
    }

    private fun sendSoapAction(
        controlUrl: String,
        serviceType: String,
        action: String,
        innerBody: String
    ): String {
        val envelope = """
            <?xml version="1.0" encoding="utf-8"?>
            <s:Envelope xmlns:s="http://schemas.xmlsoap.org/soap/envelope/" s:encodingStyle="http://schemas.xmlsoap.org/soap/encoding/">
              <s:Body>
                $innerBody
              </s:Body>
            </s:Envelope>
        """.trimIndent().toByteArray(Charsets.UTF_8)

        val conn = (URL(controlUrl).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 3500
            readTimeout = 4500
            doOutput = true
            setRequestProperty("Content-Type", "text/xml; charset=\"utf-8\"")
            setRequestProperty("SOAPACTION", "\"$serviceType#$action\"")
        }

        conn.outputStream.use { it.write(envelope) }
        val code = conn.responseCode
        val stream = if (code in 200..299) conn.inputStream else conn.errorStream
        val text = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
        conn.disconnect()
        if (code !in 200..299) {
            throw IllegalStateException("SOAP $action HTTP $code")
        }
        return text
    }

    private fun buildDidlMetadata(mediaUrl: String, item: LocalMediaItem): String {
        val upnpClass = when (item.kind) {
            MediaKind.VIDEO -> "object.item.videoItem"
            MediaKind.PHOTO -> "object.item.imageItem.photo"
            MediaKind.AUDIO -> "object.item.audioItem.musicTrack"
        }
        val dlnaFlags = "DLNA.ORG_OP=01;DLNA.ORG_CI=0;DLNA.ORG_FLAGS=01700000000000000000000000000000"
        val protocolInfo = "http-get:*:${item.mimeType}:$dlnaFlags"
        val durationAttr = if (item.durationMs > 0L) " duration=\"${formatHms(item.durationMs)}\"" else ""
        val sizeAttr = if (item.sizeBytes > 0L) " size=\"${item.sizeBytes}\"" else ""

        return """
            <DIDL-Lite xmlns="urn:schemas-upnp-org:metadata-1-0/DIDL-Lite/" xmlns:dc="http://purl.org/dc/elements/1.1/" xmlns:upnp="urn:schemas-upnp-org:metadata-1-0/upnp/">
              <item id="${escapeXml(item.id)}" parentID="0" restricted="1">
                <dc:title>${escapeXml(item.title)}</dc:title>
                <upnp:class>$upnpClass</upnp:class>
                <res protocolInfo="$protocolInfo"$durationAttr$sizeAttr>${escapeXml(mediaUrl)}</res>
              </item>
            </DIDL-Lite>
        """.trimIndent()
    }

    private fun extractTagValue(xml: String, tag: String): String {
        val start = xml.indexOf("<$tag>")
        val end = xml.indexOf("</$tag>")
        return if (start >= 0 && end > start) {
            xml.substring(start + tag.length + 2, end).trim()
        } else {
            ""
        }
    }

    private fun escapeXml(input: String): String {
        return input
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&apos;")
    }

    fun formatHms(ms: Long): String {
        val totalSec = (ms / 1000L).coerceAtLeast(0L)
        val hours = totalSec / 3600L
        val minutes = (totalSec % 3600L) / 60L
        val seconds = totalSec % 60L
        return "%02d:%02d:%02d".format(hours, minutes, seconds)
    }

    private fun parseHms(hms: String): Long {
        val parts = hms.trim().split(":")
        if (parts.size != 3) return 0L
        val h = parts[0].toLongOrNull() ?: return 0L
        val m = parts[1].toLongOrNull() ?: return 0L
        val s = parts[2].substringBefore('.').toLongOrNull() ?: return 0L
        return ((h * 3600L) + (m * 60L) + s) * 1000L
    }
}
