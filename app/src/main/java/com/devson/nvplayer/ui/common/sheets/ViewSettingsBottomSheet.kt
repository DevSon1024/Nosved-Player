package com.devson.nvplayer.ui.common.sheets

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.devson.nvplayer.domain.model.LayoutMode
import com.devson.nvplayer.domain.model.SortDirection
import com.devson.nvplayer.domain.model.ViewMode
import com.devson.nvplayer.domain.model.ViewSettings
import com.devson.nvplayer.ui.common.RotarySortWheelDialog
import com.devson.nvplayer.util.formatSortField
import com.devson.nvplayer.viewmodel.VideoListViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ViewSettingsBottomSheet(
    settings: ViewSettings,
    isFolderView: Boolean = false,
    onDismiss: () -> Unit,
    viewModel: VideoListViewModel
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)
    var isFieldsExpanded by remember { mutableStateOf(false) }
    var isAdvancedExpanded by remember { mutableStateOf(false) }
    var showSortWheel by remember { mutableStateOf(false) }

    SmoothModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        dragHandle = { BottomSheetDefaults.DragHandle() }
    ) { dismissWithAnimation ->
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState())
                .padding(bottom = 16.dp)
        ) {
            // Header Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Layout Settings",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                IconButton(onClick = dismissWithAnimation, modifier = Modifier.size(32.dp)) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = "Close",
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            // Section 1: View Mode
            SettingsSectionHeader(text = "View Mode")
            Spacer(modifier = Modifier.height(6.dp))
            SingleChoiceSegmentedButtonRow(
                modifier = Modifier.fillMaxWidth()
            ) {
                SegmentedButton(
                    selected = settings.viewMode == ViewMode.ALL_FOLDERS,
                    onClick = { viewModel.updateViewMode(ViewMode.ALL_FOLDERS) },
                    shape = SegmentedButtonDefaults.itemShape(index = 0, count = 3),
                    icon = { Icon(if (settings.viewMode == ViewMode.ALL_FOLDERS) Icons.Filled.FolderCopy else Icons.Outlined.FolderCopy, contentDescription = null, modifier = Modifier.size(16.dp)) }
                ) {
                    Text("Folders", style = MaterialTheme.typography.labelSmall)
                }
                SegmentedButton(
                    selected = settings.viewMode == ViewMode.FILES,
                    onClick = { viewModel.updateViewMode(ViewMode.FILES) },
                    shape = SegmentedButtonDefaults.itemShape(index = 1, count = 3),
                    icon = { Icon(if (settings.viewMode == ViewMode.FILES) Icons.Filled.Description else Icons.Outlined.Description, contentDescription = null, modifier = Modifier.size(16.dp)) }
                ) {
                    Text("Files", style = MaterialTheme.typography.labelSmall)
                }
                SegmentedButton(
                    selected = settings.viewMode == ViewMode.FOLDERS,
                    onClick = { viewModel.updateViewMode(ViewMode.FOLDERS) },
                    shape = SegmentedButtonDefaults.itemShape(index = 2, count = 3),
                    icon = { Icon(if (settings.viewMode == ViewMode.FOLDERS) Icons.Filled.Folder else Icons.Outlined.Folder, contentDescription = null, modifier = Modifier.size(16.dp)) }
                ) {
                    Text("Explorer", style = MaterialTheme.typography.labelSmall)
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Section 2: Layout Style & Sort Order Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    SettingsSectionHeader(text = "Layout")
                    Spacer(modifier = Modifier.height(4.dp))
                    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                        SegmentedButton(
                            selected = settings.layoutMode == LayoutMode.LIST,
                            onClick = { viewModel.updateLayoutMode(LayoutMode.LIST) },
                            shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                            icon = { Icon(if (settings.layoutMode == LayoutMode.LIST) Icons.Filled.ViewAgenda else Icons.Outlined.ViewAgenda, contentDescription = null, modifier = Modifier.size(16.dp)) }
                        ) {
                            Text("List", style = MaterialTheme.typography.labelSmall)
                        }
                        SegmentedButton(
                            selected = settings.layoutMode == LayoutMode.GRID,
                            onClick = { viewModel.updateLayoutMode(LayoutMode.GRID) },
                            shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                            icon = { Icon(if (settings.layoutMode == LayoutMode.GRID) Icons.Filled.GridView else Icons.Outlined.GridView, contentDescription = null, modifier = Modifier.size(16.dp)) }
                        ) {
                            Text("Grid", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }

                Column(modifier = Modifier.weight(1f)) {
                    SettingsSectionHeader(text = "Sort Order")
                    Spacer(modifier = Modifier.height(4.dp))
                    FilledTonalButton(
                        onClick = { showSortWheel = true },
                        modifier = Modifier.fillMaxWidth(),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp)
                    ) {
                        Icon(
                            imageVector = if (settings.sortDirection == SortDirection.ASCENDING) Icons.Outlined.ArrowUpward else Icons.Outlined.ArrowDownward,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = formatSortField(settings.sortField),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1
                        )
                    }
                }
            }

            if (settings.layoutMode == LayoutMode.GRID) {
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    SettingsSectionHeader(text = "Grid Columns")
                    SingleChoiceSegmentedButtonRow {
                        (1..4).forEachIndexed { index, columns ->
                            SegmentedButton(
                                selected = settings.gridColumns == columns,
                                onClick = { viewModel.updateGridColumns(columns) },
                                shape = SegmentedButtonDefaults.itemShape(index = index, count = 4)
                            ) {
                                Text(columns.toString(), style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
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

            HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))

            // Section 3: Expandable Metadata Fields Dropdown
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { isFieldsExpanded = !isFieldsExpanded }
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Outlined.Visibility,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = "Visible Metadata Fields",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        Icon(
                            imageVector = if (isFieldsExpanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                            contentDescription = if (isFieldsExpanded) "Collapse" else "Expand",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    AnimatedVisibility(
                        visible = isFieldsExpanded,
                        enter = expandVertically() + fadeIn(),
                        exit = shrinkVertically() + fadeOut()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 12.dp, end = 12.dp, bottom = 10.dp),
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            val fieldItems: List<Triple<String, Boolean, (Boolean) -> Unit>> = listOf(
                                Triple("Thumbnail", settings.showThumbnail) { viewModel.updateShowThumbnail(it) },
                                Triple("Length", settings.showLength) { viewModel.updateShowLength(it) },
                                Triple("File Ext.", settings.showFileExtension) { viewModel.updateShowFileExtension(it) },
                                Triple("Played Time", settings.showPlayedTime) { viewModel.updateShowPlayedTime(it) },
                                Triple("Resolution", settings.showResolution) { viewModel.updateShowResolution(it) },
                                Triple("Path", settings.showPath) { viewModel.updateShowPath(it) },
                                Triple("Size", settings.showSize) { viewModel.updateShowSize(it) },
                                Triple("Date", settings.showDate) { viewModel.updateShowDate(it) },
                                Triple("FPS", settings.showFrameRate) { viewModel.updateShowFrameRate(it) }
                            )

                            val chunked = fieldItems.chunked(3)
                            chunked.forEach { rowItems ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    rowItems.forEach { (label, checked, onChange) ->
                                        Box(Modifier.weight(1f)) {
                                            CompactMetadataToggle(
                                                label = label,
                                                checked = checked,
                                                onCheckedChange = onChange
                                            )
                                        }
                                    }
                                    repeat(3 - rowItems.size) { Box(Modifier.weight(1f)) }
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Section 4: Expandable Advanced Options Dropdown
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { isAdvancedExpanded = !isAdvancedExpanded }
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Outlined.Tune,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = "Advanced Options",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        Icon(
                            imageVector = if (isAdvancedExpanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                            contentDescription = if (isAdvancedExpanded) "Collapse" else "Expand",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    AnimatedVisibility(
                        visible = isAdvancedExpanded,
                        enter = expandVertically() + fadeIn(),
                        exit = shrinkVertically() + fadeOut()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 12.dp, end = 12.dp, bottom = 10.dp)
                        ) {
                            AdvancedToggleRow(
                                label = "Select via Thumbnail",
                                checked = settings.selectByThumbnail,
                                subtitle = "Click thumbnail to select video in list layout"
                            ) { viewModel.updateSelectByThumbnail(it) }
                            AdvancedToggleRow(
                                label = "Length over Thumbnail",
                                checked = settings.displayLengthOverThumbnail,
                                subtitle = "Overlay duration badge on top of thumbnail"
                            ) { viewModel.updateDisplayLengthOverThumbnail(it) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SettingsSectionHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.primary
    )
}

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
            modifier = Modifier.size(28.dp)
        )
        Spacer(Modifier.width(2.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, maxLines = 1)
    }
}

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

@Composable
fun AdvancedToggleRow(
    label: String,
    checked: Boolean,
    subtitle: String? = null,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.small)
            .clickable { onCheckedChange(!checked) }
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
            Text(label, style = MaterialTheme.typography.bodySmall)
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f)
                )
            }
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
fun SettingsSectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.primary
    )
}

@Composable
fun IconToggleButton(
    label: String,
    selected: Boolean,
    selectedIcon: ImageVector,
    unselectedIcon: ImageVector,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
    val icon = if (selected) selectedIcon else unselectedIcon
    val fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal

    Column(
        modifier = modifier
            .clip(MaterialTheme.shapes.small)
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp, horizontal = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = color,
            modifier = Modifier.size(28.dp)
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = fontWeight,
            color = color,
            maxLines = 1,
            softWrap = false
        )
    }
}