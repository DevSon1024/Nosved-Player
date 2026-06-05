package com.devson.nvplayer.ui.component

import android.content.res.Configuration
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.devson.nvplayer.repository.PlaybackSettings

@Composable
fun QualitySettingsSideSheet(
    visible: Boolean,
    playbackSettings: PlaybackSettings,
    onSelectQuality: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    val sheetWidthPercent = if (isLandscape) 0.5f else 1.0f

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
                                imageVector = Icons.Rounded.Settings,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(24.dp)
                            )
                            Text(
                                text = "Stream Quality Selection",
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
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        val qualities = listOf(
                            -1 to "Auto / Maximum Quality",
                            2160 to "2160p (4K)",
                            1440 to "1440p (2K)",
                            1080 to "1080p (Full HD)",
                            720 to "720p (HD)",
                            480 to "480p",
                            360 to "360p",
                            240 to "240p",
                            144 to "144p"
                        )

                        qualities.forEach { (q, label) ->
                            val isSelected = playbackSettings.ytdlQuality == q
                            QualityItem(
                                title = label,
                                isSelected = isSelected,
                                onClick = {
                                    onSelectQuality(q)
                                    onDismiss()
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun QualityItem(
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
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = title,
            fontSize = 14.sp,
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
