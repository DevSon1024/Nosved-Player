package com.devson.nvplayer.data.security

import com.devson.nvplayer.domain.model.VaultStorageMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.DataOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.IvParameterSpec
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
}

class DefaultVaultContainer : VaultContainer {

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
        require(masterKey != null || passwordOrPin.isNotEmpty()) {
            "Master key or PIN/password is required for encrypted vault files"
        }

        val destinationDir = destinationVaultFile.parentFile
        if (destinationDir != null && !destinationDir.exists()) {
            destinationDir.mkdirs()
        }

        val partFile = File(destinationDir, destinationVaultFile.name + ".part")
        if (partFile.exists()) partFile.delete()

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
            title = title.ifBlank { destinationVaultFile.nameWithoutExtension },
            durationMs = durationMs,
            originalPlaintextSize = resolvedSize,
            dateAdded = System.currentTimeMillis()
        )

        try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, GCMParameterSpec(VaultKeyDerivation.GCM_TAG_LENGTH_BITS, nonce))

            var totalPlaintext = 0L
            FileOutputStream(partFile).buffered(BUFFER_SIZE).use { fos ->
                val dos = DataOutputStream(fos)
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
            }

            if (!partFile.renameTo(destinationVaultFile)) {
                partFile.copyTo(destinationVaultFile, overwrite = true)
                partFile.delete()
            }

            header.copy(originalPlaintextSize = if (totalPlaintext > 0L) totalPlaintext else resolvedSize)
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
            var totalBytes = 0L
            FileOutputStream(partFile).buffered(BUFFER_SIZE).use { fos ->
                val buffer = ByteArray(BUFFER_SIZE)
                var bytesRead: Int
                while (sourceInputStream.read(buffer).also { bytesRead = it } != -1) {
                    fos.write(buffer, 0, bytesRead)
                    totalBytes += bytesRead
                }
                fos.flush()
            }

            if (!partFile.renameTo(destinationVaultFile)) {
                partFile.copyTo(destinationVaultFile, overwrite = true)
                partFile.delete()
            }

            VaultFileHeader(
                formatVersion = VaultFileFormat.FORMAT_VERSION_V2,
                storageMode = VaultStorageMode.NONE,
                encryptionAlgorithm = "NONE",
                kdfAlgorithm = "NONE",
                kdfIterations = 0,
                salt = ByteArray(0),
                nonce = ByteArray(0),
                originalExtension = originalExtension.trimStart('.').lowercase(),
                title = title.ifBlank { destinationVaultFile.nameWithoutExtension },
                durationMs = durationMs,
                originalPlaintextSize = totalBytes,
                dateAdded = System.currentTimeMillis()
            )
        } catch (e: Exception) {
            if (partFile.exists()) partFile.delete()
            throw e
        }
    }

    override suspend fun inspectVaultFile(vaultFile: File): VaultFileHeader = withContext(Dispatchers.IO) {
        VaultFileFormat.inspectFile(vaultFile)
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
            FileInputStream(vaultFile).buffered(BUFFER_SIZE).use { fis ->
                val (header, headerBytes) = VaultFileFormat.readHeaderWithBytes(fis)

                when (header.storageMode) {
                    VaultStorageMode.NONE -> {
                        // Rewind and copy full file byte-for-byte
                        FileOutputStream(partFile).buffered(BUFFER_SIZE).use { fos ->
                            FileInputStream(vaultFile).buffered(BUFFER_SIZE).use { rawFis ->
                                rawFis.copyTo(fos, BUFFER_SIZE)
                            }
                        }
                    }

                    VaultStorageMode.ENCRYPTED -> {
                        if (header.formatVersion == VaultFileFormat.FORMAT_VERSION_LEGACY_V1 || header.formatVersion == 0) {
                            LegacyVaultManager.decryptLegacyFile(vaultFile, partFile)
                        } else {
                            val secretKey = if (masterKey != null && (header.kdfAlgorithm == "MASTER_KEY" || header.salt.isEmpty())) {
                                masterKey
                            } else if (masterKey != null && passwordOrPin.isEmpty()) {
                                masterKey
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

                            FileOutputStream(partFile).buffered(BUFFER_SIZE).use { fos ->
                                val buffer = ByteArray(BUFFER_SIZE)
                                var bytesRead: Int

                                while (fis.read(buffer).also { bytesRead = it } != -1) {
                                    val decryptedChunk = cipher.update(buffer, 0, bytesRead)
                                    if (decryptedChunk != null && decryptedChunk.isNotEmpty()) {
                                        fos.write(decryptedChunk)
                                    }
                                }

                                try {
                                    val finalChunk = cipher.doFinal()
                                    if (finalChunk != null && finalChunk.isNotEmpty()) {
                                        fos.write(finalChunk)
                                    }
                                } catch (e: AEADBadTagException) {
                                    throw VaultIntegrityException(
                                        "Vault integrity verification failed. Password may be incorrect or file has been modified/corrupted.",
                                        e
                                    )
                                } catch (e: Exception) {
                                    throw VaultIntegrityException("Decryption error: ${e.message}", e)
                                }
                                fos.flush()
                            }
                        }
                    }
                }

                if (!partFile.renameTo(destinationFile)) {
                    partFile.copyTo(destinationFile, overwrite = true)
                    partFile.delete()
                }

                header
            }
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

        val secretKey = if (masterKey != null && (header.kdfAlgorithm == "MASTER_KEY" || header.salt.isEmpty())) {
            masterKey
        } else if (masterKey != null && passwordOrPin.isNullOrEmpty()) {
            masterKey
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

        try {
            FileInputStream(vaultFile).buffered(BUFFER_SIZE).use { fis ->
                val (_, headerBytes) = VaultFileFormat.readHeaderWithBytes(fis)

                val cipher = Cipher.getInstance("AES/GCM/NoPadding")
                cipher.init(Cipher.DECRYPT_MODE, secretKey, GCMParameterSpec(VaultKeyDerivation.GCM_TAG_LENGTH_BITS, header.nonce))
                cipher.updateAAD(headerBytes)

                val buffer = ByteArray(BUFFER_SIZE)
                var bytesRead: Int

                while (fis.read(buffer).also { bytesRead = it } != -1) {
                    cipher.update(buffer, 0, bytesRead)
                }

                cipher.doFinal()
            }
            true
        } catch (_: AEADBadTagException) {
            false
        } catch (_: Exception) {
            false
        }
    }


    companion object {
        private const val BUFFER_SIZE = 64 * 1024
    }
}
