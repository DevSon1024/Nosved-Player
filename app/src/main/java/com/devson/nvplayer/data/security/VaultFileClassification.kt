package com.devson.nvplayer.data.security

/**
 * Classification of files found in the vault directory.
 */
enum class VaultFileClassification {
    /**
     * Modern Version 2 authenticated encrypted container (AES-256-GCM + PBKDF2).
     */
    CURRENT_SECURE_ENCRYPTED,

    /**
     * Modern Version 2 unencrypted media file with original bytes preserved.
     */
    CURRENT_NO_ENCRYPTION,

    /**
     * Legacy Version 1 or Version 0 file encrypted using the global static AES-CTR key.
     * Eligible for safe, user-guided migration to CURRENT_SECURE_ENCRYPTED.
     */
    LEGACY_ENCRYPTED,

    /**
     * File is corrupted, zero-byte, or does not conform to any recognized vault format.
     */
    INVALID_UNKNOWN
}
