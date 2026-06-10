package com.devson.nvplayer.domain.thumbnail

import android.content.ContentUris
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.graphics.Matrix
import android.graphics.PixelFormat
import android.media.Image
import android.media.ImageReader
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaCodecList
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.os.Build
import android.provider.MediaStore
import android.util.Size
import android.view.Surface
import androidx.core.graphics.drawable.toDrawable
import coil3.ImageLoader
import coil3.annotation.ExperimentalCoilApi
import coil3.asImage
import coil3.decode.ContentMetadata
import coil3.decode.DecodeResult
import coil3.decode.Decoder
import coil3.decode.ImageSource
import coil3.disk.DiskCache
import coil3.fetch.SourceFetchResult
import coil3.request.Options
import coil3.toAndroidUri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okio.FileSystem
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class CoilVideoThumbnailDecoder(
    private val source: ImageSource,
    private val options: Options,
    private val strategy: ThumbnailStrategy,
    private val diskCache: Lazy<DiskCache?>,
) : Decoder {
    private val diskCacheKey: String
        get() =
            options.diskCacheKey ?: run {
                val metadata = source.metadata
                when {
                    metadata is ContentMetadata -> metadata.uri.toAndroidUri().toString()
                    source.fileSystem === FileSystem.SYSTEM -> source.file().toFile().path
                    else -> error("Unsupported thumbnail source")
                }
            }

    @OptIn(ExperimentalCoilApi::class)
    override suspend fun decode(): DecodeResult {
        readFromDiskCache()?.use { snapshot ->
            val cachedBitmap =
                snapshot.data
                    .toFile()
                    .inputStream()
                    .use(BitmapFactory::decodeStream)

            if (cachedBitmap != null) {
                val sampledBitmap = cachedBitmap.scaleToThumbnailMax()
                return DecodeResult(
                    image = sampledBitmap.toDrawable(options.context.resources).asImage(),
                    isSampled = sampledBitmap !== cachedBitmap,
                )
            }
        }

        if (strategy is ThumbnailStrategy.FirstFrame) {
            tryLoadSystemThumbnail()?.let { systemBitmap ->
                val cachedBitmap = writeToDiskCache(systemBitmap.scaleToThumbnailMax())
                return DecodeResult(
                    image = cachedBitmap.toDrawable(options.context.resources).asImage(),
                    isSampled = true,
                )
            }
        }

        return MediaMetadataRetriever().use { retriever ->
            retriever.setDataSource(source)
            val sourcePath = sourcePath()

            val embeddedPicture =
                if (strategy.prefersEmbeddedPicture()) {
                    EmbeddedArtworkResolver.decodeEmbeddedArtwork(sourcePath, retriever)
                } else {
                    null
                }

            var shouldRotate = true
            val sourcePathForFallback = sourcePath()
            val fallbackTimeUs =
                when (strategy) {
                    is ThumbnailStrategy.FrameAtPercentage -> frameTimeMicros(retriever, strategy.percentage)
                    is ThumbnailStrategy.Hybrid -> frameTimeMicros(retriever, strategy.percentage)
                    is ThumbnailStrategy.EmbeddedOrHybrid -> frameTimeMicros(retriever, strategy.percentage)
                    else -> 0L
                }
            val rawBitmap =
                when (strategy) {
                    ThumbnailStrategy.FirstFrame -> retriever.getThumbnailFrameAt(0)
                    is ThumbnailStrategy.FrameAtPercentage -> {
                        retriever.getThumbnailFrameAt(frameTimeMicros(retriever, strategy.percentage))
                    }

                    is ThumbnailStrategy.Hybrid -> {
                        val firstFrame = retriever.getThumbnailFrameAt(0)
                        if (firstFrame != null && isMostlySolidThumbnail(firstFrame)) {
                            firstFrame.recycle()
                            retriever.getThumbnailFrameAt(frameTimeMicros(retriever, strategy.percentage))
                        } else {
                            firstFrame
                        }
                    }

                    is ThumbnailStrategy.EmbeddedOrHybrid ->
                        embeddedPicture?.also { shouldRotate = false } ?: decodeHybridFrame(retriever, strategy.percentage)

                    ThumbnailStrategy.EmbeddedOrFirstFrame ->
                        embeddedPicture?.also { shouldRotate = false } ?: retriever.getThumbnailFrameAt(0)
                } ?: sourcePathForFallback?.let { path ->
                    decodeWithSoftwareCodec(path, fallbackTimeUs)
                } ?: throw IllegalStateException("Failed to decode video thumbnail")

            val rotatedBitmap = if (shouldRotate) rotateBitmapIfNeeded(retriever, rawBitmap) else rawBitmap
            val thumbnailBitmap = rotatedBitmap.scaleToThumbnailMax()
            val cachedBitmap = writeToDiskCache(thumbnailBitmap)

            DecodeResult(
                image = cachedBitmap.toDrawable(options.context.resources).asImage(),
                isSampled = true,
            )
        }
    }

    private fun tryLoadSystemThumbnail(): Bitmap? {
        val uri =
            when (val metadata = source.metadata) {
                is ContentMetadata -> metadata.uri.toAndroidUri()
                else -> {
                    if (source.fileSystem !== FileSystem.SYSTEM) return null
                    findContentUriForPath(source.file().toFile().path) ?: return null
                }
            }

        return runCatching {
            options.context.contentResolver.loadThumbnail(
                uri,
                Size(MAX_THUMBNAIL_SIZE, MAX_THUMBNAIL_SIZE),
                null,
            )
        }.getOrNull()
    }

    private fun findContentUriForPath(path: String): android.net.Uri? {
        val collection = MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        val projection = arrayOf(MediaStore.Video.Media._ID)
        return runCatching {
            options.context.contentResolver.query(
                collection,
                projection,
                "${MediaStore.Video.Media.DATA} = ?",
                arrayOf(path),
                null,
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val id = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID))
                    ContentUris.withAppendedId(collection, id)
                } else {
                    null
                }
            }
        }.getOrNull()
    }

    private fun sourcePath(): String? {
        val metadata = source.metadata
        return when {
            metadata is ContentMetadata -> {
                val uri = metadata.uri.toAndroidUri()
                if (uri.scheme == "file") uri.path else uri.toString()
            }
            source.fileSystem === FileSystem.SYSTEM -> source.file().toFile().path
            else -> null
        }
    }

    private fun frameTimeMicros(
        retriever: MediaMetadataRetriever,
        percentage: Float,
    ): Long {
        val durationMs =
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
        return (durationMs * percentage.coerceIn(0f, 1f) * 1000).toLong()
    }

    private fun decodeHybridFrame(
        retriever: MediaMetadataRetriever,
        percentage: Float,
    ): Bitmap? {
        val firstFrame = retriever.getThumbnailFrameAt(0)
        return if (firstFrame != null && isMostlySolidThumbnail(firstFrame)) {
            firstFrame.recycle()
            retriever.getThumbnailFrameAt(frameTimeMicros(retriever, percentage))
        } else {
            firstFrame
        }
    }

    private fun MediaMetadataRetriever.getThumbnailFrameAt(timeUs: Long): Bitmap? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            runCatching {
                getScaledFrameAtTime(
                    timeUs,
                    MediaMetadataRetriever.OPTION_CLOSEST_SYNC,
                    MAX_THUMBNAIL_SIZE,
                    MAX_THUMBNAIL_SIZE,
                )
            }.getOrNull() ?: getFrameAtTime(timeUs)
        } else {
            getFrameAtTime(timeUs)
        }

    private fun MediaMetadataRetriever.setDataSource(source: ImageSource) {
        val metadata = source.metadata
        when {
            metadata is ContentMetadata -> setDataSource(options.context, metadata.uri.toAndroidUri())
            source.fileSystem === FileSystem.SYSTEM -> setDataSource(source.file().toFile().path)
            else -> error("Unsupported thumbnail source")
        }
    }

    private fun rotateBitmapIfNeeded(
        retriever: MediaMetadataRetriever,
        bitmap: Bitmap,
    ): Bitmap {
        val rotation =
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull()
                ?: return bitmap
        if (rotation == 0) {
            return bitmap
        }

        val matrix =
            Matrix().apply {
                postRotate(rotation.toFloat())
            }
        val rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        if (rotated != bitmap) {
            bitmap.recycle()
        }
        return rotated
    }

    private suspend fun decodeWithSoftwareCodec(
        path: String,
        timeUs: Long,
    ): Bitmap? = withContext(Dispatchers.IO) {
        val extractor = MediaExtractor()
        runCatching { extractor.setDataSource(path) }.onFailure { extractor.release(); return@withContext null }

        var trackIndex = -1
        for (i in 0 until extractor.trackCount) {
            val mime = extractor.getTrackFormat(i).getString(MediaFormat.KEY_MIME)
            if (mime?.startsWith("video/") == true) { trackIndex = i; break }
        }
        if (trackIndex < 0) { extractor.release(); return@withContext null }

        extractor.selectTrack(trackIndex)
        val trackFormat = extractor.getTrackFormat(trackIndex)
        val mimeType = trackFormat.getString(MediaFormat.KEY_MIME) ?: run { extractor.release(); return@withContext null }

        val codecInfo = findSoftwareCodec(mimeType) ?: run { extractor.release(); return@withContext null }
        val width = trackFormat.getInteger(MediaFormat.KEY_WIDTH)
        val height = trackFormat.getInteger(MediaFormat.KEY_HEIGHT)
        val targetWidth = minOf(width, MAX_THUMBNAIL_SIZE)
        val targetHeight = (height.toFloat() * targetWidth / width).toInt().coerceAtLeast(1)

        var bitmap: Bitmap? = null

        for (pixelFormat in listOf(PixelFormat.RGBA_8888, ImageFormat.YUV_420_888)) {
            if (bitmap != null) break

            val imageReader = ImageReader.newInstance(targetWidth, targetHeight, pixelFormat, 2)
            val surface = imageReader.surface
            val codec = runCatching { MediaCodec.createByCodecName(codecInfo.name) }.getOrNull()
            if (codec == null) { imageReader.close(); continue }

            val latch = CountDownLatch(1)
            var localBitmap: Bitmap? = null

            imageReader.setOnImageAvailableListener({ reader ->
                val image = reader.acquireLatestImage()
                if (image != null && localBitmap == null) {
                    localBitmap = imageReaderImageToBitmap(image)
                    image.close()
                    latch.countDown()
                }
            }, null)

            val decodeResult = runCatching {
                var configured = false
                // Try original format first, then strip constraints for 10-bit/unsupported profiles
                for (fmt in listOf(trackFormat, cleanFormatForDecoder(trackFormat))) {
                    try {
                        codec.configure(fmt, surface, null, 0)
                        configured = true
                        break
                    } catch (_: Exception) { /* try next */ }
                }
                if (!configured) throw IllegalStateException("No compatible format for codec")
                codec.start()
                extractor.seekTo(timeUs, MediaExtractor.SEEK_TO_CLOSEST_SYNC)
                val bufferInfo = MediaCodec.BufferInfo()
                var inputDone = false
                var outputDone = false

                while (!outputDone && localBitmap == null) {
                    if (!inputDone) {
                        val inputIndex = codec.dequeueInputBuffer(5000L)
                        if (inputIndex >= 0) {
                            val inputBuffer = codec.getInputBuffer(inputIndex) ?: continue
                            val sampleSize = extractor.readSampleData(inputBuffer, 0)
                            if (sampleSize < 0) {
                                codec.queueInputBuffer(inputIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                                inputDone = true
                            } else {
                                codec.queueInputBuffer(inputIndex, 0, sampleSize, extractor.sampleTime, 0)
                                extractor.advance()
                            }
                        }
                    }

                    val outputIndex = codec.dequeueOutputBuffer(bufferInfo, 5000L)
                    if (outputIndex >= 0) {
                        if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) outputDone = true
                        codec.releaseOutputBuffer(outputIndex, true)
                    }
                }
            }

            latch.await(2, TimeUnit.SECONDS)
            runCatching { codec.stop() }
            runCatching { codec.release() }
            imageReader.close()

            if (decodeResult.isSuccess && localBitmap != null) {
                bitmap = localBitmap
            }
        }

        extractor.release()
        bitmap
    }

    private fun findSoftwareCodec(mimeType: String): MediaCodecInfo? {
        val codecList = MediaCodecList(MediaCodecList.ALL_CODECS)
        val isAliasSupported = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
        for (info in codecList.codecInfos) {
            if (info.isEncoder) continue
            if (isAliasSupported && info.isAlias) continue
            if (!info.name.startsWith("c2.android.") && !info.name.startsWith("OMX.google.")) continue
            if (info.supportedTypes.any { it.equals(mimeType, ignoreCase = true) }) return info
        }
        for (info in codecList.codecInfos) {
            if (info.isEncoder) continue
            if (!info.name.startsWith("c2.android.") && !info.name.startsWith("OMX.google.")) continue
            if (info.supportedTypes.any { it.equals(mimeType, ignoreCase = true) }) return info
        }
        return null
    }

    private fun cleanFormatForDecoder(format: MediaFormat): MediaFormat {
        val clean = MediaFormat()
        format.getString(MediaFormat.KEY_MIME)?.let { clean.setString(MediaFormat.KEY_MIME, it) }
        if (format.containsKey(MediaFormat.KEY_WIDTH)) {
            clean.setInteger(MediaFormat.KEY_WIDTH, format.getInteger(MediaFormat.KEY_WIDTH))
        }
        if (format.containsKey(MediaFormat.KEY_HEIGHT)) {
            clean.setInteger(MediaFormat.KEY_HEIGHT, format.getInteger(MediaFormat.KEY_HEIGHT))
        }
        for (key in listOf("csd-0", "csd-1", "csd-2")) {
            format.getByteBuffer(key)?.let { clean.setByteBuffer(key, it) }
        }
        return clean
    }

    private fun imageReaderImageToBitmap(image: Image): Bitmap? {
        if (image.format == PixelFormat.RGBA_8888) {
            val buffer = image.planes[0].buffer
            val pixelCount = image.width * image.height
            val pixels = IntArray(pixelCount)
            val temp = ByteArray(4)
            for (i in 0 until pixelCount) {
                buffer.get(temp)
                pixels[i] = ((temp[3].toInt() and 0xFF) shl 24) or
                    ((temp[0].toInt() and 0xFF) shl 16) or
                    ((temp[1].toInt() and 0xFF) shl 8) or
                    (temp[2].toInt() and 0xFF)
            }
            val bitmap = Bitmap.createBitmap(image.width, image.height, Bitmap.Config.ARGB_8888)
            bitmap.setPixels(pixels, 0, image.width, 0, 0, image.width, image.height)
            return bitmap
        }

        if (image.format == ImageFormat.YUV_420_888) {
            return yuv420ToBitmap(image)
        }

        return null
    }

    private fun yuv420ToBitmap(image: Image): Bitmap? {
        val planes = image.planes
        if (planes.size < 3) return null

        val width = image.width
        val height = image.height
        val yPlane = planes[0]
        val uPlane = planes[1]
        val vPlane = planes[2]
        val yBuffer = yPlane.buffer
        val uBuffer = uPlane.buffer
        val vBuffer = vPlane.buffer
        val yRowStride = yPlane.rowStride
        val uRowStride = uPlane.rowStride
        val vRowStride = vPlane.rowStride
        val uPixelStride = uPlane.pixelStride
        val vPixelStride = vPlane.pixelStride

        val pixels = IntArray(width * height)
        for (y in 0 until height) {
            for (x in 0 until width) {
                val yIdx = y * yRowStride + x
                val uIdx = (y / 2) * uRowStride + (x / 2) * uPixelStride
                val vIdx = (y / 2) * vRowStride + (x / 2) * vPixelStride

                val yVal = (yBuffer.get(yIdx).toInt() and 0xFF) - 16
                val uVal = (uBuffer.get(uIdx).toInt() and 0xFF) - 128
                val vVal = (vBuffer.get(vIdx).toInt() and 0xFF) - 128

                val r = (1.164f * yVal + 1.596f * vVal).toInt().coerceIn(0, 255)
                val g = (1.164f * yVal - 0.392f * uVal - 0.813f * vVal).toInt().coerceIn(0, 255)
                val b = (1.164f * yVal + 2.017f * uVal).toInt().coerceIn(0, 255)

                pixels[y * width + x] = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
            }
        }

        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        bitmap.setPixels(pixels, 0, width, 0, 0, width, height)
        return bitmap
    }

    private fun readFromDiskCache(): DiskCache.Snapshot? =
        if (options.diskCachePolicy.readEnabled) {
            diskCache.value?.openSnapshot(diskCacheKey)
        } else {
            null
        }

    private fun writeToDiskCache(inBitmap: Bitmap): Bitmap {
        if (!options.diskCachePolicy.writeEnabled) {
            return inBitmap
        }

        val editor = diskCache.value?.openEditor(diskCacheKey) ?: return inBitmap
        try {
            editor.data.toFile().outputStream().use { output ->
                inBitmap.compress(Bitmap.CompressFormat.JPEG, 90, output)
            }
            editor.commitAndOpenSnapshot()?.use { snapshot ->
                val outBitmap =
                    snapshot.data
                        .toFile()
                        .inputStream()
                        .use(BitmapFactory::decodeStream)
                if (outBitmap != null) {
                    if (outBitmap != inBitmap) {
                        inBitmap.recycle()
                    }
                    return outBitmap
                }
            }
        } catch (_: Exception) {
            runCatching { editor.abort() }
        }

        return inBitmap
    }

    class Factory(
        private val thumbnailStrategy: () -> ThumbnailStrategy,
    ) : Decoder.Factory {
        override fun create(
            result: SourceFetchResult,
            options: Options,
            imageLoader: ImageLoader,
        ): Decoder? {
            if (!isApplicable(result)) {
                return null
            }

            return CoilVideoThumbnailDecoder(
                source = result.source,
                options = options,
                strategy = thumbnailStrategy(),
                diskCache = lazy { imageLoader.diskCache },
            )
        }

        private fun isApplicable(result: SourceFetchResult): Boolean {
            val mimeType = result.mimeType
            if (mimeType != null && mimeType.startsWith("video/")) {
                return true
            }

            val metadata = result.source.metadata
            val sourcePath =
                when {
                    metadata is ContentMetadata -> metadata.uri.toString()
                    result.source.fileSystem === FileSystem.SYSTEM -> result.source.file().toFile().path
                    else -> null
                } ?: return false

            val extension = sourcePath.substringAfterLast('.', "").lowercase()
            return VIDEO_EXTENSIONS.contains(extension)
        }
    }

    companion object {
        private val VIDEO_EXTENSIONS = setOf(
            "mp4", "mkv", "webm", "avi", "3gp",
            "flv", "mov", "m4v", "ts", "wmv"
        )
    }
}

private inline fun <T> MediaMetadataRetriever.use(block: (MediaMetadataRetriever) -> T): T =
    try {
        block(this)
    } finally {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            close()
        } else {
            release()
        }
    }
