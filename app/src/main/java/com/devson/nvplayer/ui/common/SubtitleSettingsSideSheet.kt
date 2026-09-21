package com.devson.nvplayer.ui.common

import android.content.res.Configuration
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.devson.nvplayer.data.repository.PlaybackSettings
import com.devson.nvplayer.data.repository.SubtitleFont
import com.devson.nvplayer.player.model.TrackInfo
import com.devson.nvplayer.ui.common.components.SectionHeader
import com.devson.nvplayer.util.repeatingClickable
import com.devson.nvplayer.util.roundToTwoDecimals
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SubtitleSettingsSideSheet(
    visible: Boolean,
    playbackSettings: PlaybackSettings,
    subtitleTracks: List<TrackInfo>,
    onSelectSubtitleTrack: (Int) -> Unit,
    onSetSubtitleDelay: (Long) -> Unit,
    onUpdateSubtitleFont: (SubtitleFont) -> Unit,
    onUpdateIsSubtitleBold: (Boolean) -> Unit,
    onUpdateForceAssSubtitleOverride: (Boolean) -> Unit,
    onUpdateSubtitleTextSizeScale: (Float) -> Unit,
    onUpdateSubtitleBgStyle: (Int) -> Unit,
    onUpdateSubtitleDelay: (Long) -> Unit,
    onUpdateSubtitleVerticalOffset: (Float) -> Unit,
    onUpdateSubtitleGesturesEnabled: (Boolean) -> Unit,
    onDismiss: () -> Unit,
    onImportSubtitleClick: () -> Unit
) {
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    val sheetWidthPercent = if (isLandscape) 0.48f else 0.85f

    var selectedTabIndex by remember { mutableIntStateOf(0) }
    val selectedTrack = remember(subtitleTracks) { subtitleTracks.find { it.selected } }

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.CenterEnd
    ) {
        // Scrim
        AnimatedVisibility(
            visible = visible,
            enter = fadeIn(animationSpec = tween(durationMillis = 250)),
            exit = fadeOut(animationSpec = tween(durationMillis = 250)),
            modifier = Modifier.fillMaxSize()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.50f))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onDismiss
                    )
            )
        }

        // Sheet Content
        AnimatedVisibility(
            visible = visible,
            enter = slideInHorizontally(
                initialOffsetX = { it },
                animationSpec = tween(durationMillis = 300, easing = LinearOutSlowInEasing)
            ),
            exit = slideOutHorizontally(
                targetOffsetX = { it },
                animationSpec = tween(durationMillis = 300, easing = FastOutLinearInEasing)
            ),
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth(sheetWidthPercent)
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxSize()
                    .border(
                        width = 1.dp,
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f),
                        shape = RoundedCornerShape(topStart = 24.dp, bottomStart = 24.dp)
                    ),
                shape = RoundedCornerShape(topStart = 24.dp, bottomStart = 24.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.96f),
                tonalElevation = 8.dp
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .systemBarsPadding()
                        .padding(start = 20.dp, end = 20.dp, top = 18.dp)
                ) {
                    // Header Card
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Surface(
                                color = MaterialTheme.colorScheme.primaryContainer,
                                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.size(40.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Rounded.Subtitles,
                                        contentDescription = null,
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                            }

                            Column {
                                Text(
                                    text = "Subtitle Settings",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = selectedTrack?.name ?: "No Subtitles Active",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        IconButton(
                            onClick = onDismiss,
                            colors = IconButtonDefaults.iconButtonColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                            )
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Close,
                                contentDescription = "Close panel",
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Material 3 Segmented Button Navigation
                    SingleChoiceSegmentedButtonRow(
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        SegmentedButton(
                            selected = selectedTabIndex == 0,
                            onClick = { selectedTabIndex = 0 },
                            shape = SegmentedButtonDefaults.itemShape(index = 0, count = 3),
                            icon = { Icon(Icons.Rounded.List, contentDescription = null, modifier = Modifier.size(16.dp)) }
                        ) {
                            Text("Tracks", fontSize = 12.sp, fontWeight = FontWeight.Medium)
                        }

                        SegmentedButton(
                            selected = selectedTabIndex == 1,
                            onClick = { selectedTabIndex = 1 },
                            shape = SegmentedButtonDefaults.itemShape(index = 1, count = 3),
                            icon = { Icon(Icons.Rounded.Style, contentDescription = null, modifier = Modifier.size(16.dp)) }
                        ) {
                            Text("Style", fontSize = 12.sp, fontWeight = FontWeight.Medium)
                        }

                        SegmentedButton(
                            selected = selectedTabIndex == 2,
                            onClick = { selectedTabIndex = 2 },
                            shape = SegmentedButtonDefaults.itemShape(index = 2, count = 3),
                            icon = { Icon(Icons.Rounded.Sync, contentDescription = null, modifier = Modifier.size(16.dp)) }
                        ) {
                            Text("Sync", fontSize = 12.sp, fontWeight = FontWeight.Medium)
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Tab Content with Animated Transition
                    AnimatedContent(
                        targetState = selectedTabIndex,
                        transitionSpec = {
                            fadeIn(animationSpec = tween(220)) togetherWith fadeOut(animationSpec = tween(220))
                        },
                        label = "TabContentAnimation",
                        modifier = Modifier.weight(1f)
                    ) { targetTab ->
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            when (targetTab) {
                                0 -> TracksTab(
                                    subtitleTracks = subtitleTracks,
                                    onSelectSubtitleTrack = onSelectSubtitleTrack,
                                    onImportSubtitleClick = onImportSubtitleClick
                                )

                                1 -> StyleTab(
                                    playbackSettings = playbackSettings,
                                    onUpdateSubtitleFont = onUpdateSubtitleFont,
                                    onUpdateIsSubtitleBold = onUpdateIsSubtitleBold,
                                    onUpdateForceAssSubtitleOverride = onUpdateForceAssSubtitleOverride,
                                    onUpdateSubtitleTextSizeScale = onUpdateSubtitleTextSizeScale,
                                    onUpdateSubtitleBgStyle = onUpdateSubtitleBgStyle
                                )

                                2 -> SyncTab(
                                    playbackSettings = playbackSettings,
                                    onSetSubtitleDelay = onSetSubtitleDelay,
                                    onUpdateSubtitleDelay = onUpdateSubtitleDelay,
                                    onUpdateSubtitleVerticalOffset = onUpdateSubtitleVerticalOffset,
                                    onUpdateSubtitleGesturesEnabled = onUpdateSubtitleGesturesEnabled
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// -----------------------------------------------------------------------------
// TAB 1: TRACKS SELECTION & IMPORT
// -----------------------------------------------------------------------------
@Composable
private fun TracksTab(
    subtitleTracks: List<TrackInfo>,
    onSelectSubtitleTrack: (Int) -> Unit,
    onImportSubtitleClick: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        // Import External Subtitle Card
        Card(
            onClick = onImportSubtitleClick,
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
            ),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Surface(
                    color = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    shape = CircleShape,
                    modifier = Modifier.size(36.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Rounded.FileUpload,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Import External Subtitle",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Supports .srt, .ass, .ssa, .vtt formats",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Icon(
                    imageVector = Icons.Rounded.ChevronRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }

        SectionHeader(title = "Available Subtitle Tracks")

        if (subtitleTracks.isEmpty()) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier.padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No subtitle tracks found for this video",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            subtitleTracks.forEach { track ->
                TrackCardItem(
                    title = track.name,
                    isSelected = track.selected,
                    isExternal = track.isExternal,
                    isNone = track.id == -1,
                    onClick = { onSelectSubtitleTrack(track.id) }
                )
            }
        }
    }
}

@Composable
private fun TrackCardItem(
    title: String,
    isSelected: Boolean,
    isExternal: Boolean,
    isNone: Boolean,
    onClick: () -> Unit
) {
    val containerColor = if (isSelected) {
        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
    } else {
        MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.6f)
    }
    val borderColor = if (isSelected) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
    }

    Card(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        border = BorderStroke(if (isSelected) 1.5.dp else 1.dp, borderColor),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.weight(1f)
            ) {
                RadioButton(
                    selected = isSelected,
                    onClick = null,
                    colors = RadioButtonDefaults.colors(
                        selectedColor = MaterialTheme.colorScheme.primary
                    )
                )

                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                    color = if (isSelected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            if (!isNone) {
                Surface(
                    color = if (isExternal) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = if (isExternal) MaterialTheme.colorScheme.onTertiaryContainer else MaterialTheme.colorScheme.onSecondaryContainer,
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text(
                        text = if (isExternal) "External" else "Embedded",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            }
        }
    }
}

// -----------------------------------------------------------------------------
// TAB 2: STYLE & APPEARANCE
// -----------------------------------------------------------------------------
@Composable
private fun StyleTab(
    playbackSettings: PlaybackSettings,
    onUpdateSubtitleFont: (SubtitleFont) -> Unit,
    onUpdateIsSubtitleBold: (Boolean) -> Unit,
    onUpdateForceAssSubtitleOverride: (Boolean) -> Unit,
    onUpdateSubtitleTextSizeScale: (Float) -> Unit,
    onUpdateSubtitleBgStyle: (Int) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        // Live Subtitle Preview Box
        SubtitleLivePreviewCard(playbackSettings = playbackSettings)

        // Text Size Scale Slider Card
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.6f),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.FormatSize,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = "Text Size Scale",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = "%.1fx".format(playbackSettings.subtitleTextSizeScale),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    LongPressStepButton(
                        icon = Icons.Rounded.Remove,
                        contentDescription = "Decrease size",
                        onStep = {
                            val newScale = (playbackSettings.subtitleTextSizeScale - 0.1f)
                                .coerceAtLeast(0.5f)
                                .roundToTwoDecimals()
                            onUpdateSubtitleTextSizeScale(newScale)
                        }
                    )

                    Slider(
                        value = playbackSettings.subtitleTextSizeScale,
                        onValueChange = { onUpdateSubtitleTextSizeScale(it.roundToTwoDecimals()) },
                        valueRange = 0.5f..3.0f,
                        modifier = Modifier.weight(1f)
                    )

                    LongPressStepButton(
                        icon = Icons.Rounded.Add,
                        contentDescription = "Increase size",
                        onStep = {
                            val newScale = (playbackSettings.subtitleTextSizeScale + 0.1f)
                                .coerceAtMost(3.0f)
                                .roundToTwoDecimals()
                            onUpdateSubtitleTextSizeScale(newScale)
                        }
                    )
                }
            }
        }

        // Font Family Selector
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SectionHeader(title = "Font Family")

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                SubtitleFont.values().forEach { font ->
                    val isSelected = playbackSettings.subtitleFont == font
                    val fontName = when (font) {
                        SubtitleFont.DEFAULT -> "Default"
                        SubtitleFont.MONOSPACE -> "Mono"
                        SubtitleFont.SANS_SERIF -> "Sans"
                        SubtitleFont.SERIF -> "Serif"
                    }
                    val fontFamily = when (font) {
                        SubtitleFont.DEFAULT -> FontFamily.Default
                        SubtitleFont.MONOSPACE -> FontFamily.Monospace
                        SubtitleFont.SANS_SERIF -> FontFamily.SansSerif
                        SubtitleFont.SERIF -> FontFamily.Serif
                    }

                    Surface(
                        onClick = { onUpdateSubtitleFont(font) },
                        shape = RoundedCornerShape(12.dp),
                        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainer,
                        contentColor = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                        border = BorderStroke(
                            width = 1.dp,
                            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
                        ),
                        modifier = Modifier.weight(1f)
                    ) {
                        Box(
                            modifier = Modifier.padding(vertical = 12.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = fontName,
                                fontFamily = fontFamily,
                                fontSize = 13.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                            )
                        }
                    }
                }
            }
        }

        // Background Style Selector
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SectionHeader(title = "Background Style")

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf(
                    0 to "None",
                    1 to "Glass",
                    2 to "Solid Black"
                ).forEach { (styleId, styleName) ->
                    val isSelected = playbackSettings.subtitleBgStyle == styleId

                    Surface(
                        onClick = { onUpdateSubtitleBgStyle(styleId) },
                        shape = RoundedCornerShape(12.dp),
                        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainer,
                        contentColor = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                        border = BorderStroke(
                            width = 1.dp,
                            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
                        ),
                        modifier = Modifier.weight(1f)
                    ) {
                        Box(
                            modifier = Modifier.padding(vertical = 12.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = styleName,
                                fontSize = 13.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                            )
                        }
                    }
                }
            }
        }

        // Toggles
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SectionHeader(title = "Typography & Style Overrides")

            ToggleCardRow(
                title = "Bold Subtitles",
                description = "Thicker lines for enhanced legibility",
                icon = Icons.Rounded.FormatBold,
                checked = playbackSettings.isSubtitleBold,
                onCheckedChange = onUpdateIsSubtitleBold
            )

            ToggleCardRow(
                title = "Override Embedded ASS/SSA",
                description = "Apply app style on styled SSA/ASS captions",
                icon = Icons.Rounded.AutoFixHigh,
                checked = playbackSettings.forceAssSubtitleOverride,
                onCheckedChange = onUpdateForceAssSubtitleOverride
            )
        }
    }
}

