package com.devson.nvplayer.service

import android.app.*
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.IBinder
import android.provider.MediaStore
import androidx.core.app.NotificationCompat
import com.devson.nvplayer.viewmodel.ExtractionUiState
import com.devson.nvplayer.viewmodel.FrameFormat
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.io.File
import java.io.FileOutputStream

import android.provider.OpenableColumns

class FrameExtractionService : Service() {

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var extractionJob: Job? = null

    companion object {
        const val CHANNEL_ID = "frame_extraction_channel"
        const val NOTIFICATION_ID = 2001

        const val ACTION_START_EXTRACTION = "com.devson.nvplayer.action.START_EXTRACTION"
        const val ACTION_CANCEL_EXTRACTION = "com.devson.nvplayer.action.CANCEL_EXTRACTION"

        const val EXTRA_VIDEO_URI = "extra_video_uri"
        const val EXTRA_VIDEO_NAME = "extra_video_name"
        const val EXTRA_START_TIME_MS = "extra_start_time_ms"
        const val EXTRA_END_TIME_MS = "extra_end_time_ms"
        const val EXTRA_INTERVAL_MS = "extra_interval_ms"
        const val EXTRA_FORMAT = "extra_format"
        const val EXTRA_QUALITY = "extra_quality"

        private val _uiState = MutableStateFlow(ExtractionUiState())
        val uiState: StateFlow<ExtractionUiState> = _uiState.asStateFlow()

        fun resolveVideoDisplayName(context: Context, uri: Uri): String {
            var displayName: String? = null

            // 1. Query OpenableColumns / MediaColumns DISPLAY_NAME & TITLE
            try {
                if (uri.scheme == "content") {
                    context.contentResolver.query(
                        uri,
                        arrayOf(
                            OpenableColumns.DISPLAY_NAME,
                            MediaStore.MediaColumns.DISPLAY_NAME,
                            MediaStore.Video.Media.TITLE
                        ),
                        null,
                        null,
                        null
                    )?.use { cursor ->
                        if (cursor.moveToFirst()) {
                            var idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                            if (idx != -1) {
                                displayName = cursor.getString(idx)
                            }
                            if (displayName.isNullOrBlank()) {
                                idx = cursor.getColumnIndex(MediaStore.MediaColumns.DISPLAY_NAME)
                                if (idx != -1) {
                                    displayName = cursor.getString(idx)
                                }
                            }
                            if (displayName.isNullOrBlank()) {
                                idx = cursor.getColumnIndex(MediaStore.Video.Media.TITLE)
                                if (idx != -1) {
                                    displayName = cursor.getString(idx)
                                }
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }

            // 2. Fallback to uri path segment if not numeric ID
            if (displayName.isNullOrBlank() || isPurelyNumericOrId(displayName)) {
                val lastSegment = uri.lastPathSegment
                if (!lastSegment.isNullOrBlank()) {
                    val candidate = lastSegment.substringAfterLast('/')
                    if (!isPurelyNumericOrId(candidate)) {
                        displayName = candidate
                    }
                }
            }

            // 3. Fallback to MediaMetadataRetriever TITLE if still numeric ID or empty
            if (displayName.isNullOrBlank() || isPurelyNumericOrId(displayName)) {
                val retriever = MediaMetadataRetriever()
                try {
                    retriever.setDataSource(context, uri)
                    val title = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)
                    if (!title.isNullOrBlank() && !isPurelyNumericOrId(title)) {
                        displayName = title
                    }
                } catch (_: Exception) {
                } finally {
                    try { retriever.release() } catch (_: Exception) {}
                }
            }

            var sanitized = displayName?.substringBeforeLast(".") ?: ""
            if (sanitized.startsWith("video:", ignoreCase = true)) {
                sanitized = sanitized.substringAfter("video:")
            }

            if (sanitized.isBlank() || isPurelyNumericOrId(sanitized)) {
                sanitized = "extracted_video_frames"
            }

            return sanitized
        }

        private fun isPurelyNumericOrId(str: String?): Boolean {
            if (str.isNullOrBlank()) return true
            val clean = str.substringBeforeLast(".").trim()
            if (clean.matches(Regex("^[0-9]+$"))) return true
            if (clean.matches(Regex("^video[:_][0-9]+$", RegexOption.IGNORE_CASE))) return true
            return false
        }

        fun startExtraction(
            context: Context,
            videoUri: Uri,
            videoName: String,
            startTimeMs: Long,
            endTimeMs: Long,
            intervalMs: Long,
            format: FrameFormat = FrameFormat.JPEG,
            quality: Int = 90
        ) {
            val intent = Intent(context, FrameExtractionService::class.java).apply {
                action = ACTION_START_EXTRACTION
                putExtra(EXTRA_VIDEO_URI, videoUri.toString())
                putExtra(EXTRA_VIDEO_NAME, videoName)
                putExtra(EXTRA_START_TIME_MS, startTimeMs)
                putExtra(EXTRA_END_TIME_MS, endTimeMs)
                putExtra(EXTRA_INTERVAL_MS, intervalMs)
                putExtra(EXTRA_FORMAT, format.name)
                putExtra(EXTRA_QUALITY, quality)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun cancelExtraction(context: Context) {
            val intent = Intent(context, FrameExtractionService::class.java).apply {
                action = ACTION_CANCEL_EXTRACTION
            }
            context.startService(intent)
        }

        fun resetUiState() {
            if (!_uiState.value.isExtracting) {
                _uiState.value = ExtractionUiState()
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START_EXTRACTION -> {
                val uriString = intent.getStringExtra(EXTRA_VIDEO_URI) ?: return START_NOT_STICKY
                val uri = Uri.parse(uriString)
                val rawVideoName = intent.getStringExtra(EXTRA_VIDEO_NAME) ?: "video"
                val startTimeMs = intent.getLongExtra(EXTRA_START_TIME_MS, 0L)
                val endTimeMs = intent.getLongExtra(EXTRA_END_TIME_MS, 0L)
                val intervalMs = intent.getLongExtra(EXTRA_INTERVAL_MS, 1000L)
                val formatName = intent.getStringExtra(EXTRA_FORMAT) ?: FrameFormat.JPEG.name
                val format = FrameFormat.entries.find { it.name == formatName } ?: FrameFormat.JPEG
                val quality = intent.getIntExtra(EXTRA_QUALITY, 90)

                startExtractionJob(uri, rawVideoName, startTimeMs, endTimeMs, intervalMs, format, quality)
            }
            ACTION_CANCEL_EXTRACTION -> {
                stopExtractionJob("Extraction cancelled by user")
            }
        }
        return START_NOT_STICKY
    }

    @Volatile
    private var isCancelled = false

    private fun startExtractionJob(
        videoUri: Uri,
        rawVideoName: String,
        startTimeMs: Long,
        endTimeMs: Long,
        requestedIntervalMs: Long,
        format: FrameFormat,
        quality: Int
    ) {
        isCancelled = false
        extractionJob?.cancel()

        val sanitizedDisplayName = resolveVideoDisplayName(this, videoUri)
        val clampedStartMs = startTimeMs.coerceAtLeast(0L)
        val clampedEndMs = endTimeMs.coerceAtLeast(clampedStartMs + 100L)
        val durationSelectedMs = clampedEndMs - clampedStartMs

        val notification = buildNotification("Starting frame extraction...", 0, 100, true)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }

        extractionJob = serviceScope.launch {
            _uiState.update {
                it.copy(
                    isExtracting = true,
                    progress = 0f,
                    extractedCount = 0,
                    statusMessage = "Preparing frame extraction...",
                    isSuccess = false,
                    error = null,
                    outputFolderPath = null
                )
            }

            val retriever = MediaMetadataRetriever()
            var successCount = 0
            var previousBitmap: Bitmap? = null

            try {
                retriever.setDataSource(this@FrameExtractionService, videoUri)

                val frameRateStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_CAPTURE_FRAMERATE)
                val nativeFps = frameRateStr?.toFloatOrNull() ?: 30.0f

                val stepMs = if (requestedIntervalMs == 0L) {
                    (1000.0f / nativeFps.coerceAtLeast(1.0f)).toLong().coerceAtLeast(16L)
                } else {
                    requestedIntervalMs.coerceAtLeast(16L)
                }

                val totalFrames = ((durationSelectedMs / stepMs) + 1).toInt()

                _uiState.update {
                    it.copy(totalFramesToExtract = totalFrames)
                }

                var currentMs = clampedStartMs
                var frameIndex = 1

                while (currentMs <= clampedEndMs && coroutineContext.isActive && !isCancelled) {
                    val timeUs = currentMs * 1000L

                    // Use OPTION_CLOSEST first for exact timestamp decoding instead of keyframe OPTION_CLOSEST_SYNC
                    val bitmap: Bitmap? = try {
                        retriever.getFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST)
                            ?: retriever.getFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                    } catch (e: Exception) {
                        null
                    }

                    if (!coroutineContext.isActive || isCancelled) {
                        bitmap?.recycle()
                        break
                    }

                    if (bitmap != null) {
                        // Check if bitmap pixel content is identical to previous frame
                        val isDuplicate = previousBitmap != null && try { bitmap.sameAs(previousBitmap) } catch (_: Exception) { false }

                        if (!isDuplicate) {
                            val fileName = "${sanitizedDisplayName}_frame_$frameIndex.${format.extension}"
                            val saved = saveFrameToStorage(
                                context = this@FrameExtractionService,
                                bitmap = bitmap,
                                folderName = sanitizedDisplayName,
                                fileName = fileName,
                                format = format,
                                quality = quality
                            )

                            if (saved) {
                                successCount++
                                frameIndex++

                                try {
                                    previousBitmap?.recycle()
                                    previousBitmap = bitmap.copy(bitmap.config ?: Bitmap.Config.ARGB_8888, false)
                                } catch (_: Exception) {
                                    previousBitmap = null
                                }
                            }
                        }

                        // Immediately recycle frame bitmap
                        bitmap.recycle()
                    }

                    val progressRatio = ((currentMs - clampedStartMs).toFloat() / durationSelectedMs.toFloat()).coerceIn(0f, 1f)
                    val count = frameIndex - 1

                    val statusMsg = "Extracted $count of $totalFrames frames"
                    _uiState.update {
                        it.copy(
                            progress = progressRatio,
                            extractedCount = count,
                            statusMessage = statusMsg
                        )
                    }

                    if (coroutineContext.isActive && !isCancelled) {
                        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                        notificationManager.notify(
                            NOTIFICATION_ID,
                            buildNotification("Extracting $sanitizedDisplayName: $count/$totalFrames", (progressRatio * 100).toInt(), 100, true)
                        )
                    }

                    currentMs += stepMs
                    yield()
                }

                if (coroutineContext.isActive && !isCancelled) {
                    val relativePath = "Pictures/NosvedPlayer/$sanitizedDisplayName"
                    _uiState.update {
                        it.copy(
                            isExtracting = false,
                            progress = 1.0f,
                            isSuccess = true,
                            outputFolderPath = relativePath,
                            statusMessage = "Successfully saved $successCount frames to $relativePath"
                        )
                    }

                    val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                    notificationManager.notify(
                        NOTIFICATION_ID,
                        buildCompletionNotification("Extraction Complete", "Saved $successCount frames to $relativePath")
                    )
                }
            } catch (e: Exception) {
                if (!isCancelled) {
                    _uiState.update {
                        it.copy(
                            isExtracting = false,
                            error = e.localizedMessage ?: "Extraction failed.",
                            statusMessage = "Extraction failed"
                        )
                    }
                    val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                    notificationManager.notify(
                        NOTIFICATION_ID,
                        buildCompletionNotification("Extraction Failed", e.localizedMessage ?: "Error during extraction")
                    )
                }
            } finally {
                try {
                    previousBitmap?.recycle()
                } catch (_: Exception) {}
                try {
                    retriever.release()
                } catch (_: Exception) {}

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    stopForeground(STOP_FOREGROUND_REMOVE)
                } else {
                    @Suppress("DEPRECATION")
                    stopForeground(true)
                }
                stopSelf()
            }
        }
    }

    private fun stopExtractionJob(reason: String) {
        isCancelled = true
        extractionJob?.cancel()
        _uiState.update {
            it.copy(
                isExtracting = false,
                statusMessage = reason,
                progress = 0f
            )
        }
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.cancel(NOTIFICATION_ID)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
        stopSelf()
    }

    private fun saveFrameToStorage(
        context: Context,
        bitmap: Bitmap,
        folderName: String,
        fileName: String,
        format: FrameFormat,
        quality: Int
    ): Boolean {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val relativePath = "Pictures/NosvedPlayer/$folderName"
                val values = ContentValues().apply {
                    put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
                    put(MediaStore.Images.Media.MIME_TYPE, format.mimeType)
                    put(MediaStore.Images.Media.RELATIVE_PATH, relativePath)
                    put(MediaStore.Images.Media.IS_PENDING, 1)
                }
                val resolver = context.contentResolver
                val imageUri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return false

                resolver.openOutputStream(imageUri)?.use { out ->
                    bitmap.compress(format.compressFormat, quality.coerceIn(1, 100), out)
                }

                values.clear()
                values.put(MediaStore.Images.Media.IS_PENDING, 0)
                resolver.update(imageUri, values, null, null)
                true
            } else {
                val picturesDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
                val targetDir = File(picturesDir, "NosvedPlayer/$folderName")
                if (!targetDir.exists()) {
                    targetDir.mkdirs()
                }
                val outFile = File(targetDir, fileName)
                FileOutputStream(outFile).use { out ->
                    bitmap.compress(format.compressFormat, quality.coerceIn(1, 100), out)
                }
                MediaScannerConnection.scanFile(
                    context,
                    arrayOf(outFile.absolutePath),
                    arrayOf(format.mimeType),
                    null
                )
                true
            }
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Frame Extraction",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows frame extraction progress"
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(contentText: String, progress: Int, max: Int, ongoing: Boolean): Notification {
        val cancelIntent = Intent(this, FrameExtractionService::class.java).apply {
            action = ACTION_CANCEL_EXTRACTION
        }
        val cancelPendingIntent = PendingIntent.getService(
            this, 0, cancelIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Batch Frame Extractor")
            .setContentText(contentText)
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .setProgress(max, progress, false)
            .setOngoing(ongoing)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Cancel", cancelPendingIntent)
            .setOnlyAlertOnce(true)
            .build()
    }

    private fun buildCompletionNotification(title: String, contentText: String): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(contentText)
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .setOngoing(false)
            .setAutoCancel(true)
            .build()
    }

    override fun onDestroy() {
        serviceScope.cancel()
        super.onDestroy()
    }
}
