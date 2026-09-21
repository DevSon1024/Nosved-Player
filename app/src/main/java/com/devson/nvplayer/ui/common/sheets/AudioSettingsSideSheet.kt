package com.devson.nvplayer.ui.common.sheets

import android.content.res.Configuration
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.devson.nvplayer.player.model.TrackInfo
import com.devson.nvplayer.ui.common.components.SectionHeader

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AudioSettingsSideSheet(
    visible: Boolean,
    audioTracks: List<TrackInfo>,
    audioBoosterEnabled: Boolean,
    onToggleAudioBooster: (Boolean) -> Unit,
    onSelectAudioTrack: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    val sheetWidthPercent = if (isLandscape) 0.48f else 0.85f

    val activeTrack = remember(audioTracks) { audioTracks.find { it.selected } }

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
                    // Header
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
                                        imageVector = Icons.AutoMirrored.Rounded.VolumeUp,
                                        contentDescription = null,
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                            }

                            Column {
                                Text(
                                    text = "Audio Settings",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = if (activeTrack != null) "Active: ${activeTrack.name}" else "${audioTracks.size} tracks available",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
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

                    // Scrollable content
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(20.dp)
                    ) {
                        // Section 1: Audio Tracks
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            SectionHeader(title = "Audio Streams")

                            if (audioTracks.isEmpty()) {
                                Surface(
                                    shape = RoundedCornerShape(14.dp),
                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        text = "No audio tracks detected in media",
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        style = MaterialTheme.typography.bodyMedium,
                                        modifier = Modifier.padding(16.dp)
                                    )
                                }
                            } else {
                                audioTracks.forEach { track ->
                                    TrackCard(
                                        title = track.name,
                                        isSelected = track.selected,
                                        onClick = { onSelectAudioTrack(track.id) }
                                    )
                                }
                            }
                        }

                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f))

                        // Section 2: Audio Enhancement & Boost
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            SectionHeader(title = "Volume & Enhancement")

                            Card(
                                onClick = { onToggleAudioBooster(!audioBoosterEnabled) },
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = if (audioBoosterEnabled) {
                                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
                                    } else {
                                        MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.6f)
                                    }
                                ),
                                border = BorderStroke(
                                    width = if (audioBoosterEnabled) 1.5.dp else 1.dp,
                                    color = if (audioBoosterEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
                                ),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 14.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        modifier = Modifier.weight(1f),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        Surface(
                                            color = if (audioBoosterEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                                            contentColor = if (audioBoosterEnabled) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                                            shape = CircleShape,
                                            modifier = Modifier.size(36.dp)
                                        ) {
                                            Box(contentAlignment = Alignment.Center) {
                                                Icon(
                                                    imageVector = Icons.AutoMirrored.Rounded.VolumeUp,
                                                    contentDescription = null,
                                                    modifier = Modifier.size(20.dp)
                                                )
                                            }
                                        }

                                        Column {
                                            Text(
                                                text = "2x Volume Booster",
                                                style = MaterialTheme.typography.titleSmall,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                            Text(
                                                text = "Amplify low-volume media audio up to 200% via software pre-gain",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }

                                    Spacer(modifier = Modifier.width(8.dp))

                                    Switch(
                                        checked = audioBoosterEnabled,
                                        onCheckedChange = onToggleAudioBooster,
                                        colors = SwitchDefaults.colors(
                                            checkedThumbColor = MaterialTheme.colorScheme.primary,
                                            checkedTrackColor = MaterialTheme.colorScheme.primaryContainer
                                        )
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
private fun TrackCard(
    title: String,
    isSelected: Boolean,
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
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
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
                    color = if (isSelected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (isSelected) {
                Surface(
                    color = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text(
                        text = "ACTIVE",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            }
        }
    }
}
