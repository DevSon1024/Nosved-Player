package com.devson.nvplayer.ui.screens.videolist.components.topbar

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.SoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.devson.nvplayer.domain.model.StorageVolumeInfo
import com.devson.nvplayer.ui.common.popup.SearchSuggestionsPopup

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VideoListTopAppBar(
    isSelectionActive: Boolean,
    titleText: String?,
    selectedCount: Int,
    totalCount: Int,
    showBackButton: Boolean,
    showHomeBackButton: Boolean = true,
    onClearSelection: () -> Unit,
    onShowInfo: () -> Unit,
    onSelectAll: () -> Unit,
    onBack: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onShowSettings: () -> Unit,
    onBackToFolders: () -> Unit,
    onSearch: (String) -> Unit = {},
    searchActive: Boolean = false,
    searchText: String = "",
    onSearchActiveChange: (Boolean) -> Unit = {},
    onSearchTextChange: (String) -> Unit = {},
    searchSuggestions: List<String> = emptyList(),
    searchFocusRequester: FocusRequester = remember { FocusRequester() },
    keyboard: SoftwareKeyboardController? = null,
    onRecycleBinClick: (() -> Unit)? = null,
    onPlayFolder: (() -> Unit)? = null,
    onNetworkStreamClick: (() -> Unit)? = null,
    // Storage selector params
    availableStorages: List<StorageVolumeInfo> = emptyList(),
    selectedStorage: StorageVolumeInfo? = null,
    onStorageSelected: (StorageVolumeInfo) -> Unit = {}
) {
    if (isSelectionActive) {
        val allSelected = selectedCount == totalCount
        TopAppBar(
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainer,
                titleContentColor = MaterialTheme.colorScheme.onSurface,
                navigationIconContentColor = MaterialTheme.colorScheme.onSurface,
                actionIconContentColor = MaterialTheme.colorScheme.onSurfaceVariant
            ),
            navigationIcon = {
                IconButton(onClick = onClearSelection) {
                    Icon(Icons.Filled.Close, contentDescription = "Clear Selection")
                }
            },
            title = {
                Text(
                    "$selectedCount / $totalCount selected",
                    fontWeight = FontWeight.Bold
                )
            },
            actions = {
                IconButton(onClick = onShowInfo) {
                    Icon(
                        imageVector = Icons.Filled.Info,
                        contentDescription = "Info"
                    )
                }
                IconButton(onClick = onSelectAll) {
                    Icon(
                        imageVector = Icons.Filled.SelectAll,
                        contentDescription = if (allSelected) "Unselect All" else "Select All"
                    )
                }
            }
        )
    } else {
        // Whether to show the dropdown arrow (only when multiple volumes are available and not in back-button context)
        val showStorageDropdown = availableStorages.size > 1 && !showBackButton && !searchActive

        var storageMenuExpanded by remember { mutableStateOf(false) }

        Box(modifier = Modifier.fillMaxWidth()) {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    scrolledContainerColor = MaterialTheme.colorScheme.background
                ),
                title = {
                    if (searchActive) {
                        OutlinedTextField(
                            value = searchText,
                            onValueChange = onSearchTextChange,
                            placeholder = { Text("Search videos...") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth().focusRequester(searchFocusRequester),
                            keyboardOptions = KeyboardOptions(
                                imeAction = ImeAction.Search
                            ),
                            keyboardActions = KeyboardActions(
                                onSearch = {
                                    keyboard?.hide()
                                    if (searchText.isNotBlank()) {
                                        onSearchActiveChange(false)
                                        onSearch(searchText)
                                    }
                                }
                            ),
                            colors = OutlinedTextFieldDefaults.colors(
                                unfocusedBorderColor = Color.Transparent,
                                focusedBorderColor = Color.Transparent
                            )
                        )
                    } else if (showStorageDropdown) {
                        // Clickable title row that opens the storage DropdownMenu
                        Box {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.clickable { storageMenuExpanded = true }
                            ) {
                                Text(
                                    text = titleText ?: selectedStorage?.name ?: "Nosved Player",
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.widthIn(max = 200.dp)
                                )
                                Icon(
                                    imageVector = Icons.Filled.ArrowDropDown,
                                    contentDescription = "Select Storage",
                                    tint = MaterialTheme.colorScheme.onSurface
                                )
                            }
                            DropdownMenu(
                                expanded = storageMenuExpanded,
                                onDismissRequest = { storageMenuExpanded = false }
                            ) {
                                availableStorages.forEach { storage ->
                                    DropdownMenuItem(
                                        text = {
                                            Text(
                                                text = storage.name,
                                                fontWeight = if (storage.id == selectedStorage?.id) FontWeight.Bold else FontWeight.Normal,
                                                color = if (storage.id == selectedStorage?.id)
                                                    MaterialTheme.colorScheme.primary
                                                else
                                                    MaterialTheme.colorScheme.onSurface
                                            )
                                        },
                                        onClick = {
                                            storageMenuExpanded = false
                                            onStorageSelected(storage)
                                        }
                                    )
                                }
                            }
                        }
                    } else {
                        Text(
                            titleText ?: "Nosved Player",
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                },
                navigationIcon = {
                    if (showBackButton) {
                        IconButton(onClick = onBackToFolders) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    } else if (showHomeBackButton) {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back to Home")
                        }
                    }
                },
                actions = {
                    if (searchActive) {
                        IconButton(onClick = { onSearchActiveChange(false) }) {
                            Icon(Icons.Filled.Close, contentDescription = "Close Search")
                        }
                    } else {
                        if (showBackButton) {
                            // VideoListScreen (where video of these folders are)
                            IconButton(onClick = { onSearchActiveChange(true) }) {
                                Icon(Icons.Filled.Search, contentDescription = "Search")
                            }
                            IconButton(onClick = onShowSettings) {
                                Icon(imageVector = Icons.Filled.Tune, contentDescription = "View Settings")
                            }
                            onPlayFolder?.let { onClick ->
                                IconButton(onClick = onClick) {
                                    Icon(imageVector = Icons.Filled.PlayCircle, contentDescription = "Play Folder in Feed")
                                }
                            }
                        } else {
                            // FolderListScreen (where folders are)
                            IconButton(onClick = { onSearchActiveChange(true) }) {
                                Icon(Icons.Filled.Search, contentDescription = "Search")
                            }
                            IconButton(onClick = onShowSettings) {
                                Icon(imageVector = Icons.Filled.Tune, contentDescription = "View Settings")
                            }
                            IconButton(onClick = onNavigateToSettings) {
                                Icon(imageVector = Icons.Filled.Settings, contentDescription = "Settings")
                            }
                        }
                    }
                }
            )
            if (searchActive && searchSuggestions.isNotEmpty()) {
                SearchSuggestionsPopup(
                    suggestions = searchSuggestions,
                    keyboard = keyboard,
                    onSuggestionClick = { title ->
                        onSearchActiveChange(false)
                        onSearch(title)
                    }
                )
            }
        }
    }
}