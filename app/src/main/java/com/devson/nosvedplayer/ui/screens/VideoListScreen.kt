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
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import coil.request.CachePolicy
import coil.request.ImageRequest
import com.devson.nosvedplayer.model.Video
import com.devson.nosvedplayer.model.VideoFolder
import com.devson.nosvedplayer.model.ViewSettings
import com.devson.nosvedplayer.viewmodel.FileOperationsViewModel
import com.devson.nosvedplayer.viewmodel.VideoListViewModel
import android.app.Activity
import android.net.Uri
import androidx.activity.result.IntentSenderRequest
import com.devson.nosvedplayer.model.applySort
import com.devson.nosvedplayer.ui.components.RenameDialog
import com.devson.nosvedplayer.ui.components.ViewSettingsBottomSheet
import com.devson.nosvedplayer.utility.SelectionBottomAppBar
import com.devson.nosvedplayer.utility.formatDate
import com.devson.nosvedplayer.utility.formatDuration
import com.devson.nosvedplayer.utility.formatSize

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun VideoListScreen(
    onVideoSelected: (Video, List<Video>) -> Unit,
    onNavigateToSettings: () -> Unit,
    onBack: () -> Unit = {},
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
        topBar = {
            VideoListTopAppBar(
                isSelectionActive = isSelectionActive,
                selectedFolder = selectedFolder,
                selectedFoldersCount = selectedFolders.size,
                totalFoldersCount = sortedFolderKeys.size,
                onClearSelection = { selectedFolders = emptySet() },
                onSelectAll = {
                    val allSelected = selectedFolders.size == sortedFolderKeys.size
                    selectedFolders = if (allSelected) emptySet() else sortedFolderKeys.toSet()
                },
                onBack = onBack,
                onNavigateToSettings = onNavigateToSettings,
                onShowSettings = { showSettingsSheet = true },
                onBackToFolders = { viewModel.selectFolder(null) }
            )
        },
        bottomBar = {
            if (isSelectionActive && selectedFolder == null) {
                SelectionBottomAppBar(
                    selectedFolders = selectedFolders,
                    videosByFolder = videosByFolder,
                    viewSettings = viewSettings,
                    onVideoSelected = onVideoSelected,
                    onClearSelection = { selectedFolders = emptySet() },
                    onMove = { moveLauncher.launch(null) },
                    onCopy = { copyLauncher.launch(null) },
                    onDelete = {
                        val urisToDelete = selectedFolders
                            .flatMap { folder -> videosByFolder[folder] ?: emptyList() }
                            .mapNotNull { video ->
                                try { Uri.parse(video.uri) } catch (_: Exception) { null }
                            }
                        if (urisToDelete.isNotEmpty()) {
                            fileOpsViewModel.deleteVideos(context, urisToDelete)
                            selectedFolders = emptySet()
                        }
                    },
                    onRename = {
                        val folder = selectedFolders.first()
                        val videos = videosByFolder[folder] ?: emptyList()
                        if (videos.size == 1) {
                            renameInputText = videos.first().title.substringBeforeLast(".")
                            showRenameDialog = true
                        } else {
                            Toast.makeText(context, "Rename only works for a folder with a single video.", Toast.LENGTH_SHORT).show()
                        }
                    },
                    onShowInfo = { showInfoDialog = true }
                )
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
                        videos.applySort(viewSettings.sortOrder)
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

// VIDEO LIST CONTENT 
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

// VIDEO THUMBNAIL

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

// VIDEO LIST ITEM 

@Composable
fun VideoListItem(
    video: Video,
    settings: ViewSettings,
    onClick: (Video) -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .clickable { onClick(video) },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.5.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (settings.showThumbnail) {
                VideoThumbnail(
                    uri = video.uri,
                    modifier = Modifier
                        .size(width = 110.dp, height = 64.dp)
                        .clip(RoundedCornerShape(12.dp))
                )
                Spacer(modifier = Modifier.width(16.dp))
            } else {
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.PlayCircle,
                        contentDescription = null,
                        modifier = Modifier.size(32.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
                Spacer(modifier = Modifier.width(16.dp))
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = video.title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(6.dp))
                VideoMetadataRow(video, settings)
            }
        }
    }
}

// VIDEO GRID ITEM

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
            .aspectRatio(if (isDense) 1f else 0.85f)
            .clickable { onClick(video) },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.5.dp)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Thumbnail Section
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(if (isDense) 0.dp else 8.dp) 
            ) {
                val clipShape = if (isDense) RoundedCornerShape(0.dp) else RoundedCornerShape(12.dp)
                
                if (settings.showThumbnail) {
                    VideoThumbnail(
                        uri = video.uri,
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(clipShape)
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(clipShape)
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Filled.PlayCircle,
                            contentDescription = null,
                            modifier = Modifier.size(if (isDense) 32.dp else 48.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                // Duration badge overlay
                if (settings.showDuration) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(6.dp)
                            .background(Color.Black.copy(alpha = 0.75f), RoundedCornerShape(6.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = formatDuration(video.duration), 
                            color = Color.White, 
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }

            // Text Section (hidden if dense grid)
            if (!isDense) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        // Changed to use start, top, end, and bottom explicitly
                        .padding(start = 12.dp, top = 4.dp, end = 12.dp, bottom = 12.dp)
                ) {
                    Text(
                        text = video.title,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
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

// VIDEO METADATA ROW

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


// Top App Bar Component
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun VideoListTopAppBar(
    isSelectionActive: Boolean,
    selectedFolder: VideoFolder?,
    selectedFoldersCount: Int,
    totalFoldersCount: Int,
    onClearSelection: () -> Unit,
    onSelectAll: () -> Unit,
    onBack: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onShowSettings: () -> Unit,
    onBackToFolders: () -> Unit
) {
    if (isSelectionActive && selectedFolder == null) {
        val allSelected = selectedFoldersCount == totalFoldersCount
        TopAppBar(
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainer,
                titleContentColor = MaterialTheme.colorScheme.onSurface,
                navigationIconContentColor = MaterialTheme.colorScheme.onSurface,
                actionIconContentColor = MaterialTheme.colorScheme.onSurfaceVariant
            ),
            navigationIcon = {
                IconButton(onClick = onClearSelection) {
                    Icon(Icons.Filled.Close, contentDescription = "Clear Selection")
                }
            },
            title = {
                Text(
                    "$selectedFoldersCount / $totalFoldersCount",
                    fontWeight = FontWeight.Bold
                )
            },
            actions = {
                IconButton(onClick = onSelectAll) {
                    Icon(
                        imageVector = Icons.Filled.SelectAll,
                        contentDescription = if (allSelected) "Unselect All" else "Select All"
                    )
                }
            }
        )
    } else {
        TopAppBar(
            title = {
                Text(
                    if (selectedFolder != null) selectedFolder.name else "Folders",
                    fontWeight = FontWeight.Bold
                )
            },
            navigationIcon = {
                if (selectedFolder != null) {
                    IconButton(onClick = onBackToFolders) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back to Folders")
                    }
                } else {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back to Home")
                    }
                }
            },
            actions = {
                IconButton(onClick = onShowSettings) {
                    Icon(imageVector = Icons.Filled.Tune, contentDescription = "View Settings")
                }
                IconButton(onClick = onNavigateToSettings) {
                    Icon(imageVector = Icons.Filled.Settings, contentDescription = "Settings")
                }
            }
        )
    }
}