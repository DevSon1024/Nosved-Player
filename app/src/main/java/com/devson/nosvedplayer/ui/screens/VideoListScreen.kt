package com.devson.nosvedplayer.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DriveFileMove
import androidx.compose.material.icons.filled.DriveFileRenameOutline
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import coil.request.CachePolicy
import coil.request.ImageRequest
import com.devson.nosvedplayer.model.SortOrder
import com.devson.nosvedplayer.model.Video
import com.devson.nosvedplayer.model.VideoFolder
import com.devson.nosvedplayer.model.ViewSettings
import com.devson.nosvedplayer.viewmodel.FileOperationsViewModel
import com.devson.nosvedplayer.viewmodel.VideoListViewModel
import java.text.SimpleDateFormat
import java.util.*
import android.app.Activity
import android.net.Uri
import androidx.activity.result.IntentSenderRequest

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun VideoListScreen(
    onVideoSelected: (Video, List<Video>) -> Unit,
    onNavigateToSettings: () -> Unit,
    onFolderStateChange: (Boolean) -> Unit = {},
    onSelectionStateChange: (Boolean) -> Unit = {},
    viewModel: VideoListViewModel = viewModel()
) {
    val context = LocalContext.current
    var hasPermission by remember {
        mutableStateOf(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                ContextCompat.checkSelfPermission(context, Manifest.permission.READ_MEDIA_VIDEO) == PackageManager.PERMISSION_GRANTED
            } else {
                ContextCompat.checkSelfPermission(context, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
            }
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasPermission = isGranted
        if (isGranted) {
            viewModel.loadVideos()
        }
    }

    LaunchedEffect(hasPermission) {
        if (!hasPermission) {
            val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                Manifest.permission.READ_MEDIA_VIDEO
            } else {
                Manifest.permission.READ_EXTERNAL_STORAGE
            }
            permissionLauncher.launch(permission)
        } else {
            viewModel.loadVideos()
        }
    }

    val videosByFolder by viewModel.videosByFolder.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val selectedFolder by viewModel.selectedFolder.collectAsState()
    val viewSettings by viewModel.viewSettings.collectAsState()

    var showSettingsSheet by remember { mutableStateOf(false) }

    //  SELECTION STATE 
    var selectedFolders by remember { mutableStateOf(emptySet<VideoFolder>()) }
    var showInfoDialog by remember { mutableStateOf(false) }

    val sortedFolderKeys = remember(videosByFolder) {
        videosByFolder.keys.toList().sortedBy { it.name.lowercase() }
    }
    val isSelectionActive = selectedFolders.isNotEmpty()

    //  File Operations 
    val fileOpsViewModel: FileOperationsViewModel = viewModel()

    // Handles MediaStore permission dialogs (delete/rename on API 29-30)
    val intentSenderLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            fileOpsViewModel.onPermissionGranted(context)
        }
        fileOpsViewModel.clearPendingIntentSender()
    }

    // Folder picker for Move
    val moveLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) {
            val uris = selectedFolders.flatMap { folder ->
                videosByFolder[folder] ?: emptyList()
            }.mapNotNull { video -> try { Uri.parse(video.uri) } catch (_: Exception) { null } }
            fileOpsViewModel.moveVideos(context, uris, uri)
            selectedFolders = emptySet()
        }
    }

    // Folder picker for Copy
    val copyLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) {
            val uris = selectedFolders.flatMap { folder ->
                videosByFolder[folder] ?: emptyList()
            }.mapNotNull { video -> try { Uri.parse(video.uri) } catch (_: Exception) { null } }
            fileOpsViewModel.copyVideos(context, uris, uri)
            selectedFolders = emptySet()
        }
    }

    //  Dialog state 
    var showRenameDialog by remember { mutableStateOf(false) }
    var renameInputText by remember { mutableStateOf("") }

    //  State change callbacks → MainScreen 
    LaunchedEffect(selectedFolder) { onFolderStateChange(selectedFolder != null) }
    LaunchedEffect(isSelectionActive) { onSelectionStateChange(isSelectionActive) }

    //  Watch pendingIntentSender and launch it automatically 
    val pendingIntentSender by fileOpsViewModel.pendingIntentSender.collectAsState()
    LaunchedEffect(pendingIntentSender) {
        pendingIntentSender?.let { sender ->
            intentSenderLauncher.launch(IntentSenderRequest.Builder(sender).build())
        }
    }

    //  File operation result → Toast + list reload 
    val opResult by fileOpsViewModel.operationResult.collectAsState()
    LaunchedEffect(opResult) {
        opResult?.let { message ->
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
            viewModel.loadVideos()
            fileOpsViewModel.clearResult()
        }
    }

    // Drives progress bar visibility
    val opInProgress by fileOpsViewModel.operationInProgress.collectAsState()

    // Back handler: clears selection first before navigating out
    BackHandler(enabled = selectedFolder != null || isSelectionActive) {
        when {
            isSelectionActive -> selectedFolders = emptySet()
            else -> viewModel.selectFolder(null)
        }
    }

    Scaffold(
        // Outer MainScreen Scaffold (contentWindowInsets=WindowInsets(0)) does NOT consume
        // statusBar insets, so our TopAppBar's default windowInsets=statusBars still work.
        // We set our own contentWindowInsets=0 to avoid double-applying the system nav inset
        // (the outer NavigationBar via its windowInsets already handles it).
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            if (isSelectionActive && selectedFolder == null) {
                // ---- CONTEXTUAL TOP APP BAR ----
                val allSelected = selectedFolders.size == sortedFolderKeys.size
                TopAppBar(
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        navigationIconContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        actionIconContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                    ),
                    navigationIcon = {
                        IconButton(onClick = { selectedFolders = emptySet() }) {
                            Icon(Icons.Filled.Close, contentDescription = "Clear Selection")
                        }
                    },
                    title = {
                        Text(
                            "${selectedFolders.size} / ${sortedFolderKeys.size}",
                            fontWeight = FontWeight.Bold
                        )
                    },
                    actions = {
                        IconButton(onClick = {
                            selectedFolders = if (allSelected) emptySet()
                            else sortedFolderKeys.toSet()
                        }) {
                            Icon(
                                imageVector = Icons.Filled.SelectAll,
                                contentDescription = if (allSelected) "Unselect All" else "Select All"
                            )
                        }
                    }
                )
            } else {
                // ---- STANDARD TOP APP BAR ----
                TopAppBar(
                    title = {
                        Text(
                            if (selectedFolder != null) selectedFolder!!.name else "Folders",
                            fontWeight = FontWeight.Bold
                        )
                    },
                    navigationIcon = {
                        if (selectedFolder != null) {
                            IconButton(onClick = { viewModel.selectFolder(null) }) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                            }
                        }
                    },
                    actions = {
                        IconButton(onClick = { showSettingsSheet = true }) {
                            Icon(
                                imageVector = Icons.Filled.Tune,
                                contentDescription = "View Settings"
                            )
                        }
                        IconButton(onClick = onNavigateToSettings) {
                            Icon(
                                imageVector = Icons.Filled.Settings,
                                contentDescription = "Settings"
                            )
                        }
                    }
                )
            }
        },
        bottomBar = {
            // ---- CONTEXTUAL BOTTOM APP BAR ----
            if (isSelectionActive && selectedFolder == null) {
                BottomAppBar(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Play All
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .clickable {
                                    // Sort folders alphabetically (same as the list view)
                                    val sortedSelectedFolders = selectedFolders
                                        .sortedBy { it.name.lowercase() }
                                    // Build playlist by applying current sort order to each folder's videos
                                    val allVideos = sortedSelectedFolders.flatMap { folder ->
                                        val folderVideos = videosByFolder[folder] ?: emptyList()
                                        when (viewSettings.sortOrder) {
                                            SortOrder.A_TO_Z -> folderVideos.sortedBy { it.title.lowercase() }
                                            SortOrder.Z_TO_A -> folderVideos.sortedByDescending { it.title.lowercase() }
                                            SortOrder.NEWEST_FIRST -> folderVideos.sortedByDescending { it.dateAdded }
                                            SortOrder.OLDEST_FIRST -> folderVideos.sortedBy { it.dateAdded }
                                            SortOrder.LARGEST_FIRST -> folderVideos.sortedByDescending { it.size }
                                            SortOrder.SMALLEST_FIRST -> folderVideos.sortedBy { it.size }
                                        }
                                    }
                                    if (allVideos.isNotEmpty()) {
                                        onVideoSelected(allVideos.first(), allVideos)
                                    }
                                    selectedFolders = emptySet()
                                }
                                .padding(horizontal = 12.dp, vertical = 8.dp)
                        ) {
                            Icon(Icons.Filled.PlayArrow, contentDescription = "Play All")
                            Text("Play All", fontSize = 10.sp)
                        }

                        // Move
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .clickable { moveLauncher.launch(null) }
                                .padding(horizontal = 12.dp, vertical = 8.dp)
                        ) {
                            Icon(Icons.Filled.DriveFileMove, contentDescription = "Move")
                            Text("Move", fontSize = 10.sp)
                        }

                        // Copy
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .clickable { copyLauncher.launch(null) }
                                .padding(horizontal = 12.dp, vertical = 8.dp)
                        ) {
                            Icon(Icons.Filled.ContentCopy, contentDescription = "Copy")
                            Text("Copy", fontSize = 10.sp)
                        }

                        // Delete
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .clickable {
                                    val urisToDelete = selectedFolders
                                        .flatMap { folder -> videosByFolder[folder] ?: emptyList() }
                                        .mapNotNull { video ->
                                            try { Uri.parse(video.uri) } catch (_: Exception) { null }
                                        }
                                    if (urisToDelete.isNotEmpty()) {
                                        fileOpsViewModel.deleteVideos(context, urisToDelete)
                                        selectedFolders = emptySet()
                                    }
                                }
                                .padding(horizontal = 12.dp, vertical = 8.dp)
                        ) {
                            Icon(Icons.Filled.Delete, contentDescription = "Delete")
                            Text("Delete", fontSize = 10.sp)
                        }

                        // Rename (single selection only)
                        if (selectedFolders.size == 1) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier
                                    .clickable {
                                        val folder = selectedFolders.first()
                                        val videos = videosByFolder[folder] ?: emptyList()
                                        if (videos.size == 1) {
                                            renameInputText = videos.first().title
                                                .substringBeforeLast(".")
                                            showRenameDialog = true
                                        } else {
                                            Toast.makeText(
                                                context,
                                                "Rename only works for a folder with a single video.",
                                                Toast.LENGTH_SHORT
                                            ).show()
                                        }
                                    }
                                    .padding(horizontal = 12.dp, vertical = 8.dp)
                            ) {
                                Icon(Icons.Filled.DriveFileRenameOutline, contentDescription = "Rename")
                                Text("Rename", fontSize = 10.sp)
                            }
                        }

                        // Information
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .clickable { showInfoDialog = true }
                                .padding(horizontal = 12.dp, vertical = 8.dp)
                        ) {
                            Icon(Icons.Filled.Info, contentDescription = "Info")
                            Text("Info", fontSize = 10.sp)
                        }
                    }
                }
            }
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Progress bar shown at top while a file operation is running
            if (opInProgress) {
                LinearProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.TopCenter)
                )
            }

            if (!hasPermission) {
                Column(
                    modifier = Modifier.align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("Storage permission is required to find videos.")
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(onClick = {
                        val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            Manifest.permission.READ_MEDIA_VIDEO
                        } else {
                            Manifest.permission.READ_EXTERNAL_STORAGE
                        }
                        permissionLauncher.launch(permission)
                    }) {
                        Text("Grant Permission")
                    }
                }
            } else if (isLoading) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            } else {
                if (selectedFolder == null) {
                    FolderListContent(
                        folders = videosByFolder,
                        settings = viewSettings,
                        selectedFolders = selectedFolders,
                        onFolderClick = { folder ->
                            if (isSelectionActive) {
                                selectedFolders = if (folder in selectedFolders) {
                                    selectedFolders - folder
                                } else {
                                    selectedFolders + folder
                                }
                            } else {
                                viewModel.selectFolder(folder)
                            }
                        },
                        onFolderLongClick = { folder ->
                            selectedFolders = selectedFolders + folder
                        }
                    )
                } else {
                    val videos = videosByFolder[selectedFolder] ?: emptyList()
                    val sortedVideos = remember(videos, viewSettings.sortOrder) {
                        when (viewSettings.sortOrder) {
                            SortOrder.A_TO_Z -> videos.sortedBy { it.title.lowercase() }
                            SortOrder.Z_TO_A -> videos.sortedByDescending { it.title.lowercase() }
                            SortOrder.NEWEST_FIRST -> videos.sortedByDescending { it.dateAdded }
                            SortOrder.OLDEST_FIRST -> videos.sortedBy { it.dateAdded }
                            SortOrder.LARGEST_FIRST -> videos.sortedByDescending { it.size }
                            SortOrder.SMALLEST_FIRST -> videos.sortedBy { it.size }
                        }
                    }

                    VideoListContent(
                        videos = sortedVideos,
                        settings = viewSettings,
                        onVideoClick = { video -> onVideoSelected(video, sortedVideos) }
                    )
                }
            }
        }
    }

    // ---- FOLDER INFO DIALOG ----
    if (showInfoDialog && isSelectionActive) {
        FolderInfoDialog(
            selectedFolders = selectedFolders,
            videosByFolder = videosByFolder,
            onDismiss = { showInfoDialog = false }
        )
    }

    // ---- RENAME DIALOG ----
    if (showRenameDialog && selectedFolders.size == 1) {
        val folder = selectedFolders.first()
        val video = (videosByFolder[folder] ?: emptyList()).firstOrNull()
        RenameDialog(
            initialName = renameInputText,
            onConfirm = { newName ->
                if (video != null) {
                    fileOpsViewModel.renameVideo(context, Uri.parse(video.uri), newName)
                }
                showRenameDialog = false
                selectedFolders = emptySet()
            },
            onDismiss = { showRenameDialog = false }
        )
    }

    if (showSettingsSheet) {
        ViewSettingsBottomSheet(
            settings = viewSettings,
            isFolderView = selectedFolder == null,
            onDismiss = { showSettingsSheet = false },
            viewModel = viewModel
        )
    }
}

