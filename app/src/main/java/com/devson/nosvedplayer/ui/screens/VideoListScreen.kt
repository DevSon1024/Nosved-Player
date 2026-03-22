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
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.filled.*
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import coil.request.CachePolicy
import coil.request.ImageRequest
import com.devson.nosvedplayer.model.LayoutMode
import com.devson.nosvedplayer.model.SortDirection
import com.devson.nosvedplayer.model.SortField
import com.devson.nosvedplayer.model.Video
import com.devson.nosvedplayer.model.VideoFolder
import com.devson.nosvedplayer.model.ViewMode
import com.devson.nosvedplayer.model.ViewSettings
import com.devson.nosvedplayer.viewmodel.FileOperationsViewModel
import com.devson.nosvedplayer.viewmodel.VideoListViewModel
import android.app.Activity
import android.net.Uri
import androidx.activity.result.IntentSenderRequest
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Scaffold
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
    val explorerNodes by viewModel.explorerNodes.collectAsState()
    val currentExplorerPath by viewModel.currentExplorerPath.collectAsState()

    var showSettingsSheet by remember { mutableStateOf(false) }

    //  SELECTION STATE 
    var selectedFolders by remember { mutableStateOf(emptySet<VideoFolder>()) }
    var selectedVideos by remember { mutableStateOf(emptySet<Video>()) }
    var showInfoBottomSheet by remember { mutableStateOf(false) }

    val sortedFolderKeys = remember(videosByFolder, viewSettings.sortField, viewSettings.sortDirection) {
        val keys = videosByFolder.keys.toList()
        keys.applyFolderSort(videosByFolder, viewSettings.sortField, viewSettings.sortDirection)
    }
    val isSelectionActive = selectedFolders.isNotEmpty() || selectedVideos.isNotEmpty()

    // Hoisted scroll states — survive recomposition and view-mode toggling
    val folderListState = rememberLazyListState()
    val folderGridState = rememberLazyGridState()
    var currentFolderId by rememberSaveable { mutableStateOf<String?>(null) }
    val videoListState = rememberLazyListState()
    val videoGridState = rememberLazyGridState()

    // Reset video scroll position when entering a different folder
    LaunchedEffect(selectedFolder) {
        if (selectedFolder?.name != currentFolderId) {
            videoListState.scrollToItem(0)
            videoGridState.scrollToItem(0)
            currentFolderId = selectedFolder?.name
        }
    }

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
            val uris = if (selectedFolder != null) {
                selectedVideos.mapNotNull { video -> try { Uri.parse(video.uri) } catch (_: Exception) { null } }
            } else {
                selectedFolders.flatMap { folder ->
                    videosByFolder[folder] ?: emptyList()
                }.mapNotNull { video -> try { Uri.parse(video.uri) } catch (_: Exception) { null } }
            }
            fileOpsViewModel.moveVideos(context, uris, uri)
            selectedFolders = emptySet()
            selectedVideos = emptySet()
        }
    }

    // Folder picker for Copy
    val copyLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) {
            val uris = if (selectedFolder != null) {
                selectedVideos.mapNotNull { video -> try { Uri.parse(video.uri) } catch (_: Exception) { null } }
            } else {
                selectedFolders.flatMap { folder ->
                    videosByFolder[folder] ?: emptyList()
                }.mapNotNull { video -> try { Uri.parse(video.uri) } catch (_: Exception) { null } }
            }
            fileOpsViewModel.copyVideos(context, uris, uri)
            selectedFolders = emptySet()
            selectedVideos = emptySet()
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
    BackHandler(enabled = selectedFolder != null || isSelectionActive || (viewSettings.viewMode == ViewMode.FOLDERS && currentExplorerPath != null)) {
        when {
            selectedVideos.isNotEmpty() -> selectedVideos = emptySet()
            selectedFolders.isNotEmpty() -> selectedFolders = emptySet()
            selectedFolder != null -> viewModel.selectFolder(null)
            viewSettings.viewMode == ViewMode.FOLDERS && currentExplorerPath != null -> viewModel.navigateExplorerUp()
            else -> {}
        }
    }
    Scaffold(
        topBar = {
            val titleText = when (viewSettings.viewMode) {
                ViewMode.ALL_FOLDERS -> selectedFolder?.name
                ViewMode.FILES -> "All Files"
                ViewMode.FOLDERS -> currentExplorerPath?.substringBeforeLast('/')?.substringAfterLast('/') ?: "Folders"
            }
            VideoListTopAppBar(
                isSelectionActive = isSelectionActive,
                titleText = titleText,
                selectedCount = selectedVideos.size + selectedFolders.size,
                totalCount = when (viewSettings.viewMode) {
                    ViewMode.ALL_FOLDERS -> if (selectedFolder != null) (videosByFolder[selectedFolder] ?: emptyList()).size else sortedFolderKeys.size
                    ViewMode.FILES -> videosByFolder.values.flatten().size
                    ViewMode.FOLDERS -> explorerNodes.first.size + explorerNodes.second.size
                },
                showBackButton = selectedFolder != null || (viewSettings.viewMode == ViewMode.FOLDERS && currentExplorerPath != null),
                onClearSelection = { 
                    selectedFolders = emptySet()
                    selectedVideos = emptySet()
                },
                onSelectAll = {
                    when (viewSettings.viewMode) {
                        ViewMode.ALL_FOLDERS -> {
                            if (selectedFolder != null) {
                                val allVideos = videosByFolder[selectedFolder] ?: emptyList()
                                selectedVideos = if (selectedVideos.size == allVideos.size) emptySet() else allVideos.toSet()
                            } else {
                                selectedFolders = if (selectedFolders.size == sortedFolderKeys.size) emptySet() else sortedFolderKeys.toSet()
                            }
                        }
                        ViewMode.FILES -> {
                            val allVideos = videosByFolder.values.flatten()
                            selectedVideos = if (selectedVideos.size == allVideos.size) emptySet() else allVideos.toSet()
                        }
                        ViewMode.FOLDERS -> {
                            val allExpVideos = explorerNodes.second
                            val allExpFolders = explorerNodes.first
                            if (selectedVideos.size + selectedFolders.size == allExpVideos.size + allExpFolders.size) {
                                selectedVideos = emptySet()
                                selectedFolders = emptySet()
                            } else {
                                selectedVideos = allExpVideos.toSet()
                                selectedFolders = allExpFolders.toSet()
                            }
                        }
                    }
                },
                onBack = onBack,
                onNavigateToSettings = onNavigateToSettings,
                onShowSettings = { showSettingsSheet = true },
                onBackToFolders = { 
                    if (viewSettings.viewMode == ViewMode.FOLDERS && currentExplorerPath != null) {
                        viewModel.navigateExplorerUp()
                    } else {
                        viewModel.selectFolder(null)
                    }
                }
            )
        },
        bottomBar = {
            if (isSelectionActive) {
                if (selectedFolder == null) {
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
                        onShowInfo = { showInfoBottomSheet = true }
                    )
                } else {
                    // Contextual Bottom Bar for Videos
                    VideoSelectionBottomAppBar(
                        selectedVideos = selectedVideos,
                        onPlayAll = {
                            val allVideos = (videosByFolder[selectedFolder] ?: emptyList()).applySort(viewSettings.sortField, viewSettings.sortDirection)
                            onVideoSelected(selectedVideos.first(), allVideos)
                            selectedVideos = emptySet()
                        },
                        onMove = { moveLauncher.launch(null) },
                        onCopy = { copyLauncher.launch(null) },
                        onDelete = {
                            val urisToDelete = selectedVideos.mapNotNull { try { Uri.parse(it.uri) } catch (_: Exception) { null } }
                            if (urisToDelete.isNotEmpty()) {
                                fileOpsViewModel.deleteVideos(context, urisToDelete)
                                selectedVideos = emptySet()
                            }
                        },
                        onRename = {
                            val video = selectedVideos.first()
                            renameInputText = video.title.substringBeforeLast(".")
                            showRenameDialog = true
                        },
                        onShowInfo = { showInfoBottomSheet = true }
                    )
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
                when (viewSettings.viewMode) {
                    ViewMode.ALL_FOLDERS -> {
                        if (selectedFolder == null) {
                            FolderListContent(
                                folders = videosByFolder,
                                settings = viewSettings,
                                selectedFolders = selectedFolders,
                                onFolderClick = { folder ->
                                    if (isSelectionActive) {
                                        selectedFolders = if (folder in selectedFolders) selectedFolders - folder else selectedFolders + folder
                                    } else {
                                        viewModel.selectFolder(folder)
                                    }
                                },
                                onFolderLongClick = { folder ->
                                    selectedFolders = selectedFolders + folder
                                },
                                listState = folderListState,
                                gridState = folderGridState
                            )
                        } else {
                            val videos = videosByFolder[selectedFolder] ?: emptyList()
                            val sortedVideos = remember(videos, viewSettings.sortField, viewSettings.sortDirection) {
                                videos.applySort(viewSettings.sortField, viewSettings.sortDirection)
                            }
                            VideoListContent(
                                videos = sortedVideos,
                                settings = viewSettings,
                                selectedVideos = selectedVideos,
                                onVideoClick = { video ->
                                    if (selectedVideos.isNotEmpty()) {
                                        selectedVideos = if (video in selectedVideos) selectedVideos - video else selectedVideos + video
                                    } else {
                                        onVideoSelected(video, sortedVideos)
                                    }
                                },
                                onVideoLongClick = { video ->
                                    selectedVideos = selectedVideos + video
                                },
                                listState = videoListState,
                                gridState = videoGridState
                            )
                        }
                    }
                    ViewMode.FILES -> {
                        val allVideos = remember(videosByFolder) { videosByFolder.values.flatten() }
                        val sortedVideos = remember(allVideos, viewSettings.sortField, viewSettings.sortDirection) {
                            allVideos.applySort(viewSettings.sortField, viewSettings.sortDirection)
                        }
                        VideoListContent(
                            videos = sortedVideos,
                            settings = viewSettings,
                            selectedVideos = selectedVideos,
                            onVideoClick = { video ->
                                if (selectedVideos.isNotEmpty()) {
                                    selectedVideos = if (video in selectedVideos) selectedVideos - video else selectedVideos + video
                                } else {
                                    onVideoSelected(video, sortedVideos)
                                }
                            },
                            onVideoLongClick = { video ->
                                selectedVideos = selectedVideos + video
                            },
                            listState = videoListState,
                            gridState = videoGridState
                        )
                    }
                    ViewMode.FOLDERS -> {
                        val (expFolders, expVideos) = explorerNodes
                        val sortedExpVideos = remember(expVideos, viewSettings.sortField, viewSettings.sortDirection) {
                            expVideos.applySort(viewSettings.sortField, viewSettings.sortDirection)
                        }
                        // Need a map to resolve explorer nodes content
                        val mappedVideosByFolder = remember(videosByFolder, expFolders) {
                            val allVideos = videosByFolder.values.flatten()
                            expFolders.associateWith { folder ->
                                allVideos.filter { it.path.startsWith(folder.id) }
                            }
                        }
                        val sortedExpFolders = remember(expFolders, viewSettings.sortField, viewSettings.sortDirection, mappedVideosByFolder) {
                            expFolders.applyFolderSort(mappedVideosByFolder, viewSettings.sortField, viewSettings.sortDirection)
                        }
                        val allVideosForSize = remember(videosByFolder) { videosByFolder.values.flatten() }

                        ExplorerListContent(
                            folders = sortedExpFolders,
                            videos = sortedExpVideos,
                            allVideosForSize = allVideosForSize,
                            settings = viewSettings,
                            selectedFolders = selectedFolders,
                            selectedVideos = selectedVideos,
                            onFolderClick = { folder ->
                                if (isSelectionActive) {
                                    selectedFolders = if (folder in selectedFolders) selectedFolders - folder else selectedFolders + folder
                                } else {
                                    viewModel.navigateToExplorerPath(folder.id)
                                }
                            },
                            onFolderLongClick = { folder ->
                                selectedFolders = selectedFolders + folder
                            },
                            onVideoClick = { video ->
                                if (isSelectionActive) {
                                    selectedVideos = if (video in selectedVideos) selectedVideos - video else selectedVideos + video
                                } else {
                                    onVideoSelected(video, sortedExpVideos)
                                }
                            },
                            onVideoLongClick = { video ->
                                selectedVideos = selectedVideos + video
                            },
                            listState = folderListState,
                            gridState = folderGridState
                        )
                    }
                }
            }
        }
    }

    // ---- INFORMATION BOTTOM SHEET ----
    if (showInfoBottomSheet && isSelectionActive) {
        val videosToShow = if (selectedFolder != null) selectedVideos else {
            selectedFolders.flatMap { videosByFolder[it] ?: emptyList() }.toSet()
        }
        InformationBottomSheet(
            selectedVideos = videosToShow,
            onDismiss = { showInfoBottomSheet = false }
        )
    }

    // ---- RENAME DIALOG ----
    if (showRenameDialog && (selectedFolders.size == 1 || selectedVideos.size == 1)) {
        val video = if (selectedFolder != null) {
            selectedVideos.firstOrNull()
        } else {
            val folder = selectedFolders.first()
            (videosByFolder[folder] ?: emptyList()).firstOrNull()
        }
        RenameDialog(
            initialName = renameInputText,
            onConfirm = { newName ->
                if (video != null) {
                    fileOpsViewModel.renameVideo(context, Uri.parse(video.uri), newName)
                }
                showRenameDialog = false
                selectedFolders = emptySet()
                selectedVideos = emptySet()
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
    selectedVideos: Set<Video>,
    onVideoClick: (Video) -> Unit,
    onVideoLongClick: (Video) -> Unit,
    listState: LazyListState = rememberLazyListState(),
    gridState: LazyGridState = rememberLazyGridState()
) {
    if (settings.layoutMode == LayoutMode.GRID) {
        LazyVerticalGrid(
            columns = GridCells.Fixed(settings.gridColumns),
            state = gridState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(videos) { video ->
                VideoGridItem(
                    video = video,
                    settings = settings,
                    isSelected = video in selectedVideos,
                    onClick = { onVideoClick(video) },
                    onLongClick = { onVideoLongClick(video) }
                )
            }
        }
    } else {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 16.dp)
        ) {
            items(videos) { video ->
                VideoListItem(
                    video = video,
                    settings = settings,
                    isSelected = video in selectedVideos,
                    onClick = { onVideoClick(video) },
                    onLongClick = { onVideoLongClick(video) }
                )
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun VideoListItem(
    video: Video,
    settings: ViewSettings,
    isSelected: Boolean = false,
    onClick: (Video) -> Unit,
    onLongClick: (Video) -> Unit
) {
    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current
    val bgColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerLowest

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .combinedClickable(
                onClick = { onClick(video) },
                onLongClick = {
                    haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                    onLongClick(video)
                }
            ),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = bgColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.5.dp),
        border = if (isSelected) androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary) else null
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(modifier = Modifier.size(width = 110.dp, height = 64.dp)) {
                if (settings.showThumbnail) {
                    VideoThumbnail(
                        uri = video.uri,
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(RoundedCornerShape(12.dp))
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
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
                }
                
                if (isSelected) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.4f), RoundedCornerShape(12.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .background(MaterialTheme.colorScheme.primary, androidx.compose.foundation.shape.CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Filled.Check,
                                contentDescription = "Selected",
                                tint = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
                // Duration badge overlay
                if (settings.showLength && settings.displayLengthOverThumbnail && !isSelected) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(bottom = 6.dp, end = 6.dp)
                            .background(Color.Black.copy(alpha = 0.75f), RoundedCornerShape(4.dp))
                            .padding(horizontal = 4.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = formatDuration(video.duration), 
                            color = Color.White, 
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
            
            Spacer(modifier = Modifier.width(16.dp))

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

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun VideoGridItem(
    video: Video,
    settings: ViewSettings,
    isSelected: Boolean = false,
    onClick: (Video) -> Unit,
    onLongClick: (Video) -> Unit
) {
    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current
    val isDense = settings.gridColumns >= 3
    val bgColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerLowest
    
    if (settings.gridColumns == 1) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp)
                .combinedClickable(
                    onClick = { onClick(video) },
                    onLongClick = {
                        haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                        onLongClick(video)
                    }
                ),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = bgColor),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.5.dp),
            border = if (isSelected) androidx.compose.foundation.BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                // Top Section (Thumbnail, 16:9)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(16f / 9f)
                ) {
                    if (settings.showThumbnail) {
                        VideoThumbnail(
                            uri = video.uri,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(MaterialTheme.colorScheme.surfaceVariant),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Filled.PlayCircle,
                                contentDescription = null,
                                modifier = Modifier.size(48.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }

                    if (isSelected) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .background(MaterialTheme.colorScheme.primary, androidx.compose.foundation.shape.CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Filled.Check,
                                    contentDescription = "Selected",
                                    tint = MaterialTheme.colorScheme.onPrimary,
                                    modifier = Modifier.size(28.dp)
                                )
                            }
                        }
                    }

                    // Duration badge overlay
                    if (settings.showLength && settings.displayLengthOverThumbnail && !isSelected) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(8.dp)
                                .background(Color.Black.copy(alpha = 0.75f), RoundedCornerShape(4.dp))
                                .padding(horizontal = 4.dp, vertical = 2.dp)
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

                // Bottom Section
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp)
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = video.title,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        VideoMetadataRow(video, settings, isGrid = false)
                    }
                }
            }
        }
    } else {
        Card(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(if (isDense) 1f else 0.85f)
            .combinedClickable(
                onClick = { onClick(video) },
                onLongClick = {
                    haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                    onLongClick(video)
                }
            ),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = bgColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.5.dp),
        border = if (isSelected) androidx.compose.foundation.BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null
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

                // Selection Overlay
                if (isSelected) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size(if (isDense) 32.dp else 48.dp)
                                .background(MaterialTheme.colorScheme.primary, androidx.compose.foundation.shape.CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Filled.Check,
                                contentDescription = "Selected",
                                tint = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.size(if (isDense) 20.dp else 28.dp)
                            )
                        }
                    }
                }

                // Duration badge overlay
                if (settings.showLength && settings.displayLengthOverThumbnail && !isSelected) {
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
}

