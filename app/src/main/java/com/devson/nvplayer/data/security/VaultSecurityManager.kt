package com.devson.nvplayer.data.security

import android.content.Context
import android.content.SharedPreferences
import android.os.Environment
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.devson.nvplayer.domain.model.VaultStorageMode
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.AEADBadTagException
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
    private val customVaultDirectory: File? = null
) {

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
        if (customVaultDirectory != null) {
            if (!customVaultDirectory.exists()) {
                customVaultDirectory.mkdirs()
            }
            customVaultDirectory
        } else {
            val docsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS)
            val vaultDir = File(docsDir, "NosvedPlayer/.vault_secure_media")
            if (!vaultDir.exists()) {
                vaultDir.mkdirs()
            }
            val nomedia = File(vaultDir, ".nomedia")
            if (!nomedia.exists()) {
                try { nomedia.createNewFile() } catch (_: Exception) {}
            }
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
        return vaultConfigFile.exists() && vaultConfigFile.length() > 0L
    }

    /**
     * Validates the integrity of the persistent vault metadata file without needing a credential.
     */
    fun validateVaultMetadata(): VaultMetadataStatus {
        if (!vaultConfigFile.exists() || vaultConfigFile.length() == 0L) {
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
        if (!forceReload) {
            cachedMetadata?.let { return it }
        }

        if (!vaultConfigFile.exists() || vaultConfigFile.length() == 0L) {
            return null
        }

        return try {
            val text = vaultConfigFile.readText(Charsets.UTF_8)
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
        if (question.isNotBlank() && answer.isNotBlank()) {
            secAnswerSalt = VaultKeyDerivation.generateSalt(16)
            secAnswerHash = hashSecurityAnswer(answer, secAnswerSalt)
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
        } catch (_: AEADBadTagException) {
            false
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Derives the in-memory AES-256 SecretKeySpec for file operations after verifying the credential.
     * Unwraps the persisted Vault Master Key using the PIN-derived KEK.
     * The raw master key is NEVER stored on disk in plaintext.
     */
    fun deriveEncryptionKey(credential: String): SecretKeySpec {
        val metadata = loadVaultMetadata()
            ?: throw VaultInvalidHeaderException("Vault metadata is missing or corrupted")

        if (!verifyVaultCredential(credential)) {
            throw VaultAuthenticationException("Incorrect vault credential")
        }

        val kek = VaultKeyDerivation.deriveKey(
            credential = credential,
            salt = metadata.salt,
            iterations = metadata.kdfIterations
        )

        return if (metadata.wrappedMasterKey.isNotEmpty() && metadata.wrappedKeyIv.isNotEmpty()) {
            val masterKeyBytes = unwrapMasterKey(metadata.wrappedMasterKey, kek, metadata.wrappedKeyIv)
            SecretKeySpec(masterKeyBytes, "AES")
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
        return if (vaultConfigFile.exists()) {
            vaultConfigFile.delete()
        } else {
            true
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
    fun clearSession() {
        inMemoryActiveCredential = null
    }

    /**
     * Checks if .vlt media files exist on disk while metadata is missing or corrupted (recovery state).
     */
    fun hasOrphanedVaultFiles(): Boolean {
        val hasFiles = hasExistingVaultOnDisk()
        val isMetadataValid = validateVaultMetadata() == VaultMetadataStatus.VALID
        return hasFiles && !isMetadataValid
    }

    
    // Backward-Compatible Facades for Existing UI
    

    fun isPinSet(): Boolean {
        return hasPersistentVaultMetadata() && validateVaultMetadata() == VaultMetadataStatus.VALID
    }

    fun hasExistingVaultOnDisk(): Boolean {
        val files = persistentVaultDirectory.listFiles { file -> file.extension == "vlt" }
        return files != null && files.isNotEmpty()
    }

    fun getExistingVaultFileCount(): Int {
        val files = persistentVaultDirectory.listFiles { file -> file.extension == "vlt" }
        return files?.size ?: 0
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
            // Upgrade legacy V2: previously the derived key was used directly as media encryption key
            oldKek.encoded
        }

        // Generate new salt and nonces under the new PIN
        val newSalt = VaultKeyDerivation.generateSalt()
        val newAuthIv = VaultKeyDerivation.generateGcmNonce()
        val newWrappedKeyIv = VaultKeyDerivation.generateGcmNonce()
        val newKek = VaultKeyDerivation.deriveKey(newPin, newSalt, oldMetadata.kdfIterations)

        // Architecture A: Re-wrap the SAME Vault Master Key with the new KEK
        val newWrappedMasterKey = wrapMasterKey(masterKeyBytes, newKek, newWrappedKeyIv)

        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, newKek, GCMParameterSpec(VaultKeyDerivation.GCM_TAG_LENGTH_BITS, newAuthIv))
        val newAuthCiphertext = cipher.doFinal(AUTH_CANARY_PAYLOAD)

        val updatedMetadata = oldMetadata.copy(
            salt = newSalt,
            authCiphertext = newAuthCiphertext,
            authIv = newAuthIv,
            wrappedMasterKey = newWrappedMasterKey,
            wrappedKeyIv = newWrappedKeyIv,
            securityQuestion = question,
            securityAnswerSalt = answerSalt,
            securityAnswerHash = answerHash,
            timestamp = System.currentTimeMillis()
        )

        saveMetadataToDisk(updatedMetadata)
        cachedMetadata = updatedMetadata
        inMemoryActiveCredential = newPin
        return true
    }

    fun getSecurityQuestion(): String? {
        return loadVaultMetadata()?.securityQuestion?.takeIf { it.isNotBlank() }
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

        // Security question allows resetting authentication access for the vault.
        // NOTE: Because the Vault Master Key was protected by the user's previous PIN-derived KEK,
        // answering the security question cannot decrypt existing encrypted videos without the original PIN.
        // A new Vault Master Key is generated for future files.
        val newMasterKeyBytes = ByteArray(32).also { secureRandom.nextBytes(it) }
        val newSalt = VaultKeyDerivation.generateSalt()
        val newAuthIv = VaultKeyDerivation.generateGcmNonce()
        val newWrappedKeyIv = VaultKeyDerivation.generateGcmNonce()
        val newKek = VaultKeyDerivation.deriveKey(newPin, newSalt, oldMetadata.kdfIterations)

        val newWrappedMasterKey = wrapMasterKey(newMasterKeyBytes, newKek, newWrappedKeyIv)

        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, newKek, GCMParameterSpec(VaultKeyDerivation.GCM_TAG_LENGTH_BITS, newAuthIv))
        val newAuthCiphertext = cipher.doFinal(AUTH_CANARY_PAYLOAD)

        val updatedMetadata = oldMetadata.copy(
            salt = newSalt,
            authCiphertext = newAuthCiphertext,
            authIv = newAuthIv,
            wrappedMasterKey = newWrappedMasterKey,
            wrappedKeyIv = newWrappedKeyIv,
            timestamp = System.currentTimeMillis()
        )

        saveMetadataToDisk(updatedMetadata)
        cachedMetadata = updatedMetadata
        return true
    }

    fun resetVault(deleteFiles: Boolean) {
        deleteVaultMetadata()
        if (deleteFiles && persistentVaultDirectory.exists()) {
            persistentVaultDirectory.listFiles()?.forEach { file ->
                try { file.delete() } catch (_: Exception) {}
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

        val parent = vaultConfigFile.parentFile
        if (parent != null && !parent.exists()) {
            parent.mkdirs()
        }

        val tempConfig = File(vaultConfigFile.parentFile, ".vault_config.tmp")
        try {
            tempConfig.writeText(jsonString, Charsets.UTF_8)
            if (vaultConfigFile.exists()) {
                vaultConfigFile.delete()
            }
            if (!tempConfig.renameTo(vaultConfigFile)) {
                FileOutputStream(vaultConfigFile, false).use { out ->
                    tempConfig.inputStream().use { input ->
                        input.copyTo(out)
                    }
                    out.flush()
                }
                tempConfig.delete()
            }
        } catch (e: Exception) {
            try {
                FileOutputStream(vaultConfigFile, false).use { out ->
                    out.write(jsonString.toByteArray(Charsets.UTF_8))
                    out.flush()
                }
            } catch (_: Exception) {
                vaultConfigFile.writeText(jsonString, Charsets.UTF_8)
            } finally {
                if (tempConfig.exists()) {
                    try { tempConfig.delete() } catch (_: Exception) {}
                }
            }
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
