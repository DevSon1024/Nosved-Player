package com.devson.nvplayer.data.security

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class VaultSecurityManagerTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun firstTimeVaultCreation() {
        val vaultDir = tempFolder.newFolder("vault_first_time")
        val manager = VaultSecurityManager(customVaultDirectory = vaultDir)

        assertFalse(manager.hasPersistentVaultMetadata())
        assertEquals(VaultMetadataStatus.MISSING, manager.validateVaultMetadata())

        val metadata = manager.createVaultCredential(
            credential = "1234",
            question = "Favorite movie?",
            answer = "Interstellar"
        )

        assertTrue(manager.hasPersistentVaultMetadata())
        assertEquals(VaultMetadataStatus.VALID, manager.validateVaultMetadata())
        assertTrue(manager.verifyVaultCredential("1234"))

        // Assert plaintext PIN or answer is NOT stored in the configuration file
        val configFileText = manager.vaultConfigFile.readText()
        assertFalse("Config must not contain plaintext PIN", configFileText.contains("1234"))
        assertFalse("Config must not contain plaintext answer", configFileText.contains("Interstellar"))
        assertTrue("Config must contain KDF algorithm", configFileText.contains("PBKDF2WithHmacSHA256"))
    }

    @Test
    fun samePinAfterProcessRestart() {
        val vaultDir = tempFolder.newFolder("vault_restart")
        val manager1 = VaultSecurityManager(customVaultDirectory = vaultDir)
        manager1.createVaultCredential("2468")

        // Simulate process restart by creating a new manager instance pointing to the same storage
        val manager2 = VaultSecurityManager(customVaultDirectory = vaultDir)
        assertTrue(manager2.hasPersistentVaultMetadata())
        assertEquals(VaultMetadataStatus.VALID, manager2.validateVaultMetadata())
        assertTrue(manager2.verifyVaultCredential("2468"))
    }

    @Test
    fun samePinAfterMetadataReload() {
        val vaultDir = tempFolder.newFolder("vault_reload")
        val manager = VaultSecurityManager(customVaultDirectory = vaultDir)
        manager.createVaultCredential("1357")

        val loadedMetadata = manager.loadVaultMetadata()
        assertNotNull(loadedMetadata)
        assertTrue(manager.verifyVaultCredential("1357"))
    }

    @Test
    fun samePinAfterSimulatedReinstall() {
        val vaultDir = tempFolder.newFolder("vault_reinstall")
        val manager1 = VaultSecurityManager(customVaultDirectory = vaultDir)
        manager1.createVaultCredential("9876")

        val key1 = manager1.deriveEncryptionKey("9876")

        // Simulate reinstall: app private data destroyed, external directory survives
        val reinstalledManager = VaultSecurityManager(customVaultDirectory = vaultDir)
        assertTrue(reinstalledManager.hasPersistentVaultMetadata())
        assertTrue(reinstalledManager.verifyVaultCredential("9876"))

        val key2 = reinstalledManager.deriveEncryptionKey("9876")
        assertArrayEquals(
            "Reinstall with same PIN must deterministically derive identical AES key",
            key1.encoded,
            key2.encoded
        )
    }

    @Test
    fun wrongPin() {
        val vaultDir = tempFolder.newFolder("vault_wrong_pin")
        val manager = VaultSecurityManager(customVaultDirectory = vaultDir)
        manager.createVaultCredential("1111")

        val originalConfigFileText = manager.vaultConfigFile.readText()
        val originalMetadata = manager.loadVaultMetadata()
        assertNotNull(originalMetadata)

        // Verify wrong PIN fails
        assertFalse("Wrong PIN must return false", manager.verifyVaultCredential("9999"))

        // Assert wrong credential NEVER overwrote metadata, changed salt, or reset vault
        val afterAttemptConfigFileText = manager.vaultConfigFile.readText()
        assertEquals("Config file must not be modified after wrong PIN attempt", originalConfigFileText, afterAttemptConfigFileText)

        val afterAttemptMetadata = manager.loadVaultMetadata()
        assertNotNull(afterAttemptMetadata)
        assertArrayEquals("Salt must not be regenerated on wrong PIN", originalMetadata?.salt, afterAttemptMetadata?.salt)

        // Attempting to derive key with wrong PIN must throw VaultAuthenticationException
        try {
            manager.deriveEncryptionKey("9999")
            fail("Expected VaultAuthenticationException on wrong PIN")
        } catch (_: VaultAuthenticationException) {
            // Expected
        }
    }

    @Test
    fun corruptedMetadata() {
        val vaultDir = tempFolder.newFolder("vault_corrupted")
        val manager = VaultSecurityManager(customVaultDirectory = vaultDir)

        // Create a dummy video file that should not be touched
        val dummyVideo = File(vaultDir, "video1.vlt")
        dummyVideo.writeBytes(byteArrayOf(1, 2, 3, 4))

        // Write corrupt JSON to config
        manager.vaultConfigFile.writeText("{ corrupted_json: true, incomplete: ")

        assertEquals(VaultMetadataStatus.CORRUPTED, manager.validateVaultMetadata())
        assertNull(manager.loadVaultMetadata())
        assertFalse(manager.verifyVaultCredential("1234"))
        assertTrue("Corrupted metadata must flag orphaned vault files", manager.hasOrphanedVaultFiles())

        // Encrypted files must NOT be deleted
        assertTrue("Existing vault media files must not be deleted on corrupted metadata", dummyVideo.exists())
    }

    @Test
    fun missingMetadata() {
        val vaultDir = tempFolder.newFolder("vault_missing")
        val manager = VaultSecurityManager(customVaultDirectory = vaultDir)

        assertEquals(VaultMetadataStatus.MISSING, manager.validateVaultMetadata())
        assertFalse(manager.hasPersistentVaultMetadata())
        assertNull(manager.loadVaultMetadata())

        // If files exist without metadata, enter recovery state
        val dummyVideo = File(vaultDir, "orphan.vlt")
        dummyVideo.writeBytes(byteArrayOf(9, 8, 7, 6))

        assertTrue("Should detect orphaned vault files when metadata is missing", manager.hasOrphanedVaultFiles())
    }

    @Test
    fun deletedMetadata() {
        val vaultDir = tempFolder.newFolder("vault_deleted")
        val manager = VaultSecurityManager(customVaultDirectory = vaultDir)
        manager.createVaultCredential("5555")

        assertTrue(manager.hasPersistentVaultMetadata())
        assertTrue(manager.deleteVaultMetadata())

        assertFalse(manager.hasPersistentVaultMetadata())
        assertEquals(VaultMetadataStatus.MISSING, manager.validateVaultMetadata())
    }

    @Test
    fun changedKdfParameters() {
        val vaultDir = tempFolder.newFolder("vault_kdf_params")
        val manager = VaultSecurityManager(customVaultDirectory = vaultDir)

        val metadata = manager.createVaultCredential(
            credential = "6666",
            iterations = 5_000
        )

        assertEquals(5_000, metadata.kdfIterations)

        val loaded = manager.loadVaultMetadata()
        assertNotNull(loaded)
        assertEquals(5_000, loaded?.kdfIterations)

        assertTrue(manager.verifyVaultCredential("6666"))
    }

    @Test
    fun saltPreservation() {
        val vaultDir = tempFolder.newFolder("vault_salt_preservation")
        val manager = VaultSecurityManager(customVaultDirectory = vaultDir)
        val initialMetadata = manager.createVaultCredential("4321")
        val initialSalt = initialMetadata.salt

        // Run multiple verify calls with correct and incorrect credentials
        for (i in 1..5) {
            manager.verifyVaultCredential("4321")
            manager.verifyVaultCredential("0000")
        }

        val reloadedMetadata = manager.loadVaultMetadata()
        assertNotNull(reloadedMetadata)
        assertArrayEquals("Salt must be preserved across operations", initialSalt, reloadedMetadata?.salt)
    }

    @Test
    fun keyDerivationConsistency() {
        val vaultDir = tempFolder.newFolder("vault_key_consistency")
        val manager = VaultSecurityManager(customVaultDirectory = vaultDir)
        manager.createVaultCredential("7777")

        val key1 = manager.deriveEncryptionKey("7777")
        val key2 = manager.deriveEncryptionKey("7777")

        assertArrayEquals("Same credential must derive identical key", key1.encoded, key2.encoded)
        assertEquals("AES", key1.algorithm)
        assertEquals(32, key1.encoded.size) // 256 bits
    }
}
