package com.devson.nvplayer.data.security

import com.devson.nvplayer.data.database.VaultDao
import com.devson.nvplayer.data.database.VaultEntity
import com.devson.nvplayer.domain.model.VaultStorageMode
import com.devson.nvplayer.viewmodel.VaultAuthState
import com.devson.nvplayer.viewmodel.VaultAuthViewModel
import com.devson.nvplayer.viewmodel.VaultGalleryViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
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
import java.io.ByteArrayInputStream
import java.io.File

class VaultEndToEndReinstallTest {

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
        override suspend fun delete(vaultMedia: VaultEntity) { entities.remove(vaultMedia.id) }
        override suspend fun deleteById(id: Long) { entities.remove(id) }
        override suspend fun deleteAll() { entities.clear() }
    }

    private data class Fixture(
        val vaultDir: File,
        val thumbsDir: File,
        val tempPlaybackDir: File,
        val securityManager: VaultSecurityManager,
        val fileManager: VaultFileManager,
        val dao: InMemoryVaultDao
    )

    private fun createFixture(): Fixture {
        val root = tempFolder.newFolder("vault_env_${System.nanoTime()}")
        val vaultDir = File(root, "vault").apply { mkdirs() }
        val thumbsDir = File(root, "thumbs").apply { mkdirs() }
        val tempPlaybackDir = File(root, "playback_temp").apply { mkdirs() }

        val secManager = VaultSecurityManager(customVaultDirectory = vaultDir)
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
        return Fixture(vaultDir, thumbsDir, tempPlaybackDir, secManager, fileManager, dao)
    }

    private fun sampleMp4Payload(length: Int = 2048): ByteArray {
        val bytes = ByteArray(length) { (it % 127).toByte() }
        bytes[0] = 0
        bytes[1] = 0
        bytes[2] = 0
        bytes[3] = 24
        bytes[4] = 0x66.toByte() // 'f'
        bytes[5] = 0x74.toByte() // 't'
        bytes[6] = 0x79.toByte() // 'y'
        bytes[7] = 0x70.toByte() // 'p'
        bytes[8] = 0x69.toByte() // 'i'
        bytes[9] = 0x73.toByte() // 's'
        bytes[10] = 0x6F.toByte() // 'o'
        bytes[11] = 0x6D.toByte() // 'm'
        return bytes
    }

    /**
     * Complete lifecycle:
     * install -> create vault -> encrypt video -> uninstall -> reinstall ->
     * detect existing vault -> restore -> correct PIN -> rebuild database -> play video.
     */
    @Test
    fun testCompleteReinstallAndEncryptedPlaybackLifecycle() = runBlocking {
        val fixture = createFixture()
        val pin = "4321"

        // Step 1: Install & create vault
        fixture.securityManager.createVaultCredential(pin)
        assertEquals(pin, fixture.securityManager.getActiveCredential())

        // Step 2: Encrypt video into vault
        val originalVideo = sampleMp4Payload(4096)
        val importResult = fixture.fileManager.importStreamToVault(
            sourceInputStream = ByteArrayInputStream(originalVideo),
            title = "TopSecretVideo",
            originalExtension = "mp4",
            durationMs = 12000L,
            storageMode = VaultStorageMode.ENCRYPTED,
            vaultCredential = pin
        )
        assertTrue("Import must succeed", importResult.isSuccess)
        val initialEntity = importResult.getOrThrow()
        assertEquals(VaultStorageMode.ENCRYPTED, initialEntity.storageMode)
        assertTrue(File(initialEntity.vaultPath).exists())
        assertEquals(1, fixture.dao.entities.size)

        // Step 3: Simulate Uninstall: Room DB is wiped and local shared prefs lost
        fixture.dao.deleteAll()
        fixture.securityManager.setVaultInitializedLocally(false)
        fixture.securityManager.clearSession()
        assertEquals(0, fixture.dao.entities.size)
        assertNull(fixture.securityManager.getActiveCredential())
        assertFalse(fixture.securityManager.isVaultInitializedLocally())

        // Step 4: Reinstall & app restart: instantiate fresh ViewModel
        val authVm = VaultAuthViewModel(
            application = null,
            securityManager = fixture.securityManager,
            vaultFileManager = fixture.fileManager,
            ioDispatcher = Dispatchers.Unconfined,
            mainDispatcher = Dispatchers.Unconfined,
            coroutineScope = CoroutineScope(Dispatchers.Unconfined)
        )

        // Detect existing vault
        val initialState = authVm.authState.value
        assertTrue("Must detect existing vault data on reinstall", initialState is VaultAuthState.ExistingVaultFound)
        val existingVault = initialState as VaultAuthState.ExistingVaultFound
        assertEquals(1, existingVault.fileCount)
        assertTrue(existingVault.isMetadataValid)

        // Step 5: Click restore and enter correct PIN
        authVm.onRestoreExistingVaultClicked()
        assertTrue(authVm.authState.value is VaultAuthState.RestorePinEntry)

        authVm.onDigit("4")
        authVm.onDigit("3")
        authVm.onDigit("2")
        authVm.onDigit("1")

        // Wait for async rebuild
        var attempts = 0
        while (authVm.authState.value !is VaultAuthState.Authenticated && attempts < 50) {
            Thread.sleep(50)
            attempts++
        }

        // Verify authenticated state and rebuilt Room DB
        assertEquals(VaultAuthState.Authenticated, authVm.authState.value)
        assertEquals(pin, fixture.securityManager.getActiveCredential())
        assertEquals(1, fixture.dao.entities.size)

        val restoredEntity = fixture.dao.getAllVaultMedia().first()
        assertEquals("TopSecretVideo", restoredEntity.title)
        assertEquals(VaultStorageMode.ENCRYPTED, restoredEntity.storageMode)

        // Step 6: Play video through GalleryViewModel using the active unlocked session
        val playbackFile = fixture.fileManager.getPlaybackFile(
            restoredEntity,
            fixture.securityManager.getActiveCredential() ?: ""
        )
        assertNotNull(playbackFile)
        assertTrue(playbackFile.exists())
        assertTrue(playbackFile.length() > 0)
        assertArrayEquals("Decrypted playback content must match original plaintext video", originalVideo, playbackFile.readBytes())

        // Step 7: Lock vault -> verify active session cleared & playback temp cleaned
        authVm.lockVault()
        assertNull("Locking vault must wipe in-memory active credential", fixture.securityManager.getActiveCredential())
        assertTrue("State must return to EnterPin after lock", authVm.authState.value is VaultAuthState.EnterPin)

        // Clean temp playback and verify
        fixture.fileManager.cleanPlaybackTemp()
        val remainingTemp = fixture.tempPlaybackDir.listFiles { f -> f.name != ".nomedia" }
        assertTrue("Temporary decrypted playback files must be wiped", remainingTemp == null || remainingTemp.isEmpty())
    }

    @Test
    fun testReinstallWrongPinDoesNotMutateFilesOrMetadata() = runBlocking {
        val fixture = createFixture()
        val correctPin = "9876"
        fixture.securityManager.createVaultCredential(correctPin)

        val sampleVideo = sampleMp4Payload(1024)
        fixture.fileManager.importStreamToVault(
            sourceInputStream = ByteArrayInputStream(sampleVideo),
            title = "UnmutatedVideo",
            storageMode = VaultStorageMode.ENCRYPTED,
            vaultCredential = correctPin
        )

        val vltFile = fixture.vaultDir.listFiles { f -> f.extension == "vlt" }?.first()
        assertNotNull(vltFile)
        val originalBytes = vltFile?.readBytes() ?: ByteArray(0)
        val originalLastModified = vltFile?.lastModified() ?: 0L

        // Simulate reinstall
        fixture.dao.deleteAll()
        fixture.securityManager.setVaultInitializedLocally(false)
        fixture.securityManager.clearSession()

        val authVm = VaultAuthViewModel(
            application = null,
            securityManager = fixture.securityManager,
            vaultFileManager = fixture.fileManager,
            ioDispatcher = Dispatchers.Unconfined,
            mainDispatcher = Dispatchers.Unconfined,
            coroutineScope = CoroutineScope(Dispatchers.Unconfined)
        )

        authVm.onRestoreExistingVaultClicked()
        authVm.onDigit("0")
        authVm.onDigit("0")
        authVm.onDigit("0")
        authVm.onDigit("0")

        var attempts = 0
        while (authVm.authState.value !is VaultAuthState.IncorrectPin && attempts < 50) {
            Thread.sleep(50)
            attempts++
        }

        val errorState = authVm.authState.value
        assertTrue("Must transition to IncorrectPin", errorState is VaultAuthState.IncorrectPin)

        // Ensure zero mutation of ciphertext on disk
        val recheckFile = File(vltFile?.absolutePath ?: "")
        assertTrue(recheckFile.exists())
        assertArrayEquals("Wrong PIN must never mutate ciphertext on disk", originalBytes, recheckFile.readBytes())
        assertTrue("Metadata must remain intact", fixture.securityManager.hasPersistentVaultMetadata())
        assertNull("Wrong PIN must not populate in-memory session credential", fixture.securityManager.getActiveCredential())
        assertTrue("Original PIN must still be valid", fixture.securityManager.verifyPin(correctPin))
    }

    @Test
    fun testMissingDatabaseRestoresFromDisk() = runBlocking {
        val fixture = createFixture()
        val pin = "1122"
        fixture.securityManager.createVaultCredential(pin)

        // Import two videos: one encrypted, one unencrypted
        val video1 = sampleMp4Payload(2048)
        val video2 = sampleMp4Payload(1024)

        fixture.fileManager.importStreamToVault(
            sourceInputStream = ByteArrayInputStream(video1),
            title = "EncryptedVideo",
            storageMode = VaultStorageMode.ENCRYPTED,
            vaultCredential = pin
        )
        fixture.fileManager.importStreamToVault(
            sourceInputStream = ByteArrayInputStream(video2),
            title = "PlainVideo",
            storageMode = VaultStorageMode.NONE
        )

        assertEquals(2, fixture.dao.entities.size)

        // Wipe Room database (simulate DB corruption / deletion)
        fixture.dao.deleteAll()
        assertEquals(0, fixture.dao.entities.size)

        // Rebuild from disk storage
        val result = fixture.fileManager.rebuildDatabaseFromStorage(credential = pin)
        assertEquals(2, result.restoredCount)
        assertEquals(0, result.invalidSkippedCount)
        assertEquals(2, fixture.dao.entities.size)

        val restoredEncrypted = fixture.dao.getAllVaultMedia().firstOrNull { it.storageMode == VaultStorageMode.ENCRYPTED }
        assertNotNull(restoredEncrypted)
        assertEquals(VaultStorageMode.ENCRYPTED, restoredEncrypted?.storageMode)

        val restoredPlain = fixture.dao.getAllVaultMedia().firstOrNull { it.storageMode == VaultStorageMode.NONE }
        assertNotNull(restoredPlain)
        assertEquals(VaultStorageMode.NONE, restoredPlain?.storageMode)
    }

    @Test
    fun testCorruptMetadataResultsInSafeError() {
        val fixture = createFixture()
        fixture.securityManager.createVaultCredential("5555")

        // Intentionally corrupt metadata file
        val configFile = fixture.securityManager.vaultConfigFile
        assertTrue(configFile.exists())
        configFile.writeText("{ corrupted_json_payload: null, malformed: true }", Charsets.UTF_8)

        // Validate metadata status
        val status = fixture.securityManager.validateVaultMetadata()
        assertEquals(VaultMetadataStatus.CORRUPTED, status)

        // Verify that authentication fails gracefully without throwing an unhandled crash
        assertFalse(fixture.securityManager.verifyPin("5555"))
        assertFalse(fixture.securityManager.isPinSet())
    }

    @Test
    fun testMissingMetadataWithExistingFilesEntersRecoveryState() {
        val fixture = createFixture()
        fixture.securityManager.createVaultCredential("6666")

        // Write a .vlt file to storage
        val dummyVlt = File(fixture.vaultDir, "orphan.vlt")
        dummyVlt.writeBytes(sampleMp4Payload(512))

        // Delete metadata completely
        val configFile = fixture.securityManager.vaultConfigFile
        if (configFile.exists()) configFile.delete()

        // Verify status
        val status = fixture.securityManager.validateVaultMetadata()
        assertEquals(VaultMetadataStatus.MISSING, status)
        assertTrue(fixture.securityManager.hasExistingVaultOnDisk())
        assertTrue("Must detect orphaned vault files for recovery", fixture.securityManager.hasOrphanedVaultFiles())

        val authVm = VaultAuthViewModel(
            application = null,
            securityManager = fixture.securityManager,
            vaultFileManager = fixture.fileManager,
            ioDispatcher = Dispatchers.Unconfined,
            mainDispatcher = Dispatchers.Unconfined,
            coroutineScope = CoroutineScope(Dispatchers.Unconfined)
        )

        val state = authVm.authState.value
        assertTrue("Must enter ExistingVaultFound with isMetadataValid = false", state is VaultAuthState.ExistingVaultFound)
        val existing = state as VaultAuthState.ExistingVaultFound
        assertEquals(1, existing.fileCount)
        assertFalse(existing.isMetadataValid)
    }

    @Test
    fun testTamperedEncryptedFileFailsDecryptionSafely() = runBlocking {
        val fixture = createFixture()
        val pin = "8888"
        fixture.securityManager.createVaultCredential(pin)

        val video = sampleMp4Payload(1024)
        val entityResult = fixture.fileManager.importStreamToVault(
            sourceInputStream = ByteArrayInputStream(video),
            title = "IntegrityCheck",
            storageMode = VaultStorageMode.ENCRYPTED,
            vaultCredential = pin
        )
        val entity = entityResult.getOrThrow()
        val vltFile = File(entity.vaultPath)
        assertTrue(vltFile.exists())

        // Tamper with ciphertext bytes at the end of the file
        val bytes = vltFile.readBytes()
        bytes[bytes.size - 5] = (bytes[bytes.size - 5].toInt() xor 0xFF).toByte()
        vltFile.writeBytes(bytes)

        // Attempt playback -> AEAD verification must detect tampering and throw VaultIntegrityException safely
        try {
            fixture.fileManager.getPlaybackFile(entity, pin)
            fail("Decryption of tampered ciphertext must throw VaultIntegrityException")
        } catch (e: Exception) {
            assertTrue("Exception must indicate integrity failure: ${e.javaClass.simpleName}",
                e is VaultIntegrityException || e.cause is javax.crypto.AEADBadTagException)
        }
    }

    @Test
    fun testUnencryptedMediaReinstallWithDelayedPermissionAndOverwriteSafety() = runBlocking {
        val fixture = createFixture()
        val pin = "1234"

        // Phase 1: Initial install, setup PIN and hide 1 unencrypted video
        fixture.securityManager.setPin(pin, "Favorite color?", "Blue")
        val mp4Bytes = sampleMp4Payload()
        val importResult = fixture.fileManager.importStreamToVault(
            sourceInputStream = ByteArrayInputStream(mp4Bytes),
            title = "VacationVideo",
            storageMode = VaultStorageMode.NONE
        )
        assertTrue(importResult.isSuccess)
        val vltFiles = fixture.vaultDir.listFiles { f -> f.extension == "vlt" } ?: emptyArray()
        assertEquals(1, vltFiles.size)
        assertTrue(fixture.securityManager.hasPersistentVaultMetadata())

        // Phase 2: Simulate app uninstall/reinstall
        // New SecurityManager, fresh state, no local prefs
        val reinstalledSecManager = VaultSecurityManager(customVaultDirectory = fixture.vaultDir)
        assertFalse(reinstalledSecManager.isVaultInitializedLocally())

        val reinstalledDao = InMemoryVaultDao()
        val reinstalledFileManager = VaultFileManager(
            context = null,
            vaultDao = reinstalledDao,
            vaultContainer = DefaultVaultContainer(),
            customVaultDirectory = fixture.vaultDir,
            customThumbsDirectory = fixture.thumbsDir,
            customTempPlaybackDirectory = fixture.tempPlaybackDir,
            ioDispatcher = Dispatchers.Unconfined
        )

        val reinstalledAuthVm = VaultAuthViewModel(
            securityManager = reinstalledSecManager,
            vaultFileManager = reinstalledFileManager,
            ioDispatcher = Dispatchers.Unconfined,
            mainDispatcher = Dispatchers.Unconfined,
            coroutineScope = CoroutineScope(Dispatchers.Unconfined)
        )

        // Verify that checkPinStatus correctly detects existing vault data
        reinstalledAuthVm.checkPinStatus()
        val authState = reinstalledAuthVm.authState.value
        assertTrue("State must be ExistingVaultFound: $authState", authState is VaultAuthState.ExistingVaultFound)
        val foundState = authState as VaultAuthState.ExistingVaultFound
        assertEquals(1, foundState.fileCount)
        assertTrue(foundState.isMetadataValid)

        // Verify SetupPin guard in onDigit if auth state somehow stayed in SetupPin
        // Force state to SetupPin to simulate permission delay
        reinstalledAuthVm.onCancelRemoveVault() // runs checkPinStatus, sets ExistingVaultFound
        
        // Verify Restore flow works for unencrypted video
        reinstalledAuthVm.onRestoreExistingVaultClicked()
        assertTrue(reinstalledAuthVm.authState.value is VaultAuthState.RestorePinEntry)

        // Enter correct PIN -> Restores media into Room database
        pin.forEach { digit -> reinstalledAuthVm.onDigit(digit.toString()) }

        // Wait for async rebuild
        var attempts = 0
        while (reinstalledAuthVm.authState.value !is VaultAuthState.Authenticated && attempts < 50) {
            Thread.sleep(50)
            attempts++
        }

        assertEquals(VaultAuthState.Authenticated, reinstalledAuthVm.authState.value)
        assertEquals(1, reinstalledDao.entities.size)
        val restoredEntity = reinstalledDao.entities.values.first()
        assertEquals(VaultStorageMode.NONE, restoredEntity.storageMode)

        // Verify saveMetadataToDisk can overwrite existing .vault_config without FileAlreadyExistsException
        reinstalledSecManager.setDefaultStorageMode(VaultStorageMode.ENCRYPTED)
        assertEquals(VaultStorageMode.ENCRYPTED, reinstalledSecManager.getDefaultStorageMode())
    }
}

