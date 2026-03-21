package com.devson.nosvedplayer.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.devson.nosvedplayer.model.SortOrder
import com.devson.nosvedplayer.model.ViewSettings
import com.devson.nosvedplayer.utility.formatSortOrder
import com.devson.nosvedplayer.viewmodel.VideoListViewModel

// VIEW SETTINGS BOTTOM SHEET

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ViewSettingsBottomSheet(
    settings: ViewSettings,
    isFolderView: Boolean,
    onDismiss: () -> Unit,
    viewModel: VideoListViewModel
) {
    val sheetState = rememberModalBottomSheetState()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface
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

            if (!isFolderView) {
                Text("Show Metadata", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                MetadataToggle("Thumbnail", settings.showThumbnail) { viewModel.updateShowThumbnail(it) }
                MetadataToggle("Duration", settings.showDuration) { viewModel.updateShowDuration(it) }
                MetadataToggle("File Size", settings.showSize) { viewModel.updateShowSize(it) }
                MetadataToggle("Date Added", settings.showDate) { viewModel.updateShowDate(it) }
                MetadataToggle("File Extension", settings.showFileExtension) { viewModel.updateShowFileExtension(it) }
            } else {
                Text("Folder Metadata", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                MetadataToggle("Video Count", settings.showFolderVideoCount) { viewModel.updateShowFolderVideoCount(it) }
                MetadataToggle("Folder Size", settings.showFolderSize) { viewModel.updateShowFolderSize(it) }
                MetadataToggle("Created At", settings.showFolderDate) { viewModel.updateShowFolderDate(it) }
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

//  RENAME DIALOG
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RenameDialog(
    initialName: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var text by remember { mutableStateOf(initialName) }
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = { if (text.isNotBlank()) onConfirm(text.trim()) },
                enabled = text.isNotBlank()
            ) { Text("Rename") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
        title = { Text("Rename Video", fontWeight = FontWeight.Bold) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                label = { Text("New file name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        }
    )
}

// METADATA TOGGLE ROW
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