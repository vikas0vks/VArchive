@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.vikas.varchive.ui

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Compress
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.TaskAlt
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Unarchive
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.work.WorkInfo
import com.vikas.varchive.BuildConfig
import com.vikas.varchive.archive.ArchiveEntry
import com.vikas.varchive.archive.ArchiveFormat
import com.vikas.varchive.archive.CompressionLevel
import com.vikas.varchive.archive.CompressionOptions
import com.vikas.varchive.archive.ConflictPolicy
import com.vikas.varchive.archive.formatBytes
import com.vikas.varchive.operations.ArchiveWorker
import com.vikas.varchive.operations.OperationRequest
import com.vikas.varchive.operations.OperationType
import com.vikas.varchive.settings.AppSettings
import com.vikas.varchive.settings.ThemeMode
import com.vikas.varchive.storage.StorageItem
import com.vikas.varchive.ui.theme.VArchiveTheme
import java.text.DateFormat
import java.util.Date

private data class PendingCompression(
    val sources: List<Uri>,
    val format: ArchiveFormat,
    val level: CompressionLevel,
    val password: CharArray?,
)

@Composable
fun VArchiveRoot(viewModel: MainViewModel) {
    val context = LocalContext.current
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val work by viewModel.operations.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }

    var compressionSources by remember { mutableStateOf<List<Uri>>(emptyList()) }
    var pendingCompression by remember { mutableStateOf<PendingCompression?>(null) }
    var chooseCompressionSource by remember { mutableStateOf(false) }
    var extractSource by remember { mutableStateOf<Uri?>(null) }
    var extractName by remember { mutableStateOf("") }
    var extractPassword by remember { mutableStateOf<CharArray?>(null) }

    val createDestination = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        val pending = pendingCompression
        if (uri != null && pending != null) {
            viewModel.enqueue(
                OperationRequest(
                    OperationType.COMPRESS,
                    pending.sources,
                    uri,
                    pending.format,
                    pending.level,
                    password = pending.password,
                )
            )
        } else pending?.password?.fill('\u0000')
        pendingCompression = null
    }
    val pickCompressionSources = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty()) compressionSources = uris
    }
    val pickCompressionFolder = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) compressionSources = listOf(uri)
    }
    val pickOpenArchive = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(viewModel::openArchive)
    }
    val pickExtractArchive = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        extractSource = uri
        extractName = uri?.let(viewModel::displayName).orEmpty()
    }
    val pickExtractionDestination = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { tree ->
        val archive = extractSource
        if (tree != null && archive != null) {
            viewModel.enqueue(
                OperationRequest(
                    OperationType.EXTRACT,
                    listOf(archive),
                    tree,
                    conflict = settings.conflictPolicy,
                    password = extractPassword,
                )
            )
        } else extractPassword?.fill('\u0000')
        extractSource = null
        extractName = ""
        extractPassword = null
    }
    val pickTestArchive = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { viewModel.enqueue(OperationRequest(OperationType.TEST, listOf(it))) }
    }
    val pickTree = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        uri?.let(viewModel::addTree)
    }
    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    fun requestOperationNotifications() {
        if (Build.VERSION.SDK_INT >= 33 && context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    LaunchedEffect(ui.message) {
        ui.message?.let { snackbar.showSnackbar(it); viewModel.clearMessage() }
    }
    VArchiveTheme(settings.themeMode) {
        if (ui.archive != null) {
            ArchiveBrowserScreen(
                state = ui.archive!!,
                onBack = viewModel::closeArchive,
                onSearch = viewModel::searchArchive,
                onExtract = {
                    requestOperationNotifications()
                    extractSource = ui.archive!!.uri
                    extractName = ui.archive!!.name
                },
                onTest = { requestOperationNotifications(); viewModel.enqueue(OperationRequest(OperationType.TEST, listOf(ui.archive!!.uri))) },
            )
        } else {
            Scaffold(
                modifier = Modifier.fillMaxSize(),
                snackbarHost = { SnackbarHost(snackbar) },
                topBar = { MainTopBar(ui.tab) },
                bottomBar = { BottomNavigation(ui.tab, viewModel::selectTab) },
                floatingActionButton = {
                    if (ui.tab == MainTab.FILES) {
                        FloatingActionButton(onClick = { pickTree.launch(null) }) {
                            Icon(Icons.Default.CreateNewFolder, "Add storage location")
                        }
                    }
                },
            ) { padding ->
                AnimatedContent(ui.tab, label = "main-navigation", modifier = Modifier.padding(padding)) { tab ->
                    when (tab) {
                        MainTab.HOME -> HomeScreen(
                            roots = ui.files.items,
                            operations = work,
                            onCreate = { requestOperationNotifications(); chooseCompressionSource = true },
                            onExtract = { requestOperationNotifications(); pickExtractArchive.launch(archiveMimeTypes) },
                            onOpen = { pickOpenArchive.launch(archiveMimeTypes) },
                            onBrowse = { viewModel.selectTab(MainTab.FILES) },
                            onTasks = { viewModel.selectTab(MainTab.TASKS) },
                        )
                        MainTab.FILES -> FilesScreen(
                            state = ui.files,
                            roots = if (ui.files.current == null) ui.files.items else emptyList(),
                            onOpen = viewModel::openFolder,
                            onBack = viewModel::showRoots,
                            onSearch = viewModel::searchFiles,
                            onSort = viewModel::sortFiles,
                            onAdd = { pickTree.launch(null) },
                        )
                        MainTab.TASKS -> TasksScreen(work, viewModel::cancel, viewModel::clearCompleted)
                        MainTab.SETTINGS -> SettingsScreen(settings, viewModel)
                    }
                }
            }
        }

        if (compressionSources.isNotEmpty()) {
            CompressionSheet(
                count = compressionSources.size,
                defaults = settings,
                onDismiss = { compressionSources = emptyList() },
            ) { format, level, name, password ->
                pendingCompression = PendingCompression(compressionSources, format, level, password)
                compressionSources = emptyList()
                val safeName = if (name.lowercase().endsWith(".${format.primaryExtension}")) name else "$name.${format.primaryExtension}"
                createDestination.launch(safeName)
            }
        }

        if (chooseCompressionSource) {
            AlertDialog(
                onDismissRequest = { chooseCompressionSource = false },
                icon = { Icon(Icons.Default.Compress, null) },
                title = { Text("Choose source") },
                text = { Text("Select multiple files or an entire folder. Folder contents are streamed into the archive with relative paths preserved.") },
                confirmButton = {
                    TextButton(onClick = { chooseCompressionSource = false; pickCompressionSources.launch(arrayOf("*/*")) }) { Text("Select files") }
                },
                dismissButton = {
                    TextButton(onClick = { chooseCompressionSource = false; pickCompressionFolder.launch(null) }) { Text("Select folder") }
                },
            )
        }

        if (extractSource != null) {
            ExtractionSheet(
                archiveName = extractName.ifBlank { "Archive" },
                defaultConflict = settings.conflictPolicy,
                onDismiss = { extractSource = null; extractName = "" },
            ) { password ->
                extractPassword = password
                pickExtractionDestination.launch(null)
            }
        }
    }
}

@Composable
private fun MainTopBar(tab: MainTab) {
    CenterAlignedTopAppBar(
        title = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(if (tab == MainTab.HOME) "VArchive" else tab.name.lowercase().replaceFirstChar(Char::uppercase), fontWeight = FontWeight.SemiBold)
                if (tab == MainTab.HOME) Text("Archive utility", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    )
}

@Composable
private fun BottomNavigation(selected: MainTab, onSelect: (MainTab) -> Unit) {
    NavigationBar(modifier = Modifier.navigationBarsPadding()) {
        listOf(
            Triple(MainTab.HOME, Icons.Default.Home, "Home"),
            Triple(MainTab.FILES, Icons.Default.Folder, "Files"),
            Triple(MainTab.TASKS, Icons.Default.History, "Tasks"),
            Triple(MainTab.SETTINGS, Icons.Default.Settings, "Settings"),
        ).forEach { (tab, icon, label) ->
            NavigationBarItem(
                selected = selected == tab,
                onClick = { onSelect(tab) },
                icon = { Icon(icon, label) },
                label = { Text(label) },
            )
        }
    }
}

@Composable
private fun HomeScreen(
    roots: List<StorageItem>,
    operations: List<WorkInfo>,
    onCreate: () -> Unit,
    onExtract: () -> Unit,
    onOpen: () -> Unit,
    onBrowse: () -> Unit,
    onTasks: () -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        item {
            ElevatedCard(
                colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                shape = RoundedCornerShape(28.dp),
            ) {
                Column(Modifier.padding(24.dp)) {
                    Icon(Icons.Default.Archive, null, Modifier.size(42.dp), tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.height(16.dp))
                    Text("Archives, under control.", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    Text("Create, inspect and extract without leaving Android's secure storage model.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = .78f))
                }
            }
        }
        item {
            Text("Quick actions", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(10.dp))
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                modifier = Modifier.height(216.dp),
                userScrollEnabled = false,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item { ActionCard("Create archive", "ZIP, 7Z, TAR…", Icons.Default.Compress, onCreate) }
                item { ActionCard("Extract archive", "Secure extraction", Icons.Default.Unarchive, onExtract) }
                item { ActionCard("Open archive", "Browse contents", Icons.Default.FolderOpen, onOpen) }
                item { ActionCard("Browse files", "Granted locations", Icons.Default.Storage, onBrowse) }
            }
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Recent operations", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                TextButton(onClick = onTasks) { Text("View all") }
            }
            val recent = operations.sortedByDescending { it.runAttemptCount }.take(3)
            if (recent.isEmpty()) EmptyInline("No operations yet", "New compression and extraction jobs appear here.")
            else recent.forEach { WorkRow(it, null) }
        }
        item {
            Text("Storage", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(8.dp))
            Text(
                if (roots.isEmpty()) "Add a folder in Files to grant VArchive secure access." else "${roots.size} secure location${if (roots.size == 1) "" else "s"} available",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ActionCard(title: String, subtitle: String, icon: ImageVector, onClick: () -> Unit) {
    Card(onClick = onClick, modifier = Modifier.fillMaxSize(), shape = RoundedCornerShape(20.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.SpaceBetween) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
            Column {
                Text(title, fontWeight = FontWeight.SemiBold)
                Text(subtitle, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun FilesScreen(
    state: FileBrowserState,
    roots: List<StorageItem>,
    onOpen: (StorageItem) -> Unit,
    onBack: () -> Unit,
    onSearch: (String) -> Unit,
    onSort: (FileSort) -> Unit,
    onAdd: () -> Unit,
) {
    val source = if (state.current == null) roots else state.items
    val filtered = source.filter { it.name.contains(state.query, ignoreCase = true) }
    val sorted = filtered.sortedWith(
        when (state.sort) {
            FileSort.NAME -> compareBy { it.name.lowercase() }
            FileSort.SIZE -> compareBy { it.size }
            FileSort.DATE -> compareBy { it.lastModified }
            FileSort.TYPE -> compareBy { it.mimeType.orEmpty() }
        }
    ).let { if (state.ascending) it else it.reversed() }.sortedByDescending { it.isDirectory }
    Column(Modifier.fillMaxSize()) {
        if (state.current != null) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back to locations") }
                Text(state.current.name, modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        SearchBar(state.query, onSearch) {
            var menu by remember { mutableStateOf(false) }
            Box {
                IconButton(onClick = { menu = true }) { Icon(Icons.AutoMirrored.Filled.Sort, "Sort files") }
                DropdownMenu(menu, { menu = false }) {
                    FileSort.entries.forEach { sort -> DropdownMenuItem(text = { Text(sort.name.lowercase().replaceFirstChar(Char::uppercase)) }, onClick = { onSort(sort); menu = false }) }
                }
            }
        }
        if (state.loading) Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        else if (sorted.isEmpty()) Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            EmptyState(Icons.Default.FolderOpen, if (state.current == null) "No folders granted" else "This folder is empty", if (state.current == null) "Add a storage location to begin." else "No matching files.", if (state.current == null) onAdd else null)
        } else LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 88.dp)) {
            items(sorted, key = { it.uri.toString() }) { item ->
                FileRow(item, { onOpen(item) })
                HorizontalDivider(Modifier.padding(start = 72.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = .5f))
            }
        }
    }
}

@Composable
private fun SearchBar(value: String, onChange: (String) -> Unit, trailing: @Composable () -> Unit = {}) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        placeholder = { Text("Search") },
        leadingIcon = { Icon(Icons.Default.Search, null) },
        trailingIcon = trailing,
        singleLine = true,
        shape = RoundedCornerShape(18.dp),
    )
}

@Composable
private fun FileRow(item: StorageItem, onClick: () -> Unit) {
    val icon = when {
        item.isDirectory -> Icons.Default.Folder
        item.mimeType?.startsWith("image/") == true -> Icons.Default.Image
        item.mimeType?.startsWith("video/") == true -> Icons.Default.Movie
        item.mimeType?.startsWith("audio/") == true -> Icons.Default.MusicNote
        item.mimeType == "application/pdf" -> Icons.Default.PictureAsPdf
        ArchiveFormat.entries.any { item.name.lowercase().endsWith(".${it.primaryExtension}") } -> Icons.Default.Archive
        else -> Icons.AutoMirrored.Filled.InsertDriveFile
    }
    ListItem(
        modifier = Modifier.combinedClickable(onClick = onClick, onLongClick = {}),
        headlineContent = { Text(item.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        supportingContent = { Text(if (item.isDirectory) "Folder" else "${formatBytes(item.size)}  •  ${formatDate(item.lastModified)}") },
        leadingContent = { Icon(icon, null, tint = if (item.isDirectory) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant) },
        trailingContent = { if (item.isDirectory) Icon(Icons.Default.ChevronRight, null) },
    )
}

@Composable
private fun TasksScreen(work: List<WorkInfo>, onCancel: (java.util.UUID) -> Unit, onClear: () -> Unit) {
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.End) {
            TextButton(onClick = onClear, enabled = work.any { it.state.isFinished }) {
                Icon(Icons.Default.DeleteSweep, null)
                Spacer(Modifier.width(8.dp))
                Text("Clear completed")
            }
        }
        if (work.isEmpty()) Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            EmptyState(Icons.Default.TaskAlt, "Queue is empty", "Compression, extraction and integrity tests run here.")
        } else LazyColumn(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            items(work.sortedByDescending { it.id.toString() }, key = { it.id }) { WorkRow(it, onCancel) }
        }
    }
}

@Composable
private fun WorkRow(info: WorkInfo, onCancel: ((java.util.UUID) -> Unit)?) {
    val progress = info.progress.getInt(ArchiveWorker.KEY_PROGRESS, 0)
    val current = info.progress.getString(ArchiveWorker.KEY_CURRENT_FILE).orEmpty()
    val title = when (info.state) {
        WorkInfo.State.ENQUEUED, WorkInfo.State.BLOCKED -> "Queued"
        WorkInfo.State.RUNNING -> "Archive operation"
        WorkInfo.State.SUCCEEDED -> info.outputData.getString(ArchiveWorker.KEY_MESSAGE) ?: "Completed"
        WorkInfo.State.FAILED -> "Failed"
        WorkInfo.State.CANCELLED -> "Cancelled"
    }
    ElevatedCard(Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    when (info.state) {
                        WorkInfo.State.SUCCEEDED -> Icons.Default.CheckCircle
                        WorkInfo.State.FAILED -> Icons.Default.Error
                        WorkInfo.State.CANCELLED -> Icons.Default.Cancel
                        else -> Icons.Default.Archive
                    }, null,
                    tint = when (info.state) {
                        WorkInfo.State.SUCCEEDED -> Color(0xFF2E7D32)
                        WorkInfo.State.FAILED -> MaterialTheme.colorScheme.error
                        else -> MaterialTheme.colorScheme.primary
                    }
                )
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(title, fontWeight = FontWeight.SemiBold)
                    Text(
                        if (info.state == WorkInfo.State.FAILED) info.outputData.getString(ArchiveWorker.KEY_ERROR).orEmpty() else current.ifBlank { info.state.name.lowercase().replaceFirstChar(Char::uppercase) },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                    )
                }
                if (!info.state.isFinished && onCancel != null) IconButton(onClick = { onCancel(info.id) }) { Icon(Icons.Default.Cancel, "Cancel operation") }
            }
            if (!info.state.isFinished) LinearProgressIndicator({ progress / 100f }, Modifier.fillMaxWidth())
            if (info.state == WorkInfo.State.RUNNING) {
                val processed = info.progress.getLong(ArchiveWorker.KEY_PROCESSED, 0)
                val total = info.progress.getLong(ArchiveWorker.KEY_TOTAL, 0)
                val speed = info.progress.getLong(ArchiveWorker.KEY_SPEED, 0)
                Text("$progress%  •  ${formatBytes(processed)} / ${formatBytes(total)}  •  ${formatBytes(speed)}/s", style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

@Composable
private fun SettingsScreen(settings: AppSettings, viewModel: MainViewModel) {
    var about by remember { mutableStateOf(false) }
    var formats by remember { mutableStateOf(false) }
    var licenses by remember { mutableStateOf(false) }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
        item { SectionLabel("Appearance") }
        item { ChoiceSetting("Theme", settings.themeMode.label, Icons.Default.DarkMode, ThemeMode.entries, { it.label }, viewModel::setTheme) }
        item { SectionLabel("Archives") }
        item { ChoiceSetting("Default format", settings.defaultFormat.label, Icons.Default.Archive, ArchiveFormat.creatable, { it.label }, viewModel::setFormat) }
        item { ChoiceSetting("Compression level", settings.defaultLevel.label, Icons.Default.Speed, CompressionLevel.entries, { it.label }, viewModel::setLevel) }
        item { ChoiceSetting("Existing files", settings.conflictPolicy.label, Icons.Default.Tune, ConflictPolicy.entries, { it.label }, viewModel::setConflict) }
        item { SwitchSetting("Show hidden files", "Include dot-files in the file browser", settings.showHidden, viewModel::setShowHidden) }
        item { SwitchSetting("Archive bomb warnings", "Block extreme file counts and declared sizes", settings.bombWarnings, viewModel::setBombWarnings) }
        item { SectionLabel("Information") }
        item { SimpleSetting("Supported formats", "Honest read/create capability matrix", Icons.Default.Description) { formats = true } }
        item { SimpleSetting("Open source licenses", "Archive engine notices", Icons.Default.Description) { licenses = true } }
        item { SimpleSetting("About VArchive", "Version ${BuildConfig.VERSION_NAME}", Icons.Default.Info) { about = true } }
        item { Text("Created by V!K@$", Modifier.fillMaxWidth().padding(24.dp), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
    if (about) AboutDialog { about = false }
    if (formats) FormatsDialog { formats = false }
    if (licenses) LicensesDialog { licenses = false }
}

private val ThemeMode.label get() = name.lowercase().replaceFirstChar(Char::uppercase)
private val ConflictPolicy.label get() = name.lowercase().replaceFirstChar(Char::uppercase)

@Composable
private fun SectionLabel(text: String) { Text(text, Modifier.padding(start = 24.dp, top = 20.dp, bottom = 8.dp), color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge) }

@Composable
private fun <T> ChoiceSetting(title: String, value: String, icon: ImageVector, options: List<T>, label: (T) -> String, onSelect: (T) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        ListItem(
            modifier = Modifier.combinedClickable(onClick = { open = true }, onLongClick = {}),
            headlineContent = { Text(title) },
            supportingContent = { Text(value) },
            leadingContent = { Icon(icon, null) },
            trailingContent = { Icon(Icons.Default.ExpandMore, null) },
        )
        DropdownMenu(open, { open = false }) {
            options.forEach { option -> DropdownMenuItem(text = { Text(label(option)) }, onClick = { onSelect(option); open = false }) }
        }
    }
}

@Composable
private fun SwitchSetting(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = { Text(subtitle) },
        trailingContent = { Switch(checked, onChange) },
    )
}

@Composable
private fun SimpleSetting(title: String, subtitle: String, icon: ImageVector, onClick: () -> Unit) {
    ListItem(
        modifier = Modifier.combinedClickable(onClick = onClick, onLongClick = {}),
        headlineContent = { Text(title) }, supportingContent = { Text(subtitle) },
        leadingContent = { Icon(icon, null) }, trailingContent = { Icon(Icons.Default.ChevronRight, null) },
    )
}

@Composable
private fun AboutDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.Archive, null, Modifier.size(44.dp), tint = MaterialTheme.colorScheme.primary) },
        title = { Text("VArchive") },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Version ${BuildConfig.VERSION_NAME}")
                Text("Powerful archive manager for Android.")
                Text("Created by V!K@$", fontWeight = FontWeight.SemiBold)
                Text("Built with Apache Commons Compress, Zip4j, XZ for Java and zstd-jni.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } },
    )
}

@Composable
private fun FormatsDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Supported formats") },
        text = {
            LazyColumn(Modifier.fillMaxHeight(.62f)) {
                items(ArchiveFormat.entries.filter { it != ArchiveFormat.UNKNOWN }) { format ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(format.label, Modifier.weight(1f), fontWeight = FontWeight.Medium)
                        Text(
                            when {
                                format == ArchiveFormat.RAR -> "Read • Extract"
                                format.canCreate -> if (format.canEncrypt) "Read • Create • AES" else "Read • Create"
                                format.canRead -> "Read only"
                                else -> "Unavailable"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } },
    )
}

@Composable
private fun LicensesDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Open source licenses") },
        text = {
            LazyColumn(Modifier.fillMaxHeight(.62f), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                item { LicenseItem("Apache Commons Compress 1.28.0", "Apache License 2.0") }
                item { LicenseItem("Zip4j 2.11.6", "Apache License 2.0") }
                item { LicenseItem("XZ for Java 1.12", "0BSD") }
                item { LicenseItem("zstd-jni 1.5.7-12", "BSD 2-Clause; bundled Zstandard is BSD 3-Clause or GPL-2.0") }
                item { LicenseItem("Junrar 8.1.1", "UnRAR License; extraction only, RAR creation is prohibited and not implemented") }
                item { Text("Full integration and limitation details are included in ARCHIVE_ENGINE.md.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } },
    )
}

@Composable
private fun LicenseItem(name: String, license: String) {
    Column { Text(name, fontWeight = FontWeight.SemiBold); Text(license, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
}

@Composable
private fun CompressionSheet(count: Int, defaults: AppSettings, onDismiss: () -> Unit, onCreate: (ArchiveFormat, CompressionLevel, String, CharArray?) -> Unit) {
    var name by rememberSaveable { mutableStateOf("Archive") }
    var format by remember { mutableStateOf(defaults.defaultFormat) }
    var level by remember { mutableStateOf(defaults.defaultLevel) }
    var password by rememberSaveable { mutableStateOf("") }
    var confirm by rememberSaveable { mutableStateOf("") }
    var visible by rememberSaveable { mutableStateOf(false) }
    var formatMenu by remember { mutableStateOf(false) }
    var levelMenu by remember { mutableStateOf(false) }
    val errors = CompressionOptions(format, level, password.takeIf { it.isNotBlank() }?.toCharArray()).validate(count) +
        if (password != confirm) listOf("Passwords do not match") else emptyList()
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp).navigationBarsPadding(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text("Create archive", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text("$count selected item${if (count == 1) "" else "s"}", color = MaterialTheme.colorScheme.onSurfaceVariant)
            OutlinedTextField(name, { name = it }, Modifier.fillMaxWidth(), label = { Text("Archive name") }, singleLine = true)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(Modifier.weight(1f)) {
                    OutlinedButton({ formatMenu = true }, Modifier.fillMaxWidth()) { Text(format.label); Spacer(Modifier.weight(1f)); Icon(Icons.Default.ExpandMore, null) }
                    DropdownMenu(formatMenu, { formatMenu = false }) {
                        ArchiveFormat.creatable.forEach { value -> DropdownMenuItem({ Text(value.label) }, { format = value; formatMenu = false; if (!format.canEncrypt) { password = ""; confirm = "" } }) }
                    }
                }
                Box(Modifier.weight(1f)) {
                    OutlinedButton({ levelMenu = true }, Modifier.fillMaxWidth()) { Text(level.label); Spacer(Modifier.weight(1f)); Icon(Icons.Default.ExpandMore, null) }
                    DropdownMenu(levelMenu, { levelMenu = false }) {
                        CompressionLevel.entries.forEach { value -> DropdownMenuItem({ Text(value.label) }, { level = value; levelMenu = false }) }
                    }
                }
            }
            if (format.canEncrypt) {
                OutlinedTextField(
                    password, { password = it }, Modifier.fillMaxWidth(), label = { Text("Password (optional)") },
                    visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    trailingIcon = { IconButton({ visible = !visible }) { Icon(if (visible) Icons.Default.VisibilityOff else Icons.Default.Visibility, if (visible) "Hide password" else "Show password") } },
                    singleLine = true,
                )
                if (password.isNotEmpty()) OutlinedTextField(confirm, { confirm = it }, Modifier.fillMaxWidth(), label = { Text("Confirm password") }, visualTransformation = PasswordVisualTransformation(), singleLine = true)
                Text("ZIP encryption uses AES-256. Passwords are held only in memory.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            errors.firstOrNull()?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            Button(
                onClick = { onCreate(format, level, name.ifBlank { "Archive" }, password.takeIf(String::isNotBlank)?.toCharArray()) },
                enabled = errors.isEmpty(),
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) { Icon(Icons.Default.Compress, null); Spacer(Modifier.width(8.dp)); Text("Choose destination") }
            Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable
private fun ExtractionSheet(archiveName: String, defaultConflict: ConflictPolicy, onDismiss: () -> Unit, onContinue: (CharArray?) -> Unit) {
    var password by rememberSaveable { mutableStateOf("") }
    var visible by rememberSaveable { mutableStateOf(false) }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp).navigationBarsPadding(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text("Extract archive", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text(archiveName, maxLines = 1, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurfaceVariant)
            OutlinedTextField(
                password, { password = it }, Modifier.fillMaxWidth(), label = { Text("Password (if required)") },
                visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = { IconButton({ visible = !visible }) { Icon(if (visible) Icons.Default.VisibilityOff else Icons.Default.Visibility, if (visible) "Hide password" else "Show password") } },
                singleLine = true,
            )
            Text("Existing files: ${defaultConflict.label}. Unsafe paths and archive symlinks are blocked.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Button({ onContinue(password.takeIf(String::isNotBlank)?.toCharArray()) }, Modifier.fillMaxWidth().height(52.dp)) {
                Icon(Icons.Default.FolderOpen, null); Spacer(Modifier.width(8.dp)); Text("Choose destination folder")
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable
private fun ArchiveBrowserScreen(state: ArchiveBrowserState, onBack: () -> Unit, onSearch: (String) -> Unit, onExtract: () -> Unit, onTest: () -> Unit) {
    var showInfo by remember { mutableStateOf(false) }
    var currentPath by remember(state.name) { mutableStateOf("") }
    BackHandler(onBack = onBack)
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Column { Text(state.name, maxLines = 1, overflow = TextOverflow.Ellipsis); Text(state.info?.format?.label ?: "Detecting…", style = MaterialTheme.typography.labelSmall) } },
                navigationIcon = { IconButton(onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Close archive") } },
                actions = { IconButton({ showInfo = true }, enabled = state.info != null) { Icon(Icons.Default.Info, "Archive information") } },
            )
        },
        bottomBar = {
            if (!state.loading) Row(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceContainer).navigationBarsPadding().padding(12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(onExtract, Modifier.weight(1f)) { Icon(Icons.Default.Unarchive, null); Spacer(Modifier.width(6.dp)); Text("Extract") }
                OutlinedButton(onTest, Modifier.weight(1f)) { Icon(Icons.Default.TaskAlt, null); Spacer(Modifier.width(6.dp)); Text("Test") }
            }
        }
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            SearchBar(state.query, onSearch)
            if (!state.loading && state.query.isBlank()) {
                FlowRow(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    AssistChip(onClick = { currentPath = "" }, label = { Text(state.info?.format?.label ?: "Root") })
                    var accumulated = ""
                    currentPath.split('/').filter { it.isNotBlank() }.forEach { part ->
                        accumulated = if (accumulated.isBlank()) part else "$accumulated/$part"
                        val target = accumulated
                        Icon(Icons.Default.ChevronRight, null)
                        AssistChip(onClick = { currentPath = target }, label = { Text(part, maxLines = 1) })
                    }
                }
            }
            if (state.loading) Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            else {
                val entries = if (state.query.isNotBlank()) {
                    state.entries.filter { it.path.contains(state.query, true) }
                } else archiveChildren(state.entries, currentPath)
                val sortedEntries = entries.sortedWith(compareByDescending<ArchiveEntry> { it.isDirectory }.thenBy { it.path.lowercase() })
                LazyColumn(Modifier.fillMaxSize()) {
                    items(sortedEntries, key = { it.path }) { entry ->
                        ListItem(
                            modifier = Modifier.combinedClickable(
                                onClick = { if (entry.isDirectory && state.query.isBlank()) currentPath = entry.path.trimEnd('/') },
                                onLongClick = {},
                            ),
                            headlineContent = { Text(if (state.query.isBlank()) entry.name else entry.path, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                            supportingContent = { Text(if (entry.isDirectory) "Folder" else buildString { append(formatBytes(entry.size)); entry.ratioPercent?.let { append("  •  $it% saved") } }) },
                            leadingContent = { Icon(if (entry.isDirectory) Icons.Default.Folder else Icons.AutoMirrored.Filled.InsertDriveFile, null, tint = MaterialTheme.colorScheme.primary) },
                            trailingContent = { if (entry.encrypted) Icon(Icons.Default.Lock, "Encrypted") },
                        )
                        HorizontalDivider(Modifier.padding(start = 72.dp))
                    }
                }
            }
        }
    }
    if (showInfo && state.info != null) {
        val info = state.info
        AlertDialog(
            onDismissRequest = { showInfo = false }, title = { Text("Archive information") },
            text = { Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                InfoLine("Format", info.format.label); InfoLine("Physical size", formatBytes(info.physicalSize)); InfoLine("Uncompressed", formatBytes(info.uncompressedSize)); InfoLine("Files", info.fileCount.toString()); InfoLine("Folders", info.folderCount.toString()); InfoLine("Encrypted", if (info.encrypted) "Yes" else "No"); InfoLine("Methods", info.methods.joinToString().ifBlank { "Unknown" })
            } },
            confirmButton = { TextButton({ showInfo = false }) { Text("Done") } },
        )
    }
}

private fun archiveChildren(entries: List<ArchiveEntry>, parent: String): List<ArchiveEntry> {
    val prefix = parent.trim('/').let { if (it.isBlank()) "" else "$it/" }
    val result = linkedMapOf<String, ArchiveEntry>()
    entries.forEach { entry ->
        if (!entry.path.startsWith(prefix)) return@forEach
        val remainder = entry.path.removePrefix(prefix).trimEnd('/')
        if (remainder.isBlank()) return@forEach
        val first = remainder.substringBefore('/')
        val path = "$prefix$first"
        val isVirtualDirectory = remainder.contains('/')
        val shown = if (isVirtualDirectory) ArchiveEntry("$path/", true, 0) else entry
        result[path] = result[path]?.takeIf { it.isDirectory } ?: shown
    }
    return result.values.toList()
}

@Composable
private fun InfoLine(label: String, value: String) { Row(Modifier.fillMaxWidth()) { Text(label, Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurfaceVariant); Text(value, fontWeight = FontWeight.Medium, maxLines = 2) } }

@Composable
private fun EmptyState(icon: ImageVector, title: String, subtitle: String, action: (() -> Unit)? = null) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(32.dp)) {
        Icon(icon, null, Modifier.size(56.dp), tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(16.dp)); Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
        Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (action != null) { Spacer(Modifier.height(16.dp)); FilledTonalButton(action) { Icon(Icons.Default.Add, null); Spacer(Modifier.width(8.dp)); Text("Add folder") } }
    }
}

@Composable
private fun EmptyInline(title: String, subtitle: String) {
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
        Column(Modifier.padding(18.dp)) { Text(title, fontWeight = FontWeight.Medium); Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}

private fun formatDate(millis: Long): String = if (millis <= 0) "Unknown date" else DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(millis))

private val archiveMimeTypes = arrayOf(
    "application/zip", "application/x-7z-compressed", "application/vnd.rar", "application/x-rar-compressed",
    "application/x-tar", "application/gzip", "application/x-bzip2", "application/x-xz", "application/zstd", "application/octet-stream",
)
