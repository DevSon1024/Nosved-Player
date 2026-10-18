package com.devson.nvplayer.ui.screen.settings

import android.app.Activity
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.devson.nvplayer.R
import com.devson.nvplayer.domain.model.Video
import com.devson.nvplayer.util.formatDate
import com.devson.nvplayer.util.formatDuration
import com.devson.nvplayer.util.formatSize
import com.devson.nvplayer.viewmodel.FileOperationsViewModel
import com.devson.nvplayer.viewmodel.RecycleBinViewModel

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun RecycleBinScreen(
    onBack: () -> Unit,
    viewModel: RecycleBinViewModel = viewModel(),
    fileOpsViewModel: FileOperationsViewModel = viewModel()
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val trashedVideos by viewModel.trashedVideos.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val context = LocalContext.current

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
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeTopAppBar(
                title = {
                    if (isSelectionActive) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "${selectedVideos.size} of ${trashedVideos.size} selected",
                                fontWeight = FontWeight.Bold
                            )
                        }
                    } else {
                        Text(
                            text = stringResource(R.string.recycle_bin_title),
                            fontWeight = FontWeight.Bold
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = {
                        if (isSelectionActive) selectedVideos = emptySet()
                        else onBack()
                    }) {
                        Icon(
                            imageVector = if (isSelectionActive) Icons.Default.Close else Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back)
                        )
                    }
                },
                actions = {
                    if (isSelectionActive) {
                        IconButton(onClick = {
                            selectedVideos = if (selectedVideos.size == trashedVideos.size) {
                                emptySet()
                            } else {
                                trashedVideos.toSet()
                            }
                        }) {
                            Icon(
                                imageVector = if (selectedVideos.size == trashedVideos.size) Icons.Default.Deselect else Icons.Default.SelectAll,
                                contentDescription = stringResource(R.string.action_all)
                            )
                        }
                    } else {
                        IconButton(onClick = { showInfoDialog = true }) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = stringResource(R.string.action_info)
                            )
                        }
                    }
                },
                scrollBehavior = scrollBehavior,
                colors = TopAppBarDefaults.largeTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    scrolledContainerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground,
                    navigationIconContentColor = MaterialTheme.colorScheme.onBackground,
                    actionIconContentColor = MaterialTheme.colorScheme.onBackground
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            AnimatedVisibility(
                visible = isSelectionActive,
                enter = slideInVertically { it } + fadeIn(),
                exit = slideOutVertically { it } + fadeOut()
            ) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    tonalElevation = 6.dp,
                    shadowElevation = 8.dp
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // Restore Button
                        Button(
                            onClick = {
                                val uris = selectedVideos.mapNotNull { runCatching { Uri.parse(it.uri) }.getOrNull() }
                                fileOpsViewModel.restoreVideos(context, uris)
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer,
                                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        ) {
                            Icon(
                                imageVector = Icons.Default.RestoreFromTrash,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = "Restore (${selectedVideos.size})",
                                fontWeight = FontWeight.Bold
                            )
                        }

                        // Delete Permanently Button
                        Button(
                            onClick = { showDeleteConfirmDialog = true },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer,
                                contentColor = MaterialTheme.colorScheme.onErrorContainer
                            )
                        ) {
                            Icon(
                                imageVector = Icons.Default.DeleteForever,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = "Delete (${selectedVideos.size})",
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize()) {
            if (opInProgress) {
                LinearProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.TopCenter)
                        .padding(top = padding.calculateTopPadding())
                )
            }

            if (isLoading && trashedVideos.isEmpty()) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            } else if (trashedVideos.isEmpty()) {
                EmptyRecycleBinView(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(horizontal = 32.dp)
                )
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(
                        start = 16.dp,
                        end = 16.dp,
                        top = padding.calculateTopPadding() + 8.dp,
                        bottom = padding.calculateBottomPadding() + if (isSelectionActive) 80.dp else 24.dp
                    ),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Informational Summary Card
                    item {
                        RecycleBinSummaryCard(
                            totalCount = trashedVideos.size,
                            totalSizeBytes = remember(trashedVideos) { trashedVideos.sumOf { it.size } }
                        )
                    }

                    items(
                        items = trashedVideos,
                        key = { it.uri },
                        contentType = { "trashed_video" }
                    ) { video ->
                        val isSelected = selectedVideos.contains(video)
                        val toggleSelection = {
                            selectedVideos = if (isSelected) {
                                selectedVideos - video
                            } else {
                                selectedVideos + video
                            }
                        }

                        TrashedVideoItem(
                            video = video,
                            isSelected = isSelected,
                            onLongClick = toggleSelection,
                            onClick = {
                                if (isSelectionActive) {
                                    toggleSelection()
                                } else {
                                    // Start selection mode easily on tap
                                    selectedVideos = setOf(video)
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
            icon = {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
            },
            title = {
                Text(
                    text = "About Recycle Bin",
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    text = "Videos deleted from Open Player are safely preserved in the Android system trash for up to 30 days.\n\nYou can restore videos back to their original folders or permanently delete them at any time.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            confirmButton = {
                TextButton(onClick = { showInfoDialog = false }) {
                    Text(stringResource(R.string.ok), fontWeight = FontWeight.Bold)
                }
            }
        )
    }

    if (showDeleteConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmDialog = false },
            icon = {
                Icon(
                    imageVector = Icons.Default.DeleteForever,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error
                )
            },
            title = {
                Text(
                    text = "Delete Permanently?",
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    text = "Are you sure you want to permanently delete ${selectedVideos.size} selected video(s)? This action cannot be undone.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val uris = selectedVideos.mapNotNull { runCatching { Uri.parse(it.uri) }.getOrNull() }
                        fileOpsViewModel.deleteVideos(context, uris, trash = false)
                        showDeleteConfirmDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    )
                ) {
                    Text("Delete Forever", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmDialog = false }) {
                    Text(stringResource(R.string.dialog_cancel))
                }
            }
        )
    }
}

