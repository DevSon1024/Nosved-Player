package com.devson.nvplayer.ui.common.sheets

import android.content.res.Configuration
import androidx.compose.animation.*
import androidx.compose.animation.core.*
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
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.devson.nvplayer.data.repository.EnhanceMode
import com.devson.nvplayer.data.repository.PlaybackSettings
import com.devson.nvplayer.ui.common.components.SectionHeader
import com.devson.nvplayer.util.repeatingClickable
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EnhanceSettingsSideSheet(
    visible: Boolean,
    playbackSettings: PlaybackSettings,
    onUpdateEnhanceMode: (EnhanceMode) -> Unit,
    onUpdateEnhanceSaturation: (Int) -> Unit,
    onUpdateEnhanceContrast: (Int) -> Unit,
    onUpdateEnhanceBrightness: (Int) -> Unit,
    onUpdateEnhanceGamma: (Int) -> Unit,
    onUpdateEnhanceHue: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    val sheetWidthPercent = if (isLandscape) 0.5f else 0.75f

    val mode = playbackSettings.enhanceMode
    val isCustom = mode == EnhanceMode.CUSTOM

    // Effective values to display in the sliders when OFF or DEFAULT
    val displaySat = when (mode) {
        EnhanceMode.OFF -> 0
        EnhanceMode.DEFAULT -> 25
        EnhanceMode.CUSTOM -> playbackSettings.enhanceSaturation
    }
    val displayCon = when (mode) {
        EnhanceMode.OFF -> 0
        EnhanceMode.DEFAULT -> 8
        EnhanceMode.CUSTOM -> playbackSettings.enhanceContrast
    }
    val displayBright = when (mode) {
        EnhanceMode.OFF -> 0
        EnhanceMode.DEFAULT -> 0
        EnhanceMode.CUSTOM -> playbackSettings.enhanceBrightness
    }
    val displayGam = when (mode) {
        EnhanceMode.OFF -> 0
        EnhanceMode.DEFAULT -> 3
        EnhanceMode.CUSTOM -> playbackSettings.enhanceGamma
    }
    val displayHue = when (mode) {
        EnhanceMode.OFF -> 0
        EnhanceMode.DEFAULT -> 0
        EnhanceMode.CUSTOM -> playbackSettings.enhanceHue
    }

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.CenterEnd
    ) {
        // Scrim background
        AnimatedVisibility(
            visible = visible,
            enter = fadeIn(animationSpec = tween(250)),
            exit = fadeOut(animationSpec = tween(250)),
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

        // Sheet Panel
        AnimatedVisibility(
            visible = visible,
            enter = slideInHorizontally(
                initialOffsetX = { it },
                animationSpec = tween(300, easing = LinearOutSlowInEasing)
            ),
            exit = slideOutHorizontally(
                targetOffsetX = { it },
                animationSpec = tween(300, easing = FastOutLinearInEasing)
            ),
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth(sheetWidthPercent)
        ) {
            val sheetShape = RoundedCornerShape(topStart = 24.dp, bottomStart = 24.dp)
            Surface(
                modifier = Modifier
                    .fillMaxSize()
                    .border(
                        width = 1.dp,
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f),
                        shape = sheetShape
                    ),
                shape = sheetShape,
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
                                        imageVector = Icons.Rounded.AutoAwesome,
                                        contentDescription = null,
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                            }

                            Column {
                                Text(
                                    text = "Smart Enhance",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "Active: ${mode.name.lowercase().replaceFirstChar { it.uppercase() }}",
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
                                contentDescription = "Close sheet",
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    SectionHeader(title = "Enhancement Mode")

                    Spacer(modifier = Modifier.height(10.dp))

                    // Mode Selection Segmented Row
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                color = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.8f),
                                shape = RoundedCornerShape(12.dp)
                            )
                            .border(
                                width = 1.dp,
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f),
                                shape = RoundedCornerShape(12.dp)
                            )
                            .padding(4.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        EnhanceMode.values().forEach { m ->
                            val isSelected = mode == m
                            val bgSelectedColor = if (isSelected) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                Color.Transparent
                            }
                            val contentSelectedColor = if (isSelected) {
                                MaterialTheme.colorScheme.onPrimary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            }

                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(38.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(bgSelectedColor)
                                    .clickable { onUpdateEnhanceMode(m) },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = when (m) {
                                        EnhanceMode.OFF -> "Off"
                                        EnhanceMode.DEFAULT -> "Default"
                                        EnhanceMode.CUSTOM -> "Custom"
                                    },
                                    color = contentSelectedColor,
                                    fontSize = 13.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    SectionHeader(title = "Color & Contrast Tuning")

                    Spacer(modifier = Modifier.height(10.dp))

                    // Sliders List
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        EnhanceSliderItem(
                            label = "Saturation",
                            value = displaySat,
                            enabled = isCustom,
                            onValueChange = onUpdateEnhanceSaturation
                        )

                        EnhanceSliderItem(
                            label = "Contrast",
                            value = displayCon,
                            enabled = isCustom,
                            onValueChange = onUpdateEnhanceContrast
                        )

                        EnhanceSliderItem(
                            label = "Brightness",
                            value = displayBright,
                            enabled = isCustom,
                            onValueChange = onUpdateEnhanceBrightness
                        )

                        EnhanceSliderItem(
                            label = "Gamma",
                            value = displayGam,
                            enabled = isCustom,
                            onValueChange = onUpdateEnhanceGamma
                        )

                        EnhanceSliderItem(
                            label = "Hue",
                            value = displayHue,
                            enabled = isCustom,
                            onValueChange = onUpdateEnhanceHue
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun EnhanceSliderItem(
    label: String,
    value: Int,
    enabled: Boolean,
    onValueChange: (Int) -> Unit
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = if (enabled) MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.6f) else MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.25f),
        border = BorderStroke(
            width = 1.dp,
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = if (enabled) 0.3f else 0.15f)
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.titleSmall,
                    color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                    fontWeight = FontWeight.SemiBold
                )
                Surface(
                    color = if (enabled) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text(
                        text = if (value > 0) "+$value" else "$value",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (enabled) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                LongPressStepButton(
                    icon = Icons.Default.Remove,
                    contentDescription = "Decrease $label",
                    enabled = enabled,
                    onStep = {
                        val next = (value - 1).coerceIn(-100, 100)
                        onValueChange(next)
                    }
                )

                Slider(
                    value = value.toFloat(),
                    onValueChange = { newVal ->
                        onValueChange(newVal.roundToInt().coerceIn(-100, 100))
                    },
                    valueRange = -100f..100f,
                    enabled = enabled,
                    modifier = Modifier.weight(1f),
                    colors = SliderDefaults.colors(
                        disabledThumbColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.38f),
                        disabledActiveTrackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.24f),
                        disabledInactiveTrackColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.12f)
                    )
                )

                LongPressStepButton(
                    icon = Icons.Default.Add,
                    contentDescription = "Increase $label",
                    enabled = enabled,
                    onStep = {
                        val next = (value + 1).coerceIn(-100, 100)
                        onValueChange(next)
                    }
                )
            }
        }
    }
}

@Composable
private fun LongPressStepButton(
    icon: ImageVector,
    contentDescription: String,
    enabled: Boolean,
    onStep: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    Box(
        modifier = Modifier
            .size(36.dp)
            .clip(CircleShape)
            .background(
                if (!enabled) MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.4f)
                else if (isPressed) MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
                else MaterialTheme.colorScheme.secondaryContainer
            )
            .repeatingClickable(
                initialDelayMillis = 500,
                delayMillis = 100,
                interactionSource = interactionSource,
                enabled = enabled,
                onClick = onStep
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            modifier = Modifier.size(18.dp),
            tint = if (!enabled) MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.4f)
            else if (isPressed) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSecondaryContainer
        )
    }
}

