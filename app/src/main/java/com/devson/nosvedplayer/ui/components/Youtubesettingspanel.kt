package com.devson.nosvedplayer.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.devson.nosvedplayer.model.TrackInfo

//  Panel page state 

sealed class YtPanelPage {
    object Main : YtPanelPage()
    object Speed : YtPanelPage()
    object Audio : YtPanelPage()
    object Subtitles : YtPanelPage()
}

//  Main entry composable 

/**
 * YouTube-style floating settings panel that appears over the player.
 * Unlike a bottom sheet, this renders as a dark floating card anchored to the
 * right or bottom of the screen and supports sub-page navigation.
 */
@Composable
fun YoutubeSettingsPanel(
    visible: Boolean,
    playbackSpeed: Float,
    audioTracks: List<TrackInfo>,
    selectedAudioIndex: Int,
    subtitleTracks: List<TrackInfo>,
    selectedSubtitleIndex: Int,
    isLocked: Boolean,
    onDismiss: () -> Unit,
    onSpeedChange: (Float) -> Unit,
    onSelectAudio: (Int) -> Unit,
    onSelectSubtitle: (Int?) -> Unit,
    onToggleLock: () -> Unit,
    modifier: Modifier = Modifier,
    initialPage: YtPanelPage = YtPanelPage.Main
) {
    var page by remember(visible, initialPage) { mutableStateOf(initialPage) }

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = modifier
    ) {
        // Scrim — tapping outside dismisses the panel
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {
                        if (page == YtPanelPage.Subtitles || page == YtPanelPage.Audio) {
                            onDismiss()
                        } else if (page != YtPanelPage.Main) {
                            page = YtPanelPage.Main
                        } else {
                            onDismiss()
                        }
                    }
                )
        ) {
            // The floating panel itself
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {} // consume clicks so they don't hit the scrim
                    )
            ) {
                AnimatedContent(
                    targetState = page,
                    transitionSpec = {
                        if (targetState != YtPanelPage.Main) {
                            (slideInHorizontally { it } + fadeIn()) togetherWith
                                    (slideOutHorizontally { -it } + fadeOut())
                        } else {
                            (slideInHorizontally { -it } + fadeIn()) togetherWith
                                    (slideOutHorizontally { it } + fadeOut())
                        }
                    },
                    label = "yt_settings_page"
                ) { currentPage ->
                    when (currentPage) {
                        YtPanelPage.Main -> YtMainPage(
                            playbackSpeed = playbackSpeed,
                            selectedAudioLabel = audioTracks.find { it.index == selectedAudioIndex }?.label
                                ?: "Default",
                            selectedSubtitleLabel = if (selectedSubtitleIndex == -1) "Off"
                            else subtitleTracks.find { it.index == selectedSubtitleIndex }?.label ?: "Off",
                            isLocked = isLocked,
                            onSpeedClick = { page = YtPanelPage.Speed },
                            onAudioClick = { page = YtPanelPage.Audio },
                            onSubtitleClick = { page = YtPanelPage.Subtitles },
                            onToggleLock = {
                                onToggleLock()
                                onDismiss()
                            }
                        )

                        YtPanelPage.Speed -> YtSpeedPage(
                            currentSpeed = playbackSpeed,
                            onBack = { page = YtPanelPage.Main },
                            onSelect = { speed ->
                                onSpeedChange(speed)
                                page = YtPanelPage.Main
                            }
                        )

                        YtPanelPage.Audio -> YtTrackPage(
                            title = "Audio",
                            tracks = audioTracks,
                            selectedIndex = selectedAudioIndex,
                            includeOff = false,
                            onBack = onDismiss,
                            onSelect = { index ->
                                onSelectAudio(index)
                                onDismiss()
                            }
                        )

                        YtPanelPage.Subtitles -> YtTrackPage(
                            title = "Subtitles / CC",
                            tracks = subtitleTracks,
                            selectedIndex = selectedSubtitleIndex,
                            includeOff = true,
                            onBack = onDismiss,
                            onSelect = { index ->
                                onSelectSubtitle(index)
                                onDismiss()
                            }
                        )
                    }
                }
            }
        }
    }
}

