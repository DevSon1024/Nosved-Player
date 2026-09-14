package com.devson.nvplayer.data.security

import com.devson.nvplayer.data.database.VaultDao
import com.devson.nvplayer.data.database.VaultEntity
import com.devson.nvplayer.domain.model.VaultStorageMode
import com.devson.nvplayer.viewmodel.VaultAuthState
import com.devson.nvplayer.viewmodel.VaultAuthViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.io.File
import java.io.RandomAccessFile

class VaultResetHardeningTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private class ResetTestVaultDao : VaultDao {
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

    private data class TestFixture(
        val fileManager: VaultFileManager,
        val securityManager: VaultSecurityManager,
        val dao: ResetTestVaultDao,
        val vaultDir: File,
        val thumbsDir: File,
        val tempPlaybackDir: File
    )

    private fun createFixture(): TestFixture {
        val vaultDir = tempFolder.newFolder("vault_${System.nanoTime()}")
        val thumbsDir = tempFolder.newFolder("thumbs_${System.nanoTime()}")
        val tempPlaybackDir = tempFolder.newFolder("playback_${System.nanoTime()}")

        val dao = ResetTestVaultDao()
        val secManager = VaultSecurityManager(customVaultDirectory = vaultDir)
        val fileManager = VaultFileManager(
            context = null,
            vaultDao = dao,
            vaultContainer = DefaultVaultContainer(),
            customVaultDirectory = vaultDir,
            customThumbsDirectory = thumbsDir,
            customTempPlaybackDirectory = tempPlaybackDir,
            ioDispatcher = Dispatchers.Unconfined
        )

        return TestFixture(fileManager, secManager, dao, vaultDir, thumbsDir, tempPlaybackDir)
    }

    private fun createViewModel(fixture: TestFixture): VaultAuthViewModel {
        return VaultAuthViewModel(
            application = null,
            securityManager = fixture.securityManager,
            vaultFileManager = fixture.fileManager,
            ioDispatcher = Dispatchers.Unconfined,
            mainDispatcher = Dispatchers.Unconfined,
            coroutineScope = CoroutineScope(Dispatchers.Unconfined)
        )
    }

    private fun sampleMp4Bytes(size: Int = 1024): ByteArray {
        val bytes = ByteArray(size) { (it % 250).toByte() }
        bytes[0] = 0
        bytes[1] = 0
        bytes[2] = 0
        bytes[3] = 24
        bytes[4] = 0x66.toByte()
        bytes[5] = 0x74.toByte()
        bytes[6] = 0x79.toByte()
        bytes[7] = 0x70.toByte()
        return bytes
    }

    private fun waitForState(maxMs: Long = 3000L, predicate: () -> Boolean) {
        val start = System.currentTimeMillis()
        while (!predicate() && System.currentTimeMillis() - start < maxMs) {
            Thread.sleep(20)
        }
        assertTrue("Timed out waiting for expected state", predicate())
    }

    @Test
    fun test1_unauthenticatedResetRejection() = runBlocking {
        val fixture = createFixture()
        fixture.securityManager.createVaultCredential("1234")

        fixture.fileManager.importStreamToVault(
            sourceInputStream = ByteArrayInputStream(sampleMp4Bytes()),
            title = "Test Video",
            originalExtension = "mp4",
            durationMs = 2000L,
            storageMode = VaultStorageMode.ENCRYPTED,
            vaultCredential = "1234"
        ).getOrThrow()

        val initialVltFiles = fixture.vaultDir.listFiles { f -> f.extension == "vlt" } ?: emptyArray()
        assertTrue(initialVltFiles.isNotEmpty())
        assertEquals(1, fixture.dao.getAllVaultMedia().size)
        assertTrue(fixture.securityManager.hasPersistentVaultMetadata())

        // 1. null credential must throw SecurityException
        try {
            fixture.fileManager.removeAllVaultData(fixture.securityManager, credential = null)
            fail("Expected SecurityException for unauthenticated reset")
        } catch (_: SecurityException) {
            // Expected
        }

        // 2. empty credential must throw SecurityException
        try {
            fixture.fileManager.removeAllVaultData(fixture.securityManager, credential = "")
            fail("Expected SecurityException for unauthenticated reset with empty credential")
        } catch (_: SecurityException) {
            // Expected
        }

        // Assert all files and data remain completely untouched
        val afterFiles = fixture.vaultDir.listFiles { f -> f.extension == "vlt" } ?: emptyArray()
        assertEquals(initialVltFiles.size, afterFiles.size)
        assertEquals(1, fixture.dao.getAllVaultMedia().size)
        assertTrue(fixture.securityManager.hasPersistentVaultMetadata())
    }

