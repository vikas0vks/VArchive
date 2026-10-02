package com.vikas.varchive.archive

import com.github.junrar.Archive
import com.github.junrar.ArchiveOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import net.lingala.zip4j.ZipFile
import net.lingala.zip4j.exception.ZipException
import net.lingala.zip4j.model.ZipParameters
import net.lingala.zip4j.model.enums.AesKeyStrength
import net.lingala.zip4j.model.enums.CompressionLevel as ZipCompressionLevel
import net.lingala.zip4j.model.enums.CompressionMethod
import net.lingala.zip4j.model.enums.EncryptionMethod
import org.apache.commons.compress.archivers.ArchiveEntry as CommonsEntry
import org.apache.commons.compress.archivers.ArchiveInputStream
import org.apache.commons.compress.archivers.ar.ArArchiveInputStream
import org.apache.commons.compress.archivers.cpio.CpioArchiveInputStream
import org.apache.commons.compress.archivers.sevenz.SevenZFile
import org.apache.commons.compress.archivers.sevenz.SevenZOutputFile
import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream
import org.apache.commons.compress.archivers.zip.ZipArchiveInputStream
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorOutputStream
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream
import org.apache.commons.compress.compressors.gzip.GzipCompressorOutputStream
import org.apache.commons.compress.compressors.xz.XZCompressorInputStream
import org.apache.commons.compress.compressors.xz.XZCompressorOutputStream
import org.apache.commons.compress.compressors.zstandard.ZstdCompressorInputStream
import org.apache.commons.compress.compressors.zstandard.ZstdCompressorOutputStream
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.time.Instant
import java.util.Date

class DefaultArchiveEngine : ArchiveEngine {
    override val capabilities: List<ArchiveCapability> = ArchiveFormat.entries
        .filterNot { it == ArchiveFormat.UNKNOWN }
        .map { format ->
            ArchiveCapability(format)
        }

    override fun detect(file: File, displayName: String): ArchiveFormat = FormatDetector.detect(file, displayName)

    override suspend fun list(file: File, password: CharArray?): List<ArchiveEntry> = withContext(Dispatchers.IO) {
        runMapped {
            when (val format = detect(file)) {
                ArchiveFormat.ZIP -> listZip(file, password)
                ArchiveFormat.SEVEN_Z -> listSevenZ(file, password)
                ArchiveFormat.RAR -> listRar(file, password)
                in tarAndSimpleArchives -> listStreamArchive(file, format)
                in singleStreams -> listOf(
                    ArchiveEntry(singleOutputName(file.name, format), false, -1, file.length(), file.lastModified().toInstant())
                )
                else -> throw ArchiveFailure.Unsupported()
            }
        }
    }

    override suspend fun info(file: File, password: CharArray?): ArchiveInfo {
        val format = detect(file)
        val entries = list(file, password)
        return ArchiveInfo(
            format = format,
            physicalSize = file.length(),
            uncompressedSize = entries.filterNot { it.isDirectory }.sumOf { it.size.coerceAtLeast(0) },
            fileCount = entries.count { !it.isDirectory },
            folderCount = entries.count { it.isDirectory },
            encrypted = entries.any { it.encrypted },
            methods = when (format) {
                ArchiveFormat.ZIP -> setOf("Deflate / Store", "AES when encrypted")
                ArchiveFormat.SEVEN_Z -> setOf("7z / LZMA family")
                ArchiveFormat.TAR_GZ, ArchiveFormat.GZIP -> setOf("GZIP / Deflate")
                ArchiveFormat.TAR_BZ2, ArchiveFormat.BZIP2 -> setOf("BZip2")
                ArchiveFormat.TAR_XZ, ArchiveFormat.XZ -> setOf("XZ / LZMA2")
                ArchiveFormat.TAR_ZST, ArchiveFormat.ZSTD -> setOf("Zstandard")
                ArchiveFormat.TAR -> setOf("Store")
                else -> emptySet()
            },
        )
    }

