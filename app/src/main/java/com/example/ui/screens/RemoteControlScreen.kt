package com.example.ui.screens

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.VolumeOff
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.CastConnected
import androidx.compose.material.icons.rounded.Forward10
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Replay10
import androidx.compose.material.icons.rounded.RotateRight
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.Slideshow
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.Tv
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.i18n.UiStrings
import com.example.model.CastMode
import com.example.model.CastSessionState
import com.example.model.MediaKind
import com.example.model.PlaybackState
import com.example.ui.components.formatDuration
import com.example.ui.theme.AmberHotspot
import com.example.ui.theme.EmeraldLive
import com.example.ui.theme.RoseAlert

@Composable
fun RemoteControlScreen(
    strings: UiStrings,
    session: CastSessionState,
    queueSize: Int,
    onGoToMedia: () -> Unit,
    onTogglePlayPause: () -> Unit,
    onSeekTo: (Long) -> Unit,
    onSeekRelative: (Long) -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onRotatePhoto: () -> Unit,
    onToggleSlideshow: () -> Unit,
    onSlideshowIntervalChange: (Int) -> Unit,
    onVolumeChange: (Int) -> Unit,
    onToggleMute: () -> Unit,
    onStopCast: () -> Unit,
    modifier: Modifier = Modifier
) {
    val active = session.activeItem

    if (active == null) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(
                        1.dp,
                        MaterialTheme.colorScheme.outline.copy(alpha = 0.6f),
                        RoundedCornerShape(24.dp)
                    )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(28.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(72.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Tv,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(36.dp)
                        )
                    }
                    Text(
                        text = strings.nothingCastingTitle,
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Center
                    )
                    Text(
                        text = strings.nothingCastingBody,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                    Button(
                        onClick = onGoToMedia,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("empty_remote_go_media_button")
                    ) {
                        Text(strings.goToMedia)
                    }
                }
            }
        }
        return
    }

    val stateLabel = when (session.playbackState) {
        PlaybackState.PLAYING -> strings.statePlaying
        PlaybackState.PAUSED -> strings.statePaused
        PlaybackState.LOADING -> strings.stateLoading
        PlaybackState.STOPPED -> strings.stateStopped
        PlaybackState.ERROR -> strings.stateError
        PlaybackState.IDLE -> strings.stateIdle
    }
    val stateColor = when (session.playbackState) {
        PlaybackState.PLAYING -> EmeraldLive
        PlaybackState.LOADING -> AmberHotspot
        PlaybackState.ERROR -> RoseAlert
        else -> MaterialTheme.colorScheme.primary
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 1. Now Playing Preview Card
        item {
            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(
                        1.dp,
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.4f),
                        RoundedCornerShape(24.dp)
                    )
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = RoundedCornerShape(999.dp),
                            color = stateColor.copy(alpha = 0.16f)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.CastConnected,
                                    contentDescription = null,
                                    tint = stateColor,
                                    modifier = Modifier.size(14.dp)
                                )
                                Text(
                                    text = stateLabel,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = stateColor
                                )
                            }
                        }

                        Text(
                            text = if (session.castMode == CastMode.DLNA_TV) {
                                session.connectedTv?.friendlyName ?: "DLNA TV"
                            } else {
                                "TV Browser"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    // Visual Stage
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(190.dp)
                            .clip(RoundedCornerShape(18.dp))
                            .background(
                                Brush.radialGradient(
                                    colors = listOf(
                                        Color(0xFF1A2B44),
                                        Color(0xFF0A0E16)
                                    )
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        when (active.kind) {
                            MediaKind.PHOTO -> {
                                AsyncImage(
                                    model = active.uri,
                                    contentDescription = active.title,
                                    contentScale = ContentScale.Fit,
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .rotate(session.photoRotationDeg.toFloat())
                                )
                            }

                            MediaKind.AUDIO -> {
                                val infinite = rememberInfiniteTransition(label = "discSpin")
                                val angle by infinite.animateFloat(
                                    initialValue = 0f,
                                    targetValue = if (session.playbackState == PlaybackState.PLAYING) 360f else 0f,
                                    animationSpec = infiniteRepeatable(
                                        animation = tween(6000, easing = LinearEasing),
                                        repeatMode = RepeatMode.Restart
                                    ),
                                    label = "discAngle"
                                )
                                Box(
                                    modifier = Modifier
                                        .size(110.dp)
                                        .rotate(angle)
                                        .clip(CircleShape)
                                        .background(
                                            Brush.sweepGradient(
                                                listOf(
                                                    MaterialTheme.colorScheme.primary,
                                                    AmberHotspot,
                                                    MaterialTheme.colorScheme.primary
                                                )
                                            )
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(46.dp)
                                            .clip(CircleShape)
                                            .background(Color(0xFF0B0E14)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.MusicNote,
                                            contentDescription = null,
                                            tint = Color.White
                                        )
                                    }
                                }
                            }

                            MediaKind.VIDEO -> {
                                Icon(
                                    imageVector = Icons.Rounded.Movie,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(64.dp)
                                )
                            }
                        }
                    }

                    Text(
                        text = active.title,
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Center,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        // 2. Seek & Transport Controls (for Video / Audio) or Photo Controls
        item {
            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(
                        1.dp,
                        MaterialTheme.colorScheme.outline.copy(alpha = 0.6f),
                        RoundedCornerShape(24.dp)
                    )
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    if (active.kind != MediaKind.PHOTO) {
                        val dur = session.durationMs.coerceAtLeast(1L)
                        var isDragging by remember { mutableStateOf(false) }
                        var dragPos by remember { mutableFloatStateOf(0f) }
                        val displayPosMs = if (isDragging) {
                            dragPos.toLong()
                        } else {
                            session.positionMs.coerceIn(0L, dur)
                        }

                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Slider(
                                value = displayPosMs.toFloat(),
                                onValueChange = {
                                    isDragging = true
                                    dragPos = it
                                },
                                onValueChangeFinished = {
                                    isDragging = false
                                    onSeekTo(dragPos.toLong())
                                },
                                valueRange = 0f..dur.toFloat(),
                                colors = SliderDefaults.colors(
                                    thumbColor = MaterialTheme.colorScheme.primary,
                                    activeTrackColor = MaterialTheme.colorScheme.primary
                                ),
                                modifier = Modifier.testTag("seek_slider")
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = formatDuration(displayPosMs),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = formatDuration(session.durationMs),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        // Transport Buttons
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(
                                onClick = onPrevious,
                                enabled = queueSize > 1,
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .testTag("remote_prev_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.SkipPrevious,
                                    contentDescription = strings.previous,
                                    modifier = Modifier.size(30.dp)
                                )
                            }

                            IconButton(
                                onClick = { onSeekRelative(-10_000L) },
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .testTag("remote_rewind_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Replay10,
                                    contentDescription = strings.rewind10,
                                    modifier = Modifier.size(30.dp)
                                )
                            }

                            FilledIconButton(
                                onClick = onTogglePlayPause,
                                colors = IconButtonDefaults.filledIconButtonColors(
                                    containerColor = MaterialTheme.colorScheme.primary,
                                    contentColor = MaterialTheme.colorScheme.onPrimary
                                ),
                                modifier = Modifier
                                    .size(64.dp)
                                    .testTag("remote_play_pause_button")
                            ) {
                                Icon(
                                    imageVector = if (session.playbackState == PlaybackState.PLAYING) {
                                        Icons.Rounded.Pause
                                    } else {
                                        Icons.Rounded.PlayArrow
                                    },
                                    contentDescription = if (session.playbackState == PlaybackState.PLAYING) {
                                        strings.pause
                                    } else {
                                        strings.play
                                    },
                                    modifier = Modifier.size(34.dp)
                                )
                            }

                            IconButton(
                                onClick = { onSeekRelative(10_000L) },
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .testTag("remote_forward_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Forward10,
                                    contentDescription = strings.forward10,
                                    modifier = Modifier.size(30.dp)
                                )
                            }

                            IconButton(
                                onClick = onNext,
                                enabled = queueSize > 1,
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .testTag("remote_next_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.SkipNext,
                                    contentDescription = strings.next,
                                    modifier = Modifier.size(30.dp)
                                )
                            }
                        }
                    } else {
                        // Photo Remote Controls
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedButton(
                                onClick = onPrevious,
                                enabled = queueSize > 1,
                                modifier = Modifier
                                    .weight(1f)
                                    .minimumInteractiveComponentSize()
                                    .testTag("photo_prev_button")
                            ) {
                                Icon(Icons.Rounded.SkipPrevious, contentDescription = null)
                                Spacer(Modifier.width(4.dp))
                                Text(strings.previous)
                            }
                            Button(
                                onClick = onRotatePhoto,
                                modifier = Modifier
                                    .weight(1f)
                                    .minimumInteractiveComponentSize()
                                    .testTag("photo_rotate_button")
                            ) {
                                Icon(Icons.Rounded.RotateRight, contentDescription = null)
                                Spacer(Modifier.width(4.dp))
                                Text(strings.rotatePhoto, maxLines = 1)
                            }
                            OutlinedButton(
                                onClick = onNext,
                                enabled = queueSize > 1,
                                modifier = Modifier
                                    .weight(1f)
                                    .minimumInteractiveComponentSize()
                                    .testTag("photo_next_button")
                            ) {
                                Text(strings.next)
                                Spacer(Modifier.width(4.dp))
                                Icon(Icons.Rounded.SkipNext, contentDescription = null)
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Slideshow,
                                    contentDescription = null,
                                    tint = AmberHotspot
                                )
                                Column {
                                    Text(
                                        text = strings.slideshowToggle,
                                        style = MaterialTheme.typography.titleSmall
                                    )
                                    Text(
                                        text = strings.slideshowIntervalLabel(session.slideshowIntervalSec),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            Switch(
                                checked = session.slideshowEnabled,
                                onCheckedChange = { onToggleSlideshow() },
                                modifier = Modifier.testTag("slideshow_switch")
                            )
                        }

                        if (session.slideshowEnabled) {
                            Slider(
                                value = session.slideshowIntervalSec.toFloat(),
                                onValueChange = { onSlideshowIntervalChange(it.toInt()) },
                                valueRange = 3f..20f,
                                steps = 16,
                                modifier = Modifier.testTag("slideshow_interval_slider")
                            )
                        }
                    }

                    // Volume Row
                    if (active.kind != MediaKind.PHOTO) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            IconButton(
                                onClick = onToggleMute,
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .testTag("remote_mute_button")
                            ) {
                                Icon(
                                    imageVector = if (session.isMuted) {
                                        Icons.AutoMirrored.Rounded.VolumeOff
                                    } else {
                                        Icons.AutoMirrored.Rounded.VolumeUp
                                    },
                                    contentDescription = strings.muteToggle,
                                    tint = if (session.isMuted) RoseAlert else MaterialTheme.colorScheme.onSurface
                                )
                            }

                            Slider(
                                value = session.volume.toFloat(),
                                onValueChange = { onVolumeChange(it.toInt()) },
                                valueRange = 0f..100f,
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("volume_slider")
                            )

                            Text(
                                text = "${session.volume}%",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.width(42.dp),
                                textAlign = TextAlign.End
                            )
                        }
                    }

                    // Stop Casting Button
                    Button(
                        onClick = onStopCast,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = RoseAlert.copy(alpha = 0.16f),
                            contentColor = RoseAlert
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .minimumInteractiveComponentSize()
                            .border(1.dp, RoseAlert.copy(alpha = 0.5f), RoundedCornerShape(14.dp))
                            .testTag("stop_cast_button")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Stop,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(strings.stopCast)
                    }
                }
            }
        }

        item { Spacer(Modifier.height(76.dp)) }
    }
}
