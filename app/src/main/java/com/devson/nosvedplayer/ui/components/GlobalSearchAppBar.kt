package com.devson.nosvedplayer.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.SoftwareKeyboardController
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.devson.nosvedplayer.model.Video

@Composable
fun SearchSuggestionsPopup(
    suggestions: List<Video>,
    keyboard: SoftwareKeyboardController?,
    topBarHeight: Dp = 85.dp,
    onSuggestionClick: (String) -> Unit
) {
    if (suggestions.isEmpty()) return
    val density = LocalDensity.current
    val offsetPx = with(density) { topBarHeight.roundToPx() }
    Popup(
        offset = IntOffset(0, offsetPx),
        properties = PopupProperties(focusable = false)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 350.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
            )
        ) {
            LazyColumn {
                items(suggestions) { video ->
                    ListItem(
                        headlineContent = {
                            Text(video.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        },
                        leadingContent = {
                            Icon(Icons.Filled.Search, contentDescription = null)
                        },
                        modifier = Modifier.clickable {
                            keyboard?.hide()
                            onSuggestionClick(video.title)
                        }
                    )
                    HorizontalDivider()
                }
            }
        }
    }
}
