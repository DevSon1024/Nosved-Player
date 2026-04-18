package com.devson.nosvedplayer.ui.screens.videolist.components.selection

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DriveFileMove
import androidx.compose.material.icons.automirrored.filled.Label
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DriveFileRenameOutline
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.devson.nosvedplayer.model.Video
import com.devson.nosvedplayer.util.TagStatusDialog

@Composable
fun VideoSelectionBottomBar(
    selectedVideos: Set<Video>,
    onPlayAll: () -> Unit,
    onMove: () -> Unit,
    onCopy: () -> Unit,
    onDelete: () -> Unit,
    onRename: () -> Unit,
    onShowInfo: () -> Unit,
    onShare: () -> Unit,
    onMarkStatus: (String) -> Unit
) {
    var showTagDialog by remember { mutableStateOf(false) }

    if (showTagDialog) {
        TagStatusDialog(
            onDismiss = { showTagDialog = false },
            onConfirm = { status ->
                showTagDialog = false
                onMarkStatus(status)
            }
        )
    }

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
            ActionColumn(icon = Icons.AutoMirrored.Filled.DriveFileMove, label = "Move", onClick = onMove)
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
            // Share
            ActionColumn(icon = Icons.Filled.Share, label = "Share", onClick = onShare)
            // Info
            ActionColumn(icon = Icons.Filled.Info, label = "Info", onClick = onShowInfo)
            // Tagging
            ActionColumn(icon = Icons.AutoMirrored.Filled.Label, label = "Tag", onClick = { showTagDialog = true })
        }
    }
}

@Composable
private fun ActionColumn(
    icon: ImageVector,
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
