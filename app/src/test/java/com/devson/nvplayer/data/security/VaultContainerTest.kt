package com.devson.nvplayer.data.security

import com.devson.nvplayer.domain.model.VaultStorageMode
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.io.RandomAccessFile

class VaultContainerTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val container: VaultContainer = DefaultVaultContainer()

    @Test
    fun encryptedRoundTrip() = runBlocking {
        val originalBytes = "Nosved Player Secret Media Data 1234567890".toByteArray(Charsets.UTF_8)
        val vaultFile = tempFolder.newFile("test_encrypted.vlt")
        val restoredFile = tempFolder.newFile("test_restored.mp4")
        val password = "SecurePassword2026!"

        val header = container.createEncryptedVaultFile(
            sourceInputStream = ByteArrayInputStream(originalBytes),
            destinationVaultFile = vaultFile,
            passwordOrPin = password,
            title = "My Vacation Video",
            originalExtension = "mp4"
        )

        assertEquals(VaultStorageMode.ENCRYPTED, header.storageMode)
        assertEquals("mp4", header.originalExtension)
        assertEquals("My Vacation Video", header.title)

        val decryptedHeader = container.decryptVaultFile(
            vaultFile = vaultFile,
            destinationFile = restoredFile,
            passwordOrPin = password
        )

        assertEquals(header.title, decryptedHeader.title)
        val decryptedBytes = restoredFile.readBytes()
        assertArrayEquals(originalBytes, decryptedBytes)
    }

    @Test
    fun noEncryptionRoundTrip() = runBlocking {
        // Fake MP4 header: 4 dummy bytes + 'ftyp'
        val originalBytes = byteArrayOf(0, 0, 0, 20, 0x66, 0x74, 0x79, 0x70, 1, 2, 3, 4, 5)
        val vaultFile = tempFolder.newFile("unencrypted.vlt")
        val restoredFile = tempFolder.newFile("unencrypted_restored.mp4")

        val header = container.createUnencryptedVaultFile(
            sourceInputStream = ByteArrayInputStream(originalBytes),
            destinationVaultFile = vaultFile,
            title = "Unencrypted Media",
            originalExtension = "mp4"
        )

        assertEquals(VaultStorageMode.NONE, header.storageMode)
        assertFalse(header.isEncrypted)

        // Verify that raw bytes on disk in the vault file are byte-for-byte identical
        assertArrayEquals(originalBytes, vaultFile.readBytes())

        // Verify opening stream
        container.openUnencryptedVaultFile(vaultFile).use { stream ->
            val readBytes = stream.readBytes()
            assertArrayEquals(originalBytes, readBytes)
        }

        // Verify decryption/restore for unencrypted file
        container.decryptVaultFile(vaultFile, restoredFile, "")
        assertArrayEquals(originalBytes, restoredFile.readBytes())

        // Verify intentional rename behavior: renaming .vlt to .mp4 gives the exact original file
        val renamedFile = File(tempFolder.root, "renamed.mp4")
        vaultFile.copyTo(renamedFile)
        assertArrayEquals(originalBytes, renamedFile.readBytes())
    }

    @Test
    fun wrongPassword() = runBlocking {
        val originalBytes = "Secret Data".toByteArray(Charsets.UTF_8)
        val vaultFile = tempFolder.newFile("auth_test.vlt")
        val restoredFile = File(tempFolder.root, "should_not_exist.mp4")

        container.createEncryptedVaultFile(
            sourceInputStream = ByteArrayInputStream(originalBytes),
            destinationVaultFile = vaultFile,
            passwordOrPin = "CorrectPassword123",
            title = "Auth Test",
            originalExtension = "mp4"
        )

        try {
            container.decryptVaultFile(
                vaultFile = vaultFile,
                destinationFile = restoredFile,
                passwordOrPin = "WrongPassword456"
            )
            fail("Expected VaultIntegrityException when decrypting with wrong password")
        } catch (e: VaultIntegrityException) {
            // Expected
        }

        assertFalse("Destination file must be removed after failed decryption", restoredFile.exists())
    }

    @Test
    fun modifiedCiphertext() = runBlocking {
        val originalBytes = "Integrity Protected Media Content".toByteArray(Charsets.UTF_8)
        val vaultFile = tempFolder.newFile("tamper_test.vlt")
        val restoredFile = File(tempFolder.root, "tampered_restored.mp4")
        val password = "TamperPassword123"

        container.createEncryptedVaultFile(
            sourceInputStream = ByteArrayInputStream(originalBytes),
            destinationVaultFile = vaultFile,
            passwordOrPin = password,
            title = "Tamper Test",
            originalExtension = "mp4"
        )

        // Corrupt a byte in the ciphertext payload (near the end of the file)
        RandomAccessFile(vaultFile, "rw").use { raf ->
            val pos = raf.length() - 8
            raf.seek(pos)
            val byte = raf.readByte()
            raf.seek(pos)
            raf.writeByte(byte.toInt() xor 0xFF)
        }

        // Integrity verification should return false
        val isValid = container.verifyVaultFileIntegrity(vaultFile, password)
        assertFalse("Tampered file must fail integrity verification", isValid)

        // Decryption should throw VaultIntegrityException
        try {
            container.decryptVaultFile(
                vaultFile = vaultFile,
                destinationFile = restoredFile,
                passwordOrPin = password
            )
            fail("Expected VaultIntegrityException when decrypting tampered file")
        } catch (e: VaultIntegrityException) {
            // Expected
        }

        assertFalse("Destination file must not exist after failed integrity check", restoredFile.exists())
    }

    @Test
    fun invalidHeader() = runBlocking {
        val garbageFile = tempFolder.newFile("garbage.vlt")
        garbageFile.writeBytes(byteArrayOf(1, 2, 3, 4, 5, 6, 7, 8, 9, 10))

        // Inspecting garbage file treats it as raw unencrypted file
        val header = container.inspectVaultFile(garbageFile)
        assertEquals(VaultStorageMode.NONE, header.storageMode)

        // If an encrypted file's header is tampered with, AAD verification must fail
        val validEncrypted = tempFolder.newFile("valid_enc.vlt")
        container.createEncryptedVaultFile(
            sourceInputStream = ByteArrayInputStream("Header Test Data".toByteArray()),
            destinationVaultFile = validEncrypted,
            passwordOrPin = "HeaderPass",
            title = "Original Title",
            originalExtension = "mp4"
        )

        // Tamper with format version in header (offset 8 and 9)
        RandomAccessFile(validEncrypted, "rw").use { raf ->
            raf.seek(8)
            raf.writeShort(99) // Invalid format version
        }

        val restoredFile = File(tempFolder.root, "header_tampered.mp4")
        try {
            container.decryptVaultFile(validEncrypted, restoredFile, "HeaderPass")
            fail("Expected VaultIntegrityException when header AAD is tampered")
        } catch (e: VaultIntegrityException) {
            // Expected: AEAD tag failure because AAD was modified
        }
    }

    @Test
    fun truncatedFile() = runBlocking {
        val originalBytes = "Truncation Test Plaintext That Needs Protection".toByteArray(Charsets.UTF_8)
        val vaultFile = tempFolder.newFile("truncate_test.vlt")
        val password = "TruncatePassword"

        container.createEncryptedVaultFile(
            sourceInputStream = ByteArrayInputStream(originalBytes),
            destinationVaultFile = vaultFile,
            passwordOrPin = password,
            title = "Truncate Test",
            originalExtension = "mp4"
        )

        // Truncate file by 10 bytes (cutting into the 16-byte GCM tag)
        RandomAccessFile(vaultFile, "rw").use { raf ->
            raf.setLength(raf.length() - 10)
        }

        val restoredFile = File(tempFolder.root, "truncate_restored.mp4")
        try {
            container.decryptVaultFile(vaultFile, restoredFile, password)
            fail("Expected VaultIntegrityException for truncated file")
        } catch (e: VaultIntegrityException) {
            // Expected
        }
    }

    @Test
    fun largeFileStreaming() = runBlocking {
        val totalSize = 3 * 1024 * 1024 // 3 MB synthetic stream
        val sourceFile = tempFolder.newFile("large_source.bin")
        FileOutputStream(sourceFile).buffered().use { fos ->
            val pattern = "NosvedStreamingChunk2026!".toByteArray(Charsets.UTF_8)
            var written = 0
            while (written < totalSize) {
                val toWrite = minOf(pattern.size, totalSize - written)
                fos.write(pattern, 0, toWrite)
                written += toWrite
            }
            fos.flush()
        }

        val vaultFile = tempFolder.newFile("large_vault.vlt")
        val restoredFile = tempFolder.newFile("large_restored.bin")
        val password = "LargeFilePassword"

        FileInputStream(sourceFile).buffered().use { input ->
            container.createEncryptedVaultFile(
                sourceInputStream = input,
                destinationVaultFile = vaultFile,
                passwordOrPin = password,
                title = "Large Stream",
                originalExtension = "bin",
                originalSize = sourceFile.length()
            )
        }

        container.decryptVaultFile(vaultFile, restoredFile, password)

        assertEquals(sourceFile.length(), restoredFile.length())

        // Compare byte-by-byte using buffered streams without loading into memory
        FileInputStream(sourceFile).buffered().use { in1 ->
            FileInputStream(restoredFile).buffered().use { in2 ->
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
    }

    @Test
    fun differentNonceForEveryEncryptedFile() = runBlocking {
        val plainBytes = "Same Plaintext For Multiple Files".toByteArray(Charsets.UTF_8)
        val file1 = tempFolder.newFile("nonce_test_1.vlt")
        val file2 = tempFolder.newFile("nonce_test_2.vlt")
        val password = "SamePassword"
        val fixedSalt = ByteArray(32) { 7 }

        val h1 = container.createEncryptedVaultFile(
            sourceInputStream = ByteArrayInputStream(plainBytes),
            destinationVaultFile = file1,
            passwordOrPin = password,
            title = "File 1",
            originalExtension = "mp4",
            customSalt = fixedSalt
        )

        val h2 = container.createEncryptedVaultFile(
            sourceInputStream = ByteArrayInputStream(plainBytes),
            destinationVaultFile = file2,
            passwordOrPin = password,
            title = "File 2",
            originalExtension = "mp4",
            customSalt = fixedSalt
        )

        // Nonces must be unique even with identical password, salt, and plaintext
        assertFalse("Nonces must be different for every encrypted file", h1.nonce.contentEquals(h2.nonce))

        // Ciphertexts on disk must differ
        val bytes1 = file1.readBytes()
        val bytes2 = file2.readBytes()
        assertFalse("Ciphertext outputs must be distinct due to unique nonces", bytes1.contentEquals(bytes2))
    }

    @Test
    fun samePasswordReproducingTheSameDerivedKeyOnlyWhenSaltIsTheSame() {
        val password = "MyDeterministicVaultPin1234"
        val salt = VaultKeyDerivation.generateSalt()

        val key1 = VaultKeyDerivation.deriveKey(password, salt, 1000)
        val key2 = VaultKeyDerivation.deriveKey(password, salt, 1000)

        assertArrayEquals(
            "Same password and same salt must deterministically reproduce the same key",
            key1.encoded,
            key2.encoded
        )
    }

    @Test
    fun differentSaltProducingDifferentKeys() {
        val password = "MyDeterministicVaultPin1234"
        val salt1 = VaultKeyDerivation.generateSalt()
        val salt2 = VaultKeyDerivation.generateSalt()

        val key1 = VaultKeyDerivation.deriveKey(password, salt1, 1000)
        val key2 = VaultKeyDerivation.deriveKey(password, salt2, 1000)

        assertFalse(
            "Different salts must produce different derived keys",
            key1.encoded.contentEquals(key2.encoded)
        )
    }
}