    override suspend fun extract(
        archive: File,
        destination: File,
        password: CharArray?,
        policy: ExtractionPolicy,
        onProgress: suspend (ArchiveProgress) -> Unit,
    ) = withContext(Dispatchers.IO) {
        runMapped {
            destination.mkdirs()
            val entries = list(archive, password)
            if (policy.bombProtection) {
                ArchiveSecurity.validateBombRisk(entries)?.let { risk ->
                    if (risk.blocking) throw ArchiveFailure.Invalid("Suspicious archive blocked: ${risk.reason}")
                }
            }
            entries.forEach { ArchiveSecurity.safeRelativePath(it.path) }
            val total = entries.filterNot { it.isDirectory }.sumOf { it.size.coerceAtLeast(0) }
            val started = System.currentTimeMillis()
            when (val format = detect(archive)) {
                ArchiveFormat.ZIP -> extractZip(archive, destination, password, policy, total, started, onProgress)
                ArchiveFormat.SEVEN_Z -> extractSevenZ(archive, destination, password, policy, total, started, onProgress)
                ArchiveFormat.RAR -> extractRar(archive, destination, password, policy, total, started, onProgress)
                in tarAndSimpleArchives -> extractStreamArchive(archive, format, destination, policy, total, started, onProgress)
                in singleStreams -> extractSingleStream(archive, format, destination, policy, started, onProgress)
                else -> throw ArchiveFailure.Unsupported()
            }
            verifyExtractedTree(destination)
        }
    }

    override suspend fun compress(
        sources: List<LocalSource>,
        output: File,
        options: CompressionOptions,
        onProgress: suspend (ArchiveProgress) -> Unit,
    ) = withContext(Dispatchers.IO) {
        runMapped {
            options.validate(sources.size).firstOrNull()?.let { throw ArchiveFailure.Invalid(it) }
            output.parentFile?.mkdirs()
            val total = sources.sumOf { it.file.length() }
            val started = System.currentTimeMillis()
            when (options.format) {
                ArchiveFormat.ZIP -> compressZip(sources, output, options, total, started, onProgress)
                ArchiveFormat.SEVEN_Z -> compressSevenZ(sources, output, total, started, onProgress)
                in tarFormats -> compressTar(sources, output, options.format, total, started, onProgress)
                in singleStreams -> compressSingle(sources.single(), output, options.format, started, onProgress)
                else -> throw ArchiveFailure.Unsupported("${options.format.label} creation is not supported")
            }
        }
    }

    override suspend fun test(
        file: File,
        password: CharArray?,
        onProgress: suspend (ArchiveProgress) -> Unit,
    ): ArchiveInfo = withContext(Dispatchers.IO) {
        runMapped {
            val entries = list(file, password)
            val total = entries.filterNot { it.isDirectory }.sumOf { it.size.coerceAtLeast(0) }
            val started = System.currentTimeMillis()
            when (val format = detect(file)) {
                ArchiveFormat.ZIP -> testZip(file, password, total, started, onProgress)
                ArchiveFormat.SEVEN_Z -> testSevenZ(file, password, total, started, onProgress)
                ArchiveFormat.RAR -> testRar(file, password, total, started, onProgress)
                in tarAndSimpleArchives -> testStream(file, format, total, started, onProgress)
                in singleStreams -> openSingleInput(file, format).use { input ->
                    drain(input, singleOutputName(file.name, format), total, started, onProgress)
                }
                else -> throw ArchiveFailure.Unsupported()
            }
            info(file, password)
        }
    }

    private fun listZip(file: File, password: CharArray?): List<ArchiveEntry> {
        val zip = if (password == null) ZipFile(file) else ZipFile(file, password)
        return zip.fileHeaders.map { header ->
            ArchiveEntry(
                path = ArchiveSecurity.safeRelativePath(header.fileName),
                isDirectory = header.isDirectory,
                size = header.uncompressedSize,
                compressedSize = header.compressedSize,
                modifiedAt = header.lastModifiedTimeEpoch.takeIf { it > 0 }?.let(Instant::ofEpochMilli),
                encrypted = header.isEncrypted,
            )
        }
    }

    private fun listSevenZ(file: File, password: CharArray?): List<ArchiveEntry> {
        val builder = SevenZFile.builder().setFile(file)
        if (password != null) builder.setPassword(password)
        return builder.get().use { seven ->
            buildList {
                while (true) {
                    val entry = seven.nextEntry ?: break
                    add(
                        ArchiveEntry(
                            path = ArchiveSecurity.safeRelativePath(entry.name),
                            isDirectory = entry.isDirectory,
                            size = entry.size,
                            compressedSize = -1,
                            modifiedAt = entry.lastModifiedDate?.toInstant(),
                            encrypted = entry.isAntiItem,
                        )
                    )
                }
            }
        }
    }