//  Page: Main menu 

@Composable
private fun YtMainPage(
    playbackSpeed: Float,
    selectedAudioLabel: String,
    selectedSubtitleLabel: String,
    isLocked: Boolean,
    onSpeedClick: () -> Unit,
    onAudioClick: () -> Unit,
    onSubtitleClick: () -> Unit,
    onToggleLock: () -> Unit
) {
    Column(modifier = Modifier.padding(vertical = 8.dp)) {
        // Drag handle
        Box(
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .padding(top = 4.dp, bottom = 8.dp)
                .size(width = 36.dp, height = 4.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f))
        )

        YtMenuItem(
            icon = Icons.Filled.Speed,
            label = "Playback speed",
            value = if (playbackSpeed == 1f) "Normal" else "${playbackSpeed}x",
            onClick = onSpeedClick
        )
        YtMenuDivider()
        YtMenuItem(
            icon = Icons.Filled.Lock,
            label = if (isLocked) "Unlock screen" else "Lock screen",
            value = null,
            showArrow = false,
            onClick = onToggleLock
        )

        Spacer(Modifier.height(8.dp))
    }
}

//  Page: Playback speed 

private val SPEED_OPTIONS = listOf(0.25f, 0.5f, 0.75f, 1f, 1.25f, 1.5f, 1.75f, 2f)

@Composable
private fun YtSpeedPage(
    currentSpeed: Float,
    onBack: () -> Unit,
    onSelect: (Float) -> Unit
) {
    Column(modifier = Modifier.padding(bottom = 8.dp)) {
        YtSubPageHeader(title = "Playback speed", onBack = onBack)
        SPEED_OPTIONS.forEach { speed ->
            val label = if (speed == 1f) "Normal" else "${speed}x"
            val selected = speed == currentSpeed
            YtSelectableItem(label = label, selected = selected, onClick = { onSelect(speed) })
        }
    }
}

//  Page: Track selection (audio / subtitle) 

@Composable
private fun YtTrackPage(
    title: String,
    tracks: List<TrackInfo>,
    selectedIndex: Int,
    includeOff: Boolean,
    onBack: () -> Unit,
    onSelect: (Int) -> Unit
) {
    Column(modifier = Modifier.padding(bottom = 8.dp)) {
        YtSubPageHeader(title = title, onBack = onBack)
        LazyColumn(modifier = Modifier.heightIn(max = 320.dp)) {
            if (includeOff) {
                item {
                    YtSelectableItem(
                        label = "Off",
                        selected = selectedIndex == -1,
                        onClick = { onSelect(-1) }
                    )
                }
            }
            items(tracks) { track ->
                YtSelectableItem(
                    label = track.label,
                    selected = track.index == selectedIndex,
                    onClick = { onSelect(track.index) }
                )
            }
            if (tracks.isEmpty() && !includeOff) {
                item {
                    Text(
                        "No tracks available",
                        color = Color.White.copy(alpha = 0.5f),
                        fontSize = 14.sp,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp)
                    )
                }
            }
        }
    }
}

//  Shared primitives 

@Composable
private fun YtSubPageHeader(title: String, onBack: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            color = Color.White,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
}

@Composable
private fun YtMenuItem(
    icon: ImageVector,
    label: String,
    value: String?,
    showArrow: Boolean = true,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.size(22.dp)
        )
        Spacer(Modifier.width(16.dp))
        Text(
            text = label,
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = 15.sp,
            modifier = Modifier.weight(1f)
        )
        if (value != null) {
            Text(
                text = value,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 13.sp
            )
            Spacer(Modifier.width(6.dp))
        }
        if (showArrow) {
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

@Composable
private fun YtSelectableItem(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (selected) {
            Icon(
                Icons.Filled.Check,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.width(14.dp))
        } else {
            Spacer(Modifier.width(32.dp))
        }
        Text(
            text = label,
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            fontSize = 15.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
        )
    }
}

@Composable
private fun YtMenuDivider() {
    HorizontalDivider(
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
        modifier = Modifier.padding(horizontal = 20.dp)
    )
}