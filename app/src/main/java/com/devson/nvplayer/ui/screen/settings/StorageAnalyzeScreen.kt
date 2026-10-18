package com.devson.nvplayer.ui.screen.settings

import android.content.Context
import android.net.Uri
import android.os.Environment
import android.os.StatFs
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.devson.nvplayer.domain.model.Video
import com.devson.nvplayer.util.formatDuration
import com.devson.nvplayer.util.formatSize
import com.devson.nvplayer.viewmodel.VideoListViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.NumberFormat
import java.util.Locale

enum class StorageUnitDisplay(val label: String, val shortName: String) {
    GB("Gigabytes (GB)", "GB"),
    MB("Megabytes (MB)", "MB"),
    KB("Kilobytes (KB)", "KB"),
    BYTES("Bytes (B)", "Bytes")
}

@androidx.compose.runtime.Immutable
data class ExactStorageInfo(
    val totalBytes: Long = 0L,
    val freeBytes: Long = 0L,
    val usedBytes: Long = 0L,
    val usedPercentage: Float = 0f,
    val freePercentage: Float = 0f,
    val videoTotalBytes: Long = 0L,
    val videoCount: Int = 0,
    val topFolders: List<FolderStorageItem> = emptyList(),
    val largestVideos: List<Video> = emptyList()
)

