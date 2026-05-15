package com.devson.nvplayer.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.devson.nvplayer.model.TrackInfo
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AudioTrackSheet(
    showSheet: Boolean,
    sheetState: SheetState,
    audioTracks: List<TrackInfo>,
    selectedTrackIndex: Int,
    isLandscape: Boolean,
    isAudioBoostEnabled: Boolean,
    onToggleAudioBoost: (Boolean) -> Unit,
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
                    isAudioBoostEnabled = isAudioBoostEnabled,
                    onToggleAudioBoost = onToggleAudioBoost,
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
                com.devson.nvplayer.ui.theme.DialogNavigationBarThemeFix()
                MediaTrackSheetContent(
                    title = "Audio Tracks",
                    tracks = audioTracks,
                    selectedTrackIndex = selectedTrackIndex,
                    isAudioBoostEnabled = isAudioBoostEnabled,
                    onToggleAudioBoost = onToggleAudioBoost,
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
    isAudioBoostEnabled: Boolean,
    onToggleAudioBoost: (Boolean) -> Unit,
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
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Audio Boost Toggle
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Audio Boost (2X)",
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                )
                Text(
                    text = "Loudness Enhancer Applied. This may impact battery life.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
            Switch(
                checked = isAudioBoostEnabled,
                onCheckedChange = { onToggleAudioBoost(it) }
            )
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
    useSystemCaptionStyle: Boolean,
    subtitleFont: com.devson.nvplayer.repository.SubtitleFont,
    isSubtitleBold: Boolean,
    isSubtitleGestureEnabled: Boolean = true,
    subtitleDelayMs: Long = 0L,
    subtitleVerticalOffset: Float = 0f,
    onSelectTrack: (Int?) -> Unit,
    onPickExternalSubtitle: () -> Unit,
    onTextSizeChange: (Float) -> Unit,
    onBgStyleChange: (Int) -> Unit,
    onUseSystemCaptionStyleChange: (Boolean) -> Unit,
    onSubtitleFontChange: (com.devson.nvplayer.repository.SubtitleFont) -> Unit,
    onIsSubtitleBoldChange: (Boolean) -> Unit,
    onSubtitleGestureEnabledChange: (Boolean) -> Unit = {},
    onSubtitleDelayChange: (Long) -> Unit = {},
    onSubtitleVerticalOffsetChange: (Float) -> Unit = {},
    onPickExternalSubtitleWithEncoding: (String) -> Unit = {},
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
                    useSystemCaptionStyle = useSystemCaptionStyle,
                    subtitleFont = subtitleFont,
                    isSubtitleBold = isSubtitleBold,
                    isSubtitleGestureEnabled = isSubtitleGestureEnabled,
                    subtitleDelayMs = subtitleDelayMs,
                    subtitleVerticalOffset = subtitleVerticalOffset,
                    onSelectTrack = onSelectTrack,
                    onPickExternalSubtitle = onPickExternalSubtitle,
                    onTextSizeChange = onTextSizeChange,
                    onBgStyleChange = onBgStyleChange,
                    onUseSystemCaptionStyleChange = onUseSystemCaptionStyleChange,
                    onSubtitleFontChange = onSubtitleFontChange,
                    onIsSubtitleBoldChange = onIsSubtitleBoldChange,
                    onSubtitleGestureEnabledChange = onSubtitleGestureEnabledChange,
                    onSubtitleDelayChange = onSubtitleDelayChange,
                    onSubtitleVerticalOffsetChange = onSubtitleVerticalOffsetChange,
                    onPickExternalSubtitleWithEncoding = onPickExternalSubtitleWithEncoding,
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
                com.devson.nvplayer.ui.theme.DialogNavigationBarThemeFix()
                SubtitleSheetContent(
                    subtitleTracks = subtitleTracks,
                    selectedTrackIndex = selectedTrackIndex,
                    textSizeScale = textSizeScale,
                    bgStyle = bgStyle,
                    useSystemCaptionStyle = useSystemCaptionStyle,
                    subtitleFont = subtitleFont,
                    isSubtitleBold = isSubtitleBold,
                    isSubtitleGestureEnabled = isSubtitleGestureEnabled,
                    subtitleDelayMs = subtitleDelayMs,
                    subtitleVerticalOffset = subtitleVerticalOffset,
                    onSelectTrack = onSelectTrack,
                    onPickExternalSubtitle = onPickExternalSubtitle,
                    onTextSizeChange = onTextSizeChange,
                    onBgStyleChange = onBgStyleChange,
                    onUseSystemCaptionStyleChange = onUseSystemCaptionStyleChange,
                    onSubtitleFontChange = onSubtitleFontChange,
                    onIsSubtitleBoldChange = onIsSubtitleBoldChange,
                    onSubtitleGestureEnabledChange = onSubtitleGestureEnabledChange,
                    onSubtitleDelayChange = onSubtitleDelayChange,
                    onSubtitleVerticalOffsetChange = onSubtitleVerticalOffsetChange,
                    onPickExternalSubtitleWithEncoding = onPickExternalSubtitleWithEncoding,
                    onDismissRequest = onDismissRequest
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SubtitleSheetContent(
    subtitleTracks: List<TrackInfo>,
    selectedTrackIndex: Int,
    textSizeScale: Float,
    bgStyle: Int,
    useSystemCaptionStyle: Boolean,
    subtitleFont: com.devson.nvplayer.repository.SubtitleFont,
    isSubtitleBold: Boolean,
    isSubtitleGestureEnabled: Boolean = true,
    subtitleDelayMs: Long = 0L,
    subtitleVerticalOffset: Float = 0f,
    onSelectTrack: (Int?) -> Unit,
    onPickExternalSubtitle: () -> Unit,
    onTextSizeChange: (Float) -> Unit,
    onBgStyleChange: (Int) -> Unit,
    onUseSystemCaptionStyleChange: (Boolean) -> Unit,
    onSubtitleFontChange: (com.devson.nvplayer.repository.SubtitleFont) -> Unit,
    onIsSubtitleBoldChange: (Boolean) -> Unit,
    onSubtitleGestureEnabledChange: (Boolean) -> Unit = {},
    onSubtitleDelayChange: (Long) -> Unit = {},
    onSubtitleVerticalOffsetChange: (Float) -> Unit = {},
    onPickExternalSubtitleWithEncoding: (String) -> Unit = {},
    onDismissRequest: () -> Unit
) {
    var selectedTabIndex by remember { mutableStateOf(0) }
    
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 10.dp)
            .navigationBarsPadding()
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
            val tabs = listOf("Tracks", "External", "Style", "Sync")
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
                    var selectedEncoding by remember { mutableStateOf("UTF-8") }
                    var encodingExpanded by remember { mutableStateOf(false) }
                    val encodingOptions = listOf("UTF-8", "Windows-1252", "ISO-8859-1")

                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Button(
                            onClick = {
                                onPickExternalSubtitleWithEncoding(selectedEncoding)
                                onDismissRequest()
                            },
                            modifier = Modifier.fillMaxWidth().height(50.dp),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                        ) {
                            Text("Pick .SRT or .VTT File", color = MaterialTheme.colorScheme.onPrimary)
                        }

                        ExposedDropdownMenuBox(
                            expanded = encodingExpanded,
                            onExpandedChange = { encodingExpanded = !encodingExpanded }
                        ) {
                            OutlinedTextField(
                                value = selectedEncoding,
                                onValueChange = {},
                                readOnly = true,
                                label = { Text("Text Encoding") },
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = encodingExpanded) },
                                modifier = Modifier.fillMaxWidth().menuAnchor()
                            )
                            ExposedDropdownMenu(
                                expanded = encodingExpanded,
                                onDismissRequest = { encodingExpanded = false }
                            ) {
                                encodingOptions.forEach { enc ->
                                    DropdownMenuItem(
                                        text = { Text(enc) },
                                        onClick = {
                                            selectedEncoding = enc
                                            encodingExpanded = false
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
                2 -> { // Customize
                    Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                        SettingsSection(title = "Gestures") {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Enable Swipe Gestures", color = MaterialTheme.colorScheme.onSurface, fontSize = 15.sp)
                                Switch(
                                    checked = isSubtitleGestureEnabled,
                                    onCheckedChange = onSubtitleGestureEnabledChange
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        SettingsSection(title = "System Settings") {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Use System Caption Style", color = MaterialTheme.colorScheme.onSurface, fontSize = 15.sp)
                                Switch(
                                    checked = useSystemCaptionStyle,
                                    onCheckedChange = onUseSystemCaptionStyleChange
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        if (!useSystemCaptionStyle) {
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
                            Spacer(modifier = Modifier.height(16.dp))
                            SettingsSection(title = "Font Family") {
                                Row(
                                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    com.devson.nvplayer.repository.SubtitleFont.values().forEach { font ->
                                        ChipButton(
                                            label = font.name.lowercase().replaceFirstChar { it.uppercase() },
                                            selected = subtitleFont == font,
                                            modifier = Modifier.widthIn(min = 80.dp),
                                            onClick = { onSubtitleFontChange(font) }
                                        )
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(16.dp))
                            SettingsSection(title = "Bold Text") {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("Enable Bold Font", color = MaterialTheme.colorScheme.onSurface, fontSize = 15.sp)
                                    Switch(
                                        checked = isSubtitleBold,
                                        onCheckedChange = onIsSubtitleBoldChange
                                    )
                                }
                            }
                        } else {
                            Text(
                                "Subtitle appearance is controlled by system settings. Go to Android Settings > Accessibility > Caption preferences to change them.", 
                                color = MaterialTheme.colorScheme.onSurfaceVariant, 
                                fontSize = 14.sp
                            )
                        }
                    }
                }
                3 -> { // Sync
                    var delayInputText by remember(subtitleDelayMs) {
                        mutableStateOf("%.1f".format(subtitleDelayMs / 1000.0))
                    }

                    Column(
                        modifier = Modifier.verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        SettingsSection(title = "Subtitle Delay") {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedButton(
                                    onClick = {
                                        val newDelay = subtitleDelayMs - 100L
                                        onSubtitleDelayChange(newDelay)
                                        delayInputText = "%.1f".format(newDelay / 1000.0)
                                    },
                                    modifier = Modifier.size(48.dp),
                                    contentPadding = PaddingValues(0.dp)
                                ) {
                                    Text("-", fontSize = 20.sp)
                                }

                                OutlinedTextField(
                                    value = delayInputText,
                                    onValueChange = { text ->
                                        delayInputText = text
                                        val parsed = text.toDoubleOrNull()
                                        if (parsed != null) {
                                            onSubtitleDelayChange((parsed * 1000).toLong())
                                        }
                                    },
                                    modifier = Modifier.weight(1f),
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    suffix = { Text("s") },
                                    textStyle = androidx.compose.ui.text.TextStyle(textAlign = TextAlign.Center)
                                )

                                OutlinedButton(
                                    onClick = {
                                        val newDelay = subtitleDelayMs + 100L
                                        onSubtitleDelayChange(newDelay)
                                        delayInputText = "%.1f".format(newDelay / 1000.0)
                                    },
                                    modifier = Modifier.size(48.dp),
                                    contentPadding = PaddingValues(0.dp)
                                ) {
                                    Text("+", fontSize = 20.sp)
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            Button(
                                onClick = {
                                    onSubtitleDelayChange(0L)
                                    delayInputText = "0.0"
                                },
                                modifier = Modifier.align(Alignment.CenterHorizontally),
                                colors = ButtonDefaults.outlinedButtonColors()
                            ) {
                                Text("Reset to 0s")
                            }
                        }

                        SettingsSection(title = "Vertical Position") {
                            Slider(
                                value = subtitleVerticalOffset,
                                onValueChange = onSubtitleVerticalOffsetChange,
                                valueRange = -0.5f..0.5f,
                                modifier = Modifier.fillMaxWidth()
                            )
                            Text(
                                text = when {
                                    subtitleVerticalOffset > 0.01f -> "Up ${(subtitleVerticalOffset * 100).toInt()}%"
                                    subtitleVerticalOffset < -0.01f -> "Down ${(-subtitleVerticalOffset * 100).toInt()}%"
                                    else -> "Default"
                                },
                                color = MaterialTheme.colorScheme.onSurface.copy(0.7f),
                                fontSize = 12.sp,
                                modifier = Modifier.align(Alignment.End)
                            )
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