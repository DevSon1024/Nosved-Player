package com.devson.nvplayer.data.media

import android.content.Context
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.util.Locale
import kotlin.math.abs
import kotlin.math.round

object VideoMetadataExtractor {

    private const val TAG = "VideoMetadataExtractor"

    private val SUBTITLE_EXTENSIONS = setOf("srt", "vtt", "ass", "ssa", "sub", "smi", "idx")

    data class ExtractedDetails(
        val frameRate: Float?,
        val embeddedSubtitles: List<String>
    )

    fun isSubtitleExtension(ext: String?): Boolean {
        if (ext.isNullOrBlank()) return false
        return ext.lowercase(Locale.ROOT) in SUBTITLE_EXTENSIONS
    }

    /**
     * Locates external subtitle files residing in the same directory that match the video file name.
     * Returns a distinct list of uppercase extensions (e.g. ["SRT", "ASS"]).
     */
    fun findExternalSubtitles(videoPath: String?): List<String> {
        if (videoPath.isNullOrBlank()) return emptyList()
        return try {
            val videoFile = File(videoPath)
            val parentDir = videoFile.parentFile ?: return emptyList()
            if (!parentDir.exists() || !parentDir.isDirectory) return emptyList()

            val baseName = videoFile.nameWithoutExtension
            val subFiles = parentDir.listFiles { f ->
                f.isFile && isSubtitleExtension(f.extension)
            } ?: emptyArray()

            val matched = subFiles.filter { sub ->
                val subBase = sub.nameWithoutExtension
                subBase.equals(baseName, ignoreCase = true) ||
                    subBase.startsWith("$baseName.", ignoreCase = true) ||
                    subBase.startsWith("${baseName}_", ignoreCase = true) ||
                    subBase.startsWith("$baseName-", ignoreCase = true)
            }.map { it.extension.uppercase(Locale.ROOT) }.distinct()

            if (matched.isEmpty() && subFiles.size == 1) {
                // If only a single subtitle file exists in the directory, associate it
                listOf(subFiles[0].extension.uppercase(Locale.ROOT))
            } else {
                matched
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to find external subtitles for $videoPath", e)
            emptyList()
        }
    }

    /**
     * Extracts the real native video framerate (FPS) and embedded subtitle tracks.
     */
    suspend fun extractDetails(
        context: Context,
        path: String?,
        uri: Uri,
        durationMs: Long
    ): ExtractedDetails = withContext(Dispatchers.IO) {
        var fps: Float? = null
        val embeddedSubs = mutableSetOf<String>()

        val file = if (!path.isNullOrBlank()) File(path) else null
        val fileExists = file != null && file.exists()

        // 1. Fast Matroska EBML header inspection for MKV/WebM
        val isMkvOrWebm = path?.endsWith(".mkv", ignoreCase = true) == true ||
            path?.endsWith(".webm", ignoreCase = true) == true ||
            uri.toString().endsWith(".mkv", ignoreCase = true) ||
            uri.toString().endsWith(".webm", ignoreCase = true)

        try {
            val pfd = if (file != null && fileExists) {
                null
            } else {
                try {
                    context.contentResolver.openFileDescriptor(uri, "r")
                } catch (_: Exception) {
                    null
                }
            }
            val fis = if (file != null && fileExists) FileInputStream(file) else null
            val fd = fis?.fd ?: pfd?.fileDescriptor

            if (fd != null && MatroskaSubtitleDemuxer.isMatroskaFile(fd)) {
                val headerInfo = MatroskaSubtitleDemuxer.inspectMatroska(fd)
                if (fps == null && headerInfo.videoFps != null && headerInfo.videoFps > 0f) {
                    fps = headerInfo.videoFps
                }
                headerInfo.subtitleTracks.forEach { track ->
                    val ext = track.extension.uppercase(Locale.ROOT)
                    if (ext.isNotBlank()) {
                        embeddedSubs.add(ext)
                    } else {
                        embeddedSubs.add("SUB")
                    }
                }
            }
            try { fis?.close() } catch (_: Exception) {}
            try { pfd?.close() } catch (_: Exception) {}
        } catch (e: Exception) {
            Log.w(TAG, "Matroska fast track probe skipped", e)
        }

        // 2. MediaExtractor native inspection for Video FPS & Subtitle tracks
        try {
            val extractor = MediaExtractor()
            try {
                if (file != null && fileExists) {
                    extractor.setDataSource(file.absolutePath)
                } else {
                    extractor.setDataSource(context, uri, null)
                }

                val trackCount = extractor.trackCount
                for (i in 0 until trackCount) {
                    val format = extractor.getTrackFormat(i)
                    val mime = format.getString(MediaFormat.KEY_MIME) ?: ""

                    if (mime.startsWith("video/")) {
                        if (fps == null && format.containsKey(MediaFormat.KEY_FRAME_RATE)) {
                            try {
                                val rateInt = format.getInteger(MediaFormat.KEY_FRAME_RATE)
                                if (rateInt > 0) fps = rateInt.toFloat()
                            } catch (_: Exception) {
                                try {
                                    val rateFloat = format.getFloat(MediaFormat.KEY_FRAME_RATE)
                                    if (rateFloat > 0f) fps = rateFloat
                                } catch (_: Exception) {}
                            }
                        }
                        if (fps == null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && format.containsKey(MediaFormat.KEY_CAPTURE_RATE)) {
                            try {
                                val capRate = format.getFloat(MediaFormat.KEY_CAPTURE_RATE)
                                if (capRate > 0f) fps = capRate
                            } catch (_: Exception) {}
                        }
                    } else if (mime.startsWith("text/") || mime.startsWith("application/")) {
                        val subExt = when {
                            mime.contains("subrip", ignoreCase = true) || mime.contains("srt", ignoreCase = true) -> "SRT"
                            mime.contains("vtt", ignoreCase = true) -> "VTT"
                            mime.contains("ass", ignoreCase = true) || mime.contains("ssa", ignoreCase = true) -> "ASS"
                            mime.contains("pgs", ignoreCase = true) -> "PGS"
                            mime.contains("dvb", ignoreCase = true) -> "DVB"
                            mime.contains("tx3g", ignoreCase = true) || mime.contains("quicktime", ignoreCase = true) -> "SUB"
                            else -> "SUB"
                        }
                        embeddedSubs.add(subExt)
                    }
                }
            } finally {
                extractor.release()
            }
        } catch (e: Exception) {
            Log.w(TAG, "MediaExtractor inspection failed: ${e.localizedMessage}")
        }

        // 3. Fallback: MediaMetadataRetriever for capture framerate or frame-count calculation
        if (fps == null || fps <= 0f) {
            val retriever = MediaMetadataRetriever()
            try {
                if (file != null && fileExists) {
                    retriever.setDataSource(file.absolutePath)
                } else {
                    retriever.setDataSource(context, uri)
                }

                val capRateStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_CAPTURE_FRAMERATE)
                val capRate = capRateStr?.toFloatOrNull()
                if (capRate != null && capRate > 0f) {
                    fps = capRate
                } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    val frameCountStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_FRAME_COUNT)
                    val frameCount = frameCountStr?.toLongOrNull()
                    val dur = if (durationMs > 0L) {
                        durationMs
                    } else {
                        retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
                    }
                    if (frameCount != null && frameCount > 0L && dur > 0L) {
                        fps = (frameCount * 1000f) / dur.toFloat()
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "MediaMetadataRetriever inspection failed: ${e.localizedMessage}")
            } finally {
                try {
                    retriever.release()
                } catch (_: Exception) {}
            }
        }

