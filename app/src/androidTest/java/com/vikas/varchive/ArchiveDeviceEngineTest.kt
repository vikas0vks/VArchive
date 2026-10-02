package com.vikas.varchive

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.vikas.varchive.archive.ArchiveFormat
import com.vikas.varchive.archive.ArchiveFailure
import com.vikas.varchive.archive.CompressionOptions
import com.vikas.varchive.archive.DefaultArchiveEngine
import com.vikas.varchive.archive.LocalSource
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.Base64

@RunWith(AndroidJUnit4::class)
class ArchiveDeviceEngineTest {
    @Test fun codecsRoundTripOnRealAndroidRuntime() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val root = File(context.cacheDir, "device-engine-${System.nanoTime()}").apply { mkdirs() }
        val engine = DefaultArchiveEngine()
        try {
            val formats = listOf(
                ArchiveFormat.ZIP,
                ArchiveFormat.SEVEN_Z,
                ArchiveFormat.TAR,
                ArchiveFormat.TAR_GZ,
                ArchiveFormat.TAR_BZ2,
                ArchiveFormat.TAR_XZ,
                ArchiveFormat.TAR_ZST,
                ArchiveFormat.GZIP,
                ArchiveFormat.BZIP2,
                ArchiveFormat.XZ,
                ArchiveFormat.ZSTD,
            )
            formats.forEach { format ->
                val case = File(root, format.name).apply { mkdirs() }
                val source = File(case, "payload.txt").apply { writeText("Android codec validation: ${format.name}") }
                val archive = File(case, "payload.${format.primaryExtension}")
                engine.compress(listOf(LocalSource(source, source.name)), archive, CompressionOptions(format))
                assertTrue("${format.name} archive should not be empty", archive.length() > 0)
                engine.test(archive)
                val out = File(case, "out")
                engine.extract(archive, out)
                val extracted = out.walkTopDown().first { it.isFile }
                assertEquals(source.readText(), extracted.readText())
            }
        } finally {
            root.deleteRecursively()
        }
    }

    @Test fun encryptedZipWrongPasswordAndCorruptionFailSafelyOnDevice() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val root = File(context.cacheDir, "device-security-${System.nanoTime()}").apply { mkdirs() }
        val engine = DefaultArchiveEngine()
        try {
            val source = File(root, "secret.txt").apply { writeText("device secret") }
            val encrypted = File(root, "encrypted.zip")
            engine.compress(
                listOf(LocalSource(source, source.name)),
                encrypted,
                CompressionOptions(ArchiveFormat.ZIP, password = "correct".toCharArray()),
            )
            assertThrows(ArchiveFailure.WrongPassword::class.java) {
                runBlocking { engine.extract(encrypted, File(root, "wrong"), "incorrect".toCharArray()) }
            }
            engine.extract(encrypted, File(root, "correct"), "correct".toCharArray())
            assertEquals("device secret", File(root, "correct/secret.txt").readText())

            val corrupt = File(root, "corrupt.zip").apply {
                writeBytes(byteArrayOf(0x50, 0x4B, 0x03, 0x04, 1, 2, 3, 4))
            }
            assertThrows(ArchiveFailure::class.java) { runBlocking { engine.test(corrupt) } }
            Unit
        } finally {
            root.deleteRecursively()
        }
    }

    @Test fun rar5BrowseTestAndExtractOnDevice() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val root = File(context.cacheDir, "device-rar5-${System.nanoTime()}").apply { mkdirs() }
        val engine = DefaultArchiveEngine()
        try {
            // 130-byte Junrar upstream test fixture (rar5.rar, Git blob bf6839769eb7d0c6622f5cc0b2504ebb5beffe4e).
            val rar = File(root, "rar5.rar").apply { writeBytes(Base64.getDecoder().decode(RAR5_FIXTURE)) }
            val entries = engine.list(rar)
            assertEquals(2, entries.count { !it.isDirectory })
            engine.test(rar)
            val out = File(root, "out")
            engine.extract(rar, out)
            assertEquals(2, out.walkTopDown().count { it.isFile })
        } finally {
            root.deleteRecursively()
        }
    }

    private companion object {
        const val RAR5_FIXTURE = "UmFyIRoHAQDz4YLrCwEFBwAGAQGAgIAATS800SUCAwuHAASHACC6fRl6gAAACUZJTEUxLlRYVAoDAgDwWYPlessBZmlsZTENCqOo3u8lAgMLhwAEhwAg48NfeIAAAAlGSUxFMi5UWFQKAwIAd+2G5XrLAWZpbGUyDQodd1ZRAwUEAA=="
    }
}
