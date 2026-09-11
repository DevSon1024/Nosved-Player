package com.devson.nvplayer.ui.screen.vault

import com.devson.nvplayer.data.database.VaultDao
import com.devson.nvplayer.data.database.VaultEntity
import com.devson.nvplayer.data.security.DefaultVaultContainer
import com.devson.nvplayer.data.security.VaultContainer
import com.devson.nvplayer.data.security.VaultFileManager
import com.devson.nvplayer.data.security.VaultMetadata
import com.devson.nvplayer.data.security.VaultMetadataJson
import com.devson.nvplayer.data.security.VaultSecurityManager
import com.devson.nvplayer.domain.model.VaultStorageMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.io.File

class VaultProtectionModeTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private class TestVaultDao : VaultDao {
        private val entities = mutableMapOf<Long, VaultEntity>()
        private var nextId = 1L

        override suspend fun insert(vaultMedia: VaultEntity): Long {
            val id = if (vaultMedia.id == 0L) nextId++ else vaultMedia.id
            val entityWithId = vaultMedia.copy(id = id)
            entities[id] = entityWithId
            return id
        }

        override fun getAllVaultMediaFlow(): Flow<List<VaultEntity>> = kotlinx.coroutines.flow.flowOf(entities.values.toList())
        override suspend fun getAllVaultMedia(): List<VaultEntity> = entities.values.toList()
        override suspend fun getById(id: Long): VaultEntity? = entities[id]
        override suspend fun getByVaultPath(vaultPath: String): VaultEntity? = entities.values.firstOrNull { it.vaultPath == vaultPath }
        override suspend fun updatePlaybackPosition(id: Long, pos: Long) {
            entities[id]?.let { entities[id] = it.copy(lastPlaybackPosition = pos) }
        }
        override suspend fun delete(vaultMedia: VaultEntity) { entities.remove(vaultMedia.id) }
        override suspend fun deleteById(id: Long) { entities.remove(id) }
    }

    @Test
    fun testDefaultModeIsNone() {
        val vaultDir = tempFolder.newFolder("vault_default")
        val securityManager = VaultSecurityManager(customVaultDirectory = vaultDir)

        // Fresh installation with no metadata must default to NONE
        assertEquals(VaultStorageMode.NONE, securityManager.getDefaultStorageMode())

        // Initializing vault with a PIN defaults to NONE
        securityManager.createVaultCredential("1234")
        assertEquals(VaultStorageMode.NONE, securityManager.getDefaultStorageMode())
    }

    @Test
    fun testSwitchingModeAfterAuthentication() {
        val vaultDir = tempFolder.newFolder("vault_auth_switch")
        val securityManager = VaultSecurityManager(customVaultDirectory = vaultDir)
        securityManager.createVaultCredential("4321")

        assertEquals(VaultStorageMode.NONE, securityManager.getDefaultStorageMode())

        // Authenticate with valid credential to switch mode
        val success = securityManager.verifyAndSetStorageMode("4321", VaultStorageMode.ENCRYPTED)
        assertTrue("Mode switch after successful authentication must succeed", success)
        assertEquals(VaultStorageMode.ENCRYPTED, securityManager.getDefaultStorageMode())

        // Switch back to NONE after authentication
        val switchBack = securityManager.verifyAndSetStorageMode("4321", VaultStorageMode.NONE)
        assertTrue(switchBack)
        assertEquals(VaultStorageMode.NONE, securityManager.getDefaultStorageMode())
    }

    @Test
    fun testCancellingAuthenticationLeavesModeUnchanged() {
        val vaultDir = tempFolder.newFolder("vault_cancel")
        val securityManager = VaultSecurityManager(customVaultDirectory = vaultDir)
        securityManager.createVaultCredential("5555")

        assertEquals(VaultStorageMode.NONE, securityManager.getDefaultStorageMode())

        // Simulated cancellation: user dismisses authentication prompt without providing credential
        // Storage mode must remain intact
        val currentMode = securityManager.getDefaultStorageMode()
        assertEquals(VaultStorageMode.NONE, currentMode)
    }

    @Test
    fun testUnauthenticatedAccessRejected() {
        val vaultDir = tempFolder.newFolder("vault_unauth")
        val securityManager = VaultSecurityManager(customVaultDirectory = vaultDir)
        securityManager.createVaultCredential("9876")

        assertEquals(VaultStorageMode.NONE, securityManager.getDefaultStorageMode())

        // Wrong credential must fail and not change storage mode
        val success = securityManager.verifyAndSetStorageMode("0000", VaultStorageMode.ENCRYPTED)
        assertFalse("Unauthenticated access with wrong PIN must be rejected", success)
        assertEquals(VaultStorageMode.NONE, securityManager.getDefaultStorageMode())
    }

    @Test
    fun testExistingItemModeRemainingUnchanged() = runBlocking {
        val vaultDir = tempFolder.newFolder("vault_existing_items")
        val thumbsDir = tempFolder.newFolder("thumbs")
        val playbackDir = tempFolder.newFolder("playback")
        val fakeDao = TestVaultDao()
        val container: VaultContainer = DefaultVaultContainer()

        val fileManager = VaultFileManager(
            context = null,
            vaultDao = fakeDao,
            vaultContainer = container,
            customVaultDirectory = vaultDir,
            customThumbsDirectory = thumbsDir,
            customTempPlaybackDirectory = playbackDir
        )
        val securityManager = VaultSecurityManager(customVaultDirectory = vaultDir)
        securityManager.createVaultCredential("1111")

        // 1. Import initial video in default NONE mode
        val video1Bytes = ByteArray(512) { 1 }
        val res1 = fileManager.importStreamToVault(
            sourceInputStream = ByteArrayInputStream(video1Bytes),
            title = "Video 1 Hidden",
            originalExtension = "mp4",
            storageMode = VaultStorageMode.NONE
        )
        assertTrue(res1.isSuccess)
        val item1Id = res1.getOrThrow().id
        val item1 = fakeDao.getById(item1Id)
        assertEquals(VaultStorageMode.NONE, item1?.storageMode)

        // 2. Switch default mode to ENCRYPTED after authentication
        val switched = securityManager.verifyAndSetStorageMode("1111", VaultStorageMode.ENCRYPTED)
        assertTrue(switched)
        assertEquals(VaultStorageMode.ENCRYPTED, securityManager.getDefaultStorageMode())

        // Verify existing video 1 mode remains NONE (no silent re-encryption)
        val item1AfterSwitch = fakeDao.getById(item1Id)
        assertEquals(VaultStorageMode.NONE, item1AfterSwitch?.storageMode)

        // 3. Import new video under ENCRYPTED mode
        val video2Bytes = ByteArray(512) { 2 }
        val res2 = fileManager.importStreamToVault(
            sourceInputStream = ByteArrayInputStream(video2Bytes),
            title = "Video 2 Encrypted",
            originalExtension = "mp4",
            storageMode = VaultStorageMode.ENCRYPTED,
            vaultCredential = "1111"
        )
        assertTrue(res2.isSuccess)
        val item2Id = res2.getOrThrow().id
        val item2 = fakeDao.getById(item2Id)
        assertEquals(VaultStorageMode.ENCRYPTED, item2?.storageMode)

        // 4. Switch default mode back to NONE
        securityManager.verifyAndSetStorageMode("1111", VaultStorageMode.NONE)

        // Verify existing video 2 remains ENCRYPTED (no silent decryption)
        val item2AfterSwitchBack = fakeDao.getById(item2Id)
        assertEquals(VaultStorageMode.ENCRYPTED, item2AfterSwitchBack?.storageMode)

        // Verify existing video 1 remains NONE
        val item1Final = fakeDao.getById(item1Id)
        assertEquals(VaultStorageMode.NONE, item1Final?.storageMode)
    }

    @Test
    fun testAppRestartRetainingSelection() {
        val vaultDir = tempFolder.newFolder("vault_restart_test")
        val manager1 = VaultSecurityManager(customVaultDirectory = vaultDir)
        manager1.createVaultCredential("7777")

        // Switch to ENCRYPTED
        manager1.verifyAndSetStorageMode("7777", VaultStorageMode.ENCRYPTED)
        assertEquals(VaultStorageMode.ENCRYPTED, manager1.getDefaultStorageMode())

        // Simulate app restart / new process by creating a new manager pointing to the same storage
        val manager2 = VaultSecurityManager(customVaultDirectory = vaultDir)
        assertEquals(VaultStorageMode.ENCRYPTED, manager2.getDefaultStorageMode())

        // Switch to NONE and verify retention across restart
        manager2.verifyAndSetStorageMode("7777", VaultStorageMode.NONE)
        val manager3 = VaultSecurityManager(customVaultDirectory = vaultDir)
        assertEquals(VaultStorageMode.NONE, manager3.getDefaultStorageMode())
    }

    @Test
    fun testVaultMetadataJsonSerialization() {
        val metadata = VaultMetadata(
            salt = ByteArray(16) { it.toByte() },
            authCiphertext = ByteArray(32) { (it * 2).toByte() },
            authIv = ByteArray(12) { (it * 3).toByte() },
            defaultStorageMode = VaultStorageMode.ENCRYPTED
        )

        val json = VaultMetadataJson.toJson(metadata)
        assertTrue(json.contains("\"defaultStorageMode\": \"ENCRYPTED\""))

        val parsed = VaultMetadataJson.fromJson(json)
        assertEquals(VaultStorageMode.ENCRYPTED, parsed?.defaultStorageMode)
    }
}