    private fun listRar(file: File, password: CharArray?): List<ArchiveEntry> = openRar(file, password).use { rar ->
        rar.fileHeaders.map { header ->
            if (header.redirection != null) throw ArchiveFailure.UnsafeEntry(header.fileName)
            ArchiveEntry(
                path = ArchiveSecurity.safeRelativePath(header.fileName),
                isDirectory = header.isDirectory,
                size = header.fullUnpackSize,
                compressedSize = header.fullPackSize,
                modifiedAt = header.lastModifiedTime?.toInstant(),
                encrypted = header.isEncrypted,
            )
        }
    }

    private fun listStreamArchive(file: File, format: ArchiveFormat): List<ArchiveEntry> =
        openArchiveInput(file, format).use { input ->
            buildList {
                while (true) {
                    val entry = input.nextEntry ?: break
                    add(entry.toDomain())
                }
            }
        }

    private suspend fun extractZip(
        file: File,
        destination: File,
        password: CharArray?,
        policy: ExtractionPolicy,
        total: Long,
        started: Long,
        progress: suspend (ArchiveProgress) -> Unit,
    ) {
        val zip = if (password == null) ZipFile(file) else ZipFile(file, password)
        var processed = 0L
        zip.fileHeaders.forEach { header ->
            currentCoroutineContext().ensureActive()
            val relative = ArchiveSecurity.safeRelativePath(header.fileName)
            val rawTarget = ArchiveSecurity.resolveInside(destination, relative)
            if (header.isDirectory) {
                rawTarget.mkdirs()
            } else {
                val target = uniqueDestination(rawTarget, policy.conflictPolicy) ?: return@forEach
                target.parentFile?.mkdirs()
                zip.getInputStream(header).use { input ->
                    FileOutputStream(target).buffered().use { output ->
                        processed = copy(input, output, header.fileName, processed, total, started, progress)
                    }
                }
                target.setLastModified(header.lastModifiedTimeEpoch)
            }
        }
    }

    private suspend fun extractSevenZ(
        file: File,
        destination: File,
        password: CharArray?,
        policy: ExtractionPolicy,
        total: Long,
        started: Long,
        progress: suspend (ArchiveProgress) -> Unit,
    ) {
        val builder = SevenZFile.builder().setFile(file)
        if (password != null) builder.setPassword(password)
        var processed = 0L
        builder.get().use { seven ->
            while (true) {
                currentCoroutineContext().ensureActive()
                val entry = seven.nextEntry ?: break
                val target = ArchiveSecurity.resolveInside(destination, entry.name)
                if (entry.isDirectory) target.mkdirs() else {
                    val actual = uniqueDestination(target, policy.conflictPolicy) ?: continue
                    actual.parentFile?.mkdirs()
                    FileOutputStream(actual).buffered().use { output ->
                        processed = copyFromSevenZ(seven, output, entry.name, processed, total, started, progress)
                    }
                    entry.lastModifiedDate?.time?.let(actual::setLastModified)
                }
            }
        }
    }

    private suspend fun extractRar(
        file: File,
        destination: File,
        password: CharArray?,
        policy: ExtractionPolicy,
        total: Long,
        started: Long,
        progress: suspend (ArchiveProgress) -> Unit,
    ) {
        var processed = 0L
        openRar(file, password).use { rar ->
            rar.fileHeaders.forEach { header ->
                currentCoroutineContext().ensureActive()
                if (header.redirection != null) throw ArchiveFailure.UnsafeEntry(header.fileName)
                val target = ArchiveSecurity.resolveInside(destination, header.fileName)
                if (header.isDirectory) target.mkdirs() else {
                    val actual = uniqueDestination(target, policy.conflictPolicy) ?: return@forEach
                    actual.parentFile?.mkdirs()
                    rar.getInputStream(header).use { input ->
                        FileOutputStream(actual).buffered().use { output ->
                            processed = copy(input, output, header.fileName, processed, total, started, progress)
                        }
                    }
                    header.lastModifiedTime?.toMillis()?.let(actual::setLastModified)
                }
            }
        }
    }

