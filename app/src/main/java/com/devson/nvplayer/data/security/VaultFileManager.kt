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
import kotlinx.coroutines.CancellationException
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

class ProgressInputStream(
    private val wrapped: InputStream,
    private val totalBytes: Long,
    private val onProgress: ((Float) -> Unit)?,
    private val isCancelled: (() -> Boolean)? = null
) : InputStream() {
    private var bytesRead = 0L
    private var lastPercent = -1

    override fun read(): Int {
        if (isCancelled?.invoke() == true) {
            throw CancellationException("Operation cancelled by user")
        }
        val b = wrapped.read()
        if (b != -1) {
            bytesRead++
            report()
        }
        return b
    }

    override fun read(b: ByteArray, off: Int, len: Int): Int {
        if (isCancelled?.invoke() == true) {
            throw CancellationException("Operation cancelled by user")
        }
        val r = wrapped.read(b, off, len)
        if (r > 0) {
            bytesRead += r
            report()
        }
        return r
    }

    private fun report() {
        if (totalBytes > 0 && onProgress != null) {
            val percent = ((bytesRead.toDouble() / totalBytes.toDouble()) * 100).toInt()
            if (percent != lastPercent) {
                lastPercent = percent
                onProgress.invoke((bytesRead.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f))
            }
        }
    }

    override fun close() {
        wrapped.close()
    }
}

