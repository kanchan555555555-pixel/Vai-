package com.example.network

import android.content.Context
import android.net.Uri
import android.util.Log
import com.example.model.LocalMediaItem
import com.example.model.MediaKind
import com.example.model.PlaybackState
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

/**
 * Embedded HTTP/1.1 streaming server & TV Web Receiver host.
 * Supports:
 * - Byte-range video/audio streaming (206 Partial Content) + DLNA headers
 * - Live Screen Mirroring via MJPEG (/mirror.mjpeg) and JPEG snapshot (/mirror.jpg)
 * - Server-Sent Events (/api/events), JSON state polling (/api/state), and TV feedback (/api/report)
 */
class LocalMediaServer(
    private val context: Context,
    private val onTvFeedback: (positionMs: Long, durationMs: Long, state: PlaybackState) -> Unit,
    private val onClientCountChanged: (Int) -> Unit
) {

    data class ReceiverPayload(
        val mediaId: String? = null,
        val mediaUrl: String? = null,
        val title: String = "",
        val kind: MediaKind = MediaKind.VIDEO,
        val mimeType: String = "",
        val command: String = "IDLE", // LOAD, PLAY, PAUSE, STOP, SEEK, ROTATE, MIRROR_START, MIRROR_STOP
        val commandSeq: Long = 0L,
        val seekToMs: Long = 0L,
        val volume: Int = 65,
        val isMuted: Boolean = false,
        val rotationDeg: Int = 0
    ) {
        fun toJson(): String {
            return JSONObject().apply {
                put("mediaId", mediaId ?: "")
                put("mediaUrl", mediaUrl ?: "")
                put("title", title)
                put("kind", kind.name)
                put("mimeType", mimeType)
                put("command", command)
                put("commandSeq", commandSeq)
                put("seekToMs", seekToMs)
                put("volume", volume)
                put("isMuted", isMuted)
                put("rotationDeg", rotationDeg)
            }.toString()
        }
    }

    private val running = AtomicBoolean(false)
    private var serverSocket: ServerSocket? = null
    private var acceptThread: Thread? = null
    private var executor: ExecutorService? = null

    private val mediaRegistry = ConcurrentHashMap<String, LocalMediaItem>()
    private val sseClients = CopyOnWriteArrayList<OutputStream>()
    private val activeWebClientSeen = ConcurrentHashMap<String, Long>()
    private val commandCounter = AtomicLong(0L)

    @Volatile
    private var currentPayload = ReceiverPayload()

    @Volatile
    var port: Int = 8899
        private set

    fun isRunning(): Boolean = running.get()

    fun start(preferredPort: Int = 8899): Int {
        if (running.get()) return port

        val socket = runCatching {
            ServerSocket(preferredPort, 32, InetAddress.getByName("0.0.0.0"))
        }.getOrElse {
            ServerSocket(0, 32, InetAddress.getByName("0.0.0.0"))
        }

        port = socket.localPort
        serverSocket = socket
        executor = Executors.newCachedThreadPool()
        running.set(true)

        acceptThread = Thread({
            while (running.get()) {
                try {
                    val client = socket.accept()
                    executor?.execute { handleClient(client) }
                } catch (_: SocketException) {
                    break
                } catch (e: Exception) {
                    Log.w("LocalMediaServer", "Accept error: ${e.message}")
                }
            }
        }, "HotCastHttpAccept").apply {
            isDaemon = true
            start()
        }

        return port
    }

    fun stop() {
        running.set(false)
        sseClients.forEach { runCatching { it.close() } }
        sseClients.clear()
        activeWebClientSeen.clear()
        onClientCountChanged(0)
        runCatching { serverSocket?.close() }
        serverSocket = null
        executor?.shutdownNow()
        executor = null
    }

    fun registerMedia(items: List<LocalMediaItem>) {
        mediaRegistry.clear()
        items.forEach { mediaRegistry[it.id] = it }
    }

    fun registerSingleMedia(item: LocalMediaItem) {
        mediaRegistry[item.id] = item
    }

    fun mediaUrlFor(hostIp: String, item: LocalMediaItem): String {
        registerSingleMedia(item)
        return "http://$hostIp:$port/media/${item.id}"
    }

    fun pushMirrorCommand(active: Boolean) {
        val next = currentPayload.copy(
            mediaUrl = if (active) "/mirror.mjpeg" else null,
            title = if (active) "Live Screen Mirror" else "",
            command = if (active) "MIRROR_START" else "MIRROR_STOP",
            commandSeq = commandCounter.incrementAndGet()
        )
        currentPayload = next
        broadcastSse(next.toJson())
    }

    fun pushReceiverState(
        item: LocalMediaItem?,
        hostIp: String?,
        command: String,
        seekToMs: Long = 0L,
        volume: Int = currentPayload.volume,
        isMuted: Boolean = currentPayload.isMuted,
        rotationDeg: Int = currentPayload.rotationDeg
    ) {
        val url = if (item != null && !hostIp.isNullOrBlank()) {
            mediaUrlFor(hostIp, item)
        } else {
            currentPayload.mediaUrl
        }
        val next = ReceiverPayload(
            mediaId = item?.id,
            mediaUrl = if (command == "STOP" || item == null) null else url,
            title = item?.title ?: "",
            kind = item?.kind ?: MediaKind.VIDEO,
            mimeType = item?.mimeType ?: "",
            command = command,
            commandSeq = commandCounter.incrementAndGet(),
            seekToMs = seekToMs,
            volume = volume,
            isMuted = isMuted,
            rotationDeg = rotationDeg
        )
        currentPayload = next
        broadcastSse(next.toJson())
    }

    private fun broadcastSse(json: String) {
        val frame = "data: $json\n\n".toByteArray(Charsets.UTF_8)
        val dead = mutableListOf<OutputStream>()
        for (out in sseClients) {
            try {
                synchronized(out) {
                    out.write(frame)
                    out.flush()
                }
            } catch (_: Exception) {
                dead.add(out)
            }
        }
        if (dead.isNotEmpty()) {
            sseClients.removeAll(dead.toSet())
            notifyClientCount()
        }
    }

    private fun handleClient(socket: Socket) {
        socket.use { client ->
            client.soTimeout = 0
            val input = BufferedInputStream(client.getInputStream())
            val output = BufferedOutputStream(client.getOutputStream())

            val requestLine = readAsciiLine(input) ?: return
            val parts = requestLine.split(" ")
            if (parts.size < 2) return
            val method = parts[0].uppercase()
            val rawPath = parts[1].substringBefore("?")

            val headers = mutableMapOf<String, String>()
            while (true) {
                val line = readAsciiLine(input) ?: break
                if (line.isEmpty()) break
                val colon = line.indexOf(':')
                if (colon > 0) {
                    val key = line.substring(0, colon).trim().lowercase()
                    val value = line.substring(colon + 1).trim()
                    headers[key] = value
                }
            }

            val remoteAddr = client.inetAddress.hostAddress ?: "tv"

            when {
                rawPath == "/" || rawPath == "/index.html" -> {
                    markClientSeen(remoteAddr)
                    serveText(output, 200, "OK", "text/html; charset=utf-8", TV_RECEIVER_HTML)
                }

                rawPath == "/api/state" -> {
                    markClientSeen(remoteAddr)
                    serveText(output, 200, "OK", "application/json; charset=utf-8", currentPayload.toJson())
                }

                rawPath == "/api/events" -> {
                    markClientSeen(remoteAddr)
                    handleSseStream(output, remoteAddr)
                }

                rawPath == "/mirror.mjpeg" -> {
                    markClientSeen(remoteAddr)
                    handleMjpegStream(output)
                }

                rawPath == "/mirror.jpg" -> {
                    markClientSeen(remoteAddr)
                    val frame = MirrorFrameHub.getLatestFrame()
                    if (frame != null) {
                        val hdr = buildString {
                            append("HTTP/1.1 200 OK\r\n")
                            append("Content-Type: image/jpeg\r\n")
                            append("Content-Length: ${frame.size}\r\n")
                            append("Cache-Control: no-store\r\n")
                            append("Access-Control-Allow-Origin: *\r\n")
                            append("Connection: close\r\n\r\n")
                        }.toByteArray(Charsets.US_ASCII)
                        output.write(hdr)
                        output.write(frame)
                        output.flush()
                    } else {
                        serveText(output, 503, "Service Unavailable", "text/plain", "Mirror not active")
                    }
                }

                rawPath == "/api/report" && method == "POST" -> {
                    markClientSeen(remoteAddr)
                    val contentLen = headers["content-length"]?.toIntOrNull()?.coerceIn(0, 8192) ?: 0
                    val bodyBytes = ByteArray(contentLen)
                    var readTotal = 0
                    while (readTotal < contentLen) {
                        val r = input.read(bodyBytes, readTotal, contentLen - readTotal)
                        if (r <= 0) break
                        readTotal += r
                    }
                    parseTvFeedback(String(bodyBytes, 0, readTotal, Charsets.UTF_8))
                    serveText(output, 200, "OK", "application/json", "{\"ok\":true}")
                }

                rawPath.startsWith("/media/") -> {
                    val mediaId = rawPath.removePrefix("/media/").substringBefore("/")
                    val item = mediaRegistry[mediaId]
                    if (item == null) {
                        serveText(output, 404, "Not Found", "text/plain", "Media not found")
                    } else {
                        serveMediaFile(
                            output = output,
                            uri = item.uri,
                            mimeType = item.mimeType,
                            rangeHeader = headers["range"],
                            headOnly = method == "HEAD"
                        )
                    }
                }

                else -> {
                    serveText(output, 404, "Not Found", "text/plain", "Not Found")
                }
            }
        }
    }

    private fun handleMjpegStream(output: OutputStream) {
        val boundary = "hotcastframe"
        val header = buildString {
            append("HTTP/1.1 200 OK\r\n")
            append("Content-Type: multipart/x-mixed-replace; boundary=$boundary\r\n")
            append("Cache-Control: no-store, no-cache, must-revalidate\r\n")
            append("Pragma: no-cache\r\n")
            append("Access-Control-Allow-Origin: *\r\n")
            append("Connection: close\r\n\r\n")
        }.toByteArray(Charsets.US_ASCII)

        output.write(header)
        output.flush()

        MirrorFrameHub.onMirrorClientConnected()
        try {
            var lastSeq = -1L
            while (running.get() && MirrorFrameHub.isMirroring.value) {
                val (nextSeq, frame) = MirrorFrameHub.awaitNextFrame(lastSeq, timeoutMs = 1200L)
                if (!MirrorFrameHub.isMirroring.value) break
                if (frame != null && nextSeq != lastSeq) {
                    lastSeq = nextSeq
                    val partHeader = buildString {
                        append("--$boundary\r\n")
                        append("Content-Type: image/jpeg\r\n")
                        append("Content-Length: ${frame.size}\r\n\r\n")
                    }.toByteArray(Charsets.US_ASCII)
                    output.write(partHeader)
                    output.write(frame)
                    output.write("\r\n".toByteArray(Charsets.US_ASCII))
                    output.flush()
                }
            }
        } catch (_: Exception) {
            // TV browser closed MJPEG stream
        } finally {
            MirrorFrameHub.onMirrorClientDisconnected()
        }
    }

    private fun handleSseStream(output: OutputStream, remoteAddr: String) {
        val header = buildString {
            append("HTTP/1.1 200 OK\r\n")
            append("Content-Type: text/event-stream; charset=utf-8\r\n")
            append("Cache-Control: no-cache\r\n")
            append("Connection: keep-alive\r\n")
            append("Access-Control-Allow-Origin: *\r\n\r\n")
        }.toByteArray(Charsets.US_ASCII)

        output.write(header)
        output.write("data: ${currentPayload.toJson()}\n\n".toByteArray(Charsets.UTF_8))
        output.flush()

        sseClients.add(output)
        notifyClientCount()

        try {
            while (running.get()) {
                Thread.sleep(15_000)
                synchronized(output) {
                    output.write(": keepalive\n\n".toByteArray(Charsets.US_ASCII))
                    outFlush(output)
                }
                markClientSeen(remoteAddr)
            }
        } catch (_: Exception) {
            // Client disconnected
        } finally {
            sseClients.remove(output)
            notifyClientCount()
        }
    }

    private fun outFlush(output: OutputStream) {
        output.flush()
    }

    private fun parseTvFeedback(body: String) {
        runCatching {
            val json = JSONObject(body)
            val pos = json.optLong("positionMs", 0L)
            val dur = json.optLong("durationMs", 0L)
            val stateStr = json.optString("state", "PLAYING")
            val state = when (stateStr.uppercase()) {
                "PLAYING" -> PlaybackState.PLAYING
                "PAUSED" -> PlaybackState.PAUSED
                "STOPPED" -> PlaybackState.STOPPED
                "LOADING" -> PlaybackState.LOADING
                else -> PlaybackState.PLAYING
            }
            onTvFeedback(pos, dur, state)
        }
    }

    private fun markClientSeen(remoteAddr: String) {
        activeWebClientSeen[remoteAddr] = System.currentTimeMillis()
        notifyClientCount()
    }

    private fun notifyClientCount() {
        val now = System.currentTimeMillis()
        activeWebClientSeen.entries.removeIf { now - it.value > 25_000L }
        val count = maxOf(sseClients.size, activeWebClientSeen.size)
        onClientCountChanged(count)
    }

    private fun serveText(
        output: OutputStream,
        code: Int,
        reason: String,
        contentType: String,
        body: String
    ) {
        val bytes = body.toByteArray(Charsets.UTF_8)
        val headers = buildString {
            append("HTTP/1.1 $code $reason\r\n")
            append("Content-Type: $contentType\r\n")
            append("Content-Length: ${bytes.size}\r\n")
            append("Cache-Control: no-store\r\n")
            append("Access-Control-Allow-Origin: *\r\n")
            append("Connection: close\r\n\r\n")
        }.toByteArray(Charsets.US_ASCII)
        output.write(headers)
        output.write(bytes)
        output.flush()
    }

    private fun serveMediaFile(
        output: OutputStream,
        uri: Uri,
        mimeType: String,
        rangeHeader: String?,
        headOnly: Boolean
    ) {
        val afd = runCatching { context.contentResolver.openAssetFileDescriptor(uri, "r") }.getOrNull()
        if (afd == null) {
            serveText(output, 404, "Not Found", "text/plain", "Cannot open media")
            return
        }

        afd.use { descriptor ->
            val totalLength = descriptor.length
            val stream = descriptor.createInputStream()
            stream.use { fis ->
                val actualLength = if (totalLength > 0L) {
                    totalLength
                } else {
                    runCatching { fis.channel.size() }.getOrDefault(-1L)
                }

                var start = 0L
                var end = if (actualLength > 0L) actualLength - 1L else -1L
                var isRange = false

                if (!rangeHeader.isNullOrBlank() && rangeHeader.startsWith("bytes=") && actualLength > 0L) {
                    val rangeSpec = rangeHeader.removePrefix("bytes=").substringBefore(",").trim()
                    val dash = rangeSpec.indexOf('-')
                    if (dash >= 0) {
                        val startStr = rangeSpec.substring(0, dash).trim()
                        val endStr = rangeSpec.substring(dash + 1).trim()
                        if (startStr.isEmpty() && endStr.isNotEmpty()) {
                            val suffix = endStr.toLongOrNull() ?: 0L
                            start = (actualLength - suffix).coerceAtLeast(0L)
                            end = actualLength - 1L
                            isRange = true
                        } else if (startStr.isNotEmpty()) {
                            start = startStr.toLongOrNull()?.coerceIn(0L, actualLength - 1L) ?: 0L
                            end = endStr.toLongOrNull()?.coerceIn(start, actualLength - 1L) ?: (actualLength - 1L)
                            isRange = true
                        }
                    }
                }

                val statusLine = if (isRange) "HTTP/1.1 206 Partial Content\r\n" else "HTTP/1.1 200 OK\r\n"
                val contentLength = if (actualLength > 0L) (end - start + 1L) else -1L

                val headerText = buildString {
                    append(statusLine)
                    append("Content-Type: $mimeType\r\n")
                    if (contentLength >= 0L) append("Content-Length: $contentLength\r\n")
                    if (isRange && actualLength > 0L) {
                        append("Content-Range: bytes $start-$end/$actualLength\r\n")
                    }
                    append("Accept-Ranges: bytes\r\n")
                    append("transferMode.dlna.org: Streaming\r\n")
                    append("contentFeatures.dlna.org: DLNA.ORG_OP=01;DLNA.ORG_CI=0;DLNA.ORG_FLAGS=01700000000000000000000000000000\r\n")
                    append("Access-Control-Allow-Origin: *\r\n")
                    append("Connection: close\r\n\r\n")
                }

                output.write(headerText.toByteArray(Charsets.US_ASCII))
                output.flush()

                if (headOnly) return

                if (start > 0L) {
                    val sought = runCatching {
                        fis.channel.position(start)
                        true
                    }.getOrDefault(false)
                    if (!sought) {
                        skipFully(fis, start)
                    }
                }

                val buffer = ByteArray(64 * 1024)
                var remaining = if (contentLength >= 0L) contentLength else Long.MAX_VALUE
                while (remaining > 0L && running.get()) {
                    val toRead = minOf(buffer.size.toLong(), remaining).toInt()
                    val read = fis.read(buffer, 0, toRead)
                    if (read <= 0) break
                    output.write(buffer, 0, read)
                    if (contentLength >= 0L) remaining -= read
                }
                output.flush()
            }
        }
    }

    private fun skipFully(input: InputStream, bytesToSkip: Long) {
        var remaining = bytesToSkip
        val skipBuf = ByteArray(16 * 1024)
        while (remaining > 0) {
            val skipped = input.skip(remaining)
            if (skipped > 0) {
                remaining -= skipped
            } else {
                val read = input.read(skipBuf, 0, minOf(skipBuf.size.toLong(), remaining).toInt())
                if (read <= 0) break
                remaining -= read
            }
        }
    }

    private fun readAsciiLine(input: InputStream): String? {
        val baos = ByteArrayOutputStream(128)
        var b = input.read()
        if (b == -1) return null
        while (b != -1) {
            if (b == '\n'.code) break
            if (b != '\r'.code) baos.write(b)
            b = input.read()
        }
        return baos.toString("US-ASCII")
    }

    companion object {
        private val TV_RECEIVER_HTML = """
            <!DOCTYPE html>
            <html lang="en">
            <head>
            <meta charset="UTF-8" />
            <meta name="viewport" content="width=device-width, initial-scale=1.0" />
            <title>HotCast TV Receiver</title>
            <style>
              * { box-sizing: border-box; margin: 0; padding: 0; }
              html, body { width: 100%; height: 100%; background: #070A10; color: #F1F5F9; font-family: system-ui, -apple-system, sans-serif; overflow: hidden; }
              .stage { position: relative; width: 100vw; height: 100vh; display: flex; align-items: center; justify-content: center; background: radial-gradient(circle at 50% 35%, #111D30 0%, #070A10 75%); }
              video { width: 100%; height: 100%; object-fit: contain; background: #000; display: none; }
              #photoEl { max-width: 100vw; max-height: 100vh; object-fit: contain; transition: transform 0.35s ease, opacity 0.3s ease; display: none; }
              #mirrorEl { width: 100vw; height: 100vh; object-fit: contain; background: #000; display: none; }
              .standby { text-align: center; padding: 36px; max-width: 680px; }
              .badge { display: inline-flex; align-items: center; gap: 10px; padding: 8px 18px; border-radius: 999px; background: rgba(0, 229, 255, 0.12); border: 1px solid rgba(0, 229, 255, 0.35); color: #00E5FF; font-weight: 600; font-size: 15px; margin-bottom: 24px; }
              .dot { width: 10px; height: 10px; border-radius: 50%; background: #10B981; box-shadow: 0 0 12px #10B981; }
              h1 { font-size: clamp(28px, 4vw, 48px); letter-spacing: -0.02em; margin-bottom: 14px; }
              p { font-size: clamp(15px, 2vw, 20px); color: #94A3B8; line-height: 1.5; }
              .audio-card { display: none; flex-direction: column; align-items: center; gap: 24px; padding: 48px; border-radius: 28px; background: rgba(20, 27, 40, 0.85); border: 1px solid rgba(0, 229, 255, 0.25); box-shadow: 0 24px 80px rgba(0,0,0,0.6); max-width: 720px; width: 88vw; text-align: center; }
              .disc { width: 140px; height: 140px; border-radius: 50%; background: conic-gradient(from 0deg, #00E5FF, #0891B2, #FFB300, #00E5FF); display: flex; align-items: center; justify-content: center; animation: spin 7s linear infinite; }
              .disc-inner { width: 46px; height: 46px; border-radius: 50%; background: #0B0E14; border: 3px solid #F1F5F9; }
              @keyframes spin { from { transform: rotate(0deg); } to { transform: rotate(360deg); } }
              .hud { position: fixed; left: 28px; bottom: 24px; padding: 10px 18px; border-radius: 14px; background: rgba(11, 14, 20, 0.78); border: 1px solid rgba(255,255,255,0.12); backdrop-filter: blur(8px); font-size: 16px; color: #E2E8F0; opacity: 0; transition: opacity 0.35s ease; pointer-events: none; max-width: 70vw; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
              .hud.visible { opacity: 1; }
              .unmute-btn { position: fixed; top: 24px; right: 24px; padding: 12px 22px; border-radius: 999px; background: #FFB300; color: #1A1200; font-weight: 700; font-size: 16px; border: none; cursor: pointer; display: none; box-shadow: 0 8px 28px rgba(255, 179, 0, 0.4); }
            </style>
            </head>
            <body>
            <div class="stage">
              <div id="standby" class="standby">
                <div class="badge"><span class="dot"></span> HotCast TV Connected (Offline Hotspot)</div>
                <h1>Ready for Mirroring & Cast</h1>
                <p>Tap <strong>Start Screen Mirroring</strong> or pick any Video, Photo, or Audio on your phone.</p>
              </div>
              <img id="mirrorEl" alt="Live Screen Mirror" />
              <video id="videoEl" controls playsinline></video>
              <img id="photoEl" alt="Cast Photo" />
              <div id="audioCard" class="audio-card">
                <div class="disc"><div class="disc-inner"></div></div>
                <h2 id="audioTitle" style="font-size: clamp(22px, 3vw, 34px);">Audio</h2>
                <audio id="audioEl" controls style="width: 100%;"></audio>
              </div>
              <div id="hud" class="hud"></div>
              <button id="unmuteBtn" class="unmute-btn">Tap / Press OK to Unmute</button>
            </div>
            <script>
              const standby = document.getElementById('standby');
              const mirrorEl = document.getElementById('mirrorEl');
              const videoEl = document.getElementById('videoEl');
              const photoEl = document.getElementById('photoEl');
              const audioCard = document.getElementById('audioCard');
              const audioEl = document.getElementById('audioEl');
              const audioTitle = document.getElementById('audioTitle');
              const hud = document.getElementById('hud');
              const unmuteBtn = document.getElementById('unmuteBtn');

              let lastSeq = -1;
              let currentUrl = '';
              let currentKind = '';
              let isMirroring = false;
              let fallbackTimer = null;
              let hudTimer = null;

              function showHud(msg) {
                if (!msg) return;
                hud.textContent = msg;
                hud.classList.add('visible');
                clearTimeout(hudTimer);
                hudTimer = setTimeout(() => hud.classList.remove('visible'), 3500);
              }

              function stopMediaElements() {
                videoEl.pause(); videoEl.removeAttribute('src'); videoEl.style.display = 'none';
                audioEl.pause(); audioEl.removeAttribute('src'); audioCard.style.display = 'none';
                photoEl.style.display = 'none'; photoEl.removeAttribute('src');
                unmuteBtn.style.display = 'none';
              }

              function stopMirror() {
                isMirroring = false;
                clearInterval(fallbackTimer);
                fallbackTimer = null;
                mirrorEl.onerror = null;
                mirrorEl.removeAttribute('src');
                mirrorEl.style.display = 'none';
              }

              function startMirror(seq) {
                stopMediaElements();
                standby.style.display = 'none';
                isMirroring = true;
                mirrorEl.style.display = 'block';
                mirrorEl.onerror = () => {
                  if (!isMirroring || fallbackTimer) return;
                  fallbackTimer = setInterval(() => {
                    if (!isMirroring) return;
                    mirrorEl.src = '/mirror.jpg?t=' + Date.now();
                  }, 75);
                };
                mirrorEl.src = '/mirror.mjpeg?t=' + seq;
                showHud('Live Screen Mirroring');
              }

              unmuteBtn.addEventListener('click', () => {
                videoEl.muted = false;
                audioEl.muted = false;
                unmuteBtn.style.display = 'none';
              });

              async function safePlay(el) {
                try {
                  await el.play();
                  unmuteBtn.style.display = 'none';
                } catch (err) {
                  el.muted = true;
                  try { await el.play(); } catch (_) {}
                  unmuteBtn.style.display = 'block';
                }
              }

              function applyState(st) {
                if (!st || st.commandSeq === lastSeq) return;
                lastSeq = st.commandSeq;

                if (st.command === 'MIRROR_START') {
                  currentUrl = '';
                  startMirror(st.commandSeq);
                  return;
                }

                if (st.command === 'MIRROR_STOP') {
                  stopMirror();
                  standby.style.display = 'block';
                  return;
                }

                stopMirror();

                const vol = Math.max(0, Math.min(1, (st.volume || 65) / 100));
                videoEl.volume = vol;
                audioEl.volume = vol;
                videoEl.muted = !!st.isMuted;
                audioEl.muted = !!st.isMuted;

                if (st.command === 'STOP' || !st.mediaUrl) {
                  stopMediaElements();
                  standby.style.display = 'block';
                  currentUrl = '';
                  return;
                }

                standby.style.display = 'none';
                const newMedia = st.mediaUrl !== currentUrl || st.kind !== currentKind;
                currentUrl = st.mediaUrl;
                currentKind = st.kind;

                if (st.kind === 'PHOTO') {
                  videoEl.pause(); videoEl.style.display = 'none';
                  audioEl.pause(); audioCard.style.display = 'none';
                  photoEl.style.display = 'block';
                  photoEl.style.transform = 'rotate(' + (st.rotationDeg || 0) + 'deg)';
                  if (newMedia) photoEl.src = st.mediaUrl;
                  showHud(st.title);
                  reportState(0, 0, 'PLAYING');
                  return;
                }

                if (st.kind === 'AUDIO') {
                  videoEl.pause(); videoEl.style.display = 'none';
                  photoEl.style.display = 'none';
                  audioCard.style.display = 'flex';
                  audioTitle.textContent = st.title || 'Audio Track';
                  if (newMedia) {
                    audioEl.src = st.mediaUrl;
                    safePlay(audioEl);
                  } else if (st.command === 'PLAY') {
                    safePlay(audioEl);
                  } else if (st.command === 'PAUSE') {
                    audioEl.pause();
                  } else if (st.command === 'SEEK') {
                    audioEl.currentTime = (st.seekToMs || 0) / 1000;
                  }
                  showHud(st.title);
                  return;
                }

                photoEl.style.display = 'none';
                audioEl.pause(); audioCard.style.display = 'none';
                videoEl.style.display = 'block';
                if (newMedia) {
                  videoEl.src = st.mediaUrl;
                  safePlay(videoEl);
                } else if (st.command === 'PLAY') {
                  safePlay(videoEl);
                } else if (st.command === 'PAUSE') {
                  videoEl.pause();
                } else if (st.command === 'SEEK') {
                  videoEl.currentTime = (st.seekToMs || 0) / 1000;
                }
                showHud(st.title);
              }

              function reportState(posMs, durMs, state) {
                fetch('/api/report', {
                  method: 'POST',
                  headers: { 'Content-Type': 'application/json' },
                  body: JSON.stringify({
                    positionMs: Math.round(posMs || 0),
                    durationMs: Math.round(durMs || 0),
                    state: state
                  })
                }).catch(() => {});
              }

              setInterval(() => {
                if (isMirroring) return;
                if (currentKind === 'VIDEO' && videoEl.style.display !== 'none') {
                  const s = videoEl.paused ? 'PAUSED' : 'PLAYING';
                  reportState(videoEl.currentTime * 1000, (videoEl.duration || 0) * 1000, s);
                } else if (currentKind === 'AUDIO' && audioCard.style.display !== 'none') {
                  const s = audioEl.paused ? 'PAUSED' : 'PLAYING';
                  reportState(audioEl.currentTime * 1000, (audioEl.duration || 0) * 1000, s);
                }
              }, 1000);

              try {
                const es = new EventSource('/api/events');
                es.onmessage = (ev) => {
                  try { applyState(JSON.parse(ev.data)); } catch (_) {}
                };
              } catch (_) {}

              setInterval(() => {
                fetch('/api/state').then(r => r.json()).then(applyState).catch(() => {});
              }, 1200);
            </script>
            </body>
            </html>
        """.trimIndent()
    }
}