// 
// FOLDER INFO DIALOG
// 

@Composable
fun FolderInfoDialog(
    selectedFolders: Set<VideoFolder>,
    videosByFolder: Map<VideoFolder, List<Video>>,
    onDismiss: () -> Unit
) {
    val allVideos = selectedFolders.flatMap { folder -> videosByFolder[folder] ?: emptyList() }
    val totalVideos = allVideos.size
    val totalSize = allVideos.sumOf { it.size }
    val location = if (selectedFolders.size == 1) {
        selectedFolders.first().id
    } else {
        "${selectedFolders.size} folders selected"
    }
    val oldestDate = if (selectedFolders.size == 1) {
        allVideos.minOfOrNull { it.dateAdded }
    } else null

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("OK") }
        },
        title = {
            Text(
                if (selectedFolders.size == 1) selectedFolders.first().name else "Selected Folders",
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                InfoRow(label = "Total Videos", value = "$totalVideos")
                InfoRow(label = "Total Size", value = formatSize(totalSize))
                InfoRow(label = "Location", value = location)
                if (oldestDate != null && oldestDate > 0L) {
                    InfoRow(label = "Creation Date", value = formatDate(oldestDate))
                }
            }
        }
    )
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.weight(1.5f)
        )
    }
}

//  RENAME DIALOG 

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RenameDialog(
    initialName: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var text by remember { mutableStateOf(initialName) }
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = { if (text.isNotBlank()) onConfirm(text.trim()) },
                enabled = text.isNotBlank()
            ) { Text("Rename") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
        title = { Text("Rename Video", fontWeight = FontWeight.Bold) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                label = { Text("New file name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        }
    )
}

// FOLDER LIST CONTENT

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FolderListContent(
    folders: Map<VideoFolder, List<Video>>,
    settings: ViewSettings,
    selectedFolders: Set<VideoFolder>,
    onFolderClick: (VideoFolder) -> Unit,
    onFolderLongClick: (VideoFolder) -> Unit
) {
    val haptic = LocalHapticFeedback.current
    val sortedFolders = remember(folders) { folders.keys.toList().sortedBy { it.name.lowercase() } }

    if (settings.isGrid) {
        LazyVerticalGrid(
            columns = GridCells.Fixed(settings.gridColumns),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(sortedFolders) { folder ->
                FolderGridItem(
                    folder = folder,
                    videos = folders[folder] ?: emptyList(),
                    settings = settings,
                    isSelected = folder in selectedFolders,
                    onClick = { onFolderClick(folder) },
                    onLongClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onFolderLongClick(folder)
                    }
                )
            }
        }
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 16.dp)
        ) {
            items(sortedFolders) { folder ->
                FolderListItem(
                    folder = folder,
                    videos = folders[folder] ?: emptyList(),
                    settings = settings,
                    isSelected = folder in selectedFolders,
                    onClick = { onFolderClick(folder) },
                    onLongClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onFolderLongClick(folder)
                    }
                )
            }
        }
    }
}

