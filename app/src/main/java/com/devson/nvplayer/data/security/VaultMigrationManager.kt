package com.devson.nvplayer.data.security

import android.content.Context
import com.devson.nvplayer.data.database.VaultDao
import com.devson.nvplayer.data.database.VaultEntity
import com.devson.nvplayer.domain.model.VaultStorageMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.util.UUID

sealed interface VaultMigrationStatus {
    object Idle : VaultMigrationStatus
    object Scanning : VaultMigrationStatus
    data class Ready(
        val legacyFiles: List<File>,
        val totalBytes: Long
    ) : VaultMigrationStatus
    data class InProgress(
        val currentFileName: String,
        val currentIndex: Int,
        val totalFiles: Int,
        val progressFraction: Float
    ) : VaultMigrationStatus
    data class Complete(
        val successfulCount: Int,
        val failedCount: Int,
        val skippedCount: Int
    ) : VaultMigrationStatus
    data class Failed(
        val failedFile: File?,
        val message: String,
        val cause: Throwable? = null
    ) : VaultMigrationStatus
}

data class MigrationResult(
    val file: File,
    val success: Boolean,
    val skipped: Boolean = false,
    val error: Throwable? = null,
    val message: String = "",
    val updatedEntity: VaultEntity? = null
)

data class MigrationSummary(
    val totalFound: Int,
    val successful: Int,
    val failed: Int,
    val skipped: Int,
    val failures: List<Pair<File, Throwable>>
)

