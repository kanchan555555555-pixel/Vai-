package com.example.ui

import android.app.Application
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.MediaMetadataHelper
import com.example.data.PreferencesRepository
import com.example.i18n.stringsFor
import com.example.model.AppLanguage
import com.example.model.CastMode
import com.example.model.CastSessionState
import com.example.model.DiscoveredTv
import com.example.model.HotspotStatus
import com.example.model.LocalMediaItem
import com.example.model.MediaKind
import com.example.model.MirrorQuality
import com.example.model.MirrorSessionState
import com.example.model.PlaybackState
import com.example.network.DlnaSoapController
import com.example.network.HotspotNetworkMonitor
import com.example.network.LocalMediaServer
import com.example.network.MirrorFrameHub
import com.example.network.SsdpDlnaDiscovery
import com.example.service.CastForegroundService
import com.example.service.CastServiceBus
import com.example.service.MirrorServiceBus
import com.example.service.ScreenMirrorService
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

enum class MainTab {
    CAST,
    MEDIA,
    REMOTE
}

enum class MediaFilter {
    ALL,
    VIDEO,
    PHOTO,
    AUDIO
}

class HotCastViewModel(application: Application) : AndroidViewModel(application) {

    private val appContext = application.applicationContext
    private val prefs = PreferencesRepository(appContext)

    private val _language = MutableStateFlow(AppLanguage.BENGALI)
    val language: StateFlow<AppLanguage> = _language.asStateFlow()

    private val _selectedTab = MutableStateFlow(MainTab.CAST)
    val selectedTab: StateFlow<MainTab> = _selectedTab.asStateFlow()

    private val _mediaFilter = MutableStateFlow(MediaFilter.ALL)
    val mediaFilter: StateFlow<MediaFilter> = _mediaFilter.asStateFlow()

    private val _hotspotStatus = MutableStateFlow(HotspotStatus())
    val hotspotStatus: StateFlow<HotspotStatus> = _hotspotStatus.asStateFlow()

    private val _discoveredTvs = MutableStateFlow<List<DiscoveredTv>>(emptyList())
    val discoveredTvs: StateFlow<List<DiscoveredTv>> = _discoveredTvs.asStateFlow()

    private val _isScanningTvs = MutableStateFlow(false)
    val isScanningTvs: StateFlow<Boolean> = _isScanningTvs.asStateFlow()

    private val _mediaQueue = MutableStateFlow<List<LocalMediaItem>>(emptyList())
    val mediaQueue: StateFlow<List<LocalMediaItem>> = _mediaQueue.asStateFlow()

    private val _session = MutableStateFlow(CastSessionState())
    val session: StateFlow<CastSessionState> = _session.asStateFlow()

    private val _mirrorQuality = MutableStateFlow(MirrorQuality.BALANCED)
    val mirrorSession: StateFlow<MirrorSessionState> = combine(
        MirrorFrameHub.isMirroring,
        _mirrorQuality,
        MirrorFrameHub.dimensions,
        MirrorFrameHub.fps,
        MirrorFrameHub.clientCount
    ) { active, quality, dims, fps, clients ->
        MirrorSessionState(
            isMirroring = active,
            quality = quality,
            width = dims.first,
            height = dims.second,
            fps = fps,
            mirrorClientsCount = clients
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), MirrorSessionState())

    private var slideshowJob: Job? = null
    private var dlnaPollJob: Job? = null

    private val mediaServer = LocalMediaServer(
        context = appContext,
        onTvFeedback = { posMs, durMs, state ->
            if (_session.value.castMode == CastMode.WEB_RECEIVER && !MirrorFrameHub.isMirroring.value) {
                _session.update { current ->
                    current.copy(
                        positionMs = posMs,
                        durationMs = if (durMs > 0L) durMs else current.durationMs,
                        playbackState = state
                    )
                }
            }
        },
        onClientCountChanged = { count ->
            _session.update { it.copy(webClientsCount = count) }
        }
    )

    init {
        startLocalServerAndMonitor()
        observePreferences()
        observeServiceStops()
    }

    private fun observePreferences() {
        viewModelScope.launch {
            prefs.languageFlow.collect { lang -> _language.value = lang }
        }
        viewModelScope.launch {
            prefs.castModeFlow.collect { mode ->
                _session.update { it.copy(castMode = mode) }
            }
        }
        viewModelScope.launch {
            prefs.slideshowIntervalFlow.collect { sec ->
                _session.update { it.copy(slideshowIntervalSec = sec) }
            }
        }
    }

