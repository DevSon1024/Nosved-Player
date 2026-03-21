package com.devson.nosvedplayer.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.devson.nosvedplayer.model.TrackInfo
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AudioTrackSheet(
    showSheet: Boolean,
    sheetState: SheetState,
    audioTracks: List<TrackInfo>,
    selectedTrackIndex: Int,
    isLandscape: Boolean,
    onSelectTrack: (Int) -> Unit,
    onDismissRequest: () -> Unit
) {
    if (showSheet) {
        val scope = rememberCoroutineScope()
        val dismiss: () -> Unit = {
            scope.launch { sheetState.hide() }.invokeOnCompletion { onDismissRequest() }
        }

        if (isLandscape) {
            SideSheet(visible = showSheet, onDismissRequest = onDismissRequest) {
                MediaTrackSheetContent(
                    title = "Audio Tracks",
                    tracks = audioTracks,
                    selectedTrackIndex = selectedTrackIndex,
                    onSelectTrack = onSelectTrack,
                    onDismissRequest = onDismissRequest
                )
            }
        } else {
            ModalBottomSheet(
                onDismissRequest = onDismissRequest,
                sheetState = sheetState,
                containerColor = MaterialTheme.colorScheme.surface,
                tonalElevation = 4.dp
            ) {
                com.devson.nosvedplayer.ui.theme.DialogNavigationBarThemeFix()
                MediaTrackSheetContent(
                    title = "Audio Tracks",
                    tracks = audioTracks,
                    selectedTrackIndex = selectedTrackIndex,
                    onSelectTrack = onSelectTrack,
                    onDismissRequest = onDismissRequest
                )
            }
        }
    }
}