// VIDEO METADATA ROW

@Composable
fun VideoMetadataRow(video: Video, settings: ViewSettings, isGrid: Boolean = false) {
    val metaItems = mutableListOf<String>()

    if (settings.showLength && !settings.displayLengthOverThumbnail) metaItems.add(formatDuration(video.duration))
    if (settings.showPlayedTime && video.playedTime != null && video.playedTime > 0) metaItems.add("Played: ${formatDuration(video.playedTime)}")
    if (settings.showResolution && !video.resolution.isNullOrEmpty()) metaItems.add(video.resolution)
    if (settings.showFrameRate && video.frameRate != null) metaItems.add("${video.frameRate} fps")
    if (settings.showFileExtension) metaItems.add(video.title.substringAfterLast('.', video.uri.substringAfterLast('.', "")).uppercase())
    if (settings.showSize) metaItems.add(formatSize(video.size))
    if (settings.showDate && video.dateAdded > 0) metaItems.add(formatDate(video.dateAdded))
    if (settings.showPath) metaItems.add(video.path)

    if (metaItems.isNotEmpty()) {
        val text = metaItems.filter { it.isNotEmpty() }.joinToString(" • ")
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

// EXPLORER LIST CONTENT

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ExplorerListContent(
    folders: List<VideoFolder>,
    videos: List<Video>,
    allVideosForSize: List<Video>,
    settings: ViewSettings,
    selectedFolders: Set<VideoFolder>,
    selectedVideos: Set<Video>,
    onFolderClick: (VideoFolder) -> Unit,
    onFolderLongClick: (VideoFolder) -> Unit,
    onVideoClick: (Video) -> Unit,
    onVideoLongClick: (Video) -> Unit,
    listState: LazyListState = rememberLazyListState(),
    gridState: LazyGridState = rememberLazyGridState()
) {
    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current

    if (settings.layoutMode == LayoutMode.GRID) {
        LazyVerticalGrid(
            columns = GridCells.Fixed(settings.gridColumns),
            state = gridState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(folders) { folder ->
                val folderVideos = remember(folder, allVideosForSize) { allVideosForSize.filter { it.path.startsWith(folder.id) } }
                FolderGridItem(
                    folder = folder,
                    videos = folderVideos,
                    settings = settings,
                    isSelected = folder in selectedFolders,
                    onClick = { onFolderClick(folder) },
                    onLongClick = {
                        haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                        onFolderLongClick(folder)
                    }
                )
            }
            items(videos) { video ->
                VideoGridItem(
                    video = video,
                    settings = settings,
                    isSelected = video in selectedVideos,
                    onClick = { onVideoClick(video) },
                    onLongClick = {
                        haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                        onVideoLongClick(video)
                    }
                )
            }
        }
    } else {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 16.dp)
        ) {
            items(folders) { folder ->
                val folderVideos = remember(folder, allVideosForSize) { allVideosForSize.filter { it.path.startsWith(folder.id) } }
                FolderListItem(
                    folder = folder,
                    videos = folderVideos,
                    settings = settings,
                    isSelected = folder in selectedFolders,
                    onClick = { onFolderClick(folder) },
                    onLongClick = {
                        haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                        onFolderLongClick(folder)
                    }
                )
            }
            items(videos) { video ->
                VideoListItem(
                    video = video,
                    settings = settings,
                    isSelected = video in selectedVideos,
                    onClick = { onVideoClick(video) },
                    onLongClick = {
                        haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                        onVideoLongClick(video)
                    }
                )
            }
        }
    }
}

