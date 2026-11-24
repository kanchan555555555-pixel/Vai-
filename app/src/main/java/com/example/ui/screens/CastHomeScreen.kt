package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AddLink
import androidx.compose.material.icons.rounded.Cast
import androidx.compose.material.icons.rounded.CastConnected
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.QrCode2
import androidx.compose.material.icons.rounded.Radar
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.ScreenShare
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.StopScreenShare
import androidx.compose.material.icons.rounded.Tv
import androidx.compose.material.icons.rounded.WifiTethering
import androidx.compose.material.icons.rounded.WifiTetheringOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.i18n.UiStrings
import com.example.model.CastMode
import com.example.model.CastSessionState
import com.example.model.DiscoveredTv
import com.example.model.HotspotStatus
import com.example.model.MirrorQuality
import com.example.model.MirrorSessionState
import com.example.ui.theme.AmberHotspot
import com.example.ui.theme.EmeraldLive
import com.example.ui.theme.JetBrainsMonoFamily
import com.example.ui.theme.RoseAlert

@Composable
fun CastHomeScreen(
    strings: UiStrings,
    hotspotStatus: HotspotStatus,
    session: CastSessionState,
    mirrorSession: MirrorSessionState,
    discoveredTvs: List<DiscoveredTv>,
    isScanning: Boolean,
    receiverUrl: String?,
    onStartScreenMirror: () -> Unit,
    onStopScreenMirror: () -> Unit,
    onSelectMirrorQuality: (MirrorQuality) -> Unit,
    onOpenSystemWirelessDisplay: () -> Unit,
    onOpenHotspotSettings: () -> Unit,
    onRefreshNetwork: () -> Unit,
    onShowQr: () -> Unit,
    onUrlCopied: () -> Unit,
    onSelectCastMode: (CastMode) -> Unit,
    onScanTvs: () -> Unit,
    onAddManualTv: (String) -> Unit,
    onConnectTv: (DiscoveredTv) -> Unit,
    onDisconnectTv: () -> Unit,
    modifier: Modifier = Modifier
) {
    val clipboard = LocalClipboardManager.current
    var showManualInput by remember { mutableStateOf(false) }
    var manualUrl by remember { mutableStateOf("") }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 1. Hero Broadcast Banner
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(172.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .border(
                        1.dp,
                        MaterialTheme.colorScheme.outline.copy(alpha = 0.7f),
                        RoundedCornerShape(24.dp)
                    )
            ) {
                Image(
                    painter = painterResource(id = R.drawable.img_cast_hero),
                    contentDescription = strings.heroHeadline,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    Color(0x660B0E14),
                                    Color(0xEE0B0E14)
                                )
                            )
                        )
                )
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(18.dp),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    Surface(
                        shape = RoundedCornerShape(999.dp),
                        color = AmberHotspot.copy(alpha = 0.18f),
                        modifier = Modifier.border(
                            1.dp,
                            AmberHotspot.copy(alpha = 0.55f),
                            RoundedCornerShape(999.dp)
                        )
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.WifiTethering,
                                contentDescription = null,
                                tint = AmberHotspot,
                                modifier = Modifier.size(15.dp)
                            )
                            Text(
                                text = strings.offlineBadge,
                                style = MaterialTheme.typography.labelSmall,
                                color = AmberHotspot,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = strings.heroHeadline,
                            style = MaterialTheme.typography.headlineSmall,
                            color = Color.White
                        )
                        Text(
                            text = strings.heroSubheadline,
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color(0xFFCBD5E1),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }

        // 2. Live Offline Screen Mirroring Card
        item {
            ScreenMirrorCard(
                strings = strings,
                mirrorSession = mirrorSession,
                receiverUrl = receiverUrl,
                isNetworkReady = hotspotStatus.isNetworkReady,
                onStartMirror = onStartScreenMirror,
                onStopMirror = onStopScreenMirror,
                onSelectQuality = onSelectMirrorQuality,
                onOpenSystemCast = onOpenSystemWirelessDisplay
            )
        }

        // 3. Hotspot & Local Server Card
        item {
            HotspotStatusCard(
                strings = strings,
                hotspotStatus = hotspotStatus,
                receiverUrl = receiverUrl,
                onOpenHotspotSettings = onOpenHotspotSettings,
                onRefreshNetwork = onRefreshNetwork,
                onShowQr = onShowQr,
                onCopyUrl = { url ->
                    clipboard.setText(AnnotatedString(url))
                    onUrlCopied()
                }
            )
        }

        // 4. Casting Mode Selection
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = strings.castModeTitle,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onBackground
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    CastModeCard(
                        selected = session.castMode == CastMode.WEB_RECEIVER,
                        title = strings.modeWebTitle,
                        subtitle = if (session.webClientsCount > 0) {
                            strings.tvClientsConnected(session.webClientsCount)
                        } else {
                            strings.modeWebSubtitle
                        },
                        icon = Icons.Rounded.Language,
                        accentColor = MaterialTheme.colorScheme.primary,
                        onClick = { onSelectCastMode(CastMode.WEB_RECEIVER) },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("mode_web_receiver_card")
                    )

                    CastModeCard(
                        selected = session.castMode == CastMode.DLNA_TV,
                        title = strings.modeDlnaTitle,
                        subtitle = session.connectedTv?.friendlyName ?: strings.modeDlnaSubtitle,
                        icon = Icons.Rounded.Tv,
                        accentColor = AmberHotspot,
                        onClick = { onSelectCastMode(CastMode.DLNA_TV) },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("mode_dlna_tv_card")
                    )
                }
            }
        }

        // 5. Nearby Smart TVs (DLNA Discovery)
        item {
            Card(
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(
                        1.dp,
                        MaterialTheme.colorScheme.outline.copy(alpha = 0.65f),
                        RoundedCornerShape(22.dp)
                    )
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Radar,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = strings.nearbyTvsTitle,
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }

                        Button(
                            onClick = onScanTvs,
                            enabled = !isScanning,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary
                            ),
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("scan_tvs_button")
                        ) {
                            if (isScanning) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.onPrimary
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(strings.scanningTvs)
                            } else {
                                Text(strings.scanTvs)
                            }
                        }
                    }

                    if (discoveredTvs.isEmpty()) {
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = strings.noTvFoundTitle,
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = strings.noTvFoundBody,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            discoveredTvs.forEach { tv ->
                                val isConnected = session.connectedTv?.udn == tv.udn
                                TvDeviceRow(
                                    tv = tv,
                                    isConnected = isConnected,
                                    strings = strings,
                                    onConnect = { onConnectTv(tv) },
                                    onDisconnect = onDisconnectTv
                                )
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(
                            onClick = { showManualInput = !showManualInput },
                            modifier = Modifier.testTag("toggle_manual_tv_button")
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.AddLink,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(strings.manualTvAdd)
                        }
                    }

                    AnimatedVisibility(visible = showManualInput) {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            OutlinedTextField(
                                value = manualUrl,
                                onValueChange = { manualUrl = it },
                                placeholder = { Text(strings.manualTvHint) },
                                singleLine = true,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("manual_tv_url_input")
                            )
                            Button(
                                onClick = {
                                    if (manualUrl.isNotBlank()) {
                                        onAddManualTv(manualUrl)
                                    }
                                },
                                modifier = Modifier
                                    .align(Alignment.End)
                                    .minimumInteractiveComponentSize()
                                    .testTag("manual_tv_connect_button")
                            ) {
                                Text(strings.connect)
                            }
                        }
                    }
                }
            }
        }

        item { Spacer(Modifier.height(72.dp)) }
    }
}

