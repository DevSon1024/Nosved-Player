package com.devson.nvplayer.data.security

import com.devson.nvplayer.domain.model.VaultStorageMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.DataOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

interface VaultContainer {
    suspend fun createEncryptedVaultFile(
        sourceInputStream: InputStream,
        destinationVaultFile: File,
        passwordOrPin: String = "",
        masterKey: SecretKeySpec? = null,
        title: String,
        originalExtension: String,
        durationMs: Long = 0L,
        originalSize: Long = 0L,
        kdfIterations: Int = VaultKeyDerivation.DEFAULT_KDF_ITERATIONS,
        customSalt: ByteArray? = null
    ): VaultFileHeader

    suspend fun createUnencryptedVaultFile(
        sourceInputStream: InputStream,
        destinationVaultFile: File,
        title: String,
        originalExtension: String,
        durationMs: Long = 0L,
        originalSize: Long = 0L
    ): VaultFileHeader

    suspend fun inspectVaultFile(vaultFile: File): VaultFileHeader

    suspend fun decryptVaultFile(
        vaultFile: File,
        destinationFile: File,
        passwordOrPin: String = "",
        masterKey: SecretKeySpec? = null
    ): VaultFileHeader

    suspend fun openUnencryptedVaultFile(vaultFile: File): InputStream

    suspend fun verifyVaultFileIntegrity(
        vaultFile: File,
        passwordOrPin: String? = null,
        masterKey: SecretKeySpec? = null
    ): Boolean

    // Stream-based Operations for SAF Storage
    suspend fun createEncryptedVaultStream(
        sourceInputStream: InputStream,
        destinationOutputStream: OutputStream,
        passwordOrPin: String = "",
        masterKey: SecretKeySpec? = null,
        title: String,
        originalExtension: String,
        durationMs: Long = 0L,
        originalSize: Long = 0L,
        kdfIterations: Int = VaultKeyDerivation.DEFAULT_KDF_ITERATIONS,
        customSalt: ByteArray? = null
    ): VaultFileHeader

    suspend fun createUnencryptedVaultStream(
        sourceInputStream: InputStream,
        destinationOutputStream: OutputStream,
        title: String,
        originalExtension: String,
        durationMs: Long = 0L,
        originalSize: Long = 0L
    ): VaultFileHeader

    suspend fun inspectVaultStream(vaultInputStream: InputStream): VaultFileHeader

    suspend fun decryptVaultStream(
        vaultInputStream: InputStream,
        destinationOutputStream: OutputStream,
        passwordOrPin: String = "",
        masterKey: SecretKeySpec? = null
    ): VaultFileHeader

    suspend fun verifyVaultStreamIntegrity(
        vaultInputStream: InputStream,
        passwordOrPin: String? = null,
        masterKey: SecretKeySpec? = null
    ): Boolean
}

class DefaultVaultContainer : VaultContainer {

    override suspend fun createEncryptedVaultStream(
        sourceInputStream: InputStream,
        destinationOutputStream: OutputStream,
        passwordOrPin: String,
        masterKey: SecretKeySpec?,
        title: String,
        originalExtension: String,
        durationMs: Long,
        originalSize: Long,
        kdfIterations: Int,
        customSalt: ByteArray?
    ): VaultFileHeader = withContext(Dispatchers.IO) {
        require(masterKey != null || passwordOrPin.isNotEmpty()) {
            "Master key or PIN/password is required for encrypted vault files"
        }

        val nonce = VaultKeyDerivation.generateGcmNonce()
        val secretKey: SecretKeySpec
        val kdfAlgorithm: String
        val iterations: Int
        val salt: ByteArray
        if (masterKey != null) {
            secretKey = masterKey
            kdfAlgorithm = "MASTER_KEY"
            iterations = 0
            salt = ByteArray(0)
        } else {
            salt = customSalt ?: VaultKeyDerivation.generateSalt()
            secretKey = VaultKeyDerivation.deriveKey(passwordOrPin, salt, kdfIterations)
            kdfAlgorithm = VaultKeyDerivation.DEFAULT_KDF_ALGORITHM
            iterations = kdfIterations
        }

        val resolvedSize = if (originalSize > 0L) {
            originalSize
        } else if (sourceInputStream is FileInputStream) {
            try { sourceInputStream.channel.size() } catch (_: Exception) { 0L }
        } else {
            0L
        }

        val header = VaultFileHeader(
            formatVersion = VaultFileFormat.FORMAT_VERSION_V2,
            storageMode = VaultStorageMode.ENCRYPTED,
            encryptionAlgorithm = "AES/GCM/NoPadding",
            kdfAlgorithm = kdfAlgorithm,
            kdfIterations = iterations,
            salt = salt,
            nonce = nonce,
            originalExtension = originalExtension.trimStart('.').lowercase(),
            title = title.ifBlank { "Protected Video" },
            durationMs = durationMs,
            originalPlaintextSize = resolvedSize,
            dateAdded = System.currentTimeMillis()
        )

        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, secretKey, GCMParameterSpec(VaultKeyDerivation.GCM_TAG_LENGTH_BITS, nonce))