class VaultMigrationManager(
    private val cacheDir: File,
    private val vaultDao: VaultDao,
    private val vaultContainer: VaultContainer = DefaultVaultContainer(),
    val vaultDirectory: File,
    private val storageSpaceProvider: (File) -> Long = { it.usableSpace },
    private val permissionChecker: (File) -> Boolean = { it.canRead() && it.parentFile?.canWrite() != false }
) {
    constructor(
        context: Context,
        vaultDao: VaultDao,
        vaultContainer: VaultContainer = DefaultVaultContainer(),
        vaultDirectory: File,
        storageSpaceProvider: (File) -> Long = { it.usableSpace },
        permissionChecker: (File) -> Boolean = { it.canRead() && it.parentFile?.canWrite() != false }
    ) : this(
        cacheDir = context.cacheDir,
        vaultDao = vaultDao,
        vaultContainer = vaultContainer,
        vaultDirectory = vaultDirectory,
        storageSpaceProvider = storageSpaceProvider,
        permissionChecker = permissionChecker
    )

    private val _status = MutableStateFlow<VaultMigrationStatus>(VaultMigrationStatus.Idle)
    val status: StateFlow<VaultMigrationStatus> = _status.asStateFlow()

    val tempMigrationDir: File by lazy {
        val dir = File(cacheDir, "vault_migration_temp")
        if (!dir.exists()) dir.mkdirs()
        val nomedia = File(dir, ".nomedia")
        if (!nomedia.exists()) {
            try { nomedia.createNewFile() } catch (_: Exception) {}
        }
        dir
    }

    /**
     * Classifies any vault file on disk into one of four distinct categories:
     * - CURRENT_SECURE_ENCRYPTED: Modern V2 AES-256-GCM container
     * - CURRENT_NO_ENCRYPTION: Modern V2 or raw unencrypted video file
     * - LEGACY_ENCRYPTED: Legacy V1 or V0 AES/CTR encrypted file
     * - INVALID_UNKNOWN: Corrupted, zero-byte, or unknown format
     */
    fun classifyFile(file: File): VaultFileClassification {
        if (!file.exists() || !file.isFile || file.length() < 16L) {
            return VaultFileClassification.INVALID_UNKNOWN
        }

        try {
            FileInputStream(file).use { fis ->
                val probe = ByteArray(minOf(64, file.length().toInt()))
                var totalRead = 0
                while (totalRead < probe.size) {
                    val r = fis.read(probe, totalRead, probe.size - totalRead)
                    if (r == -1) break
                    totalRead += r
                }
                if (totalRead < 16) {
                    return VaultFileClassification.INVALID_UNKNOWN
                }

                // 1. Check for modern V2 format
                if (totalRead >= 8 && probe.copyOfRange(0, 8).contentEquals(VaultFileFormat.MAGIC_V2_ENCRYPTED)) {
                    if (totalRead >= 12) {
                        val modeShort = ((probe[10].toInt() and 0xFF) shl 8) or (probe[11].toInt() and 0xFF)
                        return if (modeShort == 1) {
                            VaultFileClassification.CURRENT_SECURE_ENCRYPTED
                        } else {
                            VaultFileClassification.CURRENT_NO_ENCRYPTION
                        }
                    }
                    return VaultFileClassification.CURRENT_SECURE_ENCRYPTED
                }

                // 2. Sniff unencrypted media signatures directly on raw bytes.
                // Existing unencrypted files MUST NEVER be treated as legacy encrypted or decrypted.
                if (isUnencryptedMediaSignature(probe, totalRead)) {
                    return VaultFileClassification.CURRENT_NO_ENCRYPTION
                }

                // 3. Check for Legacy V1 format (16 bytes IV + "NSDVLT01")
                if (totalRead >= 24 && probe.copyOfRange(16, 24).contentEquals(VaultFileFormat.MAGIC_LEGACY_V1)) {
                    return VaultFileClassification.LEGACY_ENCRYPTED
                }

                // 4. Check for Legacy V0 format (16 bytes IV + AES/CTR ciphertext)
                if (file.length() >= 32L) {
                    val iv = probe.copyOfRange(0, 16)
                    val testCiphertext = probe.copyOfRange(16, minOf(32, totalRead))
                    if (testCiphertext.size >= 16) {
                        try {
                            val decrypted = LegacyVaultManager.decryptFirstBlock(iv, testCiphertext)
                            if (isUnencryptedMediaSignature(decrypted, decrypted.size)) {
                                return VaultFileClassification.LEGACY_ENCRYPTED
                            }
                        } catch (_: Exception) {}
                    }
                }

                return VaultFileClassification.INVALID_UNKNOWN
            }
        } catch (_: Exception) {
            return VaultFileClassification.INVALID_UNKNOWN
        }
    }

    /**
     * Scans the vault directory for legacy encrypted files needing migration.
     * Automatically cleans/recovers any interrupted migrations before returning results.
     */
    suspend fun scanForLegacyFiles(): List<File> = withContext(Dispatchers.IO) {
        _status.value = VaultMigrationStatus.Scanning
        cleanOrRecoverInterruptedMigrations()

        val vltFiles = vaultDirectory.listFiles { f -> f.isFile && f.extension == "vlt" } ?: emptyArray()
        val legacyList = vltFiles.filter { classifyFile(it) == VaultFileClassification.LEGACY_ENCRYPTED }
        val totalSize = legacyList.sumOf { it.length() }

        _status.value = VaultMigrationStatus.Ready(legacyList, totalSize)
        legacyList
    }

    /**
     * Recovers from any previously interrupted migrations or unexpected app crashes.
     */
    suspend fun cleanOrRecoverInterruptedMigrations(): Int = withContext(Dispatchers.IO) {
        var recoveredCount = 0

        // 1. Clean lingering temporary plaintext cache files
        if (tempMigrationDir.exists()) {
            tempMigrationDir.listFiles()?.forEach { f ->
                if (f.name != ".nomedia") {
                    try { f.delete() } catch (_: Exception) {}
                }
            }
        }

        val allVaultFiles = vaultDirectory.listFiles() ?: return@withContext 0

        // 2. Delete leftover .migrating intermediate files
        allVaultFiles.filter { it.name.endsWith(".migrating") }.forEach { migratingFile ->
            try {
                migratingFile.delete()
                recoveredCount++
            } catch (_: Exception) {}
        }

        // 3. Resolve leftover .backup files
        allVaultFiles.filter { it.name.endsWith(".backup") }.forEach { backupFile ->
            val targetVltName = backupFile.name.removeSuffix(".backup")
            val targetVltFile = File(vaultDirectory, targetVltName)

            if (targetVltFile.exists() && targetVltFile.length() > 0L) {
                val classification = classifyFile(targetVltFile)
                if (classification == VaultFileClassification.CURRENT_SECURE_ENCRYPTED) {
                    // Replacement was successfully finalized before the crash; safely delete backup
                    backupFile.delete()
                    recoveredCount++
                } else {
                    // Replacement was interrupted or corrupted; restore from backup
                    targetVltFile.delete()
                    backupFile.renameTo(targetVltFile)
                    recoveredCount++
                }
            } else {
                // Target file is missing; restore from backup
                backupFile.renameTo(targetVltFile)
                recoveredCount++
            }
        }

        recoveredCount
    }

    /**
     * Safely migrates a single legacy vault file to modern AES-256-GCM.
     * Enforces crash-safety, atomic replacement, and integrity verification.
     */
    suspend fun migrateSingleFile(
        legacyFile: File,
        userCredential: String
    ): MigrationResult = withContext(Dispatchers.IO) {
        if (!legacyFile.exists()) {
            return@withContext MigrationResult(
                file = legacyFile,
                success = false,
                error = IOException("File does not exist: ${legacyFile.absolutePath}"),
                message = "File does not exist"
            )
        }

        if (userCredential.isBlank()) {
            return@withContext MigrationResult(
                file = legacyFile,
                success = false,
                error = IllegalArgumentException("User vault credential required for migration"),
                message = "Credential required"
            )
        }

        // 1. Classification & Preconditions
        val classification = classifyFile(legacyFile)
        when (classification) {
            VaultFileClassification.CURRENT_SECURE_ENCRYPTED -> {
                return@withContext MigrationResult(
                    file = legacyFile,
                    success = true,
                    skipped = true,
                    message = "File is already in modern secure format"
                )
            }
            VaultFileClassification.CURRENT_NO_ENCRYPTION -> {
                return@withContext MigrationResult(
                    file = legacyFile,
                    success = true,
                    skipped = true,
                    message = "File is in no-encryption format and should not be migrated"
                )
            }
            VaultFileClassification.INVALID_UNKNOWN -> {
                return@withContext MigrationResult(
                    file = legacyFile,
                    success = false,
                    error = VaultCorruptedFileException("Unrecognized or corrupted vault file: ${legacyFile.name}"),
                    message = "File is invalid or corrupted"
                )
            }
            VaultFileClassification.LEGACY_ENCRYPTED -> {
                // Eligible for migration
            }
        }

        // 2. Storage Space Check
        val minRequiredBytes = (legacyFile.length() * 2) + (5 * 1024 * 1024L)
        val cacheSpace = storageSpaceProvider(tempMigrationDir)
        val vaultSpace = storageSpaceProvider(vaultDirectory)
        if (cacheSpace < minRequiredBytes / 2 || vaultSpace < minRequiredBytes / 2) {
            return@withContext MigrationResult(
                file = legacyFile,
                success = false,
                error = VaultInsufficientStorageException("Insufficient storage space for migration"),
                message = "Insufficient storage space"
            )
        }

        // 3. Permissions Check
        if (!permissionChecker(legacyFile) || !legacyFile.canRead()) {
            return@withContext MigrationResult(
                file = legacyFile,
                success = false,
                error = SecurityException("Permission denied: Cannot read ${legacyFile.absolutePath}"),
                message = "Permission denied reading legacy file"
            )
        }
        if (!vaultDirectory.canWrite()) {
            return@withContext MigrationResult(
                file = legacyFile,
                success = false,
                error = SecurityException("Permission denied: Cannot write to vault directory"),
                message = "Permission denied writing to vault directory"
            )
        }

        val tempPlaintextFile = File(tempMigrationDir, "migration_decrypted_${UUID.randomUUID()}.tmp")
        val migratingVaultFile = File(vaultDirectory, "${legacyFile.name}.migrating")
        val backupFile = File(vaultDirectory, "${legacyFile.name}.backup")

        try {
            // Step A: Decrypt legacy file into private cache
            val legacyHeader = LegacyVaultManager.decryptLegacyFile(legacyFile, tempPlaintextFile)
            if (!tempPlaintextFile.exists() || tempPlaintextFile.length() == 0L) {
                throw VaultCorruptedFileException("Legacy decryption produced an empty file")
            }

            val originalExt = legacyHeader.originalExtension.ifBlank { "mp4" }
            val title = legacyHeader.title.ifBlank { legacyFile.nameWithoutExtension }

            // Step B: Re-encrypt using user's derived vault key with modern AES-256-GCM
            FileInputStream(tempPlaintextFile).buffered().use { plainIn ->
                vaultContainer.createEncryptedVaultFile(
                    sourceInputStream = plainIn,
                    destinationVaultFile = migratingVaultFile,
                    passwordOrPin = userCredential,
                    title = title,
                    originalExtension = originalExt,
                    durationMs = legacyHeader.durationMs,
                    originalSize = tempPlaintextFile.length()
                )
            }

            // Step C: Verify integrity of new encrypted container
            val inspectHeader = vaultContainer.inspectVaultFile(migratingVaultFile)
            if (inspectHeader.formatVersion != VaultFileFormat.FORMAT_VERSION_V2 ||
                inspectHeader.storageMode != VaultStorageMode.ENCRYPTED) {
                throw VaultIntegrityException("Migrated file header verification failed")
            }

            val isIntegrityValid = vaultContainer.verifyVaultFileIntegrity(migratingVaultFile, userCredential)
            if (!isIntegrityValid) {
                throw VaultIntegrityException("Migrated file AES-GCM integrity check failed")
            }

            // Step D: Atomic Replacement
            if (!legacyFile.renameTo(backupFile)) {
                backupFile.delete()
                legacyFile.copyTo(backupFile, overwrite = true)
                legacyFile.delete()
            }

            val renameOk = if (migratingVaultFile.renameTo(legacyFile)) {
                true
            } else {
                migratingVaultFile.copyTo(legacyFile, overwrite = true)
                migratingVaultFile.delete()
                true
            }

            if (!renameOk || !legacyFile.exists() || legacyFile.length() == 0L) {
                if (backupFile.exists()) {
                    backupFile.renameTo(legacyFile)
                }
                throw IllegalStateException("Failed to atomically replace legacy file")
            }

            val postCheck = vaultContainer.inspectVaultFile(legacyFile)
            if (postCheck.formatVersion != VaultFileFormat.FORMAT_VERSION_V2) {
                if (backupFile.exists()) {
                    legacyFile.delete()
                    backupFile.renameTo(legacyFile)
                }
                throw VaultIntegrityException("Replaced file does not have valid modern V2 header")
            }

            // Step E: Update Database Metadata
            val existingEntity = vaultDao.getByVaultPath(legacyFile.absolutePath)
            val updatedEntity = if (existingEntity != null) {
                val updated = existingEntity.copy(
                    title = title,
                    storageMode = VaultStorageMode.ENCRYPTED,
                    formatVersion = VaultFileFormat.FORMAT_VERSION_V2,
                    fileSize = legacyFile.length(),
                    durationMs = if (legacyHeader.durationMs > 0) legacyHeader.durationMs else existingEntity.durationMs,
                    originalExtension = originalExt
                )
                vaultDao.insert(updated)
                updated
            } else {
                val newEntity = VaultEntity(
                    title = title,
                    originalUri = "",
                    vaultPath = legacyFile.absolutePath,
                    thumbnailPath = null,
                    fileSize = legacyFile.length(),
                    durationMs = legacyHeader.durationMs,
                    dateAdded = legacyHeader.dateAdded.takeIf { it > 0 } ?: legacyFile.lastModified(),
                    storageMode = VaultStorageMode.ENCRYPTED,
                    formatVersion = VaultFileFormat.FORMAT_VERSION_V2,
                    originalExtension = originalExt
                )
                val id = vaultDao.insert(newEntity)
                newEntity.copy(id = id)
            }

            // Step F: Safe Deletion of Backup
            backupFile.delete()

            MigrationResult(
                file = legacyFile,
                success = true,
                message = "Migration successful",
                updatedEntity = updatedEntity
            )
        } catch (e: Exception) {
            // Rollback on any failure: ensure original legacy file is restored
            if (backupFile.exists()) {
                try {
                    if (legacyFile.exists()) {
                        legacyFile.delete()
                    }
                    if (!backupFile.renameTo(legacyFile)) {
                        backupFile.copyTo(legacyFile, overwrite = true)
                        backupFile.delete()
                    }
                } catch (_: Exception) {}
            }
            if (migratingVaultFile.exists()) {
                try { migratingVaultFile.delete() } catch (_: Exception) {}
            }
            MigrationResult(
                file = legacyFile,
                success = false,
                error = e,
                message = "Migration failed: ${e.message}"
            )
        } finally {
            // Guarantee private cache plaintext file is deleted
            if (tempPlaintextFile.exists()) {
                try { tempPlaintextFile.delete() } catch (_: Exception) {}
            }
            if (migratingVaultFile.exists()) {
                try { migratingVaultFile.delete() } catch (_: Exception) {}
            }
        }
    }

    /**
     * Migrates all legacy files in sequence with progress updates.
     */
    suspend fun migrateAll(
        userCredential: String,
        onProgress: ((currentIndex: Int, total: Int, currentFile: String) -> Unit)? = null
    ): MigrationSummary = withContext(Dispatchers.IO) {
        val legacyFiles = scanForLegacyFiles()
        val total = legacyFiles.size
        var successful = 0
        var failed = 0
        var skipped = 0
        val failures = mutableListOf<Pair<File, Throwable>>()

        for ((index, file) in legacyFiles.withIndex()) {
            val progressFraction = if (total > 0) index.toFloat() / total else 0f
            _status.value = VaultMigrationStatus.InProgress(
                currentFileName = file.name,
                currentIndex = index + 1,
                totalFiles = total,
                progressFraction = progressFraction
            )
            onProgress?.invoke(index + 1, total, file.name)

            val result = migrateSingleFile(file, userCredential)
            if (result.success) {
                if (result.skipped) {
                    skipped++
                } else {
                    successful++
                }
            } else {
                failed++
                val err = result.error ?: IOException(result.message)
                failures.add(Pair(file, err))
            }
        }

        _status.value = VaultMigrationStatus.Complete(
            successfulCount = successful,
            failedCount = failed,
            skippedCount = skipped
        )

        MigrationSummary(
            totalFound = total,
            successful = successful,
            failed = failed,
            skipped = skipped,
            failures = failures
        )
    }

    private fun isUnencryptedMediaSignature(bytes: ByteArray, size: Int): Boolean {
        if (size >= 8) {
            // MP4 / MOV: 'ftyp' at offset 4..7
            if (bytes[4] == 0x66.toByte() && bytes[5] == 0x74.toByte() &&
                bytes[6] == 0x79.toByte() && bytes[7] == 0x70.toByte()
            ) {
                return true
            }
        }
        if (size >= 4) {
            // Matroska / WebM EBML ID: 0x1A 0x45 0xDF 0xA3
            if (bytes[0] == 0x1A.toByte() && bytes[1] == 0x45.toByte() &&
                bytes[2] == 0xDF.toByte() && bytes[3] == 0xA3.toByte()
            ) {
                return true
            }
            // AVI: 'RIFF' at 0..3
            if (bytes[0] == 0x52.toByte() && bytes[1] == 0x49.toByte() &&
                bytes[2] == 0x46.toByte() && bytes[3] == 0x46.toByte()
            ) {
                if (size >= 12) {
                    if (bytes[8] == 0x41.toByte() && bytes[9] == 0x56.toByte() &&
                        bytes[10] == 0x49.toByte() && bytes[11] == 0x20.toByte()
                    ) {
                        return true
                    }
                } else {
                    return true
                }
            }
        }
        if (size >= 3) {
            // FLV: 'FLV'
            if (bytes[0] == 0x46.toByte() && bytes[1] == 0x4C.toByte() && bytes[2] == 0x56.toByte()) {
                return true
            }
        }
        if (size >= 1) {
            // MPEG-TS sync byte 0x47
            if (bytes[0] == 0x47.toByte() && size >= 188 && bytes.getOrNull(188) == 0x47.toByte()) {
                return true
            }
        }
        return false
    }
}