@Composable
private fun ScreenMirrorCard(
    strings: UiStrings,
    mirrorSession: MirrorSessionState,
    receiverUrl: String?,
    isNetworkReady: Boolean,
    onStartMirror: () -> Unit,
    onStopMirror: () -> Unit,
    onSelectQuality: (MirrorQuality) -> Unit,
    onOpenSystemCast: () -> Unit
) {
    val active = mirrorSession.isMirroring
    val borderColor = if (active) EmeraldLive else MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)

    Card(
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (active) {
                EmeraldLive.copy(alpha = 0.1f)
            } else {
                MaterialTheme.colorScheme.surfaceContainerLow
            }
        ),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.5.dp, borderColor, RoundedCornerShape(22.dp))
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(CircleShape)
                            .background(
                                if (active) EmeraldLive.copy(alpha = 0.2f)
                                else MaterialTheme.colorScheme.primaryContainer
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (active) Icons.Rounded.StopScreenShare else Icons.Rounded.ScreenShare,
                            contentDescription = null,
                            tint = if (active) EmeraldLive else MaterialTheme.colorScheme.primary
                        )
                    }
                    Column {
                        Text(
                            text = strings.mirrorCardTitle,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        if (active && mirrorSession.width > 0) {
                            Text(
                                text = strings.mirrorActiveInfo(
                                    mirrorSession.fps,
                                    mirrorSession.width,
                                    mirrorSession.height
                                ),
                                style = MaterialTheme.typography.labelMedium,
                                color = EmeraldLive
                            )
                        }
                    }
                }

                if (active) {
                    Surface(
                        shape = RoundedCornerShape(999.dp),
                        color = EmeraldLive.copy(alpha = 0.2f)
                    ) {
                        Text(
                            text = strings.mirrorLiveBadge,
                            style = MaterialTheme.typography.labelSmall,
                            color = EmeraldLive,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }
                }
            }

            Text(
                text = if (active && receiverUrl != null) {
                    "${strings.mirrorHowToTip}\n$receiverUrl"
                } else {
                    strings.mirrorCardSubtitle
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // Quality Preset Chips
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = strings.mirrorQualityLabel,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(MirrorQuality.entries) { q ->
                        FilterChip(
                            selected = mirrorSession.quality == q,
                            onClick = { onSelectQuality(q) },
                            label = { Text(strings.qualityLabel(q)) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                selectedLabelColor = MaterialTheme.colorScheme.primary
                            ),
                            modifier = Modifier.testTag("mirror_quality_${q.name.lowercase()}")
                        )
                    }
                }
            }

            // Primary Start / Stop Button + System Wireless Display fallback
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (active) {
                    Button(
                        onClick = onStopMirror,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = RoseAlert,
                            contentColor = Color.White
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .minimumInteractiveComponentSize()
                            .testTag("stop_screen_mirror_button")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.StopScreenShare,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(strings.mirrorStopButton)
                    }
                } else {
                    Button(
                        onClick = onStartMirror,
                        enabled = isNetworkReady,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .minimumInteractiveComponentSize()
                            .testTag("start_screen_mirror_button")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.ScreenShare,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(strings.mirrorStartButton)
                    }
                }

                OutlinedButton(
                    onClick = onOpenSystemCast,
                    modifier = Modifier
                        .fillMaxWidth()
                        .minimumInteractiveComponentSize()
                        .testTag("system_wireless_display_button")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Cast,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(strings.mirrorSystemCastButton)
                }
            }
        }
    }
}