// FOLDER LIST ITEM 

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FolderListItem(
    folder: VideoFolder,
    videos: List<Video>,
    settings: ViewSettings,
    isSelected: Boolean = false,
    onClick: () -> Unit,
    onLongClick: () -> Unit = {}
) {
    val bgColor = if (isSelected)
        MaterialTheme.colorScheme.primaryContainer
    else
        Color.Transparent

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(bgColor)
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = Icons.Filled.Folder,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primary
            )
            if (isSelected) {
                Box(
                    modifier = Modifier
                        .size(18.dp)
                        .align(Alignment.TopEnd)
                        .background(MaterialTheme.colorScheme.primary, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.Check,
                        contentDescription = "Selected",
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(12.dp)
                    )
                }
            }
        }
        Spacer(modifier = Modifier.width(16.dp))
        Column {
            Text(
                text = folder.name,
                style = MaterialTheme.typography.titleMedium
            )
            FolderMetadataRow(videos, settings)
        }
    }
}

// 
// FOLDER GRID ITEM
// 

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FolderGridItem(
    folder: VideoFolder,
    videos: List<Video>,
    settings: ViewSettings,
    isSelected: Boolean = false,
    onClick: () -> Unit,
    onLongClick: () -> Unit = {}
) {
    val isDense = settings.gridColumns >= 3
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(if (isDense) 1f else 0.8f)
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            ),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected)
                MaterialTheme.colorScheme.primaryContainer
            else
                MaterialTheme.colorScheme.surfaceVariant
        ),
        border = if (isSelected) {
            androidx.compose.foundation.BorderStroke(
                2.dp,
                MaterialTheme.colorScheme.primary
            )
        } else null
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Column(modifier = Modifier.fillMaxSize()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.Folder,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    if (isDense && settings.showFolderVideoCount) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(4.dp)
                                .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(4.dp))
                                .padding(horizontal = 4.dp, vertical = 2.dp)
                        ) {
                            Text(text = "${videos.size}", color = Color.White, fontSize = 10.sp)
                        }
                    }
                }

                if (!isDense) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(8.dp)
                    ) {
                        Text(
                            text = folder.name,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        FolderMetadataRow(videos, settings, isGrid = true)
                    }
                }
            }

            // Selection checkmark overlay
            if (isSelected) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(6.dp)
                        .size(22.dp)
                        .background(MaterialTheme.colorScheme.primary, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.Check,
                        contentDescription = "Selected",
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
        }
    }
}

