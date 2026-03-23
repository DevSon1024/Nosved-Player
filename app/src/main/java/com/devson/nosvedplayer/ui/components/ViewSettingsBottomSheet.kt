package com.devson.nosvedplayer.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
                .padding(horizontal = 16.dp, vertical = 4.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Text(
                "View Settings",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 12.dp)
            )

            //  View Mode 
            SettingsSectionLabel("View Mode")
            Spacer(modifier = Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                SegmentedToggleButton(
                    label = "All Folders",
                    selected = settings.viewMode == ViewMode.ALL_FOLDERS,
                    modifier = Modifier.weight(1f)
                ) { viewModel.updateViewMode(ViewMode.ALL_FOLDERS) }
                SegmentedToggleButton(
                    label = "Files",
                    selected = settings.viewMode == ViewMode.FILES,
                    modifier = Modifier.weight(1f)
                ) { viewModel.updateViewMode(ViewMode.FILES) }
                SegmentedToggleButton(
                    label = "Folders",
                    selected = settings.viewMode == ViewMode.FOLDERS,
                    modifier = Modifier.weight(1f)
                ) { viewModel.updateViewMode(ViewMode.FOLDERS) }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp))

            //  Layout 
            SettingsSectionLabel("Layout")
            Spacer(modifier = Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                SegmentedToggleButton(
                    label = "List",
                    selected = settings.layoutMode == LayoutMode.LIST,
                    modifier = Modifier.weight(1f)
                ) { viewModel.updateLayoutMode(LayoutMode.LIST) }
                SegmentedToggleButton(
                    label = "Grid",
                    selected = settings.layoutMode == LayoutMode.GRID,
                    modifier = Modifier.weight(1f)
                ) { viewModel.updateLayoutMode(LayoutMode.GRID) }
            }

            if (settings.layoutMode == LayoutMode.GRID) {
                Spacer(modifier = Modifier.height(10.dp))
                SettingsSectionLabel("Grid Columns")
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        (1..4).forEach { columns ->
                            val isSelected = settings.gridColumns == columns
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .background(
                                        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                                        shape = RoundedCornerShape(10.dp)
                                    )
                                    .border(
                                        width = 1.dp,
                                        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                                        shape = RoundedCornerShape(10.dp)
                                    )
                                    .clip(RoundedCornerShape(10.dp))
                                    .clickable { viewModel.updateGridColumns(columns) },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = columns.toString(),
                                    color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                )
                            }
                        }
                    }
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp))

            //  Sort 
            SettingsSectionLabel("Sort By")
            var showSortWheel by remember { mutableStateOf(false) }
            OutlinedButton(
                onClick = { showSortWheel = true },
                modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
            ) {
                val dirText = if (settings.sortDirection == SortDirection.ASCENDING) "↑ Ascending" else "↓ Descending"
                Text(
                    "${formatSortField(settings.sortField)}  $dirText",
                    style = MaterialTheme.typography.bodySmall
                )
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

            HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp))

            //  Fields (3 × 3 grid) 
            SettingsSectionLabel("Fields")
            Spacer(modifier = Modifier.height(4.dp))

            val fieldItems: List<Triple<String, Boolean, (Boolean) -> Unit>> = listOf(
                Triple("Thumbnail", settings.showThumbnail) { viewModel.updateShowThumbnail(it) },
                Triple("Length", settings.showLength) { viewModel.updateShowLength(it) },
                Triple("File Ext.", settings.showFileExtension) { viewModel.updateShowFileExtension(it) },
                Triple("Played Time", settings.showPlayedTime) { viewModel.updateShowPlayedTime(it) },
                Triple("Resolution", settings.showResolution) { viewModel.updateShowResolution(it) },
                Triple("Frame Rate", settings.showFrameRate) { viewModel.updateShowFrameRate(it) },
                Triple("Path", settings.showPath) { viewModel.updateShowPath(it) },
                Triple("Size", settings.showSize) { viewModel.updateShowSize(it) },
                Triple("Date", settings.showDate) { viewModel.updateShowDate(it) },
            )

            // 3 rows × 3 cols
            val chunked = fieldItems.chunked(3)
            Column(modifier = Modifier.fillMaxWidth()) {
                chunked.forEach { rowItems ->
                    Row(modifier = Modifier.fillMaxWidth()) {
                        rowItems.forEach { (label, checked, onChange) ->
                            Box(Modifier.weight(1f)) {
                                CompactMetadataToggle(
                                    label = label,
                                    checked = checked,
                                    onCheckedChange = onChange
                                )
                            }
                        }
                        // Pad remaining slots if last row has < 3 items
                        repeat(3 - rowItems.size) { Box(Modifier.weight(1f)) }
                    }
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp))

            //  Advanced 
            SettingsSectionLabel("Advanced")
            Spacer(modifier = Modifier.height(4.dp))
            AdvancedToggleRow("Length over Thumbnail", settings.displayLengthOverThumbnail) { viewModel.updateDisplayLengthOverThumbnail(it) }
            AdvancedToggleRow("Show Hidden Files", settings.showHiddenFiles) { viewModel.updateShowHiddenFiles(it) }
            AdvancedToggleRow("Recognize .nomedia", settings.recognizeNoMedia) { viewModel.updateRecognizeNoMedia(it) }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

//  HELPER COMPONENTS 

@Composable
private fun SettingsSectionLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.primary
    )
}

@Composable
private fun SegmentedToggleButton(
    label: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val bgColor = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
    val borderColor = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
    val textColor = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant

    Surface(
        modifier = modifier
            .height(36.dp)
            .border(1.dp, borderColor, RoundedCornerShape(10.dp))
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick),
        color = bgColor,
        shape = RoundedCornerShape(10.dp)
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                color = textColor
            )
        }
    }
}

// COMPACT METADATA TOGGLE (checkbox + small label)
@Composable
fun CompactMetadataToggle(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = Modifier.size(32.dp)
        )
        Text(label, style = MaterialTheme.typography.bodySmall, maxLines = 1)
    }
}

// METADATA TOGGLE ROW (kept for backward compatibility if used elsewhere)
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
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, style = MaterialTheme.typography.bodySmall)
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}