@Composable
private fun HotspotStatusCard(
    strings: UiStrings,
    hotspotStatus: HotspotStatus,
    receiverUrl: String?,
    onOpenHotspotSettings: () -> Unit,
    onRefreshNetwork: () -> Unit,
    onShowQr: () -> Unit,
    onCopyUrl: (String) -> Unit
) {
    val pulseTransition = rememberInfiniteTransition(label = "hotspotPulse")
    val pulseAlpha by pulseTransition.animateFloat(
        initialValue = 0.45f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(900),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )

    val statusColor = when {
        hotspotStatus.isLikelyHotspot -> EmeraldLive
        hotspotStatus.isNetworkReady -> MaterialTheme.colorScheme.primary
        else -> RoseAlert
    }

    val statusLabel = when {
        hotspotStatus.isLikelyHotspot -> strings.hotspotActiveLabel
        hotspotStatus.isNetworkReady -> strings.wifiLanActiveLabel
        else -> strings.hotspotInactiveLabel
    }

    Card(
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, statusColor.copy(alpha = 0.45f), RoundedCornerShape(22.dp))
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(statusColor.copy(alpha = 0.16f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (hotspotStatus.isNetworkReady) {
                                Icons.Rounded.WifiTethering
                            } else {
                                Icons.Rounded.WifiTetheringOff
                            },
                            contentDescription = null,
                            tint = statusColor
                        )
                    }

                    Column {
                        Text(
                            text = strings.hotspotCardTitle,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .alpha(if (hotspotStatus.isNetworkReady) pulseAlpha else 1f)
                                    .clip(CircleShape)
                                    .background(statusColor)
                            )
                            Text(
                                text = statusLabel,
                                style = MaterialTheme.typography.bodyMedium,
                                color = statusColor,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }

                FilledTonalButton(
                    onClick = onRefreshNetwork,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("refresh_network_button")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Refresh,
                        contentDescription = strings.refreshNetwork,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            if (receiverUrl != null) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = strings.serverAddressLabel,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = receiverUrl,
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontFamily = JetBrainsMonoFamily,
                                fontWeight = FontWeight.Bold
                            ),
                            color = MaterialTheme.colorScheme.primary
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            OutlinedButton(
                                onClick = { onCopyUrl(receiverUrl) },
                                modifier = Modifier
                                    .weight(1f)
                                    .minimumInteractiveComponentSize()
                                    .testTag("copy_receiver_url_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.ContentCopy,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(strings.copyUrl, maxLines = 1)
                            }
                            Button(
                                onClick = onShowQr,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = AmberHotspot,
                                    contentColor = Color(0xFF1F1400)
                                ),
                                modifier = Modifier
                                    .weight(1f)
                                    .minimumInteractiveComponentSize()
                                    .testTag("show_qr_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.QrCode2,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(strings.showQrCode, maxLines = 1)
                            }
                        }
                    }
                }
            }

            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.65f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = strings.hotspotGuideTitle,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = strings.hotspotStep1,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = strings.hotspotStep2,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = strings.hotspotStep3,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            OutlinedButton(
                onClick = onOpenHotspotSettings,
                modifier = Modifier
                    .fillMaxWidth()
                    .minimumInteractiveComponentSize()
                    .testTag("open_hotspot_settings_button")
            ) {
                Icon(
                    imageVector = Icons.Rounded.Settings,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(strings.openHotspotSettings)
            }
        }
    }
}

