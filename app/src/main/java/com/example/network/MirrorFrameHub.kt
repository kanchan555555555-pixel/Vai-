package com.example.network

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

/**
 * Thread-safe live frame hub connecting ScreenMirrorService (producer)
 * with LocalMediaServer MJPEG/JPEG endpoints (TV browser consumers).
 */
object MirrorFrameHub {

    private val frameLock = Object()

    @Volatile
    private var latestJpeg: ByteArray? = null

    private val frameSeq = AtomicLong(0L)
    private val activeClients = AtomicInteger(0)

    private var fpsWindowStartMs = System.currentTimeMillis()
    private var fpsFrameCount = 0

    private val _isMirroring = MutableStateFlow(false)
    val isMirroring = _isMirroring.asStateFlow()

    private val _fps = MutableStateFlow(0)
    val fps = _fps.asStateFlow()

    private val _dimensions = MutableStateFlow(0 to 0)
    val dimensions = _dimensions.asStateFlow()

    private val _clientCount = MutableStateFlow(0)
    val clientCount = _clientCount.asStateFlow()

    fun setMirroring(active: Boolean, width: Int = 0, height: Int = 0) {
        _isMirroring.value = active
        if (!active) {
            _fps.value = 0
            _dimensions.value = 0 to 0
            latestJpeg = null
            synchronized(frameLock) {
                frameSeq.incrementAndGet()
                frameLock.notifyAll()
            }
        } else {
            fpsWindowStartMs = System.currentTimeMillis()
            fpsFrameCount = 0
            if (width > 0 && height > 0) {
                _dimensions.value = width to height
            }
        }
    }

    fun publishFrame(jpegBytes: ByteArray, width: Int, height: Int) {
        latestJpeg = jpegBytes
        _dimensions.value = width to height

        val now = System.currentTimeMillis()
        fpsFrameCount++
        val elapsed = now - fpsWindowStartMs
        if (elapsed >= 1000L) {
            _fps.value = ((fpsFrameCount * 1000L) / elapsed).toInt()
            fpsWindowStartMs = now
            fpsFrameCount = 0
        }

        synchronized(frameLock) {
            frameSeq.incrementAndGet()
            frameLock.notifyAll()
        }
    }

    fun getLatestFrame(): ByteArray? = latestJpeg

    fun currentSeq(): Long = frameSeq.get()

    /**
     * Blocks briefly until a newer frame than [lastSeenSeq] arrives or timeout elapses.
     */
    fun awaitNextFrame(lastSeenSeq: Long, timeoutMs: Long = 1000L): Pair<Long, ByteArray?> {
        synchronized(frameLock) {
            if (frameSeq.get() == lastSeenSeq && _isMirroring.value) {
                runCatching { frameLock.wait(timeoutMs) }
            }
            return frameSeq.get() to latestJpeg
        }
    }

    fun onMirrorClientConnected() {
        _clientCount.value = activeClients.incrementAndGet()
    }

    fun onMirrorClientDisconnected() {
        _clientCount.value = activeClients.decrementAndGet().coerceAtLeast(0)
    }
}