@Composable
private fun SubtitleLivePreviewCard(playbackSettings: PlaybackSettings) {
    val fontFamily = when (playbackSettings.subtitleFont) {
        SubtitleFont.DEFAULT -> FontFamily.Default
        SubtitleFont.MONOSPACE -> FontFamily.Monospace
        SubtitleFont.SANS_SERIF -> FontFamily.SansSerif
        SubtitleFont.SERIF -> FontFamily.Serif
    }

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = Color.Black.copy(alpha = 0.85f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)),
        modifier = Modifier
            .fillMaxWidth()
            .height(96.dp)
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            val bgModifier = when (playbackSettings.subtitleBgStyle) {
                1 -> Modifier
                    .background(Color.Black.copy(alpha = 0.55f), RoundedCornerShape(6.dp))
                    .padding(horizontal = 10.dp, vertical = 4.dp)
                2 -> Modifier
                    .background(Color.Black, RoundedCornerShape(6.dp))
                    .padding(horizontal = 10.dp, vertical = 4.dp)
                else -> Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
            }

            Box(modifier = bgModifier) {
                Text(
                    text = "Sample Subtitle Text Preview",
                    color = Color.White,
                    fontFamily = fontFamily,
                    fontWeight = if (playbackSettings.isSubtitleBold) FontWeight.Bold else FontWeight.Normal,
                    fontSize = (14 * playbackSettings.subtitleTextSizeScale.coerceIn(0.7f, 1.8f)).sp,
                    textAlign = TextAlign.Center
                )
            }

            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                shape = RoundedCornerShape(4.dp),
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp)
            ) {
                Text(
                    text = "PREVIEW",
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }
        }
    }
}