@Composable
private fun CastModeCard(
    selected: Boolean,
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    accentColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val borderColor = if (selected) accentColor else MaterialTheme.colorScheme.outline.copy(alpha = 0.6f)
    val bgColor = if (selected) {
        accentColor.copy(alpha = 0.14f)
    } else {
        MaterialTheme.colorScheme.surfaceContainerLow
    }

    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = bgColor),
        modifier = modifier
            .border(if (selected) 1.5.dp else 1.dp, borderColor, RoundedCornerShape(18.dp))
            .clickable(onClick = onClick)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = if (selected) accentColor else MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (selected) {
                    Icon(
                        imageVector = Icons.Rounded.CheckCircle,
                        contentDescription = null,
                        tint = accentColor,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun TvDeviceRow(
    tv: DiscoveredTv,
    isConnected: Boolean,
    strings: UiStrings,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = if (isConnected) {
            AmberHotspot.copy(alpha = 0.14f)
        } else {
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
        },
        modifier = Modifier
            .fillMaxWidth()
            .border(
                1.dp,
                if (isConnected) AmberHotspot else Color.Transparent,
                RoundedCornerShape(16.dp)
            )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(
                        if (isConnected) AmberHotspot.copy(alpha = 0.2f)
                        else MaterialTheme.colorScheme.surface
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (isConnected) Icons.Rounded.CastConnected else Icons.Rounded.Cast,
                    contentDescription = null,
                    tint = if (isConnected) AmberHotspot else MaterialTheme.colorScheme.primary
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = tv.friendlyName,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${tv.manufacturer} • ${tv.host}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(Modifier.width(8.dp))
            if (isConnected) {
                OutlinedButton(
                    onClick = onDisconnect,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("disconnect_tv_${tv.host}")
                ) {
                    Text(strings.disconnect)
                }
            } else {
                Button(
                    onClick = onConnect,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("connect_tv_${tv.host}")
                ) {
                    Text(strings.connect)
                }
            }
        }
    }
}
