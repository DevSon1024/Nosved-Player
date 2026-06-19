package com.devson.nvplayer.ui.screen.settings

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.devson.nvplayer.data.repository.FolderFilterMode
import com.devson.nvplayer.viewmodel.SettingsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FolderScreen(
    onNavigateBack: () -> Unit,
    onNavigateToExplorer: () -> Unit,  // kept for backward compat, not used
    settingsViewModel: SettingsViewModel = viewModel()
) {
    val playbackSettings by settingsViewModel.playbackSettings.collectAsState()
    val blacklistedFolders = playbackSettings.blacklistedFolders
    val whitelistedFolders = playbackSettings.whitelistedFolders
    val filterMode = playbackSettings.folderFilterMode

    // Which list tab is shown: 0=Whitelist, 1=Blacklist
    var selectedTab by remember { mutableIntStateOf(if (filterMode == FolderFilterMode.WHITELIST) 0 else 1) }
    var showPicker by remember { mutableStateOf(false) }

    val currentList = if (selectedTab == 0) whitelistedFolders else blacklistedFolders
    val currentListSorted = remember(currentList) { currentList.sorted() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Folder Visibility",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                actions = {
                    if (currentList.isNotEmpty()) {
                        TextButton(
                            onClick = {
                                if (selectedTab == 0) settingsViewModel.clearWhitelist()
                                else settingsViewModel.clearBlacklist()
                            }
                        ) {
                            Text(
                                text = "Clear All",
                                color = MaterialTheme.colorScheme.error,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        floatingActionButton = {
            AnimatedVisibility(
                visible = filterMode != FolderFilterMode.NONE,
                enter = scaleIn(spring()) + fadeIn(),
                exit = scaleOut(spring()) + fadeOut()
            ) {
                ExtendedFloatingActionButton(
                    onClick = { showPicker = true },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Icon(imageVector = Icons.Default.Add, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Add Folder",
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {

            //  Filter Mode Section 
            FilterModeSection(
                currentMode = filterMode,
                onModeChanged = { mode ->
                    settingsViewModel.setFolderFilterMode(mode)
                    // Sync tab to reflect active mode
                    when (mode) {
                        FolderFilterMode.WHITELIST -> selectedTab = 0
                        FolderFilterMode.BLACKLIST -> selectedTab = 1
                        FolderFilterMode.NONE -> { /* keep current tab */ }
                    }
                }
            )

            Spacer(modifier = Modifier.height(8.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

            //  Tab row 
            TabRow(
                selectedTabIndex = selectedTab,
                containerColor = MaterialTheme.colorScheme.background,
                contentColor = MaterialTheme.colorScheme.primary
            ) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.CheckCircle,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Text("Whitelist (${whitelistedFolders.size})")
                        }
                    }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Block,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Text("Blacklist (${blacklistedFolders.size})")
                        }
                    }
                )
            }

            //  Content 
            AnimatedContent(
                targetState = selectedTab,
                transitionSpec = { fadeIn() togetherWith fadeOut() },
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f),
                label = "tab_content"
            ) { tab ->
                val list = if (tab == 0) whitelistedFolders.sorted() else blacklistedFolders.sorted()
                val modeActive = when (tab) {
                    0 -> filterMode == FolderFilterMode.WHITELIST
                    else -> filterMode == FolderFilterMode.BLACKLIST
                }
                val icon = if (tab == 0) Icons.Rounded.CheckCircle else Icons.Rounded.Block
                val emptyTitle = if (tab == 0) "No whitelisted folders" else "No blacklisted folders"
                val emptyBody = if (tab == 0)
                    "Add folders to the whitelist. Only videos from these folders will be shown (when Whitelist mode is active)."
                else
                    "Add folders to the blacklist. Videos from these folders will be hidden (when Blacklist mode is active)."

                if (list.isEmpty()) {
                    EmptyFolderState(
                        icon = icon,
                        title = emptyTitle,
                        body = emptyBody,
                        modeActive = modeActive,
                        onAddClick = if (filterMode != FolderFilterMode.NONE) ({ showPicker = true }) else null
                    )
                } else {
                    LazyColumn(
                        contentPadding = PaddingValues(
                            start = 16.dp, end = 16.dp, top = 12.dp, bottom = 100.dp
                        ),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(
                            items = list,
                            key = { it },
                            contentType = { "folder_path" }
                        ) { path ->
                            FolderPathRow(
                                path = path,
                                onRemove = {
                                    if (tab == 0) settingsViewModel.removeFromWhitelist(path)
                                    else settingsViewModel.removeFromBlacklist(path)
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    //  Folder Picker Bottom Sheet 
    if (showPicker) {
        FolderPickerBottomSheet(
            onFolderSelected = { path ->
                showPicker = false
                if (selectedTab == 0) settingsViewModel.addToWhitelist(path)
                else settingsViewModel.addToBlacklist(listOf(path))
            },
            onDismiss = { showPicker = false }
        )
    }
}

// 
// Filter Mode Selector Card
// 

@Composable
private fun FilterModeSection(
    currentMode: FolderFilterMode,
    onModeChanged: (FolderFilterMode) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            text = "Filter Mode",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterModeChip(
                label = "None",
                description = "Show all",
                icon = Icons.Rounded.AllInclusive,
                selected = currentMode == FolderFilterMode.NONE,
                onClick = { onModeChanged(FolderFilterMode.NONE) },
                modifier = Modifier.weight(1f)
            )
            FilterModeChip(
                label = "Whitelist",
                description = "Only listed",
                icon = Icons.Rounded.CheckCircle,
                selected = currentMode == FolderFilterMode.WHITELIST,
                onClick = { onModeChanged(FolderFilterMode.WHITELIST) },
                modifier = Modifier.weight(1f),
                activeColor = MaterialTheme.colorScheme.primaryContainer
            )
            FilterModeChip(
                label = "Blacklist",
                description = "Hide listed",
                icon = Icons.Rounded.Block,
                selected = currentMode == FolderFilterMode.BLACKLIST,
                onClick = { onModeChanged(FolderFilterMode.BLACKLIST) },
                modifier = Modifier.weight(1f),
                activeColor = MaterialTheme.colorScheme.errorContainer
            )
        }

        // Active mode hint
        AnimatedContent(
            targetState = currentMode,
            transitionSpec = { fadeIn() togetherWith fadeOut() },
            label = "mode_hint"
        ) { mode ->
            val (text, color) = when (mode) {
                FolderFilterMode.NONE -> "All videos are visible. No folder filtering applied." to
                        MaterialTheme.colorScheme.onSurfaceVariant
                FolderFilterMode.WHITELIST -> "Only videos in whitelisted folders are shown. An empty whitelist hides everything." to
                        MaterialTheme.colorScheme.primary
                FolderFilterMode.BLACKLIST -> "Videos in blacklisted folders are hidden. Other videos remain visible." to
                        MaterialTheme.colorScheme.error
            }
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = color.copy(alpha = 0.08f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = text,
                    style = MaterialTheme.typography.bodySmall,
                    color = color.copy(alpha = 0.9f),
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                )
            }
        }
    }
}

@Composable
private fun FilterModeChip(
    label: String,
    description: String,
    icon: ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    activeColor: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.primaryContainer
) {
    val containerColor = if (selected) activeColor else MaterialTheme.colorScheme.surfaceContainerHigh
    val contentColor = if (selected) MaterialTheme.colorScheme.onPrimaryContainer
    else MaterialTheme.colorScheme.onSurfaceVariant

    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = containerColor,
        tonalElevation = if (selected) 2.dp else 0.dp
    ) {
        Column(
            modifier = Modifier.padding(vertical = 10.dp, horizontal = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.size(20.dp)
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                color = contentColor
            )
            Text(
                text = description,
                style = MaterialTheme.typography.labelSmall,
                color = contentColor.copy(alpha = 0.7f),
                maxLines = 1,
                textAlign = TextAlign.Center
            )
        }
    }
}

// 
// Empty state
// 

@Composable
private fun EmptyFolderState(
    icon: ImageVector,
    title: String,
    body: String,
    modeActive: Boolean,
    onAddClick: (() -> Unit)?
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(80.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f))
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(40.dp)
            )
        }
        Spacer(modifier = Modifier.height(20.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onBackground
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = body,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
            textAlign = TextAlign.Center
        )

        if (!modeActive) {
            Spacer(modifier = Modifier.height(16.dp))
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Info,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = "Enable this mode above to activate filtering.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
            }
        }

        if (onAddClick != null) {
            Spacer(modifier = Modifier.height(20.dp))
            OutlinedButton(
                onClick = onAddClick,
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text("Add a Folder")
            }
        }
    }
}

// 
// Folder path row
// 

@Composable
private fun FolderPathRow(
    path: String,
    onRemove: () -> Unit
) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f))
            ) {
                Icon(
                    imageVector = Icons.Rounded.Folder,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(22.dp)
                )
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                val folderName = remember(path) {
                    path.substringAfterLast('/', path.substringAfterLast('\\', "Folder"))
                }
                Text(
                    text = folderName,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = path,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            IconButton(
                onClick = onRemove,
                colors = IconButtonDefaults.iconButtonColors(
                    contentColor = MaterialTheme.colorScheme.error
                )
            ) {
                Icon(
                    imageVector = Icons.Default.DeleteOutline,
                    contentDescription = "Remove"
                )
            }
        }
    }
}