    private fun observeServiceStops() {
        viewModelScope.launch {
            CastServiceBus.stopRequests.collect {
                stopCasting()
            }
        }
        viewModelScope.launch {
            MirrorServiceBus.stopEvents.collect {
                mediaServer.pushMirrorCommand(false)
            }
        }
    }

    private fun startLocalServerAndMonitor() {
        val port = runCatching { mediaServer.start(8899) }.getOrDefault(8899)
        _session.update { it.copy(serverRunning = mediaServer.isRunning(), serverPort = port) }

        viewModelScope.launch {
            while (isActive) {
                refreshNetworkStatus()
                delay(4000L)
            }
        }
    }

    fun refreshNetworkStatus() {
        val status = HotspotNetworkMonitor.inspectLocalNetwork(appContext)
        _hotspotStatus.value = status
    }

    fun selectTab(tab: MainTab) {
        _selectedTab.value = tab
    }

    fun setMediaFilter(filter: MediaFilter) {
        _mediaFilter.value = filter
    }

    fun toggleLanguage() {
        val next = if (_language.value == AppLanguage.BENGALI) AppLanguage.ENGLISH else AppLanguage.BENGALI
        _language.value = next
        viewModelScope.launch { prefs.setLanguage(next) }
    }

    fun setCastMode(mode: CastMode) {
        _session.update { it.copy(castMode = mode, statusMessage = null) }
        viewModelScope.launch { prefs.setCastMode(mode) }
    }

    fun setMirrorQuality(quality: MirrorQuality) {
        _mirrorQuality.value = quality
        if (MirrorFrameHub.isMirroring.value) {
            ScreenMirrorService.updateQuality(appContext, quality)
        }
    }

    fun startScreenMirroring(resultCode: Int, data: Intent) {
        refreshNetworkStatus()
        val hostIp = _hotspotStatus.value.primaryIp
        if (hostIp.isNullOrBlank()) {
            _session.update {
                it.copy(statusMessage = stringsFor(_language.value).hotspotInactiveLabel)
            }
            return
        }

        // Stop media file casting if currently running so TV switches cleanly to live screen mirror
        if (_session.value.activeItem != null) {
            stopCasting()
        }

        if (!mediaServer.isRunning()) {
            val port = runCatching { mediaServer.start(8899) }.getOrDefault(8899)
            _session.update { it.copy(serverRunning = true, serverPort = port) }
        }

        val url = "http://$hostIp:${_session.value.serverPort}"
        ScreenMirrorService.start(
            context = appContext,
            resultCode = resultCode,
            data = data,
            quality = _mirrorQuality.value,
            receiverUrl = url
        )
        mediaServer.pushMirrorCommand(true)
    }

    fun stopScreenMirroring() {
        ScreenMirrorService.stop(appContext)
        mediaServer.pushMirrorCommand(false)
    }

    fun notifyMirrorPermissionDenied() {
        _session.update {
            it.copy(statusMessage = stringsFor(_language.value).mirrorPermissionDenied)
        }
    }