    private suspend fun extractStreamArchive(
        file: File,
        format: ArchiveFormat,
        destination: File,
        policy: ExtractionPolicy,
        total: Long,
        started: Long,
        progress: suspend (ArchiveProgress) -> Unit,
    ) {
        var processed = 0L
        openArchiveInput(file, format).use { input ->
            while (true) {
                currentCoroutineContext().ensureActive()
                val entry = input.nextEntry ?: break
                if (entry is TarArchiveEntry && (entry.isSymbolicLink || entry.isLink)) {
                    throw ArchiveFailure.UnsafeEntry(entry.name)
                }
                val target = ArchiveSecurity.resolveInside(destination, entry.name)
                if (entry.isDirectory) target.mkdirs() else {
                    val actual = uniqueDestination(target, policy.conflictPolicy) ?: continue
                    actual.parentFile?.mkdirs()
                    FileOutputStream(actual).buffered().use { output ->
                        processed = copy(input, output, entry.name, processed, total, started, progress)
                    }
                    entry.lastModifiedDate?.time?.let(actual::setLastModified)
                }
            }
        }
    }

    private suspend fun extractSingleStream(
        file: File,
        format: ArchiveFormat,
        destination: File,
        policy: ExtractionPolicy,
        started: Long,
        progress: suspend (ArchiveProgress) -> Unit,
    ) {
        val name = singleOutputName(file.name, format)
        val target = uniqueDestination(ArchiveSecurity.resolveInside(destination, name), policy.conflictPolicy) ?: return
        openSingleInput(file, format).use { input ->
            FileOutputStream(target).buffered().use { output -> copy(input, output, name, 0, file.length(), started, progress) }
        }
    }

    private suspend fun compressZip(
        sources: List<LocalSource>,
        output: File,
        options: CompressionOptions,
        total: Long,
        started: Long,
        progress: suspend (ArchiveProgress) -> Unit,
    ) {
        val zip = if (options.password == null) ZipFile(output) else ZipFile(output, options.password)
        var processed = 0L
        sources.forEach { source ->
            currentCoroutineContext().ensureActive()
            val parameters = ZipParameters().apply {
                fileNameInZip = ArchiveSecurity.safeRelativePath(source.archivePath)
                compressionMethod = if (options.level == CompressionLevel.STORE) CompressionMethod.STORE else CompressionMethod.DEFLATE
                compressionLevel = options.level.toZipLevel()
                if (options.password != null && options.password.isNotEmpty()) {
                    isEncryptFiles = true
                    encryptionMethod = EncryptionMethod.AES
                    aesKeyStrength = AesKeyStrength.KEY_STRENGTH_256
                }
            }
            zip.addFile(source.file, parameters)
            processed += source.file.length()
            progress(ArchiveProgress(source.archivePath, processed, total, started))
        }
    }

    private suspend fun compressSevenZ(
        sources: List<LocalSource>,
        output: File,
        total: Long,
        started: Long,
        progress: suspend (ArchiveProgress) -> Unit,
    ) {
        var processed = 0L
        SevenZOutputFile(output).use { seven ->
            sources.forEach { source ->
                currentCoroutineContext().ensureActive()
                val entry = seven.createArchiveEntry(source.file, ArchiveSecurity.safeRelativePath(source.archivePath))
                seven.putArchiveEntry(entry)
                FileInputStream(source.file).buffered().use { input ->
                    processed = copyToSevenZ(input, seven, source.archivePath, processed, total, started, progress)
                }
                seven.closeArchiveEntry()
            }
            seven.finish()
        }
    }

    private suspend fun compressTar(
        sources: List<LocalSource>,
        output: File,
        format: ArchiveFormat,
        total: Long,
        started: Long,
        progress: suspend (ArchiveProgress) -> Unit,
    ) {
        var processed = 0L
        openTarOutput(output, format).use { tar ->
            sources.forEach { source ->
                currentCoroutineContext().ensureActive()
                val name = ArchiveSecurity.safeRelativePath(source.archivePath)
                val entry = TarArchiveEntry(name).apply {
                    size = source.file.length()
                    modTime = Date(source.modifiedAt)
                }
                tar.putArchiveEntry(entry)
                FileInputStream(source.file).buffered().use { input ->
                    processed = copy(input, tar, name, processed, total, started, progress)
                }
                tar.closeArchiveEntry()
            }
            tar.finish()
        }
    }

