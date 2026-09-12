package com.devson.nvplayer.data.security.storage

import com.devson.nvplayer.data.security.VaultMetadataJson
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream

/**
 * File-based implementation of VaultStorage.
 * Used for JVM unit tests, custom filesystem directories, and fallback operations.
 */
class FileVaultStorage(
    val baseDirectory: File,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : VaultStorage {

    init {
        if (!baseDirectory.exists()) {
            baseDirectory.mkdirs()
        }
        val nomedia = File(baseDirectory, ".nomedia")
        if (!nomedia.exists()) {
            try { nomedia.createNewFile() } catch (_: Exception) {}
        }
    }

    override fun isStorageAccessible(): Boolean {
        return try {
            baseDirectory.exists() && baseDirectory.canRead() && baseDirectory.canWrite()
        } catch (_: Exception) {
            false
        }
    }

    override fun getStorageLocationDescription(): String {
        return baseDirectory.absolutePath
    }

    override suspend fun hasVaultConfig(): Boolean = withContext(ioDispatcher) {
        val file = File(baseDirectory, ".vault_config")
        file.exists() && file.length() > 0L
    }

    override suspend fun readVaultConfig(): String? = withContext(ioDispatcher) {
        val file = File(baseDirectory, ".vault_config")
        if (!file.exists() || file.length() == 0L) return@withContext null
        try {
            file.readText(Charsets.UTF_8)
        } catch (_: Exception) {
            null
        }
    }

    override suspend fun writeVaultConfig(content: String): Boolean = withContext(ioDispatcher) {
        val configFile = File(baseDirectory, ".vault_config")
        val tempFile = File(baseDirectory, ".vault_config.tmp")
        try {
            tempFile.writeText(content, Charsets.UTF_8)

            // Verify content is valid JSON metadata before replacing
            val verified = VaultMetadataJson.fromJson(tempFile.readText(Charsets.UTF_8))
            if (verified == null) {
                if (tempFile.exists()) tempFile.delete()
                return@withContext false
            }

            if (configFile.exists()) {
                configFile.delete()
            }
            if (!tempFile.renameTo(configFile)) {
                FileOutputStream(configFile, false).use { out ->
                    tempFile.inputStream().use { input ->
                        input.copyTo(out)
                    }
                    out.flush()
                }
                tempFile.delete()
            }
            true
        } catch (e: Exception) {
            try {
                FileOutputStream(configFile, false).use { out ->
                    out.write(content.toByteArray(Charsets.UTF_8))
                    out.flush()
                }
                true
            } catch (_: Exception) {
                false
            } finally {
                if (tempFile.exists()) {
                    try { tempFile.delete() } catch (_: Exception) {}
                }
            }
        }
    }

    override suspend fun deleteVaultConfig(): Boolean = withContext(ioDispatcher) {
        val file = File(baseDirectory, ".vault_config")
        if (file.exists()) {
            file.delete()
        } else {
            true
        }
    }

    override suspend fun hasVaultIndex(): Boolean = withContext(ioDispatcher) {
        val file = File(baseDirectory, ".vault_index")
        file.exists() && file.length() > 0L
    }

    override suspend fun readVaultIndex(): String? = withContext(ioDispatcher) {
        val file = File(baseDirectory, ".vault_index")
        if (!file.exists() || file.length() == 0L) return@withContext null
        try {
            file.readText(Charsets.UTF_8)
        } catch (_: Exception) {
            null
        }
    }

    override suspend fun writeVaultIndex(content: String): Boolean = withContext(ioDispatcher) {
        val indexFile = File(baseDirectory, ".vault_index")
        val tempFile = File(baseDirectory, ".vault_index.tmp")
        try {
            tempFile.writeText(content, Charsets.UTF_8)
            val verified = VaultIndexJson.fromJson(tempFile.readText(Charsets.UTF_8))
            if (verified == null) {
                if (tempFile.exists()) tempFile.delete()
                return@withContext false
            }

            if (indexFile.exists()) {
                indexFile.delete()
            }
            if (!tempFile.renameTo(indexFile)) {
                FileOutputStream(indexFile, false).use { out ->
                    tempFile.inputStream().use { input ->
                        input.copyTo(out)
                    }
                    out.flush()
                }
                tempFile.delete()
            }
            true
        } catch (e: Exception) {
            try {
                FileOutputStream(indexFile, false).use { out ->
                    out.write(content.toByteArray(Charsets.UTF_8))
                    out.flush()
                }
                true
            } catch (_: Exception) {
                false
            } finally {
                if (tempFile.exists()) {
                    try { tempFile.delete() } catch (_: Exception) {}
                }
            }
        }
    }

    override suspend fun listVaultFiles(): List<VaultFileEntry> = withContext(ioDispatcher) {
        val files = baseDirectory.listFiles { file -> file.isFile && file.extension == "vlt" } ?: emptyArray()
        files.map { file ->
            VaultFileEntry(
                id = file.nameWithoutExtension,
                filename = file.name,
                size = file.length(),
                lastModified = file.lastModified(),
                uriString = file.toURI().toString()
            )
        }
    }

    override suspend fun fileExists(filename: String): Boolean = withContext(ioDispatcher) {
        File(baseDirectory, filename).exists()
    }

    override suspend fun getFileLength(filename: String): Long = withContext(ioDispatcher) {
        File(baseDirectory, filename).length()
    }

    override suspend fun openInputStream(filename: String): InputStream = withContext(ioDispatcher) {
        FileInputStream(File(baseDirectory, filename))
    }

    override suspend fun openOutputStream(filename: String): OutputStream = withContext(ioDispatcher) {
        FileOutputStream(File(baseDirectory, filename), false)
    }

    override suspend fun deleteFile(filename: String): Boolean = withContext(ioDispatcher) {
        val file = File(baseDirectory, filename)
        if (file.exists()) file.delete() else true
    }

    override suspend fun renameFile(oldFilename: String, newFilename: String): Boolean = withContext(ioDispatcher) {
        val source = File(baseDirectory, oldFilename)
        val target = File(baseDirectory, newFilename)
        if (!source.exists()) return@withContext false
        if (target.exists()) target.delete()
        if (source.renameTo(target)) {
            true
        } else {
            try {
                FileOutputStream(target, false).use { out ->
                    FileInputStream(source).use { input ->
                        input.copyTo(out)
                    }
                    out.flush()
                }
                source.delete()
                true
            } catch (_: Exception) {
                false
            }
        }
    }

    override suspend fun clearVault(): Boolean = withContext(ioDispatcher) {
        var allSuccess = true
        baseDirectory.listFiles()?.forEach { file ->
            if (file.name != ".nomedia") {
                val ok = if (file.isDirectory) file.deleteRecursively() else file.delete()
                if (!ok) {
                    allSuccess = false
                }
            }
        }
        allSuccess
    }
}
