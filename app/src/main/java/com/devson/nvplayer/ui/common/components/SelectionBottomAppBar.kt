package com.devson.nvplayer.ui.common.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DriveFileMove
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DriveFileRenameOutline
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.devson.nvplayer.domain.model.Video
import com.devson.nvplayer.domain.model.VideoFolder
import com.devson.nvplayer.domain.model.ViewSettings

@Composable
fun SelectionBottomAppBar(
    selectedFolders: Set<VideoFolder>,
    videosByFolder: Map<VideoFolder, List<Video>>,
    viewSettings: ViewSettings,
    onClearSelection: () -> Unit,
    onMove: () -> Unit,
    onCopy: () -> Unit,
    onDelete: () -> Unit,
    onRename: () -> Unit,
    onShare: () -> Unit
) {
    var showShareWarningDialog by remember { mutableStateOf(false) }
    val totalFiles = remember(selectedFolders, videosByFolder) {
        selectedFolders.sumOf { folder -> (videosByFolder[folder] ?: emptyList()).size }
    }

    if (showShareWarningDialog) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showShareWarningDialog = false },
            title = {
                Text(
                    text = "Share Folder Files",
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleLarge
                )
            },
            text = {
                Text(
                    text = "Warning: You are sending $totalFiles file(s) at once across the selected folder(s). Are you sure you want to proceed?",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            },
            confirmButton = {
                androidx.compose.material3.Button(
                    onClick = {
                        showShareWarningDialog = false
                        onShare()
                    }
                ) {
                    Text("Share")
                }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(
                    onClick = { showShareWarningDialog = false }
                ) {
                    Text("Cancel")
                }
            },
            shape = RoundedCornerShape(24.dp)
        )
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 16.dp, start = 16.dp, end = 16.dp),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.94f),
            contentColor = MaterialTheme.colorScheme.onSurface,
            border = BorderStroke(
                1.dp,
                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
            ),
            tonalElevation = 6.dp,
            shadowElevation = 10.dp,
            modifier = Modifier.wrapContentWidth()
        ) {
            Row(
                modifier = Modifier
                    .animateContentSize(
                        animationSpec = spring(
                            dampingRatio = 0.55f,
                            stiffness = Spring.StiffnessLow
                        )
                    )
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Move
                ActionColumn(
                    icon = Icons.AutoMirrored.Filled.DriveFileMove,
                    label = "Move",
                    onClick = onMove
                )
                // Copy
                ActionColumn(
                    icon = Icons.Filled.ContentCopy,
                    label = "Copy",
                    onClick = onCopy
                )
                // Delete
                ActionColumn(
                    icon = Icons.Filled.Delete,
                    label = "Delete",
                    onClick = onDelete
                )
                // Rename with rubber animation
                AnimatedVisibility(
                    visible = selectedFolders.size == 1,
                    enter = expandHorizontally(
                        animationSpec = spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessLow),
                        expandFrom = Alignment.CenterHorizontally
                    ) + fadeIn(animationSpec = spring(stiffness = Spring.StiffnessLow)) + scaleIn(
                        animationSpec = spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessLow),
                        initialScale = 0.7f
                    ),
                    exit = shrinkHorizontally(
                        animationSpec = spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessLow),
                        shrinkTowards = Alignment.CenterHorizontally
                    ) + fadeOut(animationSpec = spring(stiffness = Spring.StiffnessLow)) + scaleOut(
                        animationSpec = spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessLow),
                        targetScale = 0.7f
                    )
                ) {
                    ActionColumn(
                        icon = Icons.Filled.DriveFileRenameOutline,
                        label = "Rename",
                        onClick = onRename
                    )
                }
                // Share (with file count warning)
                ActionColumn(
                    icon = Icons.Filled.Share,
                    label = "Share",
                    onClick = {
                        if (totalFiles > 1) {
                            showShareWarningDialog = true
                        } else {
                            onShare()
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun ActionColumn(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit
) {
    ActionColumn(
        icon = icon,
        label = label,
        onClick = onClick,
        modifier = Modifier
    )
}

@Composable
private fun ActionColumn(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .clip(CircleShape)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(bounded = true)
            ) { onClick() }
            .padding(horizontal = 10.dp, vertical = 8.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(22.dp)
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = label,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1
        )
    }
}