@Composable
private fun RecycleBinSummaryCard(
    totalCount: Int,
    totalSizeBytes: Long
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 6.dp),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 2.dp
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.AutoDelete,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Auto-removal in 30 days",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "$totalCount video(s) • ${formatSize(totalSizeBytes)} total storage",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun EmptyRecycleBinView(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier
                .size(80.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.DeleteSweep,
                contentDescription = null,
                modifier = Modifier.size(40.dp),
                tint = MaterialTheme.colorScheme.primary
            )
        }

        Text(
            text = "Recycle Bin is Empty",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center
        )

        Text(
            text = "Deleted videos are safely retained for up to 30 days before permanent deletion.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TrashedVideoItem(
    video: Video,
    isSelected: Boolean,
    onLongClick: () -> Unit,
    onClick: () -> Unit
) {
    val formattedSize = remember(video.size) { formatSize(video.size) }
    val formattedDate = remember(video.dateAdded) { formatDate(video.dateAdded) }
    val formattedDuration = remember(video.duration) { if (video.duration > 0L) formatDuration(video.duration) else null }
    val daysLeft = remember(video.dateExpires) {
        val expiresMs = video.dateExpires ?: 0L
        val diffMs = expiresMs - System.currentTimeMillis()
        if (diffMs > 0) (diffMs / (1000 * 60 * 60 * 24)).toInt() else 0
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            ),
        shape = RoundedCornerShape(16.dp),
        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f) else MaterialTheme.colorScheme.surfaceContainerHigh,
        border = if (isSelected) BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary) else null,
        tonalElevation = if (isSelected) 4.dp else 1.dp
    ) {
        Row(
            modifier = Modifier.padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            val context = LocalContext.current
            val imageRequest = remember(video.uri) {
                ImageRequest.Builder(context)
                    .data(video.uri)
                    .size(320, 180)
                    .precision(coil3.size.Precision.INEXACT)
                    .crossfade(false)
                    .build()
            }
            Box(
                modifier = Modifier
                    .size(width = 112.dp, height = 74.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest)
            ) {
                AsyncImage(
                    model = imageRequest,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )

                // Days left badge
                if (video.dateExpires != null && video.dateExpires > 0L) {
                    val isUrgent = daysLeft <= 5
                    Surface(
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(4.dp),
                        shape = RoundedCornerShape(6.dp),
                        color = if (isUrgent) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.surface.copy(alpha = 0.85f)
                    ) {
                        Text(
                            text = if (daysLeft == 0) "Today" else "${daysLeft}d left",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (isUrgent) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                        )
                    }
                }

                // Duration badge
                if (formattedDuration != null) {
                    Surface(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(4.dp),
                        shape = RoundedCornerShape(6.dp),
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f)
                    ) {
                        Text(
                            text = formattedDuration,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                        )
                    }
                }

                // Selected Checkmark Overlay
                if (isSelected) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = "Selected",
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }

            // Text metadata
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = video.title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = formattedSize,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "•",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                    Text(
                        text = formattedDate,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
