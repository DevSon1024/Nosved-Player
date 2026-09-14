package com.devson.nvplayer.data.security

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.webkit.MimeTypeMap
import androidx.documentfile.provider.DocumentFile
import javax.crypto.spec.SecretKeySpec
import com.devson.nvplayer.data.database.VaultDao
import com.devson.nvplayer.data.database.VaultEntity
import com.devson.nvplayer.data.security.storage.FileVaultStorage
import com.devson.nvplayer.data.security.storage.SafVaultStorage
import com.devson.nvplayer.data.security.storage.VaultIndex
import com.devson.nvplayer.data.security.storage.VaultIndexEntry
import com.devson.nvplayer.data.security.storage.VaultIndexJson
import com.devson.nvplayer.data.security.storage.VaultStorage
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
    customSecurityManager: VaultSecurityManager? = null,
    customVaultStorage: VaultStorage? = null
) {

    val vaultStorage: VaultStorage = customVaultStorage
        ?: customSecurityManager?.vaultStorage
        ?: (if (customVaultDirectory != null) {
            FileVaultStorage(customVaultDirectory, ioDispatcher)
        } else if (context != null) {
            SafVaultStorage(context)
        } else {
            val docsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS)
            val vaultDir = File(docsDir, "NosvedPlayer/.vault_secure_media")
            FileVaultStorage(vaultDir, ioDispatcher)
        })

    val securityManager: VaultSecurityManager by lazy {
        customSecurityManager ?: VaultSecurityManager(
            context = context,
            customVaultDirectory = customVaultDirectory,
            customVaultStorage = vaultStorage
        )
    }

    val vaultDirectory: File by lazy {
        if (vaultStorage is FileVaultStorage) {
            vaultStorage.baseDirectory
        } else if (customVaultDirectory != null) {
            customVaultDirectory
        } else {
            val docsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS)
            val appFolder = File(docsDir, "NosvedPlayer")
            val nestedDir = File(appFolder, ".vault_secure_media")
            if (nestedDir.exists() && File(nestedDir, ".vault_config").exists()) {
                nestedDir
            } else {
                appFolder
            }
        }
    }

    val thumbsDirectory: File by lazy {
        val dir = customThumbsDirectory ?: (context?.filesDir?.let { File(it, "vault_thumbs") }
            ?: (vaultStorage as? FileVaultStorage)?.baseDirectory?.let { File(it, ".thumbs") }
            ?: File(System.getProperty("java.io.tmpdir"), "vault_thumbs"))
        if (!dir.exists()) dir.mkdirs()
        val nomedia = File(dir, ".nomedia")
        if (!nomedia.exists()) {
            try { nomedia.createNewFile() } catch (_: Exception) {}
        }
        dir
    }

    val tempPlaybackDirectory: File by lazy {
        val dir = customTempPlaybackDirectory ?: (context?.cacheDir?.let { File(it, "vault_playback_temp") }
            ?: (vaultStorage as? FileVaultStorage)?.baseDirectory?.let { File(it, ".playback_temp") }
            ?: File(System.getProperty("java.io.tmpdir"), "vault_playback_temp"))
        if (!dir.exists()) dir.mkdirs()
        val nomedia = File(dir, ".nomedia")
        if (!nomedia.exists()) {
            try { nomedia.createNewFile() } catch (_: Exception) {}
        }
        dir
    }

    val migrationManager: VaultMigrationManager by lazy {
        VaultMigrationManager(
            cacheDir = tempPlaybackDirectory.parentFile ?: File(tempPlaybackDirectory, ".cache"),
            vaultDao = vaultDao,
            vaultContainer = vaultContainer,
            vaultDirectory = vaultDirectory
        )
    }

    init {
        cleanPlaybackTemp()
        cleanOrphanedPartFiles()
    }

    internal suspend fun addOrUpdateIndexEntry(entry: VaultIndexEntry) {
        try {
            val currentJson = vaultStorage.readVaultIndex()
            val currentIndex = currentJson?.let { VaultIndexJson.fromJson(it) } ?: VaultIndex()
            val updatedItems = currentIndex.items.filter { it.id != entry.id && it.vltFilename != entry.vltFilename } + entry
            val newIndex = currentIndex.copy(items = updatedItems, lastUpdated = System.currentTimeMillis())
            vaultStorage.writeVaultIndex(VaultIndexJson.toJson(newIndex))
        } catch (_: Exception) {}
    }

    internal suspend fun removeIndexEntry(filenameOrId: String) {
        try {
            val currentJson = vaultStorage.readVaultIndex() ?: return
            val currentIndex = VaultIndexJson.fromJson(currentJson) ?: return
            val updatedItems = currentIndex.items.filter { it.id != filenameOrId && it.vltFilename != filenameOrId }
            val newIndex = currentIndex.copy(items = updatedItems, lastUpdated = System.currentTimeMillis())
            vaultStorage.writeVaultIndex(VaultIndexJson.toJson(newIndex))
        } catch (_: Exception) {}
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
        val targetFilename = "$fileId.vlt"
        val partFilename = "$fileId.vlt.part"
        val cleanTitle = title.ifBlank { "Protected Video" }
        val dateAdded = System.currentTimeMillis()
        val originalExt = originalExtension.trimStart('.').lowercase().ifBlank { "mp4" }

        try {
            val header = if (storageMode == VaultStorageMode.ENCRYPTED) {
                if (vaultCredential.isBlank()) {
                    return@withContext Result.failure(IllegalArgumentException("Vault credential is required for encrypted import"))
                }
                val masterKey = try { securityManager.deriveEncryptionKey(vaultCredential) } catch (_: Exception) { null }

                vaultStorage.openOutputStream(partFilename).use { outStream ->
                    vaultContainer.createEncryptedVaultStream(
                        sourceInputStream = sourceInputStream,
                        destinationOutputStream = outStream,
                        passwordOrPin = vaultCredential,
                        masterKey = masterKey,
                        title = cleanTitle,
                        originalExtension = originalExt,
                        durationMs = durationMs,
                        originalSize = originalSize
                    )
                }
            } else {
                vaultStorage.openOutputStream(partFilename).use { outStream ->
                    vaultContainer.createUnencryptedVaultStream(
                        sourceInputStream = sourceInputStream,
                        destinationOutputStream = outStream,
                        title = cleanTitle,
                        originalExtension = originalExt,
                        durationMs = durationMs,
                        originalSize = originalSize
                    )
                }
            }

            vaultStorage.renameFile(partFilename, targetFilename)

            var thumbPath: String? = null
            var finalDuration = durationMs
            try {
                val tempPlayback = getPlaybackFileInternal(targetFilename, fileId, vaultCredential)
                thumbPath = generateAndSaveThumbnail(tempPlayback, fileId)
                if (finalDuration <= 0L) {
                    finalDuration = extractDuration(tempPlayback)
                }
                releasePlaybackFile(tempPlayback)
            } catch (_: Exception) {}

            val resolvedSize = if (header.originalPlaintextSize > 0L) {
                header.originalPlaintextSize
            } else {
                vaultStorage.getFileLength(targetFilename)
            }

            val entity = VaultEntity(
                title = cleanTitle,
                originalUri = originalUri,
                vaultPath = File(vaultDirectory, targetFilename).absolutePath,
                thumbnailPath = thumbPath,
                fileSize = resolvedSize,
                durationMs = finalDuration,
                dateAdded = dateAdded,
                storageMode = header.storageMode,
                formatVersion = header.formatVersion,
                originalExtension = header.originalExtension
            )

            val insertedId = vaultDao.insert(entity)

            addOrUpdateIndexEntry(
                VaultIndexEntry(
                    id = fileId,
                    vltFilename = targetFilename,
                    title = cleanTitle,
                    originalExtension = header.originalExtension,
                    originalUri = originalUri,
                    fileSize = resolvedSize,
                    durationMs = finalDuration,
                    dateAdded = dateAdded,
                    storageMode = header.storageMode,
                    formatVersion = header.formatVersion
                )
            )

            Result.success(entity.copy(id = insertedId))
        } catch (e: Exception) {
            vaultStorage.deleteFile(partFilename)
            vaultStorage.deleteFile(targetFilename)
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
                originalUri = sourceFile.absolutePath,
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
        vaultCredential: String = "",
        originalPath: String? = null
    ): Result<VaultImportResult> = withContext(Dispatchers.IO) {
        try {
            val ctx = context
                ?: return@withContext Result.failure(IllegalStateException("Context is required for URI-based import"))
            val inputStream = ctx.contentResolver.openInputStream(sourceUri)
                ?: return@withContext Result.failure(FileNotFoundException("Cannot open stream for URI: $sourceUri"))

            val cleanTitle = title.ifBlank { "Protected Video" }
            val originalExt = resolveExtension(sourceUri, cleanTitle)

            var resolvedPath = originalPath?.trim().orEmpty()
            if (resolvedPath.isBlank()) {
                if (sourceUri.scheme == "file") {
                    resolvedPath = sourceUri.path ?: ""
                } else if (sourceUri.scheme == "content") {
                    try {
                        val proj = arrayOf(MediaStore.Video.Media.DATA)
                        ctx.contentResolver.query(sourceUri, proj, null, null, null)?.use { cursor ->
                            if (cursor.moveToFirst()) {
                                val idx = cursor.getColumnIndex(MediaStore.Video.Media.DATA)
                                if (idx != -1) {
                                    resolvedPath = cursor.getString(idx) ?: ""
                                }
                            }
                        }
                    } catch (_: Exception) {}
                }
            }
            val finalOriginalUri = if (resolvedPath.isNotBlank()) resolvedPath else sourceUri.toString()

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
                    originalUri = finalOriginalUri,
                    originalSize = originalSize
                )
            }

            result.fold(
                onSuccess = { entity ->
                    val explicitPath = resolvedPath.ifBlank { originalPath }
                    val pendingDeleteUri = removeOriginalSourceFile(sourceUri, explicitPath)
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

        val existingEntities = try {
            vaultDao.getAllVaultMedia().associateBy { File(it.vaultPath).name }
        } catch (_: Exception) {
            emptyMap()
        }

        // 1. Try reading the persistent .vault_index first
        val indexJson = vaultStorage.readVaultIndex()
        val index = indexJson?.let { VaultIndexJson.fromJson(it) }

        if (index != null && index.items.isNotEmpty()) {
            for (item in index.items) {
                if (existingEntities.containsKey(item.vltFilename)) {
                    continue
                }
                if (!vaultStorage.fileExists(item.vltFilename)) {
                    continue
                }

                val fileId = item.id
                val existingThumb = File(thumbsDirectory, "$fileId.jpg")
                val thumbPath = if (existingThumb.exists()) {
                    existingThumb.absolutePath
                } else {
                    try {
                        val tempPlay = getPlaybackFileInternal(item.vltFilename, fileId, credential)
                        val tp = generateAndSaveThumbnail(tempPlay, fileId)
                        releasePlaybackFile(tempPlay)
                        tp
                    } catch (_: Exception) {
                        null
                    }
                }

                val entity = VaultEntity(
                    title = item.title,
                    originalUri = item.originalUri,
                    vaultPath = File(vaultDirectory, item.vltFilename).absolutePath,
                    thumbnailPath = thumbPath,
                    fileSize = item.fileSize,
                    durationMs = item.durationMs,
                    dateAdded = item.dateAdded,
                    storageMode = item.storageMode,
                    formatVersion = item.formatVersion,
                    originalExtension = item.originalExtension
                )
                vaultDao.insert(entity)
                restoredCount++
            }
        }

        // 2. Scan physical .vlt files for any that were not in the index
        val vltFiles = vaultStorage.listVaultFiles()
        val currentEntities = try {
            vaultDao.getAllVaultMedia().associateBy { File(it.vaultPath).name }
        } catch (_: Exception) {
            emptyMap()
        }

        for (fileEntry in vltFiles) {
            if (currentEntities.containsKey(fileEntry.filename)) {
                continue
            }

            try {
                val header = try {
                    vaultStorage.openInputStream(fileEntry.filename).use { stream ->
                        vaultContainer.inspectVaultStream(stream)
                    }
                } catch (_: Exception) {
                    null
                }

                if (header == null) {
                    invalidCount++
                    continue
                }

                val fileId = fileEntry.id
                val existingThumb = File(thumbsDirectory, "$fileId.jpg")
                val thumbPath = if (existingThumb.exists()) {
                    existingThumb.absolutePath
                } else {
                    try {
                        val tempPlay = getPlaybackFileInternal(fileEntry.filename, fileId, credential)
                        val tp = generateAndSaveThumbnail(tempPlay, fileId)
                        releasePlaybackFile(tempPlay)
                        tp
                    } catch (_: Exception) {
                        null
                    }
                }

                val mode = header?.storageMode ?: VaultStorageMode.NONE
                val version = header?.formatVersion ?: VaultFileFormat.FORMAT_VERSION_V2
                val originalExt = header?.originalExtension?.ifBlank { "mp4" } ?: "mp4"
                val title = header?.title?.ifBlank { fileId } ?: fileId

                val resolvedSize = if (mode == VaultStorageMode.ENCRYPTED && header != null && header.originalPlaintextSize > 0L) {
                    header.originalPlaintextSize
                } else {
                    fileEntry.size
                }

                val entity = VaultEntity(
                    title = title,
                    originalUri = "",
                    vaultPath = File(vaultDirectory, fileEntry.filename).absolutePath,
                    thumbnailPath = thumbPath,
                    fileSize = resolvedSize,
                    durationMs = header?.durationMs ?: 0L,
                    dateAdded = fileEntry.lastModified,
                    storageMode = mode,
                    formatVersion = version,
                    originalExtension = originalExt
                )

                vaultDao.insert(entity)
                addOrUpdateIndexEntry(
                    VaultIndexEntry(
                        id = fileId,
                        vltFilename = fileEntry.filename,
                        title = title,
                        originalExtension = originalExt,
                        originalUri = "",
                        fileSize = resolvedSize,
                        durationMs = header?.durationMs ?: 0L,
                        dateAdded = fileEntry.lastModified,
                        storageMode = mode,
                        formatVersion = version
                    )
                )
                restoredCount++
            } catch (_: Exception) {
                invalidCount++
            }
        }

        RebuildDatabaseResult(restoredCount, invalidCount)
    }

    private fun resolveExtension(uri: Uri, title: String): String {
        val mime = context?.contentResolver?.getType(uri)
        if (!mime.isNullOrBlank()) {
            val ext = MimeTypeMap.getSingleton().getExtensionFromMimeType(mime)
            if (!ext.isNullOrBlank()) return ext.lowercase()
        }
        val pathExt = uri.lastPathSegment?.substringAfterLast('.', "")
        if (!pathExt.isNullOrBlank() && pathExt.length in 2..5) {
            return pathExt.lowercase()
        }
        val titleExt = title.substringAfterLast('.', "")
        if (titleExt.isNotBlank() && titleExt.length in 2..5 && titleExt != title) {
            return titleExt.lowercase()
        }
        return "mp4"
    }

    private fun removeOriginalSourceFile(sourceUri: Uri, explicitPath: String? = null): Uri? {
        val ctx = context ?: return null

        // 1. SAF Document URI deletion
        try {
            if (DocumentsContract.isDocumentUri(ctx, sourceUri)) {
                if (DocumentsContract.deleteDocument(ctx.contentResolver, sourceUri)) {
                    return null
                }
            }
        } catch (_: Exception) {}

        // 2. DocumentFile deletion for non-MediaStore content URIs
        try {
            if (sourceUri.scheme == "content" && !isMediaStoreUri(sourceUri)) {
                val docFile = DocumentFile.fromSingleUri(ctx, sourceUri)
                if (docFile?.exists() == true && docFile.delete()) {
                    return null
                }
            }
        } catch (_: Exception) {}

        // 3. Direct File deletion if physical path is known
        val filePath = explicitPath ?: getFilePathFromUri(sourceUri)
        if (!filePath.isNullOrBlank()) {
            try {
                val file = File(filePath)
                if (file.exists() && file.delete()) {
                    try {
                        ctx.contentResolver.delete(sourceUri, null, null)
                    } catch (_: Exception) {}
                    MediaScannerConnection.scanFile(ctx, arrayOf(filePath), null, null)
                    return null
                }
            } catch (_: Exception) {}
        }

        // 4. ContentResolver direct deletion (works if app has permission or created the media)
        try {
            val deleted = ctx.contentResolver.delete(sourceUri, null, null)
            if (deleted > 0) {
                if (!filePath.isNullOrBlank()) {
                    MediaScannerConnection.scanFile(ctx, arrayOf(filePath), null, null)
                }
                return null
            }
        } catch (e: SecurityException) {
            // Cannot delete directly without user consent on Android 11+
            if (isMediaStoreUri(sourceUri)) {
                return sourceUri
            }
        } catch (_: Exception) {}

        // 5. Direct file scheme deletion
        if (sourceUri.scheme == "file") {
            try {
                val file = File(sourceUri.path ?: "")
                if (file.exists() && file.delete()) {
                    return null
                }
            } catch (_: Exception) {}
        }

        return if (isMediaStoreUri(sourceUri)) sourceUri else null
    }

    private fun isMediaStoreUri(uri: Uri): Boolean {
        return uri.scheme == "content" && uri.authority == MediaStore.AUTHORITY
    }

    private fun getFilePathFromUri(uri: Uri): String? {
        val ctx = context ?: return null
        try {
            if (DocumentsContract.isDocumentUri(ctx, uri)) {
                val docId = DocumentsContract.getDocumentId(uri)
                if (docId.startsWith("raw:")) {
                    return docId.removePrefix("raw:")
                }
                val split = docId.split(":")
                if (split.size > 1) {
                    val type = split[0]
                    val relativePath = split[1]
                    if ("primary".equals(type, ignoreCase = true)) {
                        return "${Environment.getExternalStorageDirectory()}/$relativePath"
                    }
                }
            }
            val projection = arrayOf(MediaStore.MediaColumns.DATA)
            ctx.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
                val columnIndex = cursor.getColumnIndex(MediaStore.MediaColumns.DATA)
                if (columnIndex != -1 && cursor.moveToFirst()) {
                    val path = cursor.getString(columnIndex)
                    if (!path.isNullOrBlank() && File(path).exists()) {
                        return path
                    }
                }
            }
        } catch (_: Exception) {}
        return null
    }

    fun getPlaybackFile(vaultEntity: VaultEntity, credential: String = ""): File = runBlocking(ioDispatcher) {
        val filename = File(vaultEntity.vaultPath).name
        if (!vaultStorage.fileExists(filename)) {
            throw FileNotFoundException("Vault file not found: ${vaultEntity.vaultPath}")
        }
        val fileId = "${vaultEntity.id}_${filename.substringBeforeLast(".vlt")}"
        getPlaybackFileInternal(filename, fileId, credential)
    }

    internal fun getPlaybackFileInternal(filename: String, fileId: String, credential: String = ""): File {
        val header = try {
            runBlocking {
                vaultStorage.openInputStream(filename).use { stream ->
                    vaultContainer.inspectVaultStream(stream)
                }
            }
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
            vaultStorage.openInputStream(filename).use { inStream ->
                FileOutputStream(tempFile).use { outStream ->
                    vaultContainer.decryptVaultStream(inStream, outStream, credential, masterKey = masterKey)
                }
            }
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

    private fun isPublicExternalStorage(dir: File): Boolean {
        val path = dir.absolutePath.replace('\\', '/')
        val extRoot = Environment.getExternalStorageDirectory()?.absolutePath?.replace('\\', '/') ?: "/storage/emulated/0"
        return path.startsWith(extRoot, ignoreCase = true) ||
               path.startsWith("/storage/emulated/0", ignoreCase = true) ||
               path.startsWith("/sdcard", ignoreCase = true) ||
               path.contains("/Movies", ignoreCase = true) ||
               path.contains("/Download", ignoreCase = true) ||
               path.contains("/DCIM", ignoreCase = true)
    }

    private fun resolveMimeType(extension: String): String {
        val clean = extension.trimStart('.').lowercase()
        return MimeTypeMap.getSingleton().getMimeTypeFromExtension(clean)
            ?: when (clean) {
                "mkv" -> "video/x-matroska"
                "mp4" -> "video/mp4"
                "mov" -> "video/quicktime"
                "avi" -> "video/x-msvideo"
                "webm" -> "video/webm"
                "3gp" -> "video/3gpp"
                "ts" -> "video/mp2t"
                "flv" -> "video/x-flv"
                else -> "video/*"
            }
    }

    private suspend fun restoreViaMediaStore(
        context: Context,
        filename: String,
        baseName: String,
        ext: String,
        credential: String,
        masterKey: SecretKeySpec?,
        destinationDirectory: File,
        vaultEntity: VaultEntity
    ): Result<File> {
        val resolver = context.contentResolver
        val mimeType = resolveMimeType(ext)
        val destPath = destinationDirectory.absolutePath.replace('\\', '/')
        val extRoot = Environment.getExternalStorageDirectory()?.absolutePath?.replace('\\', '/') ?: "/storage/emulated/0"
        val relativePath = if (destPath.startsWith(extRoot, ignoreCase = true)) {
            val rel = destPath.removePrefix(extRoot).trimStart('/')
            if (rel.isNotEmpty()) {
                if (rel.endsWith("/")) rel else "$rel/"
            } else {
                "${Environment.DIRECTORY_MOVIES}/"
            }
        } else if (destPath.contains("Movies", ignoreCase = true)) {
            "${Environment.DIRECTORY_MOVIES}/"
        } else {
            "${Environment.DIRECTORY_MOVIES}/"
        }

        val displayName = "$baseName.$ext"
        val contentValues = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, displayName)
            put(MediaStore.Video.Media.MIME_TYPE, mimeType)
            put(MediaStore.Video.Media.RELATIVE_PATH, relativePath)
            put(MediaStore.Video.Media.IS_PENDING, 1)
        }

        val collection = MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        val destUri = runCatching {
            resolver.insert(collection, contentValues)
        }.getOrNull() ?: return Result.failure(IOException("Failed to insert MediaStore entry for $displayName in $relativePath"))

        try {
            resolver.openOutputStream(destUri)?.buffered(64 * 1024)?.use { outStream ->
                vaultStorage.openInputStream(filename).use { inStream ->
                    vaultContainer.decryptVaultStream(inStream, outStream, credential, masterKey = masterKey)
                }
            } ?: throw IOException("Cannot open output stream for MediaStore URI: $destUri")

            val finishValues = ContentValues().apply {
                put(MediaStore.Video.Media.IS_PENDING, 0)
            }
            resolver.update(destUri, finishValues, null, null)

            var resolvedFile: File? = null
            try {
                val projection = arrayOf(MediaStore.Video.Media.DATA)
                resolver.query(destUri, projection, null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val idx = cursor.getColumnIndex(MediaStore.Video.Media.DATA)
                        if (idx != -1) {
                            val path = cursor.getString(idx)
                            if (!path.isNullOrBlank()) {
                                resolvedFile = File(path)
                            }
                        }
                    }
                }
            } catch (_: Exception) {}

            val finalFile = resolvedFile ?: File(destinationDirectory, displayName)

            vaultStorage.deleteFile(filename)
            removeIndexEntry(filename)

            vaultEntity.thumbnailPath?.let {
                val thumb = File(it)
                if (thumb.exists()) thumb.delete()
            }

            val tempFile = File(tempPlaybackDirectory, "playback_${vaultEntity.id}_${filename.substringBeforeLast(".vlt")}.$ext")
            if (tempFile.exists()) tempFile.delete()

            vaultDao.delete(vaultEntity)

            try {
                MediaScannerConnection.scanFile(context, arrayOf(finalFile.absolutePath), arrayOf(mimeType), null)
            } catch (_: Exception) {}

            return Result.success(finalFile)
        } catch (e: Exception) {
            try { resolver.delete(destUri, null, null) } catch (_: Exception) {}
            return Result.failure(e)
        }
    }

    fun resolveRestoreDestination(vaultEntity: VaultEntity, defaultDir: File): File {
        val raw = vaultEntity.originalUri.trim()
        if (raw.isBlank() || raw.startsWith("content://", ignoreCase = true)) return defaultDir

        val path = if (raw.startsWith("file://", ignoreCase = true)) {
            try { Uri.parse(raw).path ?: "" } catch (_: Exception) { "" }
        } else {
            raw
        }

        if (path.isNotBlank()) {
            try {
                val originalFile = File(path)
                val originalParent = originalFile.parentFile
                if (originalParent != null && originalParent.exists() && originalParent.isDirectory) {
                    return originalParent
                }
            } catch (_: Exception) {}
        }
        return defaultDir
    }

    private suspend fun restoreVideoInternal(
        vaultEntity: VaultEntity,
        destinationDirectory: File,
        credential: String = ""
    ): Result<File> = withContext(ioDispatcher) {
        try {
            val filename = File(vaultEntity.vaultPath).name
            if (!vaultStorage.fileExists(filename)) {
                return@withContext Result.failure(FileNotFoundException("Vault file not found: ${vaultEntity.vaultPath}"))
            }

            val header = vaultStorage.openInputStream(filename).use { inStream ->
                vaultContainer.inspectVaultStream(inStream)
            }

            val ext = header.originalExtension.ifBlank { vaultEntity.originalExtension.ifBlank { "mp4" } }.trimStart('.').lowercase()
            var baseName = vaultEntity.title.ifBlank { header.title.ifBlank { "Restored_Video" } }
            if (baseName.endsWith(".$ext", ignoreCase = true)) {
                baseName = baseName.substring(0, baseName.length - ext.length - 1)
            }

            val masterKey = if (credential.isNotBlank()) {
                try { securityManager.deriveEncryptionKey(credential) } catch (_: Exception) { null }
            } else null

            // If context is available on Android Q+ and targeting public storage, restore via MediaStore to avoid EPERM
            if (context != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && isPublicExternalStorage(destinationDirectory)) {
                val mediaResult = restoreViaMediaStore(
                    context = context,
                    filename = filename,
                    baseName = baseName,
                    ext = ext,
                    credential = credential,
                    masterKey = masterKey,
                    destinationDirectory = destinationDirectory,
                    vaultEntity = vaultEntity
                )
                if (mediaResult.isSuccess) {
                    return@withContext mediaResult
                }
            }

            if (!destinationDirectory.exists()) {
                destinationDirectory.mkdirs()
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
                vaultStorage.openInputStream(filename).use { inStream ->
                    FileOutputStream(partFile).buffered(64 * 1024).use { outStream ->
                        vaultContainer.decryptVaultStream(inStream, outStream, credential, masterKey = masterKey)
                    }
                }

                if (!partFile.exists() || partFile.length() == 0L) {
                    throw IOException("Restoration failed: target file is empty")
                }

                if (!partFile.renameTo(targetFile)) {
                    partFile.copyTo(targetFile, overwrite = true)
                    partFile.delete()
                }

                vaultStorage.deleteFile(filename)
                removeIndexEntry(filename)

                vaultEntity.thumbnailPath?.let {
                    val thumb = File(it)
                    if (thumb.exists()) thumb.delete()
                }

                val tempFile = File(tempPlaybackDirectory, "playback_${vaultEntity.id}_${filename.substringBeforeLast(".vlt")}.$ext")
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

    suspend fun restoreVideoFromVault(
        vaultEntity: VaultEntity,
        destinationDirectory: File,
        credential: String = ""
    ): Result<File> = withContext(ioDispatcher) {
        val primaryResult = restoreVideoInternal(vaultEntity, destinationDirectory, credential)
        if (primaryResult.isSuccess) {
            return@withContext primaryResult
        }

        // If restoring to custom/original directory failed (e.g. Scoped Storage restrictions on hidden folders),
        // fallback to public Movies directory so the media is safely restored without loss.
        val defaultMoviesDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES)
        if (defaultMoviesDir != null && destinationDirectory.canonicalPath != defaultMoviesDir.canonicalPath) {
            val fallbackResult = restoreVideoInternal(vaultEntity, defaultMoviesDir, credential)
            if (fallbackResult.isSuccess) {
                return@withContext fallbackResult
            }
        }

        primaryResult
    }

    suspend fun deletePermanently(vaultEntity: VaultEntity): Result<Unit> = withContext(ioDispatcher) {
        try {
            val filename = File(vaultEntity.vaultPath).name
            vaultStorage.deleteFile(filename)
            removeIndexEntry(filename)

            val ext = vaultEntity.originalExtension.ifBlank { "mp4" }
            val tempFile = File(tempPlaybackDirectory, "playback_${vaultEntity.id}_${filename.substringBeforeLast(".vlt")}.$ext")
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
                    val ok = if (file.isDirectory) file.deleteRecursively() else file.delete()
                    if (!ok && file.exists()) {
                        remaining.add("playback_temp/${file.name}")
                    }
                }
            }
            val tempConvDir = context?.cacheDir?.let { File(it, "vault_conversion_temp") }
                ?: File(tempPlaybackDirectory, ".vault_conversion_temp")
            if (tempConvDir.exists()) {
                tempConvDir.listFiles()?.forEach { file ->
                    if (file.name != ".nomedia") {
                        val ok = if (file.isDirectory) file.deleteRecursively() else file.delete()
                        if (!ok && file.exists()) {
                            remaining.add("conversion_temp/${file.name}")
                        }
                    }
                }
            }
            val convDirInVault = File(vaultDirectory, "vault_conversion_temp")
            if (convDirInVault.exists()) {
                convDirInVault.deleteRecursively()
            }
            val dotConvDirInVault = File(vaultDirectory, ".vault_conversion_temp")
            if (dotConvDirInVault.exists()) {
                dotConvDirInVault.deleteRecursively()
            }
        } catch (_: Exception) {}

        val vltFiles = vaultStorage.listVaultFiles()
        deletedCount = 0
        failedCount = 0
        for (fileEntry in vltFiles) {
            val deleted = vaultStorage.deleteFile(fileEntry.filename)
            if (deleted) {
                deletedCount++
            } else {
                failedCount++
                remaining.add(fileEntry.filename)
            }
        }

        if (failedCount > 0) {
            return@withContext VaultDeletionResult(
                success = false,
                deletedMediaCount = deletedCount,
                failedMediaCount = failedCount,
                remainingFiles = remaining
            )
        }

        thumbsDirectory.listFiles()?.forEach { file ->
            if (file.name != ".nomedia") {
                val ok = if (file.isDirectory) file.deleteRecursively() else file.delete()
                if (!ok && file.exists()) {
                    remaining.add("thumbs/${file.name}")
                }
            }
        }

        try {
            securityManager?.deleteVaultMetadata()
            vaultStorage.deleteVaultConfig()
            vaultStorage.deleteFile(".vault_index")
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

        val filename = File(vaultEntity.vaultPath).name
        if (!vaultStorage.fileExists(filename)) {
            return@withContext Result.failure(FileNotFoundException("Vault file not found: ${vaultEntity.vaultPath}"))
        }

        if (credential.isBlank() || !securityManager.verifyVaultCredential(credential)) {
            return@withContext Result.failure(SecurityException("Invalid vault credentials"))
        }

        val convertingFilename = "$filename.converting"
        val backupFilename = "$filename.backup"

        try {
            if (isCancelled?.invoke() == true) {
                throw CancellationException("Operation cancelled by user")
            }

            val sourceFileSize = vaultStorage.getFileLength(filename)

            val newHeader: VaultFileHeader = if (targetMode == VaultStorageMode.ENCRYPTED) {
                val masterKey = if (credential.isNotBlank()) {
                    try { securityManager.deriveEncryptionKey(credential) } catch (_: Exception) { null }
                } else null

                vaultStorage.openInputStream(filename).use { inStream ->
                    val progressStream = ProgressInputStream(inStream, sourceFileSize, onProgress, isCancelled)
                    vaultStorage.openOutputStream(convertingFilename).use { outStream ->
                        vaultContainer.createEncryptedVaultStream(
                            sourceInputStream = progressStream,
                            destinationOutputStream = outStream,
                            passwordOrPin = credential,
                            masterKey = masterKey,
                            title = vaultEntity.title,
                            originalExtension = vaultEntity.originalExtension.ifBlank { "mp4" },
                            durationMs = vaultEntity.durationMs,
                            originalSize = sourceFileSize
                        )
                    }
                }
            } else {
                val tempConvDir = context?.cacheDir?.let { File(it, "vault_conversion_temp") }
                    ?: File(tempPlaybackDirectory, ".vault_conversion_temp")
                if (!tempConvDir.exists()) tempConvDir.mkdirs()
                val intermediateFile = File(tempConvDir, "conv_${vaultEntity.id}_${UUID.randomUUID()}.${vaultEntity.originalExtension.ifBlank { "mp4" }}")

                try {
                    val masterKey = if (credential.isNotBlank()) {
                        try { securityManager.deriveEncryptionKey(credential) } catch (_: Exception) { null }
                    } else null

                    vaultStorage.openInputStream(filename).use { inStream ->
                        FileOutputStream(intermediateFile).use { outStream ->
                            vaultContainer.decryptVaultStream(inStream, outStream, credential, masterKey = masterKey)
                        }
                    }

                    if (isCancelled?.invoke() == true) {
                        throw CancellationException("Operation cancelled by user")
                    }

                    val intermediateSize = intermediateFile.length()
                    val header = FileInputStream(intermediateFile).buffered().use { fis ->
                        val progressStream = ProgressInputStream(fis, intermediateSize, onProgress, isCancelled)
                        vaultStorage.openOutputStream(convertingFilename).use { outStream ->
                            vaultContainer.createUnencryptedVaultStream(
                                sourceInputStream = progressStream,
                                destinationOutputStream = outStream,
                                title = vaultEntity.title,
                                originalExtension = vaultEntity.originalExtension.ifBlank { "mp4" },
                                durationMs = vaultEntity.durationMs,
                                originalSize = intermediateSize
                            )
                        }
                    }
                    header
                } finally {
                    if (intermediateFile.exists()) intermediateFile.delete()
                }
            }

            if (isCancelled?.invoke() == true) {
                throw CancellationException("Operation cancelled by user")
            }

            // Backup original file before swapping
            vaultStorage.renameFile(filename, backupFilename)
            vaultStorage.renameFile(convertingFilename, filename)

            val updatedEntity = vaultEntity.copy(
                fileSize = vaultStorage.getFileLength(filename),
                storageMode = targetMode,
                formatVersion = newHeader.formatVersion
            )

            try {
                vaultDao.update(updatedEntity)
            } catch (dbEx: Exception) {
                vaultStorage.renameFile(backupFilename, filename)
                throw dbEx
            }

            vaultStorage.deleteFile(backupFilename)

            addOrUpdateIndexEntry(
                VaultIndexEntry(
                    id = filename.substringBeforeLast(".vlt"),
                    vltFilename = filename,
                    title = updatedEntity.title,
                    originalExtension = updatedEntity.originalExtension,
                    originalUri = updatedEntity.originalUri,
                    fileSize = updatedEntity.fileSize,
                    durationMs = updatedEntity.durationMs,
                    dateAdded = updatedEntity.dateAdded,
                    storageMode = updatedEntity.storageMode,
                    formatVersion = updatedEntity.formatVersion
                )
            )

            val ext = vaultEntity.originalExtension.ifBlank { "mp4" }
            val cachedPlayback = File(tempPlaybackDirectory, "playback_${vaultEntity.id}_${filename.substringBeforeLast(".vlt")}.$ext")
            if (cachedPlayback.exists()) {
                cachedPlayback.delete()
            }

            Result.success(updatedEntity)
        } catch (e: Exception) {
            vaultStorage.deleteFile(convertingFilename)
            if (!vaultStorage.fileExists(filename) && vaultStorage.fileExists(backupFilename)) {
                vaultStorage.renameFile(backupFilename, filename)
            } else {
                vaultStorage.deleteFile(backupFilename)
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

            // Clean vaultDirectory if accessible
            if (vaultDirectory.exists()) {
                vaultDirectory.listFiles()?.forEach { file ->
                    val name = file.name
                    if (name.endsWith(".part") || name.endsWith(".tmp") || name.endsWith(".converting")) {
                        if (file.delete()) count++
                    } else if (name.endsWith(".backup")) {
                        val primaryName = name.removeSuffix(".backup")
                        val primaryFile = File(vaultDirectory, primaryName)
                        if (!primaryFile.exists()) {
                            if (file.renameTo(primaryFile)) {
                                count++
                            } else {
                                try {
                                    file.copyTo(primaryFile, overwrite = true)
                                    file.delete()
                                    count++
                                } catch (_: Exception) {}
                            }
                        } else {
                            if (file.delete()) count++
                        }
                    }
                }
            }

            // Also clean via vaultStorage for non-file storage (SAF)
            if (vaultStorage !is FileVaultStorage) {
                runBlocking {
                    try {
                        val entries = vaultStorage.listVaultFiles()
                        for (entry in entries) {
                            val name = entry.filename
                            if (name.endsWith(".part") || name.endsWith(".tmp") || name.endsWith(".converting")) {
                                if (vaultStorage.deleteFile(name)) count++
                            } else if (name.endsWith(".backup")) {
                                val primaryName = name.removeSuffix(".backup")
                                if (!vaultStorage.fileExists(primaryName)) {
                                    try {
                                        val backupIn = vaultStorage.openInputStream(name)
                                        val primaryOut = vaultStorage.openOutputStream(primaryName)
                                        backupIn.use { input ->
                                            primaryOut.use { output ->
                                                input.copyTo(output)
                                            }
                                        }
                                        vaultStorage.deleteFile(name)
                                        count++
                                    } catch (_: Exception) {}
                                } else {
                                    if (vaultStorage.deleteFile(name)) count++
                                }
                            }
                        }
                    } catch (_: Exception) {}
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