// -----------------------------------------------------------------------------
// TAB 3: SYNC & POSITIONING
// -----------------------------------------------------------------------------
@Composable
private fun SyncTab(
    playbackSettings: PlaybackSettings,
    onSetSubtitleDelay: (Long) -> Unit,
    onUpdateSubtitleDelay: (Long) -> Unit,
    onUpdateSubtitleVerticalOffset: (Float) -> Unit,
    onUpdateSubtitleGesturesEnabled: (Boolean) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        // Subtitle Delay Card
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.6f),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Timelapse,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = "Subtitle Delay Sync",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    Surface(
                        color = if (playbackSettings.subtitleDelayMs != 0L) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                        contentColor = if (playbackSettings.subtitleDelayMs != 0L) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = when {
                                playbackSettings.subtitleDelayMs > 0 -> "+${playbackSettings.subtitleDelayMs} ms"
                                playbackSettings.subtitleDelayMs < 0 -> "${playbackSettings.subtitleDelayMs} ms"
                                else -> "0 ms (In Sync)"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    LongPressTextButton(
                        text = "-100ms",
                        onClick = {
                            val newDelay = (playbackSettings.subtitleDelayMs - 100L).coerceIn(-600000L, 600000L)
                            onSetSubtitleDelay(newDelay)
                            onUpdateSubtitleDelay(newDelay)
                        },
                        modifier = Modifier.weight(1f).height(38.dp)
                    )

                    LongPressTextButton(
                        text = "-50ms",
                        onClick = {
                            val newDelay = (playbackSettings.subtitleDelayMs - 50L).coerceIn(-600000L, 600000L)
                            onSetSubtitleDelay(newDelay)
                            onUpdateSubtitleDelay(newDelay)
                        },
                        modifier = Modifier.weight(1f).height(38.dp)
                    )

                    IconButton(
                        onClick = {
                            onSetSubtitleDelay(0L)
                            onUpdateSubtitleDelay(0L)
                        },
                        modifier = Modifier
                            .size(38.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.SettingsBackupRestore,
                            contentDescription = "Reset sync",
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }

                    LongPressTextButton(
                        text = "+50ms",
                        onClick = {
                            val newDelay = (playbackSettings.subtitleDelayMs + 50L).coerceIn(-600000L, 600000L)
                            onSetSubtitleDelay(newDelay)
                            onUpdateSubtitleDelay(newDelay)
                        },
                        modifier = Modifier.weight(1f).height(38.dp)
                    )

                    LongPressTextButton(
                        text = "+100ms",
                        onClick = {
                            val newDelay = (playbackSettings.subtitleDelayMs + 100L).coerceIn(-600000L, 600000L)
                            onSetSubtitleDelay(newDelay)
                            onUpdateSubtitleDelay(newDelay)
                        },
                        modifier = Modifier.weight(1f).height(38.dp)
                    )
                }
            }
        }

        // Vertical Height Position Offset Card
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.6f),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.VerticalAlignBottom,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = "Vertical Alignment Height",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = "${(playbackSettings.subtitleVerticalOffset * 100).roundToInt()}%",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    LongPressStepButton(
                        icon = Icons.Rounded.Remove,
                        contentDescription = "Lower subtitles",
                        onStep = {
                            val newOffset = (playbackSettings.subtitleVerticalOffset - 0.05f)
                                .coerceAtLeast(0f)
                                .roundToTwoDecimals()
                            onUpdateSubtitleVerticalOffset(newOffset)
                        }
                    )

                    Slider(
                        value = playbackSettings.subtitleVerticalOffset,
                        onValueChange = { onUpdateSubtitleVerticalOffset(it.roundToTwoDecimals()) },
                        valueRange = 0f..0.85f,
                        modifier = Modifier.weight(1f)
                    )

                    LongPressStepButton(
                        icon = Icons.Rounded.Add,
                        contentDescription = "Raise subtitles",
                        onStep = {
                            val newOffset = (playbackSettings.subtitleVerticalOffset + 0.05f)
                                .coerceAtMost(0.85f)
                                .roundToTwoDecimals()
                            onUpdateSubtitleVerticalOffset(newOffset)
                        }
                    )
                }
            }
        }

        // Gesture Settings Card
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SectionHeader(title = "Gesture Controls")

            ToggleCardRow(
                title = "Swipe & Drag Gestures",
                description = "Drag subtitles on video screen to reposition freely",
                icon = Icons.Rounded.TouchApp,
                checked = playbackSettings.subtitleGesturesEnabled,
                onCheckedChange = onUpdateSubtitleGesturesEnabled
            )
        }
    }
}

