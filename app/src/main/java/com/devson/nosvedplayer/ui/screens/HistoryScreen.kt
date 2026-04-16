package com.devson.nosvedplayer.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.devson.nosvedplayer.R
import androidx.lifecycle.viewmodel.compose.viewModel
import com.devson.nosvedplayer.model.Video
import com.devson.nosvedplayer.model.ViewSettings
import com.devson.nosvedplayer.model.WatchHistory
import com.devson.nosvedplayer.viewmodel.HomeViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    onVideoSelected: (Video, List<Video>, Long) -> Unit,
    onBack: () -> Unit,
    homeViewModel: HomeViewModel = viewModel()
) {
    val history by homeViewModel.history.collectAsState()
    var showClearDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.history_title), fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                actions = {
                    if (history.isNotEmpty()) {
                        IconButton(onClick = { showClearDialog = true }) {
                            Icon(Icons.Filled.DeleteSweep, contentDescription = stringResource(R.string.history_clear_all),
                                tint = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            )
        }
    ) { padding ->
        if (history.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.history_no_history), style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            val playlist = remember(history) {
                history.map { it.toVideo() }
            }
            val historyMapLocal = remember(history) { history.associateBy { it.uri } }
            val defaultSettings = remember { ViewSettings() }

            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(bottom = 16.dp)
            ) {
                items(history, key = { it.uri }) { item ->
                    val video = remember(item) { item.toVideo() }
                    VideoListItem(
                        video = video,
                        settings = defaultSettings,
                        isSelected = false,
                        lastPositionMs = item.lastPositionMs,
                        onClick = { onVideoSelected(video, playlist, item.lastPositionMs) },
                        onLongClick = {}
                    )
                }
            }
        }
    }

    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = { Text(stringResource(R.string.history_clear_confirm_title)) },
            text = { Text(stringResource(R.string.history_clear_confirm_desc)) },
            confirmButton = {
                TextButton(onClick = {
                    homeViewModel.clearAllHistory()
                    showClearDialog = false
                }) { Text(stringResource(R.string.history_clear_button), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) { Text(stringResource(R.string.history_cancel_button)) }
            }
        )
    }
}

private fun WatchHistory.toVideo() = Video(
    uri = uri,
    title = title,
    duration = duration,
    size = size,
    folderName = folderName,
    path = path
)
