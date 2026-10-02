package com.vikas.varchive.archive

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.io.path.createTempDirectory

class ArchiveEngineIntegrationTest {
    private val engine = DefaultArchiveEngine()

    @Test fun zipSevenZTarAndTarGzRoundTrip() = runTest {
        listOf(ArchiveFormat.ZIP, ArchiveFormat.SEVEN_Z, ArchiveFormat.TAR, ArchiveFormat.TAR_GZ).forEach { format ->
            withTemp { root ->
                val source = File(root, "source.txt").apply { writeText("VArchive round-trip $format") }
                val archive = File(root, "result.${format.primaryExtension}")
                engine.compress(listOf(LocalSource(source, "folder/source.txt")), archive, CompressionOptions(format))
                assertTrue("$format output", archive.length() > 0)
                val entries = engine.list(archive)
                assertEquals("folder/source.txt", entries.single().path)
                engine.test(archive)
                val extracted = File(root, "out")
                engine.extract(archive, extracted)
                assertEquals(source.readText(), File(extracted, "folder/source.txt").readText())
            }
        }
    }

    @Test fun encryptedZipRoundTripAndWrongPasswordIsFriendly() = runTest {
        withTemp { root ->
            val source = File(root, "secret.txt").apply { writeText("confidential") }
            val archive = File(root, "secret.zip")
            engine.compress(
                listOf(LocalSource(source, source.name)),
                archive,
                CompressionOptions(ArchiveFormat.ZIP, password = "correct horse".toCharArray()),
            )
            assertTrue(engine.list(archive).single().encrypted)
            engine.extract(archive, File(root, "good"), "correct horse".toCharArray())
            assertEquals("confidential", File(root, "good/secret.txt").readText())
            assertThrows(ArchiveFailure.WrongPassword::class.java) {
                kotlinx.coroutines.runBlocking { engine.extract(archive, File(root, "bad"), "wrong".toCharArray()) }
            }
        }
    }

    @Test fun maliciousZipEntryCannotEscapeDestination() = runTest {
        withTemp { root ->
            val archive = File(root, "zip-slip.zip")
            ZipOutputStream(archive.outputStream()).use { zip ->
                zip.putNextEntry(ZipEntry("../../escaped.txt"))
                zip.write("blocked".encodeToByteArray())
                zip.closeEntry()
            }
            val destination = File(root, "safe")
            assertThrows(ArchiveFailure.UnsafeEntry::class.java) {
                kotlinx.coroutines.runBlocking { engine.extract(archive, destination) }
            }
            assertTrue(!File(root.parentFile, "escaped.txt").exists())
        }
    }

    @Test fun corruptArchiveReturnsDomainFailure() = runTest {
        withTemp { root ->
            val corrupt = File(root, "broken.zip").apply { writeBytes(byteArrayOf(0x50, 0x4B, 0x03, 0x04, 1, 2, 3)) }
            assertThrows(ArchiveFailure::class.java) {
                kotlinx.coroutines.runBlocking { engine.test(corrupt) }
            }
        }
    }

    @Test fun singleStreamUsesStreamingRoundTrip() = runTest {
        withTemp { root ->
            val bytes = ByteArray(1024 * 1024) { (it % 251).toByte() }
            val source = File(root, "sample.bin").apply { writeBytes(bytes) }
            val archive = File(root, "sample.bin.gz")
            engine.compress(listOf(LocalSource(source, source.name)), archive, CompressionOptions(ArchiveFormat.GZIP))
            engine.extract(archive, File(root, "out"))
            assertArrayEquals(bytes, File(root, "out/sample.bin").readBytes())
        }
    }

    private suspend fun withTemp(block: suspend (File) -> Unit) {
        val root = createTempDirectory("varchive-engine").toFile()
        try { block(root) } finally { root.deleteRecursively() }
    }
}
