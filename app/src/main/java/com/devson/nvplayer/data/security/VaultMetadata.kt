package com.devson.nvplayer.data.security

import com.devson.nvplayer.domain.model.VaultStorageMode
import java.util.Base64

/**
 * Health/integrity status of persistent vault configuration metadata.
 */
enum class VaultMetadataStatus {
    /** Valid and readable metadata found on persistent storage. */
    VALID,
    /** No metadata found on storage. */
    MISSING,
    /** Metadata file exists but is malformed, truncated, or missing required fields. */
    CORRUPTED
}

/**
 * Represents the persistent cryptographic vault metadata stored on public/external storage.
 * Contains KDF parameters, random salt, and an authenticated canary verification tag.
 * Never contains plaintext PINs, passwords, or derived encryption keys.
 */
data class VaultMetadata(
    val metadataVersion: Int = CURRENT_METADATA_VERSION,
    val vaultFormatVersion: Int = CURRENT_VAULT_FORMAT_VERSION,
    val kdfAlgorithm: String = VaultKeyDerivation.DEFAULT_KDF_ALGORITHM,
    val kdfIterations: Int = VaultKeyDerivation.DEFAULT_KDF_ITERATIONS,
    val salt: ByteArray,
    val authCiphertext: ByteArray,
    val authIv: ByteArray,
    val securityQuestion: String = "",
    val securityAnswerSalt: ByteArray = ByteArray(0),
    val securityAnswerHash: String = "",
    val defaultStorageMode: VaultStorageMode = VaultStorageMode.NONE,
    val timestamp: Long = System.currentTimeMillis()
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as VaultMetadata

        if (metadataVersion != other.metadataVersion) return false
        if (vaultFormatVersion != other.vaultFormatVersion) return false
        if (kdfAlgorithm != other.kdfAlgorithm) return false
        if (kdfIterations != other.kdfIterations) return false
        if (!salt.contentEquals(other.salt)) return false
        if (!authCiphertext.contentEquals(other.authCiphertext)) return false
        if (!authIv.contentEquals(other.authIv)) return false
        if (securityQuestion != other.securityQuestion) return false
        if (!securityAnswerSalt.contentEquals(other.securityAnswerSalt)) return false
        if (securityAnswerHash != other.securityAnswerHash) return false
        if (defaultStorageMode != other.defaultStorageMode) return false
        if (timestamp != other.timestamp) return false

        return true
    }

    override fun hashCode(): Int {
        var result = metadataVersion
        result = 31 * result + vaultFormatVersion
        result = 31 * result + kdfAlgorithm.hashCode()
        result = 31 * result + kdfIterations
        result = 31 * result + salt.contentHashCode()
        result = 31 * result + authCiphertext.contentHashCode()
        result = 31 * result + authIv.contentHashCode()
        result = 31 * result + securityQuestion.hashCode()
        result = 31 * result + securityAnswerSalt.contentHashCode()
        result = 31 * result + securityAnswerHash.hashCode()
        result = 31 * result + defaultStorageMode.hashCode()
        result = 31 * result + timestamp.hashCode()
        return result
    }

    companion object {
        const val CURRENT_METADATA_VERSION = 2
        const val CURRENT_VAULT_FORMAT_VERSION = 2
    }
}

/**
 * Pure Kotlin serializer and parser for VaultMetadata.
 * Avoids stubbed Android org.json dependencies in JVM unit test environments.
 */
object VaultMetadataJson {

