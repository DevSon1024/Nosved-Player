package com.devson.nosvedplayer.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Check
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
import coil.request.ImageRequest
import coil.request.videoFrameMillis
import com.devson.nosvedplayer.model.SortOrder
import com.devson.nosvedplayer.model.Video
import com.devson.nosvedplayer.model.ViewSettings
import com.devson.nosvedplayer.viewmodel.VideoListViewModel
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VideoListScreen(
    onVideoSelected: (Video) -> Unit,
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

    // Intercept device back button when inside a folder — go to folder list, not out of app
    BackHandler(enabled = selectedFolder != null) {
        viewModel.selectFolder(null)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                windowInsets = WindowInsets(0, 0, 0, 0),
                title = { Text(if (selectedFolder != null) selectedFolder!! else "Folders", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    if (selectedFolder != null) {
                        IconButton(onClick = { viewModel.selectFolder(null) }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    }
                },
                actions = {
                    if (selectedFolder != null) {
                        IconButton(onClick = { showSettingsSheet = true }) {
                            Icon(Icons.Filled.Settings, contentDescription = "View Settings")
                        }
                    }
                }
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
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
                    FolderList(
                        folders = videosByFolder,
                        onFolderClick = { folderName -> viewModel.selectFolder(folderName) }
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
                        onVideoClick = onVideoSelected
                    )
                }
            }
        }
    }

    if (showSettingsSheet) {
        ViewSettingsBottomSheet(
            settings = viewSettings,
            onDismiss = { showSettingsSheet = false },
            viewModel = viewModel
        )
    }
}

@Composable
fun FolderList(
    folders: Map<String, List<Video>>,
    onFolderClick: (String) -> Unit
) {
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        items(folders.keys.toList().sortedBy { it.lowercase() }) { folderName ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onFolderClick(folderName) }
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Filled.Folder,
                    contentDescription = null,
                    modifier = Modifier.size(48.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(16.dp))
                Column {
                    Text(
                        text = folderName,
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = "${folders[folderName]?.size ?: 0} videos",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

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
                .videoFrameMillis(1000)
                .crossfade(true)
                .build(),
            contentDescription = "Video Thumbnail",
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
        // Dim overlay + Play icon to show it's a video
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

@Composable
fun VideoGridItem(
    video: Video,
    settings: ViewSettings,
    onClick: (Video) -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(0.8f)
            .clickable { onClick(video) },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            if (settings.showThumbnail) {
                VideoThumbnail(
                    uri = video.uri,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f) // Takes remaining height
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ViewSettingsBottomSheet(
    settings: ViewSettings,
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

            // Metadata Toggles
            Text("Show Metadata", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            
            MetadataToggle("Thumbnail", settings.showThumbnail) { viewModel.updateShowThumbnail(it) }
            MetadataToggle("Duration", settings.showDuration) { viewModel.updateShowDuration(it) }
            MetadataToggle("File Size", settings.showSize) { viewModel.updateShowSize(it) }
            MetadataToggle("Date Added", settings.showDate) { viewModel.updateShowDate(it) }
            MetadataToggle("File Extension", settings.showFileExtension) { viewModel.updateShowFileExtension(it) }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

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

private fun formatSortOrder(order: SortOrder): String {
    return when(order) {
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