class VaultFileManager(
    private val context: Context? = null,
    private val vaultDao: VaultDao,
    val vaultContainer: VaultContainer = DefaultVaultContainer(),
    customVaultDirectory: File? = null,
    customThumbsDirectory: File? = null,
    customTempPlaybackDirectory: File? = null,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    customSecurityManager: VaultSecurityManager? = null
) {

    val securityManager: VaultSecurityManager by lazy {
        customSecurityManager ?: VaultSecurityManager(context, customVaultDirectory)
    }

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
        vaultCredential: String = "",
        originalUri: String = "",
        originalSize: Long = 0L
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
                val masterKey = try { securityManager.deriveEncryptionKey(vaultCredential) } catch (_: Exception) { null }

                vaultContainer.createEncryptedVaultFile(
                    sourceInputStream = sourceInputStream,
                    destinationVaultFile = destFile,
                    passwordOrPin = vaultCredential,
                    masterKey = masterKey,
                    title = cleanTitle,
                    originalExtension = originalExt,
                    durationMs = durationMs,
                    originalSize = originalSize
                )
            } else {
                vaultContainer.createUnencryptedVaultFile(
                    sourceInputStream = sourceInputStream,
                    destinationVaultFile = destFile,
                    title = cleanTitle,
                    originalExtension = originalExt,
                    durationMs = durationMs,
                    originalSize = originalSize
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

            val resolvedSize = if (header.originalPlaintextSize > 0L) {
                header.originalPlaintextSize
            } else {
                destFile.length()
            }

            val entity = VaultEntity(
                title = cleanTitle,
                originalUri = originalUri,
                vaultPath = destFile.absolutePath,
                thumbnailPath = thumbPath,
                fileSize = resolvedSize,
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
                vaultCredential = vaultCredential,
                originalSize = sourceFile.length()
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

            var originalSize = 0L
            try {
                ctx.contentResolver.openAssetFileDescriptor(sourceUri, "r")?.use { afd ->
                    originalSize = afd.length.takeIf { it > 0L } ?: 0L
                }
            } catch (_: Exception) {}

            val result = inputStream.use { stream ->
                importStreamToVault(
                    sourceInputStream = stream,
                    title = cleanTitle,
                    originalExtension = originalExt,
                    durationMs = durationMs,
                    storageMode = storageMode,
                    vaultCredential = vaultCredential,
                    originalUri = sourceUri.toString(),
                    originalSize = originalSize
                )
            }

            result.fold(
                onSuccess = { entity ->
                    val pendingDeleteUri = removeOriginalSourceFile(sourceUri)
                    Result.success(VaultImportResult(entity, pendingDeleteUri))
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
        val vltFiles = buildList {
            try {
                vaultDirectory.listFiles { file -> file.isFile && file.extension == "vlt" }?.let { addAll(it) }
            } catch (_: Exception) {}
            try {
                context?.filesDir?.let { File(it, "vault_secure_media") }?.listFiles { file -> file.isFile && file.extension == "vlt" }?.let { addAll(it) }
            } catch (_: Exception) {}
        }.distinctBy { it.name }
        if (vltFiles.isEmpty()) return@withContext RebuildDatabaseResult(0, 0)
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

                val resolvedSize = if (mode == VaultStorageMode.ENCRYPTED && header != null && header.originalPlaintextSize > 0L) {
                    header.originalPlaintextSize
                } else {
                    file.length()
                }

                val entity = VaultEntity(
                    title = title,
                    originalUri = "",
                    vaultPath = file.absolutePath,
                    thumbnailPath = thumbPath,
                    fileSize = resolvedSize,
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

        val masterKey = if (credential.isNotBlank()) {
            try { securityManager.deriveEncryptionKey(credential) } catch (_: Exception) { null }
        } else null

        runBlocking {
            vaultContainer.decryptVaultFile(vaultFile, tempFile, credential, masterKey = masterKey)
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
                    val masterKey = if (credential.isNotBlank()) {
                        try { securityManager.deriveEncryptionKey(credential) } catch (_: Exception) { null }
                    } else null
                    vaultContainer.decryptVaultFile(vaultFile, partFile, credential, masterKey = masterKey)
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

    suspend fun removeAllVaultData(
        securityManager: VaultSecurityManager? = null,
        credential: String? = null,
        force: Boolean = false
    ): VaultDeletionResult = withContext(ioDispatcher) {
        if (!force && securityManager != null && securityManager.hasPersistentVaultMetadata()) {
            if (credential.isNullOrBlank() || !securityManager.verifyVaultCredential(credential)) {
                throw SecurityException("Authentication failed: valid vault credential required for reset")
            }
        }

        var deletedCount = 0
        var failedCount = 0
        val remaining = mutableListOf<String>()

        try {
            tempPlaybackDirectory.listFiles()?.forEach { file ->
                if (file.name != ".nomedia") {
                    if (!file.delete() && file.exists()) {
                        remaining.add("playback_temp/${file.name}")
                    }
                }
            }
            val tempConvDir = context?.cacheDir?.let { File(it, "vault_conversion_temp") }
                ?: File(tempPlaybackDirectory, ".vault_conversion_temp")
            if (tempConvDir.exists()) {
                tempConvDir.listFiles()?.forEach { file ->
                    if (file.name != ".nomedia") {
                        if (!file.delete() && file.exists()) {
                            remaining.add("conversion_temp/${file.name}")
                        }
                    }
                }
            }
        } catch (_: Exception) {}

        val files = vaultDirectory.listFiles()
        if (files != null) {
            for (file in files) {
                if (file.name == ".nomedia" || file.name == ".vault_config") continue
                if (file.isDirectory) {
                    if (file.name == ".thumbs" || file.name == ".playback_temp" || file.name == ".vault_conversion_temp" || file.name == "vault_conversion_temp") {
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

        if (failedCount > 0 || remaining.isNotEmpty()) {
            return@withContext VaultDeletionResult(
                success = false,
                deletedMediaCount = deletedCount,
                failedMediaCount = failedCount,
                remainingFiles = remaining
            )
        }

        thumbsDirectory.listFiles()?.forEach { file ->
            if (file.name != ".nomedia") {
                if (!file.delete() && file.exists()) {
                    remaining.add("thumbs/${file.name}")
                }
            }
        }

        if (remaining.isNotEmpty()) {
            return@withContext VaultDeletionResult(
                success = false,
                deletedMediaCount = deletedCount,
                failedMediaCount = failedCount,
                remainingFiles = remaining
            )
        }

        try {
            securityManager?.deleteVaultMetadata()
        } catch (e: Exception) {
            remaining.add(".vault_config")
            return@withContext VaultDeletionResult(
                success = false,
                deletedMediaCount = deletedCount,
                failedMediaCount = failedCount,
                remainingFiles = remaining
            )
        }

        var dbCleared = false
        try {
            vaultDao.deleteAll()
            dbCleared = true
        } catch (_: Exception) {
            try {
                vaultDao.getAllVaultMedia().forEach { vaultDao.delete(it) }
                dbCleared = true
            } catch (_: Exception) {
                dbCleared = false
            }
        }

        if (!dbCleared) {
            remaining.add("database_records")
            return@withContext VaultDeletionResult(
                success = false,
                deletedMediaCount = deletedCount,
                failedMediaCount = failedCount,
                remainingFiles = remaining
            )
        }

        securityManager?.setVaultInitializedLocally(false)

        VaultDeletionResult(
            success = true,
            deletedMediaCount = deletedCount,
            failedMediaCount = 0,
            remainingFiles = emptyList()
        )
    }

    suspend fun convertVideoProtection(
        vaultEntity: VaultEntity,
        targetMode: VaultStorageMode,
        credential: String,
        securityManager: VaultSecurityManager,
        onProgress: ((Float) -> Unit)? = null,
        isCancelled: (() -> Boolean)? = null
    ): Result<VaultEntity> = withContext(ioDispatcher) {
        if (vaultEntity.storageMode == targetMode) {
            return@withContext Result.success(vaultEntity)
        }

        val vaultFile = File(vaultEntity.vaultPath)
        if (!vaultFile.exists()) {
            return@withContext Result.failure(FileNotFoundException("Vault file not found: ${vaultEntity.vaultPath}"))
        }

        if (credential.isBlank() || !securityManager.verifyVaultCredential(credential)) {
            return@withContext Result.failure(SecurityException("Invalid vault credentials"))
        }

        val sourceFileSize = vaultFile.length()
        val requiredSpace = (sourceFileSize * 2) + (10L * 1024 * 1024)
        val usableSpace = vaultDirectory.usableSpace
        if (usableSpace > 0 && usableSpace < requiredSpace) {
            return@withContext Result.failure(
                IOException("Insufficient storage space for conversion. Required: ${requiredSpace / (1024 * 1024)}MB, Available: ${usableSpace / (1024 * 1024)}MB")
            )
        }

        val convertingFile = File(vaultFile.parentFile ?: vaultDirectory, "${vaultFile.name}.converting")
        val backupFile = File(vaultFile.parentFile ?: vaultDirectory, "${vaultFile.name}.backup")
        if (convertingFile.exists()) convertingFile.delete()
        if (backupFile.exists()) backupFile.delete()

        val tempConvDir = context?.cacheDir?.let { File(it, "vault_conversion_temp") }
            ?: File(tempPlaybackDirectory, ".vault_conversion_temp")
        if (!tempConvDir.exists()) tempConvDir.mkdirs()
        val nomedia = File(tempConvDir, ".nomedia")
        if (!nomedia.exists()) {
            try { nomedia.createNewFile() } catch (_: Exception) {}
        }
        var tempDecryptedFile: File? = null

        try {
            if (isCancelled?.invoke() == true) {
                throw CancellationException("Operation cancelled by user")
            }

            val newHeader: VaultFileHeader = if (targetMode == VaultStorageMode.ENCRYPTED) {
                val masterKey = if (credential.isNotBlank()) {
                    try { securityManager.deriveEncryptionKey(credential) } catch (_: Exception) { null }
                } else null
                FileInputStream(vaultFile).buffered().use { fis ->
                    val progressStream = ProgressInputStream(fis, sourceFileSize, onProgress, isCancelled)
                    vaultContainer.createEncryptedVaultFile(
                        sourceInputStream = progressStream,
                        destinationVaultFile = convertingFile,
                        passwordOrPin = credential,
                        masterKey = masterKey,
                        title = vaultEntity.title,
                        originalExtension = vaultEntity.originalExtension.ifBlank { "mp4" },
                        durationMs = vaultEntity.durationMs,
                        originalSize = sourceFileSize
                    )
                }
            } else {
                val ext = vaultEntity.originalExtension.ifBlank { "mp4" }
                val intermediateFile = File(tempConvDir, "conv_${vaultEntity.id}_${UUID.randomUUID()}.$ext")
                tempDecryptedFile = intermediateFile
                if (intermediateFile.exists()) intermediateFile.delete()

                val masterKey = if (credential.isNotBlank()) {
                    try { securityManager.deriveEncryptionKey(credential) } catch (_: Exception) { null }
                } else null
                vaultContainer.decryptVaultFile(vaultFile, intermediateFile, credential, masterKey = masterKey)

                if (isCancelled?.invoke() == true) {
                    throw CancellationException("Operation cancelled by user")
                }

                if (!intermediateFile.exists() || intermediateFile.length() == 0L) {
                    throw IOException("Decryption produced empty intermediate file")
                }

                val intermediateSize = intermediateFile.length()
                val header = FileInputStream(intermediateFile).buffered().use { fis ->
                    val progressStream = ProgressInputStream(fis, intermediateSize, onProgress, isCancelled)
                    vaultContainer.createUnencryptedVaultFile(
                        sourceInputStream = progressStream,
                        destinationVaultFile = convertingFile,
                        title = vaultEntity.title,
                        originalExtension = ext,
                        durationMs = vaultEntity.durationMs,
                        originalSize = intermediateSize
                    )
                }

                intermediateFile.delete()
                tempDecryptedFile = null
                header
            }

            if (isCancelled?.invoke() == true) {
                throw CancellationException("Operation cancelled by user")
            }

            if (!convertingFile.exists() || convertingFile.length() == 0L) {
                throw IOException("Converted file is missing or empty")
            }

            if (targetMode == VaultStorageMode.ENCRYPTED) {
                val masterKey = if (credential.isNotBlank()) {
                    try { securityManager.deriveEncryptionKey(credential) } catch (_: Exception) { null }
                } else null
                val verified = vaultContainer.verifyVaultFileIntegrity(convertingFile, credential, masterKey = masterKey)
                if (!verified) {
                    throw IOException("Integrity check failed on newly encrypted vault file")
                }
            }

            var renameSuccess = vaultFile.renameTo(backupFile)
            if (!renameSuccess) {
                vaultFile.copyTo(backupFile, overwrite = true)
                vaultFile.delete()
                renameSuccess = true
            }

            if (!convertingFile.renameTo(vaultFile)) {
                convertingFile.copyTo(vaultFile, overwrite = true)
                convertingFile.delete()
            }

            val updatedEntity = vaultEntity.copy(
                fileSize = vaultFile.length(),
                storageMode = targetMode,
                formatVersion = newHeader.formatVersion
            )

            try {
                vaultDao.insert(updatedEntity)
            } catch (dbEx: Exception) {
                if (backupFile.exists()) {
                    vaultFile.delete()
                    if (!backupFile.renameTo(vaultFile)) {
                        backupFile.copyTo(vaultFile, overwrite = true)
                        backupFile.delete()
                    }
                }
                throw dbEx
            }

            if (backupFile.exists()) {
                backupFile.delete()
            }
            val ext = vaultEntity.originalExtension.ifBlank { "mp4" }
            val cachedPlayback = File(tempPlaybackDirectory, "playback_${vaultEntity.id}_${vaultFile.nameWithoutExtension}.$ext")
            if (cachedPlayback.exists()) {
                cachedPlayback.delete()
            }

            Result.success(updatedEntity)
        } catch (e: Exception) {
            if (convertingFile.exists()) convertingFile.delete()
            tempDecryptedFile?.let {
                if (it.exists()) it.delete()
            }
            if (backupFile.exists() && (!vaultFile.exists() || vaultFile.length() == 0L)) {
                backupFile.renameTo(vaultFile)
            } else if (backupFile.exists()) {
                backupFile.delete()
            }
            Result.failure(e)
        }
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
                if (file.name.endsWith(".part") || file.name.endsWith(".migrating") || file.name.endsWith(".converting")) {
                    if (file.delete()) count++
                } else if (file.name.endsWith(".backup")) {
                    val primaryName = file.name.removeSuffix(".backup")
                    val primaryFile = File(vaultDirectory, primaryName)
                    if (!primaryFile.exists() || primaryFile.length() == 0L) {
                        if (file.renameTo(primaryFile)) {
                            count++
                        } else {
                            if (file.delete()) count++
                        }
                    } else {
                        if (file.delete()) count++
                    }
                }
            }
            tempPlaybackDirectory.listFiles()?.forEach { file ->
                if (file.name.endsWith(".part") || file.name.endsWith(".tmp")) {
                    if (file.delete()) count++
                }
            }
            val tempConvDir = context?.cacheDir?.let { File(it, "vault_conversion_temp") }
                ?: File(tempPlaybackDirectory, ".vault_conversion_temp")
            if (tempConvDir.exists()) {
                tempConvDir.listFiles()?.forEach { file ->
                    if (file.name != ".nomedia") {
                        if (file.delete()) count++
                    }
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
