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
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.devson.nvplayer.repository.DoubleTapAction
import com.devson.nvplayer.repository.PlaybackSettings
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.coroutineScope
import androidx.compose.ui.composed
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.foundation.interaction.collectIsPressedAsState
import kotlin.math.roundToInt

//  Step constants 
private const val SPEED_STEP = 0.05f
private const val LONG_PRESS_INITIAL_DELAY = 400L  // ms before repeat starts
private const val LONG_PRESS_REPEAT_INTERVAL = 80L // ms between repeats

@Composable
fun PlaybackSpeedSideSheet(
    visible: Boolean,
    currentSpeed: Float,
    playbackSettings: PlaybackSettings = PlaybackSettings(),
    onSpeedSelected: (Float) -> Unit,
    onUpdateDoubleTapAction: (DoubleTapAction) -> Unit = {},
    onUpdateDoubleTapSeekDuration: (Long) -> Unit = {},
    onUpdateLongPressEnabled: (Boolean) -> Unit = {},
    onUpdateTapAndHoldSpeed: (Float) -> Unit = {},
    onUpdateLongPressSpeed: (Float) -> Unit = {},
    onDismiss: () -> Unit
) {
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    val sheetWidthPercent = if (isLandscape) 0.5f else 0.75f

    var textValue by remember(currentSpeed) {
        mutableStateOf(String.format(java.util.Locale.US, "%.2f", currentSpeed))
    }

    var showDoubleTapDialog by remember { mutableStateOf(false) }
    var showSeekDurationDialog by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.CenterEnd
    ) {
        //  Scrim 
        AnimatedVisibility(
            visible = visible,
            enter = fadeIn(animationSpec = tween(250)),
            exit  = fadeOut(animationSpec = tween(250)),
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

        //  Side sheet panel 
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
                    //  Header 
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
                                imageVector = Icons.Rounded.Speed,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(24.dp)
                            )
                            Text(
                                text = "Playback Speed",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            // Global reset to 1.00x
                            IconButton(
                                onClick = {
                                    onSpeedSelected(1.0f)
                                    textValue = "1.00"
                                }
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Refresh,
                                    contentDescription = "Reset speed to 1x",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            IconButton(onClick = onDismiss) {
                                Icon(
                                    imageVector = Icons.Rounded.Close,
                                    contentDescription = "Close speed panel"
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    //  Scrollable body 
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(24.dp)
                    ) {

                        //  1. Large speed display 
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = String.format(java.util.Locale.US, "%.2fx", currentSpeed),
                                fontSize = 48.sp,
                                fontWeight = FontWeight.Black,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(vertical = 8.dp)
                            )
                        }

                        //  2. Speed Slider (0x – 4x) with ± buttons 
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            SpeedSheetSectionLabel("Speed Slider")
                            StepSlider(
                                value = currentSpeed,
                                onValueChange = { v ->
                                    onSpeedSelected(v)
                                    textValue = String.format(java.util.Locale.US, "%.2f", v)
                                },
                                valueRange = 0.25f..4f,
                                step = SPEED_STEP,
                                defaultValue = 1.0f
                            )
                            // Tick labels
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                listOf("0.25x", "1x", "2x", "3x", "4x").forEach { label ->
                                    Text(
                                        text = label,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f)
                                    )
                                }
                            }
                        }

                        //  3. Presets 
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            SpeedSheetSectionLabel("Presets")
                            val presets = listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f)
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                presets.chunked(3).forEach { rowItems ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        rowItems.forEach { speed ->
                                            val isSelected = kotlin.math.abs(currentSpeed - speed) < 0.01f
                                            Box(
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .height(48.dp)
                                                    .clip(RoundedCornerShape(12.dp))
                                                    .background(
                                                        if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                                                        else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                                                    )
                                                    .border(
                                                        width = 1.5.dp,
                                                        color = if (isSelected) MaterialTheme.colorScheme.primary
                                                        else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                                                        shape = RoundedCornerShape(12.dp)
                                                    )
                                                    .clickable {
                                                        onSpeedSelected(speed)
                                                        textValue = String.format(java.util.Locale.US, "%.2f", speed)
                                                    },
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Text(
                                                    text = String.format(java.util.Locale.US, "%.2fx", speed),
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    fontWeight = FontWeight.Bold,
                                                    color = if (isSelected) MaterialTheme.colorScheme.primary
                                                    else MaterialTheme.colorScheme.onSurface
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        //  4. Custom Speed Entry 
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            SpeedSheetSectionLabel("Custom Speed")
                            val parsedSpeed = textValue.toFloatOrNull()
                            val isValid = parsedSpeed != null && parsedSpeed in 0.25f..4.0f
                            val isError = textValue.isNotEmpty() && !isValid

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedTextField(
                                    value = textValue,
                                    onValueChange = { nv ->
                                        val s = nv.filter { it.isDigit() || it == '.' }
                                        if (s.count { it == '.' } <= 1) textValue = s
                                    },
                                    placeholder = { Text("1.00") },
                                    singleLine = true,
                                    isError = isError,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    modifier = Modifier.weight(1f),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                                        unfocusedBorderColor = MaterialTheme.colorScheme.outline
                                    )
                                )
                                Button(
                                    onClick = { parsedSpeed?.let { onSpeedSelected(it) } },
                                    enabled = isValid,
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.height(56.dp)
                                ) { Text("Apply") }
                            }
                            Text(
                                text = if (isError) "Enter a speed between 0.25x and 4.0x"
                                       else "Speed range: 0.25x – 4.00x",
                                color = if (isError) MaterialTheme.colorScheme.error
                                        else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(start = 4.dp)
                            )
                        }

                        //  5. Press & Tap Controls 
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            SpeedSheetSectionLabel("Press & Tap Controls")

                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(16.dp),
                                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                tonalElevation = 1.dp
                            ) {
                                Column {
                                    // Double Tap Action row
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable { showDoubleTapDialog = true }
                                            .padding(horizontal = 16.dp, vertical = 14.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.TouchApp,
                                            contentDescription = null,
                                            modifier = Modifier.size(20.dp),
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = "Double Tap Action",
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = FontWeight.Medium
                                            )
                                            Text(
                                                text = when (playbackSettings.doubleTapAction) {
                                                    DoubleTapAction.BOTH         -> "Seek on double-tap"
                                                    DoubleTapAction.PLAY_PAUSE   -> "Play / Pause"
                                                    DoubleTapAction.FAST_FORWARD -> "Fast Forward Only"
                                                    DoubleTapAction.REWIND       -> "Rewind Only"
                                                    DoubleTapAction.NONE         -> "No Action"
                                                },
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        Icon(
                                            imageVector = Icons.Rounded.ChevronRight,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp),
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }

                                    HorizontalDivider(
                                        modifier = Modifier.padding(start = 50.dp),
                                        color = MaterialTheme.colorScheme.outlineVariant
                                    )

                                    // Double Tap Seek Duration row
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable { showSeekDurationDialog = true }
                                            .padding(horizontal = 16.dp, vertical = 14.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Timer,
                                            contentDescription = null,
                                            modifier = Modifier.size(20.dp),
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = "Double Tap Seek Duration",
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = FontWeight.Medium
                                            )
                                            Text(
                                                text = "${playbackSettings.doubleTapSeekDuration / 1000} seconds",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        Icon(
                                            imageVector = Icons.Rounded.ChevronRight,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp),
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }

                                    HorizontalDivider(
                                        modifier = Modifier.padding(start = 50.dp),
                                        color = MaterialTheme.colorScheme.outlineVariant
                                    )

                                    // Press & Hold toggle
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable { onUpdateLongPressEnabled(!playbackSettings.longPressEnabled) }
                                            .padding(horizontal = 16.dp, vertical = 14.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.Speed,
                                            contentDescription = null,
                                            modifier = Modifier.size(20.dp),
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = "Press & Hold Acceleration",
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = FontWeight.Medium
                                            )
                                            Text(
                                                text = "Hold screen to temporarily speed up video",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        Switch(
                                            checked = playbackSettings.longPressEnabled,
                                            onCheckedChange = { onUpdateLongPressEnabled(it) },
                                            modifier = Modifier.minimumInteractiveComponentSize()
                                        )
                                    }

                                    // Speed sliders (shown when long press is enabled)
                                    if (playbackSettings.longPressEnabled) {
                                        HorizontalDivider(
                                            modifier = Modifier.padding(start = 50.dp),
                                            color = MaterialTheme.colorScheme.outlineVariant
                                        )
                                        SpeedInlineSlider(
                                            label = "Tap & Hold Speed Override",
                                            value = playbackSettings.tapAndHoldSpeed,
                                            valueRange = 1.5f..4.0f,
                                            step = SPEED_STEP,
                                            defaultValue = 2.0f,
                                            onValueChange = onUpdateTapAndHoldSpeed
                                        )
                                        HorizontalDivider(
                                            modifier = Modifier.padding(start = 50.dp),
                                            color = MaterialTheme.colorScheme.outlineVariant
                                        )
                                        SpeedInlineSlider(
                                            label = "Long Press Default Speed",
                                            value = playbackSettings.longPressSpeed,
                                            valueRange = 1.5f..4.0f,
                                            step = SPEED_STEP,
                                            defaultValue = 2.0f,
                                            onValueChange = onUpdateLongPressSpeed
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(Modifier.height(8.dp))
                    }
                }
            }
        }
    }

    //  Dialog: Double Tap Action 
    if (showDoubleTapDialog) {
        val actions = DoubleTapAction.values().toList()
        FullListDialog(
            title = "Double Tap Action",
            onDismiss = { showDoubleTapDialog = false },
            onReset = {
                onUpdateDoubleTapAction(DoubleTapAction.BOTH)
                showDoubleTapDialog = false
            }
        ) {
            items(actions) { action ->
                val selected = playbackSettings.doubleTapAction == action
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .selectable(
                            selected = selected,
                            onClick = {
                                onUpdateDoubleTapAction(action)
                                showDoubleTapDialog = false
                            },
                            role = Role.RadioButton
                        )
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(selected = selected, onClick = null)
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(
                            text = when (action) {
                                DoubleTapAction.BOTH         -> "Seek Left/Right"
                                DoubleTapAction.PLAY_PAUSE   -> "Play / Pause"
                                DoubleTapAction.FAST_FORWARD -> "Fast Forward Only"
                                DoubleTapAction.REWIND       -> "Rewind Only"
                                DoubleTapAction.NONE         -> "Disable Double Tap"
                            },
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
                        )
                        Text(
                            text = when (action) {
                                DoubleTapAction.BOTH         -> "Tap left to rewind, right to forward"
                                DoubleTapAction.PLAY_PAUSE   -> "Double tap anywhere to toggle playback"
                                DoubleTapAction.FAST_FORWARD -> "Double tap to jump forward"
                                DoubleTapAction.REWIND       -> "Double tap to jump backward"
                                DoubleTapAction.NONE         -> "No gesture on double tap"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                if (action != actions.last()) {
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                }
            }
        }
    }

    //  Dialog: Double Tap Seek Duration 
    if (showSeekDurationDialog) {
        val durations = listOf(5000L, 10000L, 15000L, 30000L, 60000L)
        FullListDialog(
            title = "Seek Duration",
            onDismiss = { showSeekDurationDialog = false },
            onReset = {
                onUpdateDoubleTapSeekDuration(10000L)
                showSeekDurationDialog = false
            }
        ) {
            items(durations) { duration ->
                val selected = playbackSettings.doubleTapSeekDuration == duration
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .selectable(
                            selected = selected,
                            onClick = {
                                onUpdateDoubleTapSeekDuration(duration)
                                showSeekDurationDialog = false
                            },
                            role = Role.RadioButton
                        )
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(selected = selected, onClick = null)
                    Spacer(Modifier.width(12.dp))
                    Text(
                        text = "${duration / 1000} seconds",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
                    )
                }
                if (duration != durations.last()) {
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                }
            }
        }
    }
}

