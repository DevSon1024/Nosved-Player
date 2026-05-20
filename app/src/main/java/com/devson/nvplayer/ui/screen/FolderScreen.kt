package com.devson.nvplayer.ui.screen

import android.app.Activity
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import com.devson.nvplayer.model.Video
import com.devson.nvplayer.model.applySort
import com.devson.nvplayer.ui.components.CustomRenameDialog
import com.devson.nvplayer.ui.components.ViewSettingsBottomSheet
import com.devson.nvplayer.ui.screens.videolist.components.list.VideoListContent
import com.devson.nvplayer.ui.screens.StorageExplorerScreen
import com.devson.nvplayer.ui.screens.videolist.components.topbar.VideoListTopAppBar
import com.devson.nvplayer.ui.screens.videolist.components.selection.VideoSelectionBottomBar
import com.devson.nvplayer.ui.screens.videolist.utils.shareVideos
import com.devson.nvplayer.viewmodel.FileOperationsViewModel
import com.devson.nvplayer.viewmodel.HomeViewModel
import com.devson.nvplayer.viewmodel.VideoListViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FolderScreen(
    folderName: String,
    viewModel: VideoListViewModel,
    fileOpsViewModel: FileOperationsViewModel,
    homeViewModel: HomeViewModel,
    onBackClick: () -> Unit,
    onVideoClick: (Uri, List<Uri>) -> Unit,
    onSettingsClick: () -> Unit
) {
    val context = LocalContext.current

    val videosByFolder by viewModel.videosByFolder.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val isRefreshing by viewModel.isRefreshing.collectAsState()
    val viewSettings by viewModel.viewSettings.collectAsState()

    val searchSuggestions by viewModel.searchSuggestions.collectAsState()
    var searchActive by remember { mutableStateOf(false) }
    var searchText by remember { mutableStateOf("") }
    val searchFocusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current

    LaunchedEffect(searchActive) {
        if (searchActive) searchFocusRequester.requestFocus()
    }

    var showSettingsSheet by remember { mutableStateOf(false) }

    // SELECTION STATE
    var selectedVideos by remember { mutableStateOf(emptySet<Video>()) }
    var showInfoBottomSheet by remember { mutableStateOf(false) }

    // Filter videos by folderName
    val currentFolder = remember(videosByFolder, folderName) {
        videosByFolder.keys.find { it.name == folderName }
    }
    val videos = remember(videosByFolder, currentFolder) {
        if (currentFolder != null) videosByFolder[currentFolder] ?: emptyList() else emptyList()
    }
    val sortedVideos = remember(videos, viewSettings.sortField, viewSettings.sortDirection) {
        videos.applySort(viewSettings.sortField, viewSettings.sortDirection)
    }

    val isSelectionActive = selectedVideos.isNotEmpty()

    // Hoisted scroll states
    val videoListState = rememberLazyListState()
    val videoGridState = rememberLazyGridState()

    // Watch History Map
    val history by homeViewModel.history.collectAsState()
    val historyMap = remember(history) { history.associateBy { it.uri } }

    // Handles MediaStore permission dialogs (delete/rename on API 29-30)
    val intentSenderLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            fileOpsViewModel.onPermissionGranted(context)
        }
        fileOpsViewModel.clearPendingIntentSender()
    }

    // Storage Explorer State
    var storageExplorerOp by remember { mutableStateOf<String?>(null) }
    var storageExplorerUris by remember { mutableStateOf<List<Uri>>(emptyList()) }

    if (storageExplorerOp != null) {
        BackHandler { storageExplorerOp = null }
        StorageExplorerScreen(
            operationType = storageExplorerOp!!,
            sourceUris = storageExplorerUris,
            onComplete = {
                storageExplorerOp = null
                selectedVideos = emptySet()
            },
            onCancel = {
                storageExplorerOp = null
            }
        )
        return
    }

    // Dialog states
    var showRenameDialog by remember { mutableStateOf(false) }
    var renameInputText by remember { mutableStateOf("") }
    var showDeleteConfirmation by remember { mutableStateOf(false) }

    // Watch pendingIntentSender and launch it automatically
    val pendingIntentSender by fileOpsViewModel.pendingIntentSender.collectAsState()
    LaunchedEffect(pendingIntentSender) {
        pendingIntentSender?.let { sender ->
            intentSenderLauncher.launch(IntentSenderRequest.Builder(sender).build())
        }
    }

    // File operation result -> Toast + list reload
    val opResult by fileOpsViewModel.operationResult.collectAsState()
    LaunchedEffect(opResult) {
        opResult?.let { message ->
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
            viewModel.loadVideos(forceRefresh = true)
            homeViewModel.loadWatchHistory()
            fileOpsViewModel.clearResult()
        }
    }

    val needsRefresh by fileOpsViewModel.needsRefresh.collectAsState()
    LaunchedEffect(needsRefresh) {
        if (needsRefresh) {
            viewModel.loadVideos(forceRefresh = true)
            homeViewModel.loadWatchHistory()
            fileOpsViewModel.onRefreshHandled()
        }
    }

    // Drives progress bar visibility
    val opInProgress by fileOpsViewModel.operationInProgress.collectAsState()

    // Back handler: clears selection first before navigating out
    BackHandler(enabled = isSelectionActive) {
        if (selectedVideos.isNotEmpty()) {
            selectedVideos = emptySet()
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            VideoListTopAppBar(
                isSelectionActive = isSelectionActive,
                titleText = folderName,
                selectedCount = selectedVideos.size,
                totalCount = sortedVideos.size,
                showBackButton = true,
                showHomeBackButton = false,
                onClearSelection = {
                    selectedVideos = emptySet()
                },
                onSelectAll = {
                    selectedVideos = if (selectedVideos.size == sortedVideos.size) emptySet() else sortedVideos.toSet()
                },
                onBack = onBackClick,
                onNavigateToSettings = onSettingsClick,
                onShowSettings = { showSettingsSheet = true },
                onSearch = { query ->
                    // Handle search query
                },
                searchActive = searchActive,
                searchText = searchText,
                onSearchActiveChange = { active ->
                    searchActive = active
                    if (!active) {
                        searchText = ""
                        viewModel.clearSearch()
                    }
                },
                onSearchTextChange = { text ->
                    searchText = text
                    viewModel.onSearchQueryChanged(text)
                },
                searchSuggestions = searchSuggestions,
                searchFocusRequester = searchFocusRequester,
                keyboard = keyboard,
                onBackToFolders = onBackClick
            )
        },
        bottomBar = {
            if (isSelectionActive) {
                val selectedUris: List<Uri> = remember(selectedVideos) {
                    selectedVideos.mapNotNull { runCatching { Uri.parse(it.uri) }.getOrNull() }
                }

                VideoSelectionBottomBar(
                    selectedVideos = selectedVideos,
                    onPlayAll = {
                        val playVideo = selectedVideos.firstOrNull()
                        if (playVideo != null) {
                            val playlist = selectedVideos.mapNotNull { runCatching { Uri.parse(it.uri) }.getOrNull() }
                            onVideoClick(Uri.parse(playVideo.uri), playlist)
                            selectedVideos = emptySet()
                        }
                    },
                    onMove = {
                        if (selectedUris.isNotEmpty()) {
                            storageExplorerUris = selectedUris
                            storageExplorerOp = "MOVE"
                        }
                    },
                    onCopy = {
                        if (selectedUris.isNotEmpty()) {
                            storageExplorerUris = selectedUris
                            storageExplorerOp = "COPY"
                        }
                    },
                    onDelete = {
                        if (selectedUris.isNotEmpty()) {
                            showDeleteConfirmation = true
                        }
                    },
                    onRename = {
                        val video = selectedVideos.firstOrNull()
                        if (video != null) {
                            renameInputText = video.title.substringBeforeLast(".")
                            showRenameDialog = true
                        }
                    },
                    onShare = {
                        shareVideos(context, selectedVideos.toList())
                        selectedVideos = emptySet()
                    },
                    onShowInfo = { showInfoBottomSheet = true },
                    onMarkStatus = { status ->
                        selectedVideos.forEach { video ->
                            val position = when (status) {
                                "NEW" -> 0L
                                "RUNNING" -> video.duration / 2
                                "ENDED" -> video.duration
                                else -> 0L
                            }
                            homeViewModel.setWatchStatus(video, position)
                        }
                        selectedVideos = emptySet()
                    }
                )
            }
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
        ) {
            if (opInProgress) {
                LinearProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = padding.calculateTopPadding())
                        .align(Alignment.TopCenter)
                )
            }

            if (isLoading && sortedVideos.isEmpty()) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            } else if (sortedVideos.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = "No videos in this folder.",
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                    )
                }
            } else {
                PullToRefreshBox(
                    isRefreshing = isRefreshing,
                    onRefresh = { viewModel.loadVideos(forceRefresh = true) },
                    modifier = Modifier.fillMaxSize()
                ) {
                    VideoListContent(
                        videos = sortedVideos,
                        settings = viewSettings,
                        selectedVideos = selectedVideos,
                        onVideoClick = { video ->
                            if (isSelectionActive) {
                                selectedVideos = if (video in selectedVideos) selectedVideos - video else selectedVideos + video
                            } else {
                                val playlist = sortedVideos.mapNotNull { runCatching { Uri.parse(it.uri) }.getOrNull() }
                                onVideoClick(Uri.parse(video.uri), playlist)
                            }
                        },
                        onVideoLongClick = { video ->
                            selectedVideos = if (video in selectedVideos) selectedVideos - video else selectedVideos + video
                        },
                        listState = videoListState,
                        gridState = videoGridState,
                        historyMap = historyMap,
                        contentPadding = padding
                    )
                }
            }
        }
    }

    // ---- INFORMATION BOTTOM SHEET ----
    if (showInfoBottomSheet && isSelectionActive) {
        InformationBottomSheet(
            selectedVideos = selectedVideos,
            onDismiss = { showInfoBottomSheet = false }
        )
    }

    // ---- RENAME DIALOG ----
    if (showRenameDialog && selectedVideos.size == 1) {
        CustomRenameDialog(
            initialName = renameInputText,
            title = "Rename Video",
            onConfirm = { newName ->
                val video = selectedVideos.firstOrNull()
                if (video != null) {
                    fileOpsViewModel.renameVideo(context, Uri.parse(video.uri), newName)
                }
                showRenameDialog = false
                selectedVideos = emptySet()
            },
            onDismiss = { showRenameDialog = false }
        )
    }

    if (showSettingsSheet) {
        ViewSettingsBottomSheet(
            settings = viewSettings,
            isFolderView = false,
            onDismiss = { showSettingsSheet = false },
            viewModel = viewModel
        )
    }

    if (showDeleteConfirmation) {
        val selectedUrisForDelete = selectedVideos.mapNotNull { runCatching { Uri.parse(it.uri) }.getOrNull() }

        AlertDialog(
            onDismissRequest = { showDeleteConfirmation = false },
            title = { Text("Delete Video(s)") },
            text = { Text("Choose how you want to delete the selected video(s).") },
            confirmButton = {
                TextButton(
                    onClick = {
                        fileOpsViewModel.deleteVideos(context, selectedUrisForDelete, trash = true)
                        selectedVideos = emptySet()
                        showDeleteConfirmation = false
                    }
                ) { Text("Move to Recycle Bin") }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = { showDeleteConfirmation = false }) { Text("Cancel") }
                    TextButton(
                        onClick = {
                            fileOpsViewModel.deleteVideos(context, selectedUrisForDelete, trash = false)
                            selectedVideos = emptySet()
                            showDeleteConfirmation = false
                        }
                    ) { Text("Delete Permanently", color = MaterialTheme.colorScheme.error) }
                }
            }
        )
    }
}
