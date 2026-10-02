package com.vikas.varchive.operations

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import android.net.Uri
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.vikas.varchive.R
import com.vikas.varchive.VArchiveApp
import com.vikas.varchive.archive.ArchiveFailure
import com.vikas.varchive.archive.ArchiveFormat
import com.vikas.varchive.archive.ArchiveProgress
import com.vikas.varchive.archive.CompressionLevel
import com.vikas.varchive.archive.CompressionOptions
import com.vikas.varchive.archive.ConflictPolicy
import com.vikas.varchive.archive.ExtractionPolicy
import com.vikas.varchive.archive.LocalSource
import org.json.JSONArray
import java.io.File

class ArchiveWorker(appContext: Context, params: WorkerParameters) : CoroutineWorker(appContext, params) {
    private val app = appContext as VArchiveApp

    override suspend fun doWork(): Result {
        val type = inputData.getString(KEY_TYPE)?.let { runCatching { OperationType.valueOf(it) }.getOrNull() }
            ?: return Result.failure(errorData("Invalid operation"))
        val sourceUris = inputData.getString(KEY_SOURCES)?.let(::jsonUris).orEmpty()
        val destination = inputData.getString(KEY_DESTINATION)?.let(Uri::parse)
        val password = SensitiveOperationSecrets.take(id)
        val workDir = File(applicationContext.filesDir, "work/$id")
        return try {
            setForeground(foreground(type, 0, "Preparing"))
            when (type) {
                OperationType.COMPRESS -> compress(sourceUris, destination, password, workDir)
                OperationType.EXTRACT -> extract(sourceUris.singleOrNull(), destination, password, workDir)
                OperationType.TEST -> test(sourceUris.singleOrNull(), password, workDir)
            }
            Result.success(workDataOf(KEY_MESSAGE to successMessage(type)))
        } catch (cancelled: kotlinx.coroutines.CancellationException) {
            throw cancelled
        } catch (error: Throwable) {
            Result.failure(errorData(userMessage(error)))
        } finally {
            password?.fill('\u0000')
            SensitiveOperationSecrets.clear(id)
            if (workDir.exists()) workDir.deleteRecursively()
        }
    }

    private suspend fun compress(sourceUris: List<Uri>, destination: Uri?, password: CharArray?, workDir: File) {
        if (sourceUris.isEmpty() || destination == null) throw ArchiveFailure.Invalid("Select sources and destination")
        val stagedRoots = sourceUris.mapIndexed { index, uri ->
            val file = app.storage.stage(uri, File(workDir, "sources/$index"))
            file
        }
        val staged = stagedRoots.flatMap { root ->
            if (root.isDirectory) {
                root.walkTopDown().filter { it.isFile }.map { file ->
                    LocalSource(file, "${root.name}/${file.relativeTo(root).invariantSeparatorsPath}", file.lastModified())
                }.toList()
            } else listOf(LocalSource(root, root.name, root.lastModified()))
        }
        val format = inputData.getString(KEY_FORMAT)?.let(ArchiveFormat::valueOf) ?: ArchiveFormat.ZIP
        val level = inputData.getString(KEY_LEVEL)?.let(CompressionLevel::valueOf) ?: CompressionLevel.NORMAL
        val output = File(workDir, "output.${format.primaryExtension}")
        app.archiveEngine.compress(staged, output, CompressionOptions(format, level, password)) { update(OperationType.COMPRESS, it) }
        app.storage.copyToUri(output, destination)
    }

    private suspend fun extract(sourceUri: Uri?, destination: Uri?, password: CharArray?, workDir: File) {
        if (sourceUri == null || destination == null) throw ArchiveFailure.Invalid("Select an archive and destination")
        val archive = app.storage.stage(sourceUri, File(workDir, "archive"))
        val output = File(workDir, "extracted")
        val conflict = inputData.getString(KEY_CONFLICT)?.let(ConflictPolicy::valueOf) ?: ConflictPolicy.RENAME
        val policy = ExtractionPolicy(conflict)
        app.archiveEngine.extract(archive, output, password, policy) { update(OperationType.EXTRACT, it) }
        app.storage.copyTreeToSaf(output, destination, policy)
    }

    private suspend fun test(sourceUri: Uri?, password: CharArray?, workDir: File) {
        if (sourceUri == null) throw ArchiveFailure.Invalid("Select an archive")
        val archive = app.storage.stage(sourceUri, File(workDir, "archive"))
        app.archiveEngine.test(archive, password) { update(OperationType.TEST, it) }
    }

    private suspend fun update(type: OperationType, progress: ArchiveProgress) {
        setProgress(
            workDataOf(
                KEY_PROGRESS to progress.percent,
                KEY_CURRENT_FILE to progress.currentFile,
                KEY_PROCESSED to progress.processedBytes,
                KEY_TOTAL to progress.totalBytes,
                KEY_SPEED to progress.bytesPerSecond,
            )
        )
        setForeground(foreground(type, progress.percent, progress.currentFile))
    }

    private fun foreground(type: OperationType, percent: Int, detail: String): ForegroundInfo {
        createChannel()
        val title = when (type) {
            OperationType.COMPRESS -> "Compressing…"
            OperationType.EXTRACT -> "Extracting…"
            OperationType.TEST -> "Testing archive…"
        }
        val notification = NotificationCompat.Builder(applicationContext, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(detail.take(80))
            .setOnlyAlertOnce(true)
            .setOngoing(true)
            .setProgress(100, percent, percent == 0)
            .addAction(0, "Cancel", androidx.work.WorkManager.getInstance(applicationContext).createCancelPendingIntent(id))
            .build()
        return if (android.os.Build.VERSION.SDK_INT >= 29) {
            ForegroundInfo(id.hashCode(), notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else ForegroundInfo(id.hashCode(), notification)
    }

    private fun createChannel() {
        val manager = applicationContext.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel(CHANNEL, "Archive operations", NotificationManager.IMPORTANCE_LOW))
    }

    private fun successMessage(type: OperationType) = when (type) {
        OperationType.COMPRESS -> "Archive created"
        OperationType.EXTRACT -> "Extraction complete"
        OperationType.TEST -> "Archive OK"
    }

    private fun userMessage(error: Throwable): String = when (error) {
        is ArchiveFailure -> error.message ?: "Archive operation failed"
        else -> error.message?.takeIf { it.isNotBlank() } ?: "Archive operation failed"
    }

    private fun errorData(message: String) = workDataOf(KEY_ERROR to message)

    private fun jsonUris(value: String): List<Uri> {
        val array = JSONArray(value)
        return List(array.length()) { Uri.parse(array.getString(it)) }
    }

    companion object {
        const val TAG = "varchive_operation"
        const val QUEUE = "varchive_heavy_queue"
        const val KEY_TYPE = "type"
        const val KEY_SOURCES = "sources"
        const val KEY_DESTINATION = "destination"
        const val KEY_FORMAT = "format"
        const val KEY_LEVEL = "level"
        const val KEY_CONFLICT = "conflict"
        const val KEY_PROGRESS = "progress"
        const val KEY_CURRENT_FILE = "current_file"
        const val KEY_PROCESSED = "processed"
        const val KEY_TOTAL = "total"
        const val KEY_SPEED = "speed"
        const val KEY_ERROR = "error"
        const val KEY_MESSAGE = "message"
        private const val CHANNEL = "archive_operations"
    }
}
