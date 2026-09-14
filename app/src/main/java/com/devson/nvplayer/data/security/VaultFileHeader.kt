package com.devson.nvplayer.data.security

import com.devson.nvplayer.domain.model.VaultStorageMode

/**
 * Represents the parsed versioned metadata header of a vault file.
 */
data class VaultFileHeader(
    val formatVersion: Int,
    val storageMode: VaultStorageMode,
    val encryptionAlgorithm: String = "AES/GCM/NoPadding",
    val kdfAlgorithm: String = VaultKeyDerivation.DEFAULT_KDF_ALGORITHM,
    val kdfIterations: Int = VaultKeyDerivation.DEFAULT_KDF_ITERATIONS,
    val salt: ByteArray = ByteArray(0),
    val nonce: ByteArray = ByteArray(0),
    val originalExtension: String = "mp4",
    val title: String = "",
    val durationMs: Long = 0L,
    val originalPlaintextSize: Long = 0L,
    val dateAdded: Long = System.currentTimeMillis()
) {
    val isEncrypted: Boolean
        get() = storageMode == VaultStorageMode.ENCRYPTED

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as VaultFileHeader

        if (formatVersion != other.formatVersion) return false
        if (storageMode != other.storageMode) return false
        if (encryptionAlgorithm != other.encryptionAlgorithm) return false
        if (kdfAlgorithm != other.kdfAlgorithm) return false
        if (kdfIterations != other.kdfIterations) return false
        if (!salt.contentEquals(other.salt)) return false
        if (!nonce.contentEquals(other.nonce)) return false
        if (originalExtension != other.originalExtension) return false
        if (title != other.title) return false
        if (durationMs != other.durationMs) return false
        if (originalPlaintextSize != other.originalPlaintextSize) return false
        if (dateAdded != other.dateAdded) return false

        return true
    }

    override fun hashCode(): Int {
        var result = formatVersion
        result = 31 * result + storageMode.hashCode()
        result = 31 * result + encryptionAlgorithm.hashCode()
        result = 31 * result + kdfAlgorithm.hashCode()
        result = 31 * result + kdfIterations
        result = 31 * result + salt.contentHashCode()
        result = 31 * result + nonce.contentHashCode()
        result = 31 * result + originalExtension.hashCode()
        result = 31 * result + title.hashCode()
        result = 31 * result + durationMs.hashCode()
        result = 31 * result + originalPlaintextSize.hashCode()
        result = 31 * result + dateAdded.hashCode()
        return result
    }
}
