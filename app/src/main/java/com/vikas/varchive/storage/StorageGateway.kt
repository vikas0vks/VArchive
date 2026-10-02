package com.vikas.varchive.storage

import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import android.provider.DocumentsContract
import androidx.documentfile.provider.DocumentFile
import com.vikas.varchive.archive.ArchiveFailure
import com.vikas.varchive.archive.ArchiveSecurity
import com.vikas.varchive.archive.ConflictPolicy
import com.vikas.varchive.archive.ExtractionPolicy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import kotlin.coroutines.coroutineContext

data class StorageItem(
    val uri: Uri,
    val name: String,
    val isDirectory: Boolean,
    val size: Long,
    val lastModified: Long,
    val mimeType: String?,
)

class StorageGateway(private val context: Context) {
    private val resolver: ContentResolver get() = context.contentResolver

    fun persistReadPermission(uri: Uri) = runCatching {
        resolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }

    fun persistTreePermission(uri: Uri) = runCatching {
        resolver.takePersistableUriPermission(
            uri,
            Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
        )
    }

    fun persistOutputPermission(uri: Uri) = runCatching {
        resolver.takePersistableUriPermission(
            uri,
            Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
        )
    }

    fun grantedTrees(): List<StorageItem> = resolver.persistedUriPermissions
        .asSequence()
        .filter { it.isReadPermission }
        .mapNotNull { permission ->
            runCatching { DocumentFile.fromTreeUri(context, permission.uri) }.getOrNull()
        }
        .distinctBy { it.uri }
        .map { it.toItem() }
        .toList()

