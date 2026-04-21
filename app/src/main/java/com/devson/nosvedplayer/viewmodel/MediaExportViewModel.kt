package com.devson.nosvedplayer.viewmodel

import android.content.Context
import android.net.Uri
import android.os.Environment
import androidx.annotation.OptIn
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.Crop
import androidx.media3.effect.ScaleAndRotateTransformation
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.Effects
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

enum class AspectRatio(val label: String, val w: Float, val h: Float) {
    RATIO_1_1("1:1", 1f, 1f),
    RATIO_16_9("16:9", 16f, 9f),
    RATIO_9_16("9:16", 9f, 16f)
}

sealed class ExportOperation {
    data class Rotate(val degrees: Float) : ExportOperation()
    data class CropVideo(val ratio: AspectRatio) : ExportOperation()
    object ExtractAudio : ExportOperation()
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

    fun startExport(context: Context, inputUri: Uri, operation: ExportOperation) {
        if (_state.value is ExportUiState.Processing) return
        _state.value = ExportUiState.Processing(0f)

        viewModelScope.launch(Dispatchers.Main) {
            val outputFile = buildOutputFile(context, operation)

            val editedMediaItem = buildEditedMediaItem(inputUri, operation)

            val listener = object : Transformer.Listener {
                override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                    pollJob?.cancel()
                    _state.value = ExportUiState.Success(outputFile.absolutePath)
                }

                override fun onError(
                    composition: Composition,
                    exportResult: ExportResult,
                    exportException: ExportException
                ) {
                    pollJob?.cancel()
                    _state.value = ExportUiState.Error(exportException.message ?: "Export failed")
                }
            }

            val transformer = Transformer.Builder(context)
                .addListener(listener)
                .apply {
                    if (operation is ExportOperation.ExtractAudio) {
                        setAudioMimeType(MimeTypes.AUDIO_AAC)
                        setVideoMimeType(MimeTypes.VIDEO_H264)
                    } else {
                        setVideoMimeType(MimeTypes.VIDEO_H264)
                    }
                }
                .build()

            activeTransformer = transformer
            transformer.start(editedMediaItem, outputFile.absolutePath)

            pollJob = launch {
                val holder = ProgressHolder()
                while (true) {
                    delay(250)
                    val state = transformer.getProgress(holder)
                    if (state == Transformer.PROGRESS_STATE_AVAILABLE) {
                        _state.value = ExportUiState.Processing(holder.progress / 100f)
                    }
                }
            }
        }
    }

    fun cancelExport() {
        pollJob?.cancel()
        activeTransformer?.cancel()
        activeTransformer = null
        _state.value = ExportUiState.Idle
    }

    fun resetState() {
        _state.value = ExportUiState.Idle
    }

    override fun onCleared() {
        cancelExport()
        super.onCleared()
    }

    @OptIn(UnstableApi::class)
    private fun buildEditedMediaItem(uri: Uri, operation: ExportOperation): EditedMediaItem {
        val mediaItem = MediaItem.fromUri(uri)
        return when (operation) {
            is ExportOperation.Rotate -> {
                val effect = ScaleAndRotateTransformation.Builder()
                    .setRotationDegrees(operation.degrees)
                    .build()
                EditedMediaItem.Builder(mediaItem)
                    .setEffects(Effects(emptyList(), listOf(effect)))
                    .build()
            }

            is ExportOperation.CropVideo -> {
                val r = operation.ratio
                val cropEffect = buildCropEffect(r)
                EditedMediaItem.Builder(mediaItem)
                    .setEffects(Effects(emptyList(), listOf(cropEffect)))
                    .build()
            }

            is ExportOperation.ExtractAudio -> {
                EditedMediaItem.Builder(mediaItem)
                    .setRemoveVideo(true)
                    .build()
            }
        }
    }

    @OptIn(UnstableApi::class)
    private fun buildCropEffect(ratio: AspectRatio): Crop {
        return when (ratio) {
            AspectRatio.RATIO_1_1 -> Crop(-1f, 1f, -1f, 1f)
            AspectRatio.RATIO_16_9 -> {
                val halfH = 9f / 16f
                Crop(-1f, 1f, -halfH, halfH)
            }
            AspectRatio.RATIO_9_16 -> {
                val halfW = 9f / 16f
                Crop(-halfW, halfW, -1f, 1f)
            }
        }
    }

    private fun buildOutputFile(context: Context, operation: ExportOperation): File {
        val ts = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val dir = context.getExternalFilesDir(Environment.DIRECTORY_MOVIES)
            ?: context.filesDir
        return when (operation) {
            is ExportOperation.Rotate -> File(dir, "rotated_$ts.mp4")
            is ExportOperation.CropVideo -> File(dir, "cropped_${operation.ratio.label.replace(":", "x")}_$ts.mp4")
            is ExportOperation.ExtractAudio -> File(dir, "audio_$ts.m4a")
        }
    }
}