    fun toJson(metadata: VaultMetadata): String {
        fun escape(s: String): String =
            s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r")

        val saltB64 = Base64.getEncoder().encodeToString(metadata.salt)
        val authCipherB64 = Base64.getEncoder().encodeToString(metadata.authCiphertext)
        val authIvB64 = Base64.getEncoder().encodeToString(metadata.authIv)
        val answerSaltB64 = Base64.getEncoder().encodeToString(metadata.securityAnswerSalt)

        return buildString {
            append("{\n")
            append("  \"metadataVersion\": ${metadata.metadataVersion},\n")
            append("  \"vaultFormatVersion\": ${metadata.vaultFormatVersion},\n")
            append("  \"kdfAlgorithm\": \"${escape(metadata.kdfAlgorithm)}\",\n")
            append("  \"kdfIterations\": ${metadata.kdfIterations},\n")
            append("  \"salt\": \"$saltB64\",\n")
            append("  \"authCiphertext\": \"$authCipherB64\",\n")
            append("  \"authIv\": \"$authIvB64\",\n")
            append("  \"securityQuestion\": \"${escape(metadata.securityQuestion)}\",\n")
            append("  \"securityAnswerSalt\": \"$answerSaltB64\",\n")
            append("  \"securityAnswerHash\": \"${escape(metadata.securityAnswerHash)}\",\n")
            append("  \"defaultStorageMode\": \"${metadata.defaultStorageMode.name}\",\n")
            append("  \"timestamp\": ${metadata.timestamp}\n")
            append("}")
        }
    }

    fun fromJson(jsonText: String): VaultMetadata? {
        return try {
            fun extractString(key: String): String? {
                val pattern = """"$key"\s*:\s*"((?:\\.|[^"\\])*)"""".toRegex()
                val match = pattern.find(jsonText) ?: return null
                val raw = match.groupValues[1]
                return raw.replace("\\\"", "\"").replace("\\\\", "\\").replace("\\n", "\n").replace("\\r", "\r")
            }

            fun extractLong(key: String): Long? {
                val pattern = """"$key"\s*:\s*(-?\d+)""".toRegex()
                return pattern.find(jsonText)?.groupValues?.get(1)?.toLongOrNull()
            }

            val metadataVersion = extractLong("metadataVersion")?.toInt() ?: return null
            val vaultFormatVersion = extractLong("vaultFormatVersion")?.toInt() ?: return null
            val kdfAlgorithm = extractString("kdfAlgorithm") ?: VaultKeyDerivation.DEFAULT_KDF_ALGORITHM
            val kdfIterations = extractLong("kdfIterations")?.toInt() ?: return null

            val saltStr = extractString("salt") ?: return null
            val authCiphertextStr = extractString("authCiphertext") ?: return null
            val authIvStr = extractString("authIv") ?: return null

            val salt = Base64.getDecoder().decode(saltStr)
            val authCiphertext = Base64.getDecoder().decode(authCiphertextStr)
            val authIv = Base64.getDecoder().decode(authIvStr)

            val question = extractString("securityQuestion") ?: ""
            val secAnswerSaltStr = extractString("securityAnswerSalt")
            val secAnswerSalt = if (!secAnswerSaltStr.isNullOrEmpty()) {
                Base64.getDecoder().decode(secAnswerSaltStr)
            } else {
                ByteArray(0)
            }
            val secAnswerHash = extractString("securityAnswerHash") ?: ""
            val defaultStorageMode = extractString("defaultStorageMode")?.let {
                try {
                    VaultStorageMode.valueOf(it)
                } catch (_: Exception) {
                    VaultStorageMode.NONE
                }
            } ?: VaultStorageMode.NONE
            val timestamp = extractLong("timestamp") ?: System.currentTimeMillis()

            VaultMetadata(
                metadataVersion = metadataVersion,
                vaultFormatVersion = vaultFormatVersion,
                kdfAlgorithm = kdfAlgorithm,
                kdfIterations = kdfIterations,
                salt = salt,
                authCiphertext = authCiphertext,
                authIv = authIv,
                securityQuestion = question,
                securityAnswerSalt = secAnswerSalt,
                securityAnswerHash = secAnswerHash,
                defaultStorageMode = defaultStorageMode,
                timestamp = timestamp
            )
        } catch (_: Exception) {
            null
        }
    }
}