    private suspend fun compressSingle(
        source: LocalSource,
        output: File,
        format: ArchiveFormat,
        started: Long,
        progress: suspend (ArchiveProgress) -> Unit,
    ) {
        openSingleOutput(output, format).use { compressed ->
            FileInputStream(source.file).buffered().use { input ->
                copy(input, compressed, source.archivePath, 0, source.file.length(), started, progress)
            }
        }
    }

    private suspend fun testZip(file: File, password: CharArray?, total: Long, started: Long, progress: suspend (ArchiveProgress) -> Unit) {
        val zip = if (password == null) ZipFile(file) else ZipFile(file, password)
        var processed = 0L
        zip.fileHeaders.filterNot { it.isDirectory }.forEach { header ->
            zip.getInputStream(header).use { input -> processed = drain(input, header.fileName, total, started, progress, processed) }
        }
    }

    private suspend fun testSevenZ(file: File, password: CharArray?, total: Long, started: Long, progress: suspend (ArchiveProgress) -> Unit) {
        val builder = SevenZFile.builder().setFile(file)
        if (password != null) builder.setPassword(password)
        var processed = 0L
        builder.get().use { seven ->
            while (true) {
                val entry = seven.nextEntry ?: break
                if (!entry.isDirectory) processed = drainSevenZ(seven, entry.name, total, started, progress, processed)
            }
        }
    }

    private suspend fun testRar(file: File, password: CharArray?, total: Long, started: Long, progress: suspend (ArchiveProgress) -> Unit) {
        var processed = 0L
        openRar(file, password).use { rar ->
            rar.fileHeaders.filterNot { it.isDirectory }.forEach { header ->
                if (header.redirection != null) throw ArchiveFailure.UnsafeEntry(header.fileName)
                rar.getInputStream(header).use { input ->
                    processed = drain(input, header.fileName, total, started, progress, processed)
                }
            }
            if (rar.hasBrokenHeaders()) throw ArchiveFailure.Damaged()
        }
    }

    private fun openRar(file: File, password: CharArray?): Archive {
        val builder = ArchiveOptions.builder().maxDictionarySize(256L * 1024 * 1024)
        if (password != null) builder.password(password)
        return Archive(file, builder.build())
    }

    private suspend fun testStream(file: File, format: ArchiveFormat, total: Long, started: Long, progress: suspend (ArchiveProgress) -> Unit) {
        var processed = 0L
        openArchiveInput(file, format).use { input ->
            while (true) {
                val entry = input.nextEntry ?: break
                if (!entry.isDirectory) processed = drain(input, entry.name, total, started, progress, processed)
            }
        }
    }

    private fun openArchiveInput(file: File, format: ArchiveFormat): ArchiveInputStream<out CommonsEntry> {
        val raw = BufferedInputStream(FileInputStream(file), BUFFER_SIZE)
        val payload: InputStream = when (format) {
            ArchiveFormat.TAR_GZ -> GzipCompressorInputStream(raw)
            ArchiveFormat.TAR_BZ2 -> BZip2CompressorInputStream(raw)
            ArchiveFormat.TAR_XZ -> XZCompressorInputStream(raw)
            ArchiveFormat.TAR_ZST -> ZstdCompressorInputStream(raw)
            else -> raw
        }
        return when (format) {
            in tarFormats -> TarArchiveInputStream(payload)
            ArchiveFormat.AR -> ArArchiveInputStream(payload)
            ArchiveFormat.CPIO -> CpioArchiveInputStream(payload)
            ArchiveFormat.ZIP -> ZipArchiveInputStream(payload)
            else -> throw ArchiveFailure.Unsupported()
        }
    }

    private fun openTarOutput(file: File, format: ArchiveFormat): TarArchiveOutputStream {
        val raw = BufferedOutputStream(FileOutputStream(file), BUFFER_SIZE)
        val payload: OutputStream = when (format) {
            ArchiveFormat.TAR_GZ -> GzipCompressorOutputStream(raw)
            ArchiveFormat.TAR_BZ2 -> BZip2CompressorOutputStream(raw)
            ArchiveFormat.TAR_XZ -> XZCompressorOutputStream(raw)
            ArchiveFormat.TAR_ZST -> ZstdCompressorOutputStream(raw)
            else -> raw
        }
        return TarArchiveOutputStream(payload).apply {
            setLongFileMode(TarArchiveOutputStream.LONGFILE_POSIX)
            setBigNumberMode(TarArchiveOutputStream.BIGNUMBER_POSIX)
        }
    }

