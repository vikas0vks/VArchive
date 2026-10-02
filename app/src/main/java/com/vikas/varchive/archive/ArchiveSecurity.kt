package com.vikas.varchive.archive

import java.io.File

object ArchiveSecurity {
    private val windowsDrive = Regex("^[A-Za-z]:.*")
    private val controlChars = Regex("[\\u0000-\\u001F\\u007F]")

    fun safeRelativePath(raw: String): String {
        val normalized = raw.replace('\\', '/').trimStart()
        if (normalized.isBlank() || normalized.startsWith('/') || windowsDrive.matches(normalized)) {
            throw ArchiveFailure.UnsafeEntry(raw)
        }
        val parts = normalized.split('/').filter { it.isNotEmpty() && it != "." }
        if (parts.isEmpty() || parts.any { it == ".." || controlChars.containsMatchIn(it) }) {
            throw ArchiveFailure.UnsafeEntry(raw)
        }
        return parts.joinToString("/")
    }

    fun resolveInside(root: File, entry: String): File {
        val target = File(root, safeRelativePath(entry))
        val rootPath = root.canonicalFile.toPath()
        val targetPath = target.canonicalFile.toPath()
        if (!targetPath.startsWith(rootPath)) throw ArchiveFailure.UnsafeEntry(entry)
        return target
    }

    fun sanitizeFilename(input: String, fallback: String = "archive"): String {
        val cleaned = input
            .replace(controlChars, "")
            .replace(Regex("[\\\\/:*?\"<>|]"), "_")
            .trim().trim('.')
            .take(180)
        return cleaned.ifBlank { fallback }
    }

    fun validateBombRisk(entries: List<ArchiveEntry>, policy: BombPolicy = BombPolicy()): BombRisk? {
        val fileCount = entries.count { !it.isDirectory }
        val total = entries.asSequence().filterNot { it.isDirectory }.sumOf { it.size.coerceAtLeast(0) }
        val compressed = entries.asSequence().filterNot { it.isDirectory }.sumOf { it.compressedSize.coerceAtLeast(0) }
        return when {
            fileCount > policy.maxFileCount -> BombRisk("Archive contains $fileCount files", true)
            total > policy.maxUncompressedBytes -> BombRisk("Archive expands to ${formatBytes(total)}", true)
            compressed > 0 && total / compressed.coerceAtLeast(1) > policy.maxCompressionRatio ->
                BombRisk("Compression ratio exceeds ${policy.maxCompressionRatio}:1", false)
            else -> null
        }
    }
}

data class BombPolicy(
    val maxFileCount: Int = 100_000,
    val maxUncompressedBytes: Long = 50L * 1024 * 1024 * 1024,
    val maxCompressionRatio: Long = 1_000,
)

data class BombRisk(val reason: String, val blocking: Boolean)

fun formatBytes(value: Long): String {
    if (value < 1024) return "$value B"
    val units = arrayOf("KB", "MB", "GB", "TB")
    var size = value.toDouble()
    var index = -1
    do {
        size /= 1024.0
        index++
    } while (size >= 1024 && index < units.lastIndex)
    return if (size >= 100) "%.0f %s".format(size, units[index]) else "%.1f %s".format(size, units[index])
}

fun uniqueDestination(file: File, policy: ConflictPolicy): File? {
    if (!file.exists()) return file
    return when (policy) {
        ConflictPolicy.OVERWRITE -> file
        ConflictPolicy.SKIP -> null
        ConflictPolicy.ASK -> throw ArchiveFailure.Invalid("A file already exists: ${file.name}")
        ConflictPolicy.RENAME -> {
            val base = file.nameWithoutExtension
            val ext = file.extension.takeIf { it.isNotBlank() }?.let { ".$it" }.orEmpty()
            generateSequence(1) { it + 1 }
                .map { File(file.parentFile, "$base ($it)$ext") }
                .first { !it.exists() }
        }
    }
}
