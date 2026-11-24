package com.example.ui

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.media.projection.MediaProjectionManager
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Cast
import androidx.compose.material.icons.outlined.PermMedia
import androidx.compose.material.icons.outlined.SettingsRemote
import androidx.compose.material.icons.rounded.Cast
import androidx.compose.material.icons.rounded.PermMedia
import androidx.compose.material.icons.rounded.SettingsRemote
import androidx.compose.material.icons.rounded.Translate
import androidx.compose.material.icons.rounded.WifiTethering
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.i18n.stringsFor
import com.example.model.LocalMediaItem
import com.example.ui.components.MiniNowPlayingBar
import com.example.ui.components.QrReceiverDialog
import com.example.ui.screens.CastHomeScreen
import com.example.ui.screens.MediaQueueScreen
import com.example.ui.screens.RemoteControlScreen
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HotCastApp(
    viewModel: HotCastViewModel = viewModel()
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    val language by viewModel.language.collectAsStateWithLifecycle()
    val selectedTab by viewModel.selectedTab.collectAsStateWithLifecycle()
    val mediaFilter by viewModel.mediaFilter.collectAsStateWithLifecycle()
    val hotspotStatus by viewModel.hotspotStatus.collectAsStateWithLifecycle()
    val discoveredTvs by viewModel.discoveredTvs.collectAsStateWithLifecycle()
    val isScanning by viewModel.isScanningTvs.collectAsStateWithLifecycle()
    val mediaQueue by viewModel.mediaQueue.collectAsStateWithLifecycle()
    val session by viewModel.session.collectAsStateWithLifecycle()
    val mirrorSession by viewModel.mirrorSession.collectAsStateWithLifecycle()

    val strings = remember(language) { stringsFor(language) }
    var showQrDialog by remember { mutableStateOf(false) }
    var pendingCastItem by remember { mutableStateOf<LocalMediaItem?>(null) }
    var pendingMirrorRequest by remember { mutableStateOf(false) }

    val screenCaptureLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val data = result.data
        if (result.resultCode == Activity.RESULT_OK && data != null) {
            viewModel.startScreenMirroring(result.resultCode, data)
        } else {
            viewModel.notifyMirrorPermissionDenied()
        }
    }

    fun launchScreenCaptureConsent() {
        val mgr = context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        screenCaptureLauncher.launch(mgr.createScreenCaptureIntent())
    }

    val notifPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { _ ->
        pendingCastItem?.let { item ->
            pendingCastItem = null
            viewModel.castItem(item)
        }
        if (pendingMirrorRequest) {
            pendingMirrorRequest = false
            launchScreenCaptureConsent()
        }
    }

    fun requestCastWithNotifPermission(item: LocalMediaItem) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) {
                pendingCastItem = item
                notifPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                return
            }
        }
        viewModel.castItem(item)
    }

    fun requestMirrorWithNotifPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) {
                pendingMirrorRequest = true
                notifPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                return
            }
        }
        launchScreenCaptureConsent()
    }

    BackHandler(enabled = selectedTab != MainTab.CAST) {
        viewModel.selectTab(MainTab.CAST)
    }

    LaunchedEffect(session.statusMessage) {
        val msg = session.statusMessage
        if (!msg.isNullOrBlank()) {
            snackbarHostState.showSnackbar(msg)
            viewModel.dismissStatusMessage()
        }
    }

    val receiverUrl = if (hotspotStatus.isNetworkReady && hotspotStatus.primaryIp != null) {
        "http://${hotspotStatus.primaryIp}:${session.serverPort}"
    } else {
        null
    }

    if (showQrDialog && receiverUrl != null) {
        QrReceiverDialog(
            url = receiverUrl,
            strings = strings,
            onCopied = {
                scope.launch { snackbarHostState.showSnackbar(strings.urlCopied) }
            },
            onDismiss = { showQrDialog = false }
        )
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val useRail = maxWidth >= 600.dp

        Scaffold(
            contentWindowInsets = WindowInsets.safeDrawing,
            snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
            topBar = {
                TopAppBar(
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background,
                        titleContentColor = MaterialTheme.colorScheme.onBackground
                    ),
                    title = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(34.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(MaterialTheme.colorScheme.primaryContainer),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.WifiTethering,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Text(
                                text = strings.appTitle,
                                style = MaterialTheme.typography.titleLarge
                            )
                        }
                    },
                    actions = {
                        Surface(
                            shape = RoundedCornerShape(999.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier
                                .padding(end = 12.dp)
                                .border(
                                    1.dp,
                                    MaterialTheme.colorScheme.outline,
                                    RoundedCornerShape(999.dp)
                                )
                                .clip(RoundedCornerShape(999.dp))
                                .minimumInteractiveComponentSize()
                                .clickable { viewModel.toggleLanguage() }
                                .testTag("language_toggle_button")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(5.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Translate,
                                    contentDescription = "Language",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = language.label,
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                )
            },
            bottomBar = {
                if (!useRail) {
                    NavigationBar(
                        containerColor = MaterialTheme.colorScheme.surface,
                        tonalElevation = 4.dp
                    ) {
                        MainTab.entries.forEach { tab ->
                            val selected = selectedTab == tab
                            val label = when (tab) {
                                MainTab.CAST -> strings.tabCast
                                MainTab.MEDIA -> strings.tabMedia
                                MainTab.REMOTE -> strings.tabRemote
                            }
                            val icon = when (tab) {
                                MainTab.CAST -> if (selected) Icons.Rounded.Cast else Icons.Outlined.Cast
                                MainTab.MEDIA -> if (selected) Icons.Rounded.PermMedia else Icons.Outlined.PermMedia
                                MainTab.REMOTE -> if (selected) Icons.Rounded.SettingsRemote else Icons.Outlined.SettingsRemote
                            }
                            NavigationBarItem(
                                selected = selected,
                                onClick = { viewModel.selectTab(tab) },
                                icon = { Icon(imageVector = icon, contentDescription = label) },
                                label = { Text(label) },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = MaterialTheme.colorScheme.primary,
                                    selectedTextColor = MaterialTheme.colorScheme.primary,
                                    indicatorColor = MaterialTheme.colorScheme.primaryContainer
                                ),
                                modifier = Modifier.testTag("nav_tab_${tab.name.lowercase()}")
                            )
                        }
                    }
                }
            }
        ) { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                if (useRail) {
                    NavigationRail(
                        containerColor = MaterialTheme.colorScheme.surface,
                        modifier = Modifier.fillMaxHeight()
                    ) {
                        Spacer(Modifier.size(12.dp))
                        MainTab.entries.forEach { tab ->
                            val selected = selectedTab == tab
                            val label = when (tab) {
                                MainTab.CAST -> strings.tabCast
                                MainTab.MEDIA -> strings.tabMedia
                                MainTab.REMOTE -> strings.tabRemote
                            }
                            val icon = when (tab) {
                                MainTab.CAST -> if (selected) Icons.Rounded.Cast else Icons.Outlined.Cast
                                MainTab.MEDIA -> if (selected) Icons.Rounded.PermMedia else Icons.Outlined.PermMedia
                                MainTab.REMOTE -> if (selected) Icons.Rounded.SettingsRemote else Icons.Outlined.SettingsRemote
                            }
                            NavigationRailItem(
                                selected = selected,
                                onClick = { viewModel.selectTab(tab) },
                                icon = { Icon(imageVector = icon, contentDescription = label) },
                                label = { Text(label) },
                                colors = NavigationRailItemDefaults.colors(
                                    selectedIconColor = MaterialTheme.colorScheme.primary,
                                    selectedTextColor = MaterialTheme.colorScheme.primary,
                                    indicatorColor = MaterialTheme.colorScheme.primaryContainer
                                ),
                                modifier = Modifier.testTag("rail_tab_${tab.name.lowercase()}")
                            )
                        }
                    }
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    contentAlignment = Alignment.TopCenter
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .widthIn(max = 840.dp)
                    ) {
                        when (selectedTab) {
                            MainTab.CAST -> CastHomeScreen(
                                strings = strings,
                                hotspotStatus = hotspotStatus,
                                session = session,
                                mirrorSession = mirrorSession,
                                discoveredTvs = discoveredTvs,
                                isScanning = isScanning,
                                receiverUrl = receiverUrl,
                                onStartScreenMirror = ::requestMirrorWithNotifPermission,
                                onStopScreenMirror = viewModel::stopScreenMirroring,
                                onSelectMirrorQuality = viewModel::setMirrorQuality,
                                onOpenSystemWirelessDisplay = viewModel::openSystemWirelessDisplay,
                                onOpenHotspotSettings = viewModel::openHotspotSettings,
                                onRefreshNetwork = viewModel::refreshNetworkStatus,
                                onShowQr = { showQrDialog = true },
                                onUrlCopied = {
                                    scope.launch { snackbarHostState.showSnackbar(strings.urlCopied) }
                                },
                                onSelectCastMode = viewModel::setCastMode,
                                onScanTvs = viewModel::scanDlnaTvs,
                                onAddManualTv = viewModel::addManualTv,
                                onConnectTv = viewModel::connectTv,
                                onDisconnectTv = viewModel::disconnectTv
                            )

                            MainTab.MEDIA -> MediaQueueScreen(
                                strings = strings,
                                mediaQueue = mediaQueue,
                                activeMediaId = session.activeItem?.id,
                                selectedFilter = mediaFilter,
                                onFilterChange = viewModel::setMediaFilter,
                                onAddVisualMedia = { uris -> viewModel.addMediaUris(uris) },
                                onAddAudioOrFiles = { uris -> viewModel.addMediaUris(uris) },
                                onCastItem = ::requestCastWithNotifPermission,
                                onRemoveItem = viewModel::removeMediaItem,
                                onClearQueue = viewModel::clearMediaQueue
                            )

                            MainTab.REMOTE -> RemoteControlScreen(
                                strings = strings,
                                session = session,
                                queueSize = mediaQueue.size,
                                onGoToMedia = { viewModel.selectTab(MainTab.MEDIA) },
                                onTogglePlayPause = viewModel::togglePlayPause,
                                onSeekTo = viewModel::seekTo,
                                onSeekRelative = viewModel::seekRelative,
                                onPrevious = viewModel::playPreviousInQueue,
                                onNext = viewModel::playNextInQueue,
                                onRotatePhoto = viewModel::rotatePhoto,
                                onToggleSlideshow = viewModel::toggleSlideshow,
                                onSlideshowIntervalChange = viewModel::setSlideshowInterval,
                                onVolumeChange = viewModel::setVolume,
                                onToggleMute = viewModel::toggleMute,
                                onStopCast = viewModel::stopCasting
                            )
                        }

                        if (selectedTab != MainTab.REMOTE) {
                            MiniNowPlayingBar(
                                session = session,
                                strings = strings,
                                onOpenRemote = { viewModel.selectTab(MainTab.REMOTE) },
                                onTogglePlayPause = viewModel::togglePlayPause,
                                onStop = viewModel::stopCasting,
                                modifier = Modifier
                                    .align(Alignment.BottomCenter)
                                    .padding(horizontal = 16.dp, vertical = 10.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