// Top App Bar Component
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun VideoListTopAppBar(
    isSelectionActive: Boolean,
    titleText: String?,
    selectedCount: Int,
    totalCount: Int,
    showBackButton: Boolean,
    onClearSelection: () -> Unit,
    onSelectAll: () -> Unit,
    onBack: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onShowSettings: () -> Unit,
    onBackToFolders: () -> Unit
) {
    if (isSelectionActive) {
        val allSelected = selectedCount == totalCount
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
                    "$selectedCount / $totalCount selected",
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
                    titleText ?: "Nosved Player",
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            },
            navigationIcon = {
                if (showBackButton) {
                    IconButton(onClick = onBackToFolders) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                } else {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back to Home"
                        )
                    }
                }
            },
            actions = {
                var isMenuExpanded by remember { mutableStateOf(false) }

                IconButton(onClick = onShowSettings) {
                    Icon(imageVector = Icons.Filled.Tune, contentDescription = "View Settings")
                }
                
                Box {
                    IconButton(onClick = { isMenuExpanded = true }) {
                        Icon(imageVector = Icons.Filled.MoreVert, contentDescription = "More Options")
                    }
                    
                    DropdownMenu(
                        expanded = isMenuExpanded,
                        onDismissRequest = { isMenuExpanded = false }
                    ) {
                        val menuOptions = listOf("Import Video", "Settings", "About")
                        menuOptions.forEach { text ->
                            DropdownMenuItem(
                                text = { Text(text) },
                                onClick = {
                                    isMenuExpanded = false
                                    when (text) {
                                        "Settings" -> onNavigateToSettings()
                                        // Handle other actions here
                                    }
                                }
                            )
                        }
                    }
                }
            }
        )
    }
}

