package com.devson.nvplayer.data.security

import com.devson.nvplayer.domain.model.VaultStorageMode
import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.InputStream

/**
 * Handles serialization, parsing, and detection of vault file headers across versions.
 */
object VaultFileFormat {
    val MAGIC_V2_ENCRYPTED = "NSVLT02E".toByteArray(Charsets.US_ASCII)
    val MAGIC_LEGACY_V1 = "NSDVLT01".toByteArray(Charsets.US_ASCII)

    const val FORMAT_VERSION_V2 = 2
    const val FORMAT_VERSION_LEGACY_V1 = 1

    const val CIPHER_ID_AES_256_GCM: Short = 1
    const val CIPHER_ID_LEGACY_CTR: Short = 2

    const val KDF_ID_PBKDF2_HMAC_SHA256: Short = 1
    const val KDF_ID_LEGACY_STATIC: Short = 2

    /**
     * Serializes a Version 2 Encrypted header into the target output stream and returns
     * the exact raw header bytes (for AAD binding in AES-GCM).
     */
    fun writeV2EncryptedHeader(
        dos: DataOutputStream,
        header: VaultFileHeader
    ): ByteArray {
        val baos = ByteArrayOutputStream()
        val memDos = DataOutputStream(baos)

        // 1. Magic (8 bytes)
        memDos.write(MAGIC_V2_ENCRYPTED)
        // 2. Format Version (2 bytes Short)
        memDos.writeShort(FORMAT_VERSION_V2)
        // 3. Storage Mode (2 bytes Short: 1 = ENCRYPTED)
        memDos.writeShort(1)
        // 4. Cipher Algorithm ID (2 bytes Short)
        memDos.writeShort(CIPHER_ID_AES_256_GCM.toInt())
        // 5. KDF Algorithm ID (2 bytes Short)
        memDos.writeShort(KDF_ID_PBKDF2_HMAC_SHA256.toInt())
        // 6. KDF Iterations (4 bytes Int)
        memDos.writeInt(header.kdfIterations)
        // 7. Salt (length + bytes)
        memDos.writeShort(header.salt.size)
        memDos.write(header.salt)
        // 8. Nonce (length + bytes)
        memDos.writeShort(header.nonce.size)
        memDos.write(header.nonce)
        // 9. Original Extension (length + UTF-8 bytes)
        val extBytes = header.originalExtension.trimStart('.').lowercase().toByteArray(Charsets.UTF_8)
        memDos.writeShort(extBytes.size)
        memDos.write(extBytes)
        // 10. Title (length + UTF-8 bytes)
        val titleBytes = header.title.toByteArray(Charsets.UTF_8)
        memDos.writeShort(titleBytes.size)
        memDos.write(titleBytes)
        // 11. Duration ms (8 bytes Long)
        memDos.writeLong(header.durationMs)
        // 12. Plaintext Size (8 bytes Long)
        memDos.writeLong(header.originalPlaintextSize)
        // 13. Date Added (8 bytes Long)
        memDos.writeLong(header.dateAdded)

        memDos.flush()
        val headerBytes = baos.toByteArray()
        dos.write(headerBytes)
        dos.flush()
        return headerBytes
    }

