package com.devson.nvplayer.data.media

import android.util.Log
import com.devson.nvplayer.data.model.SubtitleStreamInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.FileDescriptor
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.util.Locale
import java.util.zip.Inflater
import kotlin.coroutines.coroutineContext

object MatroskaSubtitleDemuxer {

    private const val TAG = "MatroskaSubtitleDemux"

    // Top-Level EBML IDs
    private const val ID_EBML = 0x1A45DFA3L
    private const val ID_SEGMENT = 0x18538067L
    private const val ID_INFO = 0x1549A966L
    private const val ID_TIMECODE_SCALE = 0x2AD7B1L
    private const val ID_TRACKS = 0x1654AE6BL
    private const val ID_TRACK_ENTRY = 0xAEL
    private const val ID_TRACK_NUMBER = 0xD7L
    private const val ID_TRACK_UID = 0x73C5L
    private const val ID_TRACK_TYPE = 0x83L
    private const val ID_NAME = 0x536EL
    private const val ID_CODEC_ID = 0x86L
    private const val ID_CODEC_PRIVATE = 0x63A2L
    private const val ID_LANGUAGE = 0x22B59CL
    private const val ID_LANGUAGE_IETF = 0x22B59DL
    private const val ID_CONTENT_ENCODINGS = 0x6D80L
    private const val ID_CONTENT_COMP_ALGO = 0x4254L
    private const val ID_DEFAULT_DURATION = 0x23E383L
    private const val ID_VIDEO = 0xE0L
    private const val ID_FRAME_RATE = 0x2383E3L

    // Cluster IDs
    private const val ID_CLUSTER = 0x1F43B675L
    private const val ID_CLUSTER_TIMECODE = 0xE7L
    private const val ID_SIMPLE_BLOCK = 0xA3L
    private const val ID_BLOCK_GROUP = 0xA0L
    private const val ID_BLOCK = 0xA1L
    private const val ID_BLOCK_DURATION = 0x9BL

    data class MatroskaHeaderInfo(
        val videoFps: Float? = null,
        val subtitleTracks: List<SubtitleStreamInfo> = emptyList()
    )

    private data class MatroskaParseResult(
        val videoFps: Float? = null,
        val tracks: List<MatroskaTrack> = emptyList()
    )

    data class MatroskaTrack(
        val trackNumber: Long,
        val trackUid: Long,
        val trackType: Long,
        val codecId: String,
        val name: String,
        val language: String,
        val codecPrivate: ByteArray?,
        val isCompressed: Boolean
    ) {
        val isSubtitle: Boolean get() = trackType == 17L // 0x11 = Subtitle

        val isImageBased: Boolean
            get() {
                val lower = codecId.lowercase()
                return lower.contains("pgs") ||
                       lower.contains("vobsub") ||
                       lower.contains("dvd") ||
                       lower.contains("image") ||
                       lower.contains("bitmap")
            }

        val extension: String
            get() = when {
                isImageBased -> ""
                codecId.contains("ASS", ignoreCase = true) || codecId.contains("SSA", ignoreCase = true) -> "ass"
                codecId.contains("VTT", ignoreCase = true) -> "vtt"
                else -> "srt"
            }

        val codecName: String
            get() = when {
                isImageBased -> "Image-based (PGS/VobSub) - Cannot export as text"
                codecId.contains("ASS", ignoreCase = true) -> "ASS"
                codecId.contains("SSA", ignoreCase = true) -> "SSA"
                codecId.contains("VTT", ignoreCase = true) -> "VTT"
                else -> "subrip"
            }
    }

    data class DemuxedCue(
        val startTimeMs: Long,
        val durationMs: Long,
        val rawText: String
    )

    fun isMatroskaFile(fd: FileDescriptor): Boolean {
        return try {
            val fis = FileInputStream(fd)
            val channel = fis.channel
            channel.position(0)
            val buffer = ByteBuffer.allocate(4)
            val bytesRead = channel.read(buffer)
            channel.position(0)
            if (bytesRead == 4) {
                buffer.flip()
                val magic = buffer.int.toLong() and 0xFFFFFFFFL
                magic == ID_EBML
            } else {
                false
            }
        } catch (e: Exception) {
            false
        }
    }