    fun openHotspotSettings() {
        val intents = listOf(
            Intent("android.settings.TETHER_SETTINGS"),
            Intent(Settings.ACTION_WIRELESS_SETTINGS),
            Intent(Settings.ACTION_WIFI_SETTINGS)
        )
        for (intent in intents) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            val resolved = runCatching {
                appContext.startActivity(intent)
                true
            }.getOrDefault(false)
            if (resolved) break
        }
    }

    fun openSystemWirelessDisplay() {
        val intents = listOf(
            Intent("android.settings.WIFI_DISPLAY_SETTINGS"),
            Intent(Settings.ACTION_CAST_SETTINGS),
            Intent(Settings.ACTION_WIRELESS_SETTINGS)
        )
        for (intent in intents) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            val resolved = runCatching {
                appContext.startActivity(intent)
                true
            }.getOrDefault(false)
            if (resolved) break
        }
    }

    fun scanDlnaTvs() {
        if (_isScanningTvs.value) return
        viewModelScope.launch {
            _isScanningTvs.value = true
            _session.update { it.copy(statusMessage = null) }
            refreshNetworkStatus()
            val found = SsdpDlnaDiscovery.discoverTvs(appContext, timeoutMs = 3600)
            _discoveredTvs.value = found
            _isScanningTvs.value = false
        }
    }

    fun addManualTv(locationUrl: String) {
        viewModelScope.launch {
            _isScanningTvs.value = true
            val tv = SsdpDlnaDiscovery.probeLocationUrl(locationUrl)
            _isScanningTvs.value = false
            if (tv != null) {
                val updated = (_discoveredTvs.value.filterNot { it.udn == tv.udn } + tv)
                _discoveredTvs.value = updated
                connectTv(tv)
            } else {
                val msg = stringsFor(_language.value).manualIpInvalid
                _session.update { it.copy(statusMessage = msg) }
            }
        }
    }

    fun connectTv(tv: DiscoveredTv) {
        _session.update {
            it.copy(
                connectedTv = tv,
                castMode = CastMode.DLNA_TV,
                statusMessage = null
            )
        }
        viewModelScope.launch { prefs.setCastMode(CastMode.DLNA_TV) }
    }

    fun disconnectTv() {
        stopDlnaPolling()
        _session.update { it.copy(connectedTv = null) }
    }

    fun addMediaUris(uris: List<Uri>, forcedKind: MediaKind? = null) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            val newItems = uris.map { uri ->
                runCatching {
                    appContext.contentResolver.takePersistableUriPermission(
                        uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )
                }
                MediaMetadataHelper.buildMediaItem(appContext, uri, forcedKind)
            }
            val combined = (_mediaQueue.value + newItems).distinctBy { it.uri.toString() }
            _mediaQueue.value = combined
            mediaServer.registerMedia(combined)
        }
    }

    fun removeMediaItem(item: LocalMediaItem) {
        val updated = _mediaQueue.value.filterNot { it.id == item.id }
        _mediaQueue.value = updated
        mediaServer.registerMedia(updated)
        if (_session.value.activeItem?.id == item.id) {
            stopCasting()
        }
    }

    fun clearMediaQueue() {
        _mediaQueue.value = emptyList()
        mediaServer.registerMedia(emptyList())
        stopCasting()
    }

    fun castItem(item: LocalMediaItem) {
        val hostIp = _hotspotStatus.value.primaryIp
        val strings = stringsFor(_language.value)
        if (hostIp.isNullOrBlank()) {
            _session.update { it.copy(statusMessage = strings.hotspotInactiveLabel) }
            return
        }

        val currentMode = _session.value.castMode
        val tv = _session.value.connectedTv
        if (currentMode == CastMode.DLNA_TV && tv == null) {
            _session.update { it.copy(statusMessage = strings.selectTvFirstNote) }
            _selectedTab.value = MainTab.CAST
            return
        }

        if (MirrorFrameHub.isMirroring.value) {
            stopScreenMirroring()
        }

        if (!mediaServer.isRunning()) {
            val port = runCatching { mediaServer.start(8899) }.getOrDefault(8899)
            _session.update { it.copy(serverRunning = true, serverPort = port) }
        }

        mediaServer.registerSingleMedia(item)
        val mediaUrl = mediaServer.mediaUrlFor(hostIp, item)

        CastForegroundService.startOrUpdate(
            context = appContext,
            title = item.title,
            subtitle = "http://$hostIp:${_session.value.serverPort}"
        )

        _session.update {
            it.copy(
                activeItem = item,
                playbackState = PlaybackState.LOADING,
                positionMs = 0L,
                durationMs = item.durationMs,
                photoRotationDeg = 0,
                statusMessage = null
            )
        }
        _selectedTab.value = MainTab.REMOTE

        if (currentMode == CastMode.WEB_RECEIVER) {
            mediaServer.pushReceiverState(
                item = item,
                hostIp = hostIp,
                command = "LOAD",
                seekToMs = 0L,
                volume = _session.value.volume,
                isMuted = _session.value.isMuted,
                rotationDeg = 0
            )
            _session.update { it.copy(playbackState = PlaybackState.PLAYING) }
            maybeStartSlideshow(item)
        } else if (tv != null) {
            stopDlnaPolling()
            viewModelScope.launch {
                val res = DlnaSoapController.castMedia(tv, mediaUrl, item)
                if (res.isSuccess) {
                    _session.update { it.copy(playbackState = PlaybackState.PLAYING) }
                    startDlnaPolling(tv)
                    maybeStartSlideshow(item)
                } else {
                    _session.update {
                        it.copy(
                            playbackState = PlaybackState.ERROR,
                            statusMessage = "${strings.dlnaErrorPrefix}: ${res.exceptionOrNull()?.message ?: ""}"
                        )
                    }
                }
            }
        }
    }

    fun togglePlayPause() {
        val state = _session.value
        val item = state.activeItem ?: return
        val hostIp = _hotspotStatus.value.primaryIp

        if (state.castMode == CastMode.WEB_RECEIVER) {
            val nextCmd = if (state.playbackState == PlaybackState.PLAYING) "PAUSE" else "PLAY"
            val nextState = if (nextCmd == "PLAY") PlaybackState.PLAYING else PlaybackState.PAUSED
            mediaServer.pushReceiverState(
                item = item,
                hostIp = hostIp,
                command = nextCmd,
                volume = state.volume,
                isMuted = state.isMuted,
                rotationDeg = state.photoRotationDeg
            )
            _session.update { it.copy(playbackState = nextState) }
        } else {
            val tv = state.connectedTv ?: return
            viewModelScope.launch {
                if (state.playbackState == PlaybackState.PLAYING) {
                    if (DlnaSoapController.pause(tv).isSuccess) {
                        _session.update { it.copy(playbackState = PlaybackState.PAUSED) }
                    }
                } else {
                    if (DlnaSoapController.play(tv).isSuccess) {
                        _session.update { it.copy(playbackState = PlaybackState.PLAYING) }
                    }
                }
            }
        }
    }

    fun seekTo(targetMs: Long) {
        val state = _session.value
        val item = state.activeItem ?: return
        val clamped = targetMs.coerceAtLeast(0L)
        val hostIp = _hotspotStatus.value.primaryIp

        _session.update { it.copy(positionMs = clamped) }
        if (state.castMode == CastMode.WEB_RECEIVER) {
            mediaServer.pushReceiverState(
                item = item,
                hostIp = hostIp,
                command = "SEEK",
                seekToMs = clamped,
                volume = state.volume,
                isMuted = state.isMuted,
                rotationDeg = state.photoRotationDeg
            )
        } else {
            val tv = state.connectedTv ?: return
            viewModelScope.launch {
                DlnaSoapController.seek(tv, clamped)
            }
        }
    }

    fun seekRelative(deltaMs: Long) {
        val dur = _session.value.durationMs
        val target = (_session.value.positionMs + deltaMs).let {
            if (dur > 0L) it.coerceIn(0L, dur) else it.coerceAtLeast(0L)
        }
        seekTo(target)
    }

    fun setVolume(volume: Int) {
        val clamped = volume.coerceIn(0, 100)
        val state = _session.value
        _session.update { it.copy(volume = clamped) }

        if (state.castMode == CastMode.WEB_RECEIVER) {
            mediaServer.pushReceiverState(
                item = state.activeItem,
                hostIp = _hotspotStatus.value.primaryIp,
                command = if (state.playbackState == PlaybackState.PLAYING) "PLAY" else "PAUSE",
                volume = clamped,
                isMuted = state.isMuted,
                rotationDeg = state.photoRotationDeg
            )
        } else {
            val tv = state.connectedTv ?: return
            viewModelScope.launch { DlnaSoapController.setVolume(tv, clamped) }
        }
    }

    fun toggleMute() {
        val state = _session.value
        val nextMuted = !state.isMuted
        _session.update { it.copy(isMuted = nextMuted) }

        if (state.castMode == CastMode.WEB_RECEIVER) {
            mediaServer.pushReceiverState(
                item = state.activeItem,
                hostIp = _hotspotStatus.value.primaryIp,
                command = if (state.playbackState == PlaybackState.PLAYING) "PLAY" else "PAUSE",
                volume = state.volume,
                isMuted = nextMuted,
                rotationDeg = state.photoRotationDeg
            )
        } else {
            val tv = state.connectedTv ?: return
            viewModelScope.launch { DlnaSoapController.setMute(tv, nextMuted) }
        }
    }

    fun rotatePhoto() {
        val state = _session.value
        val item = state.activeItem ?: return
        if (item.kind != MediaKind.PHOTO) return
        val nextDeg = (state.photoRotationDeg + 90) % 360
        _session.update { it.copy(photoRotationDeg = nextDeg) }
        mediaServer.pushReceiverState(
            item = item,
            hostIp = _hotspotStatus.value.primaryIp,
            command = "ROTATE",
            volume = state.volume,
            isMuted = state.isMuted,
            rotationDeg = nextDeg
        )
    }

    fun toggleSlideshow() {
        val next = !_session.value.slideshowEnabled
        _session.update { it.copy(slideshowEnabled = next) }
        val active = _session.value.activeItem
        if (next && active != null) {
            maybeStartSlideshow(active)
        } else {
            slideshowJob?.cancel()
            slideshowJob = null
        }
    }

    fun setSlideshowInterval(seconds: Int) {
        val clamped = seconds.coerceIn(3, 30)
        _session.update { it.copy(slideshowIntervalSec = clamped) }
        viewModelScope.launch { prefs.setSlideshowInterval(clamped) }
        val active = _session.value.activeItem
        if (_session.value.slideshowEnabled && active != null) {
            maybeStartSlideshow(active)
        }
    }

    private fun maybeStartSlideshow(activeItem: LocalMediaItem) {
        slideshowJob?.cancel()
        if (!_session.value.slideshowEnabled || activeItem.kind != MediaKind.PHOTO) return
        slideshowJob = viewModelScope.launch {
            delay(_session.value.slideshowIntervalSec * 1000L)
            val photos = _mediaQueue.value.filter { it.kind == MediaKind.PHOTO }
            if (photos.size > 1) {
                val idx = photos.indexOfFirst { it.id == activeItem.id }
                val nextPhoto = photos[(idx + 1) % photos.size]
                castItem(nextPhoto)
            }
        }
    }

    fun playNextInQueue() {
        val queue = _mediaQueue.value
        if (queue.isEmpty()) return
        val currentId = _session.value.activeItem?.id
        val idx = queue.indexOfFirst { it.id == currentId }
        val next = if (idx < 0) queue.first() else queue[(idx + 1) % queue.size]
        castItem(next)
    }

    fun playPreviousInQueue() {
        val queue = _mediaQueue.value
        if (queue.isEmpty()) return
        val currentId = _session.value.activeItem?.id
        val idx = queue.indexOfFirst { it.id == currentId }
        val prev = if (idx <= 0) queue.last() else queue[idx - 1]
        castItem(prev)
    }

    fun stopCasting() {
        slideshowJob?.cancel()
        slideshowJob = null
        stopDlnaPolling()

        val state = _session.value
        if (state.castMode == CastMode.DLNA_TV && state.connectedTv != null) {
            viewModelScope.launch { DlnaSoapController.stop(state.connectedTv) }
        }
        mediaServer.pushReceiverState(
            item = null,
            hostIp = _hotspotStatus.value.primaryIp,
            command = "STOP"
        )
        CastForegroundService.stop(appContext)
        _session.update {
            it.copy(
                activeItem = null,
                playbackState = PlaybackState.STOPPED,
                positionMs = 0L,
                durationMs = 0L
            )
        }
    }

    fun dismissStatusMessage() {
        _session.update { it.copy(statusMessage = null) }
    }

    private fun startDlnaPolling(tv: DiscoveredTv) {
        dlnaPollJob?.cancel()
        dlnaPollJob = viewModelScope.launch {
            while (isActive) {
                delay(1500L)
                val pos = DlnaSoapController.getPositionInfo(tv)
                val st = DlnaSoapController.getTransportState(tv)
                _session.update { curr ->
                    curr.copy(
                        positionMs = pos?.positionMs ?: curr.positionMs,
                        durationMs = if ((pos?.durationMs ?: 0L) > 0L) pos!!.durationMs else curr.durationMs,
                        playbackState = st ?: curr.playbackState
                    )
                }
            }
        }
    }

    private fun stopDlnaPolling() {
        dlnaPollJob?.cancel()
        dlnaPollJob = null
    }

    override fun onCleared() {
        slideshowJob?.cancel()
        dlnaPollJob?.cancel()
        mediaServer.stop()
        CastForegroundService.stop(appContext)
        ScreenMirrorService.stop(appContext)
        super.onCleared()
    }
}
