package com.devson.nvplayer.data.security

import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

data class LegacyVaultHeader(
    val title: String,
    val durationMs: Long,
    val fileSize: Long,
    val dateAdded: Long,
    val originalExtension: String,
    val isV1: Boolean
)

/**
 * Handles detection and decryption of legacy Version 1 and Version 0 vault files.
 *
 * CRITICAL SECURITY REQUIREMENT:
 * The hard-coded legacy key constant below is used EXCLUSIVELY for migrating legacy
 * vault files and backward-compatible reading of existing vault media.
 * It MUST NEVER be used for newly created or migrated vault files.
 */
object LegacyVaultManager {
    private const val LEGACY_KEY_SEED = "com.devson.nvplayer_VaultMasterAES256Key_NosvedPlayer_SecureStorage"
    private const val BUFFER_SIZE = 64 * 1024

    private val legacySecretKey: SecretKeySpec by lazy {
        val sha = MessageDigest.getInstance("SHA-256")
        val keyBytes = sha.digest(LEGACY_KEY_SEED.toByteArray(Charsets.UTF_8))
        SecretKeySpec(keyBytes, "AES")
    }

    /**
     * Decrypts the first block of ciphertext using the legacy static key.
     * Used for header sniffing and format classification.
     */
    fun decryptFirstBlock(iv: ByteArray, ciphertext: ByteArray): ByteArray {
        val cipher = Cipher.getInstance("AES/CTR/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, legacySecretKey, IvParameterSpec(iv))
        return cipher.doFinal(ciphertext)
    }

    /**
     * Decrypts a legacy V1 or V0 vault file to the target destination file.
     *
     * @param legacyFile The legacy .vlt file on disk.
     * @param destinationFile The output file where plaintext media bytes will be written.
     * @return The parsed legacy header information.
     */
    fun decryptLegacyFile(legacyFile: File, destinationFile: File): LegacyVaultHeader {
        if (!legacyFile.exists() || legacyFile.length() < 16L) {
            throw VaultCorruptedFileException("File does not exist or is too short to be a legacy vault file: ${legacyFile.absolutePath}")
        }

        val destDir = destinationFile.parentFile
        if (destDir != null && !destDir.exists()) {
            destDir.mkdirs()
        }

        val partFile = File(destDir, destinationFile.name + ".part")
        if (partFile.exists()) partFile.delete()

        try {
            FileInputStream(legacyFile).buffered(BUFFER_SIZE).use { fis ->
                val dis = DataInputStream(fis)
                val iv = ByteArray(16)
                dis.readFully(iv)

                val probeMagic = ByteArray(8)
                val readMagic = dis.read(probeMagic)

                val (header, isV1) = if (readMagic == 8 && probeMagic.contentEquals(VaultFileFormat.MAGIC_LEGACY_V1)) {
                    val titleLen = dis.readInt()
                    if (titleLen !in 0..8192) {
                        throw VaultCorruptedFileException("Corrupted legacy header: invalid title length $titleLen")
                    }
                    val titleBytes = ByteArray(titleLen)
                    dis.readFully(titleBytes)
                    val title = String(titleBytes, Charsets.UTF_8)
                    val durationMs = dis.readLong()
                    val fileSize = dis.readLong()
                    val dateAdded = dis.readLong()
                    Pair(
                        LegacyVaultHeader(
                            title = title,
                            durationMs = durationMs,
                            fileSize = fileSize,
                            dateAdded = dateAdded,
                            originalExtension = "mp4",
                            isV1 = true
                        ),
                        true
                    )
                } else {
                    Pair(
                        LegacyVaultHeader(
                            title = legacyFile.nameWithoutExtension,
                            durationMs = 0L,
                            fileSize = maxOf(0L, legacyFile.length() - 16),
                            dateAdded = legacyFile.lastModified(),
                            originalExtension = "mp4",
                            isV1 = false
                        ),
                        false
                    )
                }

                val cipher = Cipher.getInstance("AES/CTR/NoPadding")
                cipher.init(Cipher.DECRYPT_MODE, legacySecretKey, IvParameterSpec(iv))

                FileOutputStream(partFile).buffered(BUFFER_SIZE).use { fos ->
                    if (!isV1 && readMagic > 0) {
                        val chunk = cipher.update(probeMagic, 0, readMagic)
                        if (chunk != null && chunk.isNotEmpty()) {
                            fos.write(chunk)
                        }
                    }

                    val buffer = ByteArray(BUFFER_SIZE)
                    var bytesRead: Int
                    while (fis.read(buffer).also { bytesRead = it } != -1) {
                        val decrypted = cipher.update(buffer, 0, bytesRead)
                        if (decrypted != null && decrypted.isNotEmpty()) {
                            fos.write(decrypted)
                        }
                    }
                    val finalBytes = cipher.doFinal()
                    if (finalBytes != null && finalBytes.isNotEmpty()) {
                        fos.write(finalBytes)
                    }
                    fos.flush()
                }

                if (!partFile.renameTo(destinationFile)) {
                    partFile.copyTo(destinationFile, overwrite = true)
                    partFile.delete()
                }

                val detectedExt = if (destinationFile.exists() && destinationFile.length() >= 4) {
                    val headerBytes = ByteArray(minOf(64, destinationFile.length().toInt()))
                    FileInputStream(destinationFile).use { it.read(headerBytes) }
                    VaultFileFormat.detectMediaExtension(headerBytes)
                } else {
                    "mp4"
                }

                return header.copy(originalExtension = detectedExt)
            }
        } catch (e: Exception) {
            if (partFile.exists()) partFile.delete()
            if (destinationFile.exists()) destinationFile.delete()
            throw e
        }
    }

    /**
     * Testing fixture utility: Creates a legacy V1 vault file.
     * Accessible internally for test suite validation.
     */
    internal fun createLegacyV1File(
        sourceBytes: ByteArray,
        destinationFile: File,
        title: String = "Legacy Media",
        durationMs: Long = 1000L,
        dateAdded: Long = System.currentTimeMillis()
    ) {
        val iv = ByteArray(16) { 0x42.toByte() }
        val cipher = Cipher.getInstance("AES/CTR/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, legacySecretKey, IvParameterSpec(iv))

        val titleBytes = title.toByteArray(Charsets.UTF_8)
        FileOutputStream(destinationFile).buffered().use { fos ->
            val dos = DataOutputStream(fos)
            dos.write(iv)
            dos.write(VaultFileFormat.MAGIC_LEGACY_V1)
            dos.writeInt(titleBytes.size)
            dos.write(titleBytes)
            dos.writeLong(durationMs)
            dos.writeLong(sourceBytes.size.toLong())
            dos.writeLong(dateAdded)
            dos.flush()

            val ciphertext = cipher.doFinal(sourceBytes)
            dos.write(ciphertext)
            dos.flush()
        }
    }

    /**
     * Testing fixture utility: Creates a legacy V0 vault file.
     * Accessible internally for test suite validation.
     */
    internal fun createLegacyV0File(
        sourceBytes: ByteArray,
        destinationFile: File
    ) {
        val iv = ByteArray(16) { 0x24.toByte() }
        val cipher = Cipher.getInstance("AES/CTR/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, legacySecretKey, IvParameterSpec(iv))

        FileOutputStream(destinationFile).buffered().use { fos ->
            val dos = DataOutputStream(fos)
            dos.write(iv)
            dos.flush()

            val ciphertext = cipher.doFinal(sourceBytes)
            dos.write(ciphertext)
            dos.flush()
        }
    }
}