        // 4. MediaInfo fallback for formats where native extractors fail (MKV, AVI, etc.)
        if (fps == null || fps <= 0f) {
            try {
                val pfd = context.contentResolver.openFileDescriptor(uri, "r")
                if (pfd != null) {
                    val mi = net.mediaarea.mediainfo.lib.MediaInfo()
                    try {
                        val detachedFd = pfd.detachFd()
                        mi.Open(detachedFd, path ?: "video")
                        val miFpsStr = mi.Get(net.mediaarea.mediainfo.lib.MediaInfo.Stream.Video, 0, "FrameRate")
                        val parsed = miFpsStr.toFloatOrNull()
                        if (parsed != null && parsed > 0f) {
                            fps = parsed
                        } else {
                            val strFps = mi.Get(net.mediaarea.mediainfo.lib.MediaInfo.Stream.Video, 0, "FrameRate/String")
                            val cleanStr = strFps.replace("FPS", "", ignoreCase = true).trim()
                            val parsedStr = cleanStr.toFloatOrNull()
                            if (parsedStr != null && parsedStr > 0f) {
                                fps = parsedStr
                            }
                        }
                    } finally {
                        try { mi.Close() } catch (_: Exception) {}
                        try { pfd.close() } catch (_: Exception) {}
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "MediaInfo fallback failed: ${e.localizedMessage}")
            }
        }

        // Normalize and clean up FPS value
        val cleanFps = fps?.let { raw ->
            if (raw > 0f && raw <= 360f) {
                val rounded = round(raw * 100f) / 100f
                if (abs(rounded - round(rounded)) < 0.01f) {
                    round(rounded)
                } else {
                    rounded
                }
            } else {
                null
            }
        }

        ExtractedDetails(
            frameRate = cleanFps,
            embeddedSubtitles = embeddedSubs.toList()
        )
    }
}
