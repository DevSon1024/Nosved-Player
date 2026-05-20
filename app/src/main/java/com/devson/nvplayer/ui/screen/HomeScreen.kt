package com.devson.nvplayer.ui.screen

import android.app.Activity
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
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
import com.devson.nvplayer.model.DefaultScreen
import com.devson.nvplayer.model.VideoFolder
import com.devson.nvplayer.ui.components.CustomRenameDialog
import com.devson.nvplayer.ui.components.ViewSettingsBottomSheet
import com.devson.nvplayer.ui.screens.videolist.components.folder.FolderListContent
import com.devson.nvplayer.ui.screens.StorageExplorerScreen
import com.devson.nvplayer.ui.screens.videolist.components.topbar.VideoListTopAppBar
import com.devson.nvplayer.ui.screens.videolist.utils.applyFolderSort
import com.devson.nvplayer.ui.screens.videolist.utils.shareVideos
import com.devson.nvplayer.util.SelectionBottomAppBar
import com.devson.nvplayer.viewmodel.FileOperationsViewModel
import com.devson.nvplayer.viewmodel.HomeViewModel
import com.devson.nvplayer.viewmodel.VideoListViewModel

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: VideoListViewModel,
    fileOpsViewModel: FileOperationsViewModel,
    homeViewModel: HomeViewModel,
    onFolderClick: (String) -> Unit,
    onSettingsClick: () -> Unit
) {
    val context = LocalContext.current

    // Trigger initial loading of videos
    LaunchedEffect(Unit) {
        viewModel.loadVideos()
    }

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
    var selectedFolders by remember { mutableStateOf(emptySet<VideoFolder>()) }
    var showInfoBottomSheet by remember { mutableStateOf(false) }

    val sortedFolderKeys = remember(videosByFolder, viewSettings.sortField, viewSettings.sortDirection) {
        val keys = videosByFolder.keys.toList()
        keys.applyFolderSort(videosByFolder, viewSettings.sortField, viewSettings.sortDirection)
    }
    val isSelectionActive = selectedFolders.isNotEmpty()

    // Hoisted scroll states
    val folderListState = rememberLazyListState()
    val folderGridState = rememberLazyGridState()

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
                selectedFolders = emptySet()
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
        if (selectedFolders.isNotEmpty()) {
            selectedFolders = emptySet()
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            VideoListTopAppBar(
                isSelectionActive = isSelectionActive,
                titleText = "Folders",
                selectedCount = selectedFolders.size,
                totalCount = sortedFolderKeys.size,
                showBackButton = false,
                showHomeBackButton = viewSettings.defaultScreen != DefaultScreen.VIDEO_LIST,
                onClearSelection = {
                    selectedFolders = emptySet()
                },
                onSelectAll = {
                    selectedFolders = if (selectedFolders.size == sortedFolderKeys.size) emptySet() else sortedFolderKeys.toSet()
                },
                onBack = {},
                onNavigateToSettings = onSettingsClick,
                onShowSettings = { showSettingsSheet = true },
                onSearch = { query ->
                    // Global search if query is searched
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
                onBackToFolders = {
                    selectedFolders = emptySet()
                }
            )
        },
        bottomBar = {
            if (isSelectionActive) {
                val selectedUris: List<Uri> = remember(selectedFolders, videosByFolder) {
                    selectedFolders
                        .flatMap { folder -> videosByFolder[folder] ?: emptyList() }
                        .mapNotNull { runCatching { Uri.parse(it.uri) }.getOrNull() }
                }

                SelectionBottomAppBar(
                    selectedFolders = selectedFolders,
                    videosByFolder = videosByFolder,
                    viewSettings = viewSettings,
                    onVideoSelected = { _, _ -> },
                    onClearSelection = { selectedFolders = emptySet() },
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
                        val folder = selectedFolders.first()
                        renameInputText = folder.name
                        showRenameDialog = true
                    },
                    onShare = {
                        val videos = selectedFolders.flatMap { videosByFolder[it] ?: emptyList() }
                        shareVideos(context, videos)
                        selectedFolders = emptySet()
                    },
                    onShowInfo = { showInfoBottomSheet = true },
                    onMarkStatus = { status ->
                        selectedFolders.flatMap { videosByFolder[it] ?: emptyList() }.forEach { video ->
                            val position = when (status) {
                                "NEW" -> 0L
                                "RUNNING" -> video.duration / 2
                                "ENDED" -> video.duration
                                else -> 0L
                            }
                            homeViewModel.setWatchStatus(video, position)
                        }
                        selectedFolders = emptySet()
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

            if (isLoading && videosByFolder.isEmpty()) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            } else {
                PullToRefreshBox(
                    isRefreshing = isRefreshing,
                    onRefresh = { viewModel.loadVideos(forceRefresh = true) },
                    modifier = Modifier.fillMaxSize()
                ) {
                    FolderListContent(
                        folders = videosByFolder,
                        settings = viewSettings,
                        selectedFolders = selectedFolders,
                        historyMap = historyMap,
                        onFolderClick = { folder ->
                            if (isSelectionActive) {
                                selectedFolders = if (folder in selectedFolders) selectedFolders - folder else selectedFolders + folder
                            } else {
                                onFolderClick(folder.name)
                            }
                        },
                        onFolderLongClick = { folder ->
                            selectedFolders = if (folder in selectedFolders) selectedFolders - folder else selectedFolders + folder
                        },
                        listState = folderListState,
                        gridState = folderGridState,
                        contentPadding = padding
                    )
                }
            }
        }
    }

    // ---- INFORMATION BOTTOM SHEET ----
    if (showInfoBottomSheet && isSelectionActive) {
        val videosToShow = selectedFolders.flatMap { videosByFolder[it] ?: emptyList() }.toSet()
        InformationBottomSheet(
            selectedVideos = videosToShow,
            onDismiss = { showInfoBottomSheet = false }
        )
    }

    // ---- RENAME DIALOG ----
    if (showRenameDialog && selectedFolders.size == 1) {
        CustomRenameDialog(
            initialName = renameInputText,
            title = "Rename Folder",
            onConfirm = { newName ->
                val folder = selectedFolders.first()
                val folderPath = if (folder.id.startsWith("/")) {
                    folder.id
                } else {
                    (videosByFolder[folder] ?: emptyList()).firstOrNull()?.path?.substringBeforeLast("/")
                }
                if (folderPath != null) {
                    fileOpsViewModel.renameFolder(context, folderPath, newName)
                } else {
                    Toast.makeText(context, "Could not determine folder path.", Toast.LENGTH_SHORT).show()
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
            isFolderView = true,
            onDismiss = { showSettingsSheet = false },
            viewModel = viewModel
        )
    }

    if (showDeleteConfirmation) {
        val selectedUrisForDelete = selectedFolders.flatMap { folder -> videosByFolder[folder] ?: emptyList() }
            .mapNotNull { runCatching { Uri.parse(it.uri) }.getOrNull() }

        AlertDialog(
            onDismissRequest = { showDeleteConfirmation = false },
            title = { Text("Delete Folder(s)") },
            text = { Text("Choose how you want to delete the selected folders and all videos inside.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        fileOpsViewModel.deleteVideos(context, selectedUrisForDelete, trash = true)
                        selectedFolders = emptySet()
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
                            selectedFolders = emptySet()
                            showDeleteConfirmation = false
                        }
                    ) { Text("Delete Permanently", color = MaterialTheme.colorScheme.error) }
                }
            }
        )
    }
}
