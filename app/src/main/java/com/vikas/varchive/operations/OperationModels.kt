package com.vikas.varchive.operations

import android.content.Context
import android.net.Uri
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.vikas.varchive.archive.ArchiveFormat
import com.vikas.varchive.archive.CompressionLevel
import com.vikas.varchive.archive.ConflictPolicy
import org.json.JSONArray
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

enum class OperationType { COMPRESS, EXTRACT, TEST }

data class OperationRequest(
    val type: OperationType,
    val sources: List<Uri>,
    val destination: Uri? = null,
    val format: ArchiveFormat? = null,
    val level: CompressionLevel = CompressionLevel.NORMAL,
    val conflict: ConflictPolicy = ConflictPolicy.RENAME,
    val password: CharArray? = null,
)

object SensitiveOperationSecrets {
    private val secrets = ConcurrentHashMap<UUID, CharArray>()

    fun put(id: UUID, password: CharArray?) {
        if (password != null && password.isNotEmpty()) secrets[id] = password.copyOf()
    }

    fun take(id: UUID): CharArray? = secrets.remove(id)

    fun clear(id: UUID) {
        secrets.remove(id)?.fill('\u0000')
    }
}

class OperationScheduler(context: Context) {
    private val workManager = WorkManager.getInstance(context)

    fun enqueue(request: OperationRequest): UUID {
        val id = UUID.randomUUID()
        SensitiveOperationSecrets.put(id, request.password)
        val data = workDataOf(
            ArchiveWorker.KEY_TYPE to request.type.name,
            ArchiveWorker.KEY_SOURCES to JSONArray(request.sources.map(Uri::toString)).toString(),
            ArchiveWorker.KEY_DESTINATION to request.destination?.toString(),
            ArchiveWorker.KEY_FORMAT to request.format?.name,
            ArchiveWorker.KEY_LEVEL to request.level.name,
            ArchiveWorker.KEY_CONFLICT to request.conflict.name,
        )
        val work = OneTimeWorkRequestBuilder<ArchiveWorker>()
            .setId(id)
            .setInputData(data)
            .addTag(ArchiveWorker.TAG)
            .build()
        workManager.enqueueUniqueWork(ArchiveWorker.QUEUE, ExistingWorkPolicy.APPEND_OR_REPLACE, work)
        request.password?.fill('\u0000')
        return id
    }

    fun cancel(id: UUID) = workManager.cancelWorkById(id)
    fun clearCompleted() = workManager.pruneWork()
}

internal fun Data.progressPercent(): Int = getInt(ArchiveWorker.KEY_PROGRESS, 0)
