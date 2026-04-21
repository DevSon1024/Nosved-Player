package com.devson.nosvedplayer.viewmodel

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.os.ResultReceiver
import android.provider.DocumentsContract
import androidx.annotation.OptIn
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import com.devson.nosvedplayer.service.MediaConverterService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.nio.ByteBuffer
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer

data class AudioTrackInfo(val index: Int, val language: String, val mime: String)

enum class ExportAudioFormat(val ext: String, val mime: String) {
    M4A(".m4a", MimeTypes.AUDIO_AAC)
}

sealed class ExportOperation {
    data class ExtractAudio(val format: ExportAudioFormat, val startMs: Long, val endMs: Long, val tracks: List<AudioTrackInfo>) : ExportOperation()
}

sealed class ExportUiState {
    object Idle : ExportUiState()
    data class Processing(val progress: Float) : ExportUiState()
    data class Success(val outputPath: String) : ExportUiState()
    data class Error(val msg: String) : ExportUiState()
}

@OptIn(UnstableApi::class)
class MediaExportViewModel : ViewModel() {

    private val _state = MutableStateFlow<ExportUiState>(ExportUiState.Idle)
    val state: StateFlow<ExportUiState> = _state

    private var pollJob: Job? = null

    fun startExport(context: Context, inputUri: Uri, operation: ExportOperation.ExtractAudio, outputFolderUri: Uri?) {
        if (_state.value is ExportUiState.Processing) return
        _state.value = ExportUiState.Processing(0f)

        pollJob = viewModelScope.launch(Dispatchers.IO) {
            var originalName = "audio"
            try {
                context.contentResolver.query(inputUri, null, null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                        if (nameIndex != -1) cursor.getString(nameIndex)?.let { originalName = it.substringBeforeLast(".") }
                    }
                }
            } catch (e: Exception) {}
            if (originalName == "audio") originalName = inputUri.lastPathSegment?.substringBeforeLast(".") ?: "audio"
            originalName = originalName.replace(Regex("[^a-zA-Z0-9_\\\\-\\\\s]"), "").trim().replace(" ", "_").ifEmpty { "audio" }

            try {
                for ((i, track) in operation.tracks.withIndex()) {
                    val progressOffset = i.toFloat() / operation.tracks.size
                    val trackFraction = 1f / operation.tracks.size

                    val fileNamePrefix = "$originalName-${System.currentTimeMillis()}-${track.language}"
                    val tempExtractedFile = File(context.cacheDir, "$fileNamePrefix.m4a")

                    val extractor = MediaExtractor()
                    extractor.setDataSource(context, inputUri, null)
                    extractor.selectTrack(track.index)
                    extractor.seekTo(operation.startMs * 1000, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)

                    val format = extractor.getTrackFormat(track.index)
                    val muxer = MediaMuxer(tempExtractedFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
                    val outTrack = muxer.addTrack(format)
                    muxer.start()

                    val buffer = ByteBuffer.allocate(1024 * 1024)
                    val bufferInfo = MediaCodec.BufferInfo()

                    var framesWritten = 0
                    var firstSampleTimeUs = -1L

                    while (true) {
                        val size = extractor.readSampleData(buffer, 0)
                        if (size < 0) break
                        val timeUs = extractor.sampleTime
                        if (timeUs > operation.endMs * 1000) break

                        if (timeUs < operation.startMs * 1000) {
                            extractor.advance()
                            continue
                        }

                        if (firstSampleTimeUs == -1L) firstSampleTimeUs = timeUs

                        bufferInfo.offset = 0
                        bufferInfo.size = size
                        bufferInfo.presentationTimeUs = timeUs - firstSampleTimeUs
                        bufferInfo.flags = extractor.sampleFlags

                        muxer.writeSampleData(outTrack, buffer, bufferInfo)
                        framesWritten++
                        extractor.advance()

                        if (timeUs % 500_000 < 50000) {
                            val currentMs = (timeUs / 1000).coerceAtLeast(operation.startMs)
                            val p = (currentMs - operation.startMs).toFloat() / (operation.endMs - operation.startMs).coerceAtLeast(1)
                            _state.value = ExportUiState.Processing(progressOffset + p * trackFraction)
                        }
                    }
                    if (framesWritten > 0) {
                        muxer.stop()
                    }
                    muxer.release()
                    extractor.release()

                    if (framesWritten == 0) {
                        tempExtractedFile.delete()
                        throw Exception("No valid audio frames found in the selected range")
                    }

                    saveOutput(context, tempExtractedFile, outputFolderUri, operation.format, fileNamePrefix)
                }
                _state.value = ExportUiState.Success("Export fully completed")
            } catch (e: Exception) {
                _state.value = ExportUiState.Error(e.message ?: "Export failed")
            }
        }
    }