//  Reusable Dialog with LazyColumn + Reset 
@Composable
private fun FullListDialog(
    title: String,
    onDismiss: () -> Unit,
    onReset: () -> Unit,
    content: androidx.compose.foundation.lazy.LazyListScope.() -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .wrapContentHeight(),
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            Column {
                // Title row with Reset button
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 20.dp, end = 8.dp, top = 16.dp, bottom = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    TextButton(
                        onClick = onReset,
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Reset",
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text("Reset", style = MaterialTheme.typography.labelMedium)
                    }
                }

                HorizontalDivider()

                // Full scrollable list - all options always visible
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 400.dp),
                    content = content
                )

                HorizontalDivider()

                // Close button
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .align(Alignment.End)
                        .padding(end = 12.dp, bottom = 8.dp, top = 4.dp)
                ) {
                    Text("Cancel")
                }
            }
        }
    }
}

//  Slider with − / + step buttons (long-press repeating) 
@Composable
private fun StepSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    step: Float = 0.05f,
    defaultValue: Float = valueRange.start,
    showReset: Boolean = false
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        // − button with long-press repeat
        LongPressStepButton(
            icon = Icons.Default.Remove,
            contentDescription = "Decrease",
            onStep = {
                val next = (value - step).coerceIn(valueRange.start, valueRange.endInclusive)
                val snapped = (next / step).roundToInt() * step
                onValueChange(snapped.coerceIn(valueRange.start, valueRange.endInclusive))
            }
        )

        Slider(
            value = value.coerceIn(valueRange.start, valueRange.endInclusive),
            onValueChange = { raw ->
                val snapped = ((raw / step).roundToInt() * step)
                    .coerceIn(valueRange.start, valueRange.endInclusive)
                onValueChange(snapped)
            },
            valueRange = valueRange,
            modifier = Modifier.weight(1f)
        )

        // + button with long-press repeat
        LongPressStepButton(
            icon = Icons.Default.Add,
            contentDescription = "Increase",
            onStep = {
                val next = (value + step).coerceIn(valueRange.start, valueRange.endInclusive)
                val snapped = (next / step).roundToInt() * step
                onValueChange(snapped.coerceIn(valueRange.start, valueRange.endInclusive))
            }
        )
    }
}