        var totalPlaintext = 0L
        val dos = DataOutputStream(destinationOutputStream)
        val headerBytes = VaultFileFormat.writeV2EncryptedHeader(dos, header)
        cipher.updateAAD(headerBytes)

        val buffer = ByteArray(BUFFER_SIZE)
        var bytesRead: Int

        while (sourceInputStream.read(buffer).also { bytesRead = it } != -1) {
            totalPlaintext += bytesRead
            val encryptedChunk = cipher.update(buffer, 0, bytesRead)
            if (encryptedChunk != null && encryptedChunk.isNotEmpty()) {
                dos.write(encryptedChunk)
            }
        }

        val finalChunk = cipher.doFinal()
        if (finalChunk != null && finalChunk.isNotEmpty()) {
            dos.write(finalChunk)
        }
        dos.flush()

        header.copy(originalPlaintextSize = if (totalPlaintext > 0L) totalPlaintext else resolvedSize)
    }

    override suspend fun createUnencryptedVaultStream(
        sourceInputStream: InputStream,
        destinationOutputStream: OutputStream,
        title: String,
        originalExtension: String,
        durationMs: Long,
        originalSize: Long
    ): VaultFileHeader = withContext(Dispatchers.IO) {
        var totalBytes = 0L
        val buffer = ByteArray(BUFFER_SIZE)
        var bytesRead: Int
        while (sourceInputStream.read(buffer).also { bytesRead = it } != -1) {
            destinationOutputStream.write(buffer, 0, bytesRead)
            totalBytes += bytesRead
        }
        destinationOutputStream.flush()

        VaultFileHeader(
            formatVersion = VaultFileFormat.FORMAT_VERSION_V2,
            storageMode = VaultStorageMode.NONE,
            encryptionAlgorithm = "NONE",
            kdfAlgorithm = "NONE",
            kdfIterations = 0,
            salt = ByteArray(0),
            nonce = ByteArray(0),
            originalExtension = originalExtension.trimStart('.').lowercase(),
            title = title.ifBlank { "Protected Video" },
            durationMs = durationMs,
            originalPlaintextSize = totalBytes,
            dateAdded = System.currentTimeMillis()
        )
    }

    override suspend fun inspectVaultStream(vaultInputStream: InputStream): VaultFileHeader {
        return VaultFileFormat.inspectStream(vaultInputStream)
    }

    override suspend fun decryptVaultStream(
        vaultInputStream: InputStream,
        destinationOutputStream: OutputStream,
        passwordOrPin: String,
        masterKey: SecretKeySpec?
    ): VaultFileHeader = withContext(Dispatchers.IO) {
        val bis = if (vaultInputStream is java.io.BufferedInputStream) vaultInputStream else java.io.BufferedInputStream(vaultInputStream, BUFFER_SIZE)
        val (header, headerBytes) = VaultFileFormat.readHeaderWithBytes(bis)

        when (header.storageMode) {
            VaultStorageMode.NONE -> {
                bis.copyTo(destinationOutputStream, BUFFER_SIZE)
                destinationOutputStream.flush()
                header
            }

            VaultStorageMode.ENCRYPTED -> {
                val secretKey = if (header.kdfAlgorithm == "MASTER_KEY" || header.salt.isEmpty()) {
                    masterKey ?: throw VaultAuthenticationException("Master Key required to decrypt this vault file")
                } else if (passwordOrPin.isNotEmpty() && header.salt.isNotEmpty()) {
                    VaultKeyDerivation.deriveKey(
                        passwordOrPin,
                        header.salt,
                        header.kdfIterations
                    )
                } else if (masterKey != null) {
                    masterKey
                } else {
                    throw VaultAuthenticationException("Password, PIN, or Master Key required to decrypt this vault file")
                }

                val cipher = Cipher.getInstance("AES/GCM/NoPadding")
                cipher.init(Cipher.DECRYPT_MODE, secretKey, GCMParameterSpec(VaultKeyDerivation.GCM_TAG_LENGTH_BITS, header.nonce))
                cipher.updateAAD(headerBytes)

                val buffer = ByteArray(BUFFER_SIZE)
                var bytesRead: Int

                while (bis.read(buffer).also { bytesRead = it } != -1) {
                    val decryptedChunk = cipher.update(buffer, 0, bytesRead)
                    if (decryptedChunk != null && decryptedChunk.isNotEmpty()) {
                        destinationOutputStream.write(decryptedChunk)
                    }
                }

                try {
                    val finalChunk = cipher.doFinal()
                    if (finalChunk != null && finalChunk.isNotEmpty()) {
                        destinationOutputStream.write(finalChunk)
                    }
                } catch (e: AEADBadTagException) {
                    throw VaultIntegrityException(
                        "Vault integrity verification failed. Password may be incorrect or file has been modified/corrupted.",
                        e
                    )
                } catch (e: Exception) {
                    throw VaultIntegrityException("Decryption error: ${e.message}", e)
                }
                destinationOutputStream.flush()
                header
            }
        }
    }

    override suspend fun verifyVaultStreamIntegrity(
        vaultInputStream: InputStream,
        passwordOrPin: String?,
        masterKey: SecretKeySpec?
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val bis = if (vaultInputStream is java.io.BufferedInputStream) vaultInputStream else java.io.BufferedInputStream(vaultInputStream, BUFFER_SIZE)
            val (header, headerBytes) = VaultFileFormat.readHeaderWithBytes(bis)

            if (header.storageMode == VaultStorageMode.NONE) {
                return@withContext true
            }

            val secretKey = if (header.kdfAlgorithm == "MASTER_KEY" || header.salt.isEmpty()) {
                masterKey ?: return@withContext false
            } else if (!passwordOrPin.isNullOrEmpty() && header.salt.isNotEmpty()) {
                VaultKeyDerivation.deriveKey(
                    passwordOrPin,
                    header.salt,
                    header.kdfIterations
                )
            } else if (masterKey != null) {
                masterKey
            } else {
                return@withContext false
            }

            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, secretKey, GCMParameterSpec(VaultKeyDerivation.GCM_TAG_LENGTH_BITS, header.nonce))
            cipher.updateAAD(headerBytes)

            val buffer = ByteArray(BUFFER_SIZE)
            var bytesRead: Int

            while (bis.read(buffer).also { bytesRead = it } != -1) {
                cipher.update(buffer, 0, bytesRead)
            }

            cipher.doFinal()
            true
        } catch (_: Exception) {
            false
        }
    }

    override suspend fun createEncryptedVaultFile(
        sourceInputStream: InputStream,
        destinationVaultFile: File,
        passwordOrPin: String,
        masterKey: SecretKeySpec?,
        title: String,
        originalExtension: String,
        durationMs: Long,
        originalSize: Long,
        kdfIterations: Int,
        customSalt: ByteArray?
    ): VaultFileHeader = withContext(Dispatchers.IO) {
        val destinationDir = destinationVaultFile.parentFile
        if (destinationDir != null && !destinationDir.exists()) {
            destinationDir.mkdirs()
        }

        val partFile = File(destinationDir, destinationVaultFile.name + ".part")
        if (partFile.exists()) partFile.delete()

        try {
            val header = FileOutputStream(partFile).buffered(BUFFER_SIZE).use { fos ->
                createEncryptedVaultStream(
                    sourceInputStream = sourceInputStream,
                    destinationOutputStream = fos,
                    passwordOrPin = passwordOrPin,
                    masterKey = masterKey,
                    title = title,
                    originalExtension = originalExtension,
                    durationMs = durationMs,
                    originalSize = originalSize,
                    kdfIterations = kdfIterations,
                    customSalt = customSalt
                )
            }

            if (!partFile.renameTo(destinationVaultFile)) {
                partFile.copyTo(destinationVaultFile, overwrite = true)
                partFile.delete()
            }

            header
        } catch (e: Exception) {
            if (partFile.exists()) partFile.delete()
            throw e
        }
    }

    override suspend fun createUnencryptedVaultFile(
        sourceInputStream: InputStream,
        destinationVaultFile: File,
        title: String,
        originalExtension: String,
        durationMs: Long,
        originalSize: Long
    ): VaultFileHeader = withContext(Dispatchers.IO) {
        val destinationDir = destinationVaultFile.parentFile
        if (destinationDir != null && !destinationDir.exists()) {
            destinationDir.mkdirs()
        }

        val partFile = File(destinationDir, destinationVaultFile.name + ".part")
        if (partFile.exists()) partFile.delete()

        try {
            val header = FileOutputStream(partFile).buffered(BUFFER_SIZE).use { fos ->
                createUnencryptedVaultStream(
                    sourceInputStream = sourceInputStream,
                    destinationOutputStream = fos,
                    title = title,
                    originalExtension = originalExtension,
                    durationMs = durationMs,
                    originalSize = originalSize
                )
            }

            if (!partFile.renameTo(destinationVaultFile)) {
                partFile.copyTo(destinationVaultFile, overwrite = true)
                partFile.delete()
            }

            header
        } catch (e: Exception) {
            if (partFile.exists()) partFile.delete()
            throw e
        }
    }

    override suspend fun inspectVaultFile(vaultFile: File): VaultFileHeader {
        return VaultFileFormat.inspectFile(vaultFile)
    }

    override suspend fun decryptVaultFile(
        vaultFile: File,
        destinationFile: File,
        passwordOrPin: String,
        masterKey: SecretKeySpec?
    ): VaultFileHeader = withContext(Dispatchers.IO) {
        if (!vaultFile.exists()) {
            throw VaultCorruptedFileException("Vault file does not exist: ${vaultFile.absolutePath}")
        }

        val destDir = destinationFile.parentFile
        if (destDir != null && !destDir.exists()) {
            destDir.mkdirs()
        }

        val partFile = File(destDir, destinationFile.name + ".part")
        if (partFile.exists()) partFile.delete()

        try {
            val header = FileInputStream(vaultFile).buffered(BUFFER_SIZE).use { fis ->
                val (peekHeader, _) = VaultFileFormat.readHeaderWithBytes(fis)
                if (peekHeader.formatVersion == VaultFileFormat.FORMAT_VERSION_LEGACY_V1 || peekHeader.formatVersion == 0) {
                    LegacyVaultManager.decryptLegacyFile(vaultFile, partFile)
                    peekHeader
                } else {
                    FileInputStream(vaultFile).buffered(BUFFER_SIZE).use { streamForDecrypt ->
                        FileOutputStream(partFile).buffered(BUFFER_SIZE).use { fos ->
                            decryptVaultStream(
                                vaultInputStream = streamForDecrypt,
                                destinationOutputStream = fos,
                                passwordOrPin = passwordOrPin,
                                masterKey = masterKey
                            )
                        }
                    }
                }
            }

            if (!partFile.renameTo(destinationFile)) {
                partFile.copyTo(destinationFile, overwrite = true)
                partFile.delete()
            }

            header
        } catch (e: Exception) {
            if (partFile.exists()) partFile.delete()
            throw e
        }
    }

    override suspend fun openUnencryptedVaultFile(vaultFile: File): InputStream = withContext(Dispatchers.IO) {
        val header = inspectVaultFile(vaultFile)
        if (header.storageMode != VaultStorageMode.NONE) {
            throw IllegalStateException("Vault file is encrypted. Use decryptVaultFile to access media payload.")
        }
        FileInputStream(vaultFile).buffered(BUFFER_SIZE)
    }

    override suspend fun verifyVaultFileIntegrity(
        vaultFile: File,
        passwordOrPin: String?,
        masterKey: SecretKeySpec?
    ): Boolean = withContext(Dispatchers.IO) {
        if (!vaultFile.exists() || vaultFile.length() == 0L) {
            return@withContext false
        }

        val header = try {
            inspectVaultFile(vaultFile)
        } catch (_: Exception) {
            return@withContext false
        }

        if (header.storageMode == VaultStorageMode.NONE) {
            return@withContext vaultFile.canRead() && vaultFile.length() > 0L
        }

        if (header.formatVersion == VaultFileFormat.FORMAT_VERSION_LEGACY_V1) {
            return@withContext vaultFile.length() > 24L
        }

        FileInputStream(vaultFile).buffered(BUFFER_SIZE).use { fis ->
            verifyVaultStreamIntegrity(fis, passwordOrPin, masterKey)
        }
    }

    companion object {
        private const val BUFFER_SIZE = 64 * 1024
    }
}
