package com.devson.nvplayer.data.security

import android.content.ContentUris
import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.webkit.MimeTypeMap
import com.devson.nvplayer.data.database.VaultDao
import com.devson.nvplayer.data.database.VaultEntity
import com.devson.nvplayer.domain.model.VaultStorageMode
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.util.UUID

data class VaultImportResult(
    val entity: VaultEntity,
    val pendingDeleteUri: Uri? = null
)

data class RebuildDatabaseResult(
    val restoredCount: Int,
    val invalidSkippedCount: Int
)

data class VaultDeletionResult(
    val success: Boolean,
    val deletedMediaCount: Int,
    val failedMediaCount: Int,
    val remainingFiles: List<String>
)

class VaultFileManager(
    private val context: Context? = null,
    private val vaultDao: VaultDao,
    val vaultContainer: VaultContainer = DefaultVaultContainer(),
    customVaultDirectory: File? = null,
    customThumbsDirectory: File? = null,
    customTempPlaybackDirectory: File? = null,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) {

    val vaultDirectory: File by lazy {
        if (customVaultDirectory != null) {
            if (!customVaultDirectory.exists()) customVaultDirectory.mkdirs()
            val nomedia = File(customVaultDirectory, ".nomedia")
            if (!nomedia.exists()) {
                try { nomedia.createNewFile() } catch (_: Exception) {}
            }
            return@lazy customVaultDirectory
        }

        val docsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS)
        val vaultDir = File(docsDir, "NosvedPlayer/.vault_secure_media")
        if (!vaultDir.exists()) {
            vaultDir.mkdirs()
        }
        val nomedia = File(vaultDir, ".nomedia")
        if (!nomedia.exists()) {
            try { nomedia.createNewFile() } catch (_: Exception) {}
        }
        val legacyInternal = context?.filesDir?.let { File(it, "vault_secure_media") }
        if (legacyInternal != null && legacyInternal.exists() && legacyInternal.isDirectory) {
            legacyInternal.listFiles()?.forEach { file ->
                if (file.isFile && file.extension == "vlt") {
                    val dest = File(vaultDir, file.name)
                    if (!dest.exists()) {
                        file.copyTo(dest, overwrite = true)
                    }
                    file.delete()
                }
            }
        }
        vaultDir
    }

    val thumbsDirectory: File by lazy {
        val dir = customThumbsDirectory ?: File(vaultDirectory, ".thumbs")
        if (!dir.exists()) dir.mkdirs()
        val nomedia = File(dir, ".nomedia")
        if (!nomedia.exists()) {
            try { nomedia.createNewFile() } catch (_: Exception) {}
        }
        dir
    }

    val tempPlaybackDirectory: File by lazy {
        val dir = customTempPlaybackDirectory
            ?: (context?.cacheDir?.let { File(it, "vault_playback_temp") }
                ?: File(vaultDirectory, ".playback_temp"))
        if (!dir.exists()) dir.mkdirs()
        val nomedia = File(dir, ".nomedia")
        if (!nomedia.exists()) {
            try { nomedia.createNewFile() } catch (_: Exception) {}
        }
        dir
    }

    val migrationManager: VaultMigrationManager by lazy {
        VaultMigrationManager(
            cacheDir = tempPlaybackDirectory.parentFile ?: File(vaultDirectory, ".cache"),
            vaultDao = vaultDao,
            vaultContainer = vaultContainer,
            vaultDirectory = vaultDirectory
        )
    }

    init {
        cleanPlaybackTemp()
        cleanOrphanedPartFiles()
    }

    /**
     * Imports media directly from an InputStream into the vault using buffered streaming.
     */
    suspend fun importStreamToVault(
        sourceInputStream: InputStream,
        title: String,
        originalExtension: String = "mp4",
        durationMs: Long = 0L,
        storageMode: VaultStorageMode = VaultStorageMode.NONE,
        vaultCredential: String = ""
    ): Result<VaultEntity> = withContext(ioDispatcher) {
        val fileId = UUID.randomUUID().toString()
        val destFile = File(vaultDirectory, "$fileId.vlt")
        val cleanTitle = title.ifBlank { "Protected Video" }
        val dateAdded = System.currentTimeMillis()
        val originalExt = originalExtension.trimStart('.').lowercase().ifBlank { "mp4" }

        try {
            val header = if (storageMode == VaultStorageMode.ENCRYPTED) {
                if (vaultCredential.isBlank()) {
                    return@withContext Result.failure(IllegalArgumentException("Vault credential is required for encrypted import"))
                }
                vaultContainer.createEncryptedVaultFile(
                    sourceInputStream = sourceInputStream,
                    destinationVaultFile = destFile,
                    passwordOrPin = vaultCredential,
                    title = cleanTitle,
                    originalExtension = originalExt,
                    durationMs = durationMs
                )
            } else {
                vaultContainer.createUnencryptedVaultFile(
                    sourceInputStream = sourceInputStream,
                    destinationVaultFile = destFile,
                    title = cleanTitle,
                    originalExtension = originalExt,
                    durationMs = durationMs
                )
            }

            var thumbPath: String? = null
            var finalDuration = durationMs
            try {
                val tempPlayback = getPlaybackFileInternal(destFile, fileId, vaultCredential)
                thumbPath = generateAndSaveThumbnail(tempPlayback, fileId)
                if (finalDuration <= 0L) {
                    finalDuration = extractDuration(tempPlayback)
                }
                releasePlaybackFile(tempPlayback)
            } catch (_: Exception) {}

            val entity = VaultEntity(
                title = cleanTitle,
                originalUri = "",
                vaultPath = destFile.absolutePath,
                thumbnailPath = thumbPath,
                fileSize = destFile.length(),
                durationMs = finalDuration,
                dateAdded = dateAdded,
                storageMode = header.storageMode,
                formatVersion = header.formatVersion,
                originalExtension = header.originalExtension
            )

            val insertedId = vaultDao.insert(entity)
            Result.success(entity.copy(id = insertedId))
        } catch (e: Exception) {
            if (destFile.exists()) {
                try { destFile.delete() } catch (_: Exception) {}
            }
            val partFile = File(vaultDirectory, "$fileId.vlt.part")
            if (partFile.exists()) {
                try { partFile.delete() } catch (_: Exception) {}
            }
            Result.failure(e)
        }
    }

    /**
     * Imports a source File into the vault.
     */
    suspend fun importFileToVault(
        sourceFile: File,
        title: String = "",
        durationMs: Long = 0L,
        storageMode: VaultStorageMode = VaultStorageMode.NONE,
        vaultCredential: String = ""
    ): Result<VaultEntity> = withContext(ioDispatcher) {
        if (!sourceFile.exists()) {
            return@withContext Result.failure(FileNotFoundException("Source file not found: ${sourceFile.absolutePath}"))
        }
        val cleanTitle = title.ifBlank { sourceFile.nameWithoutExtension }
        val ext = sourceFile.extension.ifBlank { "mp4" }
        FileInputStream(sourceFile).buffered().use { stream ->
            importStreamToVault(
                sourceInputStream = stream,
                title = cleanTitle,
                originalExtension = ext,
                durationMs = durationMs,
                storageMode = storageMode,
                vaultCredential = vaultCredential
            )
        }
    }

    /**
     * Imports a video from an external Android ContentProvider URI into the vault.
     */
    suspend fun importVideoToVault(
        sourceUri: Uri,
        title: String,
        durationMs: Long = 0L,
        storageMode: VaultStorageMode = VaultStorageMode.NONE,
        vaultCredential: String = ""
    ): Result<VaultImportResult> = withContext(Dispatchers.IO) {
        try {
            val ctx = context
                ?: return@withContext Result.failure(IllegalStateException("Context is required for URI-based import"))
            val inputStream = ctx.contentResolver.openInputStream(sourceUri)
                ?: return@withContext Result.failure(FileNotFoundException("Cannot open stream for URI: $sourceUri"))

            val cleanTitle = title.ifBlank { "Protected Video" }
            val originalExt = resolveExtension(sourceUri, cleanTitle)

            val result = inputStream.use { stream ->
                importStreamToVault(
                    sourceInputStream = stream,
                    title = cleanTitle,
                    originalExtension = originalExt,
                    durationMs = durationMs,
                    storageMode = storageMode,
                    vaultCredential = vaultCredential
                )
            }

            result.fold(
                onSuccess = { entity ->
                    val updatedEntity = entity.copy(originalUri = sourceUri.toString())
                    vaultDao.insert(updatedEntity)
                    val pendingDeleteUri = removeOriginalSourceFile(sourceUri)
                    Result.success(VaultImportResult(updatedEntity, pendingDeleteUri))
                },
                onFailure = { error ->
                    Result.failure(error)
                }
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun rebuildDatabaseFromStorage(credential: String = ""): RebuildDatabaseResult = withContext(ioDispatcher) {
        var restoredCount = 0
        var invalidCount = 0
        val vltFiles = vaultDirectory.listFiles { file -> file.isFile && file.extension == "vlt" }
            ?: return@withContext RebuildDatabaseResult(0, 0)
        val existingEntities = try {
            vaultDao.getAllVaultMedia().associateBy { File(it.vaultPath).name }
        } catch (_: Exception) {
            emptyMap()
        }

        for (file in vltFiles) {
            if (existingEntities.containsKey(file.name)) {
                continue
            }

            val classification = migrationManager.classifyFile(file)
            if (classification == VaultFileClassification.INVALID_UNKNOWN) {
                invalidCount++
                continue
            }

            try {
                val header = try {
                    vaultContainer.inspectVaultFile(file)
                } catch (_: Exception) {
                    null
                }

                val fileId = file.nameWithoutExtension
                val existingThumb = File(thumbsDirectory, "$fileId.jpg")
                val thumbPath = if (existingThumb.exists()) {
                    existingThumb.absolutePath
                } else {
                    try {
                        val tempPlay = getPlaybackFileInternal(file, fileId, credential)
                        val tp = generateAndSaveThumbnail(tempPlay, fileId)
                        releasePlaybackFile(tempPlay)
                        tp
                    } catch (_: Exception) {
                        null
                    }
                }

                val mode = when (classification) {
                    VaultFileClassification.CURRENT_NO_ENCRYPTION -> VaultStorageMode.NONE
                    VaultFileClassification.CURRENT_SECURE_ENCRYPTED,
                    VaultFileClassification.LEGACY_ENCRYPTED -> VaultStorageMode.ENCRYPTED
                    else -> header?.storageMode ?: VaultStorageMode.NONE
                }

                val version = when (classification) {
                    VaultFileClassification.LEGACY_ENCRYPTED -> VaultFileFormat.FORMAT_VERSION_LEGACY_V1
                    else -> header?.formatVersion ?: VaultFileFormat.FORMAT_VERSION_V2
                }

                val originalExt = header?.originalExtension?.ifBlank { "mp4" } ?: "mp4"
                val title = header?.title?.ifBlank { file.nameWithoutExtension } ?: file.nameWithoutExtension

                val entity = VaultEntity(
                    title = title,
                    originalUri = "",
                    vaultPath = file.absolutePath,
                    thumbnailPath = thumbPath,
                    fileSize = file.length(),
                    durationMs = header?.durationMs ?: 0L,
                    dateAdded = header?.dateAdded?.takeIf { it > 0 } ?: file.lastModified(),
                    storageMode = mode,
                    formatVersion = version,
                    originalExtension = originalExt
                )

                vaultDao.insert(entity)
                restoredCount++
            } catch (_: Exception) {
                invalidCount++
            }
        }
        RebuildDatabaseResult(restoredCount, invalidCount)
    }

    private fun resolveExtension(sourceUri: Uri, title: String): String {
        val fromTitle = title.substringAfterLast('.', "").trim().lowercase()
        if (fromTitle.isNotEmpty() && fromTitle.length in 2..5 && fromTitle.all { it.isLetterOrDigit() }) {
            return fromTitle
        }
        val mime = context?.contentResolver?.getType(sourceUri)
        if (mime != null) {
            val ext = MimeTypeMap.getSingleton().getExtensionFromMimeType(mime)
            if (!ext.isNullOrBlank()) return ext.lowercase()
        }
        return "mp4"
    }

    private fun removeOriginalSourceFile(sourceUri: Uri): Uri? {
        val ctx = context ?: return null
        var successfullyDeleted = false

        try {
            if (DocumentsContract.isDocumentUri(ctx, sourceUri)) {
                successfullyDeleted = DocumentsContract.deleteDocument(ctx.contentResolver, sourceUri)
            }
        } catch (_: Exception) {}

        if (!successfullyDeleted) {
            try {
                val deletedRows = ctx.contentResolver.delete(sourceUri, null, null)
                if (deletedRows > 0) successfullyDeleted = true
            } catch (_: Exception) {}
        }

        var originalPath: String? = null
        try {
            val cursor = ctx.contentResolver.query(
                sourceUri,
                arrayOf(MediaStore.MediaColumns.DATA),
                null,
                null,
                null
            )
            cursor?.use {
                if (it.moveToFirst()) {
                    originalPath = it.getString(it.getColumnIndexOrThrow(MediaStore.MediaColumns.DATA))
                }
            }
        } catch (_: Exception) {}

        if (originalPath != null) {
            try {
                val file = File(originalPath)
                if (file.exists()) {
                    if (file.delete()) {
                        successfullyDeleted = true
                    }
                }
                MediaScannerConnection.scanFile(
                    ctx,
                    arrayOf(originalPath),
                    null,
                    null
                )
            } catch (_: Exception) {}
        }

        if (successfullyDeleted) {
            try {
                ctx.contentResolver.notifyChange(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, null)
            } catch (_: Exception) {}
            return null
        }

        return resolveMediaStoreUri(sourceUri, originalPath)
    }

    private fun resolveMediaStoreUri(sourceUri: Uri, knownPath: String?): Uri? {
        val ctx = context ?: return null
        if (sourceUri.authority == "media") {
            return sourceUri
        }
        val pathToQuery = knownPath ?: try {
            var p: String? = null
            ctx.contentResolver.query(
                sourceUri,
                arrayOf(MediaStore.MediaColumns.DATA),
                null,
                null,
                null
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    p = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATA))
                }
            }
            p
        } catch (_: Exception) { null }

        if (pathToQuery != null) {
            try {
                ctx.contentResolver.query(
                    MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                    arrayOf(MediaStore.Video.Media._ID),
                    "${MediaStore.Video.Media.DATA} = ?",
                    arrayOf(pathToQuery),
                    null
                )?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val id = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID))
                        return ContentUris.withAppendedId(
                            MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                            id
                        )
                    }
                }
            } catch (_: Exception) {}
        }
        return null
    }

    fun getPlaybackFile(vaultEntity: VaultEntity, credential: String = ""): File = runBlocking(ioDispatcher) {
        val vaultFile = File(vaultEntity.vaultPath)
        if (!vaultFile.exists()) {
            throw FileNotFoundException("Vault file not found: ${vaultEntity.vaultPath}")
        }
        val fileId = "${vaultEntity.id}_${vaultFile.nameWithoutExtension}"
        getPlaybackFileInternal(vaultFile, fileId, credential)
    }

    internal fun getPlaybackFileInternal(vaultFile: File, fileId: String, credential: String = ""): File {
        val header = try {
            runBlocking { vaultContainer.inspectVaultFile(vaultFile) }
        } catch (_: Exception) {
            null
        }

        val ext = header?.originalExtension?.ifBlank { "mp4" } ?: "mp4"
        val tempFile = File(tempPlaybackDirectory, "playback_${fileId}.$ext")
        if (tempFile.exists() && tempFile.length() > 0) {
            return tempFile
        }

        runBlocking {
            vaultContainer.decryptVaultFile(vaultFile, tempFile, credential)
        }
        return tempFile
    }

    fun releasePlaybackFile(playbackFile: File) {
        try {
            if (playbackFile.exists() && playbackFile.parentFile?.absolutePath == tempPlaybackDirectory.absolutePath) {
                playbackFile.delete()
            }
        } catch (_: Exception) {}
    }

    suspend fun restoreVideoFromVault(
        vaultEntity: VaultEntity,
        destinationDirectory: File,
        credential: String = ""
    ): Result<File> = withContext(ioDispatcher) {
        try {
            val vaultFile = File(vaultEntity.vaultPath)
            if (!vaultFile.exists()) {
                return@withContext Result.failure(FileNotFoundException("Vault file not found: ${vaultEntity.vaultPath}"))
            }

            if (!destinationDirectory.exists()) {
                destinationDirectory.mkdirs()
            }

            val ext = vaultEntity.originalExtension.ifBlank { "mp4" }
            var baseName = vaultEntity.title
            if (baseName.endsWith(".$ext", ignoreCase = true)) {
                baseName = baseName.substring(0, baseName.length - ext.length - 1)
            }
            var targetFile = File(destinationDirectory, "$baseName.$ext")
            var counter = 1
            while (targetFile.exists()) {
                targetFile = File(destinationDirectory, "${baseName}_$counter.$ext")
                counter++
            }

            val partFile = File(destinationDirectory, "${targetFile.name}.part")
            if (partFile.exists()) partFile.delete()

            try {
                val header = vaultContainer.inspectVaultFile(vaultFile)
                if (header.storageMode == VaultStorageMode.NONE) {
                    FileInputStream(vaultFile).buffered().use { inStream ->
                        FileOutputStream(partFile).buffered().use { outStream ->
                            val buffer = ByteArray(64 * 1024)
                            var bytesRead: Int
                            while (inStream.read(buffer).also { bytesRead = it } != -1) {
                                outStream.write(buffer, 0, bytesRead)
                            }
                            outStream.flush()
                        }
                    }
                } else {
                    vaultContainer.decryptVaultFile(vaultFile, partFile, credential)
                }

                if (!partFile.exists() || partFile.length() == 0L) {
                    throw IOException("Restoration failed: target file is empty")
                }

                if (header.storageMode == VaultStorageMode.NONE && partFile.length() != vaultFile.length()) {
                    throw IOException("Restoration failed: file size mismatch")
                }

                if (!partFile.renameTo(targetFile)) {
                    partFile.copyTo(targetFile, overwrite = true)
                    partFile.delete()
                }

                vaultFile.delete()
                vaultEntity.thumbnailPath?.let {
                    val thumb = File(it)
                    if (thumb.exists()) thumb.delete()
                }

                val tempFile = File(tempPlaybackDirectory, "playback_${vaultEntity.id}_${vaultFile.nameWithoutExtension}.$ext")
                if (tempFile.exists()) tempFile.delete()

                vaultDao.delete(vaultEntity)

                if (context != null) {
                    try {
                        MediaScannerConnection.scanFile(
                            context,
                            arrayOf(targetFile.absolutePath),
                            null,
                            null
                        )
                    } catch (_: Exception) {}
                }

                Result.success(targetFile)
            } catch (e: Exception) {
                if (partFile.exists()) partFile.delete()
                Result.failure(e)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun deletePermanently(vaultEntity: VaultEntity): Result<Unit> = withContext(ioDispatcher) {
        try {
            val vaultFile = File(vaultEntity.vaultPath)
            if (vaultFile.exists()) {
                val deleted = vaultFile.delete()
                if (!deleted && vaultFile.exists()) {
                    return@withContext Result.failure(IOException("Failed to delete vault file on storage: ${vaultFile.absolutePath}"))
                }
            }

            val ext = vaultEntity.originalExtension.ifBlank { "mp4" }
            val tempFile = File(tempPlaybackDirectory, "playback_${vaultEntity.id}_${vaultFile.nameWithoutExtension}.$ext")
            if (tempFile.exists()) tempFile.delete()

            vaultEntity.thumbnailPath?.let {
                val thumb = File(it)
                if (thumb.exists()) thumb.delete()
            }

            vaultDao.delete(vaultEntity)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun hasDatabaseRecords(): Boolean = withContext(ioDispatcher) {
        try {
            vaultDao.getAllVaultMedia().isNotEmpty()
        } catch (_: Exception) {
            false
        }
    }

    suspend fun removeAllVaultData(securityManager: VaultSecurityManager? = null): VaultDeletionResult = withContext(ioDispatcher) {
        var deletedCount = 0
        var failedCount = 0
        val remaining = mutableListOf<String>()

        val files = vaultDirectory.listFiles()
        if (files != null) {
            for (file in files) {
                if (file.name == ".nomedia") continue
                if (file.isDirectory) {
                    if (file.name == ".thumbs" || file.name == ".playback_temp") {
                        file.listFiles()?.forEach { sub ->
                            if (sub.name != ".nomedia") {
                                if (!sub.delete() && sub.exists()) {
                                    remaining.add("${file.name}/${sub.name}")
                                }
                            }
                        }
                    }
                } else {
                    val isVlt = file.extension == "vlt"
                    if (file.delete()) {
                        if (isVlt) deletedCount++
                    } else if (file.exists()) {
                        if (isVlt) failedCount++
                        remaining.add(file.name)
                    }
                }
            }
        }

        thumbsDirectory.listFiles()?.forEach { file ->
            if (file.name != ".nomedia") {
                if (!file.delete() && file.exists()) {
                    remaining.add("thumbs/${file.name}")
                }
            }
        }

        tempPlaybackDirectory.listFiles()?.forEach { file ->
            if (file.name != ".nomedia") {
                if (!file.delete() && file.exists()) {
                    remaining.add("playback_temp/${file.name}")
                }
            }
        }

        try {
            vaultDao.deleteAll()
        } catch (_: Exception) {
            try {
                vaultDao.getAllVaultMedia().forEach { vaultDao.delete(it) }
            } catch (_: Exception) {}
        }

        securityManager?.deleteVaultMetadata()

        val overallSuccess = failedCount == 0 && remaining.isEmpty()
        VaultDeletionResult(
            success = overallSuccess,
            deletedMediaCount = deletedCount,
            failedMediaCount = failedCount,
            remainingFiles = remaining
        )
    }

    fun cleanPlaybackTemp() {
        try {
            tempPlaybackDirectory.listFiles()?.forEach { file ->
                if (file.name != ".nomedia") {
                    try { file.delete() } catch (_: Exception) {}
                }
            }
            cleanOrphanedPartFiles()
        } catch (_: Exception) {}
    }

    fun cleanOrphanedPartFiles(): Int {
        var count = 0
        try {
            vaultDirectory.listFiles()?.forEach { file ->
                if (file.name.endsWith(".part") || file.name.endsWith(".migrating")) {
                    if (file.delete()) count++
                }
            }
            tempPlaybackDirectory.listFiles()?.forEach { file ->
                if (file.name.endsWith(".part") || file.name.endsWith(".tmp")) {
                    if (file.delete()) count++
                }
            }
        } catch (_: Exception) {}
        return count
    }

    private fun generateAndSaveThumbnail(videoFile: File, fileId: String): String? {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(videoFile.absolutePath)
            val bitmap = retriever.getFrameAtTime(1000000, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                ?: retriever.frameAtTime
            if (bitmap != null) {
                val thumbFile = File(thumbsDirectory, "$fileId.jpg")
                FileOutputStream(thumbFile).use { out ->
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 85, out)
                    out.flush()
                }
                bitmap.recycle()
                thumbFile.absolutePath
            } else {
                null
            }
        } catch (e: Exception) {
            null
        } finally {
            try {
                retriever.release()
            } catch (_: Exception) {}
        }
    }

    private fun extractDuration(videoFile: File): Long {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(videoFile.absolutePath)
            val durString = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
            durString?.toLongOrNull() ?: 0L
        } catch (e: Exception) {
            0L
        } finally {
            try {
                retriever.release()
            } catch (_: Exception) {}
        }
    }
}
