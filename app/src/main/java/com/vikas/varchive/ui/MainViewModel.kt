package com.vikas.varchive.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.vikas.varchive.VArchiveApp
import com.vikas.varchive.archive.ArchiveEntry
import com.vikas.varchive.archive.ArchiveInfo
import com.vikas.varchive.operations.ArchiveWorker
import com.vikas.varchive.operations.OperationRequest
import com.vikas.varchive.settings.AppSettings
import com.vikas.varchive.storage.StorageItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

enum class MainTab { HOME, FILES, TASKS, SETTINGS }

data class ArchiveBrowserState(
    val uri: Uri,
    val name: String,
    val entries: List<ArchiveEntry> = emptyList(),
    val info: ArchiveInfo? = null,
    val loading: Boolean = true,
    val query: String = "",
)

data class FileBrowserState(
    val current: StorageItem? = null,
    val items: List<StorageItem> = emptyList(),
    val loading: Boolean = false,
    val query: String = "",
    val sort: FileSort = FileSort.NAME,
    val ascending: Boolean = true,
)

enum class FileSort { NAME, SIZE, DATE, TYPE }

data class UiState(
    val tab: MainTab = MainTab.HOME,
    val archive: ArchiveBrowserState? = null,
    val files: FileBrowserState = FileBrowserState(),
    val message: String? = null,
)

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as VArchiveApp
    private val _ui = MutableStateFlow(UiState())
    val ui: StateFlow<UiState> = _ui.asStateFlow()
    val settings: StateFlow<AppSettings> = app.settings.settings.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        AppSettings(),
    )
    val operations: StateFlow<List<WorkInfo>> = WorkManager.getInstance(application)
        .getWorkInfosByTagFlow(ArchiveWorker.TAG)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun selectTab(tab: MainTab) {
        _ui.value = _ui.value.copy(tab = tab)
        if (tab == MainTab.FILES && _ui.value.files.current == null) showRoots()
    }
    fun clearMessage() { _ui.value = _ui.value.copy(message = null) }
    fun showMessage(value: String) { _ui.value = _ui.value.copy(message = value) }
    fun displayName(uri: Uri): String = app.storage.displayName(uri)

    fun openArchive(uri: Uri, password: CharArray? = null) {
        app.storage.persistReadPermission(uri)
        val name = app.storage.displayName(uri)
        _ui.value = _ui.value.copy(archive = ArchiveBrowserState(uri, name))
        viewModelScope.launch {
            val workDir = File(app.cacheDir, "browse/${System.nanoTime()}")
            try {
                val staged = app.storage.stage(uri, workDir)
                val entries = app.archiveEngine.list(staged, password)
                val info = app.archiveEngine.info(staged, password)
                _ui.value = _ui.value.copy(archive = ArchiveBrowserState(uri, name, entries, info, false))
            } catch (error: Throwable) {
                _ui.value = _ui.value.copy(
                    archive = null,
                    message = error.message ?: "Could not open archive",
                )
            } finally {
                password?.fill('\u0000')
                withContext(Dispatchers.IO) { if (workDir.exists()) workDir.deleteRecursively() }
            }
        }
    }

    fun closeArchive() { _ui.value = _ui.value.copy(archive = null) }
    fun searchArchive(query: String) {
        _ui.value = _ui.value.copy(archive = _ui.value.archive?.copy(query = query))
    }

    fun addTree(uri: Uri) {
        app.storage.persistTreePermission(uri)
        val item = app.storage.grantedTrees().firstOrNull { it.uri == uri }
            ?: StorageItem(uri, "Storage", true, 0, 0, null)
        openFolder(item)
    }

    fun openFolder(item: StorageItem) {
        if (!item.isDirectory) {
            if (com.vikas.varchive.archive.FormatDetector.fromName(item.name).canRead) openArchive(item.uri)
            return
        }
        _ui.value = _ui.value.copy(files = _ui.value.files.copy(current = item, loading = true))
        viewModelScope.launch {
            try {
                val items = app.storage.list(item.uri, settings.value.showHidden)
                _ui.value = _ui.value.copy(files = _ui.value.files.copy(current = item, items = items, loading = false))
            } catch (error: Throwable) {
                _ui.value = _ui.value.copy(files = _ui.value.files.copy(loading = false), message = error.message)
            }
        }
    }

    fun showRoots() { _ui.value = _ui.value.copy(files = FileBrowserState(items = app.storage.grantedTrees())) }
    fun searchFiles(query: String) { _ui.value = _ui.value.copy(files = _ui.value.files.copy(query = query)) }
    fun sortFiles(sort: FileSort) {
        val current = _ui.value.files
        _ui.value = _ui.value.copy(files = current.copy(sort = sort, ascending = if (current.sort == sort) !current.ascending else true))
    }

    fun enqueue(request: OperationRequest) {
        request.sources.forEach { uri ->
            app.storage.persistReadPermission(uri)
            app.storage.persistTreePermission(uri)
        }
        request.destination?.let { destination ->
            if (request.type == com.vikas.varchive.operations.OperationType.EXTRACT) app.storage.persistTreePermission(destination)
            else app.storage.persistOutputPermission(destination)
        }
        app.operations.enqueue(request)
        _ui.value = _ui.value.copy(tab = MainTab.TASKS, archive = null, message = "Operation queued")
    }

    fun cancel(id: java.util.UUID) = app.operations.cancel(id)
    fun clearCompleted() = app.operations.clearCompleted()

    fun setTheme(value: com.vikas.varchive.settings.ThemeMode) = viewModelScope.launch { app.settings.setTheme(value) }
    fun setFormat(value: com.vikas.varchive.archive.ArchiveFormat) = viewModelScope.launch { app.settings.setDefaultFormat(value) }
    fun setLevel(value: com.vikas.varchive.archive.CompressionLevel) = viewModelScope.launch { app.settings.setDefaultLevel(value) }
    fun setConflict(value: com.vikas.varchive.archive.ConflictPolicy) = viewModelScope.launch { app.settings.setConflict(value) }
    fun setShowHidden(value: Boolean) = viewModelScope.launch { app.settings.setShowHidden(value) }
    fun setBombWarnings(value: Boolean) = viewModelScope.launch { app.settings.setBombWarnings(value) }

    init { showRoots() }
}