// VIDEO SELECTION BOTTOM APP BAR
@Composable
fun VideoSelectionBottomAppBar(
    selectedVideos: Set<Video>,
    onPlayAll: () -> Unit,
    onMove: () -> Unit,
    onCopy: () -> Unit,
    onDelete: () -> Unit,
    onRename: () -> Unit,
    onShowInfo: () -> Unit
) {
    BottomAppBar(
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
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
            ActionColumn(
                icon = Icons.Filled.PlayCircle,
                label = "Play All",
                onClick = onPlayAll
            )
            // Move
            ActionColumn(icon = Icons.Filled.DriveFileMove, label = "Move", onClick = onMove)
            // Copy
            ActionColumn(icon = Icons.Filled.ContentCopy, label = "Copy", onClick = onCopy)
            // Delete
            ActionColumn(icon = Icons.Filled.Delete, label = "Delete", onClick = onDelete)
            // Rename
            if (selectedVideos.size == 1) {
                ActionColumn(
                    icon = Icons.Filled.DriveFileRenameOutline,
                    label = "Rename",
                    onClick = onRename
                )
            }
            // Info
            ActionColumn(icon = Icons.Filled.Info, label = "Info", onClick = onShowInfo)
        }
    }
}

@Composable
private fun ActionColumn(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Icon(icon, contentDescription = label)
        Text(label, fontSize = 10.sp)
    }
}