    private fun openSingleInput(file: File, format: ArchiveFormat): InputStream {
        val raw = BufferedInputStream(FileInputStream(file), BUFFER_SIZE)
        return when (format) {
            ArchiveFormat.GZIP -> GzipCompressorInputStream(raw)
            ArchiveFormat.BZIP2 -> BZip2CompressorInputStream(raw)
            ArchiveFormat.XZ -> XZCompressorInputStream(raw)
            ArchiveFormat.ZSTD -> ZstdCompressorInputStream(raw)
            else -> throw ArchiveFailure.Unsupported()
        }
    }

    private fun openSingleOutput(file: File, format: ArchiveFormat): OutputStream {
        val raw = BufferedOutputStream(FileOutputStream(file), BUFFER_SIZE)
        return when (format) {
            ArchiveFormat.GZIP -> GzipCompressorOutputStream(raw)
            ArchiveFormat.BZIP2 -> BZip2CompressorOutputStream(raw)
            ArchiveFormat.XZ -> XZCompressorOutputStream(raw)
            ArchiveFormat.ZSTD -> ZstdCompressorOutputStream(raw)
            else -> throw ArchiveFailure.Unsupported()
        }
    }

    private suspend fun copy(
        input: InputStream,
        output: OutputStream,
        name: String,
        initial: Long,
        total: Long,
        started: Long,
        progress: suspend (ArchiveProgress) -> Unit,
    ): Long {
        var processed = initial
        var lastUpdate = 0L
        val buffer = ByteArray(BUFFER_SIZE)
        while (true) {
            currentCoroutineContext().ensureActive()
            val read = input.read(buffer)
            if (read < 0) break
            output.write(buffer, 0, read)
            processed += read
            val now = System.currentTimeMillis()
            if (now - lastUpdate >= 200) {
                progress(ArchiveProgress(name, processed, total, started))
                lastUpdate = now
            }
        }
        progress(ArchiveProgress(name, processed, total, started))
        return processed
    }

    private suspend fun copyFromSevenZ(
        input: SevenZFile,
        output: OutputStream,
        name: String,
        initial: Long,
        total: Long,
        started: Long,
        progress: suspend (ArchiveProgress) -> Unit,
    ): Long {
        var processed = initial
        var lastUpdate = 0L
        val buffer = ByteArray(BUFFER_SIZE)
        while (true) {
            currentCoroutineContext().ensureActive()
            val read = input.read(buffer)
            if (read < 0) break
            output.write(buffer, 0, read)
            processed += read
            val now = System.currentTimeMillis()
            if (now - lastUpdate >= 200) {
                progress(ArchiveProgress(name, processed, total, started))
                lastUpdate = now
            }
        }
        progress(ArchiveProgress(name, processed, total, started))
        return processed
    }

    private suspend fun copyToSevenZ(
        input: InputStream,
        output: SevenZOutputFile,
        name: String,
        initial: Long,
        total: Long,
        started: Long,
        progress: suspend (ArchiveProgress) -> Unit,
    ): Long {
        var processed = initial
        var lastUpdate = 0L
        val buffer = ByteArray(BUFFER_SIZE)
        while (true) {
            currentCoroutineContext().ensureActive()
            val read = input.read(buffer)
            if (read < 0) break
            output.write(buffer, 0, read)
            processed += read
            val now = System.currentTimeMillis()
            if (now - lastUpdate >= 200) {
                progress(ArchiveProgress(name, processed, total, started))
                lastUpdate = now
            }
        }
        progress(ArchiveProgress(name, processed, total, started))
        return processed
    }

    private suspend fun drainSevenZ(
        input: SevenZFile,
        name: String,
        total: Long,
        started: Long,
        progress: suspend (ArchiveProgress) -> Unit,
        initial: Long,
    ): Long {
        var processed = initial
        var lastUpdate = 0L
        val buffer = ByteArray(BUFFER_SIZE)
        while (true) {
            currentCoroutineContext().ensureActive()
            val read = input.read(buffer)
            if (read < 0) break
            processed += read
            val now = System.currentTimeMillis()
            if (now - lastUpdate >= 200) {
                progress(ArchiveProgress(name, processed, total, started))
                lastUpdate = now
            }
        }
        progress(ArchiveProgress(name, processed, total, started))
        return processed
    }

