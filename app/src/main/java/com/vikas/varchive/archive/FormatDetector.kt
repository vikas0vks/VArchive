package com.vikas.varchive.archive

import java.io.File
import java.io.FileInputStream

object FormatDetector {
    private val tarMagic = "ustar".encodeToByteArray()

    fun detect(file: File, displayName: String = file.name): ArchiveFormat {
        val header = ByteArray(600)
        val count = runCatching { FileInputStream(file).use { it.read(header) } }.getOrDefault(0)
        fun starts(vararg bytes: Int): Boolean = count >= bytes.size && bytes.indices.all { header[it].toInt() and 0xFF == bytes[it] }

        return when {
            starts(0x50, 0x4B, 0x03, 0x04) || starts(0x50, 0x4B, 0x05, 0x06) || starts(0x50, 0x4B, 0x07, 0x08) -> ArchiveFormat.ZIP
            starts(0x37, 0x7A, 0xBC, 0xAF, 0x27, 0x1C) -> ArchiveFormat.SEVEN_Z
            starts(0x52, 0x61, 0x72, 0x21, 0x1A, 0x07) -> ArchiveFormat.RAR
            starts(0x1F, 0x8B) -> if (displayName.lowercase().endsWith(".tar.gz") || displayName.lowercase().endsWith(".tgz")) ArchiveFormat.TAR_GZ else ArchiveFormat.GZIP
            starts(0x42, 0x5A, 0x68) -> if (displayName.lowercase().let { it.endsWith(".tar.bz2") || it.endsWith(".tbz2") }) ArchiveFormat.TAR_BZ2 else ArchiveFormat.BZIP2
            starts(0xFD, 0x37, 0x7A, 0x58, 0x5A, 0x00) -> if (displayName.lowercase().let { it.endsWith(".tar.xz") || it.endsWith(".txz") }) ArchiveFormat.TAR_XZ else ArchiveFormat.XZ
            starts(0x28, 0xB5, 0x2F, 0xFD) -> if (displayName.lowercase().endsWith(".tar.zst")) ArchiveFormat.TAR_ZST else ArchiveFormat.ZSTD
            count > 262 && tarMagic.indices.all { header[257 + it] == tarMagic[it] } -> ArchiveFormat.TAR
            starts(0x21, 0x3C, 0x61, 0x72, 0x63, 0x68, 0x3E, 0x0A) -> ArchiveFormat.AR
            starts(0x30, 0x37, 0x30, 0x37, 0x30) -> ArchiveFormat.CPIO
            else -> fromName(displayName)
        }
    }

    fun fromName(name: String): ArchiveFormat {
        val lower = name.lowercase()
        return when {
            lower.endsWith(".tar.gz") || lower.endsWith(".tgz") -> ArchiveFormat.TAR_GZ
            lower.endsWith(".tar.bz2") || lower.endsWith(".tbz2") -> ArchiveFormat.TAR_BZ2
            lower.endsWith(".tar.xz") || lower.endsWith(".txz") -> ArchiveFormat.TAR_XZ
            lower.endsWith(".tar.zst") -> ArchiveFormat.TAR_ZST
            lower.endsWith(".7z.001") || lower.endsWith(".7z") -> ArchiveFormat.SEVEN_Z
            lower.endsWith(".zip.001") || lower.endsWith(".zip") -> ArchiveFormat.ZIP
            lower.endsWith(".rar") || Regex(".*\\.r\\d{2}$").matches(lower) -> ArchiveFormat.RAR
            lower.endsWith(".tar") -> ArchiveFormat.TAR
            lower.endsWith(".gz") -> ArchiveFormat.GZIP
            lower.endsWith(".bz2") -> ArchiveFormat.BZIP2
            lower.endsWith(".xz") -> ArchiveFormat.XZ
            lower.endsWith(".zst") || lower.endsWith(".zstd") -> ArchiveFormat.ZSTD
            lower.endsWith(".ar") || lower.endsWith(".deb") -> ArchiveFormat.AR
            lower.endsWith(".cpio") -> ArchiveFormat.CPIO
            else -> ArchiveFormat.UNKNOWN
        }
    }
}
