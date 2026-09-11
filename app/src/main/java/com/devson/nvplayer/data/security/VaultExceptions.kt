package com.devson.nvplayer.data.security

import java.io.IOException

/**
 * Thrown when an encrypted vault file fails authentication tag verification,
 * has been tampered with, corrupted, or when the decryption key is incorrect.
 */
class VaultIntegrityException(
    message: String = "Vault file integrity check failed. The file may be corrupted, tampered with, or the password is incorrect.",
    cause: Throwable? = null
) : SecurityException(message, cause)

/**
 * Thrown when authentication with the provided PIN/password fails.
 */
class VaultAuthenticationException(
    message: String = "Vault authentication failed: incorrect credential.",
    cause: Throwable? = null
) : SecurityException(message, cause)

/**
 * Thrown when the vault file header is missing, corrupted, or unsupported.
 */
class VaultInvalidHeaderException(
    message: String = "Invalid or unsupported vault file header."
) : IOException(message)

/**
 * Thrown when a vault file is corrupted or truncated during stream processing.
 */
class VaultCorruptedFileException(
    message: String = "Vault file is corrupted or truncated.",
    cause: Throwable? = null
) : IOException(message, cause)
