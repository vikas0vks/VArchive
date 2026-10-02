package com.vikas.varchive.archive

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import kotlin.io.path.createTempDirectory

class ArchiveSecurityTest {
    @Test fun safePathsAreNormalized() {
        assertEquals("folder/file.txt", ArchiveSecurity.safeRelativePath("folder//./file.txt"))
        assertEquals("folder/file.txt", ArchiveSecurity.safeRelativePath("folder\\file.txt"))
    }

    @Test fun traversalAndAbsolutePathsAreBlocked() {
        listOf("../secret", "a/../../secret", "/etc/passwd", "C:\\Windows\\file", "ok/\u0000bad").forEach { path ->
            assertThrows(path, ArchiveFailure.UnsafeEntry::class.java) { ArchiveSecurity.safeRelativePath(path) }
        }
    }

    @Test fun resolvedPathsStayInsideRoot() {
        val root = createTempDirectory("varchive-security").toFile()
        try {
            assertTrue(ArchiveSecurity.resolveInside(root, "a/b.txt").canonicalPath.startsWith(root.canonicalPath))
            assertThrows(ArchiveFailure.UnsafeEntry::class.java) { ArchiveSecurity.resolveInside(root, "../../escape") }
        } finally { root.deleteRecursively() }
    }

    @Test fun filenamesAreSanitizedAndBounded() {
        assertEquals("bad_name_.zip", ArchiveSecurity.sanitizeFilename("bad:name?.zip"))
        assertEquals("archive", ArchiveSecurity.sanitizeFilename("..."))
        assertTrue(ArchiveSecurity.sanitizeFilename("x".repeat(500)).length <= 180)
    }

    @Test fun conflictPoliciesBehaveDeterministically() {
        val root = createTempDirectory("varchive-conflict").toFile()
        try {
            val existing = File(root, "report.txt").apply { writeText("old") }
            assertEquals(existing, uniqueDestination(existing, ConflictPolicy.OVERWRITE))
            assertNull(uniqueDestination(existing, ConflictPolicy.SKIP))
            val renamed = uniqueDestination(existing, ConflictPolicy.RENAME)!!
            assertEquals("report (1).txt", renamed.name)
            assertNotEquals(existing, renamed)
            assertFalse(renamed.exists())
        } finally { root.deleteRecursively() }
    }

    @Test fun archiveBombThresholdsUseLongArithmetic() {
        val huge = ArchiveEntry("huge.bin", false, 51L * 1024 * 1024 * 1024, 1)
        assertTrue(ArchiveSecurity.validateBombRisk(listOf(huge))!!.blocking)
        val normal = ArchiveEntry("normal.bin", false, 1_000, 500)
        assertNull(ArchiveSecurity.validateBombRisk(listOf(normal)))
    }
}
