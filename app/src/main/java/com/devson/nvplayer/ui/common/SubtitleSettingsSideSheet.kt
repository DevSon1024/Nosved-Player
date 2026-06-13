package com.devson.nvplayer.ui.common

import android.content.res.Configuration
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.animateContentSize
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.shape.CircleShape
import com.devson.nvplayer.util.repeatingClickable
import com.devson.nvplayer.ui.common.components.SectionHeader
import com.devson.nvplayer.util.roundToTwoDecimals
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.devson.nvplayer.player.model.TrackInfo
import com.devson.nvplayer.data.repository.SubtitleFont
import com.devson.nvplayer.data.repository.PlaybackSettings
import kotlin.math.roundToInt

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
    val sheetWidthPercent = if (isLandscape) 0.5f else 1.0f

    var styleOptionsExpanded by remember { mutableStateOf(false) }
    var syncOptionsExpanded by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = if (isLandscape) Alignment.CenterEnd else Alignment.BottomCenter
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
                    .background(if (isLandscape) Color.Black.copy(alpha = 0.45f) else Color.Black.copy(alpha = 0.1f))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onDismiss
                    )
            )
        }

        // Sheet Content
        val enterAnim = if (isLandscape) {
            slideInHorizontally(
                initialOffsetX = { it },
                animationSpec = tween(durationMillis = 300, easing = LinearOutSlowInEasing)
            )
        } else {
            slideInVertically(
                initialOffsetY = { it },
                animationSpec = tween(durationMillis = 300, easing = LinearOutSlowInEasing)
            )
        }

        val exitAnim = if (isLandscape) {
            slideOutHorizontally(
                targetOffsetX = { it },
                animationSpec = tween(durationMillis = 300, easing = FastOutLinearInEasing)
            )
        } else {
            slideOutVertically(
                targetOffsetY = { it },
                animationSpec = tween(durationMillis = 300, easing = FastOutLinearInEasing)
            )
        }

        AnimatedVisibility(
            visible = visible,
            enter = enterAnim,
            exit = exitAnim,
            modifier = if (isLandscape) {
                Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(sheetWidthPercent)
            } else {
                Modifier
                    .fillMaxWidth()
                    .wrapContentHeight(Alignment.Bottom)
            }
        ) {
            Box(
                modifier = if (isLandscape) {
                    Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.background.copy(alpha = 0.88f))
                        .border(
                            width = 1.dp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f),
                            shape = RoundedCornerShape(topStart = 16.dp, bottomStart = 16.dp)
                        )
                } else {
                    Modifier
                        .fillMaxWidth()
                        .wrapContentHeight()
                        .heightIn(max = (configuration.screenHeightDp * 0.6f).dp)
                        .animateContentSize(animationSpec = tween(300, easing = FastOutSlowInEasing))
                        .background(
                            color = MaterialTheme.colorScheme.background.copy(alpha = 0.65f),
                            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
                        )
                        .border(
                            width = 1.dp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f),
                            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
                        )
                }
            ) {
                Column(
                    modifier = if (isLandscape) {
                        Modifier
                            .fillMaxSize()
                            .systemBarsPadding()
                            .padding(horizontal = 24.dp, vertical = 20.dp)
                    } else {
                        Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding()
                            .statusBarsPadding()
                            .padding(horizontal = 24.dp, vertical = 20.dp)
                    }
                ) {
                    // Header
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
                                imageVector = Icons.Rounded.Subtitles,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(24.dp)
                            )
                            Text(
                                text = "Subtitle Settings",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        IconButton(onClick = onDismiss) {
                            Icon(
                                imageVector = Icons.Rounded.Close,
                                contentDescription = "Close panel"
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Scrollable content
                    Column(
                        modifier = Modifier
                            .then(
                                if (isLandscape) Modifier.weight(1f)
                                else Modifier.weight(1f, fill = false)
                            )
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(24.dp)
                    ) {
                        // Section 1: Subtitle Tracks
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                SectionHeader(title = "Subtitle Tracks")
                                TextButton(
                                    onClick = onImportSubtitleClick,
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Add,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Import", fontSize = 13.sp)
                                }
                            }

                            subtitleTracks.forEach { track ->
                                TrackItem(
                                    title = track.name,
                                    isSelected = track.selected,
                                    isExternal = track.isExternal,
                                    isNone = track.id == -1,
                                    onClick = { onSelectSubtitleTrack(track.id) }
                                )
                            }
                        }

                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f))

                        // Section 2: Styling
                        Column {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable { styleOptionsExpanded = !styleOptionsExpanded }
                                    .padding(vertical = 8.dp, horizontal = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                SectionHeader(title = "Style Options")
                                Icon(
                                    imageVector = if (styleOptionsExpanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                                    contentDescription = if (styleOptionsExpanded) "Collapse" else "Expand",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }

                            AnimatedVisibility(visible = styleOptionsExpanded) {
                                Column(
                                    modifier = Modifier.padding(top = 8.dp),
                                    verticalArrangement = Arrangement.spacedBy(16.dp)
                                ) {
                                    // Text Size slider
                                    Column(modifier = Modifier.fillMaxWidth()) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = "Text Size Scale",
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                            Text(
                                                text = "%.1fx".format(playbackSettings.subtitleTextSizeScale),
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(top = 4.dp)
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

                                    // Font Family
                                    Column(modifier = Modifier.fillMaxWidth()) {
                                        Text(
                                            text = "Font Family",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.padding(bottom = 8.dp)
                                        )
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
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
                                                val containerColor = if (isSelected) {
                                                    MaterialTheme.colorScheme.primary
                                                } else {
                                                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)
                                                }
                                                val contentColor = if (isSelected) {
                                                    MaterialTheme.colorScheme.onPrimary
                                                } else {
                                                    MaterialTheme.colorScheme.onSurface
                                                }

                                                Box(
                                                    modifier = Modifier
                                                        .weight(1f)
                                                        .clip(RoundedCornerShape(8.dp))
                                                        .background(containerColor)
                                                        .clickable { onUpdateSubtitleFont(font) }
                                                        .padding(vertical = 10.dp),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Text(
                                                        text = fontName,
                                                        fontFamily = fontFamily,
                                                        fontSize = 13.sp,
                                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                                        color = contentColor
                                                    )
                                                }
                                            }
                                        }
                                    }

                                    // Background Style
                                    Column(modifier = Modifier.fillMaxWidth()) {
                                        Text(
                                            text = "Background Style",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.padding(bottom = 8.dp)
                                        )
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            listOf(
                                                0 to "None",
                                                1 to "Glass",
                                                2 to "Solid Black"
                                            ).forEach { (styleId, styleName) ->
                                                val isSelected = playbackSettings.subtitleBgStyle == styleId
                                                val containerColor = if (isSelected) {
                                                    MaterialTheme.colorScheme.primary
                                                } else {
                                                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)
                                                }
                                                val contentColor = if (isSelected) {
                                                    MaterialTheme.colorScheme.onPrimary
                                                } else {
                                                    MaterialTheme.colorScheme.onSurface
                                                }

                                                Box(
                                                    modifier = Modifier
                                                        .weight(1f)
                                                        .clip(RoundedCornerShape(8.dp))
                                                        .background(containerColor)
                                                        .clickable { onUpdateSubtitleBgStyle(styleId) }
                                                        .padding(vertical = 10.dp),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Text(
                                                        text = styleName,
                                                        fontSize = 13.sp,
                                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                                        color = contentColor
                                                    )
                                                }
                                            }
                                        }
                                    }

                                    // Toggles
                                    ToggleRow(
                                        title = "Bold Subtitles",
                                        description = "Thicker and easier to read fonts",
                                        checked = playbackSettings.isSubtitleBold,
                                        onCheckedChange = onUpdateIsSubtitleBold
                                    )

                                    ToggleRow(
                                        title = "Swipe & Drag Gestures",
                                        description = "Drag subtitles to reposition, swipe to seek dialogs",
                                        checked = playbackSettings.subtitleGesturesEnabled,
                                        onCheckedChange = onUpdateSubtitleGesturesEnabled
                                    )

                                    ToggleRow(
                                        title = "Override Embedded Style",
                                        description = "Force styling settings on SSA/ASS formats",
                                        checked = playbackSettings.forceAssSubtitleOverride,
                                        onCheckedChange = onUpdateForceAssSubtitleOverride
                                    )
                                }
                            }
                        }

                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f))

                        // Section 3: Sync & Vertical Height Offset
                        Column {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable { syncOptionsExpanded = !syncOptionsExpanded }
                                    .padding(vertical = 8.dp, horizontal = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                SectionHeader(title = "Sync & Height Options")
                                Icon(
                                    imageVector = if (syncOptionsExpanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                                    contentDescription = if (syncOptionsExpanded) "Collapse" else "Expand",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }

                            AnimatedVisibility(visible = syncOptionsExpanded) {
                                Column(
                                    modifier = Modifier.padding(top = 8.dp),
                                    verticalArrangement = Arrangement.spacedBy(16.dp)
                                ) {
                                    // Delay Control
                                    Column(modifier = Modifier.fillMaxWidth()) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = "Subtitle Delay Sync",
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                            Text(
                                                text = when {
                                                    playbackSettings.subtitleDelayMs > 0 -> "+${playbackSettings.subtitleDelayMs} ms"
                                                    playbackSettings.subtitleDelayMs < 0 -> "${playbackSettings.subtitleDelayMs} ms"
                                                    else -> "0 ms (Sync)"
                                                },
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = FontWeight.Bold,
                                                color = if (playbackSettings.subtitleDelayMs != 0L) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }

                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(top = 8.dp),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
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
                                                onClick = { onSetSubtitleDelay(0L)
                                                            onUpdateSubtitleDelay(0L) },
                                                modifier = Modifier
                                                    .size(38.dp)
                                                    .clip(RoundedCornerShape(8.dp))
                                                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Rounded.SettingsBackupRestore,
                                                    contentDescription = "Reset sync",
                                                    modifier = Modifier.size(16.dp)
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

                                    // Vertical offset slider
                                    Column(modifier = Modifier.fillMaxWidth()) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = "Vertical Alignment Height",
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                            Text(
                                                text = "${(playbackSettings.subtitleVerticalOffset * 100).roundToInt()}%",
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(top = 4.dp)
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
    title: String,
    isSelected: Boolean,
    isExternal: Boolean,
    isNone: Boolean,
    onClick: () -> Unit
) {
    val containerColor = if (isSelected) {
        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.15f)
    } else {
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.12f)
    }
    val borderColor = if (isSelected) {
        MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)
    } else {
        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f)
    }
    val contentColor = if (isSelected) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.onSurface
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(containerColor)
            .border(1.dp, borderColor, RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = title,
                fontSize = 13.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                color = contentColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (isExternal) {
                Surface(
                    color = MaterialTheme.colorScheme.tertiaryContainer,
                    contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Text(
                        text = "ext",
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                    )
                }
            }
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (!isNone) {
                Text(
                    text = if (isExternal) "External" else "Embedded",
                    fontSize = 11.sp,
                    color = if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.8f) else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    fontWeight = FontWeight.Normal
                )
            }
            if (isSelected) {
                Icon(
                    imageVector = Icons.Rounded.CheckCircle,
                    contentDescription = "Selected",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                )
            } else {
                Spacer(modifier = Modifier.size(18.dp))
            }
        }
    }
}

@Composable
private fun ToggleRow(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
            )
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
            .size(32.dp)
            .clip(CircleShape)
            .background(
                if (isPressed) MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
                else MaterialTheme.colorScheme.secondaryContainer
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
            modifier = Modifier.size(16.dp),
            tint = if (isPressed) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSecondaryContainer
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
            .clip(RoundedCornerShape(8.dp))
            .background(
                if (isPressed) MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
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
            fontWeight = FontWeight.Medium,
            color = if (isPressed) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
        )
    }
}
