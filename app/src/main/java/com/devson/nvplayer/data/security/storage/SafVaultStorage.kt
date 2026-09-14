package com.devson.nvplayer.data.security.storage

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.devson.nvplayer.data.security.VaultMetadataJson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.FileNotFoundException
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
            prefs.edit().putString(KEY_TREE_URI, treeUri.toString()).commit()
            ensureVaultDirectory() != null
        } catch (_: Exception) {
            false
        }
    }

    fun clearSavedTreeUri() {
        prefs.edit().remove(KEY_TREE_URI).commit()
    }

    override fun isStorageAccessible(): Boolean {
        val uri = getSavedTreeUri() ?: return false
        return try {
            val vaultDoc = getVaultDirectoryDocument()
            vaultDoc != null && vaultDoc.exists() && vaultDoc.canRead() && vaultDoc.canWrite()
        } catch (_: Exception) {
            false
        }
    }

    override fun getStorageLocationDescription(): String {
        val vaultDoc = getVaultDirectoryDocument()
        if (vaultDoc != null && vaultDoc.exists()) {
            return "Documents/NosvedPlayer/$VAULT_FOLDER_NAME"
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

    private fun findOrCreateVaultDirectory(createIfMissing: Boolean): DocumentFile? {
        val treeUri = getSavedTreeUri() ?: return null
        return try {
            val rootDoc = DocumentFile.fromTreeUri(context, treeUri) ?: return null
            if (!rootDoc.exists() || !rootDoc.canRead()) return null

            // 1. If user directly selected .vault_secure_media
            if (rootDoc.name == VAULT_FOLDER_NAME) {
                if (createIfMissing) ensureNoMedia(rootDoc)
                return rootDoc
            }

            // 2. If root has .vault_secure_media directly inside it
            val directVault = rootDoc.findFile(VAULT_FOLDER_NAME)
            if (directVault != null && directVault.exists()) {
                if (createIfMissing) ensureNoMedia(directVault)
                return directVault
            }

            // 3. If root has "NosvedPlayer" subfolder (e.g. user selected Documents)
            val appFolder = rootDoc.findFile("NosvedPlayer")
            if (appFolder != null && appFolder.isDirectory) {
                val nestedVault = appFolder.findFile(VAULT_FOLDER_NAME)
                if (nestedVault != null && nestedVault.exists()) {
                    if (createIfMissing) ensureNoMedia(nestedVault)
                    return nestedVault
                }
                if (createIfMissing) {
                    val created = appFolder.createDirectory(VAULT_FOLDER_NAME)
                    if (created != null && created.exists()) {
                        ensureNoMedia(created)
                        return created
                    }
                }
            }

            // 4. If root is "NosvedPlayer" (e.g. user opened Documents/NosvedPlayer and selected it)
            if (rootDoc.name?.equals("NosvedPlayer", ignoreCase = true) == true) {
                val vault = rootDoc.findFile(VAULT_FOLDER_NAME)
                    ?: if (createIfMissing) rootDoc.createDirectory(VAULT_FOLDER_NAME) else null
                if (vault != null && vault.exists()) {
                    if (createIfMissing) ensureNoMedia(vault)
                    return vault
                }
            }

            // 5. If creating when missing and root is Documents or similar
            if (createIfMissing) {
                val targetAppFolder = rootDoc.findFile("NosvedPlayer")
                    ?: rootDoc.createDirectory("NosvedPlayer")
                val targetParent = targetAppFolder ?: rootDoc
                val createdVault = targetParent.findFile(VAULT_FOLDER_NAME)
                    ?: targetParent.createDirectory(VAULT_FOLDER_NAME)
                if (createdVault != null && createdVault.exists()) {
                    ensureNoMedia(createdVault)
                    return createdVault
                }
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
            context.contentResolver.openInputStream(file.uri)?.use { stream ->
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

            context.contentResolver.openOutputStream(tmpDoc.uri, "wt")?.use { stream ->
                stream.write(content.toByteArray(Charsets.UTF_8))
                stream.flush()
            } ?: return@withContext false

            // Verify content is valid JSON metadata before committing
            val verifiedText = context.contentResolver.openInputStream(tmpDoc.uri)?.use { stream ->
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
                context.contentResolver.openOutputStream(newConfigDoc.uri, "wt")?.use { out ->
                    context.contentResolver.openInputStream(tmpDoc.uri)?.use { input ->
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
            context.contentResolver.openInputStream(file.uri)?.use { stream ->
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

            context.contentResolver.openOutputStream(tmpDoc.uri, "wt")?.use { stream ->
                stream.write(content.toByteArray(Charsets.UTF_8))
                stream.flush()
            } ?: return@withContext false

            val verifiedText = context.contentResolver.openInputStream(tmpDoc.uri)?.use { stream ->
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
                context.contentResolver.openOutputStream(newIndexDoc.uri, "wt")?.use { out ->
                    context.contentResolver.openInputStream(tmpDoc.uri)?.use { input ->
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
        context.contentResolver.openInputStream(fileDoc.uri)
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
        context.contentResolver.openOutputStream(fileDoc.uri, "wt")
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
                context.contentResolver.openOutputStream(targetDoc.uri, "wt")?.use { out ->
                    context.contentResolver.openInputStream(sourceDoc.uri)?.use { input ->
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
