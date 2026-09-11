package com.devson.nvplayer.data.security

import com.devson.nvplayer.data.database.VaultDao
import com.devson.nvplayer.data.database.VaultEntity
import com.devson.nvplayer.domain.model.VaultStorageMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.sql.SQLException

class VaultMigrationManagerTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val fakeDao = FakeVaultDao()
    private val container: VaultContainer = DefaultVaultContainer()

    private fun createMigrationManager(
        cacheDir: File = tempFolder.newFolder("cache"),
        vaultDir: File = tempFolder.newFolder("vault"),
        storageSpaceProvider: (File) -> Long = { Long.MAX_VALUE },
        permissionChecker: (File) -> Boolean = { true }
    ): Pair<VaultMigrationManager, File> {
        val manager = VaultMigrationManager(
            cacheDir = cacheDir,
            vaultDao = fakeDao,
            vaultContainer = container,
            vaultDirectory = vaultDir,
            storageSpaceProvider = storageSpaceProvider,
            permissionChecker = permissionChecker
        )
        return Pair(manager, vaultDir)
    }

    private fun sampleMp4Bytes(): ByteArray {
        // Standard MP4 header with 'ftyp' box
        return byteArrayOf(
            0, 0, 0, 24,
            0x66, 0x74, 0x79, 0x70, // 'ftyp'
            0x69, 0x73, 0x6F, 0x6D, // 'isom'
            0, 0, 2, 0,
            0x6D, 0x70, 0x34, 0x31,
            1, 2, 3, 4
        )
    }

    @Test
    fun successfulLegacyMigration_V1() = runBlocking {
        val (manager, vaultDir) = createMigrationManager()
        val originalMedia = sampleMp4Bytes()
        val legacyFile = File(vaultDir, "video_legacy_v1.vlt")

        LegacyVaultManager.createLegacyV1File(
            sourceBytes = originalMedia,
            destinationFile = legacyFile,
            title = "My Vacation",
            durationMs = 54321L
        )

        val entity = VaultEntity(
            title = "My Vacation",
            originalUri = "content://media/external/video/1",
            vaultPath = legacyFile.absolutePath,
            thumbnailPath = null,
            fileSize = legacyFile.length(),
            durationMs = 54321L,
            dateAdded = 100000L,
            storageMode = VaultStorageMode.ENCRYPTED,
            formatVersion = 1,
            originalExtension = "mp4"
        )
        val entityId = fakeDao.insert(entity)

        assertEquals(VaultFileClassification.LEGACY_ENCRYPTED, manager.classifyFile(legacyFile))

        val credential = "UserDerivedPin4321"
        val result = manager.migrateSingleFile(legacyFile, credential)

        assertTrue("Migration must succeed", result.success)
        assertFalse("Migration must not be skipped", result.skipped)

        assertEquals(VaultFileClassification.CURRENT_SECURE_ENCRYPTED, manager.classifyFile(legacyFile))

        val backupFile = File(vaultDir, "video_legacy_v1.vlt.backup")
        assertFalse("Backup file must be deleted upon successful migration", backupFile.exists())

        val migratingFile = File(vaultDir, "video_legacy_v1.vlt.migrating")
        assertFalse("Intermediate .migrating file must be cleaned up", migratingFile.exists())

        // Verify decrypted payload with modern container
        val restoredFile = File(tempFolder.root, "restored_v1.mp4")
        val restoredHeader = container.decryptVaultFile(legacyFile, restoredFile, credential)
        assertEquals("My Vacation", restoredHeader.title)
        assertArrayEquals(originalMedia, restoredFile.readBytes())

        // Verify Database entity update
        val updatedInDb = fakeDao.getById(entityId)
        assertNotNull(updatedInDb)
        assertEquals(VaultStorageMode.ENCRYPTED, updatedInDb?.storageMode)
        assertEquals(VaultFileFormat.FORMAT_VERSION_V2, updatedInDb?.formatVersion)
        assertEquals(legacyFile.length(), updatedInDb?.fileSize)
    }

    @Test
    fun successfulLegacyMigration_V0() = runBlocking {
        val (manager, vaultDir) = createMigrationManager()
        val originalMedia = sampleMp4Bytes()
        val legacyFile = File(vaultDir, "video_legacy_v0.vlt")

        LegacyVaultManager.createLegacyV0File(
            sourceBytes = originalMedia,
            destinationFile = legacyFile
        )

        assertEquals(VaultFileClassification.LEGACY_ENCRYPTED, manager.classifyFile(legacyFile))

        val credential = "UserPin0000"
        val result = manager.migrateSingleFile(legacyFile, credential)

        assertTrue(result.success)
        assertEquals(VaultFileClassification.CURRENT_SECURE_ENCRYPTED, manager.classifyFile(legacyFile))

        val restoredFile = File(tempFolder.root, "restored_v0.mp4")
        container.decryptVaultFile(legacyFile, restoredFile, credential)
        assertArrayEquals(originalMedia, restoredFile.readBytes())
    }

    @Test
    fun corruptedLegacyFile_doesNotCorruptOrDeleteOriginal() = runBlocking {
        val (manager, vaultDir) = createMigrationManager()
        val corruptedFile = File(vaultDir, "corrupt.vlt")
        // Write 20 bytes of non-media random junk
        val junkBytes = ByteArray(20) { 0xFF.toByte() }
        corruptedFile.writeBytes(junkBytes)

        assertEquals(VaultFileClassification.INVALID_UNKNOWN, manager.classifyFile(corruptedFile))

        val result = manager.migrateSingleFile(corruptedFile, "SomePin")
        assertFalse("Migration of corrupt file must fail", result.success)
        assertTrue(result.error is VaultCorruptedFileException)

        // Original file must be completely untouched
        assertTrue("Original file must remain on disk", corruptedFile.exists())
        assertArrayEquals(junkBytes, corruptedFile.readBytes())
    }

    @Test
    fun interruptedMigration_recoveryCleansIntermediates() = runBlocking {
        val (manager, vaultDir) = createMigrationManager()
        val originalMedia = sampleMp4Bytes()
        val legacyFile = File(vaultDir, "interrupted.vlt")
        LegacyVaultManager.createLegacyV1File(originalMedia, legacyFile)

        // Simulate crash leftover: .migrating file and cache tmp file
        val lingeringMigrating = File(vaultDir, "interrupted.vlt.migrating")
        lingeringMigrating.writeBytes(byteArrayOf(1, 2, 3, 4))

        val lingeringTmp = File(manager.tempMigrationDir, "lingering.tmp")
        lingeringTmp.writeBytes(byteArrayOf(5, 6, 7, 8))

        val recovered = manager.cleanOrRecoverInterruptedMigrations()
        assertTrue("At least one leftover artifact should be cleaned", recovered >= 1)

        assertFalse(lingeringMigrating.exists())
        assertFalse(lingeringTmp.exists())
        assertTrue("Legacy file must still exist and be intact", legacyFile.exists())
        assertEquals(VaultFileClassification.LEGACY_ENCRYPTED, manager.classifyFile(legacyFile))
    }

    @Test
    fun appCrashDuringMigration_restoresFromBackup() = runBlocking {
        val (manager, vaultDir) = createMigrationManager()
        val originalMedia = sampleMp4Bytes()
        val targetVlt = File(vaultDir, "crashed_during_swap.vlt")
        val backupFile = File(vaultDir, "crashed_during_swap.vlt.backup")

        // Original valid legacy file is stored in backup (as happens right after rename)
        LegacyVaultManager.createLegacyV1File(originalMedia, backupFile)

        // Target file is corrupted or partially written when power was lost
        targetVlt.writeBytes(byteArrayOf(9, 9, 9))

        val recovered = manager.cleanOrRecoverInterruptedMigrations()
        assertTrue(recovered >= 1)

        assertFalse(backupFile.exists())
        assertTrue(targetVlt.exists())
        assertEquals(VaultFileClassification.LEGACY_ENCRYPTED, manager.classifyFile(targetVlt))

        // Ensure restored file can be successfully migrated now
        val result = manager.migrateSingleFile(targetVlt, "RecoveryPin")
        assertTrue(result.success)
        assertEquals(VaultFileClassification.CURRENT_SECURE_ENCRYPTED, manager.classifyFile(targetVlt))
    }

    @Test
    fun duplicateMigration_isPrevented() = runBlocking {
        val (manager, vaultDir) = createMigrationManager()
        val originalMedia = sampleMp4Bytes()
        val legacyFile = File(vaultDir, "duplicate_test.vlt")
        LegacyVaultManager.createLegacyV1File(originalMedia, legacyFile)

        val credential = "MyVaultPin99"
        val firstResult = manager.migrateSingleFile(legacyFile, credential)
        assertTrue(firstResult.success)
        assertFalse(firstResult.skipped)

        val fileLengthAfterFirstMigration = legacyFile.length()
        val bytesAfterFirstMigration = legacyFile.readBytes()

        // Attempt second migration on already-migrated file
        val secondResult = manager.migrateSingleFile(legacyFile, credential)
        assertTrue(secondResult.success)
        assertTrue("Second migration must be skipped", secondResult.skipped)

        // File must not be modified or re-encrypted
        assertEquals(fileLengthAfterFirstMigration, legacyFile.length())
        assertArrayEquals(bytesAfterFirstMigration, legacyFile.readBytes())
    }

    @Test
    fun databaseFailure_rollsBackMigration() = runBlocking {
        val (manager, vaultDir) = createMigrationManager()
        val originalMedia = sampleMp4Bytes()
        val legacyFile = File(vaultDir, "db_fail_test.vlt")
        LegacyVaultManager.createLegacyV1File(originalMedia, legacyFile)
        val originalLegacyBytes = legacyFile.readBytes()

        fakeDao.shouldFailOnInsert = true

        val result = manager.migrateSingleFile(legacyFile, "Pin123")
        assertFalse("Migration must fail if DB insert fails", result.success)

        // File on disk must be rolled back to original legacy bytes
        assertTrue(legacyFile.exists())
        assertArrayEquals(originalLegacyBytes, legacyFile.readBytes())
        assertEquals(VaultFileClassification.LEGACY_ENCRYPTED, manager.classifyFile(legacyFile))

        val backupFile = File(vaultDir, "db_fail_test.vlt.backup")
        assertFalse("Backup file must not linger", backupFile.exists())
    }

    @Test
    fun insufficientStorage_failsCleanlyWithoutAlteringFile() = runBlocking {
        val (manager, vaultDir) = createMigrationManager(
            storageSpaceProvider = { 1024L } // Only 1 KB available
        )
        val originalMedia = sampleMp4Bytes()
        val legacyFile = File(vaultDir, "storage_fail.vlt")
        LegacyVaultManager.createLegacyV1File(originalMedia, legacyFile)
        val originalBytes = legacyFile.readBytes()

        val result = manager.migrateSingleFile(legacyFile, "Pin123")
        assertFalse(result.success)
        assertTrue(result.error is VaultInsufficientStorageException)

        // Legacy file must be completely untouched
        assertArrayEquals(originalBytes, legacyFile.readBytes())
    }

    @Test
    fun permissionFailure_failsCleanlyWithoutAlteringFile() = runBlocking {
        val (manager, vaultDir) = createMigrationManager(
            permissionChecker = { false } // Simulate permission denied
        )
        val originalMedia = sampleMp4Bytes()
        val legacyFile = File(vaultDir, "permission_fail.vlt")
        LegacyVaultManager.createLegacyV1File(originalMedia, legacyFile)
        val originalBytes = legacyFile.readBytes()

        val result = manager.migrateSingleFile(legacyFile, "Pin123")
        assertFalse(result.success)
        assertTrue(result.error is SecurityException)

        // Legacy file must be completely untouched
        assertArrayEquals(originalBytes, legacyFile.readBytes())
    }

    @Test
    fun existingNoEncryptionFiles_neverUndergoLegacyDecryption() = runBlocking {
        val (manager, vaultDir) = createMigrationManager()
        val originalMedia = sampleMp4Bytes()
        val unencryptedFile = File(vaultDir, "plain_media.vlt")
        unencryptedFile.writeBytes(originalMedia)

        // Must be classified as CURRENT_NO_ENCRYPTION
        assertEquals(VaultFileClassification.CURRENT_NO_ENCRYPTION, manager.classifyFile(unencryptedFile))

        // Migrate should safely skip this file
        val result = manager.migrateSingleFile(unencryptedFile, "Pin123")
        assertTrue(result.success)
        assertTrue(result.skipped)

        // Must be byte-for-byte identical to original raw bytes
        assertArrayEquals(originalMedia, unencryptedFile.readBytes())
    }

    @Test
    fun batchMigration_migrateAllWithProgress() = runBlocking {
        val (manager, vaultDir) = createMigrationManager()
        val originalMedia = sampleMp4Bytes()

        for (i in 1..3) {
            val file = File(vaultDir, "batch_$i.vlt")
            LegacyVaultManager.createLegacyV1File(originalMedia, file, title = "Video $i")
        }

        val progressList = mutableListOf<String>()
        val summary = manager.migrateAll("BatchPin") { current, total, name ->
            progressList.add("$current/$total: $name")
        }

        assertEquals(3, summary.totalFound)
        assertEquals(3, summary.successful)
        assertEquals(0, summary.failed)
        assertEquals(0, summary.skipped)
        assertEquals(3, progressList.size)

        // Verify all 3 files are now CURRENT_SECURE_ENCRYPTED
        for (i in 1..3) {
            val file = File(vaultDir, "batch_$i.vlt")
            assertEquals(VaultFileClassification.CURRENT_SECURE_ENCRYPTED, manager.classifyFile(file))
        }
    }
}

class FakeVaultDao : VaultDao {
    private val entities = mutableMapOf<Long, VaultEntity>()
    private var nextId = 1L
    var shouldFailOnInsert = false

    override suspend fun insert(vaultMedia: VaultEntity): Long {
        if (shouldFailOnInsert) {
            throw SQLException("Simulated Room database write failure")
        }
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
