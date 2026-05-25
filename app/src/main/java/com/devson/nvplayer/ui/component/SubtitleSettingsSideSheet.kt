package com.devson.nvplayer.ui.component

import android.content.res.Configuration
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
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
import com.devson.nvplayer.util.roundToTwoDecimals
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.devson.nvplayer.player.TrackInfo
import com.devson.nvplayer.repository.SubtitleFont
import com.devson.nvplayer.repository.PlaybackSettings
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
    onDismiss: () -> Unit
) {
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    val sheetWidthPercent = if (isLandscape) 0.5f else 0.75f

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
                    .background(Color.Black.copy(alpha = 0.45f))
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
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background.copy(alpha = 0.88f))
                    .border(
                        width = 1.dp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f),
                        shape = RoundedCornerShape(topStart = 16.dp, bottomStart = 16.dp)
                    )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .systemBarsPadding()
                        .padding(horizontal = 24.dp, vertical = 20.dp)
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
                            .weight(1f)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(24.dp)
                    ) {
                        // Section 1: Subtitle Tracks
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            SectionHeader(title = "Subtitle Tracks")

                            val isNoSubtitleSelected = subtitleTracks.none { it.selected }
                            // TrackItem(
                            //     title = "None (Disabled)",
                            //     isSelected = isNoSubtitleSelected,
                            //     onClick = { onSelectSubtitleTrack(-1) }
                            // )

                            subtitleTracks.forEach { track ->
                                TrackItem(
                                    title = track.name,
                                    isSelected = track.selected,
                                    onClick = { onSelectSubtitleTrack(track.id) }
                                )
                            }
                        }

                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f))

                        // Section 2: Styling
                        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            SectionHeader(title = "Style Options")

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

                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f))

                        // Section 3: Sync & Vertical Height Offset
                        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            SectionHeader(title = "Sync & Height Options")

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

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(bottom = 4.dp)
    )
}

@Composable
private fun TrackItem(
    title: String,
    isSelected: Boolean,
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
        Text(
            text = title,
            fontSize = 13.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
            color = contentColor,
            modifier = Modifier.weight(1f)
        )
        if (isSelected) {
            Icon(
                imageVector = Icons.Rounded.CheckCircle,
                contentDescription = "Selected",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp)
            )
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