// EXTENSIONS FOR SORTING FOLDERS
private fun List<VideoFolder>.applyFolderSort(
    folderMap: Map<VideoFolder, List<Video>>,
    field: SortField,
    direction: SortDirection
): List<VideoFolder> {
    val sorted = when (field) {
        SortField.TITLE -> sortedBy { it.name.lowercase() }
        SortField.DATE -> sortedBy { folder -> folderMap[folder]?.maxOfOrNull { it.dateAdded } ?: 0L }
        SortField.PLAYED_TIME -> sortedBy { folder -> folderMap[folder]?.maxOfOrNull { it.playedTime ?: 0L } ?: 0L }
        SortField.STATUS -> sortedBy { it.name.lowercase() }
        SortField.LENGTH -> sortedBy { folder -> folderMap[folder]?.sumOf { it.duration } ?: 0L }
        SortField.SIZE -> sortedBy { folder -> folderMap[folder]?.sumOf { it.size } ?: 0L }
        SortField.RESOLUTION -> sortedBy { it.name.lowercase() }
        SortField.PATH -> sortedBy { it.id.lowercase() }
        SortField.FRAME_RATE -> sortedBy { it.name.lowercase() }
        SortField.TYPE -> sortedBy { it.name.lowercase() }
    }
    return if (direction == SortDirection.DESCENDING) sorted.reversed() else sorted
}