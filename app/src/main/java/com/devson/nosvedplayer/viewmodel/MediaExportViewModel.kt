package com.devson.nosvedplayer.viewmodel

import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.DocumentsContract
import androidx.annotation.OptIn
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.ProgressHolder
import androidx.media3.transformer.Transformer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class ExportAudioFormat(val ext: String, val mime: String) {
    M4A(".m4a", MimeTypes.AUDIO_AAC),
    MP3(".mp3", MimeTypes.AUDIO_MPEG)
}

sealed class ExportOperation {
    data class ExtractAudio(val format: ExportAudioFormat, val startMs: Long, val endMs: Long) : ExportOperation()
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

    private var activeTransformer: Transformer? = null
    private var pollJob: Job? = null

    fun startExport(context: Context, inputUri: Uri, operation: ExportOperation.ExtractAudio, outputFolderUri: Uri?) {
        if (_state.value is ExportUiState.Processing) return
        _state.value = ExportUiState.Processing(0f)

        viewModelScope.launch(Dispatchers.Main) {
            var originalName = "audio"
            try {
                context.contentResolver.query(inputUri, null, null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                        if (nameIndex != -1) cursor.getString(nameIndex)?.let { originalName = it.substringBeforeLast(".") }
                    }
                }
            } catch (e: Exception) { e.printStackTrace() }
            if (originalName == "audio") originalName = inputUri.lastPathSegment?.substringBeforeLast(".") ?: "audio"
            originalName = originalName.replace(Regex("[^a-zA-Z0-9_\\-\\s]"), "").trim().replace(" ", "_")
            if (originalName.isEmpty()) originalName = "audio"

            val fileNamePrefix = "$originalName-${System.currentTimeMillis()}"
            val tempFile = File(context.cacheDir, "$fileNamePrefix${operation.format.ext}")
            val mediaItem = MediaItem.Builder().setUri(inputUri)
                .setClippingConfiguration(
                    MediaItem.ClippingConfiguration.Builder()
                        .setStartPositionMs(operation.startMs)
                        .setEndPositionMs(operation.endMs)
                        .build()
                ).build()

            val editedMediaItem = EditedMediaItem.Builder(mediaItem)
                .setRemoveVideo(true)
                .build()

            val listener = object : Transformer.Listener {
                override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                    pollJob?.cancel()
                    viewModelScope.launch(Dispatchers.IO) {
                        try {
                            val finalPath = saveOutput(context, tempFile, outputFolderUri, operation.format, fileNamePrefix)
                            _state.value = ExportUiState.Success(finalPath)
                        } catch (e: Exception) {
                            _state.value = ExportUiState.Error("Copy failed: ${e.message}")
                        }
                    }
                }

                override fun onError(composition: Composition, exportResult: ExportResult, exportException: ExportException) {
                    pollJob?.cancel()
                    tempFile.delete()
                    _state.value = ExportUiState.Error(exportException.message ?: "Export failed")
                }
            }

            val transformer = Transformer.Builder(context)
                .addListener(listener)
                .setAudioMimeType(MimeTypes.AUDIO_AAC)
                .build()

            activeTransformer = transformer
            transformer.start(editedMediaItem, tempFile.absolutePath)

            pollJob = launch {
                val holder = ProgressHolder()
                while (true) {
                    delay(250)
                    if (transformer.getProgress(holder) == Transformer.PROGRESS_STATE_AVAILABLE) {
                        _state.value = ExportUiState.Processing(holder.progress / 100f)
                    }
                }
            }
        }
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
        activeTransformer?.cancel()
        activeTransformer = null
        _state.value = ExportUiState.Idle
    }

    fun resetState() { _state.value = ExportUiState.Idle }

    override fun onCleared() {
        cancelExport()
        super.onCleared()
    }
}
