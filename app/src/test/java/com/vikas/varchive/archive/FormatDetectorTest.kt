package com.vikas.varchive.archive

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File
import kotlin.io.path.createTempDirectory

class FormatDetectorTest {
    @Test fun detectsMagicBeforeMisleadingExtension() {
        val root = createTempDirectory("varchive-detect").toFile()
        try {
            val file = File(root, "not-a-zip.txt").apply { writeBytes(byteArrayOf(0x50, 0x4B, 0x03, 0x04)) }
            assertEquals(ArchiveFormat.ZIP, FormatDetector.detect(file))
        } finally { root.deleteRecursively() }
    }

    @Test fun detectsCompressedTarAliases() {
        assertEquals(ArchiveFormat.TAR_GZ, FormatDetector.fromName("backup.tgz"))
        assertEquals(ArchiveFormat.TAR_BZ2, FormatDetector.fromName("backup.tbz2"))
        assertEquals(ArchiveFormat.TAR_XZ, FormatDetector.fromName("backup.txz"))
        assertEquals(ArchiveFormat.TAR_ZST, FormatDetector.fromName("backup.tar.zst"))
    }

    @Test fun detectsRarAndMultipartNames() {
        assertEquals(ArchiveFormat.RAR, FormatDetector.fromName("volume.rar"))
        assertEquals(ArchiveFormat.RAR, FormatDetector.fromName("volume.r00"))
        assertEquals(ArchiveFormat.SEVEN_Z, FormatDetector.fromName("data.7z.001"))
    }
}
