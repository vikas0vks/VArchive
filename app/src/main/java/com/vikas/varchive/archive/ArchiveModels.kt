package com.vikas.varchive.archive

import java.io.File
import java.time.Instant

enum class ArchiveFormat(
    val label: String,
    val primaryExtension: String,
    val mimeType: String,
    val canRead: Boolean,
    val canExtract: Boolean,
    val canCreate: Boolean,
    val canEncrypt: Boolean,
    val canTest: Boolean,
) {
    ZIP("ZIP", "zip", "application/zip", true, true, true, true, true),
    SEVEN_Z("7Z", "7z", "application/x-7z-compressed", true, true, true, false, true),
    RAR("RAR / RAR5", "rar", "application/vnd.rar", true, true, false, false, true),
    TAR("TAR", "tar", "application/x-tar", true, true, true, false, true),
    TAR_GZ("TAR.GZ", "tar.gz", "application/gzip", true, true, true, false, true),
    TAR_BZ2("TAR.BZ2", "tar.bz2", "application/x-bzip2", true, true, true, false, true),
    TAR_XZ("TAR.XZ", "tar.xz", "application/x-xz", true, true, true, false, true),
    TAR_ZST("TAR.ZST", "tar.zst", "application/zstd", true, true, true, false, true),
    GZIP("GZIP", "gz", "application/gzip", true, true, true, false, true),
    BZIP2("BZIP2", "bz2", "application/x-bzip2", true, true, true, false, true),
    XZ("XZ", "xz", "application/x-xz", true, true, true, false, true),
    ZSTD("ZSTD", "zst", "application/zstd", true, true, true, false, true),
    AR("AR", "ar", "application/x-archive", true, true, false, false, true),
    CPIO("CPIO", "cpio", "application/x-cpio", true, true, false, false, true),
    UNKNOWN("Unknown", "bin", "application/octet-stream", false, false, false, false, false);

    val isSingleStream: Boolean get() = this in setOf(GZIP, BZIP2, XZ, ZSTD)
    val isTarFamily: Boolean get() = this in setOf(TAR, TAR_GZ, TAR_BZ2, TAR_XZ, TAR_ZST)

    companion object {
        val creatable = entries.filter { it.canCreate }
    }
}

data class ArchiveCapability(
    val format: ArchiveFormat,
    val canRead: Boolean = format.canRead,
    val canExtract: Boolean = format.canExtract,
    val canCreate: Boolean = format.canCreate,
    val canEncrypt: Boolean = format.canEncrypt,
    val canUpdate: Boolean = false,
    val canTest: Boolean = format.canTest,
)

data class ArchiveEntry(
    val path: String,
    val isDirectory: Boolean,
    val size: Long,
    val compressedSize: Long = -1,
    val modifiedAt: Instant? = null,
    val encrypted: Boolean = false,
) {
    val name: String get() = path.trimEnd('/').substringAfterLast('/')
    val ratioPercent: Int?
        get() = if (size > 0L && compressedSize >= 0L) {
            ((1.0 - compressedSize.toDouble() / size.toDouble()) * 100).toInt()
        } else null
}

data class ArchiveInfo(
    val format: ArchiveFormat,
    val physicalSize: Long,
    val uncompressedSize: Long,
    val fileCount: Int,
    val folderCount: Int,
    val encrypted: Boolean,
    val methods: Set<String> = emptySet(),
)

enum class CompressionLevel(val label: String, val numeric: Int) {
    STORE("Store", 0), FAST("Fast", 3), NORMAL("Normal", 5), MAXIMUM("Maximum", 7), ULTRA("Ultra", 9)
}

data class CompressionOptions(
    val format: ArchiveFormat,
    val level: CompressionLevel = CompressionLevel.NORMAL,
    val password: CharArray? = null,
    val preserveTimestamps: Boolean = true,
    val includeHidden: Boolean = false,
) {
    fun validate(sourceCount: Int): List<String> = buildList {
        if (!format.canCreate) add("${format.label} creation is not supported")
        if (sourceCount <= 0) add("Select at least one source")
        if (format.isSingleStream && sourceCount != 1) add("${format.label} accepts exactly one file")
        if (password != null && password.isNotEmpty() && !format.canEncrypt) add("${format.label} encryption is not supported")
    }
}

enum class ConflictPolicy { ASK, OVERWRITE, SKIP, RENAME }

data class ExtractionPolicy(
    val conflictPolicy: ConflictPolicy = ConflictPolicy.RENAME,
    val preservePaths: Boolean = true,
    val bombProtection: Boolean = true,
)

data class LocalSource(val file: File, val archivePath: String, val modifiedAt: Long = file.lastModified())

data class ArchiveProgress(
    val currentFile: String = "",
    val processedBytes: Long = 0,
    val totalBytes: Long = 0,
    val startedAtMillis: Long = System.currentTimeMillis(),
) {
    val percent: Int get() = if (totalBytes <= 0) 0 else ((processedBytes.coerceAtMost(totalBytes) * 100) / totalBytes).toInt()
    val elapsedMillis: Long get() = (System.currentTimeMillis() - startedAtMillis).coerceAtLeast(1)
    val bytesPerSecond: Long get() = processedBytes * 1_000L / elapsedMillis
}

sealed class ArchiveFailure(message: String, cause: Throwable? = null) : Exception(message, cause) {
    class WrongPassword(cause: Throwable? = null) : ArchiveFailure("Wrong password", cause)
    class Damaged(cause: Throwable? = null) : ArchiveFailure("Archive is damaged or incomplete", cause)
    class Unsupported(message: String = "Archive format or method is not supported", cause: Throwable? = null) : ArchiveFailure(message, cause)
    class UnsafeEntry(val entry: String) : ArchiveFailure("Unsafe archive path blocked: $entry")
    class MissingVolume(val volume: String) : ArchiveFailure("Missing archive volume: $volume")
    class NoSpace(cause: Throwable? = null) : ArchiveFailure("Not enough storage space", cause)
    class Permission(cause: Throwable? = null) : ArchiveFailure("Permission denied", cause)
    class Invalid(message: String = "Invalid archive", cause: Throwable? = null) : ArchiveFailure(message, cause)
}

interface ArchiveEngine {
    val capabilities: List<ArchiveCapability>
    fun detect(file: File, displayName: String = file.name): ArchiveFormat
    suspend fun list(file: File, password: CharArray? = null): List<ArchiveEntry>
    suspend fun info(file: File, password: CharArray? = null): ArchiveInfo
    suspend fun extract(
        archive: File,
        destination: File,
        password: CharArray? = null,
        policy: ExtractionPolicy = ExtractionPolicy(),
        onProgress: suspend (ArchiveProgress) -> Unit = {},
    )
    suspend fun compress(
        sources: List<LocalSource>,
        output: File,
        options: CompressionOptions,
        onProgress: suspend (ArchiveProgress) -> Unit = {},
    )
    suspend fun test(file: File, password: CharArray? = null, onProgress: suspend (ArchiveProgress) -> Unit = {}): ArchiveInfo
}