    /**
     * Reads and parses the vault header from an input stream.
     * Returns a pair of the parsed VaultFileHeader and the raw header bytes (for AAD verification).
     */
    fun readHeaderWithBytes(inputStream: InputStream): Pair<VaultFileHeader, ByteArray> {
        val bis = if (inputStream is BufferedInputStream) inputStream else BufferedInputStream(inputStream, 64 * 1024)
        bis.mark(48)
        val probe = ByteArray(24)
        val readCount = bis.read(probe)
        bis.reset()

        if (readCount >= 8 && probe.copyOfRange(0, 8).contentEquals(MAGIC_V2_ENCRYPTED)) {
            val dis = DataInputStream(bis)
            val baos = ByteArrayOutputStream()

            fun readFullyAndRecord(b: ByteArray) {
                dis.readFully(b)
                baos.write(b)
            }
            fun readShortAndRecord(): Short {
                val s = dis.readShort()
                baos.write((s.toInt() ushr 8) and 0xFF)
                baos.write(s.toInt() and 0xFF)
                return s
            }
            fun readIntAndRecord(): Int {
                val i = dis.readInt()
                baos.write((i ushr 24) and 0xFF)
                baos.write((i ushr 16) and 0xFF)
                baos.write((i ushr 8) and 0xFF)
                baos.write(i and 0xFF)
                return i
            }
            fun readLongAndRecord(): Long {
                val l = dis.readLong()
                for (i in 7 downTo 0) {
                    baos.write(((l ushr (i * 8)) and 0xFF).toInt())
                }
                return l
            }

            val magic = ByteArray(8)
            readFullyAndRecord(magic)
            val version = readShortAndRecord().toInt()
            val modeShort = readShortAndRecord().toInt()
            val storageMode = if (modeShort == 1) VaultStorageMode.ENCRYPTED else VaultStorageMode.NONE
            val cipherId = readShortAndRecord().toInt()
            val kdfId = readShortAndRecord().toInt()
            val kdfIterations = readIntAndRecord()

            val saltLen = readShortAndRecord().toInt()
            val salt = ByteArray(saltLen)
            readFullyAndRecord(salt)

            val nonceLen = readShortAndRecord().toInt()
            val nonce = ByteArray(nonceLen)
            readFullyAndRecord(nonce)

            val extLen = readShortAndRecord().toInt()
            val extBytes = ByteArray(extLen)
            readFullyAndRecord(extBytes)
            val originalExtension = String(extBytes, Charsets.UTF_8)

            val titleLen = readShortAndRecord().toInt()
            val titleBytes = ByteArray(titleLen)
            readFullyAndRecord(titleBytes)
            val title = String(titleBytes, Charsets.UTF_8)

            val durationMs = readLongAndRecord()
            val plaintextSize = readLongAndRecord()
            val dateAdded = readLongAndRecord()

            val cipherAlg = if (cipherId == CIPHER_ID_AES_256_GCM.toInt()) "AES/GCM/NoPadding" else "UNKNOWN"
            val kdfAlg = if (kdfId == KDF_ID_PBKDF2_HMAC_SHA256.toInt()) VaultKeyDerivation.DEFAULT_KDF_ALGORITHM else "UNKNOWN"

            val header = VaultFileHeader(
                formatVersion = version,
                storageMode = storageMode,
                encryptionAlgorithm = cipherAlg,
                kdfAlgorithm = kdfAlg,
                kdfIterations = kdfIterations,
                salt = salt,
                nonce = nonce,
                originalExtension = originalExtension,
                title = title,
                durationMs = durationMs,
                originalPlaintextSize = plaintextSize,
                dateAdded = dateAdded
            )

            return Pair(header, baos.toByteArray())
        }

        if (readCount >= 24 && probe.copyOfRange(16, 24).contentEquals(MAGIC_LEGACY_V1)) {
            val dis = DataInputStream(bis)
            val iv = ByteArray(16)
            dis.readFully(iv)
            val legacyMagic = ByteArray(8)
            dis.readFully(legacyMagic)
            val titleLen = dis.readInt()
            val titleBytes = ByteArray(titleLen)
            dis.readFully(titleBytes)
            val title = String(titleBytes, Charsets.UTF_8)
            val durationMs = dis.readLong()
            val fileSize = dis.readLong()
            val dateAdded = dis.readLong()

            val header = VaultFileHeader(
                formatVersion = FORMAT_VERSION_LEGACY_V1,
                storageMode = VaultStorageMode.ENCRYPTED,
                encryptionAlgorithm = "AES/CTR/NoPadding",
                kdfAlgorithm = "LEGACY_STATIC",
                kdfIterations = 1,
                salt = ByteArray(0),
                nonce = iv,
                originalExtension = "mp4",
                title = title,
                durationMs = durationMs,
                originalPlaintextSize = fileSize,
                dateAdded = dateAdded
            )
            return Pair(header, ByteArray(0))
        }

        // Case 3: Raw Unencrypted File (NONE)
        val ext = detectMediaExtension(probe)
        val header = VaultFileHeader(
            formatVersion = FORMAT_VERSION_V2,
            storageMode = VaultStorageMode.NONE,
            encryptionAlgorithm = "NONE",
            kdfAlgorithm = "NONE",
            kdfIterations = 0,
            salt = ByteArray(0),
            nonce = ByteArray(0),
            originalExtension = ext,
            title = "",
            durationMs = 0L,
            originalPlaintextSize = 0L,
            dateAdded = System.currentTimeMillis()
        )
        return Pair(header, ByteArray(0))
    }

    /**
     * Inspects a vault file on disk without reading or decrypting the media payload.
     */
    fun inspectFile(file: File): VaultFileHeader {
        if (!file.exists() || file.length() == 0L) {
            throw VaultInvalidHeaderException("File does not exist or is empty: ${file.absolutePath}")
        }

        return FileInputStream(file).use { fis ->
            val (header, _) = readHeaderWithBytes(fis)
            if (header.storageMode == VaultStorageMode.NONE) {
                header.copy(
                    title = file.nameWithoutExtension,
                    originalPlaintextSize = file.length(),
                    dateAdded = file.lastModified()
                )
            } else {
                header
            }
        }
    }

    /**
     * Sniffs media container magic bytes to infer the original extension.
     */
    fun detectMediaExtension(bytes: ByteArray): String {
        if (bytes.size >= 8) {
            // ISO Base Media File Format (MP4 / M4V / MOV): offset 4..7 == 'ftyp'
            if (bytes[4] == 0x66.toByte() && bytes[5] == 0x74.toByte() &&
                bytes[6] == 0x79.toByte() && bytes[7] == 0x70.toByte()
            ) {
                return "mp4"
            }
        }
        if (bytes.size >= 4) {
            // Matroska / WebM EBML ID: 0x1A 0x45 0xDF 0xA3
            if (bytes[0] == 0x1A.toByte() && bytes[1] == 0x45.toByte() &&
                bytes[2] == 0xDF.toByte() && bytes[3] == 0xA3.toByte()
            ) {
                return "mkv"
            }
            // AVI: RIFF....AVI
            if (bytes[0] == 0x52.toByte() && bytes[1] == 0x49.toByte() &&
                bytes[2] == 0x46.toByte() && bytes[3] == 0x46.toByte()
            ) {
                return "avi"
            }
        }
        return "mp4"
    }
}
