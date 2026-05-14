package com.devson.nvplayer.ui.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.devson.nvplayer.repository.DoubleTapAction
import com.devson.nvplayer.repository.MultiFingerAction
import com.devson.nvplayer.viewmodel.SettingsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GestureSettingsScreen(
    onBack: () -> Unit,
    settingsViewModel: SettingsViewModel
) {
    val settings by settingsViewModel.playbackSettings.collectAsState(initial = null)

    // Dialog States
    var showSeekSensitivityDialog by remember { mutableStateOf(false) }
    var showBrightnessSensitivityDialog by remember { mutableStateOf(false) }
    var showVolumeSensitivityDialog by remember { mutableStateOf(false) }
    var showDoubleTapDialog by remember { mutableStateOf(false) }
    var showTwoFingerDialog by remember { mutableStateOf(false) }
    var showThreeFingerDialog by remember { mutableStateOf(false) }
    var showLongPressSpeedDialog by remember { mutableStateOf(false) }

    settings?.let { prefs ->
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
                        IconButton(onClick = onBack) {
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
                    .verticalScroll(rememberScrollState())
                    .padding(
                        top = padding.calculateTopPadding(),
                        bottom = padding.calculateBottomPadding() + 16.dp
                    )
                    .padding(horizontal = 16.dp)
            ) {
                Spacer(Modifier.height(4.dp))

                // SWIPE GESTURES
                GestureSectionLabel("Swipe Gestures")
                GestureCard {
                    GestureToggleRow(
                        icon = Icons.Default.FastForward,
                        title = "Seek Gesture",
                        subtitle = "Swipe horizontally to seek",
                        checked = prefs.seekGestureEnabled,
                        onCheckedChange = { settingsViewModel.updateSeekGesture(it) },
                        onCustomizeClick = { showSeekSensitivityDialog = true }
                    )
                    GestureDivider()
                    GestureToggleRow(
                        icon = Icons.Default.BrightnessMedium,
                        title = "Brightness Gesture",
                        subtitle = "Swipe vertically on left side",
                        checked = prefs.brightnessGestureEnabled,
                        onCheckedChange = { settingsViewModel.updateBrightnessGesture(it) },
                        onCustomizeClick = { showBrightnessSensitivityDialog = true }
                    )
                    GestureDivider()
                    GestureToggleRow(
                        icon = Icons.AutoMirrored.Filled.VolumeUp,
                        title = "Volume Gesture",
                        subtitle = "Swipe vertically on right side",
                        checked = prefs.volumeGestureEnabled,
                        onCheckedChange = { settingsViewModel.updateVolumeGesture(it) },
                        onCustomizeClick = { showVolumeSensitivityDialog = true }
                    )
                }

                Spacer(Modifier.height(24.dp))

                // TAP GESTURES
                GestureSectionLabel("Tap & Press Gestures")
                GestureCard {
                    GestureActionRow(
                        icon = Icons.Default.TouchApp,
                        title = "Double Tap",
                        subtitle = prefs.doubleTapAction.displayName,
                        onClick = { showDoubleTapDialog = true }
                    )
                    GestureDivider()
                    GestureActionRow(
                        icon = Icons.Default.PanTool,
                        title = "Two-Finger Click",
                        subtitle = prefs.twoFingerAction.displayName,
                        onClick = { showTwoFingerDialog = true }
                    )
                    GestureDivider()
                    GestureActionRow(
                        icon = Icons.Default.ViewCarousel,
                        title = "Three-Finger Click",
                        subtitle = prefs.threeFingerAction.displayName,
                        onClick = { showThreeFingerDialog = true }
                    )
                }

                Spacer(Modifier.height(24.dp))

                // LONG PRESS
                GestureSectionLabel("Long Press Gestures")
                GestureCard {
                    GestureToggleRow(
                        icon = Icons.Default.Speed,
                        title = "Long Press to Fast Play",
                        subtitle = "Plays at ${prefs.longPressSpeed}x while Long Pressing",
                        checked = prefs.longPressEnabled,
                        onCheckedChange = { settingsViewModel.updateLongPressEnabled(it) },
                        onCustomizeClick = { showLongPressSpeedDialog = true }
                    )
                }
            }
        }

        // --- Dialogs ---

        if (showSeekSensitivityDialog) {
            SensitivityDialog(
                title = "Seek Sensitivity",
                initialValue = prefs.seekSensitivity,
                onDismiss = { showSeekSensitivityDialog = false },
                onConfirm = { settingsViewModel.updateSeekSensitivity(it); showSeekSensitivityDialog = false }
            )
        }
        if (showBrightnessSensitivityDialog) {
            SensitivityDialog(
                title = "Brightness Sensitivity",
                initialValue = prefs.brightnessSensitivity,
                onDismiss = { showBrightnessSensitivityDialog = false },
                onConfirm = { settingsViewModel.updateBrightnessSensitivity(it); showBrightnessSensitivityDialog = false }
            )
        }
        if (showVolumeSensitivityDialog) {
            SensitivityDialog(
                title = "Volume Sensitivity",
                initialValue = prefs.volumeSensitivity,
                onDismiss = { showVolumeSensitivityDialog = false },
                onConfirm = { settingsViewModel.updateVolumeSensitivity(it); showVolumeSensitivityDialog = false }
            )
        }

        if (showLongPressSpeedDialog) {
            SensitivityDialog(
                title = "Long Press Play Speed",
                initialValue = prefs.longPressSpeed,
                valueRange = 1.0f..4.0f,
                steps = 11,
                labelFormatter = { "%.1fx".format(it) },
                onDismiss = { showLongPressSpeedDialog = false },
                onConfirm = { settingsViewModel.updateLongPressSpeed(it); showLongPressSpeedDialog = false }
            )
        }

        if (showDoubleTapDialog) {
            RadioSelectionDialog(
                title = "Double Tap Action",
                options = DoubleTapAction.values().toList(),
                selectedOption = prefs.doubleTapAction,
                labelMapper = { it.displayName },
                onDismiss = { showDoubleTapDialog = false },
                onConfirm = { settingsViewModel.updateDoubleTapAction(it); showDoubleTapDialog = false }
            )
        }

        if (showTwoFingerDialog) {
            RadioSelectionDialog(
                title = "Two-Finger Action",
                options = MultiFingerAction.values().toList(),
                selectedOption = prefs.twoFingerAction,
                labelMapper = { it.displayName },
                onDismiss = { showTwoFingerDialog = false },
                onConfirm = { settingsViewModel.updateTwoFingerAction(it); showTwoFingerDialog = false }
            )
        }

        if (showThreeFingerDialog) {
            RadioSelectionDialog(
                title = "Three-Finger Action",
                options = MultiFingerAction.values().toList(),
                selectedOption = prefs.threeFingerAction,
                labelMapper = { it.displayName },
                onDismiss = { showThreeFingerDialog = false },
                onConfirm = { settingsViewModel.updateThreeFingerAction(it); showThreeFingerDialog = false }
            )
        }
    }
}

