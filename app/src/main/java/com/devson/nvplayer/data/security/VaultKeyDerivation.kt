package com.devson.nvplayer.data.security

import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * Handles cryptographically secure key derivation (PBKDF2) and random salt/nonce generation.
 */
object VaultKeyDerivation {
    const val DEFAULT_KDF_ALGORITHM = "PBKDF2WithHmacSHA256"
    const val DEFAULT_KDF_ITERATIONS = 100_000
    const val DEFAULT_SALT_BYTES = 32
    const val GCM_NONCE_BYTES = 12
    const val GCM_TAG_LENGTH_BITS = 128
    const val KEY_LENGTH_BITS = 256

    private val secureRandom = SecureRandom()

    /**
     * Generates a cryptographically secure random salt of the given length.
     */
    fun generateSalt(length: Int = DEFAULT_SALT_BYTES): ByteArray {
        val salt = ByteArray(length)
        secureRandom.nextBytes(salt)
        return salt
    }

    /**
     * Generates a unique cryptographically secure random nonce for AES-GCM.
     */
    fun generateGcmNonce(): ByteArray {
        val nonce = ByteArray(GCM_NONCE_BYTES)
        secureRandom.nextBytes(nonce)
        return nonce
    }

    /**
     * Derives an AES-256 SecretKeySpec from the user's password/PIN and salt using PBKDF2WithHmacSHA256.
     * Guaranteed to deterministically reconstruct the same key given identical credential, salt, and iterations.
     */
    fun deriveKey(
        credential: CharArray,
        salt: ByteArray,
        iterations: Int = DEFAULT_KDF_ITERATIONS,
        keyLengthBits: Int = KEY_LENGTH_BITS
    ): SecretKeySpec {
        require(credential.isNotEmpty()) { "Credential must not be empty" }
        require(salt.isNotEmpty()) { "Salt must not be empty" }
        require(iterations > 0) { "Iterations must be positive" }

        val spec = PBEKeySpec(credential, salt, iterations, keyLengthBits)
        try {
            val factory = SecretKeyFactory.getInstance(DEFAULT_KDF_ALGORITHM)
            val keyBytes = factory.generateSecret(spec).encoded
            return SecretKeySpec(keyBytes, "AES")
        } finally {
            spec.clearPassword()
        }
    }

    /**
     * Convenience overload for String credential.
     */
    fun deriveKey(
        credential: String,
        salt: ByteArray,
        iterations: Int = DEFAULT_KDF_ITERATIONS,
        keyLengthBits: Int = KEY_LENGTH_BITS
    ): SecretKeySpec {
        val charArray = credential.toCharArray()
        return try {
            deriveKey(charArray, salt, iterations, keyLengthBits)
        } finally {
            charArray.fill('0')
        }
    }
}