    private suspend fun drain(
        input: InputStream,
        name: String,
        total: Long,
        started: Long,
        progress: suspend (ArchiveProgress) -> Unit,
        initial: Long = 0,
    ): Long {
        var processed = initial
        var lastUpdate = 0L
        val buffer = ByteArray(BUFFER_SIZE)
        while (true) {
            currentCoroutineContext().ensureActive()
            val read = input.read(buffer)
            if (read < 0) break
            processed += read
            val now = System.currentTimeMillis()
            if (now - lastUpdate >= 200) {
                progress(ArchiveProgress(name, processed, total, started))
                lastUpdate = now
            }
        }
        progress(ArchiveProgress(name, processed, total, started))
        return processed
    }

    private fun CommonsEntry.toDomain(): ArchiveEntry = ArchiveEntry(
        path = ArchiveSecurity.safeRelativePath(name),
        isDirectory = isDirectory,
        size = size,
        modifiedAt = lastModifiedDate?.toInstant(),
    )

    private fun verifyExtractedTree(root: File) {
        val rootPath = root.canonicalFile.toPath()
        root.walkTopDown().forEach { file ->
            if (!file.canonicalFile.toPath().startsWith(rootPath)) throw ArchiveFailure.UnsafeEntry(file.path)
        }
    }

    private inline fun <T> runMapped(block: () -> T): T = try {
        block()
    } catch (known: ArchiveFailure) {
        throw known
    } catch (error: Throwable) {
        val text = error.message.orEmpty().lowercase()
        when {
            error is ZipException && (text.contains("password") || text.contains("mac")) -> throw ArchiveFailure.WrongPassword(error)
            text.contains("password") || text.contains("bad padding") -> throw ArchiveFailure.WrongPassword(error)
            text.contains("space") || text.contains("enospc") -> throw ArchiveFailure.NoSpace(error)
            text.contains("permission") || text.contains("access denied") -> throw ArchiveFailure.Permission(error)
            text.contains("crc") || text.contains("checksum") || text.contains("unexpected end") || text.contains("eof") -> throw ArchiveFailure.Damaged(error)
            else -> throw ArchiveFailure.Invalid(error.message ?: "Archive operation failed", error)
        }
    }

    private fun CompressionLevel.toZipLevel(): ZipCompressionLevel = when (this) {
        CompressionLevel.STORE -> ZipCompressionLevel.NO_COMPRESSION
        CompressionLevel.FAST -> ZipCompressionLevel.FASTEST
        CompressionLevel.NORMAL -> ZipCompressionLevel.NORMAL
        CompressionLevel.MAXIMUM -> ZipCompressionLevel.MAXIMUM
        CompressionLevel.ULTRA -> ZipCompressionLevel.ULTRA
    }

    private fun Long.toInstant(): Instant? = takeIf { it > 0 }?.let(Instant::ofEpochMilli)

    private fun singleOutputName(name: String, format: ArchiveFormat): String {
        val suffixes = when (format) {
            ArchiveFormat.GZIP -> listOf(".gzip", ".gz")
            ArchiveFormat.BZIP2 -> listOf(".bzip2", ".bz2")
            ArchiveFormat.XZ -> listOf(".xz")
            ArchiveFormat.ZSTD -> listOf(".zstd", ".zst")
            else -> emptyList()
        }
        val lower = name.lowercase()
        val suffix = suffixes.firstOrNull { lower.endsWith(it) }
        return ArchiveSecurity.sanitizeFilename(if (suffix == null) "$name.out" else name.dropLast(suffix.length), "extracted-file")
    }

    private companion object {
        const val BUFFER_SIZE = 64 * 1024
        val singleStreams = setOf(ArchiveFormat.GZIP, ArchiveFormat.BZIP2, ArchiveFormat.XZ, ArchiveFormat.ZSTD)
        val tarFormats = setOf(ArchiveFormat.TAR, ArchiveFormat.TAR_GZ, ArchiveFormat.TAR_BZ2, ArchiveFormat.TAR_XZ, ArchiveFormat.TAR_ZST)
        val tarAndSimpleArchives = tarFormats + setOf(ArchiveFormat.AR, ArchiveFormat.CPIO)
    }
}
