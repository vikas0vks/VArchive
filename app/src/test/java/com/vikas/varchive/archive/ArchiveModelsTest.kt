package com.vikas.varchive.archive

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ArchiveModelsTest {
    @Test fun capabilityMatrixDoesNotClaimRarCreation() {
        val rar = DefaultArchiveEngine().capabilities.single { it.format == ArchiveFormat.RAR }
        assertTrue(rar.canRead)
        assertTrue(rar.canExtract)
        assertFalse(rar.canCreate)
        assertFalse(rar.canEncrypt)
    }

    @Test fun singleStreamFormatsRequireOneSource() {
        assertTrue(CompressionOptions(ArchiveFormat.GZIP).validate(2).isNotEmpty())
        assertTrue(CompressionOptions(ArchiveFormat.GZIP).validate(1).isEmpty())
    }

    @Test fun unsupportedEncryptionIsRejected() {
        val options = CompressionOptions(ArchiveFormat.SEVEN_Z, password = "secret".toCharArray())
        assertTrue(options.validate(1).any { it.contains("encryption") })
    }

    @Test fun progressUsesLongAndNeverExceedsHundred() {
        assertEquals(50, ArchiveProgress(processedBytes = 5L shl 32, totalBytes = 10L shl 32).percent)
        assertEquals(100, ArchiveProgress(processedBytes = 20, totalBytes = 10).percent)
        assertEquals(0, ArchiveProgress(processedBytes = 1, totalBytes = 0).percent)
    }

    @Test fun fileSizeFormattingHasExpectedBoundaries() {
        assertEquals("0 B", formatBytes(0))
        assertEquals("1.0 KB", formatBytes(1024))
        assertEquals("4.0 GB", formatBytes(4L * 1024 * 1024 * 1024))
    }
}
