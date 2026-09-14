package com.devson.nvplayer.data.security

import com.devson.nvplayer.data.database.VaultDao
import com.devson.nvplayer.data.database.VaultEntity
import com.devson.nvplayer.domain.model.VaultStorageMode
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
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
import java.io.FileInputStream
import java.io.FileOutputStream

class VaultVideoConversionTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val fakeDao = ConversionTestVaultDao()
    private val container: VaultContainer = DefaultVaultContainer()

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

    private fun createEnvironment(): Triple<VaultFileManager, VaultSecurityManager, Triple<File, File, File>> {
        val vaultDir = tempFolder.newFolder("vault_${System.nanoTime()}")
        val thumbsDir = tempFolder.newFolder("thumbs_${System.nanoTime()}")
        val tempPlaybackDir = tempFolder.newFolder("playback_${System.nanoTime()}")

        val fileManager = VaultFileManager(
            context = null,
            vaultDao = fakeDao,
            vaultContainer = container,
            customVaultDirectory = vaultDir,
            customThumbsDirectory = thumbsDir,
            customTempPlaybackDirectory = tempPlaybackDir
        )

        val secManager = VaultSecurityManager(customVaultDirectory = vaultDir)
        return Triple(fileManager, secManager, Triple(vaultDir, thumbsDir, tempPlaybackDir))
    }

    @Test
    fun convertVideo_noneToEncrypted_success() = runBlocking {
        val (fileManager, secManager, _) = createEnvironment()
        val pin = "1234"
        secManager.createVaultCredential(pin)

        val originalBytes = sampleMp4Bytes()
        val importResult = fileManager.importStreamToVault(
            sourceInputStream = ByteArrayInputStream(originalBytes),
            title = "Test Conversion Video",
            originalExtension = "mp4",
            durationMs = 4000L,
            storageMode = VaultStorageMode.NONE
        )

        assertTrue("Import must succeed", importResult.isSuccess)
        val initialEntity = importResult.getOrNull()
        assertNotNull(initialEntity)
        val entity = initialEntity ?: return@runBlocking
        assertEquals(VaultStorageMode.NONE, entity.storageMode)

        var progressReported = false
        val conversionResult = fileManager.convertVideoProtection(
            vaultEntity = entity,
            targetMode = VaultStorageMode.ENCRYPTED,
            credential = pin,
            securityManager = secManager,
            onProgress = { p ->
                if (p > 0f) progressReported = true
            }
        )

        assertTrue("Conversion NONE -> ENCRYPTED must succeed", conversionResult.isSuccess)
        val convertedEntity = conversionResult.getOrNull()
        assertNotNull(convertedEntity)
        val nonNullConverted = convertedEntity ?: return@runBlocking
        assertEquals(VaultStorageMode.ENCRYPTED, nonNullConverted.storageMode)

        // Verify file on disk is encrypted
        val vaultFile = File(nonNullConverted.vaultPath)
        assertTrue("Vault file must exist", vaultFile.exists())
        val diskBytes = vaultFile.readBytes()
        assertFalse("Ciphertext must not match original plaintext", diskBytes.contentEquals(originalBytes))
        val magic = diskBytes.copyOfRange(0, 8)
        assertArrayEquals(VaultFileFormat.MAGIC_V2_ENCRYPTED, magic)

        // Verify playback extraction works with PIN and produces original bytes
        val playbackFile = fileManager.getPlaybackFile(nonNullConverted, pin)
        assertTrue(playbackFile.exists())
        assertEquals(originalBytes.size.toLong(), playbackFile.length())
        assertArrayEquals(originalBytes, playbackFile.readBytes())
        fileManager.releasePlaybackFile(playbackFile)

        // Verify DB was updated
        val dbEntity = fakeDao.getById(nonNullConverted.id)
        assertNotNull(dbEntity)
        assertEquals(VaultStorageMode.ENCRYPTED, dbEntity?.storageMode)
    }

    @Test
    fun convertVideo_encryptedToNone_success() = runBlocking {
        val (fileManager, secManager, _) = createEnvironment()
        val pin = "5678"
        secManager.createVaultCredential(pin)

        val originalBytes = sampleMp4Bytes()
        val importResult = fileManager.importStreamToVault(
            sourceInputStream = ByteArrayInputStream(originalBytes),
            title = "Encrypted Initial",
            originalExtension = "mp4",
            durationMs = 6000L,
            storageMode = VaultStorageMode.ENCRYPTED,
            vaultCredential = pin
        )

        assertTrue(importResult.isSuccess)
        val initialEntity = importResult.getOrNull()
        assertNotNull(initialEntity)
        val entity = initialEntity ?: return@runBlocking
        assertEquals(VaultStorageMode.ENCRYPTED, entity.storageMode)

        val conversionResult = fileManager.convertVideoProtection(
            vaultEntity = entity,
            targetMode = VaultStorageMode.NONE,
            credential = pin,
            securityManager = secManager
        )

        assertTrue("Conversion ENCRYPTED -> NONE must succeed", conversionResult.isSuccess)
        val convertedEntity = conversionResult.getOrNull()
        assertNotNull(convertedEntity)
        val nonNullConverted = convertedEntity ?: return@runBlocking
        assertEquals(VaultStorageMode.NONE, nonNullConverted.storageMode)

        // Verify file on disk is now the unencrypted payload with .vlt extension
        val vaultFile = File(nonNullConverted.vaultPath)
        assertTrue(vaultFile.exists())
        assertEquals(originalBytes.size.toLong(), vaultFile.length())
        assertArrayEquals(originalBytes, vaultFile.readBytes())

        // Playback works without password
        val playbackFile = fileManager.getPlaybackFile(nonNullConverted, "")
        assertTrue(playbackFile.exists())
        assertArrayEquals(originalBytes, playbackFile.readBytes())
        fileManager.releasePlaybackFile(playbackFile)
    }

    @Test
    fun convertVideo_invalidCredential_fails() = runBlocking {
        val (fileManager, secManager, _) = createEnvironment()
        val correctPin = "4321"
        secManager.createVaultCredential(correctPin)

        val originalBytes = sampleMp4Bytes()
        val initialEntity = fileManager.importStreamToVault(
            sourceInputStream = ByteArrayInputStream(originalBytes),
            title = "Wrong PIN test",
            storageMode = VaultStorageMode.NONE
        ).getOrThrow()

        val wrongResult = fileManager.convertVideoProtection(
            vaultEntity = initialEntity,
            targetMode = VaultStorageMode.ENCRYPTED,
            credential = "9999",
            securityManager = secManager
        )

        assertTrue("Conversion with wrong PIN must fail", wrongResult.isFailure)
        assertTrue(wrongResult.exceptionOrNull() is SecurityException)

        // Original file intact
        val vaultFile = File(initialEntity.vaultPath)
        assertTrue(vaultFile.exists())
        assertArrayEquals(originalBytes, vaultFile.readBytes())

        // DB entity mode unchanged
        val dbEntity = fakeDao.getById(initialEntity.id)
        assertEquals(VaultStorageMode.NONE, dbEntity?.storageMode)
    }

    @Test
    fun convertVideo_sameMode_noOp() = runBlocking {
        val (fileManager, secManager, _) = createEnvironment()
        val pin = "1111"
        secManager.createVaultCredential(pin)

        val originalBytes = sampleMp4Bytes()
        val initialEntity = fileManager.importStreamToVault(
            sourceInputStream = ByteArrayInputStream(originalBytes),
            title = "Same Mode Test",
            storageMode = VaultStorageMode.NONE
        ).getOrThrow()

        val result = fileManager.convertVideoProtection(
            vaultEntity = initialEntity,
            targetMode = VaultStorageMode.NONE,
            credential = pin,
            securityManager = secManager
        )

        assertTrue(result.isSuccess)
        assertEquals(initialEntity, result.getOrNull())
    }

    @Test
    fun convertVideo_cancellation_safeRollback() = runBlocking {
        val (fileManager, secManager, dirs) = createEnvironment()
        val (vaultDir, _, _) = dirs
        val pin = "2222"
        secManager.createVaultCredential(pin)

        val originalBytes = sampleMp4Bytes(64 * 1024)
        val initialEntity = fileManager.importStreamToVault(
            sourceInputStream = ByteArrayInputStream(originalBytes),
            title = "Cancellation Test",
            storageMode = VaultStorageMode.NONE
        ).getOrThrow()

        val result = fileManager.convertVideoProtection(
            vaultEntity = initialEntity,
            targetMode = VaultStorageMode.ENCRYPTED,
            credential = pin,
            securityManager = secManager,
            isCancelled = { true }
        )

        assertTrue("Cancelled conversion must fail", result.isFailure)
        assertTrue(result.exceptionOrNull() is CancellationException)

        // Verify no leftover .converting file
        val convertingFiles = vaultDir.listFiles { f -> f.name.endsWith(".converting") }
        assertTrue(convertingFiles.isNullOrEmpty())

        // Verify original file is still intact
        val originalFile = File(initialEntity.vaultPath)
        assertTrue(originalFile.exists())
        assertEquals(originalBytes.size.toLong(), originalFile.length())
    }

    @Test
    fun processInterruption_cleanupOrphanedPartAndBackupFiles() = runBlocking {
        val (fileManager, _, dirs) = createEnvironment()
        val (vaultDir, _, _) = dirs

        // Create orphaned .converting file
        val orphanConverting = File(vaultDir, "orphan.vlt.converting")
        orphanConverting.writeBytes(byteArrayOf(1, 2, 3))

        // Create a missing primary file with backup
        val missingPrimary = File(vaultDir, "missing.vlt")
        val backupFile = File(vaultDir, "missing.vlt.backup")
        backupFile.writeBytes(byteArrayOf(4, 5, 6))

        val cleanedCount = fileManager.cleanOrphanedPartFiles()
        assertTrue("Must clean or recover files", cleanedCount >= 2)

        assertFalse(orphanConverting.exists())
        assertTrue("Primary file must be recovered from backup", missingPrimary.exists())
        assertArrayEquals(byteArrayOf(4, 5, 6), missingPrimary.readBytes())
        assertFalse(backupFile.exists())
    }
}

class ConversionTestVaultDao : VaultDao {
    private val entities = mutableMapOf<Long, VaultEntity>()
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
