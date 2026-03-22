package com.devson.nosvedplayer.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.devson.nosvedplayer.model.LayoutMode
import com.devson.nosvedplayer.model.SortDirection
import com.devson.nosvedplayer.model.ViewMode
import com.devson.nosvedplayer.model.ViewSettings
import com.devson.nosvedplayer.util.formatSortField
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
                .verticalScroll(rememberScrollState())
        ) {
            Text(
                "View Settings",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            // View Mode Section
            Text("View Mode", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                ViewModeRadioButton("All Folders", ViewMode.ALL_FOLDERS, settings.viewMode) { viewModel.updateViewMode(it) }
                ViewModeRadioButton("Files", ViewMode.FILES, settings.viewMode) { viewModel.updateViewMode(it) }
                ViewModeRadioButton("Folders", ViewMode.FOLDERS, settings.viewMode) { viewModel.updateViewMode(it) }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

            // Layout Section
            Text("Layout", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(
                        selected = settings.layoutMode == LayoutMode.LIST,
                        onClick = { viewModel.updateLayoutMode(LayoutMode.LIST) }
                    )
                    Text("List", modifier = Modifier.clickable { viewModel.updateLayoutMode(LayoutMode.LIST) })
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(
                        selected = settings.layoutMode == LayoutMode.GRID,
                        onClick = { viewModel.updateLayoutMode(LayoutMode.GRID) }
                    )
                    Text("Grid", modifier = Modifier.clickable { viewModel.updateLayoutMode(LayoutMode.GRID) })
                }
            }

            if (settings.layoutMode == LayoutMode.GRID) {
                Spacer(modifier = Modifier.height(12.dp))
                Text("Grid Columns", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    (1..4).forEach { columns ->
                        val isSelected = settings.gridColumns == columns
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .background(
                                    color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
                                    shape = RoundedCornerShape(8.dp)
                                )
                                .border(
                                    width = 1.dp,
                                    color = if (isSelected) Color.Transparent else MaterialTheme.colorScheme.outlineVariant,
                                    shape = RoundedCornerShape(8.dp)
                                )
                                .clickable { viewModel.updateGridColumns(columns) },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = columns.toString(),
                                color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    }
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

            // Sort Section
            Text("Sort By", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            var showSortWheel by remember { mutableStateOf(false) }
            Box(modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                OutlinedButton(
                    onClick = { showSortWheel = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    val dirText = if (settings.sortDirection == SortDirection.ASCENDING) "Ascending" else "Descending"
                    Text("${formatSortField(settings.sortField)} ($dirText)")
                }
            }

            if (showSortWheel) {
                RotarySortWheelDialog(
                    currentSortField = settings.sortField,
                    sortDirection = settings.sortDirection,
                    onSortFieldSelected = { viewModel.updateSortField(it) },
                    onSortOrderToggled = { viewModel.updateSortDirection(it) },
                    onDismissRequest = { showSortWheel = false }
                )
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

            // Fields Section
            Text("Fields", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(8.dp))
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(modifier = Modifier.fillMaxWidth()) {
                    Box(Modifier.weight(1f)) { MetadataToggle("Thumbnail", settings.showThumbnail) { viewModel.updateShowThumbnail(it) } }
                    Box(Modifier.weight(1f)) { MetadataToggle("Length", settings.showLength) { viewModel.updateShowLength(it) } }
                }
                Row(modifier = Modifier.fillMaxWidth()) {
                    Box(Modifier.weight(1f)) { MetadataToggle("File Ext.", settings.showFileExtension) { viewModel.updateShowFileExtension(it) } }
                    Box(Modifier.weight(1f)) { MetadataToggle("Played Time", settings.showPlayedTime) { viewModel.updateShowPlayedTime(it) } }
                }
                Row(modifier = Modifier.fillMaxWidth()) {
                    Box(Modifier.weight(1f)) { MetadataToggle("Resolution", settings.showResolution) { viewModel.updateShowResolution(it) } }
                    Box(Modifier.weight(1f)) { MetadataToggle("Frame Rate", settings.showFrameRate) { viewModel.updateShowFrameRate(it) } }
                }
                Row(modifier = Modifier.fillMaxWidth()) {
                    Box(Modifier.weight(1f)) { MetadataToggle("Path", settings.showPath) { viewModel.updateShowPath(it) } }
                    Box(Modifier.weight(1f)) { MetadataToggle("Size", settings.showSize) { viewModel.updateShowSize(it) } }
                }
                Row(modifier = Modifier.fillMaxWidth()) {
                    Box(Modifier.weight(1f)) { MetadataToggle("Date", settings.showDate) { viewModel.updateShowDate(it) } }
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

            // Advanced Section
            Text("Advanced", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(8.dp))
            AdvancedToggleRow("Length over Thumbnail", settings.displayLengthOverThumbnail) { viewModel.updateDisplayLengthOverThumbnail(it) }
            AdvancedToggleRow("Hidden files (files with '.')", settings.showHiddenFiles) { viewModel.updateShowHiddenFiles(it) }
            AdvancedToggleRow("Recognize .nomedia", settings.recognizeNoMedia) { viewModel.updateRecognizeNoMedia(it) }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

// HELPER COMPONENTS

@Composable
fun ViewModeRadioButton(label: String, mode: ViewMode, selectedMode: ViewMode, onClick: (ViewMode) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(end = 8.dp)) {
        RadioButton(selected = mode == selectedMode, onClick = { onClick(mode) })
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.clickable { onClick(mode) })
    }
}

// RENAME DIALOG
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
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.Start,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(checked = checked, onCheckedChange = onCheckedChange)
        Spacer(modifier = Modifier.width(4.dp))
        Text(label, style = MaterialTheme.typography.bodyMedium)
    }
}

// ADVANCED TOGGLE ROW
@Composable
fun AdvancedToggleRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}