@Composable
private fun MediaTrackSheetContent(
    title: String,
    tracks: List<TrackInfo>,
    selectedTrackIndex: Int,
    onSelectTrack: (Int) -> Unit,
    onDismissRequest: () -> Unit
) {
    val scope = rememberCoroutineScope()
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 10.dp)
            .padding(bottom = 32.dp)
    ) {
        Text(
            text = title,
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = 17.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(bottom = 20.dp)
        )

        if (tracks.isEmpty()) {
            Text(
                text = "No tracks found.",
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                fontSize = 14.sp
            )
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f, fill = false),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(tracks) { track ->
                    TrackItem(
                        track = track,
                        isSelected = selectedTrackIndex == track.index,
                        onClick = {
                            onSelectTrack(track.index)
                            onDismissRequest()
                        }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SubtitleSheet(
    showSheet: Boolean,
    sheetState: SheetState,
    subtitleTracks: List<TrackInfo>,
    selectedTrackIndex: Int,
    textSizeScale: Float,
    bgStyle: Int,
    isLandscape: Boolean,
    onSelectTrack: (Int?) -> Unit,
    onPickExternalSubtitle: () -> Unit,
    onTextSizeChange: (Float) -> Unit,
    onBgStyleChange: (Int) -> Unit,
    onDismissRequest: () -> Unit
) {
    if (showSheet) {
        var selectedTabIndex by remember { mutableStateOf(0) }
        val scope = rememberCoroutineScope()
        val dismiss: () -> Unit = {
            scope.launch { sheetState.hide() }.invokeOnCompletion { onDismissRequest() }
        }

        if (isLandscape) {
            SideSheet(visible = showSheet, onDismissRequest = onDismissRequest) {
                SubtitleSheetContent(
                    subtitleTracks = subtitleTracks,
                    selectedTrackIndex = selectedTrackIndex,
                    textSizeScale = textSizeScale,
                    bgStyle = bgStyle,
                    onSelectTrack = onSelectTrack,
                    onPickExternalSubtitle = onPickExternalSubtitle,
                    onTextSizeChange = onTextSizeChange,
                    onBgStyleChange = onBgStyleChange,
                    onDismissRequest = onDismissRequest
                )
            }
        } else {
            ModalBottomSheet(
                onDismissRequest = onDismissRequest,
                sheetState = sheetState,
                containerColor = MaterialTheme.colorScheme.surface,
                tonalElevation = 4.dp
            ) {
                com.devson.nosvedplayer.ui.theme.DialogNavigationBarThemeFix()
                SubtitleSheetContent(
                    subtitleTracks = subtitleTracks,
                    selectedTrackIndex = selectedTrackIndex,
                    textSizeScale = textSizeScale,
                    bgStyle = bgStyle,
                    onSelectTrack = onSelectTrack,
                    onPickExternalSubtitle = onPickExternalSubtitle,
                    onTextSizeChange = onTextSizeChange,
                    onBgStyleChange = onBgStyleChange,
                    onDismissRequest = onDismissRequest
                )
            }
        }
    }
}

@Composable
private fun SubtitleSheetContent(
    subtitleTracks: List<TrackInfo>,
    selectedTrackIndex: Int,
    textSizeScale: Float,
    bgStyle: Int,
    onSelectTrack: (Int?) -> Unit,
    onPickExternalSubtitle: () -> Unit,
    onTextSizeChange: (Float) -> Unit,
    onBgStyleChange: (Int) -> Unit,
    onDismissRequest: () -> Unit
) {
    var selectedTabIndex by remember { mutableStateOf(0) }
    
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 10.dp)
            .padding(bottom = 32.dp)
    ) {
        Text(
            text = "Subtitles",
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = 17.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(bottom = 12.dp)
        )

        TabRow(
            selectedTabIndex = selectedTabIndex,
            containerColor = Color.Transparent,
            contentColor = MaterialTheme.colorScheme.primary,
            divider = {}
        ) {
            val tabs = listOf("Tracks", "External", "Customize")
            tabs.forEachIndexed { index, title ->
                Tab(
                    selected = selectedTabIndex == index,
                    onClick = { selectedTabIndex = index },
                    text = { 
                        Text(
                            title, 
                            color = if (selectedTabIndex == index) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f) 
                        ) 
                    }
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Box(modifier = Modifier.weight(1f, fill = false)) {
            when (selectedTabIndex) {
                0 -> { // Tracks
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        item {
                            TrackItem(
                                track = TrackInfo(-1, "Off", null),
                                isSelected = selectedTrackIndex == -1,
                                onClick = {
                                    onSelectTrack(-1)
                                    onDismissRequest()
                                }
                            )
                        }
                        items(subtitleTracks) { track ->
                            TrackItem(
                                track = track,
                                isSelected = selectedTrackIndex == track.index,
                                onClick = {
                                    onSelectTrack(track.index)
                                    onDismissRequest()
                                }
                            )
                        }
                    }
                }
                1 -> { // External
                    Button(
                        onClick = {
                            onPickExternalSubtitle()
                            onDismissRequest()
                        },
                        modifier = Modifier.fillMaxWidth().height(50.dp),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                    ) {
                        Text("Pick .SRT or .VTT File", color = MaterialTheme.colorScheme.onPrimary)
                    }
                }
                2 -> { // Customize
                    Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                        SettingsSection(title = "Text Size") {
                            Slider(
                                value = textSizeScale,
                                onValueChange = onTextSizeChange,
                                valueRange = 0.5f..2.5f,
                                steps = 7
                            )
                            Text(
                                "${(textSizeScale * 100).toInt()}%", 
                                color = MaterialTheme.colorScheme.onSurface.copy(0.7f), 
                                fontSize = 12.sp,
                                modifier = Modifier.align(Alignment.End)
                            )
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        SettingsSection(title = "Background Style") {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                listOf("None", "Dark", "Solid").forEachIndexed { index, label ->
                                    ChipButton(
                                        label = label,
                                        selected = bgStyle == index,
                                        modifier = Modifier.weight(1f),
                                        onClick = { onBgStyleChange(index) }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TrackItem(
    track: TrackInfo,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val bgColor = if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.2f) 
                  else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(bgColor)
            .clickable { onClick() }
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = track.label,
            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
            fontSize = 15.sp
        )
    }
}