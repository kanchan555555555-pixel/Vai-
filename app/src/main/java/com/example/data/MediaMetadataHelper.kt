package com.example.data

import android.content.ContentResolver
import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.OpenableColumns
import com.example.model.LocalMediaItem
import com.example.model.MediaKind
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.UUID

object MediaMetadataHelper {

    suspend fun buildMediaItem(
        context: Context,
        uri: Uri,
        forcedKind: MediaKind? = null
    ): LocalMediaItem = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        val rawMime = resolver.getType(uri) ?: guessMimeFromUri(uri)
        val kind = forcedKind ?: when {
            rawMime.startsWith("video/") -> MediaKind.VIDEO
            rawMime.startsWith("audio/") -> MediaKind.AUDIO
            else -> MediaKind.PHOTO
        }

        var displayName: String? = null
        var sizeBytes = 0L

        runCatching {
            resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)
                ?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        val sizeIdx = cursor.getColumnIndex(OpenableColumns.SIZE)
                        if (nameIdx >= 0) displayName = cursor.getString(nameIdx)
                        if (sizeIdx >= 0 && !cursor.isNull(sizeIdx)) sizeBytes = cursor.getLong(sizeIdx)
                    }
                }
        }

        if (sizeBytes <= 0L) {
            runCatching {
                resolver.openAssetFileDescriptor(uri, "r")?.use { afd ->
                    if (afd.length > 0) sizeBytes = afd.length
                }
            }
        }

        var durationMs = 0L
        if (kind == MediaKind.VIDEO || kind == MediaKind.AUDIO) {
            durationMs = extractDurationMs(context, resolver, uri)
        }

        val fallbackTitle = uri.lastPathSegment?.substringAfterLast('/') ?: when (kind) {
            MediaKind.VIDEO -> "Video"
            MediaKind.PHOTO -> "Photo"
            MediaKind.AUDIO -> "Audio"
        }

        val finalMime = if (rawMime == "*/*" || rawMime.isBlank()) {
            when (kind) {
                MediaKind.VIDEO -> "video/mp4"
                MediaKind.PHOTO -> "image/jpeg"
                MediaKind.AUDIO -> "audio/mpeg"
            }
        } else {
            rawMime
        }

        LocalMediaItem(
            id = UUID.randomUUID().toString().replace("-", "").take(12),
            uri = uri,
            title = displayName?.takeIf { it.isNotBlank() } ?: fallbackTitle,
            mimeType = finalMime,
            kind = kind,
            sizeBytes = sizeBytes,
            durationMs = durationMs
        )
    }

    private fun extractDurationMs(
        context: Context,
        resolver: ContentResolver,
        uri: Uri
    ): Long {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, uri)
            val durStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
            durStr?.toLongOrNull() ?: 0L
        } catch (_: Exception) {
            try {
                resolver.openFileDescriptor(uri, "r")?.use { pfd ->
                    retriever.setDataSource(pfd.fileDescriptor)
                    retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                        ?.toLongOrNull() ?: 0L
                } ?: 0L
            } catch (_: Exception) {
                0L
            }
        } finally {
            runCatching { retriever.release() }
        }
    }

    private fun guessMimeFromUri(uri: Uri): String {
        val path = uri.toString().lowercase()
        return when {
            path.endsWith(".mp4") || path.endsWith(".m4v") -> "video/mp4"
            path.endsWith(".mkv") -> "video/x-matroska"
            path.endsWith(".webm") -> "video/webm"
            path.endsWith(".mov") -> "video/quicktime"
            path.endsWith(".avi") -> "video/x-msvideo"
            path.endsWith(".mp3") -> "audio/mpeg"
            path.endsWith(".aac") || path.endsWith(".m4a") -> "audio/mp4"
            path.endsWith(".wav") -> "audio/wav"
            path.endsWith(".flac") -> "audio/flac"
            path.endsWith(".ogg") -> "audio/ogg"
            path.endsWith(".png") -> "image/png"
            path.endsWith(".webp") -> "image/webp"
            path.endsWith(".gif") -> "image/gif"
            else -> "image/jpeg"
        }
    }
}