    @Test
    fun test2_incorrectPinRejection() = runBlocking {
        val fixture = createFixture()
        fixture.securityManager.createVaultCredential("1234")

        fixture.fileManager.importStreamToVault(
            sourceInputStream = ByteArrayInputStream(sampleMp4Bytes()),
            title = "Test Video",
            originalExtension = "mp4",
            durationMs = 2000L,
            storageMode = VaultStorageMode.ENCRYPTED,
            vaultCredential = "1234"
        ).getOrThrow()

        try {
            fixture.fileManager.removeAllVaultData(fixture.securityManager, credential = "9999")
            fail("Expected SecurityException for incorrect PIN")
        } catch (_: SecurityException) {
            // Expected
        }

        assertEquals(1, fixture.dao.getAllVaultMedia().size)
        assertTrue(fixture.securityManager.hasPersistentVaultMetadata())
        val vltFiles = fixture.vaultDir.listFiles { f -> f.extension == "vlt" } ?: emptyArray()
        assertEquals(1, vltFiles.size)
    }

    @Test
    fun test3_cancellationBeforeAuthenticationLeavesDataUntouched() = runBlocking {
        val fixture = createFixture()
        fixture.securityManager.createVaultCredential("1234")
        fixture.securityManager.setVaultInitializedLocally(true)

        fixture.fileManager.importStreamToVault(
            sourceInputStream = ByteArrayInputStream(sampleMp4Bytes()),
            title = "Video 1",
            originalExtension = "mp4",
            durationMs = 1000L,
            storageMode = VaultStorageMode.ENCRYPTED,
            vaultCredential = "1234"
        ).getOrThrow()

        val viewModel = createViewModel(fixture)
        viewModel.requestVaultReset()
        waitForState { viewModel.authState.value is VaultAuthState.EnterPinForReset }

        val state = viewModel.authState.value
        assertTrue("State must be EnterPinForReset", state is VaultAuthState.EnterPinForReset)
        assertEquals(1, (state as VaultAuthState.EnterPinForReset).fileCount)

        // Cancel before authentication
        viewModel.onCancelReset()

        // State must revert to normal auth check (EnterPin)
        assertEquals(VaultAuthState.EnterPin, viewModel.authState.value)
        assertEquals(1, fixture.dao.getAllVaultMedia().size)
        assertTrue(fixture.securityManager.hasPersistentVaultMetadata())
    }

    @Test
    fun test4_cancellationAtStep1ConfirmationLeavesDataUntouched() = runBlocking {
        val fixture = createFixture()
        fixture.securityManager.createVaultCredential("1234")
        fixture.securityManager.setVaultInitializedLocally(true)

        fixture.fileManager.importStreamToVault(
            sourceInputStream = ByteArrayInputStream(sampleMp4Bytes()),
            title = "Video 1",
            originalExtension = "mp4",
            durationMs = 1000L,
            storageMode = VaultStorageMode.ENCRYPTED,
            vaultCredential = "1234"
        ).getOrThrow()

        val viewModel = createViewModel(fixture)
        viewModel.requestVaultReset()
        waitForState { viewModel.authState.value is VaultAuthState.EnterPinForReset }

        // Authenticate with valid PIN digits
        "1234".forEach { viewModel.onDigit(it.toString()) }
        waitForState { viewModel.authState.value is VaultAuthState.ConfirmDeleteVault }

        val confirmState = viewModel.authState.value
        assertTrue("State must be ConfirmDeleteVault", confirmState is VaultAuthState.ConfirmDeleteVault)
        assertEquals(1, (confirmState as VaultAuthState.ConfirmDeleteVault).step)
        assertEquals(1, confirmState.fileCount)

        // User cancels at Step 1
        viewModel.onCancelReset()

        assertEquals(VaultAuthState.EnterPin, viewModel.authState.value)
        assertEquals(1, fixture.dao.getAllVaultMedia().size)
        assertTrue(fixture.securityManager.hasPersistentVaultMetadata())
    }

    @Test
    fun test5_cancellationAtStep2ConfirmationLeavesDataUntouched() = runBlocking {
        val fixture = createFixture()
        fixture.securityManager.createVaultCredential("1234")
        fixture.securityManager.setVaultInitializedLocally(true)

        fixture.fileManager.importStreamToVault(
            sourceInputStream = ByteArrayInputStream(sampleMp4Bytes()),
            title = "Video 1",
            originalExtension = "mp4",
            durationMs = 1000L,
            storageMode = VaultStorageMode.ENCRYPTED,
            vaultCredential = "1234"
        ).getOrThrow()

        val viewModel = createViewModel(fixture)
        viewModel.requestVaultReset()
        waitForState { viewModel.authState.value is VaultAuthState.EnterPinForReset }
        "1234".forEach { viewModel.onDigit(it.toString()) }
        waitForState { viewModel.authState.value is VaultAuthState.ConfirmDeleteVault }

        // Advance to Step 2
        viewModel.onConfirmResetStep1()
        val confirmState = viewModel.authState.value
        assertTrue("State must be ConfirmDeleteVault at step 2", confirmState is VaultAuthState.ConfirmDeleteVault)
        assertEquals(2, (confirmState as VaultAuthState.ConfirmDeleteVault).step)

        // User cancels at Step 2
        viewModel.onCancelReset()

        assertEquals(VaultAuthState.EnterPin, viewModel.authState.value)
        assertEquals(1, fixture.dao.getAllVaultMedia().size)
        assertTrue(fixture.securityManager.hasPersistentVaultMetadata())
    }