// 
// FOLDER METADATA ROW
// 

@Composable
fun FolderMetadataRow(videos: List<Video>, settings: ViewSettings, isGrid: Boolean = false) {
    val metaItems = mutableListOf<String>()

    if (settings.showFolderVideoCount) metaItems.add("${videos.size} videos")
    if (settings.showFolderSize) {
        val totalSize = videos.sumOf { it.size }
        metaItems.add(formatSize(totalSize))
    }
    if (settings.showFolderDate) {
        val oldestVideoDate = videos.minOfOrNull { it.dateAdded } ?: 0L
        if (oldestVideoDate > 0) metaItems.add(formatDate(oldestVideoDate))
    }

    if (metaItems.isNotEmpty()) {
        val text = metaItems.filter { it.isNotEmpty() }.joinToString(" • ")
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = if (isGrid) 2 else 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

// 
// VIDEO LIST CONTENT
// 

@Composable
fun VideoListContent(
    videos: List<Video>,
    settings: ViewSettings,
    onVideoClick: (Video) -> Unit
) {
    if (settings.isGrid) {
        LazyVerticalGrid(
            columns = GridCells.Fixed(settings.gridColumns),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(videos) { video ->
                VideoGridItem(video, settings, onVideoClick)
            }
        }
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 16.dp)
        ) {
            items(videos) { video ->
                VideoListItem(video, settings, onVideoClick)
            }
        }
    }
}