    suspend fun inspectMatroska(fd: FileDescriptor): MatroskaHeaderInfo = withContext(Dispatchers.IO) {
        val tracks = mutableListOf<SubtitleStreamInfo>()
        var fps: Float? = null
        try {
            val parseResult = parseTracks(fd)
            fps = parseResult.videoFps
            val internalTracks = parseResult.tracks
            val subTracks = internalTracks.filter { it.isSubtitle }
            val nameCounts = subTracks.groupBy { it.name.trim().lowercase() }

            var subIndex = 0
            for (track in subTracks) {
                val hasDuplicateName = track.name.isNotBlank() && (nameCounts[track.name.trim().lowercase()]?.size ?: 0) > 1
                val lang = if (track.language.isNotBlank() && track.language != "und") track.language else "eng"

                val scriptTitle = track.codecPrivate?.let {
                    try {
                        val header = String(it, Charsets.UTF_8)
                        val match = Regex("""(?m)^Title:\s*(.+)$""").find(header)
                        match?.groupValues?.getOrNull(1)?.trim()
                    } catch (_: Exception) { null }
                }

                val baseTitle = when {
                    track.name.isNotBlank() -> track.name
                    !scriptTitle.isNullOrBlank() -> scriptTitle
                    lang.isNotBlank() && lang != "und" -> "${lang.uppercase(Locale.US)} Subtitle"
                    else -> "Subtitle Track #${subIndex + 1}"
                }

                val displayTitle = if (hasDuplicateName) {
                    "$baseTitle [Track ${subIndex + 1}]"
                } else {
                    baseTitle
                }

                tracks.add(
                    SubtitleStreamInfo(
                        index = subIndex,
                        trackId = track.trackNumber.toInt(),
                        title = displayTitle,
                        language = lang,
                        codecName = track.codecName,
                        extension = track.extension
                    )
                )
                subIndex++
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error inspecting Matroska tracks", e)
        }
        MatroskaHeaderInfo(videoFps = fps, subtitleTracks = tracks)
    }

    suspend fun inspectMatroskaTracks(fd: FileDescriptor): List<SubtitleStreamInfo> {
        return inspectMatroska(fd).subtitleTracks
    }

    suspend fun demuxTrackCues(
        fd: FileDescriptor,
        targetTrack: SubtitleStreamInfo,
        onProgress: ((Float) -> Unit)? = null
    ): Pair<List<DemuxedCue>, String?> = withContext(Dispatchers.IO) {
        val fis = FileInputStream(fd)
        val channel = fis.channel
        channel.position(0)

        val buffer = ByteBuffer.allocate(128 * 1024)
        buffer.flip() // Initially empty

        val internalTracks = parseTracks(fd).tracks
        val subtitleTracks = internalTracks.filter { it.isSubtitle }

        val matchedTrack = subtitleTracks.find {
            it.trackNumber == targetTrack.trackId.toLong()
        } ?: subtitleTracks.getOrNull(targetTrack.index)
        ?: subtitleTracks.find {
            it.name.isNotBlank() && it.name.equals(targetTrack.title, ignoreCase = true)
        }
        ?: throw java.io.IOException("Subtitle track '${targetTrack.title}' not found in container")

        val scriptHeader = matchedTrack.codecPrivate?.let {
            try { String(it, Charsets.UTF_8) } catch (_: Exception) { null }
        }

        channel.position(0)
        buffer.clear()
        buffer.flip()

        var timecodeScale = 1_000_000L // default: 1ms per unit
        val cues = mutableListOf<DemuxedCue>()
        val fileSize = channel.size()

        // Scan down to Segment
        while (coroutineContext.isActive) {
            val id = readElementId(channel, buffer)
            if (id == -1L) break
            val size = readElementSize(channel, buffer)

            if (id == ID_SEGMENT) {
                break
            } else {
                if (size > 0) skipBytes(channel, buffer, size)
            }
        }

        var currentClusterTimecode = 0L

        // Scan elements inside Segment
        while (coroutineContext.isActive) {
            val id = readElementId(channel, buffer)
            if (id == -1L) break
            val size = readElementSize(channel, buffer)

            when (id) {
                ID_INFO -> {
                    val infoEnd = if (size > 0) currentFileOffset(channel, buffer) + size else Long.MAX_VALUE
                    while (coroutineContext.isActive && currentFileOffset(channel, buffer) < infoEnd) {
                        val infoId = readElementId(channel, buffer)
                        if (infoId == -1L) break
                        val infoSize = readElementSize(channel, buffer)
                        if (infoId == ID_TIMECODE_SCALE && infoSize in 1..8) {
                            timecodeScale = readUInt(channel, buffer, infoSize.toInt())
                        } else if (infoSize > 0) {
                            skipBytes(channel, buffer, infoSize)
                        }
                    }
                }
                ID_CLUSTER -> {
                    val clusterEnd = if (size > 0) currentFileOffset(channel, buffer) + size else Long.MAX_VALUE
                    while (coroutineContext.isActive && currentFileOffset(channel, buffer) < clusterEnd) {
                        val clusterId = readElementId(channel, buffer)
                        if (clusterId == -1L) break
                        val clusterElemSize = readElementSize(channel, buffer)

                        when (clusterId) {
                            ID_CLUSTER_TIMECODE -> {
                                currentClusterTimecode = readUInt(channel, buffer, clusterElemSize.toInt())
                            }
                            ID_SIMPLE_BLOCK -> {
                                val cue = parseBlockData(
                                    channel = channel,
                                    buffer = buffer,
                                    blockSize = clusterElemSize,
                                    targetTrackNumber = matchedTrack.trackNumber,
                                    clusterTimecode = currentClusterTimecode,
                                    timecodeScale = timecodeScale,
                                    isCompressed = matchedTrack.isCompressed
                                )
                                if (cue != null) {
                                    cues.add(cue)
                                }
                            }
                            ID_BLOCK_GROUP -> {
                                val groupEnd = if (clusterElemSize > 0) currentFileOffset(channel, buffer) + clusterElemSize else Long.MAX_VALUE
                                var blockDuration = -1L
                                var blockCue: DemuxedCue? = null

                                while (coroutineContext.isActive && currentFileOffset(channel, buffer) < groupEnd) {
                                    val groupId = readElementId(channel, buffer)
                                    if (groupId == -1L) break
                                    val groupElemSize = readElementSize(channel, buffer)

                                    when (groupId) {
                                        ID_BLOCK -> {
                                            blockCue = parseBlockData(
                                                channel = channel,
                                                buffer = buffer,
                                                blockSize = groupElemSize,
                                                targetTrackNumber = matchedTrack.trackNumber,
                                                clusterTimecode = currentClusterTimecode,
                                                timecodeScale = timecodeScale,
                                                isCompressed = matchedTrack.isCompressed
                                            )
                                        }
                                        ID_BLOCK_DURATION -> {
                                            blockDuration = readUInt(channel, buffer, groupElemSize.toInt())
                                        }
                                        else -> {
                                            if (groupElemSize > 0) skipBytes(channel, buffer, groupElemSize)
                                        }
                                    }
                                }

                                if (blockCue != null) {
                                    val adjustedDuration = if (blockDuration > 0) {
                                        (blockDuration * timecodeScale) / 1_000_000L
                                    } else {
                                        blockCue.durationMs
                                    }
                                    cues.add(blockCue.copy(durationMs = adjustedDuration))
                                }
                            }
                            else -> {
                                if (clusterElemSize > 0) skipBytes(channel, buffer, clusterElemSize)
                            }
                        }
                    }

                    if (fileSize > 0) {
                        val progress = (currentFileOffset(channel, buffer).toFloat() / fileSize).coerceIn(0f, 1f)
                        onProgress?.invoke(progress)
                    }
                }
                else -> {
                    if (size > 0) skipBytes(channel, buffer, size)
                }
            }
        }

        cues.sortBy { it.startTimeMs }
        Pair(cues, scriptHeader)
    }

    private fun parseBlockData(
        channel: FileChannel,
        buffer: ByteBuffer,
        blockSize: Long,
        targetTrackNumber: Long,
        clusterTimecode: Long,
        timecodeScale: Long,
        isCompressed: Boolean
    ): DemuxedCue? {
        if (blockSize < 4) {
            skipBytes(channel, buffer, blockSize)
            return null
        }

        val startOffset = currentFileOffset(channel, buffer)
        val trackNum = readVint(channel, buffer)
        val readHeaderBytes = currentFileOffset(channel, buffer) - startOffset

        val relTimecode = readShort(channel, buffer)
        val flags = readByte(channel, buffer)

        val payloadSize = (blockSize - readHeaderBytes - 3).toInt()
        if (payloadSize <= 0) {
            return null
        }

        if (trackNum != targetTrackNumber) {
            skipBytes(channel, buffer, payloadSize.toLong())
            return null
        }

        val payloadBytes = ByteArray(payloadSize)
        readFully(channel, buffer, payloadBytes)

        val decompressedBytes = if (isCompressed || isZlibCompressed(payloadBytes)) {
            decompressZlib(payloadBytes)
        } else {
            payloadBytes
        }

        val text = String(decompressedBytes, Charsets.UTF_8).trim().trimEnd('\u0000')
        if (text.isBlank()) return null

        val startTimeMs = ((clusterTimecode + relTimecode) * timecodeScale) / 1_000_000L
        return DemuxedCue(startTimeMs = maxOf(0L, startTimeMs), durationMs = -1L, rawText = text)
    }

    private fun parseTracks(fd: FileDescriptor): MatroskaParseResult {
        val fis = FileInputStream(fd)
        val channel = fis.channel
        channel.position(0)

        val buffer = ByteBuffer.allocate(64 * 1024)
        buffer.flip()

        val tracks = mutableListOf<MatroskaTrack>()
        var detectedFps: Float? = null

        while (true) {
            val id = readElementId(channel, buffer)
            if (id == -1L) break
            val size = readElementSize(channel, buffer)

            if (id == ID_SEGMENT) {
                val segEnd = if (size > 0) currentFileOffset(channel, buffer) + size else Long.MAX_VALUE
                while (currentFileOffset(channel, buffer) < segEnd) {
                    val childId = readElementId(channel, buffer)
                    if (childId == -1L) break
                    val childSize = readElementSize(channel, buffer)

                    if (childId == ID_TRACKS) {
                        val tracksEnd = currentFileOffset(channel, buffer) + childSize
                        while (currentFileOffset(channel, buffer) < tracksEnd) {
                            val trackEntryId = readElementId(channel, buffer)
                            if (trackEntryId == -1L) break
                            val trackEntrySize = readElementSize(channel, buffer)

                            if (trackEntryId == ID_TRACK_ENTRY) {
                                val entryEnd = currentFileOffset(channel, buffer) + trackEntrySize
                                var trackNum = 0L
                                var trackUid = 0L
                                var trackType = 0L
                                var defaultDurationNs = 0L
                                var name = ""
                                var codecId = ""
                                var lang = "eng"
                                var codecPrivate: ByteArray? = null
                                var isCompressed = false
                                var trackFps: Float? = null

                                while (currentFileOffset(channel, buffer) < entryEnd) {
                                    val fieldId = readElementId(channel, buffer)
                                    if (fieldId == -1L) break
                                    val fieldSize = readElementSize(channel, buffer)

                                    when (fieldId) {
                                        ID_TRACK_NUMBER -> trackNum = readUInt(channel, buffer, fieldSize.toInt())
                                        ID_TRACK_UID -> trackUid = readUInt(channel, buffer, fieldSize.toInt())
                                        ID_TRACK_TYPE -> trackType = readUInt(channel, buffer, fieldSize.toInt())
                                        ID_DEFAULT_DURATION -> defaultDurationNs = readUInt(channel, buffer, fieldSize.toInt())
                                        ID_NAME -> name = readString(channel, buffer, fieldSize.toInt())
                                        ID_CODEC_ID -> codecId = readString(channel, buffer, fieldSize.toInt())
                                        ID_LANGUAGE -> lang = readString(channel, buffer, fieldSize.toInt())
                                        ID_LANGUAGE_IETF -> lang = readString(channel, buffer, fieldSize.toInt())
                                        ID_VIDEO -> {
                                            val videoEnd = currentFileOffset(channel, buffer) + fieldSize
                                            while (currentFileOffset(channel, buffer) < videoEnd) {
                                                val vFieldId = readElementId(channel, buffer)
                                                if (vFieldId == -1L) break
                                                val vFieldSize = readElementSize(channel, buffer)
                                                when (vFieldId) {
                                                    ID_FRAME_RATE -> {
                                                        val fr = readFloat(channel, buffer, vFieldSize.toInt())
                                                        if (fr in 1f..360f) {
                                                            trackFps = fr
                                                        }
                                                    }
                                                    else -> if (vFieldSize > 0) skipBytes(channel, buffer, vFieldSize)
                                                }
                                            }
                                        }
                                        ID_CODEC_PRIVATE -> {
                                            if (fieldSize in 1..512 * 1024) {
                                                val cpBytes = ByteArray(fieldSize.toInt())
                                                readFully(channel, buffer, cpBytes)
                                                codecPrivate = cpBytes
                                            } else {
                                                skipBytes(channel, buffer, fieldSize)
                                            }
                                        }
                                        ID_CONTENT_ENCODINGS -> {
                                            val encEnd = currentFileOffset(channel, buffer) + fieldSize
                                            while (currentFileOffset(channel, buffer) < encEnd) {
                                                val encId = readElementId(channel, buffer)
                                                if (encId == -1L) break
                                                val encSize = readElementSize(channel, buffer)
                                                if (encId == ID_CONTENT_COMP_ALGO) {
                                                    val algo = readUInt(channel, buffer, encSize.toInt())
                                                    if (algo == 0L) isCompressed = true
                                                } else if (encSize > 0) {
                                                    skipBytes(channel, buffer, encSize)
                                                }
                                            }
                                        }
                                        else -> if (fieldSize > 0) skipBytes(channel, buffer, fieldSize)
                                    }
                                }

                                if (trackType == 1L && detectedFps == null) {
                                    if (defaultDurationNs > 0) {
                                        val calculated = 1_000_000_000.0 / defaultDurationNs.toDouble()
                                        if (calculated in 1.0..360.0) {
                                            detectedFps = calculated.toFloat()
                                        }
                                    }
                                    if (detectedFps == null && trackFps != null) {
                                        detectedFps = trackFps
                                    }
                                }

                                if (trackNum > 0 && trackType == 17L) {
                                    tracks.add(
                                        MatroskaTrack(
                                            trackNumber = trackNum,
                                            trackUid = trackUid,
                                            trackType = trackType,
                                            codecId = codecId,
                                            name = name,
                                            language = lang,
                                            codecPrivate = codecPrivate,
                                            isCompressed = isCompressed
                                        )
                                    )
                                }
                            } else {
                                if (trackEntrySize > 0) skipBytes(channel, buffer, trackEntrySize)
                            }
                        }
                        return MatroskaParseResult(videoFps = detectedFps, tracks = tracks)
                    } else {
                        if (childSize > 0) skipBytes(channel, buffer, childSize)
                    }
                }
                break
            } else {
                if (size > 0) skipBytes(channel, buffer, size)
            }
        }
        return MatroskaParseResult(videoFps = detectedFps, tracks = tracks)
    }

    private fun isZlibCompressed(data: ByteArray): Boolean {
        if (data.size < 2) return false
        val b0 = data[0].toInt() and 0xFF
        val b1 = data[1].toInt() and 0xFF
        return b0 == 0x78 && (b1 == 0x01 || b1 == 0x5E || b1 == 0x9C || b1 == 0xDA)
    }

    private fun decompressZlib(data: ByteArray): ByteArray {
        return try {
            val inflater = Inflater()
            inflater.setInput(data)
            val outputStream = ByteArrayOutputStream(data.size * 3)
            val buf = ByteArray(2048)
            while (!inflater.finished()) {
                val count = inflater.inflate(buf)
                if (count <= 0) break
                outputStream.write(buf, 0, count)
            }
            inflater.end()
            outputStream.toByteArray()
        } catch (e: Exception) {
            data
        }
    }

    private fun readElementId(channel: FileChannel, buffer: ByteBuffer): Long {
        if (!ensureBytes(channel, buffer, 1)) return -1L
        val b0 = buffer.get().toInt() and 0xFF
        var numBytes = 1
        while (numBytes <= 4) {
            if ((b0 and (0x80 shr (numBytes - 1))) != 0) break
            numBytes++
        }
        if (numBytes > 4) return -1L

        var id = b0.toLong()
        if (numBytes > 1) {
            if (!ensureBytes(channel, buffer, numBytes - 1)) return -1L
            for (i in 1 until numBytes) {
                id = (id shl 8) or (buffer.get().toLong() and 0xFF)
            }
        }
        return id
    }

    private fun readElementSize(channel: FileChannel, buffer: ByteBuffer): Long {
        if (!ensureBytes(channel, buffer, 1)) return -1L
        val b0 = buffer.get().toInt() and 0xFF
        var numBytes = 1
        while (numBytes <= 8) {
            if ((b0 and (0x80 shr (numBytes - 1))) != 0) break
            numBytes++
        }
        if (numBytes > 8) return -1L

        val mask = 0xFF shr numBytes
        var size = (b0 and mask).toLong()
        var isUnknown = (b0 and mask) == mask

        if (numBytes > 1) {
            if (!ensureBytes(channel, buffer, numBytes - 1)) return -1L
            for (i in 1 until numBytes) {
                val b = buffer.get().toInt() and 0xFF
                if (b != 0xFF) isUnknown = false
                size = (size shl 8) or b.toLong()
            }
        }
        return if (isUnknown) -1L else size
    }

    private fun readVint(channel: FileChannel, buffer: ByteBuffer): Long {
        if (!ensureBytes(channel, buffer, 1)) return -1L
        val b0 = buffer.get().toInt() and 0xFF
        var numBytes = 1
        while (numBytes <= 8) {
            if ((b0 and (0x80 shr (numBytes - 1))) != 0) break
            numBytes++
        }
        if (numBytes > 8) return -1L

        val mask = 0xFF shr numBytes
        var value = (b0 and mask).toLong()
        if (numBytes > 1) {
            if (!ensureBytes(channel, buffer, numBytes - 1)) return -1L
            for (i in 1 until numBytes) {
                value = (value shl 8) or (buffer.get().toLong() and 0xFF)
            }
        }
        return value
    }

    private fun readUInt(channel: FileChannel, buffer: ByteBuffer, size: Int): Long {
        if (size <= 0 || size > 8) return 0L
        if (!ensureBytes(channel, buffer, size)) return 0L
        var value = 0L
        for (i in 0 until size) {
            value = (value shl 8) or (buffer.get().toLong() and 0xFF)
        }
        return value
    }

    private fun readFloat(channel: FileChannel, buffer: ByteBuffer, size: Int): Float {
        return try {
            when (size) {
                4 -> {
                    if (!ensureBytes(channel, buffer, 4)) return 0f
                    buffer.float
                }
                8 -> {
                    if (!ensureBytes(channel, buffer, 8)) return 0f
                    buffer.double.toFloat()
                }
                else -> {
                    if (size > 0) skipBytes(channel, buffer, size.toLong())
                    0f
                }
            }
        } catch (_: Exception) {
            0f
        }
    }

    private fun readShort(channel: FileChannel, buffer: ByteBuffer): Short {
        if (!ensureBytes(channel, buffer, 2)) return 0
        return buffer.short
    }

    private fun readByte(channel: FileChannel, buffer: ByteBuffer): Byte {
        if (!ensureBytes(channel, buffer, 1)) return 0
        return buffer.get()
    }

    private fun readString(channel: FileChannel, buffer: ByteBuffer, length: Int): String {
        if (length <= 0) return ""
        val bytes = ByteArray(length)
        readFully(channel, buffer, bytes)
        return String(bytes, Charsets.UTF_8).trim().trimEnd('\u0000')
    }

    private fun readFully(channel: FileChannel, buffer: ByteBuffer, destination: ByteArray) {
        var offset = 0
        var remaining = destination.size
        while (remaining > 0) {
            if (buffer.hasRemaining()) {
                val toRead = minOf(remaining, buffer.remaining())
                buffer.get(destination, offset, toRead)
                offset += toRead
                remaining -= toRead
            } else {
                if (!ensureBytes(channel, buffer, minOf(remaining, buffer.capacity()))) {
                    break
                }
            }
        }
    }

    private fun skipBytes(channel: FileChannel, buffer: ByteBuffer, count: Long) {
        if (count <= 0) return
        val inBuf = buffer.remaining()
        if (count <= inBuf) {
            buffer.position(buffer.position() + count.toInt())
        } else {
            val remainingToSkip = count - inBuf
            buffer.position(buffer.limit()) // clear remaining
            channel.position(channel.position() + remainingToSkip)
        }
    }

    private fun ensureBytes(channel: FileChannel, buffer: ByteBuffer, count: Int): Boolean {
        if (buffer.remaining() >= count) return true
        buffer.compact()
        while (buffer.position() < count) {
            val read = channel.read(buffer)
            if (read <= 0) break
        }
        buffer.flip()
        return buffer.remaining() >= count
    }

    private fun currentFileOffset(channel: FileChannel, buffer: ByteBuffer): Long {
        return channel.position() - buffer.remaining()
    }
}
