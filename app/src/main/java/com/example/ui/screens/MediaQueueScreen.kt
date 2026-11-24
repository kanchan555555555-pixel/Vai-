package com.example.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AudioFile
import androidx.compose.material.icons.rounded.Cast
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.VideoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.i18n.UiStrings
import com.example.model.LocalMediaItem
import com.example.model.MediaKind
import com.example.ui.MediaFilter
import com.example.ui.components.formatDuration
import com.example.ui.components.formatFileSize
import com.example.ui.theme.AmberHotspot

@Composable
fun MediaQueueScreen(
    strings: UiStrings,
    mediaQueue: List<LocalMediaItem>,
    activeMediaId: String?,
    selectedFilter: MediaFilter,
    onFilterChange: (MediaFilter) -> Unit,
    onAddVisualMedia: (List<Uri>) -> Unit,
    onAddAudioOrFiles: (List<Uri>) -> Unit,
    onCastItem: (LocalMediaItem) -> Unit,
    onRemoveItem: (LocalMediaItem) -> Unit,
    onClearQueue: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Zero-permission Android Photo Picker for Videos & Photos
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(25)
    ) { uris ->
        if (uris.isNotEmpty()) onAddVisualMedia(uris)
    }

    // Zero-permission Storage Access Framework (SAF) for Audio / Video / Image files
    val safPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        if (uris.isNotEmpty()) onAddAudioOrFiles(uris)
    }

    val filteredItems = remember(mediaQueue, selectedFilter) {
        when (selectedFilter) {
            MediaFilter.ALL -> mediaQueue
            MediaFilter.VIDEO -> mediaQueue.filter { it.kind == MediaKind.VIDEO }
            MediaFilter.PHOTO -> mediaQueue.filter { it.kind == MediaKind.PHOTO }
            MediaFilter.AUDIO -> mediaQueue.filter { it.kind == MediaKind.AUDIO }
        }
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Picker Action Header
        item {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = strings.mediaTitle,
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    if (mediaQueue.isNotEmpty()) {
                        TextButton(
                            onClick = onClearQueue,
                            modifier = Modifier.testTag("clear_queue_button")
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.DeleteSweep,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                                tint = MaterialTheme.colorScheme.error
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                text = strings.clearQueue,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = {
                            photoPickerLauncher.launch(
                                PickVisualMediaRequest(
                                    ActivityResultContracts.PickVisualMedia.ImageAndVideo
                                )
                            )
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .minimumInteractiveComponentSize()
                            .testTag("pick_video_photo_button")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.PhotoLibrary,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(strings.pickVideoPhoto, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }

                    OutlinedButton(
                        onClick = {
                            safPickerLauncher.launch(arrayOf("audio/*", "video/*", "image/*"))
                        },
                        modifier = Modifier
                            .weight(1f)
                            .minimumInteractiveComponentSize()
                            .testTag("pick_audio_files_button")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.FolderOpen,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(strings.pickAudioFiles, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }

                // Filter Chips
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val filters = listOf(
                        MediaFilter.ALL to strings.filterAll,
                        MediaFilter.VIDEO to strings.filterVideo,
                        MediaFilter.PHOTO to strings.filterPhoto,
                        MediaFilter.AUDIO to strings.filterAudio
                    )
                    items(filters) { (filter, label) ->
                        FilterChip(
                            selected = selectedFilter == filter,
                            onClick = { onFilterChange(filter) },
                            label = { Text(label) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                selectedLabelColor = MaterialTheme.colorScheme.primary
                            ),
                            modifier = Modifier.testTag("filter_chip_${filter.name.lowercase()}")
                        )
                    }
                }
            }
        }

        if (filteredItems.isEmpty()) {
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
                            MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                            RoundedCornerShape(24.dp)
                        )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(28.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(68.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primaryContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.VideoLibrary,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(34.dp)
                            )
                        }
                        Text(
                            text = strings.emptyMediaTitle,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            textAlign = TextAlign.Center
                        )
                        Text(
                            text = strings.emptyMediaBody,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        } else {
            items(filteredItems, key = { it.id }) { item ->
                MediaQueueCard(
                    item = item,
                    isActive = item.id == activeMediaId,
                    strings = strings,
                    onCast = { onCastItem(item) },
                    onRemove = { onRemoveItem(item) }
                )
            }
        }

        item { Spacer(Modifier.height(76.dp)) }
    }
}

@Composable
private fun MediaQueueCard(
    item: LocalMediaItem,
    isActive: Boolean,
    strings: UiStrings,
    onCast: () -> Unit,
    onRemove: () -> Unit
) {
    val borderColor = if (isActive) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.outline.copy(alpha = 0.55f)
    }

    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isActive) {
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
            } else {
                MaterialTheme.colorScheme.surfaceContainerLow
            }
        ),
        modifier = Modifier
            .fillMaxWidth()
            .border(if (isActive) 1.5.dp else 1.dp, borderColor, RoundedCornerShape(20.dp))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Thumbnail / Media Type Icon
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                if (item.kind == MediaKind.PHOTO) {
                    AsyncImage(
                        model = item.uri,
                        contentDescription = item.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Icon(
                        imageVector = when (item.kind) {
                            MediaKind.VIDEO -> Icons.Rounded.Movie
                            MediaKind.AUDIO -> Icons.Rounded.AudioFile
                            MediaKind.PHOTO -> Icons.Rounded.Image
                        },
                        contentDescription = null,
                        tint = if (item.kind == MediaKind.VIDEO) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            AmberHotspot
                        },
                        modifier = Modifier.size(30.dp)
                    )
                }
            }

            Spacer(Modifier.width(12.dp))

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                if (isActive) {
                    Surface(
                        shape = RoundedCornerShape(999.dp),
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                    ) {
                        Text(
                            text = strings.nowCastingBadge,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                        )
                    }
                }
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                val metaParts = buildList {
                    add(
                        when (item.kind) {
                            MediaKind.VIDEO -> strings.filterVideo
                            MediaKind.PHOTO -> strings.filterPhoto
                            MediaKind.AUDIO -> strings.filterAudio
                        }
                    )
                    if (item.durationMs > 0L) add(formatDuration(item.durationMs))
                    val sizeLabel = formatFileSize(item.sizeBytes)
                    if (sizeLabel.isNotEmpty()) add(sizeLabel)
                }
                Text(
                    text = metaParts.joinToString(" • "),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(Modifier.width(8.dp))

            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Button(
                    onClick = onCast,
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isActive) AmberHotspot else MaterialTheme.colorScheme.primary,
                        contentColor = if (isActive) Color(0xFF1F1400) else MaterialTheme.colorScheme.onPrimary
                    ),
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("cast_item_button_${item.id}")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Cast,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(strings.castNow, style = MaterialTheme.typography.labelLarge)
                }

                IconButton(
                    onClick = onRemove,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("remove_item_button_${item.id}")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.DeleteOutline,
                        contentDescription = strings.removeFromQueue,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
