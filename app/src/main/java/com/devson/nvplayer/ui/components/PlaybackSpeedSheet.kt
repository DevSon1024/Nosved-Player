package com.devson.nvplayer.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp

private const val SPEED_MIN = 0.25f
private const val SPEED_MAX = 4.0f
private const val SPEED_STEP = 0.05f

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaybackSpeedSheet(
    showSheet: Boolean,
    speed: Float,
    isLandscape: Boolean,
    onSpeedChange: (Float) -> Unit,
    onDismiss: () -> Unit
) {
    if (!showSheet) return

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var textValue by remember(speed) { mutableStateOf(formatSpeed(speed)) }
    var sliderValue by remember(speed) { mutableFloatStateOf(speed.coerceIn(SPEED_MIN, SPEED_MAX)) }

    fun applySpeed(newSpeed: Float) {
        val clamped = newSpeed.coerceIn(SPEED_MIN, SPEED_MAX)
        sliderValue = clamped
        textValue = formatSpeed(clamped)
        onSpeedChange(clamped)
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        modifier = if (isLandscape) Modifier.fillMaxWidth(0.6f) else Modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            Text(
                text = "Playback Speed",
                style = MaterialTheme.typography.titleMedium
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                IconButton(
                    onClick = { applySpeed(sliderValue - SPEED_STEP) },
                    enabled = sliderValue > SPEED_MIN
                ) {
                    Icon(Icons.Filled.Remove, contentDescription = "Decrease speed")
                }

                OutlinedTextField(
                    value = textValue,
                    onValueChange = { raw ->
                        textValue = raw
                        raw.toFloatOrNull()?.let { parsed ->
                            val clamped = parsed.coerceIn(SPEED_MIN, SPEED_MAX)
                            sliderValue = clamped
                            onSpeedChange(clamped)
                        }
                    },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    label = { Text("Speed (${SPEED_MIN}x - ${SPEED_MAX}x)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    suffix = { Text("x") }
                )

                IconButton(
                    onClick = { applySpeed(sliderValue + SPEED_STEP) },
                    enabled = sliderValue < SPEED_MAX
                ) {
                    Icon(Icons.Filled.Add, contentDescription = "Increase speed")
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Slider(
                    value = sliderValue,
                    onValueChange = { applySpeed(it) },
                    valueRange = SPEED_MIN..SPEED_MAX,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = { applySpeed(1.0f) }) {
                    Icon(Icons.Filled.Refresh, contentDescription = "Reset to 1x")
                }
            }

            Text(
                text = "Current: ${formatSpeed(sliderValue)}x",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private fun formatSpeed(speed: Float): String {
    val rounded = kotlin.math.round(speed * 100) / 100.0
    return if (rounded == rounded.toLong().toDouble()) {
        rounded.toLong().toString()
    } else {
        rounded.toBigDecimal().stripTrailingZeros().toPlainString()
    }
}