    @Test
    fun test6_successfulDeletionWithValidAuthenticationStrictOrder() = runBlocking {
        val fixture = createFixture()
        fixture.securityManager.createVaultCredential("1234")
        fixture.securityManager.setVaultInitializedLocally(true)

        // 1. Add encrypted video
        val entityEnc = fixture.fileManager.importStreamToVault(
            sourceInputStream = ByteArrayInputStream(sampleMp4Bytes()),
            title = "Encrypted Video",
            originalExtension = "mp4",
            durationMs = 1000L,
            storageMode = VaultStorageMode.ENCRYPTED,
            vaultCredential = "1234"
        ).getOrThrow()

        // 2. Add unencrypted video
        val entityNoEnc = fixture.fileManager.importStreamToVault(
            sourceInputStream = ByteArrayInputStream(sampleMp4Bytes()),
            title = "Plain Video",
            originalExtension = "mp4",
            durationMs = 2000L,
            storageMode = VaultStorageMode.NONE,
            vaultCredential = "1234"
        ).getOrThrow()

        // 3. Create thumbnail in thumbsDir
        val thumbFile = File(fixture.thumbsDir, "thumb_${entityEnc.id}.jpg")
        thumbFile.writeBytes(byteArrayOf(1, 2, 3, 4))
        assertTrue(thumbFile.exists())

        // 4. Create playback temp file in tempPlaybackDir
        val tempPlaybackFile = File(fixture.tempPlaybackDir, "temp_play.mp4")
        tempPlaybackFile.writeBytes(byteArrayOf(5, 6, 7, 8))
        assertTrue(tempPlaybackFile.exists())

        // 5. Create conversion temp file in vaultDir
        val convDir = File(fixture.vaultDir, "vault_conversion_temp")
        convDir.mkdirs()
        val convFile = File(convDir, "conv.tmp")
        convFile.writeBytes(byteArrayOf(9, 10))
        assertTrue(convFile.exists())

        val viewModel = createViewModel(fixture)
        viewModel.requestVaultReset()
        waitForState { viewModel.authState.value is VaultAuthState.EnterPinForReset }
        "1234".forEach { viewModel.onDigit(it.toString()) }
        waitForState { viewModel.authState.value is VaultAuthState.ConfirmDeleteVault }
        viewModel.onConfirmResetStep1()

        val stateStep2 = viewModel.authState.value as VaultAuthState.ConfirmDeleteVault
        assertEquals(2, stateStep2.step)
        assertEquals(2, stateStep2.fileCount)

        // Execute deletion
        viewModel.executeVaultReset(stateStep2.authenticatedPin)
        waitForState { viewModel.authState.value is VaultAuthState.SetupPin }

        // Verify post-deletion state is SetupPin
        assertEquals(VaultAuthState.SetupPin, viewModel.authState.value)

        // Step 1 verification: playback temp cleaned
        val playbackFiles = fixture.tempPlaybackDir.listFiles { f -> f.name != ".nomedia" } ?: emptyArray()
        assertEquals(0, playbackFiles.size)
        assertFalse(tempPlaybackFile.exists())

        // Step 2 verification: conversion temp cleaned
        assertFalse(convFile.exists())

        // Step 3 verification: vault media files deleted
        val vltFiles = fixture.vaultDir.listFiles { f -> f.extension == "vlt" } ?: emptyArray()
        assertEquals(0, vltFiles.size)

        // Step 4 verification: thumbnails cleaned
        val thumbFiles = fixture.thumbsDir.listFiles { f -> f.name != ".nomedia" } ?: emptyArray()
        assertEquals(0, thumbFiles.size)
        assertFalse(thumbFile.exists())

        // Step 5 verification: metadata deleted
        assertFalse(fixture.securityManager.hasPersistentVaultMetadata())

        // Step 6 verification: database cleared
        assertEquals(0, fixture.dao.getAllVaultMedia().size)

        // Step 7 verification: local vault initialized is false
        assertFalse(fixture.securityManager.isVaultInitializedLocally())
    }

