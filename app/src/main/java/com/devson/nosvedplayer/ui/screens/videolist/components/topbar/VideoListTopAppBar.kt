package com.devson.nosvedplayer.ui.screens.videolist.components.topbar

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Tune
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.SoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import com.devson.nosvedplayer.model.Video
import com.devson.nosvedplayer.ui.components.SearchSuggestionsPopup

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VideoListTopAppBar(
    isSelectionActive: Boolean,
    titleText: String?,
    selectedCount: Int,
    totalCount: Int,
    showBackButton: Boolean,
    onClearSelection: () -> Unit,
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
    searchSuggestions: List<Video> = emptyList(),
    searchFocusRequester: FocusRequester = remember { FocusRequester() },
    keyboard: SoftwareKeyboardController? = null
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
                IconButton(onClick = onSelectAll) {
                    Icon(
                        imageVector = Icons.Filled.SelectAll,
                        contentDescription = if (allSelected) "Unselect All" else "Select All"
                    )
                }
            }
        )
    } else {
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
                } else {
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