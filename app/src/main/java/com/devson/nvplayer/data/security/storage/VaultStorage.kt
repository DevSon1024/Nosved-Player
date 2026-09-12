package com.devson.nvplayer.data.security.storage

import java.io.InputStream
import java.io.OutputStream

/**
 * Metadata representation of a file stored in the persistent secure vault.
 */
data class VaultFileEntry(
    val id: String,
    val filename: String,
    val size: Long,
    val lastModified: Long,
    val uriString: String = ""
)

/**
 * Storage abstraction for persistent vault data.
 * All persistent vault I/O must pass through this layer to ensure compatibility
 * with Android Scoped Storage / Storage Access Framework (SAF) and JVM testing.
 */
interface VaultStorage {
    /**
     * Checks if persistent storage is granted and writable/readable.
     */
    fun isStorageAccessible(): Boolean

    /**
     * Returns a user-readable description or path of the storage location.
     */
    fun getStorageLocationDescription(): String

    /**
     * Checks if the persistent vault configuration metadata exists.
     */
    suspend fun hasVaultConfig(): Boolean

    /**
     * Reads the raw text of .vault_config. Returns null if missing or unreadable.
     */
    suspend fun readVaultConfig(): String?

    /**
     * Atomically writes .vault_config text.
     * Writes to a temporary file, flushes, verifies validity, and commits.
     */
    suspend fun writeVaultConfig(content: String): Boolean

    /**
     * Deletes the persistent .vault_config file.
     */
    suspend fun deleteVaultConfig(): Boolean

    /**
     * Checks if the persistent vault index file exists.
     */
    suspend fun hasVaultIndex(): Boolean

    /**
     * Reads the persistent vault index JSON text.
     */
    suspend fun readVaultIndex(): String?

    /**
     * Atomically writes the persistent vault index JSON text.
     */
    suspend fun writeVaultIndex(content: String): Boolean

    /**
     * Lists all .vlt files currently stored in the vault.
     */
    suspend fun listVaultFiles(): List<VaultFileEntry>

    /**
     * Checks if a specific file exists in the vault directory.
     */
    suspend fun fileExists(filename: String): Boolean

    /**
     * Gets the length in bytes of a specific file in the vault.
     */
    suspend fun getFileLength(filename: String): Long

    /**
     * Opens an InputStream for reading a file from the vault.
     */
    suspend fun openInputStream(filename: String): InputStream

    /**
     * Opens an OutputStream for writing/overwriting a file in the vault.
     */
    suspend fun openOutputStream(filename: String): OutputStream

    /**
     * Deletes a specific file from the vault.
     */
    suspend fun deleteFile(filename: String): Boolean

    /**
     * Renames or moves a file within the vault storage.
     */
    suspend fun renameFile(oldFilename: String, newFilename: String): Boolean

    /**
     * Deletes all vault files, indexes, and metadata (complete destructive reset).
     */
    suspend fun clearVault(): Boolean
}
