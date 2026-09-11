package com.devson.nvplayer.domain.model

/**
 * Explicit storage modes for items held in the Privacy Vault.
 */
enum class VaultStorageMode {
    /**
     * Hidden only: File is moved into the vault directory and renamed with .vlt extension.
     * The media payload remains 100% byte-for-byte identical to the original media file.
     * Renaming back to the original media extension (e.g., .mp4, .mkv) immediately restores
     * normal playback in standard media players.
     */
    NONE,

    /**
     * Authenticated encryption: Media payload is encrypted using AES-256-GCM with a key
     * derived via PBKDF2 from the user credential and a persistent per-file salt.
     * Renaming to .mp4/.mkv will NOT produce a playable file. Tampered or corrupted data
     * fails integrity validation.
     */
    ENCRYPTED
}
