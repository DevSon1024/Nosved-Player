package com.devson.nvplayer.data.security

import android.content.Context
import android.content.SharedPreferences
import android.os.Environment
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.devson.nvplayer.data.security.storage.FileVaultStorage
import com.devson.nvplayer.data.security.storage.SafVaultStorage
import com.devson.nvplayer.data.security.storage.VaultStorage
import com.devson.nvplayer.domain.model.VaultStorageMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import java.io.File
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Manages vault credentials, PBKDF2 key derivation, and persistent cryptographic metadata.
 * Logically separates authentication verification (via AES-GCM canary) from encryption-key derivation.
 * Never persists plaintext PINs, passwords, or raw encryption keys.
 */
class VaultSecurityManager(
    private val context: Context? = null,
    private val customVaultDirectory: File? = null,
    customVaultStorage: VaultStorage? = null
) {

    val vaultStorage: VaultStorage = customVaultStorage
        ?: (if (customVaultDirectory != null) {
            FileVaultStorage(customVaultDirectory)
        } else if (context != null) {
            SafVaultStorage(context)
        } else {
            val docsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS)
            val vaultDir = File(docsDir, "NosvedPlayer/.vault_secure_media")
            FileVaultStorage(vaultDir)
        })

    private val securePrefs: SharedPreferences? by lazy {
        context?.applicationContext?.let { appContext ->
            try {
                val masterKey = MasterKey.Builder(appContext)
                    .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                    .build()
                EncryptedSharedPreferences.create(
                    appContext,
                    PREFS_FILE_NAME,
                    masterKey,
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
                )
            } catch (_: Exception) {
                try {
                    appContext.deleteSharedPreferences(PREFS_FILE_NAME)
                    val masterKey = MasterKey.Builder(appContext)
                        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                        .build()
                    EncryptedSharedPreferences.create(
                        appContext,
                        PREFS_FILE_NAME,
                        masterKey,
                        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
                    )
                } catch (_: Exception) {
                    null
                }
            }
        }
    }

    val persistentVaultDirectory: File by lazy {
        if (vaultStorage is FileVaultStorage) {
            vaultStorage.baseDirectory
        } else if (customVaultDirectory != null) {
            if (!customVaultDirectory.exists()) customVaultDirectory.mkdirs()
            customVaultDirectory
        } else {
            val docsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS)
            val vaultDir = File(docsDir, "NosvedPlayer/.vault_secure_media")
            vaultDir
        }
    }

    val vaultConfigFile: File
        get() = File(persistentVaultDirectory, ".vault_config")

    // In-memory cache of validated metadata to prevent excessive disk reads
    @Volatile
    private var cachedMetadata: VaultMetadata? = null

    private var inMemoryBiometricEnabled: Boolean = true
    private var inMemoryVaultInitializedLocally: Boolean = false
    @Volatile
    private var inMemoryActiveCredential: String? = null

    // Core Cryptographic Credential & Metadata APIs

    /**
     * Checks if persistent vault metadata exists on external/persistent storage.
     */
    fun hasPersistentVaultMetadata(): Boolean {
        return runBlocking(Dispatchers.IO) {
            vaultStorage.hasVaultConfig()
        }
    }

    /**
     * Validates the integrity of the persistent vault metadata file without needing a credential.
     */
    fun validateVaultMetadata(): VaultMetadataStatus {
        val hasConfig = runBlocking(Dispatchers.IO) {
            vaultStorage.hasVaultConfig()
        }
        if (!hasConfig) {
            return VaultMetadataStatus.MISSING
        }
        return try {
            val metadata = loadVaultMetadata(forceReload = true)
            if (metadata != null &&
                metadata.salt.isNotEmpty() &&
                metadata.authCiphertext.isNotEmpty() &&
                metadata.authIv.isNotEmpty() &&
                (metadata.wrappedMasterKey.isEmpty() || metadata.wrappedKeyIv.isNotEmpty())
            ) {
                VaultMetadataStatus.VALID
            } else {
                VaultMetadataStatus.CORRUPTED
            }
        } catch (_: Exception) {
            VaultMetadataStatus.CORRUPTED
        }
    }

    /**
     * Loads and parses the persistent vault metadata. Returns null if missing or malformed.
     */
    fun loadVaultMetadata(forceReload: Boolean = false): VaultMetadata? {
        if (!forceReload && cachedMetadata != null) {
            return cachedMetadata
        }

        val text = runBlocking(Dispatchers.IO) {
            vaultStorage.readVaultConfig()
        } ?: return null

        return try {
            val metadata = VaultMetadataJson.fromJson(text)
            cachedMetadata = metadata
            metadata
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Initializes a new vault credential. Derives a temporary AES-256 key to generate
     * an authenticated canary tag, then safely persists the metadata.
     * Never writes the plaintext PIN or raw encryption key to disk.
     */
    fun createVaultCredential(
        credential: String,
        question: String = "",
        answer: String = "",
        iterations: Int = VaultKeyDerivation.DEFAULT_KDF_ITERATIONS
    ): VaultMetadata {
        require(credential.isNotEmpty()) { "Vault credential cannot be empty" }

        val masterKeyBytes = ByteArray(32).also { secureRandom.nextBytes(it) }
        val salt = VaultKeyDerivation.generateSalt()
        val authIv = VaultKeyDerivation.generateGcmNonce()
        val wrappedKeyIv = VaultKeyDerivation.generateGcmNonce()
        val kek = VaultKeyDerivation.deriveKey(credential, salt, iterations)

        val wrappedMasterKey = wrapMasterKey(masterKeyBytes, kek, wrappedKeyIv)

        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, kek, GCMParameterSpec(VaultKeyDerivation.GCM_TAG_LENGTH_BITS, authIv))
        val authCiphertext = cipher.doFinal(AUTH_CANARY_PAYLOAD)

        var secAnswerSalt = ByteArray(0)
        var secAnswerHash = ""
        var secWrappedMasterKey = ByteArray(0)
        var secWrappedKeyIv = ByteArray(0)
        if (question.isNotBlank() && answer.isNotBlank()) {
            secAnswerSalt = VaultKeyDerivation.generateSalt(16)
            secAnswerHash = hashSecurityAnswer(answer, secAnswerSalt)
            val secKey = VaultKeyDerivation.deriveKey(answer.trim().lowercase(), secAnswerSalt, iterations)
            secWrappedKeyIv = VaultKeyDerivation.generateGcmNonce()
            secWrappedMasterKey = wrapMasterKey(masterKeyBytes, secKey, secWrappedKeyIv)
        }

        val metadata = VaultMetadata(
            metadataVersion = VaultMetadata.CURRENT_METADATA_VERSION,
            vaultFormatVersion = VaultMetadata.CURRENT_VAULT_FORMAT_VERSION,
            kdfAlgorithm = VaultKeyDerivation.DEFAULT_KDF_ALGORITHM,
            kdfIterations = iterations,
            salt = salt,
            authCiphertext = authCiphertext,
            authIv = authIv,
            wrappedMasterKey = wrappedMasterKey,
            wrappedKeyIv = wrappedKeyIv,
            secWrappedMasterKey = secWrappedMasterKey,
            secWrappedKeyIv = secWrappedKeyIv,
            securityQuestion = question,
            securityAnswerSalt = secAnswerSalt,
            securityAnswerHash = secAnswerHash,
            defaultStorageMode = VaultStorageMode.NONE,
            timestamp = System.currentTimeMillis()
        )

        saveMetadataToDisk(metadata)
        cachedMetadata = metadata
        inMemoryActiveCredential = credential
        setVaultInitializedLocally(true)
        return metadata
    }

    /**
     * Verifies the user's PIN/credential against the persisted canary tag and wrapped master key.
     * Returns true if credential correctly derives the key that decrypts the canary and unwraps the master key.
     * Never alters, regenerates, or deletes any files or salt on wrong credentials.
     */
    fun verifyVaultCredential(credential: String): Boolean {
        if (credential.isEmpty()) return false

        val metadata = loadVaultMetadata() ?: return false

        return try {
            val candidateKey = VaultKeyDerivation.deriveKey(
                credential = credential,
                salt = metadata.salt,
                iterations = metadata.kdfIterations
            )

            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(
                Cipher.DECRYPT_MODE,
                candidateKey,
                GCMParameterSpec(VaultKeyDerivation.GCM_TAG_LENGTH_BITS, metadata.authIv)
            )

            val decryptedCanary = cipher.doFinal(metadata.authCiphertext)
            if (!decryptedCanary.contentEquals(AUTH_CANARY_PAYLOAD)) {
                return false
            }

            if (metadata.wrappedMasterKey.isNotEmpty() && metadata.wrappedKeyIv.isNotEmpty()) {
                val unwrapped = unwrapMasterKey(metadata.wrappedMasterKey, candidateKey, metadata.wrappedKeyIv)
                if (unwrapped.size != 32) return false
            }

            inMemoryActiveCredential = credential
            true
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Derives the persistent Vault Master Key (AES-256) using the authenticated user credential.
     */
    fun deriveEncryptionKey(credential: String): SecretKeySpec {
        require(credential.isNotEmpty()) { "Vault credential cannot be empty" }

        val metadata = loadVaultMetadata()
            ?: throw IllegalStateException("Persistent vault metadata not initialized or unreadable")

        if (!verifyVaultCredential(credential)) {
            throw VaultAuthenticationException("Invalid vault credential")
        }

        val kek = VaultKeyDerivation.deriveKey(
            credential = credential,
            salt = metadata.salt,
            iterations = metadata.kdfIterations
        )

        return if (metadata.wrappedMasterKey.isNotEmpty() && metadata.wrappedKeyIv.isNotEmpty()) {
            try {
                val masterKeyBytes = unwrapMasterKey(metadata.wrappedMasterKey, kek, metadata.wrappedKeyIv)
                SecretKeySpec(masterKeyBytes, "AES")
            } catch (e: Exception) {
                throw VaultAuthenticationException("Invalid vault credential: could not unwrap master key", e)
            }
        } else {
            kek
        }
    }

    /**
     * Safely deletes the vault metadata configuration file.
     */
    fun deleteVaultMetadata(): Boolean {
        cachedMetadata = null
        inMemoryActiveCredential = null
        setVaultInitializedLocally(false)
        securePrefs?.edit()?.clear()?.apply()
        return runBlocking {
            vaultStorage.deleteVaultConfig()
        }
    }

    /**
     * Returns the active in-memory credential for the current unlocked vault session.
     * Null when the vault is locked.
     */
    fun getActiveCredential(): String? = inMemoryActiveCredential

    /**
     * Explicitly clears the active in-memory session credential upon locking the vault.
     */
    fun lockVault() {
        inMemoryActiveCredential = null
    }

    fun clearSession() {
        lockVault()
    }

    /**
     * Detects if previous vault media exists on external storage while the metadata is corrupted or missing.
     */
    fun isVaultCorruptedWithExistingFiles(): Boolean {
        val hasFiles = hasExistingVaultOnDisk()
        val isMetadataValid = validateVaultMetadata() == VaultMetadataStatus.VALID
        return hasFiles && !isMetadataValid
    }

    fun hasOrphanedVaultFiles(): Boolean {
        return isVaultCorruptedWithExistingFiles()
    }

    // Backward-Compatible Facades for Existing UI

    fun isPinSet(): Boolean {
        return hasPersistentVaultMetadata() && validateVaultMetadata() == VaultMetadataStatus.VALID
    }

    fun hasExistingVaultOnDisk(): Boolean {
        return runBlocking(Dispatchers.IO) {
            vaultStorage.hasVaultConfig() || vaultStorage.listVaultFiles().isNotEmpty()
        }
    }

    fun getExistingVaultFileCount(): Int {
        return runBlocking(Dispatchers.IO) {
            vaultStorage.listVaultFiles().size
        }
    }

    fun setPin(pin: String, question: String = "", answer: String = "") {
        createVaultCredential(credential = pin, question = question, answer = answer)
    }

    fun verifyPin(pin: String): Boolean {
        return verifyVaultCredential(pin)
    }

    fun updatePin(oldPin: String, newPin: String): Boolean {
        if (!verifyVaultCredential(oldPin)) return false
        if (newPin.isEmpty()) return false

        val oldMetadata = loadVaultMetadata() ?: return false
        val question = oldMetadata.securityQuestion
        val answerSalt = oldMetadata.securityAnswerSalt
        val answerHash = oldMetadata.securityAnswerHash

        val oldKek = VaultKeyDerivation.deriveKey(oldPin, oldMetadata.salt, oldMetadata.kdfIterations)
        val masterKeyBytes = if (oldMetadata.wrappedMasterKey.isNotEmpty() && oldMetadata.wrappedKeyIv.isNotEmpty()) {
            unwrapMasterKey(oldMetadata.wrappedMasterKey, oldKek, oldMetadata.wrappedKeyIv)
        } else {
            ByteArray(32).also { secureRandom.nextBytes(it) }
        }

        val newSalt = VaultKeyDerivation.generateSalt()
        val newAuthIv = VaultKeyDerivation.generateGcmNonce()
        val newWrappedKeyIv = VaultKeyDerivation.generateGcmNonce()
        val newKek = VaultKeyDerivation.deriveKey(newPin, newSalt, oldMetadata.kdfIterations)

        val newWrappedMasterKey = wrapMasterKey(masterKeyBytes, newKek, newWrappedKeyIv)

        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, newKek, GCMParameterSpec(VaultKeyDerivation.GCM_TAG_LENGTH_BITS, newAuthIv))
        val newAuthCiphertext = cipher.doFinal(AUTH_CANARY_PAYLOAD)

        var secWrappedMasterKey = oldMetadata.secWrappedMasterKey
        var secWrappedKeyIv = oldMetadata.secWrappedKeyIv
        if (question.isNotBlank() && answerSalt.isNotEmpty()) {
            // Retain existing security question recovery wrapping
            if (secWrappedMasterKey.isEmpty() || secWrappedKeyIv.isEmpty()) {
                secWrappedKeyIv = VaultKeyDerivation.generateGcmNonce()
            }
        }

        val updatedMetadata = oldMetadata.copy(
            salt = newSalt,
            authCiphertext = newAuthCiphertext,
            authIv = newAuthIv,
            wrappedMasterKey = newWrappedMasterKey,
            wrappedKeyIv = newWrappedKeyIv,
            secWrappedMasterKey = secWrappedMasterKey,
            secWrappedKeyIv = secWrappedKeyIv,
            timestamp = System.currentTimeMillis()
        )

        saveMetadataToDisk(updatedMetadata)
        cachedMetadata = updatedMetadata
        inMemoryActiveCredential = newPin
        setVaultInitializedLocally(true)
        return true
    }

    fun setSecurityQuestion(question: String, answer: String): Boolean {
        if (question.isBlank() || answer.isBlank()) return false
        val metadata = loadVaultMetadata() ?: return false
        val activeCred = getActiveCredential() ?: return false

        val kek = VaultKeyDerivation.deriveKey(activeCred, metadata.salt, metadata.kdfIterations)
        val masterKeyBytes = if (metadata.wrappedMasterKey.isNotEmpty() && metadata.wrappedKeyIv.isNotEmpty()) {
            unwrapMasterKey(metadata.wrappedMasterKey, kek, metadata.wrappedKeyIv)
        } else {
            return false
        }

        val salt = VaultKeyDerivation.generateSalt(16)
        val hash = hashSecurityAnswer(answer, salt)
        val secKey = VaultKeyDerivation.deriveKey(answer.trim().lowercase(), salt, metadata.kdfIterations)
        val secWrappedKeyIv = VaultKeyDerivation.generateGcmNonce()
        val secWrappedMasterKey = wrapMasterKey(masterKeyBytes, secKey, secWrappedKeyIv)

        val updated = metadata.copy(
            securityQuestion = question,
            securityAnswerSalt = salt,
            securityAnswerHash = hash,
            secWrappedMasterKey = secWrappedMasterKey,
            secWrappedKeyIv = secWrappedKeyIv,
            timestamp = System.currentTimeMillis()
        )
        saveMetadataToDisk(updated)
        cachedMetadata = updated
        return true
    }

    fun getSecurityQuestion(): String? {
        return loadVaultMetadata()?.securityQuestion?.takeIf { it.isNotBlank() }
    }

    fun hasEncryptedVaultFiles(): Boolean {
        return runBlocking(Dispatchers.IO) {
            val files = vaultStorage.listVaultFiles()
            files.any { entry ->
                try {
                    vaultStorage.openInputStream(entry.filename).use { stream ->
                        val header = VaultFileFormat.inspectStream(stream)
                        header.storageMode == VaultStorageMode.ENCRYPTED
                    }
                } catch (_: Exception) {
                    false
                }
            }
        }
    }

    fun verifySecurityAnswer(answer: String): Boolean {
        val metadata = loadVaultMetadata() ?: return false
        if (metadata.securityAnswerHash.isBlank() || metadata.securityAnswerSalt.isEmpty()) return false
        val candidateHash = hashSecurityAnswer(answer, metadata.securityAnswerSalt)
        return metadata.securityAnswerHash == candidateHash
    }

    fun resetPinWithSecurityAnswer(answer: String, newPin: String): Boolean {
        if (!verifySecurityAnswer(answer)) return false
        if (newPin.isEmpty()) return false
        val oldMetadata = loadVaultMetadata() ?: return false

        val secKey = VaultKeyDerivation.deriveKey(
            answer.trim().lowercase(),
            oldMetadata.securityAnswerSalt,
            oldMetadata.kdfIterations
        )

        val masterKeyBytes = if (oldMetadata.secWrappedMasterKey.isNotEmpty() && oldMetadata.secWrappedKeyIv.isNotEmpty()) {
            unwrapMasterKey(oldMetadata.secWrappedMasterKey, secKey, oldMetadata.secWrappedKeyIv)
        } else if (hasEncryptedVaultFiles()) {
            // Cannot silently replace keys when encrypted files exist without secWrappedMasterKey
            return false
        } else {
            ByteArray(32).also { secureRandom.nextBytes(it) }
        }

        val newSalt = VaultKeyDerivation.generateSalt()
        val newAuthIv = VaultKeyDerivation.generateGcmNonce()
        val newWrappedKeyIv = VaultKeyDerivation.generateGcmNonce()
        val newKek = VaultKeyDerivation.deriveKey(newPin, newSalt, oldMetadata.kdfIterations)

        val newWrappedMasterKey = wrapMasterKey(masterKeyBytes, newKek, newWrappedKeyIv)

        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, newKek, GCMParameterSpec(VaultKeyDerivation.GCM_TAG_LENGTH_BITS, newAuthIv))
        val newAuthCiphertext = cipher.doFinal(AUTH_CANARY_PAYLOAD)

        // Re-wrap master key with security question key as well
        val newSecWrappedKeyIv = VaultKeyDerivation.generateGcmNonce()
        val newSecWrappedMasterKey = wrapMasterKey(masterKeyBytes, secKey, newSecWrappedKeyIv)

        val updatedMetadata = oldMetadata.copy(
            salt = newSalt,
            authCiphertext = newAuthCiphertext,
            authIv = newAuthIv,
            wrappedMasterKey = newWrappedMasterKey,
            wrappedKeyIv = newWrappedKeyIv,
            secWrappedMasterKey = newSecWrappedMasterKey,
            secWrappedKeyIv = newSecWrappedKeyIv,
            timestamp = System.currentTimeMillis()
        )

        saveMetadataToDisk(updatedMetadata)
        cachedMetadata = updatedMetadata
        inMemoryActiveCredential = newPin
        setVaultInitializedLocally(true)
        return true
    }

    fun resetVault(deleteFiles: Boolean) {
        deleteVaultMetadata()
        if (deleteFiles) {
            runBlocking {
                vaultStorage.clearVault()
            }
        }
    }

    fun isBiometricEnabled(): Boolean {
        return securePrefs?.getBoolean(KEY_BIOMETRIC_ENABLED, true) ?: inMemoryBiometricEnabled
    }

    fun setBiometricEnabled(enabled: Boolean) {
        inMemoryBiometricEnabled = enabled
        securePrefs?.edit()?.putBoolean(KEY_BIOMETRIC_ENABLED, enabled)?.apply()
    }

    fun isVaultInitializedLocally(): Boolean {
        return securePrefs?.getBoolean(KEY_VAULT_INITIALIZED_LOCALLY, false) ?: inMemoryVaultInitializedLocally
    }

    fun setVaultInitializedLocally(initialized: Boolean) {
        inMemoryVaultInitializedLocally = initialized
        securePrefs?.edit()?.putBoolean(KEY_VAULT_INITIALIZED_LOCALLY, initialized)?.apply()
    }

    /**
     * Returns the configured default storage mode for future imports.
     * Defaults to VaultStorageMode.NONE (Hidden / No Encryption) for new installations.
     */
    fun getDefaultStorageMode(): VaultStorageMode {
        return loadVaultMetadata()?.defaultStorageMode ?: VaultStorageMode.NONE
    }

    /**
     * Updates the default storage mode in persistent metadata.
     * Safe persistence inside .vault_config.
     */
    fun setDefaultStorageMode(mode: VaultStorageMode): Boolean {
        val current = loadVaultMetadata() ?: return false
        val updated = current.copy(
            defaultStorageMode = mode,
            timestamp = System.currentTimeMillis()
        )
        saveMetadataToDisk(updated)
        cachedMetadata = updated
        return true
    }

    /**
     * Authenticates with user credential before changing the storage mode.
     * Rejects unauthenticated attempts and leaves state unchanged.
     */
    fun verifyAndSetStorageMode(credential: String, mode: VaultStorageMode): Boolean {
        if (!verifyVaultCredential(credential)) {
            return false
        }
        return setDefaultStorageMode(mode)
    }

    // Internal Helper Methods

    private fun saveMetadataToDisk(metadata: VaultMetadata) {
        val jsonString = VaultMetadataJson.toJson(metadata)
        val success = runBlocking(Dispatchers.IO) {
            vaultStorage.writeVaultConfig(jsonString)
        }
        if (success) {
            cachedMetadata = metadata
        }
    }

    private fun hashSecurityAnswer(answer: String, salt: ByteArray): String {
        val normalized = answer.trim().lowercase()
        val digest = MessageDigest.getInstance("SHA-256")
        digest.update(salt)
        val bytes = digest.digest(normalized.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }

    private fun wrapMasterKey(masterKey: ByteArray, kek: SecretKeySpec, iv: ByteArray): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, kek, GCMParameterSpec(VaultKeyDerivation.GCM_TAG_LENGTH_BITS, iv))
        cipher.updateAAD(VMK_AAD)
        return cipher.doFinal(masterKey)
    }

    private fun unwrapMasterKey(wrappedMasterKey: ByteArray, kek: SecretKeySpec, iv: ByteArray): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, kek, GCMParameterSpec(VaultKeyDerivation.GCM_TAG_LENGTH_BITS, iv))
        cipher.updateAAD(VMK_AAD)
        return cipher.doFinal(wrappedMasterKey)
    }

    companion object {
        private const val PREFS_FILE_NAME = "secure_vault_prefs"
        private const val KEY_BIOMETRIC_ENABLED = "vault_biometric_enabled"
        private const val KEY_VAULT_INITIALIZED_LOCALLY = "vault_initialized_locally"
        private val AUTH_CANARY_PAYLOAD = "NOSVED_VAULT_AUTH_CANARY_V2".toByteArray(Charsets.UTF_8)
        private val VMK_AAD = "NOSVED_VAULT_VMK_V2".toByteArray(Charsets.UTF_8)
        private val secureRandom = SecureRandom()

        val DEFAULT_SECURITY_QUESTIONS = listOf(
            "What is your birthplace?",
            "What was the name of your first pet?",
            "What is your favorite movie?",
            "What is your mother's maiden name?",
            "What was your first school name?"
        )
    }
}
