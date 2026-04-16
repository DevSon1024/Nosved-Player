package com.devson.nosvedplayer.ui.screens

import android.app.Activity
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.RestoreFromTrash
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
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.ui.res.stringResource
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.devson.nosvedplayer.R
import com.devson.nosvedplayer.model.Video
import com.devson.nosvedplayer.util.formatDate
import com.devson.nosvedplayer.util.formatSize
import com.devson.nosvedplayer.viewmodel.FileOperationsViewModel
import com.devson.nosvedplayer.viewmodel.RecycleBinViewModel

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun RecycleBinScreen(
    onBack: () -> Unit,
    viewModel: RecycleBinViewModel = viewModel(),
    fileOpsViewModel: FileOperationsViewModel = viewModel()
) {
    val context = LocalContext.current
    val trashedVideos by viewModel.trashedVideos.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    
    var selectedVideos by remember { mutableStateOf(emptySet<Video>()) }
    val isSelectionActive = selectedVideos.isNotEmpty()
    var showInfoDialog by remember { mutableStateOf(false) }
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }

    // MediaStore permission dialog for trash/restore operations
    val intentSenderLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            fileOpsViewModel.onPermissionGranted(context)
        }
        fileOpsViewModel.clearPendingIntentSender()
    }

    val pendingIntentSender by fileOpsViewModel.pendingIntentSender.collectAsState()
    LaunchedEffect(pendingIntentSender) {
        pendingIntentSender?.let { sender ->
            intentSenderLauncher.launch(IntentSenderRequest.Builder(sender).build())
        }
    }

    val opResult by fileOpsViewModel.operationResult.collectAsState()
    LaunchedEffect(opResult) {
        opResult?.let { message ->
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
            viewModel.loadTrashedVideos()
            fileOpsViewModel.clearResult()
            selectedVideos = emptySet()
        }
    }

    val needsRefresh by fileOpsViewModel.needsRefresh.collectAsState()
    LaunchedEffect(needsRefresh) {
        if (needsRefresh) {
            viewModel.loadTrashedVideos()
            fileOpsViewModel.onRefreshHandled()
        }
    }

    val opInProgress by fileOpsViewModel.operationInProgress.collectAsState()

    BackHandler(enabled = isSelectionActive) {
        selectedVideos = emptySet()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    if (isSelectionActive) {
                        Text(stringResource(R.string.selection_items_count, selectedVideos.size, trashedVideos.size))
                    } else {
                        Text(stringResource(R.string.recycle_bin_title))
                    }
                },
                navigationIcon = {
                    IconButton(onClick = {
                        if (isSelectionActive) selectedVideos = emptySet()
                        else onBack()
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                actions = {
                    if (isSelectionActive) {
                        IconButton(onClick = {
                            if (selectedVideos.size == trashedVideos.size) {
                                selectedVideos = emptySet()
                            } else {
                                selectedVideos = trashedVideos.toSet()
                            }
                        }) {
                            Text(stringResource(R.string.action_all), modifier = Modifier.padding(8.dp))
                        }
                    } else {
                        IconButton(onClick = { showInfoDialog = true }) {
                            Icon(Icons.Filled.Info, contentDescription = stringResource(R.string.action_info))
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            if (isSelectionActive) {
                BottomAppBar(
                    containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(3.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        TextButton(
                            onClick = {
                                val uris = selectedVideos.mapNotNull { runCatching { Uri.parse(it.uri) }.getOrNull() }
                                fileOpsViewModel.restoreVideos(context, uris)
                            }
                        ) {
                            Icon(Icons.Filled.RestoreFromTrash, contentDescription = stringResource(R.string.action_restore))
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.action_restore))
                        }
                        TextButton(
                            onClick = { showDeleteConfirmDialog = true },
                            colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                        ) {
                            Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.action_delete_permanently))
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.action_delete))
                        }
                    }
                }
            }
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            if (opInProgress) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth().align(Alignment.TopCenter))
            }

            if (isLoading && trashedVideos.isEmpty()) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            } else if (trashedVideos.isEmpty()) {
                Column(
                    modifier = Modifier.align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.Filled.Delete,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = stringResource(R.string.recycle_bin_empty),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(trashedVideos, key = { it.uri }) { video ->
                        val isSelected = selectedVideos.contains(video)
                        TrashedVideoItem(
                            video = video,
                            isSelected = isSelected,
                            onLongClick = {
                                selectedVideos = if (isSelected) {
                                    selectedVideos - video
                                } else {
                                    selectedVideos + video
                                }
                            },
                            onClick = {
                                if (isSelectionActive) {
                                    selectedVideos = if (isSelected) {
                                        selectedVideos - video
                                    } else {
                                        selectedVideos + video
                                    }
                                } else {
                                    Toast.makeText(context, context.getString(R.string.recycle_bin_select_prompt), Toast.LENGTH_SHORT).show()
                                }
                            }
                        )
                    }
                }
            }
        }
    }

    if (showInfoDialog) {
        AlertDialog(
            onDismissRequest = { showInfoDialog = false },
            title = { Text(stringResource(R.string.recycle_bin_title)) },
            text = { Text(stringResource(R.string.recycle_bin_info_desc)) },
            confirmButton = {
                TextButton(onClick = { showInfoDialog = false }) { Text(stringResource(R.string.ok)) }
            }
        )
    }

    if (showDeleteConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmDialog = false },
            title = { Text(stringResource(R.string.recycle_bin_delete_confirm_title)) },
            text = { Text(stringResource(R.string.recycle_bin_delete_confirm_desc)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        val uris = selectedVideos.mapNotNull { runCatching { Uri.parse(it.uri) }.getOrNull() }
                        fileOpsViewModel.deleteVideos(context, uris, trash = false)
                        showDeleteConfirmDialog = false
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Text(stringResource(R.string.action_delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmDialog = false }) { Text(stringResource(R.string.dialog_cancel)) }
            }
        )
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun TrashedVideoItem(
    video: Video,
    isSelected: Boolean,
    onLongClick: () -> Unit,
    onClick: () -> Unit
) {
    val backgroundColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier.background(backgroundColor),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(width = 120.dp, height = 80.dp)
                    .background(MaterialTheme.colorScheme.surfaceDim)
            ) {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(video.uri)
                        .crossfade(true)
                        .build(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
                if (video.dateExpires != null && video.dateExpires > 0L) {
                    val expiresMs = video.dateExpires
                    val diffMs = expiresMs - System.currentTimeMillis()
                    val daysLeft = if (diffMs > 0) (diffMs / (1000 * 60 * 60 * 24)).toInt() else 0
                    
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(4.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.9f))
                            .padding(horizontal = 4.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.recycle_bin_days_left, daysLeft),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
            Column(
                modifier = Modifier
                    .padding(12.dp)
                    .weight(1f)
            ) {
                Text(
                    text = video.title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = formatSize(video.size),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = formatDate(video.dateAdded),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
