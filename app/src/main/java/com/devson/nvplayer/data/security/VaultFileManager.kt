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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.util.UUID

data class VaultImportResult(
    val entity: VaultEntity,
    val pendingDeleteUri: Uri? = null
)

class VaultFileManager(
    private val context: Context,
    private val vaultDao: VaultDao,
    val vaultContainer: VaultContainer = DefaultVaultContainer()
) {

    val vaultDirectory: File by lazy {
        val docsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS)
        val vaultDir = File(docsDir, "NosvedPlayer/.vault_secure_media")
        if (!vaultDir.exists()) {
            vaultDir.mkdirs()
        }
        val nomedia = File(vaultDir, ".nomedia")
        if (!nomedia.exists()) {
            try { nomedia.createNewFile() } catch (_: Exception) {}
        }
        val legacyInternal = File(context.filesDir, "vault_secure_media")
        if (legacyInternal.exists() && legacyInternal.isDirectory) {
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
        val dir = File(vaultDirectory, ".thumbs")
        if (!dir.exists()) dir.mkdirs()
        val nomedia = File(dir, ".nomedia")
        if (!nomedia.exists()) {
            try { nomedia.createNewFile() } catch (_: Exception) {}
        }
        dir
    }

    val tempPlaybackDirectory: File by lazy {
        val dir = File(context.cacheDir, "vault_playback_temp")
        if (!dir.exists()) dir.mkdirs()
        val nomedia = File(dir, ".nomedia")
        if (!nomedia.exists()) {
            try { nomedia.createNewFile() } catch (_: Exception) {}
        }
        dir
    }

    suspend fun importVideoToVault(
        sourceUri: Uri,
        title: String,
        durationMs: Long = 0L,
        storageMode: VaultStorageMode = VaultStorageMode.NONE,
        vaultCredential: String = ""
    ): Result<VaultImportResult> = withContext(Dispatchers.IO) {
        try {
            val fileId = UUID.randomUUID().toString()
            val destFile = File(vaultDirectory, "$fileId.vlt")

            val inputStream: InputStream = context.contentResolver.openInputStream(sourceUri)
                ?: return@withContext Result.failure(Exception("Cannot open stream for URI: $sourceUri"))

            val cleanTitle = title.ifBlank { "Protected Video" }
            val originalExt = resolveExtension(sourceUri, cleanTitle)
            val dateAdded = System.currentTimeMillis()

            val header = inputStream.use { input ->
                if (storageMode == VaultStorageMode.ENCRYPTED) {
                    vaultContainer.createEncryptedVaultFile(
                        sourceInputStream = input,
                        destinationVaultFile = destFile,
                        passwordOrPin = vaultCredential,
                        title = cleanTitle,
                        originalExtension = originalExt,
                        durationMs = durationMs
                    )
                } else {
                    vaultContainer.createUnencryptedVaultFile(
                        sourceInputStream = input,
                        destinationVaultFile = destFile,
                        title = cleanTitle,
                        originalExtension = originalExt,
                        durationMs = durationMs
                    )
                }
            }

            val tempPlayback = getPlaybackFileInternal(destFile, fileId, vaultCredential)
            val thumbPath = generateAndSaveThumbnail(tempPlayback, fileId)
            val finalDuration = if (durationMs > 0) {
                durationMs
            } else {
                extractDuration(tempPlayback)
            }
            tempPlayback.delete()

            val entity = VaultEntity(
                title = cleanTitle,
                originalUri = sourceUri.toString(),
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
            val pendingDeleteUri = removeOriginalSourceFile(sourceUri)

            Result.success(VaultImportResult(entity.copy(id = insertedId), pendingDeleteUri))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun rebuildDatabaseFromStorage(credential: String = ""): Int = withContext(Dispatchers.IO) {
        var restoredCount = 0
        try {
            val vltFiles = vaultDirectory.listFiles { file -> file.extension == "vlt" } ?: return@withContext 0
            val existingEntities = vaultDao.getAllVaultMedia().associateBy { File(it.vaultPath).name }

            for (file in vltFiles) {
                if (existingEntities.containsKey(file.name)) {
                    continue
                }

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
                        tempPlay.delete()
                        tp
                    } catch (_: Exception) {
                        null
                    }
                }

                val entity = VaultEntity(
                    title = header?.title?.ifBlank { file.nameWithoutExtension } ?: file.nameWithoutExtension,
                    originalUri = "",
                    vaultPath = file.absolutePath,
                    thumbnailPath = thumbPath,
                    fileSize = file.length(),
                    durationMs = header?.durationMs ?: 0L,
                    dateAdded = header?.dateAdded?.takeIf { it > 0 } ?: file.lastModified(),
                    storageMode = header?.storageMode ?: VaultStorageMode.NONE,
                    formatVersion = header?.formatVersion ?: 2,
                    originalExtension = header?.originalExtension ?: "mp4"
                )

                vaultDao.insert(entity)
                restoredCount++
            }
        } catch (_: Exception) {}
        restoredCount
    }

    private fun resolveExtension(sourceUri: Uri, title: String): String {
        val fromTitle = title.substringAfterLast('.', "").trim().lowercase()
        if (fromTitle.isNotEmpty() && fromTitle.length in 2..5 && fromTitle.all { it.isLetterOrDigit() }) {
            return fromTitle
        }
        val mime = context.contentResolver.getType(sourceUri)
        if (mime != null) {
            val ext = MimeTypeMap.getSingleton().getExtensionFromMimeType(mime)
            if (!ext.isNullOrBlank()) return ext.lowercase()
        }
        return "mp4"
    }

    private fun removeOriginalSourceFile(sourceUri: Uri): Uri? {
        var successfullyDeleted = false

        try {
            if (DocumentsContract.isDocumentUri(context, sourceUri)) {
                successfullyDeleted = DocumentsContract.deleteDocument(context.contentResolver, sourceUri)
            }
        } catch (_: Exception) {}

        if (!successfullyDeleted) {
            try {
                val deletedRows = context.contentResolver.delete(sourceUri, null, null)
                if (deletedRows > 0) successfullyDeleted = true
            } catch (_: Exception) {}
        }

        var originalPath: String? = null
        try {
            val cursor = context.contentResolver.query(
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
                    context,
                    arrayOf(originalPath),
                    null,
                    null
                )
            } catch (_: Exception) {}
        }

        if (successfullyDeleted) {
            try {
                context.contentResolver.notifyChange(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, null)
            } catch (_: Exception) {}
            return null
        }

        return resolveMediaStoreUri(sourceUri, originalPath)
    }

    private fun resolveMediaStoreUri(sourceUri: Uri, knownPath: String?): Uri? {
        if (sourceUri.authority == "media") {
            return sourceUri
        }
        val pathToQuery = knownPath ?: try {
            var p: String? = null
            context.contentResolver.query(
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
                context.contentResolver.query(
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

    fun getPlaybackFile(vaultEntity: VaultEntity, credential: String = ""): File {
        val vaultFile = File(vaultEntity.vaultPath)
        val fileId = vaultFile.nameWithoutExtension
        return getPlaybackFileInternal(vaultFile, fileId, credential)
    }

    private fun getPlaybackFileInternal(vaultFile: File, fileId: String, credential: String = ""): File {
        val header = try {
            runBlocking { vaultContainer.inspectVaultFile(vaultFile) }
        } catch (_: Exception) {
            null
        }

        val ext = header?.originalExtension ?: "mp4"
        val tempFile = File(tempPlaybackDirectory, "$fileId.$ext")
        if (tempFile.exists() && tempFile.length() > 0) {
            return tempFile
        }

        runBlocking {
            vaultContainer.decryptVaultFile(vaultFile, tempFile, credential)
        }
        return tempFile
    }

    suspend fun restoreVideoFromVault(
        vaultEntity: VaultEntity,
        destinationDirectory: File,
        credential: String = ""
    ): Result<File> = withContext(Dispatchers.IO) {
        try {
            val vaultFile = File(vaultEntity.vaultPath)
            if (!vaultFile.exists()) {
                return@withContext Result.failure(Exception("Vault file not found: ${vaultEntity.vaultPath}"))
            }

            if (!destinationDirectory.exists()) {
                destinationDirectory.mkdirs()
            }

            var destFileName = vaultEntity.title
            if (!destFileName.contains('.')) {
                val ext = vaultEntity.originalExtension.ifBlank { "mp4" }
                destFileName = "$destFileName.$ext"
            }
            val restoredFile = File(destinationDirectory, destFileName)

            vaultContainer.decryptVaultFile(vaultFile, restoredFile, credential)

            vaultFile.delete()
            vaultEntity.thumbnailPath?.let { File(it).delete() }
            vaultDao.delete(vaultEntity)

            MediaScannerConnection.scanFile(
                context,
                arrayOf(restoredFile.absolutePath),
                null,
                null
            )

            Result.success(restoredFile)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun deletePermanently(vaultEntity: VaultEntity): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val vaultFile = File(vaultEntity.vaultPath)
            if (vaultFile.exists()) {
                vaultFile.delete()
            }
            val ext = vaultEntity.originalExtension.ifBlank { "mp4" }
            val tempPlayback = File(tempPlaybackDirectory, "${vaultFile.nameWithoutExtension}.$ext")
            if (tempPlayback.exists()) tempPlayback.delete()

            vaultEntity.thumbnailPath?.let {
                val thumbFile = File(it)
                if (thumbFile.exists()) thumbFile.delete()
            }
            vaultDao.delete(vaultEntity)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun cleanPlaybackTemp() {
        try {
            tempPlaybackDirectory.listFiles()?.forEach { file ->
                if (file.name != ".nomedia") {
                    file.delete()
                }
            }
        } catch (_: Exception) {}
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