    /**
     * Offloads FFmpeg work to the isolated :converter process.
     * inputUri is passed as Intent data with FLAG_GRANT_READ_URI_PERMISSION so
     * the remote process can open Scoped Storage URIs.
     */
    fun startFfmpegConvert(
        context: Context,
        inputUri: Uri,
        ffmpegArgs: ArrayList<String>
    ) {
        if (_state.value is ExportUiState.Processing) return
        _state.value = ExportUiState.Processing(0f)

        val receiver = object : ResultReceiver(Handler(Looper.getMainLooper())) {
            override fun onReceiveResult(resultCode: Int, resultData: Bundle?) {
                when (resultCode) {
                    MediaConverterService.RESULT_PROGRESS -> {
                        val pct = resultData?.getFloat(MediaConverterService.KEY_PROGRESS, 0f) ?: 0f
                        _state.value = ExportUiState.Processing(pct)
                    }
                    MediaConverterService.RESULT_SUCCESS -> {
                        val path = resultData?.getString(MediaConverterService.KEY_PATH) ?: ""
                        _state.value = ExportUiState.Success(path)
                    }
                    MediaConverterService.RESULT_ERROR -> {
                        val msg = resultData?.getString(MediaConverterService.KEY_MSG) ?: "FFmpeg error"
                        _state.value = ExportUiState.Error(msg)
                    }
                }
            }
        }

        val intent = Intent(context, MediaConverterService::class.java).apply {
            action = MediaConverterService.ACTION_CONVERT
            data = inputUri
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            putExtra(MediaConverterService.EXTRA_RECEIVER, receiver)
            putStringArrayListExtra(MediaConverterService.EXTRA_CMD_ARGS, ffmpegArgs)
        }
        context.startForegroundService(intent)
    }

    private fun saveOutput(context: Context, tempFile: File, outputFolderUri: Uri?, format: ExportAudioFormat, fileNamePrefix: String): String {
        val fileName = "$fileNamePrefix${format.ext}"
        if (outputFolderUri != null) {
            val docUri = DocumentsContract.buildDocumentUriUsingTree(
                outputFolderUri,
                DocumentsContract.getTreeDocumentId(outputFolderUri)
            )
            val newUri = DocumentsContract.createDocument(context.contentResolver, docUri, format.mime, fileName)
                ?: throw Exception("Cannot create file in tree")
            context.contentResolver.openOutputStream(newUri)?.use { out ->
                tempFile.inputStream().use { input -> input.copyTo(out) }
            }
            tempFile.delete()
            return newUri.toString()
        } else {
            val dDir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES), "NosvedPlayer/Music")
            if (!dDir.exists()) dDir.mkdirs()
            val dest = File(dDir, fileName)
            tempFile.copyTo(dest, overwrite = true)
            tempFile.delete()
            return dest.absolutePath
        }
    }

    fun cancelExport() {
        pollJob?.cancel()
        _state.value = ExportUiState.Idle
    }

    fun resetState() { _state.value = ExportUiState.Idle }

    override fun onCleared() {
        cancelExport()
        super.onCleared()
    }
}