// 
// VIDEO THUMBNAIL
// 

@Composable
fun VideoThumbnail(
    uri: String,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .background(MaterialTheme.colorScheme.surfaceVariant)
    ) {
        AsyncImage(
            model = ImageRequest.Builder(LocalContext.current)
                .data(uri)
                .size(512, 512)
                .memoryCachePolicy(CachePolicy.ENABLED)
                .diskCachePolicy(CachePolicy.ENABLED)
                .crossfade(true)
                .build(),
            contentDescription = "Video Thumbnail",
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.2f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Filled.PlayCircle,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.8f),
                modifier = Modifier.size(32.dp)
            )
        }
    }
}

// 
// VIDEO LIST ITEM
// 

@Composable
fun VideoListItem(
    video: Video,
    settings: ViewSettings,
    onClick: (Video) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick(video) }
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (settings.showThumbnail) {
            VideoThumbnail(
                uri = video.uri,
                modifier = Modifier
                    .size(width = 100.dp, height = 56.dp)
                    .clip(RoundedCornerShape(8.dp))
            )
            Spacer(modifier = Modifier.width(16.dp))
        } else {
            Icon(
                imageVector = Icons.Filled.PlayCircle,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.secondary
            )
            Spacer(modifier = Modifier.width(16.dp))
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = video.title,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(4.dp))
            VideoMetadataRow(video, settings)
        }
    }
}

