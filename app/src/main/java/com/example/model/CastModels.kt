package com.example.model

import android.net.Uri

enum class MediaKind {
    VIDEO,
    PHOTO,
    AUDIO
}

enum class CastMode {
    DLNA_TV,
    WEB_RECEIVER
}

enum class PlaybackState {
    IDLE,
    LOADING,
    PLAYING,
    PAUSED,
    STOPPED,
    ERROR
}

enum class AppLanguage(val code: String, val label: String) {
    BENGALI("bn", "বাংলা"),
    ENGLISH("en", "EN")
}

enum class MirrorQuality(
    val maxLongEdge: Int,
    val jpegQuality: Int,
    val targetFps: Int
) {
    SMOOTH(maxLongEdge = 960, jpegQuality = 62, targetFps = 20),
    BALANCED(maxLongEdge = 1280, jpegQuality = 72, targetFps = 18),
    SHARP(maxLongEdge = 1920, jpegQuality = 82, targetFps = 15)
}

data class MirrorSessionState(
    val isMirroring: Boolean = false,
    val quality: MirrorQuality = MirrorQuality.BALANCED,
    val width: Int = 0,
    val height: Int = 0,
    val fps: Int = 0,
    val mirrorClientsCount: Int = 0
)

data class LocalMediaItem(
    val id: String,
    val uri: Uri,
    val title: String,
    val mimeType: String,
    val kind: MediaKind,
    val sizeBytes: Long = 0L,
    val durationMs: Long = 0L
)

data class DiscoveredTv(
    val udn: String,
    val friendlyName: String,
    val manufacturer: String,
    val modelName: String,
    val host: String,
    val locationUrl: String,
    val avTransportControlUrl: String,
    val renderingControlUrl: String?
)

data class HotspotStatus(
    val isNetworkReady: Boolean = false,
    val isLikelyHotspot: Boolean = false,
    val primaryIp: String? = null,
    val interfaceName: String? = null,
    val allLocalIps: List<String> = emptyList()
)

data class CastSessionState(
    val serverRunning: Boolean = false,
    val serverPort: Int = 8899,
    val castMode: CastMode = CastMode.WEB_RECEIVER,
    val connectedTv: DiscoveredTv? = null,
    val activeItem: LocalMediaItem? = null,
    val playbackState: PlaybackState = PlaybackState.IDLE,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val volume: Int = 65,
    val isMuted: Boolean = false,
    val photoRotationDeg: Int = 0,
    val slideshowEnabled: Boolean = false,
    val slideshowIntervalSec: Int = 6,
    val webClientsCount: Int = 0,
    val statusMessage: String? = null
)
