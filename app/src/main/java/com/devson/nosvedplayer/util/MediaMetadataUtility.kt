package com.devson.nosvedplayer.util

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.annotation.OptIn
import com.devson.nosvedplayer.data.WatchHistoryDao
import com.devson.nosvedplayer.model.Video
import com.devson.nosvedplayer.model.WatchHistory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.MetadataRetriever
import java.util.Locale
import androidx.core.net.toUri

data class DetailedVideoMetadata(
    val video: Video,
    val history: WatchHistory?,
    val format: String,
    val resolution: String,
    val encodingSW: String?,
    val tracks: List<TrackMetadata>
)

data class TrackMetadata(
    val type: TrackType,
    val codec: String?,
    val language: String?,
    val extra: Map<String, String>
)

enum class TrackType { VIDEO, AUDIO, SUBTITLE, OTHER }


@OptIn(UnstableApi::class)
suspend fun getVideoMetadata(
    context: Context,
    video: Video,
    dao: WatchHistoryDao
): DetailedVideoMetadata = coroutineScope {
    val history = async(Dispatchers.IO) { dao.getHistoryByUri(video.uri) }
    
    // Resolve path early for display and external sub check
    val resolvedPath = withContext(Dispatchers.IO) {
        try {
            val uri = video.uri.toUri()
            if (uri.scheme == "content") {
                context.contentResolver.query(uri, arrayOf("_data"), null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) cursor.getString(cursor.getColumnIndexOrThrow("_data")) else video.uri
                } ?: video.uri
            } else video.uri.replace("file://", "")
        } catch (_: Exception) { video.uri }
    }

    val tracksJob = async(Dispatchers.IO) {
        val tracks = mutableListOf<TrackMetadata>()
        var resolution = "Unknown"
        try {
            val mediaItem = MediaItem.fromUri(video.uri)
            // MetadataRetriever uses ExoPlayer's extractors, much better than system MediaExtractor
            val trackGroups = MetadataRetriever.retrieveMetadata(context, mediaItem).get()
            
            for (i in 0 until trackGroups.length) {
                val group = trackGroups.get(i)
                val format = group.getFormat(0)
                val mime = format.sampleMimeType ?: ""
                
                val type = when (group.type) {
                    C.TRACK_TYPE_VIDEO -> TrackType.VIDEO
                    C.TRACK_TYPE_AUDIO -> TrackType.AUDIO
                    C.TRACK_TYPE_TEXT -> TrackType.SUBTITLE
                    else -> TrackType.OTHER
                }

                val codec = when {
                    mime.contains("avc") || mime.contains("h264") -> "H.264 (AVC)"
                    mime.contains("hevc") || mime.contains("h265") -> "H.265 (HEVC)"
                    mime.contains("vp9") -> "VP9"
                    mime.contains("vp8") -> "VP8"
                    mime.contains("av1") -> "AV1"
                    mime.contains("mp4a") -> "AAC"
                    mime.contains("ac3") -> "AC-3"
                    mime.contains("eac3") -> "E-AC-3"
                    mime.contains("opus") -> "Opus"
                    mime.contains("vorbis") -> "Vorbis"
                    mime.contains("flac") -> "FLAC"
                    mime.contains("x-subrip") || mime.contains("srt") -> "SRT"
                    mime.contains("vtt") -> "WebVTT"
                    mime.contains("pgs") -> "PGS"
                    mime.contains("vobsub") || mime.contains("dvd") -> "VOBSUB"
                    mime.contains("ssa") || mime.contains("ass") -> "SSA/ASS"
                    else -> mime.substringAfterLast('/').uppercase()
                }

                val language = format.language?.let { Locale(it).displayLanguage }
                val extra = mutableMapOf<String, String>()
                
                format.label?.let { extra["Title"] = it }
                
                when (type) {
                    TrackType.VIDEO -> {
                        if (format.width > 0 && format.height > 0) {
                            resolution = "${format.width}x${format.height}"
                            extra["Resolution"] = resolution
                        }
                        if (format.frameRate > 0) extra["Frame Rate"] = "${format.frameRate.toInt()} fps"
                    }
                    TrackType.AUDIO -> {
                        if (format.sampleRate > 0) extra["Sample Rate"] = "${format.sampleRate} Hz"
                        if (format.channelCount > 0) extra["Channels"] = format.channelCount.toString()
                        if (format.bitrate > 0) extra["Bitrate"] = "${format.bitrate / 1000} kbps"
                    }
                    TrackType.SUBTITLE -> {
                        if (format.selectionFlags and C.SELECTION_FLAG_FORCED != 0) extra["Type"] = "Forced"
                    }
                    else -> extra["MIME"] = mime
                }
                tracks.add(TrackMetadata(type, codec, language, extra))
            }
        } catch (_: Exception) {}
        tracks to resolution
    }

    val retrieverJob = async(Dispatchers.IO) {
        val retriever = MediaMetadataRetriever()
        var format = "Unknown"
        var writeBy: String? = null
        try {
            retriever.setDataSource(context, video.uri.toUri())
            val mime = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_MIMETYPE)
            format = when {
                mime == null -> video.uri.substringAfterLast('.', "Unknown").uppercase()
                mime.contains("mp4") -> "MP4"
                mime.contains("matroska") || mime.contains("x-matroska") -> "MKV"
                mime.contains("webm") -> "WebM"
                mime.contains("quicktime") -> "MOV"
                mime.contains("avi") -> "AVI"
                else -> mime.substringAfterLast('/').uppercase()
            }
            writeBy = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_WRITER)
        } catch (_: Exception) {} finally { retriever.release() }
        format to writeBy
    }

    val externalSubJob = async(Dispatchers.IO) {
        val externalTracks = mutableListOf<TrackMetadata>()
        try {
            val file = File(resolvedPath)
            if (file.exists()) {
                val base = file.nameWithoutExtension
                val subExts = listOf("srt", "vtt", "ass", "ssa")
                file.parentFile?.listFiles { _, name ->
                    subExts.any { name.equals("$base.$it", true) }
                }?.forEach { sub ->
                    externalTracks.add(TrackMetadata(TrackType.SUBTITLE, sub.extension.uppercase(), "External", mapOf("Source" to "External File")))
                }
            }
        } catch (_: Exception) {}
        externalTracks
    }

    val (extractorTracks, resolution) = tracksJob.await()
    val (containerFormat, encodingSW) = retrieverJob.await()
    val externalTracks = externalSubJob.await()

    DetailedVideoMetadata(
        video = video.copy(uri = resolvedPath),
        history = history.await(),
        format = containerFormat,
        resolution = resolution,
        encodingSW = encodingSW,
        tracks = extractorTracks + externalTracks
    )
}