@androidx.compose.runtime.Immutable
data class FolderStorageItem(
    val name: String,
    val path: String,
    val count: Int,
    val totalBytes: Long,
    val percentageOfVideos: Float
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StorageAnalyzeScreen(
    onBack: () -> Unit,
    videoListViewModel: VideoListViewModel,
    onNavigateToRecycleBin: () -> Unit = {},
    onVideoClick: (Uri, List<Uri>) -> Unit = { _, _ -> }
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val context = LocalContext.current
    val videosFlat by videoListViewModel.videosFlat.collectAsState()

    var storageInfo by remember { mutableStateOf(ExactStorageInfo()) }
    var selectedUnit by remember { mutableStateOf(StorageUnitDisplay.GB) }
    var isLoading by remember { mutableStateOf(true) }

    suspend fun refreshStorageData() {
        isLoading = true
        val info = withContext(Dispatchers.IO) {
            calculateStorageTelemetry(context, videosFlat)
        }
        storageInfo = info
        isLoading = false
    }

    LaunchedEffect(videosFlat) {
        refreshStorageData()
    }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeTopAppBar(
                title = {
                    Text(
                        text = "Storage Analyzer",
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                scrollBehavior = scrollBehavior,
                colors = TopAppBarDefaults.largeTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    scrolledContainerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground,
                    navigationIconContentColor = MaterialTheme.colorScheme.onBackground
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        if (isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // 1. Primary Overview Hero Card
                item {
                    StorageOverviewCard(storageInfo = storageInfo)
                }

                // 2. Exact Remaining & Total Breakdown Card with Unit Dropdown
                item {
                    SectionHeaderWithDropdown(
                        title = "Exact Storage Breakdown",
                        selectedUnit = selectedUnit,
                        onSelectUnit = { selectedUnit = it }
                    )
                    ExactUnitsCard(
                        storageInfo = storageInfo,
                        selectedUnit = selectedUnit
                    )
                }

                // 3. Media Footprint Breakdown Card
                item {
                    SectionHeader(title = "Video Storage Footprint")
                    VideoFootprintCard(storageInfo = storageInfo)
                }

                // 4. Top Folders by Storage Consumption
                if (storageInfo.topFolders.isNotEmpty()) {
                    item {
                        SectionHeader(title = "Largest Video Folders")
                    }

                    items(
                        items = storageInfo.topFolders,
                        key = { it.name + it.path },
                        contentType = { "folder_storage_item" }
                    ) { folder ->
                        FolderStorageRowCard(folder = folder)
                    }
                }

                // 5. Largest Single Video Files
                if (storageInfo.largestVideos.isNotEmpty()) {
                    item {
                        SectionHeader(title = "Largest Video Files")
                    }

                    items(
                        items = storageInfo.largestVideos,
                        key = { it.uri },
                        contentType = { "largest_video_item" }
                    ) { video ->
                        LargestVideoItemCard(
                            video = video,
                            onClick = {
                                val uri = Uri.parse(video.uri)
                                onVideoClick(uri, listOf(uri))
                            }
                        )
                    }
                }

                // 6. Quick Recycle Bin Action Card
                item {
                    SectionHeader(title = "Cleanup Tools")
                    RecycleBinShortcutCard(onClick = onNavigateToRecycleBin)
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 4.dp, top = 6.dp, bottom = 2.dp)
    )
}

@Composable
private fun SectionHeaderWithDropdown(
    title: String,
    selectedUnit: StorageUnitDisplay,
    onSelectUnit: (StorageUnitDisplay) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 4.dp, end = 2.dp, top = 6.dp, bottom = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )

        Box {
            Surface(
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .clickable { expanded = true },
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = selectedUnit.shortName,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Icon(
                        imageVector = Icons.Default.ArrowDropDown,
                        contentDescription = "Select Unit",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false }
            ) {
                StorageUnitDisplay.entries.forEach { unit ->
                    DropdownMenuItem(
                        text = {
                            Text(
                                text = unit.label,
                                fontWeight = if (unit == selectedUnit) FontWeight.Bold else FontWeight.Normal,
                                color = if (unit == selectedUnit) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                            )
                        },
                        trailingIcon = {
                            if (unit == selectedUnit) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        },
                        onClick = {
                            onSelectUnit(unit)
                            expanded = false
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun StorageOverviewCard(storageInfo: ExactStorageInfo) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 3.dp
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Storage,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Column {
                        Text(
                            text = "Internal Storage",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "${formatDouble(storageInfo.usedBytes.toDouble() / 1024 / 1024 / 1024)} GB used of ${formatDouble(storageInfo.totalBytes.toDouble() / 1024 / 1024 / 1024)} GB",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                ) {
                    Text(
                        text = "${String.format(Locale.US, "%.1f", storageInfo.usedPercentage)}%",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                    )
                }
            }

            // Visual segmented progress bar
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                LinearProgressIndicator(
                    progress = { (storageInfo.usedPercentage / 100f).coerceIn(0f, 1f) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(10.dp)
                        .clip(RoundedCornerShape(5.dp)),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceContainerHighest
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Used: ${formatBytesToGb(storageInfo.usedBytes)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "Free: ${formatBytesToGb(storageInfo.freeBytes)}",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            HorizontalDivider(
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
            )

            // 3 Status Badges
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                StatusPill(
                    label = "Remaining",
                    value = formatBytesToGb(storageInfo.freeBytes),
                    color = MaterialTheme.colorScheme.primary
                )
                StatusPill(
                    label = "Used Space",
                    value = formatBytesToGb(storageInfo.usedBytes),
                    color = MaterialTheme.colorScheme.secondary
                )
                StatusPill(
                    label = "Total Capacity",
                    value = formatBytesToGb(storageInfo.totalBytes),
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

@Composable
private fun StatusPill(
    label: String,
    value: String,
    color: Color
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = color
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun ExactUnitsCard(
    storageInfo: ExactStorageInfo,
    selectedUnit: StorageUnitDisplay
) {
    val numberFormat = remember { NumberFormat.getNumberInstance(Locale.US) }

    val freeStr = remember(storageInfo.freeBytes, selectedUnit) {
        formatValueForUnit(storageInfo.freeBytes, selectedUnit, numberFormat)
    }
    val usedStr = remember(storageInfo.usedBytes, selectedUnit) {
        formatValueForUnit(storageInfo.usedBytes, selectedUnit, numberFormat)
    }
    val totalStr = remember(storageInfo.totalBytes, selectedUnit) {
        formatValueForUnit(storageInfo.totalBytes, selectedUnit, numberFormat)
    }
    val videoStr = remember(storageInfo.videoTotalBytes, selectedUnit) {
        formatValueForUnit(storageInfo.videoTotalBytes, selectedUnit, numberFormat)
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 2.dp
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            TelemetryRow(
                label = "Remaining Free Space",
                value = freeStr
            )
            DividerLine()
            TelemetryRow(
                label = "Used Space",
                value = "$usedStr (${String.format(Locale.US, "%.1f", storageInfo.usedPercentage)}%)"
            )
            DividerLine()
            TelemetryRow(
                label = "Total Capacity",
                value = totalStr
            )
            DividerLine()
            TelemetryRow(
                label = "Video Files Size",
                value = "$videoStr (${storageInfo.videoCount} files)"
            )
        }
    }
}

@Composable
private fun VideoFootprintCard(storageInfo: ExactStorageInfo) {
    val numberFormat = remember { NumberFormat.getNumberInstance(Locale.US) }
    val videoPercentOfUsed = if (storageInfo.usedBytes > 0) {
        (storageInfo.videoTotalBytes.toFloat() / storageInfo.usedBytes.toFloat()) * 100f
    } else 0f
    val videoPercentOfTotal = if (storageInfo.totalBytes > 0) {
        (storageInfo.videoTotalBytes.toFloat() / storageInfo.totalBytes.toFloat()) * 100f
    } else 0f

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 2.dp
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            TelemetryRow(
                label = "Videos Indexed in App",
                value = "${storageInfo.videoCount} media files"
            )
            DividerLine()
            TelemetryRow(
                label = "Total Video Storage",
                value = "${formatSize(storageInfo.videoTotalBytes)} (${numberFormat.format(storageInfo.videoTotalBytes)} Bytes)"
            )
            DividerLine()
            TelemetryRow(
                label = "Share of Used Storage",
                value = "${String.format(Locale.US, "%.1f", videoPercentOfUsed)}%"
            )
            DividerLine()
            TelemetryRow(
                label = "Share of Total Storage",
                value = "${String.format(Locale.US, "%.1f", videoPercentOfTotal)}%"
            )
        }
    }
}

@Composable
private fun FolderStorageRowCard(folder: FolderStorageItem) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 1.dp
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.secondaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Folder,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.size(20.dp)
                )
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = folder.name,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${folder.count} videos • ${formatSize(folder.totalBytes)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Surface(
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHighest
            ) {
                Text(
                    text = "${String.format(Locale.US, "%.1f", folder.percentageOfVideos)}%",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
        }
    }
}

@Composable
private fun LargestVideoItemCard(
    video: Video,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 1.dp
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
                    .size(240, 160)
                    .precision(coil3.size.Precision.INEXACT)
                    .crossfade(false)
                    .build()
            }
            Box(
                modifier = Modifier
                    .size(width = 88.dp, height = 58.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest)
            ) {
                AsyncImage(
                    model = imageRequest,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )

                if (video.duration > 0L) {
                    Surface(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(3.dp),
                        shape = RoundedCornerShape(4.dp),
                        color = Color.Black.copy(alpha = 0.75f)
                    ) {
                        Text(
                            text = formatDuration(video.duration),
                            style = MaterialTheme.typography.labelSmall,
                            fontSize = 9.sp,
                            color = Color.White,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                        )
                    }
                }
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Text(
                    text = video.title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${video.folderName} • ${formatSize(video.size)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Icon(
                imageVector = Icons.Default.PlayArrow,
                contentDescription = "Play",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(22.dp)
            )
        }
    }
}

@Composable
private fun RecycleBinShortcutCard(onClick: () -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 1.dp
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.errorContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.size(20.dp)
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Manage Recycle Bin",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "Review and delete trashed videos to free space",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
private fun TelemetryRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.End,
            modifier = Modifier.weight(1.2f)
        )
    }
}

@Composable
private fun DividerLine() {
    HorizontalDivider(
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
        modifier = Modifier.padding(vertical = 2.dp)
    )
}

private fun formatValueForUnit(
    bytes: Long,
    unit: StorageUnitDisplay,
    numberFormat: NumberFormat
): String {
    return when (unit) {
        StorageUnitDisplay.GB -> {
            val gb = bytes.toDouble() / (1024.0 * 1024.0 * 1024.0)
            "${String.format(Locale.US, "%.3f", gb)} GB"
        }
        StorageUnitDisplay.MB -> {
            val mb = bytes / (1024L * 1024L)
            "${numberFormat.format(mb)} MB"
        }
        StorageUnitDisplay.KB -> {
            val kb = bytes / 1024L
            "${numberFormat.format(kb)} KB"
        }
        StorageUnitDisplay.BYTES -> {
            "${numberFormat.format(bytes)} Bytes"
        }
    }
}

private fun calculateStorageTelemetry(
    context: Context,
    videos: List<Video>
): ExactStorageInfo {
    return try {
        val path = Environment.getExternalStorageDirectory().absolutePath
        val stat = StatFs(path)
        val blockSize = stat.blockSizeLong
        val totalBlocks = stat.blockCountLong
        val availableBlocks = stat.availableBlocksLong

        val totalBytes = totalBlocks * blockSize
        val freeBytes = availableBlocks * blockSize
        val usedBytes = (totalBytes - freeBytes).coerceAtLeast(0L)

        val usedPercentage = if (totalBytes > 0) (usedBytes.toFloat() / totalBytes.toFloat()) * 100f else 0f
        val freePercentage = if (totalBytes > 0) (freeBytes.toFloat() / totalBytes.toFloat()) * 100f else 0f

        val videoTotalBytes = videos.sumOf { it.size }
        val videoCount = videos.size

        val topFolders = videos.groupBy { it.folderName.ifBlank { "Root" } }
            .map { (name, vids) ->
                val folderBytes = vids.sumOf { it.size }
                val pct = if (videoTotalBytes > 0) (folderBytes.toFloat() / videoTotalBytes.toFloat()) * 100f else 0f
                FolderStorageItem(
                    name = name,
                    path = vids.firstOrNull()?.path?.substringBeforeLast('/') ?: "",
                    count = vids.size,
                    totalBytes = folderBytes,
                    percentageOfVideos = pct
                )
            }
            .sortedByDescending { it.totalBytes }
            .take(6)

        val largestVideos = videos.sortedByDescending { it.size }.take(5)

        ExactStorageInfo(
            totalBytes = totalBytes,
            freeBytes = freeBytes,
            usedBytes = usedBytes,
            usedPercentage = usedPercentage,
            freePercentage = freePercentage,
            videoTotalBytes = videoTotalBytes,
            videoCount = videoCount,
            topFolders = topFolders,
            largestVideos = largestVideos
        )
    } catch (e: Exception) {
        ExactStorageInfo()
    }
}

private fun formatBytesToGb(bytes: Long): String {
    val gb = bytes.toDouble() / (1024.0 * 1024.0 * 1024.0)
    return String.format(Locale.US, "%.2f GB", gb)
}

private fun formatDouble(value: Double): String {
    return String.format(Locale.US, "%.2f", value)
}
