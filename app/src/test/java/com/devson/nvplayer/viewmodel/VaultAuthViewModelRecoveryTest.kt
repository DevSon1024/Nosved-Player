package com.devson.nvplayer.viewmodel

import com.devson.nvplayer.data.database.VaultDao
import com.devson.nvplayer.data.database.VaultEntity
import com.devson.nvplayer.data.security.DefaultVaultContainer
import com.devson.nvplayer.data.security.VaultContainer
import com.devson.nvplayer.data.security.VaultFileManager
import com.devson.nvplayer.data.security.VaultSecurityManager
import com.devson.nvplayer.domain.model.VaultStorageMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.io.File

class VaultAuthViewModelRecoveryTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private class InMemoryVaultDao : VaultDao {
        val entities = mutableMapOf<Long, VaultEntity>()
        private var nextId = 1L

        override suspend fun insert(vaultMedia: VaultEntity): Long {
            val id = if (vaultMedia.id == 0L) nextId++ else vaultMedia.id
            val entityWithId = vaultMedia.copy(id = id)
            entities[id] = entityWithId
            return id
        }

        override fun getAllVaultMediaFlow(): Flow<List<VaultEntity>> = flowOf(entities.values.toList())
        override suspend fun getAllVaultMedia(): List<VaultEntity> = entities.values.toList()
        override suspend fun getById(id: Long): VaultEntity? = entities[id]
        override suspend fun getByVaultPath(vaultPath: String): VaultEntity? = entities.values.firstOrNull { it.vaultPath == vaultPath }
        override suspend fun updatePlaybackPosition(id: Long, pos: Long) {
            entities[id]?.let { entities[id] = it.copy(lastPlaybackPosition = pos) }
        }
        override suspend fun update(vaultMedia: VaultEntity) {
            entities[vaultMedia.id] = vaultMedia
        }
        override suspend fun delete(vaultMedia: VaultEntity) { entities.remove(vaultMedia.id) }
        override suspend fun deleteById(id: Long) { entities.remove(id) }
        override suspend fun deleteAll() { entities.clear() }
    }

    private fun createTestFixture(): Triple<VaultSecurityManager, VaultFileManager, InMemoryVaultDao> {
        val vaultDir = tempFolder.newFolder("vault_data_${System.nanoTime()}")
        val thumbsDir = tempFolder.newFolder("thumbs_${System.nanoTime()}")
        val tempPlaybackDir = tempFolder.newFolder("playback_${System.nanoTime()}")

        val securityManager = VaultSecurityManager(customVaultDirectory = vaultDir)
        val dao = InMemoryVaultDao()
        val fileManager = VaultFileManager(
            context = null,
            vaultDao = dao,
            vaultContainer = DefaultVaultContainer(),
            customVaultDirectory = vaultDir,
            customThumbsDirectory = thumbsDir,
            customTempPlaybackDirectory = tempPlaybackDir,
            ioDispatcher = Dispatchers.Unconfined
        )
        return Triple(securityManager, fileManager, dao)
    }

    private fun createViewModel(
        securityManager: VaultSecurityManager,
        fileManager: VaultFileManager
    ): VaultAuthViewModel {
        return VaultAuthViewModel(
            application = null,
            securityManager = securityManager,
            vaultFileManager = fileManager,
            ioDispatcher = Dispatchers.Unconfined,
            mainDispatcher = Dispatchers.Unconfined,
            coroutineScope = CoroutineScope(Dispatchers.Unconfined)
        )
    }

    @Test
    fun testFreshInstallWithoutVaultFilesShowsSetupPin() {
        val (securityManager, fileManager, _) = createTestFixture()
        val viewModel = createViewModel(securityManager, fileManager)

        assertTrue(viewModel.authState.value is VaultAuthState.SetupPin)
    }

    @Test
    fun testReinstallDetectionWithExistingVaultFilesShowsExistingVaultFound() {
        val (securityManager, fileManager, _) = createTestFixture()

        // Create vault credential and import video
        securityManager.createVaultCredential("1234")
        val sampleVideoBytes = ByteArray(1024) { (it % 128).toByte() }
        val dummyFile = File(securityManager.persistentVaultDirectory, "video_1.vlt")
        dummyFile.writeBytes(sampleVideoBytes)

        val dummyFile2 = File(securityManager.persistentVaultDirectory, "video_2.vlt")
        dummyFile2.writeBytes(sampleVideoBytes)

        // Simulate reinstall: Room DB and app-local prefs are cleared
        securityManager.setVaultInitializedLocally(false)

        val viewModel = createViewModel(securityManager, fileManager)

        val state = viewModel.authState.value
        assertTrue("State should be ExistingVaultFound", state is VaultAuthState.ExistingVaultFound)
        val recoveryState = state as VaultAuthState.ExistingVaultFound
        assertEquals(2, recoveryState.fileCount)
        assertTrue(recoveryState.isMetadataValid)
    }

    private fun sampleMp4Bytes(size: Int = 1024): ByteArray {
        val bytes = ByteArray(size) { (it % 250).toByte() }
        bytes[0] = 0
        bytes[1] = 0
        bytes[2] = 0
        bytes[3] = 24
        bytes[4] = 0x66.toByte() // 'f'
        bytes[5] = 0x74.toByte() // 't'
        bytes[6] = 0x79.toByte() // 'y'
        bytes[7] = 0x70.toByte() // 'p'
        return bytes
    }

    @Test
    fun testRestoreFlowWithCorrectPinRebuildsDatabaseAndAuthenticates() {
        val (securityManager, fileManager, dao) = createTestFixture()

        // Set up PIN and create unencrypted and encrypted vault files
        securityManager.createVaultCredential("2468")

        // Import a media file via file manager
        val sampleVideoBytes = sampleMp4Bytes()
        val srcFile = File(tempFolder.root, "sample.mp4")
        srcFile.writeBytes(sampleVideoBytes)

        runBlocking {
            fileManager.importStreamToVault(
                sourceInputStream = ByteArrayInputStream(sampleVideoBytes),
                title = "sample",
                originalExtension = "mp4",
                durationMs = 1000L,
                storageMode = VaultStorageMode.NONE
            )
        }

        assertEquals(1, dao.entities.size)

        // Simulate reinstall: clear database and local initialization flag
        runBlocking { dao.deleteAll() }
        securityManager.setVaultInitializedLocally(false)
        assertEquals(0, dao.entities.size)
        assertTrue(securityManager.hasExistingVaultOnDisk())

        val viewModel = createViewModel(securityManager, fileManager)

        assertTrue(viewModel.authState.value is VaultAuthState.ExistingVaultFound)

        // User clicks Restore
        viewModel.onRestoreExistingVaultClicked()
        assertEquals(VaultAuthState.RestorePinEntry(1), viewModel.authState.value)

        // User enters correct PIN
        viewModel.onDigit("2")
        viewModel.onDigit("4")
        viewModel.onDigit("6")
        viewModel.onDigit("8")

        // Wait for asynchronous database rebuild to finish
        var attempts = 0
        while (viewModel.authState.value !is VaultAuthState.Authenticated && attempts < 50) {
            Thread.sleep(50)
            attempts++
        }

        // Authenticated and DB rebuilt!
        assertEquals(VaultAuthState.Authenticated, viewModel.authState.value)
        assertEquals(1, dao.entities.size)
        assertTrue(securityManager.isVaultInitializedLocally())
    }

    @Test
    fun testRestoreFlowWithWrongPinPreservesDataAndShowsIncorrectPin() {
        val (securityManager, fileManager, _) = createTestFixture()

        securityManager.createVaultCredential("1111")
        securityManager.setVaultInitializedLocally(false)

        val vltFile = File(securityManager.persistentVaultDirectory, "secret.vlt")
        vltFile.writeBytes("TEST_SECRET_CONTENT".toByteArray())
        val originalLastModified = vltFile.lastModified()
        val originalLength = vltFile.length()

        val viewModel = createViewModel(securityManager, fileManager)

        assertTrue(viewModel.authState.value is VaultAuthState.ExistingVaultFound)

        viewModel.onRestoreExistingVaultClicked()
        assertTrue(viewModel.authState.value is VaultAuthState.RestorePinEntry)

        // User enters WRONG PIN
        viewModel.onDigit("9")
        viewModel.onDigit("9")
        viewModel.onDigit("9")
        viewModel.onDigit("9")

        val errorState = viewModel.authState.value
        assertTrue("Must transition to IncorrectPin", errorState is VaultAuthState.IncorrectPin)
        val incorrect = errorState as VaultAuthState.IncorrectPin
        assertTrue(incorrect.isFromRestore)
        assertTrue(incorrect.message.contains("Incorrect Vault PIN"))
        assertTrue(incorrect.message.contains("Your existing vault data has not been changed"))

        // CRITICAL: Verify files on disk and metadata remain 100% UNTOUCHED
        assertTrue("Vault file must still exist", vltFile.exists())
        assertEquals("Vault file size must be unchanged", originalLength, vltFile.length())
        assertTrue("Vault metadata must remain intact", securityManager.hasPersistentVaultMetadata())
        assertTrue("Original PIN must still be valid", securityManager.verifyPin("1111"))

        // Retry with correct PIN
        viewModel.onRetryPin()
        assertTrue(viewModel.authState.value is VaultAuthState.RestorePinEntry)
        viewModel.onDigit("1")
        viewModel.onDigit("1")
        viewModel.onDigit("1")
        viewModel.onDigit("1")

        assertEquals(VaultAuthState.Authenticated, viewModel.authState.value)
        assertTrue(securityManager.isVaultInitializedLocally())
    }

    @Test
    fun testRemoveOldVaultDataTwoStepConfirmationDeletesAllDataAndTransitionsToSetupPin() {
        val (securityManager, fileManager, dao) = createTestFixture()

        securityManager.createVaultCredential("5555")
        securityManager.setVaultInitializedLocally(false)

        val vltFile1 = File(securityManager.persistentVaultDirectory, "file1.vlt")
        vltFile1.writeBytes("DATA1".toByteArray())
        val vltFile2 = File(securityManager.persistentVaultDirectory, "file2.vlt")
        vltFile2.writeBytes("DATA2".toByteArray())

        val thumbFile = File(fileManager.thumbsDirectory, "thumb1.jpg")
        thumbFile.writeBytes("THUMB".toByteArray())

        val tempPlay = File(fileManager.tempPlaybackDirectory, "playback_temp.mp4")
        tempPlay.writeBytes("TEMP".toByteArray())

        val viewModel = createViewModel(securityManager, fileManager)

        assertTrue(viewModel.authState.value is VaultAuthState.ExistingVaultFound)

        // Step 1: Click Remove
        viewModel.onRemoveOldVaultClicked()
        val step1 = viewModel.authState.value
        assertTrue(step1 is VaultAuthState.ConfirmRemoveVault && step1.step == 1 && step1.fileCount == 2)

        // Step 2: Confirm Continue
        viewModel.onConfirmRemoveStep1()
        val step2 = viewModel.authState.value
        assertTrue(step2 is VaultAuthState.ConfirmRemoveVault && step2.step == 2 && step2.fileCount == 2)

        // Final Confirm: Delete Permanently
        viewModel.onConfirmRemoveFinal()

        assertTrue(viewModel.authState.value is VaultAuthState.SetupPin)

        // Verify all media files, thumbs, temp playback, metadata, and DB are deleted
        assertFalse(vltFile1.exists())
        assertFalse(vltFile2.exists())
        assertFalse(thumbFile.exists())
        assertFalse(tempPlay.exists())
        assertFalse(securityManager.hasPersistentVaultMetadata())
        assertFalse(securityManager.hasExistingVaultOnDisk())
        assertFalse(securityManager.isVaultInitializedLocally())
        assertEquals(0, dao.entities.size)
    }

    @Test
    fun testCancellingRemoveOldVaultDataLeavesAllFilesIntact() {
        val (securityManager, fileManager, _) = createTestFixture()

        securityManager.createVaultCredential("7777")
        securityManager.setVaultInitializedLocally(false)

        val vltFile = File(securityManager.persistentVaultDirectory, "keep_me.vlt")
        vltFile.writeBytes("IMPORTANT_VIDEO".toByteArray())

        val viewModel = createViewModel(securityManager, fileManager)

        assertTrue(viewModel.authState.value is VaultAuthState.ExistingVaultFound)

        // Enter Step 1 and cancel
        viewModel.onRemoveOldVaultClicked()
        assertEquals(VaultAuthState.ConfirmRemoveVault(1, 1), viewModel.authState.value)
        viewModel.onCancelRemoveVault()
        assertTrue(viewModel.authState.value is VaultAuthState.ExistingVaultFound)
        assertTrue(vltFile.exists())

        // Enter Step 2 and cancel
        viewModel.onRemoveOldVaultClicked()
        viewModel.onConfirmRemoveStep1()
        assertEquals(VaultAuthState.ConfirmRemoveVault(2, 1), viewModel.authState.value)
        viewModel.onCancelRemoveVault()
        assertTrue(viewModel.authState.value is VaultAuthState.ExistingVaultFound)
        assertTrue(vltFile.exists())
        assertTrue(securityManager.hasPersistentVaultMetadata())
    }

    @Test
    fun testNormalSessionWithPopulatedDatabaseGoesDirectlyToEnterPin() {
        val (securityManager, fileManager, dao) = createTestFixture()

        securityManager.createVaultCredential("3333")
        val vltFile = File(securityManager.persistentVaultDirectory, "active.vlt")
        vltFile.writeBytes("ACTIVE".toByteArray())

        // DB is already populated (normal existing session)
        runBlocking {
            dao.insert(
                VaultEntity(
                    id = 1L,
                    title = "active.mp4",
                    originalUri = "",
                    vaultPath = vltFile.absolutePath,
                    fileSize = 6L
                )
            )
        }

        val viewModel = createViewModel(securityManager, fileManager)

        assertEquals(VaultAuthState.EnterPin, viewModel.authState.value)
    }
}

