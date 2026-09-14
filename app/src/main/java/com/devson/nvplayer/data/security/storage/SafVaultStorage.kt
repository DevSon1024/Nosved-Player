package com.devson.nvplayer.data.security.storage

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Environment
import androidx.documentfile.provider.DocumentFile
import com.devson.nvplayer.data.security.VaultMetadataJson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream

/**
 * Storage Access Framework (SAF) implementation of VaultStorage.
 * Manages persistent user storage in Documents/NosvedPlayer/.vault_secure_media
 * surviving app uninstall, reinstalls, and process restarts.
 */
class SafVaultStorage(
    private val context: Context,
    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
) : VaultStorage {

    companion object {
        const val PREFS_NAME = "vault_saf_prefs"
        const val KEY_TREE_URI = "vault_tree_uri"
        const val VAULT_FOLDER_NAME = ".vault_secure_media"
        const val CONFIG_FILENAME = ".vault_config"
        const val CONFIG_TMP_FILENAME = ".vault_config.tmp"
        const val INDEX_FILENAME = ".vault_index"
        const val INDEX_TMP_FILENAME = ".vault_index.tmp"
        const val NOMEDIA_FILENAME = ".nomedia"
    }

    fun getSavedTreeUri(): Uri? {
        val uriStr = prefs.getString(KEY_TREE_URI, null) ?: return null
        return try {
            Uri.parse(uriStr)
        } catch (_: Exception) {
            null
        }
    }

    fun setTreeUri(treeUri: Uri): Boolean {
        return try {
            val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            try {
                context.contentResolver.takePersistableUriPermission(treeUri, flags)
            } catch (_: SecurityException) {
                // If permission cannot be persisted, proceed if accessible
            }
            val rootDoc = DocumentFile.fromTreeUri(context, treeUri) ?: return false
            if (!rootDoc.exists() || !rootDoc.canRead()) return false
            prefs.edit().putString(KEY_TREE_URI, treeUri.toString()).commit()
            true
        } catch (_: Exception) {
            false
        }
    }

    fun clearSavedTreeUri() {
        prefs.edit().remove(KEY_TREE_URI).commit()
    }

    /**
     * Checks if the physical NosvedPlayer vault folder and .vault_config exist in Documents.
     * Used on fresh installs to detect if an existing vault from a prior install is present in Documents.
     */
    fun hasPhysicalVaultFolder(): Boolean {
        return try {
            val docsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS)
            val appFolder = File(docsDir, "NosvedPlayer")
            if (!appFolder.exists() || !appFolder.isDirectory) return false
            val directConfig = File(appFolder, CONFIG_FILENAME)
            val vaultFolder = File(appFolder, VAULT_FOLDER_NAME)
            val nestedConfig = File(vaultFolder, CONFIG_FILENAME)
            (directConfig.exists() && directConfig.length() > 0L) || (nestedConfig.exists() && nestedConfig.length() > 0L)
        } catch (_: Exception) {
            false
        }
    }

    fun getPhysicalVaultLocationDescription(): String {
        return "Documents/NosvedPlayer"
    }

    /**
     * Tests whether Documents/NosvedPlayer/.vault_secure_media exists and is directly writable.
     * Never creates folders during probing to avoid phantom folder creation.
     */
    fun canWriteDirectly(): Boolean {
        return try {
            val docsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS)
            val appFolder = File(docsDir, "NosvedPlayer")
            if (!appFolder.exists() || !appFolder.isDirectory) return false
            val vaultFolder = File(appFolder, VAULT_FOLDER_NAME)
            val targetFolder = if (vaultFolder.exists() && vaultFolder.isDirectory) vaultFolder else appFolder
            targetFolder.canRead() && targetFolder.canWrite()
        } catch (_: Exception) {
            false
        }
    }

    override fun isStorageAccessible(): Boolean {
        val uri = getSavedTreeUri()
        if (uri != null) {
            return try {
                val vaultDoc = getVaultDirectoryDocument()
                vaultDoc != null && vaultDoc.exists() && vaultDoc.canRead() && vaultDoc.canWrite()
            } catch (_: Exception) {
                false
            }
        }
        return canWriteDirectly()
    }

    override fun getStorageLocationDescription(): String {
        val vaultDoc = getVaultDirectoryDocument()
        if (vaultDoc != null && vaultDoc.exists()) {
            return "Documents/NosvedPlayer"
        }
        val uri = getSavedTreeUri()
        return uri?.toString() ?: "Storage not connected"
    }

    private fun getVaultDirectoryDocument(): DocumentFile? {
        return findOrCreateVaultDirectory(createIfMissing = false)
    }

    private fun ensureVaultDirectory(): DocumentFile? {
        return findOrCreateVaultDirectory(createIfMissing = true)
    }

    private fun ensureNoMedia(vaultDoc: DocumentFile) {
        if (vaultDoc.findFile(NOMEDIA_FILENAME) == null) {
            try {
                vaultDoc.createFile("application/octet-stream", NOMEDIA_FILENAME)
            } catch (_: Exception) {}
        }
    }

    private fun openInputStreamForDoc(doc: DocumentFile): InputStream? {
        return try {
            if (doc.uri.scheme == "file") {
                val path = doc.uri.path
                if (path != null) FileInputStream(File(path)) else context.contentResolver.openInputStream(doc.uri)
            } else {
                context.contentResolver.openInputStream(doc.uri)
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun openOutputStreamForDoc(doc: DocumentFile, mode: String = "wt"): OutputStream? {
        return try {
            if (doc.uri.scheme == "file") {
                val path = doc.uri.path
                if (path != null) FileOutputStream(File(path)) else context.contentResolver.openOutputStream(doc.uri, mode)
            } else {
                context.contentResolver.openOutputStream(doc.uri, mode)
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun findOrCreateVaultDirectory(createIfMissing: Boolean): DocumentFile? {
        val treeUri = getSavedTreeUri()
        if (treeUri == null) {
            if (canWriteDirectly()) {
                try {
                    val docsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS)
                    val appFolder = File(docsDir, "NosvedPlayer")
                    val vaultFolder = File(appFolder, VAULT_FOLDER_NAME)
                    if (vaultFolder.exists() && File(vaultFolder, CONFIG_FILENAME).exists()) {
                        return DocumentFile.fromFile(vaultFolder)
                    } else if (appFolder.exists()) {
                        return DocumentFile.fromFile(appFolder)
                    }
                } catch (_: Exception) {}
            }
            return null
        }
        return try {
            val rootDoc = DocumentFile.fromTreeUri(context, treeUri) ?: return null
            if (!rootDoc.exists() || !rootDoc.canRead()) return null

            // 1. If root itself is NosvedPlayer or .vault_secure_media
            if (rootDoc.name?.equals("NosvedPlayer", ignoreCase = true) == true || rootDoc.name == VAULT_FOLDER_NAME) {
                val vault = rootDoc.findFile(VAULT_FOLDER_NAME)
                if (vault != null && vault.exists() && vault.findFile(CONFIG_FILENAME) != null) {
                    if (createIfMissing) ensureNoMedia(vault)
                    return vault
                }
                if (rootDoc.findFile(CONFIG_FILENAME) != null) {
                    if (createIfMissing) ensureNoMedia(rootDoc)
                    return rootDoc
                }
                if (vault != null && vault.exists()) {
                    if (createIfMissing) ensureNoMedia(vault)
                    return vault
                }
                if (createIfMissing) ensureNoMedia(rootDoc)
                return rootDoc
            }

            // 2. If root is Documents or similar directory containing NosvedPlayer folder
            val appFolder = rootDoc.findFile("NosvedPlayer")
            if (appFolder != null && appFolder.isDirectory) {
                val nestedVault = appFolder.findFile(VAULT_FOLDER_NAME)
                if (nestedVault != null && nestedVault.exists() && nestedVault.findFile(CONFIG_FILENAME) != null) {
                    if (createIfMissing) ensureNoMedia(nestedVault)
                    return nestedVault
                }
                if (appFolder.findFile(CONFIG_FILENAME) != null) {
                    if (createIfMissing) ensureNoMedia(appFolder)
                    return appFolder
                }
                if (nestedVault != null && nestedVault.exists()) {
                    if (createIfMissing) ensureNoMedia(nestedVault)
                    return nestedVault
                }
                if (createIfMissing) ensureNoMedia(appFolder)
                return appFolder
            }

            // 3. Only if createIfMissing is true (explicit new vault creation)
            if (createIfMissing) {
                val targetAppFolder = rootDoc.findFile("NosvedPlayer")
                    ?: rootDoc.createDirectory("NosvedPlayer")
                if (targetAppFolder != null && targetAppFolder.exists()) {
                    ensureNoMedia(targetAppFolder)
                    return targetAppFolder
                }
                ensureNoMedia(rootDoc)
                return rootDoc
            }

            null
        } catch (_: Exception) {
            null
        }
    }

    override suspend fun hasVaultConfig(): Boolean = withContext(Dispatchers.IO) {
        val vaultDoc = getVaultDirectoryDocument() ?: return@withContext false
        val file = vaultDoc.findFile(CONFIG_FILENAME)
        file != null && file.exists() && file.length() > 0L
    }

    override suspend fun readVaultConfig(): String? = withContext(Dispatchers.IO) {
        val vaultDoc = getVaultDirectoryDocument() ?: return@withContext null
        val file = vaultDoc.findFile(CONFIG_FILENAME) ?: return@withContext null
        if (!file.exists() || file.length() == 0L) return@withContext null
        try {
            openInputStreamForDoc(file)?.use { stream ->
                stream.bufferedReader(Charsets.UTF_8).readText()
            }
        } catch (_: Exception) {
            null
        }
    }

    override suspend fun writeVaultConfig(content: String): Boolean = withContext(Dispatchers.IO) {
        val vaultDoc = ensureVaultDirectory() ?: return@withContext false
        try {
            var tmpDoc = vaultDoc.findFile(CONFIG_TMP_FILENAME)
            if (tmpDoc != null && tmpDoc.exists()) {
                tmpDoc.delete()
            }
            tmpDoc = vaultDoc.createFile("application/octet-stream", CONFIG_TMP_FILENAME)
                ?: return@withContext false

            openOutputStreamForDoc(tmpDoc, "wt")?.use { stream ->
                stream.write(content.toByteArray(Charsets.UTF_8))
                stream.flush()
            } ?: return@withContext false

            // Verify content is valid JSON metadata before committing
            val verifiedText = openInputStreamForDoc(tmpDoc)?.use { stream ->
                stream.bufferedReader(Charsets.UTF_8).readText()
            } ?: ""
            val verified = VaultMetadataJson.fromJson(verifiedText)
            if (verified == null) {
                tmpDoc.delete()
                return@withContext false
            }

            val targetDoc = vaultDoc.findFile(CONFIG_FILENAME)
            if (targetDoc != null && targetDoc.exists()) {
                targetDoc.delete()
            }

            if (!tmpDoc.renameTo(CONFIG_FILENAME)) {
                // Fallback if provider does not support rename
                val newConfigDoc = vaultDoc.createFile("application/octet-stream", CONFIG_FILENAME)
                    ?: return@withContext false
                openOutputStreamForDoc(newConfigDoc, "wt")?.use { out ->
                    openInputStreamForDoc(tmpDoc)?.use { input ->
                        input.copyTo(out)
                    }
                    out.flush()
                }
                tmpDoc.delete()
            }
            true
        } catch (_: Exception) {
            false
        }
    }

    override suspend fun deleteVaultConfig(): Boolean = withContext(Dispatchers.IO) {
        val vaultDoc = getVaultDirectoryDocument() ?: return@withContext true
        val file = vaultDoc.findFile(CONFIG_FILENAME)
        if (file != null && file.exists()) {
            file.delete()
        } else {
            true
        }
    }

    override suspend fun hasVaultIndex(): Boolean = withContext(Dispatchers.IO) {
        val vaultDoc = getVaultDirectoryDocument() ?: return@withContext false
        val file = vaultDoc.findFile(INDEX_FILENAME)
        file != null && file.exists() && file.length() > 0L
    }

    override suspend fun readVaultIndex(): String? = withContext(Dispatchers.IO) {
        val vaultDoc = getVaultDirectoryDocument() ?: return@withContext null
        val file = vaultDoc.findFile(INDEX_FILENAME) ?: return@withContext null
        if (!file.exists() || file.length() == 0L) return@withContext null
        try {
            openInputStreamForDoc(file)?.use { stream ->
                stream.bufferedReader(Charsets.UTF_8).readText()
            }
        } catch (_: Exception) {
            null
        }
    }

    override suspend fun writeVaultIndex(content: String): Boolean = withContext(Dispatchers.IO) {
        val vaultDoc = ensureVaultDirectory() ?: return@withContext false
        try {
            var tmpDoc = vaultDoc.findFile(INDEX_TMP_FILENAME)
            if (tmpDoc != null && tmpDoc.exists()) {
                tmpDoc.delete()
            }
            tmpDoc = vaultDoc.createFile("application/octet-stream", INDEX_TMP_FILENAME)
                ?: return@withContext false

            openOutputStreamForDoc(tmpDoc, "wt")?.use { stream ->
                stream.write(content.toByteArray(Charsets.UTF_8))
                stream.flush()
            } ?: return@withContext false

            val verifiedText = openInputStreamForDoc(tmpDoc)?.use { stream ->
                stream.bufferedReader(Charsets.UTF_8).readText()
            } ?: ""
            val verified = VaultIndexJson.fromJson(verifiedText)
            if (verified == null) {
                tmpDoc.delete()
                return@withContext false
            }

            val targetDoc = vaultDoc.findFile(INDEX_FILENAME)
            if (targetDoc != null && targetDoc.exists()) {
                targetDoc.delete()
            }

            if (!tmpDoc.renameTo(INDEX_FILENAME)) {
                val newIndexDoc = vaultDoc.createFile("application/octet-stream", INDEX_FILENAME)
                    ?: return@withContext false
                openOutputStreamForDoc(newIndexDoc, "wt")?.use { out ->
                    openInputStreamForDoc(tmpDoc)?.use { input ->
                        input.copyTo(out)
                    }
                    out.flush()
                }
                tmpDoc.delete()
            }
            true
        } catch (_: Exception) {
            false
        }
    }

    override suspend fun listVaultFiles(): List<VaultFileEntry> = withContext(Dispatchers.IO) {
        val vaultDoc = getVaultDirectoryDocument() ?: return@withContext emptyList()
        val result = mutableListOf<VaultFileEntry>()
        try {
            vaultDoc.listFiles().forEach { doc ->
                val name = doc.name ?: return@forEach
                if (doc.isFile && name.endsWith(".vlt") && !name.endsWith(".part")) {
                    val id = if (name.endsWith(".vlt")) name.substring(0, name.length - 4) else name
                    result.add(
                        VaultFileEntry(
                            id = id,
                            filename = name,
                            size = doc.length(),
                            lastModified = doc.lastModified(),
                            uriString = doc.uri.toString()
                        )
                    )
                }
            }
        } catch (_: Exception) {}
        result
    }

    override suspend fun fileExists(filename: String): Boolean = withContext(Dispatchers.IO) {
        val vaultDoc = getVaultDirectoryDocument() ?: return@withContext false
        vaultDoc.findFile(filename)?.exists() == true
    }

    override suspend fun getFileLength(filename: String): Long = withContext(Dispatchers.IO) {
        val vaultDoc = getVaultDirectoryDocument() ?: return@withContext 0L
        vaultDoc.findFile(filename)?.length() ?: 0L
    }

    override suspend fun openInputStream(filename: String): InputStream = withContext(Dispatchers.IO) {
        val vaultDoc = getVaultDirectoryDocument()
            ?: throw FileNotFoundException("Vault storage directory not accessible")
        val fileDoc = vaultDoc.findFile(filename)
            ?: throw FileNotFoundException("Vault file not found: $filename")
        openInputStreamForDoc(fileDoc)
            ?: throw IOException("Cannot open input stream for: $filename")
    }

    override suspend fun openOutputStream(filename: String): OutputStream = withContext(Dispatchers.IO) {
        val vaultDoc = ensureVaultDirectory()
            ?: throw FileNotFoundException("Vault storage directory not accessible")
        var fileDoc = vaultDoc.findFile(filename)
        if (fileDoc == null) {
            fileDoc = vaultDoc.createFile("application/octet-stream", filename)
                ?: throw IOException("Cannot create file in vault storage: $filename")
        }
        openOutputStreamForDoc(fileDoc, "wt")
            ?: throw IOException("Cannot open output stream for: $filename")
    }

    override suspend fun deleteFile(filename: String): Boolean = withContext(Dispatchers.IO) {
        val vaultDoc = getVaultDirectoryDocument() ?: return@withContext true
        val fileDoc = vaultDoc.findFile(filename) ?: return@withContext true
        fileDoc.delete()
    }

    override suspend fun renameFile(oldFilename: String, newFilename: String): Boolean = withContext(Dispatchers.IO) {
        val vaultDoc = ensureVaultDirectory() ?: return@withContext false
        val sourceDoc = vaultDoc.findFile(oldFilename) ?: return@withContext false
        val existingTarget = vaultDoc.findFile(newFilename)
        if (existingTarget != null && existingTarget.exists()) {
            existingTarget.delete()
        }
        if (sourceDoc.renameTo(newFilename)) {
            true
        } else {
            try {
                val targetDoc = vaultDoc.createFile("application/octet-stream", newFilename)
                    ?: return@withContext false
                openOutputStreamForDoc(targetDoc, "wt")?.use { out ->
                    openInputStreamForDoc(sourceDoc)?.use { input ->
                        input.copyTo(out)
                    }
                    out.flush()
                }
                sourceDoc.delete()
                true
            } catch (_: Exception) {
                false
            }
        }
    }

    override suspend fun clearVault(): Boolean = withContext(Dispatchers.IO) {
        val vaultDoc = getVaultDirectoryDocument() ?: return@withContext true
        var allSuccess = true
        try {
            vaultDoc.listFiles().forEach { doc ->
                if (doc.name != NOMEDIA_FILENAME) {
                    if (!doc.delete()) {
                        allSuccess = false
                    }
                }
            }
        } catch (_: Exception) {
            allSuccess = false
        }
        allSuccess
    }
}