// -----------------------------------------------------------------------------
// REUSABLE HELPER COMPONENTS
// -----------------------------------------------------------------------------
@Composable
private fun ToggleCardRow(
    title: String,
    description: String,
    icon: ImageVector,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Card(
        onClick = { onCheckedChange(!checked) },
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.6f)
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.weight(1f)
            ) {
                Surface(
                    color = if (checked) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = if (checked) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.size(36.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                Column {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f)
                    )
                }
            }

            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = MaterialTheme.colorScheme.primary,
                    checkedTrackColor = MaterialTheme.colorScheme.primaryContainer
                )
            )
        }
    }
}

@Composable
private fun LongPressStepButton(
    icon: ImageVector,
    contentDescription: String,
    onStep: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    Box(
        modifier = Modifier
            .size(34.dp)
            .clip(CircleShape)
            .background(
                if (isPressed) MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)
                else MaterialTheme.colorScheme.surfaceContainerHigh
            )
            .repeatingClickable(
                initialDelayMillis = 500,
                delayMillis = 100,
                interactionSource = interactionSource,
                onClick = onStep
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            modifier = Modifier.size(18.dp),
            tint = if (isPressed) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun LongPressTextButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(
                if (isPressed) MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)
                else MaterialTheme.colorScheme.surfaceContainerHigh
            )
            .repeatingClickable(
                interactionSource = interactionSource,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            color = if (isPressed) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
        )
    }
}
