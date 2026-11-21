package com.devson.nvplayer.ui.screens.videolist.components.selection

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
import androidx.compose.material.icons.automirrored.filled.Label
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
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import com.devson.nvplayer.domain.model.Video
import com.devson.nvplayer.util.TagStatusDialog

@Composable
fun VideoSelectionBottomBar(
    selectedVideos: Set<Video>,
    onMove: () -> Unit,
    onCopy: () -> Unit,
    onDelete: () -> Unit,
    onRename: () -> Unit,
    onShare: () -> Unit,
    onMarkStatus: (String) -> Unit,
    onAddToVault: (() -> Unit)? = null,
    onExtractSubtitles: (() -> Unit)? = null
) {
    VideoSelectionBottomBar(
        selectedVideos = selectedVideos,
        onMove = onMove,
        onCopy = onCopy,
        onDelete = onDelete,
        onRename = onRename,
        onShare = onShare,
        onMarkStatus = onMarkStatus,
        onAddToVault = onAddToVault,
        onExtractSubtitles = onExtractSubtitles,
        showTagAndShare = true
    )
}

@Composable
fun VideoSelectionBottomBar(
    selectedVideos: Set<Video>,
    onMove: () -> Unit,
    onCopy: () -> Unit,
    onDelete: () -> Unit,
    onRename: () -> Unit,
    onShare: () -> Unit,
    onMarkStatus: (String) -> Unit,
    onAddToVault: (() -> Unit)? = null,
    onExtractSubtitles: (() -> Unit)? = null,
    showTagAndShare: Boolean
) {
    var showTagDialog by remember { mutableStateOf(false) }
    var showMoreMenu by remember { mutableStateOf(false) }

    if (showTagDialog) {
        TagStatusDialog(
            onDismiss = { showTagDialog = false },
            onConfirm = { status ->
                showTagDialog = false
                onMarkStatus(status)
            }
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
                // 1. Move
                ActionColumn(
                    icon = Icons.AutoMirrored.Filled.DriveFileMove,
                    label = "Move",
                    onClick = onMove
                )
                // 2. Share
                if (showTagAndShare) {
                    ActionColumn(
                        icon = Icons.Filled.Share,
                        label = "Share",
                        onClick = onShare
                    )
                }
                // 3. Delete
                ActionColumn(
                    icon = Icons.Filled.Delete,
                    label = "Delete",
                    onClick = onDelete
                )
                // 4. Rename with rubber animation
                AnimatedVisibility(
                    visible = selectedVideos.size == 1,
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
                // 5. Tagging
                if (showTagAndShare) {
                    ActionColumn(
                        icon = Icons.AutoMirrored.Filled.Label,
                        label = "Tag",
                        onClick = { showTagDialog = true }
                    )
                }
                // 6. More (Copy & Add to Vault)
                Box {
                    ActionColumn(
                        icon = Icons.Default.MoreVert,
                        label = "More",
                        onClick = { showMoreMenu = true }
                    )
                    DropdownMenu(
                        expanded = showMoreMenu,
                        onDismissRequest = { showMoreMenu = false },
                        shape = RoundedCornerShape(16.dp),
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.94f)
                    ) {
                        if (selectedVideos.size == 1 && onExtractSubtitles != null) {
                            DropdownMenuItem(
                                text = { Text("Subtitle Extraction", color = MaterialTheme.colorScheme.onSurface) },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Filled.Subtitles,
                                        contentDescription = "Subtitle Extraction",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                },
                                onClick = {
                                    showMoreMenu = false
                                    onExtractSubtitles()
                                }
                            )
                        }
                        DropdownMenuItem(
                            text = { Text("Copy", color = MaterialTheme.colorScheme.onSurface) },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Filled.ContentCopy,
                                    contentDescription = "Copy",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            },
                            onClick = {
                                showMoreMenu = false
                                onCopy()
                            }
                        )
                        if (onAddToVault != null) {
                            DropdownMenuItem(
                                text = { Text(if (selectedVideos.size > 1) "Add all to Vault (${selectedVideos.size})" else "Add to Vault", color = MaterialTheme.colorScheme.onSurface) },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Filled.Lock,
                                        contentDescription = "Add to Vault",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                },
                                onClick = {
                                    showMoreMenu = false
                                    onAddToVault()
                                }
                            )
                        }
                    }
                }
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