// --- Reusable UI Components specific to this screen ---

@Composable
private fun GestureSectionLabel(label: String) {
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
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 2.dp
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
private fun GestureToggleRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    onCustomizeClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(start = 16.dp, top = 8.dp, bottom = 8.dp, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
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
        Spacer(Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // Settings/Tune button for sensitivity
        IconButton(onClick = onCustomizeClick, enabled = checked) {
            Icon(
                Icons.Default.Tune,
                contentDescription = "Sensitivity",
                tint = if (checked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
            )
        }

        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange
        )
    }
}

@Composable
private fun GestureActionRow(
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
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
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
        Spacer(Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

// --- Dialogs ---

@Composable
fun SensitivityDialog(
    title: String,
    initialValue: Float,
    valueRange: ClosedFloatingPointRange<Float> = 0.0f..1.0f,
    steps: Int = 10,
    labelFormatter: (Float) -> String = {
        when {
            it <= 0.2f -> "Hard to swipe"
            it >= 0.8f -> "Smooth"
            else -> "Normal"
        }
    },
    onDismiss: () -> Unit,
    onConfirm: (Float) -> Unit
) {
    var sliderValue by remember { mutableFloatStateOf(initialValue) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                Text("Adjust sensitivity for gesture:")
                Spacer(modifier = Modifier.height(16.dp))
                Slider(
                    value = sliderValue,
                    onValueChange = { sliderValue = it },
                    valueRange = valueRange,
                    steps = steps
                )
                Text(
                    text = labelFormatter(sliderValue),
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(sliderValue) }) {
                Text("Save")
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
fun <T> RadioSelectionDialog(
    title: String,
    options: List<T>,
    selectedOption: T,
    labelMapper: (T) -> String,
    onDismiss: () -> Unit,
    onConfirm: (T) -> Unit
) {
    var currentSelection by remember { mutableStateOf(selectedOption) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                options.forEach { option ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { currentSelection = option }
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = (option == currentSelection),
                            onClick = { currentSelection = option }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(text = labelMapper(option))
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(currentSelection) }) {
                Text("Select")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}