//  Small ± icon button that repeats while held (pointerInput) 
@Composable
private fun LongPressStepButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    onStep: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    Box(
        modifier = Modifier
            .size(36.dp)
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
            modifier = Modifier.size(18.dp),
            tint = if (isPressed) MaterialTheme.colorScheme.primary
                   else MaterialTheme.colorScheme.onSecondaryContainer
        )
    }
}

// Reusable custom Compose extension for auto-repeating clickable pointerInput
fun Modifier.repeatingClickable(
    initialDelayMillis: Long = 500,
    delayMillis: Long = 100,
    interactionSource: MutableInteractionSource? = null,
    onClick: () -> Unit
): Modifier = composed {
    val scope = rememberCoroutineScope()
    val localInteractionSource = interactionSource ?: remember { MutableInteractionSource() }

    this.pointerInput(onClick, initialDelayMillis, delayMillis) {
        coroutineScope {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                var pressed = true
                
                val press = PressInteraction.Press(down.position)
                scope.launch {
                    localInteractionSource.emit(press)
                }
                
                val job = launch {
                    onClick()
                    delay(initialDelayMillis)
                    while (pressed) {
                        onClick()
                        delay(delayMillis)
                    }
                }
                
                do {
                    val event = awaitPointerEvent()
                    val anyPressed = event.changes.any { it.pressed }
                    if (!anyPressed) {
                        pressed = false
                    }
                } while (pressed)
                
                job.cancel()
                scope.launch {
                    localInteractionSource.emit(PressInteraction.Release(press))
                }
            }
        }
    }
}

//  Inline slider row used inside the Press & Tap card 
@Composable
private fun SpeedInlineSlider(
    label: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float> = 1.5f..4.0f,
    step: Float = SPEED_STEP,
    defaultValue: Float = 2.0f,
    onValueChange: (Float) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 6.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = String.format(java.util.Locale.US, "%.2fx", value),
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                // Inline reset chip
                Surface(
                    onClick = { onValueChange(defaultValue) },
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.tertiaryContainer,
                    modifier = Modifier.size(22.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Reset",
                            modifier = Modifier.size(12.dp),
                            tint = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(4.dp))

        StepSlider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            step = step,
            defaultValue = defaultValue
        )
    }
}

//  Section label 
@Composable
private fun SpeedSheetSectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary
    )
}