// 
// VIDEO GRID ITEM
// 

@Composable
fun VideoGridItem(
    video: Video,
    settings: ViewSettings,
    onClick: (Video) -> Unit
) {
    val isDense = settings.gridColumns >= 3
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(if (isDense) 1f else 0.8f)
            .clickable { onClick(video) },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
                if (settings.showThumbnail) {
                    VideoThumbnail(
                        uri = video.uri,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Filled.PlayCircle,
                            contentDescription = null,
                            modifier = Modifier.size(48.dp),
                            tint = MaterialTheme.colorScheme.secondary
                        )
                    }
                }

                if (isDense && settings.showDuration) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(4.dp)
                            .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(4.dp))
                            .padding(horizontal = 4.dp, vertical = 2.dp)
                    ) {
                        Text(formatDuration(video.duration), color = Color.White, fontSize = 10.sp)
                    }
                }
            }

            if (!isDense) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp)
                ) {
                    Text(
                        text = video.title,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    VideoMetadataRow(video, settings, isGrid = true)
                }
            }
        }
    }
}

// 
// VIDEO METADATA ROW
// 

@Composable
fun VideoMetadataRow(video: Video, settings: ViewSettings, isGrid: Boolean = false) {
    val metaItems = mutableListOf<String>()

    if (settings.showDuration) metaItems.add(formatDuration(video.duration))
    if (settings.showSize) metaItems.add(formatSize(video.size))
    if (settings.showDate && video.dateAdded > 0) metaItems.add(formatDate(video.dateAdded))
    if (settings.showFileExtension) metaItems.add(video.uri.substringAfterLast('.', ""))

    if (metaItems.isNotEmpty()) {
        val text = metaItems.filter { it.isNotEmpty() }.joinToString(" • ")
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = if (isGrid) 2 else 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

// 
// VIEW SETTINGS BOTTOM SHEET
// 

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ViewSettingsBottomSheet(
    settings: ViewSettings,
    isFolderView: Boolean,
    onDismiss: () -> Unit,
    viewModel: VideoListViewModel
) {
    val sheetState = rememberModalBottomSheetState()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            Text(
                "View Settings",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            // Layout Toggle
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Use Grid View", style = MaterialTheme.typography.bodyLarge)
                Switch(
                    checked = settings.isGrid,
                    onCheckedChange = { viewModel.updateIsGrid(it) }
                )
            }

            if (settings.isGrid) {
                Spacer(modifier = Modifier.height(8.dp))
                Text("Grid Columns: ${settings.gridColumns}", style = MaterialTheme.typography.bodyMedium)
                Slider(
                    value = settings.gridColumns.toFloat(),
                    onValueChange = { viewModel.updateGridColumns(it.toInt()) },
                    valueRange = 1f..6f,
                    steps = 4
                )
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

            // Sorting
            var sortExpanded by remember { mutableStateOf(false) }
            Text("Sort By", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Box(modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                OutlinedButton(
                    onClick = { sortExpanded = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(formatSortOrder(settings.sortOrder))
                }
                DropdownMenu(
                    expanded = sortExpanded,
                    onDismissRequest = { sortExpanded = false },
                    modifier = Modifier.fillMaxWidth(0.9f)
                ) {
                    SortOrder.values().forEach { order ->
                        DropdownMenuItem(
                            text = { Text(formatSortOrder(order)) },
                            onClick = {
                                viewModel.updateSortOrder(order)
                                sortExpanded = false
                            },
                            trailingIcon = if (settings.sortOrder == order) {
                                { Icon(Icons.Filled.Check, null) }
                            } else null
                        )
                    }
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

            if (!isFolderView) {
                Text("Show Metadata", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                MetadataToggle("Thumbnail", settings.showThumbnail) { viewModel.updateShowThumbnail(it) }
                MetadataToggle("Duration", settings.showDuration) { viewModel.updateShowDuration(it) }
                MetadataToggle("File Size", settings.showSize) { viewModel.updateShowSize(it) }
                MetadataToggle("Date Added", settings.showDate) { viewModel.updateShowDate(it) }
                MetadataToggle("File Extension", settings.showFileExtension) { viewModel.updateShowFileExtension(it) }
            } else {
                Text("Folder Metadata", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                MetadataToggle("Video Count", settings.showFolderVideoCount) { viewModel.updateShowFolderVideoCount(it) }
                MetadataToggle("Folder Size", settings.showFolderSize) { viewModel.updateShowFolderSize(it) }
                MetadataToggle("Created At", settings.showFolderDate) { viewModel.updateShowFolderDate(it) }
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

// 
// METADATA TOGGLE ROW
// 

@Composable
fun MetadataToggle(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge)
        Checkbox(checked = checked, onCheckedChange = onCheckedChange)
    }
}

// 
// FORMATTING UTILITIES
// 

private fun formatSortOrder(order: SortOrder): String {
    return when (order) {
        SortOrder.A_TO_Z -> "A to Z"
        SortOrder.Z_TO_A -> "Z to A"
        SortOrder.NEWEST_FIRST -> "Newest First"
        SortOrder.OLDEST_FIRST -> "Oldest First"
        SortOrder.LARGEST_FIRST -> "Largest First"
        SortOrder.SMALLEST_FIRST -> "Smallest First"
    }
}

private fun formatDuration(durationMs: Long): String {
    val df = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
    df.timeZone = TimeZone.getTimeZone("UTC")
    return if (durationMs >= 3600000) {
        df.format(Date(durationMs))
    } else {
        val dfShort = SimpleDateFormat("mm:ss", Locale.getDefault())
        dfShort.timeZone = TimeZone.getTimeZone("UTC")
        dfShort.format(Date(durationMs))
    }
}

private fun formatSize(sizeBytes: Long): String {
    if (sizeBytes <= 0) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    val digitGroups = (Math.log10(sizeBytes.toDouble()) / Math.log10(1024.0)).toInt()
    return String.format(Locale.getDefault(), "%.1f %s", sizeBytes / Math.pow(1024.0, digitGroups.toDouble()), units[digitGroups])
}

private fun formatDate(epochMs: Long): String {
    val df = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
    return df.format(Date(epochMs))
}