    @Test
    fun test7_partialDeletionFailureAbortsAndPreservesMetadataAndDatabase() = runBlocking {
        val fixture = createFixture()
        fixture.securityManager.createVaultCredential("1234")

        // Import two videos
        val entity1 = fixture.fileManager.importStreamToVault(
            sourceInputStream = ByteArrayInputStream(sampleMp4Bytes()),
            title = "Video 1",
            originalExtension = "mp4",
            durationMs = 1000L,
            storageMode = VaultStorageMode.ENCRYPTED,
            vaultCredential = "1234"
        ).getOrThrow()

        val entity2 = fixture.fileManager.importStreamToVault(
            sourceInputStream = ByteArrayInputStream(sampleMp4Bytes()),
            title = "Video 2",
            originalExtension = "mp4",
            durationMs = 1000L,
            storageMode = VaultStorageMode.ENCRYPTED,
            vaultCredential = "1234"
        ).getOrThrow()

        val file2 = File(entity2.vaultPath)
        assertTrue(file2.exists())

        // Lock file2 so file.delete() fails on Windows
        val raf = RandomAccessFile(file2, "rw")
        try {
            val result = fixture.fileManager.removeAllVaultData(fixture.securityManager, credential = "1234")

            // Deletion must report failure
            assertFalse("Result must report failure when a file cannot be deleted", result.success)
            assertTrue("Failed media count must be at least 1", result.failedMediaCount >= 1)
            assertTrue("Remaining files must contain the undeleted file", result.remainingFiles.contains(file2.name))

            // ABORT must preserve metadata and database records!
            assertTrue("Metadata must be preserved when media deletion fails", fixture.securityManager.hasPersistentVaultMetadata())
            assertTrue("Database records must NOT be deleted when media deletion fails", fixture.dao.getAllVaultMedia().isNotEmpty())
            assertTrue("Undeleted media file must remain on disk", file2.exists())
        } finally {
            raf.close()
        }
    }

    @Test
    fun test8_encryptedFilesDeletedCleanly() = runBlocking {
        val fixture = createFixture()
        fixture.securityManager.createVaultCredential("1234")

        val entity = fixture.fileManager.importStreamToVault(
            sourceInputStream = ByteArrayInputStream(sampleMp4Bytes()),
            title = "Encrypted File",
            originalExtension = "mp4",
            durationMs = 3000L,
            storageMode = VaultStorageMode.ENCRYPTED,
            vaultCredential = "1234"
        ).getOrThrow()

        val vltFile = File(entity.vaultPath)
        assertTrue(vltFile.exists())

        val result = fixture.fileManager.removeAllVaultData(fixture.securityManager, credential = "1234")
        assertTrue(result.success)
        assertEquals(1, result.deletedMediaCount)
        assertFalse("Encrypted vlt file must be deleted", vltFile.exists())
    }

    @Test
    fun test9_noEncryptionFilesDeletedCleanly() = runBlocking {
        val fixture = createFixture()
        fixture.securityManager.createVaultCredential("1234")

        val entity = fixture.fileManager.importStreamToVault(
            sourceInputStream = ByteArrayInputStream(sampleMp4Bytes()),
            title = "No Encryption File",
            originalExtension = "mp4",
            durationMs = 3000L,
            storageMode = VaultStorageMode.NONE,
            vaultCredential = "1234"
        ).getOrThrow()

        val vltFile = File(entity.vaultPath)
        assertTrue(vltFile.exists())

        val result = fixture.fileManager.removeAllVaultData(fixture.securityManager, credential = "1234")
        assertTrue(result.success)
        assertEquals(1, result.deletedMediaCount)
        assertFalse("No encryption vlt file must be deleted", vltFile.exists())
    }

    @Test
    fun test10_thumbnailsDirectoryCleaned() = runBlocking {
        val fixture = createFixture()
        fixture.securityManager.createVaultCredential("1234")

        val thumb1 = File(fixture.thumbsDir, "t1.jpg")
        val thumb2 = File(fixture.thumbsDir, "t2.png")
        thumb1.writeBytes(byteArrayOf(1, 2))
        thumb2.writeBytes(byteArrayOf(3, 4))
        assertTrue(thumb1.exists())
        assertTrue(thumb2.exists())

        val result = fixture.fileManager.removeAllVaultData(fixture.securityManager, credential = "1234")
        assertTrue(result.success)
        assertFalse(thumb1.exists())
        assertFalse(thumb2.exists())
    }

    @Test
    fun test11_tempPlaybackDirectoryCleaned() = runBlocking {
        val fixture = createFixture()
        fixture.securityManager.createVaultCredential("1234")

        val playTmp = File(fixture.tempPlaybackDir, "stream.mp4")
        playTmp.writeBytes(byteArrayOf(5, 6, 7))
        assertTrue(playTmp.exists())

        val result = fixture.fileManager.removeAllVaultData(fixture.securityManager, credential = "1234")
        assertTrue(result.success)
        assertFalse(playTmp.exists())
    }
}