    suspend fun list(uri: Uri, showHidden: Boolean): List<StorageItem> = withContext(Dispatchers.IO) {
        val documentId = runCatching { DocumentsContract.getDocumentId(uri) }.getOrElse {
            runCatching { DocumentsContract.getTreeDocumentId(uri) }.getOrElse { throw ArchiveFailure.Permission() }
        }
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(uri, documentId)
        val projection = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
            DocumentsContract.Document.COLUMN_SIZE,
            DocumentsContract.Document.COLUMN_LAST_MODIFIED,
        )
        val items = mutableListOf<StorageItem>()
        resolver.query(childrenUri, projection, null, null, null)?.use { cursor ->
            val idIndex = cursor.getColumnIndexOrThrow(projection[0])
            val nameIndex = cursor.getColumnIndexOrThrow(projection[1])
            val typeIndex = cursor.getColumnIndexOrThrow(projection[2])
            val sizeIndex = cursor.getColumnIndexOrThrow(projection[3])
            val dateIndex = cursor.getColumnIndexOrThrow(projection[4])
            while (cursor.moveToNext()) {
                val name = cursor.getString(nameIndex) ?: "Unnamed"
                if (!showHidden && name.startsWith('.')) continue
                val mime = cursor.getString(typeIndex)
                items += StorageItem(
                    uri = DocumentsContract.buildDocumentUriUsingTree(uri, cursor.getString(idIndex)),
                    name = name,
                    isDirectory = mime == DocumentsContract.Document.MIME_TYPE_DIR,
                    size = if (cursor.isNull(sizeIndex)) 0 else cursor.getLong(sizeIndex),
                    lastModified = if (cursor.isNull(dateIndex)) 0 else cursor.getLong(dateIndex),
                    mimeType = mime,
                )
            }
        } ?: throw ArchiveFailure.Permission()
        items.asSequence()
            .sortedWith(compareByDescending<StorageItem> { it.isDirectory }.thenBy { it.name.lowercase() })
            .toList()
    }

    fun displayName(uri: Uri): String {
        if (uri.scheme == ContentResolver.SCHEME_FILE) return uri.lastPathSegment ?: "archive"
        return resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
        } ?: uri.lastPathSegment ?: "archive"
    }

    suspend fun stage(uri: Uri, workDir: File): File = withContext(Dispatchers.IO) {
        workDir.mkdirs()
        val document = runCatching { DocumentFile.fromTreeUri(context, uri) }.getOrNull()
        val target = File(workDir, ArchiveSecurity.sanitizeFilename(document?.name ?: displayName(uri), "archive.bin"))
        if (document?.isDirectory == true) copyDocumentTree(document, target)
        else copyUriToFile(uri, target)
        target
    }

    private suspend fun copyDocumentTree(source: DocumentFile, target: File) {
        coroutineContext.ensureActive()
        if (source.isDirectory) {
            target.mkdirs()
            source.listFiles().forEach { child ->
                val name = ArchiveSecurity.sanitizeFilename(child.name.orEmpty(), "unnamed")
                copyDocumentTree(child, File(target, name))
            }
        } else copyUriToFile(source.uri, target)
    }

    private suspend fun copyUriToFile(uri: Uri, target: File) {
        resolver.openInputStream(uri)?.use { input ->
            FileOutputStream(target).use { output ->
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE * 8)
                while (true) {
                    coroutineContext.ensureActive()
                    val read = input.read(buffer)
                    if (read < 0) break
                    output.write(buffer, 0, read)
                }
                output.fd.sync()
            }
        } ?: throw ArchiveFailure.Permission()
    }

    suspend fun copyToUri(source: File, destination: Uri) = withContext(Dispatchers.IO) {
        resolver.openOutputStream(destination, "wt")?.use { output ->
            source.inputStream().buffered(DEFAULT_BUFFER_SIZE * 8).use { input -> input.copyTo(output, DEFAULT_BUFFER_SIZE * 8) }
        } ?: throw ArchiveFailure.Permission()
    }

    suspend fun copyTreeToSaf(source: File, destinationTree: Uri, policy: ExtractionPolicy) = withContext(Dispatchers.IO) {
        val root = DocumentFile.fromTreeUri(context, destinationTree) ?: throw ArchiveFailure.Permission()
        source.walkTopDown().drop(1).forEach { file ->
            coroutineContext.ensureActive()
            val relative = file.relativeTo(source).invariantSeparatorsPath
            ArchiveSecurity.safeRelativePath(relative)
            if (file.isDirectory) {
                ensureDirectory(root, relative)
            } else {
                val parentPath = relative.substringBeforeLast('/', "")
                val parent = if (parentPath.isBlank()) root else ensureDirectory(root, parentPath)
                val existing = parent.findFile(file.name)
                val targetName = when {
                    existing == null -> file.name
                    policy.conflictPolicy == ConflictPolicy.SKIP -> return@forEach
                    policy.conflictPolicy == ConflictPolicy.OVERWRITE -> {
                        existing.delete()
                        file.name
                    }
                    policy.conflictPolicy == ConflictPolicy.ASK -> throw ArchiveFailure.Invalid("A file already exists: ${file.name}")
                    else -> uniqueSafName(parent, file.name)
                }
                val doc = parent.createFile("application/octet-stream", targetName) ?: throw ArchiveFailure.Permission()
                resolver.openOutputStream(doc.uri, "wt")?.use { out -> file.inputStream().use { it.copyTo(out, DEFAULT_BUFFER_SIZE * 8) } }
                    ?: throw ArchiveFailure.Permission()
            }
        }
    }

    private fun ensureDirectory(root: DocumentFile, path: String): DocumentFile {
        var current = root
        path.split('/').filter { it.isNotBlank() }.forEach { name ->
            current = current.findFile(name)?.takeIf { it.isDirectory } ?: current.createDirectory(name)
                ?: throw ArchiveFailure.Permission()
        }
        return current
    }

    private fun uniqueSafName(parent: DocumentFile, name: String): String {
        val base = name.substringBeforeLast('.', name)
        val ext = name.substringAfterLast('.', "").let { if (it.isBlank()) "" else ".$it" }
        return generateSequence(1) { it + 1 }.map { "$base ($it)$ext" }.first { parent.findFile(it) == null }
    }

    private fun DocumentFile.toItem() = StorageItem(uri, name ?: "Unnamed", isDirectory, length(), lastModified(), type)
}
