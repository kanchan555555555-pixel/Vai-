package com.example

import com.example.model.MirrorQuality
import com.example.network.DlnaSoapController
import com.example.network.MirrorFrameHub
import com.example.qr.QrCodeGenerator
import com.example.ui.components.formatDuration
import com.example.ui.components.formatFileSize
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExampleUnitTest {

    @Test
    fun qrCodeGenerator_encodesHotspotUrlWithValidFinderPatterns() {
        val matrix = QrCodeGenerator.encode("http://192.168.43.1:8899")
        assertEquals(25, matrix.size)
        assertTrue(matrix[0, 0])
        assertTrue(matrix[6, 6])
        assertFalse(matrix[1, 1])
        assertTrue(matrix[3, 3])
        assertTrue(matrix[8, matrix.size - 8])
    }

    @Test
    fun dlnaSoapFormatHms_formatsCorrectly() {
        assertEquals("00:00:00", DlnaSoapController.formatHms(0L))
        assertEquals("00:01:05", DlnaSoapController.formatHms(65_000L))
        assertEquals("01:02:03", DlnaSoapController.formatHms(3723_000L))
    }

    @Test
    fun mediaFormatHelpers_formatDurationAndSize() {
        assertEquals("00:00", formatDuration(0L))
        assertEquals("02:35", formatDuration(155_000L))
        assertEquals("1:01:00", formatDuration(3_660_000L))
        assertEquals("512 KB", formatFileSize(512 * 1024L))
        assertEquals("2.5 MB", formatFileSize((2.5 * 1024 * 1024).toLong()))
    }

    @Test
    fun mirrorFrameHub_publishesAndClearsFrames() {
        MirrorFrameHub.setMirroring(true, 720, 1280)
        assertTrue(MirrorFrameHub.isMirroring.value)
        assertEquals(720 to 1280, MirrorFrameHub.dimensions.value)

        val sampleFrame = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0x01, 0x02)
        val prevSeq = MirrorFrameHub.currentSeq()
        MirrorFrameHub.publishFrame(sampleFrame, 720, 1280)
        assertTrue(MirrorFrameHub.currentSeq() > prevSeq)
        assertArrayEquals(sampleFrame, MirrorFrameHub.getLatestFrame())

        MirrorFrameHub.setMirroring(false)
        assertFalse(MirrorFrameHub.isMirroring.value)
        assertEquals(null, MirrorFrameHub.getLatestFrame())
    }

    @Test
    fun mirrorQualityPresets_areOrderedByResolution() {
        assertTrue(MirrorQuality.SMOOTH.maxLongEdge < MirrorQuality.BALANCED.maxLongEdge)
        assertTrue(MirrorQuality.BALANCED.maxLongEdge < MirrorQuality.SHARP.maxLongEdge)
    }
}
