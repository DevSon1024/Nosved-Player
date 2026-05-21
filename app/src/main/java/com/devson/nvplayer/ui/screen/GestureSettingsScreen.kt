package com.devson.nvplayer.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.devson.nvplayer.repository.DoubleTapAction
import com.devson.nvplayer.repository.MultiFingerAction
import com.devson.nvplayer.viewmodel.SettingsViewModel
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GestureSettingsScreen(
    onNavigateBack: () -> Unit,
    settingsViewModel: SettingsViewModel = viewModel()
) {
    val playbackSettings by settingsViewModel.playbackSettings.collectAsState()
    val scrollState = rememberScrollState()

    var showDoubleTapDialog by remember { mutableStateOf(false) }
    var showSeekDurationDialog by remember { mutableStateOf(false) }
    var showTwoFingerActionDialog by remember { mutableStateOf(false) }
    var showThreeFingerActionDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Gestures & Taps",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = onNavigateBack,
                        modifier = Modifier.minimumInteractiveComponentSize()
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(padding)
                .padding(horizontal = 16.dp)
        ) {
            Spacer(Modifier.height(8.dp))

            // Swipe Gestures Section
            GestureSectionHeader("Swipe Gestures")
            GestureCard {
                GestureToggleRow(
                    icon = Icons.Default.SwipeLeft,
                    title = "Horizontal Swipe seeking",
                    subtitle = "Swipe left/right to seek through video",
                    checked = playbackSettings.seekGestureEnabled,
                    onCheckedChange = { settingsViewModel.updateSeekGesture(it) }
                )
                if (playbackSettings.seekGestureEnabled) {
                    GestureSliderRow(
                        title = "Seek Sensitivity",
                        value = playbackSettings.seekSensitivity,
                        defaultValue = 0.5f,
                        onValueChange = { settingsViewModel.updateSeekSensitivity(it) },
                        onReset = { settingsViewModel.updateSeekSensitivity(0.5f) }
                    )
                }

                GestureDivider()

                GestureToggleRow(
                    icon = Icons.AutoMirrored.Filled.VolumeUp,
                    title = "Vertical Swipe Volume",
                    subtitle = "Swipe up/down on right side to adjust volume",
                    checked = playbackSettings.volumeGestureEnabled,
                    onCheckedChange = { settingsViewModel.updateVolumeGesture(it) }
                )
                if (playbackSettings.volumeGestureEnabled) {
                    GestureSliderRow(
                        title = "Volume Sensitivity",
                        value = playbackSettings.volumeSensitivity,
                        defaultValue = 0.5f,
                        onValueChange = { settingsViewModel.updateVolumeSensitivity(it) },
                        onReset = { settingsViewModel.updateVolumeSensitivity(0.5f) }
                    )
                }

                GestureDivider()

                GestureToggleRow(
                    icon = Icons.Default.LightMode,
                    title = "Vertical Swipe Brightness",
                    subtitle = "Swipe up/down on left side to adjust brightness",
                    checked = playbackSettings.brightnessGestureEnabled,
                    onCheckedChange = { settingsViewModel.updateBrightnessGesture(it) }
                )
                if (playbackSettings.brightnessGestureEnabled) {
                    GestureSliderRow(
                        title = "Brightness Sensitivity",
                        value = playbackSettings.brightnessSensitivity,
                        defaultValue = 0.5f,
                        onValueChange = { settingsViewModel.updateBrightnessSensitivity(it) },
                        onReset = { settingsViewModel.updateBrightnessSensitivity(0.5f) }
                    )
                }
            }

            Spacer(Modifier.height(20.dp))

            // Tap & Hold Section
            GestureSectionHeader("Press & Tap Controls")
            GestureCard {
                GestureRow(
                    icon = Icons.Default.TouchApp,
                    title = "Double Tap Action",
                    subtitle = when (playbackSettings.doubleTapAction) {
                        DoubleTapAction.BOTH -> "Seek on double-tap"
                        DoubleTapAction.PLAY_PAUSE -> "Play / Pause"
                        DoubleTapAction.FAST_FORWARD -> "Fast Forward Only"
                        DoubleTapAction.REWIND -> "Rewind Only"
                        DoubleTapAction.NONE -> "No Action"
                    },
                    onClick = { showDoubleTapDialog = true }
                )

                GestureDivider()

                GestureRow(
                    icon = Icons.Default.Timer,
                    title = "Double Tap Seek Duration",
                    subtitle = "${playbackSettings.doubleTapSeekDuration / 1000} seconds",
                    onClick = { showSeekDurationDialog = true }
                )

                GestureDivider()

                GestureToggleRow(
                    icon = Icons.Default.Speed,
                    title = "Press & Hold Playback Acceleration",
                    subtitle = "Tap and hold screen to temporarily accelerate video",
                    checked = playbackSettings.longPressEnabled,
                    onCheckedChange = { settingsViewModel.updateLongPressEnabled(it) }
                )
                if (playbackSettings.longPressEnabled) {
                    GestureSliderRow(
                        title = "Tap & Hold Speed Override",
                        value = playbackSettings.tapAndHoldSpeed,
                        defaultValue = 2.0f,
                        valueRange = 1.5f..3.0f,
                        steps = 2,
                        valueFormatter = { "${String.format("%.1f", it)}x" },
                        onValueChange = { settingsViewModel.updateTapAndHoldSpeed(it) },
                        onReset = { settingsViewModel.updateTapAndHoldSpeed(2.0f) }
                    )
                    GestureSliderRow(
                        title = "Long Press Default Speed",
                        value = playbackSettings.longPressSpeed,
                        defaultValue = 2.0f,
                        valueRange = 1.5f..3.0f,
                        steps = 2,
                        valueFormatter = { "${String.format("%.1f", it)}x" },
                        onValueChange = { settingsViewModel.updateLongPressSpeed(it) },
                        onReset = { settingsViewModel.updateLongPressSpeed(2.0f) }
                    )
                }
            }

            Spacer(Modifier.height(20.dp))

            // Multi-finger Actions
            GestureSectionHeader("Multi-finger Gestures")
            GestureCard {
                GestureRow(
                    icon = Icons.Default.Gesture,
                    title = "Two-Finger Action",
                    subtitle = when (playbackSettings.twoFingerAction) {
                        MultiFingerAction.PLAY_PAUSE -> "Play / Pause"
                        MultiFingerAction.FAST_PLAY -> "Fast Play (2x)"
                        MultiFingerAction.MUTE -> "Mute"
                        MultiFingerAction.NONE -> "No Action"
                        MultiFingerAction.SCREENSHOT -> "Take Screenshot"
                        MultiFingerAction.PINCH_ZOOM -> "Pinch to Zoom"
                    },
                    onClick = { showTwoFingerActionDialog = true }
                )

                GestureDivider()

                GestureRow(
                    icon = Icons.Default.SettingsAccessibility,
                    title = "Three-Finger Action",
                    subtitle = when (playbackSettings.threeFingerAction) {
                        MultiFingerAction.PLAY_PAUSE -> "Play / Pause"
                        MultiFingerAction.FAST_PLAY -> "Fast Play (2x)"
                        MultiFingerAction.MUTE -> "Mute"
                        MultiFingerAction.NONE -> "No Action"
                        MultiFingerAction.SCREENSHOT -> "Take Screenshot"
                        MultiFingerAction.PINCH_ZOOM -> "Pinch to Zoom"
                    },
                    onClick = { showThreeFingerActionDialog = true }
                )
            }

            Spacer(Modifier.height(24.dp))
        }
    }

    // Dialogs
    if (showDoubleTapDialog) {
        AlertDialog(
            onDismissRequest = { showDoubleTapDialog = false },
            title = { Text("Double Tap Action") },
            text = {
                Column(Modifier.selectableGroup()) {
                    DoubleTapAction.values().forEach { action ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp)
                                .selectable(
                                    selected = (playbackSettings.doubleTapAction == action),
                                    onClick = {
                                        settingsViewModel.updateDoubleTapAction(action)
                                        showDoubleTapDialog = false
                                    },
                                    role = Role.RadioButton
                                )
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = (playbackSettings.doubleTapAction == action),
                                onClick = null
                            )
                            Spacer(Modifier.width(16.dp))
                            Text(
                                text = when (action) {
                                    DoubleTapAction.BOTH -> "Double Tap Left/Right to Seek"
                                    DoubleTapAction.PLAY_PAUSE -> "Play / Pause"
                                    DoubleTapAction.FAST_FORWARD -> "Fast Forward Only"
                                    DoubleTapAction.REWIND -> "Rewind Only"
                                    DoubleTapAction.NONE -> "Disable Double Tap"
                                },
                                style = MaterialTheme.typography.bodyLarge
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showDoubleTapDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showSeekDurationDialog) {
        val durations = listOf(5000L, 10000L, 15000L, 30000L, 60000L)
        AlertDialog(
            onDismissRequest = { showSeekDurationDialog = false },
            title = { Text("Seek Duration") },
            text = {
                Column(Modifier.selectableGroup()) {
                    durations.forEach { duration ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp)
                                .selectable(
                                    selected = (playbackSettings.doubleTapSeekDuration == duration),
                                    onClick = {
                                        settingsViewModel.updateDoubleTapSeekDuration(duration)
                                        showSeekDurationDialog = false
                                    },
                                    role = Role.RadioButton
                                )
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = (playbackSettings.doubleTapSeekDuration == duration),
                                onClick = null
                            )
                            Spacer(Modifier.width(16.dp))
                            Text(
                                text = "${duration / 1000} seconds",
                                style = MaterialTheme.typography.bodyLarge
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showSeekDurationDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showTwoFingerActionDialog) {
        AlertDialog(
            onDismissRequest = { showTwoFingerActionDialog = false },
            title = { Text("Two-Finger Action") },
            text = {
                Column(Modifier.selectableGroup()) {
                    MultiFingerAction.values().forEach { action ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp)
                                .selectable(
                                    selected = (playbackSettings.twoFingerAction == action),
                                    onClick = {
                                        settingsViewModel.updateTwoFingerAction(action)
                                        showTwoFingerActionDialog = false
                                    },
                                    role = Role.RadioButton
                                )
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = (playbackSettings.twoFingerAction == action),
                                onClick = null
                            )
                            Spacer(Modifier.width(16.dp))
                            Text(
                                text = when (action) {
                                    MultiFingerAction.PLAY_PAUSE -> "Play / Pause"
                                    MultiFingerAction.FAST_PLAY -> "Fast Play (2x)"
                                    MultiFingerAction.MUTE -> "Mute Audio"
                                    MultiFingerAction.NONE -> "No Action"
                                    MultiFingerAction.SCREENSHOT -> "Take Screenshot"
                                    MultiFingerAction.PINCH_ZOOM -> "Pinch to Zoom (two-finger)"
                                },
                                style = MaterialTheme.typography.bodyLarge
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showTwoFingerActionDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showThreeFingerActionDialog) {
        AlertDialog(
            onDismissRequest = { showThreeFingerActionDialog = false },
            title = { Text("Three-Finger Action") },
            text = {
                Column(Modifier.selectableGroup()) {
                    MultiFingerAction.values().forEach { action ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp)
                                .selectable(
                                    selected = (playbackSettings.threeFingerAction == action),
                                    onClick = {
                                        settingsViewModel.updateThreeFingerAction(action)
                                        showThreeFingerActionDialog = false
                                    },
                                    role = Role.RadioButton
                                )
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = (playbackSettings.threeFingerAction == action),
                                onClick = null
                            )
                            Spacer(Modifier.width(16.dp))
                            Text(
                                text = when (action) {
                                    MultiFingerAction.PLAY_PAUSE -> "Play / Pause"
                                    MultiFingerAction.FAST_PLAY -> "Fast Play (2x)"
                                    MultiFingerAction.MUTE -> "Mute Audio"
                                    MultiFingerAction.NONE -> "No Action"
                                    MultiFingerAction.SCREENSHOT -> "Take Screenshot"
                                    MultiFingerAction.PINCH_ZOOM -> "Pinch to Zoom"
                                },
                                style = MaterialTheme.typography.bodyLarge
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showThreeFingerActionDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun GestureSectionHeader(label: String) {
    Text(
        text = label,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(start = 4.dp, bottom = 6.dp)
    )
}

@Composable
private fun GestureCard(content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        )
    ) {
        Column(content = content)
    }
}

@Composable
private fun GestureDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(start = 56.dp),
        color = MaterialTheme.colorScheme.outlineVariant
    )
}

@Composable
private fun GestureRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.secondaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.onSecondaryContainer
            )
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Icon(
            imageVector = Icons.Default.ChevronRight,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun GestureToggleRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.secondaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.onSecondaryContainer
            )
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = Modifier.minimumInteractiveComponentSize()
        )
    }
}

@Composable
private fun GestureSliderRow(
    title: String,
    value: Float,
    defaultValue: Float = 0.5f,
    valueRange: ClosedFloatingPointRange<Float> = 0.1f..1.0f,
    steps: Int = 0,
    valueFormatter: (Float) -> String = { "${(it * 100).roundToInt()}%" },
    onValueChange: (Float) -> Unit,
    onReset: (() -> Unit)? = null
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 56.dp, end = 16.dp, bottom = 12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f)
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = valueFormatter(value),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                if (onReset != null) {
                    val isAtDefault = kotlin.math.abs(value - defaultValue) < 0.001f
                    IconButton(
                        onClick = onReset,
                        enabled = !isAtDefault,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Reset to default",
                            modifier = Modifier.size(16.dp),
                            tint = if (isAtDefault)
                                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                            else
                                MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            steps = steps,
            modifier = Modifier.padding(top = 4.dp)
        )
    }
}
