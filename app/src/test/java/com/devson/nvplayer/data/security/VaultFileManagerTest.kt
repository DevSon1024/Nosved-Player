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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.RandomAccessFile

class VaultFileManagerTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val fakeDao = TestVaultDao()
    private val container: VaultContainer = DefaultVaultContainer()

    private fun sampleMp4Bytes(size: Int = 1024): ByteArray {
        val bytes = ByteArray(size) { (it % 250).toByte() }
        // MP4 magic box 'ftyp'
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

    private fun createVaultFileManager(): Pair<VaultFileManager, Triple<File, File, File>> {
        val vaultDir = tempFolder.newFolder("vault_${System.nanoTime()}")
        val thumbsDir = tempFolder.newFolder("thumbs_${System.nanoTime()}")
        val tempPlaybackDir = tempFolder.newFolder("playback_${System.nanoTime()}")

        val manager = VaultFileManager(
            context = null,
            vaultDao = fakeDao,
            vaultContainer = container,
            customVaultDirectory = vaultDir,
            customThumbsDirectory = thumbsDir,
            customTempPlaybackDirectory = tempPlaybackDir
        )
        return Pair(manager, Triple(vaultDir, thumbsDir, tempPlaybackDir))
    }

    @Test
    fun encryptedImport() = runBlocking {
        val (manager, dirs) = createVaultFileManager()
        val (vaultDir, _, _) = dirs
        val originalBytes = sampleMp4Bytes()
        val password = "MySecretPassword123"

        val result = manager.importStreamToVault(
            sourceInputStream = ByteArrayInputStream(originalBytes),
            title = "Secret Video",
            originalExtension = "mp4",
            durationMs = 5000L,
            storageMode = VaultStorageMode.ENCRYPTED,
            vaultCredential = password
        )

        assertTrue("Encrypted import must succeed", result.isSuccess)
        val entity = result.getOrNull()
        assertNotNull(entity)
        val nonNullEntity = entity ?: return@runBlocking

        assertEquals(VaultStorageMode.ENCRYPTED, nonNullEntity.storageMode)
        assertEquals(VaultFileFormat.FORMAT_VERSION_V2, nonNullEntity.formatVersion)
        assertEquals("mp4", nonNullEntity.originalExtension)

        val vaultFile = File(nonNullEntity.vaultPath)
        assertTrue("Vault file must exist on disk", vaultFile.exists())

        // Ensure file is NOT raw plaintext on disk
        val diskBytes = vaultFile.readBytes()
        assertFalse("Ciphertext on disk must not equal plaintext", diskBytes.contentEquals(originalBytes))

        // Ensure header magic matches modern V2
        val magic = diskBytes.copyOfRange(0, 8)
        assertArrayEquals(VaultFileFormat.MAGIC_V2_ENCRYPTED, magic)

        // Verify entity in DB
        val fromDb = fakeDao.getById(nonNullEntity.id)
        assertNotNull(fromDb)
        assertEquals(nonNullEntity.title, fromDb?.title)
    }

    @Test
    fun noEncryptionImport() = runBlocking {
        val (manager, dirs) = createVaultFileManager()
        val originalBytes = sampleMp4Bytes()

        val result = manager.importStreamToVault(
            sourceInputStream = ByteArrayInputStream(originalBytes),
            title = "Unencrypted Video",
            originalExtension = "mp4",
            durationMs = 3000L,
            storageMode = VaultStorageMode.NONE
        )

        assertTrue(result.isSuccess)
        val entity = result.getOrNull()
        assertNotNull(entity)
        val nonNullEntity = entity ?: return@runBlocking

        assertEquals(VaultStorageMode.NONE, nonNullEntity.storageMode)

        val vaultFile = File(nonNullEntity.vaultPath)
        assertTrue(vaultFile.exists())

        // In no-encryption mode, original bytes are preserved byte-for-byte
        assertArrayEquals("Raw bytes must be preserved byte-for-byte", originalBytes, vaultFile.readBytes())
    }

    @Test
    fun playback() = runBlocking {
        val (manager, dirs) = createVaultFileManager()
        val (_, _, tempPlaybackDir) = dirs
        val originalBytes = sampleMp4Bytes()
        val password = "PlaybackPassword"

        // 1. Encrypted playback
        val encEntity = manager.importStreamToVault(
            sourceInputStream = ByteArrayInputStream(originalBytes),
            title = "Play Encrypted",
            storageMode = VaultStorageMode.ENCRYPTED,
            vaultCredential = password
        ).getOrThrow()

        val playbackFile = manager.getPlaybackFile(encEntity, password)
        assertTrue(playbackFile.exists())
        assertEquals(tempPlaybackDir.absolutePath, playbackFile.parentFile?.absolutePath)
        assertArrayEquals(originalBytes, playbackFile.readBytes())

        // Releasing playback file deletes it
        manager.releasePlaybackFile(playbackFile)
        assertFalse(playbackFile.exists())

        // 2. No-encryption playback
        val plainEntity = manager.importStreamToVault(
            sourceInputStream = ByteArrayInputStream(originalBytes),
            title = "Play Plain",
            storageMode = VaultStorageMode.NONE
        ).getOrThrow()

        val plainPlayback = manager.getPlaybackFile(plainEntity)
        assertTrue(plainPlayback.exists())
        assertArrayEquals(originalBytes, plainPlayback.readBytes())

        manager.cleanPlaybackTemp()
        assertFalse(plainPlayback.exists())
    }

    @Test
    fun restore() = runBlocking {
        val (manager, dirs) = createVaultFileManager()
        val (vaultDir, _, _) = dirs
        val originalBytes = sampleMp4Bytes()
        val password = "RestorePassword"

        val entity = manager.importStreamToVault(
            sourceInputStream = ByteArrayInputStream(originalBytes),
            title = "To Restore",
            originalExtension = "mp4",
            storageMode = VaultStorageMode.ENCRYPTED,
            vaultCredential = password
        ).getOrThrow()

        val restoreDir = tempFolder.newFolder("restored_output")
        val result = manager.restoreVideoFromVault(entity, restoreDir, password)

        assertTrue(result.isSuccess)
        val restoredFile = result.getOrThrow()
        assertTrue(restoredFile.exists())
        assertEquals("To Restore.mp4", restoredFile.name)
        assertArrayEquals(originalBytes, restoredFile.readBytes())

        // Vault file and database entry must be removed after successful verified restore
        val vaultFile = File(entity.vaultPath)
        assertFalse("Vault file must be deleted after restore", vaultFile.exists())
        assertNull(fakeDao.getById(entity.id))
    }

    @Test
    fun restoreUnencryptedVideo() = runBlocking {
        val (manager, dirs) = createVaultFileManager()
        val originalBytes = sampleMp4Bytes()

        val entity = manager.importStreamToVault(
            sourceInputStream = ByteArrayInputStream(originalBytes),
            title = "Unencrypted Restore",
            originalExtension = "mp4",
            storageMode = VaultStorageMode.NONE
        ).getOrThrow()

        val restoreDir = tempFolder.newFolder("unencrypted_restored_output")
        val result = manager.restoreVideoFromVault(entity, restoreDir)

        assertTrue(result.isSuccess)
        val restoredFile = result.getOrThrow()
        assertTrue(restoredFile.exists())
        assertEquals("Unencrypted Restore.mp4", restoredFile.name)
        assertArrayEquals(originalBytes, restoredFile.readBytes())

        val vaultFile = File(entity.vaultPath)
        assertFalse("Vault file must be deleted after restore", vaultFile.exists())
        assertNull(fakeDao.getById(entity.id))
    }

    @Test
    fun resolveRestoreDestination_returnsOriginalParentWhenExists_andFallbackWhenMissing() = runBlocking {
        val (manager, _) = createVaultFileManager()
        val defaultMoviesDir = tempFolder.newFolder("default_movies")

        // 1. Original folder exists
        val customFolder = tempFolder.newFolder("custom_subtitles")
        val originalFile = File(customFolder, "demo.mp4")
        val entityWithExistingFolder = VaultEntity(
            title = "demo",
            originalUri = originalFile.absolutePath,
            vaultPath = "dummy.vlt",
            fileSize = 100L
        )
        val resolvedExisting = manager.resolveRestoreDestination(entityWithExistingFolder, defaultMoviesDir)
        assertEquals(customFolder.canonicalPath, resolvedExisting.canonicalPath)

        // 2. Original folder was deleted
        val deletedFolder = File(tempFolder.root, "deleted_folder")
        val nonExistentFile = File(deletedFolder, "deleted.mp4")
        val entityWithDeletedFolder = VaultEntity(
            title = "deleted",
            originalUri = nonExistentFile.absolutePath,
            vaultPath = "dummy.vlt",
            fileSize = 100L
        )
        val resolvedDeleted = manager.resolveRestoreDestination(entityWithDeletedFolder, defaultMoviesDir)
        assertEquals(defaultMoviesDir.canonicalPath, resolvedDeleted.canonicalPath)

        // 3. Original URI is blank
        val entityWithBlankUri = VaultEntity(
            title = "blank",
            originalUri = "",
            vaultPath = "dummy.vlt",
            fileSize = 100L
        )
        val resolvedBlank = manager.resolveRestoreDestination(entityWithBlankUri, defaultMoviesDir)
        assertEquals(defaultMoviesDir.canonicalPath, resolvedBlank.canonicalPath)
    }

    @Test
    fun restoreToOriginalFolder_restoresAtExactLocationWhenDirectoryExists() = runBlocking {
        val (manager, _) = createVaultFileManager()
        val originalBytes = sampleMp4Bytes()
        val originalFolder = tempFolder.newFolder("original_custom_folder")
        val dummyOriginalFile = File(originalFolder, "my_clip.mp4")

        val entity = manager.importStreamToVault(
            sourceInputStream = ByteArrayInputStream(originalBytes),
            title = "my_clip",
            originalExtension = "mp4",
            storageMode = VaultStorageMode.NONE,
            originalUri = dummyOriginalFile.absolutePath
        ).getOrThrow()

        val defaultMoviesDir = tempFolder.newFolder("default_movies_fallback")
        val targetDestination = manager.resolveRestoreDestination(entity, defaultMoviesDir)
        assertEquals(originalFolder.canonicalPath, targetDestination.canonicalPath)

        val result = manager.restoreVideoFromVault(entity, targetDestination)
        assertTrue(result.isSuccess)
        val restoredFile = result.getOrThrow()
        assertEquals(originalFolder.canonicalPath, restoredFile.parentFile?.canonicalPath)
        assertEquals("my_clip.mp4", restoredFile.name)
        assertArrayEquals(originalBytes, restoredFile.readBytes())
    }

    @Test
    fun deletion() = runBlocking {
        val (manager, dirs) = createVaultFileManager()
        val originalBytes = sampleMp4Bytes()

        val entity = manager.importStreamToVault(
            sourceInputStream = ByteArrayInputStream(originalBytes),
            title = "To Delete",
            storageMode = VaultStorageMode.NONE
        ).getOrThrow()

        val vaultFile = File(entity.vaultPath)
        assertTrue(vaultFile.exists())

        val deleteResult = manager.deletePermanently(entity)
        assertTrue(deleteResult.isSuccess)

        assertFalse("Vault file must be deleted permanently", vaultFile.exists())
        assertNull("Database record must be deleted", fakeDao.getById(entity.id))
    }

    @Test
    fun interruptedImport() = runBlocking {
        val (manager, dirs) = createVaultFileManager()
        val (vaultDir, _, _) = dirs

        // Simulate failing stream mid-transfer
        val failingStream = object : InputStream() {
            private var count = 0
            override fun read(): Int {
                if (++count > 200) throw IOException("Simulated network/disk crash during stream")
                return 42
            }
        }

        val result = manager.importStreamToVault(
            sourceInputStream = failingStream,
            title = "Failing Stream",
            storageMode = VaultStorageMode.ENCRYPTED,
            vaultCredential = "Pass"
        )

        assertTrue("Import must fail on stream error", result.isFailure)

        // Ensure no stray .vlt files or entities linger
        val vltFiles = vaultDir.listFiles { f -> f.extension == "vlt" } ?: emptyArray()
        assertEquals(0, vltFiles.size)
        assertEquals(0, fakeDao.getAllVaultMedia().size)

        // Orphaned .part files cleaned
        val dummyPart = File(vaultDir, "stale.vlt.part")
        dummyPart.writeBytes(byteArrayOf(1, 2, 3))
        assertTrue(dummyPart.exists())

        val cleaned = manager.cleanOrphanedPartFiles()
        assertTrue(cleaned >= 1)
        assertFalse(dummyPart.exists())
    }

    @Test
    fun corruptedEncryptedFile() = runBlocking {
        val (manager, dirs) = createVaultFileManager()
        val originalBytes = sampleMp4Bytes()
        val password = "Pass"

        val entity = manager.importStreamToVault(
            sourceInputStream = ByteArrayInputStream(originalBytes),
            title = "Corrupt Me",
            storageMode = VaultStorageMode.ENCRYPTED,
            vaultCredential = password
        ).getOrThrow()

        // Corrupt ciphertext body
        val vaultFile = File(entity.vaultPath)
        RandomAccessFile(vaultFile, "rw").use { raf ->
            raf.seek(raf.length() - 8)
            raf.writeByte(0xAA)
            raf.writeByte(0xBB)
        }

        try {
            manager.getPlaybackFile(entity, password)
            fail("Expected VaultIntegrityException when decrypting corrupted vault file")
        } catch (e: VaultIntegrityException) {
            // Expected
        }
    }

    @Test
    fun wrongPassword() = runBlocking {
        val (manager, dirs) = createVaultFileManager()
        val originalBytes = sampleMp4Bytes()

        val entity = manager.importStreamToVault(
            sourceInputStream = ByteArrayInputStream(originalBytes),
            title = "Wrong Pass Test",
            storageMode = VaultStorageMode.ENCRYPTED,
            vaultCredential = "CorrectPassword123"
        ).getOrThrow()

        try {
            manager.getPlaybackFile(entity, "WrongPassword!")
            fail("Expected VaultIntegrityException or VaultAuthenticationException on wrong password")
        } catch (e: SecurityException) {
            // Expected
        }
    }

    @Test
    fun databaseMissing() = runBlocking {
        val (manager, dirs) = createVaultFileManager()
        val originalBytes = sampleMp4Bytes()

        // Create one encrypted and one unencrypted file
        val enc = manager.importStreamToVault(
            sourceInputStream = ByteArrayInputStream(originalBytes),
            title = "Encrypted Item",
            storageMode = VaultStorageMode.ENCRYPTED,
            vaultCredential = "Pass"
        ).getOrThrow()

        val plain = manager.importStreamToVault(
            sourceInputStream = ByteArrayInputStream(originalBytes),
            title = "Plain Item",
            storageMode = VaultStorageMode.NONE
        ).getOrThrow()

        // Wipe database completely
        fakeDao.clearAll()
        assertEquals(0, fakeDao.getAllVaultMedia().size)

        // Rebuild from storage
        val rebuild = manager.rebuildDatabaseFromStorage("Pass")
        assertEquals(2, rebuild.restoredCount)
        assertEquals(0, rebuild.invalidSkippedCount)

        val restoredEntities = fakeDao.getAllVaultMedia()
        assertEquals(2, restoredEntities.size)

        val encRestored = restoredEntities.firstOrNull { it.vaultPath == enc.vaultPath }
        assertNotNull(encRestored)
        assertEquals(VaultStorageMode.ENCRYPTED, encRestored?.storageMode)

        val plainRestored = restoredEntities.firstOrNull { it.vaultPath == plain.vaultPath }
        assertNotNull(plainRestored)
        assertEquals(VaultStorageMode.NONE, plainRestored?.storageMode)
    }

    @Test
    fun databaseRebuiltFromStorage_handlesMixedAndInvalidFiles() = runBlocking {
        val (manager, dirs) = createVaultFileManager()
        val (vaultDir, _, _) = dirs
        val originalBytes = sampleMp4Bytes()

        // 1. Modern Encrypted
        manager.importStreamToVault(
            sourceInputStream = ByteArrayInputStream(originalBytes),
            title = "Modern Enc",
            storageMode = VaultStorageMode.ENCRYPTED,
            vaultCredential = "Pass"
        ).getOrThrow()

        // 2. Modern None
        manager.importStreamToVault(
            sourceInputStream = ByteArrayInputStream(originalBytes),
            title = "Modern Plain",
            storageMode = VaultStorageMode.NONE
        ).getOrThrow()

        // 3. Legacy V1
        val legacyFile = File(vaultDir, "legacy_file.vlt")
        LegacyVaultManager.createLegacyV1File(originalBytes, legacyFile, title = "Legacy Item")

        // 4. Corrupted / Invalid file
        val corruptedFile = File(vaultDir, "corrupted_garbage.vlt")
        corruptedFile.writeBytes(byteArrayOf(1, 2, 3, 4, 5))

        fakeDao.clearAll()

        // Rebuild should restore 3 valid files and skip the corrupted one without crashing
        val rebuild = manager.rebuildDatabaseFromStorage("Pass")
        assertEquals(3, rebuild.restoredCount)
        assertTrue(rebuild.invalidSkippedCount >= 1)

        val restored = fakeDao.getAllVaultMedia()
        assertEquals(3, restored.size)
    }

    @Test
    fun orphanedTempFiles() = runBlocking {
        val (manager, dirs) = createVaultFileManager()
        val (vaultDir, _, tempPlaybackDir) = dirs

        val stalePart1 = File(vaultDir, "orphan1.vlt.part")
        stalePart1.writeBytes(byteArrayOf(1, 2, 3))

        val staleTmp2 = File(tempPlaybackDir, "orphan2.tmp")
        staleTmp2.writeBytes(byteArrayOf(4, 5, 6))

        manager.cleanPlaybackTemp()

        assertFalse(stalePart1.exists())
        assertFalse(staleTmp2.exists())
    }

    @Test
    fun largeVideos() = runBlocking {
        val (manager, dirs) = createVaultFileManager()
        val largeSize = 3 * 1024 * 1024 // 3 MB stream
        val sourceFile = tempFolder.newFile("large_input.mp4")

        // Create 3MB file with MP4 header
        FileOutputStream(sourceFile).buffered().use { fos ->
            val chunk = sampleMp4Bytes(64 * 1024)
            var written = 0
            while (written < largeSize) {
                val toWrite = minOf(chunk.size, largeSize - written)
                fos.write(chunk, 0, toWrite)
                written += toWrite
            }
            fos.flush()
        }

        val password = "LargeVideoPassword"
        val entity = manager.importFileToVault(
            sourceFile = sourceFile,
            title = "Large Movie",
            storageMode = VaultStorageMode.ENCRYPTED,
            vaultCredential = password
        ).getOrThrow()

        val playbackFile = manager.getPlaybackFile(entity, password)
        assertEquals(sourceFile.length(), playbackFile.length())

        // Byte-by-byte streaming verification
        FileInputStream(sourceFile).buffered().use { in1 ->
            FileInputStream(playbackFile).buffered().use { in2 ->
                val b1 = ByteArray(64 * 1024)
                val b2 = ByteArray(64 * 1024)
                var r1: Int
                while (in1.read(b1).also { r1 = it } != -1) {
                    val r2 = in2.read(b2)
                    assertEquals(r1, r2)
                    assertArrayEquals(b1.copyOf(r1), b2.copyOf(r2))
                }
            }
        }

        manager.releasePlaybackFile(playbackFile)
        assertFalse(playbackFile.exists())
    }
}

class TestVaultDao : VaultDao {
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

    fun clearAll() {
        entities.clear()
    }
}
