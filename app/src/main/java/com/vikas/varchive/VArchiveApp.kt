package com.vikas.varchive

import android.app.Application
import com.vikas.varchive.archive.ArchiveEngine
import com.vikas.varchive.archive.DefaultArchiveEngine
import com.vikas.varchive.operations.OperationScheduler
import com.vikas.varchive.settings.SettingsRepository
import com.vikas.varchive.storage.StorageGateway

class VArchiveApp : Application() {
    val archiveEngine: ArchiveEngine by lazy { DefaultArchiveEngine() }
    val storage: StorageGateway by lazy { StorageGateway(this) }
    val settings: SettingsRepository by lazy { SettingsRepository(this) }
    val operations: OperationScheduler by lazy { OperationScheduler(this) }
}
