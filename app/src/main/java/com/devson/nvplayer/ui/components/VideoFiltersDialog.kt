package com.devson.nvplayer.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.devson.nvplayer.repository.PlaybackSettings

@Composable
fun VideoFiltersDialog(
    settings: PlaybackSettings,
    onDismiss: () -> Unit,
    onConfirm: (
        shouldApply: Boolean,
        isBrightnessEnabled: Boolean, brightness: Float,
        isContrastEnabled: Boolean, contrast: Float,
        isSaturationEnabled: Boolean, saturation: Float,
        isHueEnabled: Boolean, hue: Float,
        isGammaEnabled: Boolean, gamma: Float,
        isSharpeningEnabled: Boolean, sharpening: Float
    ) -> Unit
) {
    var shouldApply by remember { mutableStateOf(settings.shouldApplyVideoFilters) }
    var isBrightnessEnabled by remember { mutableStateOf(settings.isVideoBrightnessFilterEnabled) }
    var brightness by remember { mutableFloatStateOf(settings.videoBrightness) }
    var isContrastEnabled by remember { mutableStateOf(settings.isVideoContrastFilterEnabled) }
    var contrast by remember { mutableFloatStateOf(settings.videoContrast) }
    var isSaturationEnabled by remember { mutableStateOf(settings.isVideoSaturationFilterEnabled) }
    var saturation by remember { mutableFloatStateOf(settings.videoSaturation) }
    var isHueEnabled by remember { mutableStateOf(settings.isVideoHueFilterEnabled) }
    var hue by remember { mutableFloatStateOf(settings.videoHue) }
    var isGammaEnabled by remember { mutableStateOf(settings.isVideoGammaFilterEnabled) }
    var gamma by remember { mutableFloatStateOf(settings.videoGamma) }
    var isSharpeningEnabled by remember { mutableStateOf(settings.isVideoSharpeningFilterEnabled) }
    var sharpening by remember { mutableFloatStateOf(settings.videoSharpening) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Video Filters", style = MaterialTheme.typography.titleLarge)
                IconButton(onClick = {
                    shouldApply = false
                    isBrightnessEnabled = false; brightness = 0f
                    isContrastEnabled = false; contrast = 0f
                    isSaturationEnabled = false; saturation = 0f
                    isHueEnabled = false; hue = 0f
                    isGammaEnabled = false; gamma = 1f
                    isSharpeningEnabled = false; sharpening = 0f
                }) {
                    Icon(Icons.Default.Refresh, contentDescription = "Reset")
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Enable Filters", modifier = Modifier.weight(1f))
                    Switch(checked = shouldApply, onCheckedChange = { shouldApply = it })
                }
                
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                FilterSlider(
                    label = "Brightness",
                    enabled = shouldApply,
                    isChecked = isBrightnessEnabled,
                    onCheckedChange = { isBrightnessEnabled = it },
                    value = brightness,
                    onValueChange = { brightness = it },
                    valueRange = -1f..1f,
                    displayValue = "${(brightness * 100).toInt()}%"
                )

                FilterSlider(
                    label = "Contrast",
                    enabled = shouldApply,
                    isChecked = isContrastEnabled,
                    onCheckedChange = { isContrastEnabled = it },
                    value = contrast,
                    onValueChange = { contrast = it },
                    valueRange = -1f..1f,
                    displayValue = "${(contrast * 100).toInt()}%"
                )

                FilterSlider(
                    label = "Saturation",
                    enabled = shouldApply,
                    isChecked = isSaturationEnabled,
                    onCheckedChange = { isSaturationEnabled = it },
                    value = saturation,
                    onValueChange = { saturation = it },
                    valueRange = -100f..100f,
                    displayValue = "${saturation.toInt()}"
                )

                FilterSlider(
                    label = "Hue",
                    enabled = shouldApply,
                    isChecked = isHueEnabled,
                    onCheckedChange = { isHueEnabled = it },
                    value = hue,
                    onValueChange = { hue = it },
                    valueRange = -180f..180f,
                    displayValue = "${hue.toInt()}°"
                )

                FilterSlider(
                    label = "Gamma",
                    enabled = shouldApply,
                    isChecked = isGammaEnabled,
                    onCheckedChange = { isGammaEnabled = it },
                    value = gamma,
                    onValueChange = { gamma = it },
                    valueRange = 0.1f..3.0f,
                    displayValue = String.format("%.2f", gamma)
                )

                FilterSlider(
                    label = "Sharpening",
                    enabled = shouldApply,
                    isChecked = isSharpeningEnabled,
                    onCheckedChange = { isSharpeningEnabled = it },
                    value = sharpening,
                    onValueChange = { sharpening = it },
                    valueRange = 0f..1f,
                    displayValue = "${(sharpening * 100).toInt()}%"
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onConfirm(
                    shouldApply,
                    isBrightnessEnabled, brightness,
                    isContrastEnabled, contrast,
                    isSaturationEnabled, saturation,
                    isHueEnabled, hue,
                    isGammaEnabled, gamma,
                    isSharpeningEnabled, sharpening
                )
                onDismiss()
            }) {
                Text("Apply")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
private fun FilterSlider(
    label: String,
    enabled: Boolean,
    isChecked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    displayValue: String
) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(
                checked = isChecked,
                onCheckedChange = onCheckedChange,
                enabled = enabled
            )
            Text(label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
            Text(displayValue, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            enabled = enabled && isChecked,
            modifier = Modifier.padding(horizontal = 12.dp)
        )
    }
}
