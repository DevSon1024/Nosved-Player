package com.devson.nvplayer.ui.screen.settings

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.devson.nvplayer.data.repository.DoubleTapAction
import com.devson.nvplayer.data.repository.MultiFingerAction
import com.devson.nvplayer.viewmodel.SettingsViewModel
import kotlin.math.abs
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GestureSettingsScreen(
    onNavigateBack: () -> Unit,
    settingsViewModel: SettingsViewModel = viewModel()
) {
    val playbackSettings by settingsViewModel.playbackSettings.collectAsState()
    val scrollState = rememberScrollState()
    val context = LocalContext.current

    val dirLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        uri?.let {
            try {
                context.contentResolver.takePersistableUriPermission(
                    it,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
            } catch (e: Exception) {
                // Ignore
            }
            settingsViewModel.updateScreenshotLocation(it.toString())
        }
    }

    var showDoubleTapDialog by remember { mutableStateOf(false) }
    var showSeekDurationDialog by remember { mutableStateOf(false) }
    var showSeekButtonDurationDialog by remember { mutableStateOf(false) }
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
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground,
                    navigationIconContentColor = MaterialTheme.colorScheme.onBackground
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
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Spacer(Modifier.height(4.dp))

            // Swipe Gestures Section
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                GestureSectionHeader("Swipe Gestures")

                GestureToggleCardWithSlider(
                    icon = Icons.Default.SwipeLeft,
                    title = "Horizontal Swipe seeking",
                    subtitle = "Swipe left/right to seek through video",
                    checked = playbackSettings.seekGestureEnabled,
                    onCheckedChange = { settingsViewModel.updateSeekGesture(it) },
                    sliderTitle = "Swipe Seek Speed",
                    sliderValue = playbackSettings.seekSpeedSecPerCm.toFloat(),
                    sliderDefaultValue = 10f,
                    sliderValueRange = 2f..400f,
                    sliderValueFormatter = { "${it.toInt()}s/cm" },
                    onSliderValueChange = { settingsViewModel.updateSeekSpeedSecPerCm(it.toInt()) },
                    onSliderReset = { settingsViewModel.updateSeekSpeedSecPerCm(10) }
                )

                GestureToggleCardWithSlider(
                    icon = Icons.AutoMirrored.Filled.VolumeUp,
                    title = "Vertical Swipe Volume",
                    subtitle = "Swipe up/down on right side to adjust volume",
                    checked = playbackSettings.volumeGestureEnabled,
                    onCheckedChange = { settingsViewModel.updateVolumeGesture(it) },
                    sliderTitle = "Volume Sensitivity",
                    sliderValue = playbackSettings.volumeSensitivity,
                    sliderDefaultValue = 0.5f,
                    sliderValueRange = 0.1f..1.0f,
                    onSliderValueChange = { settingsViewModel.updateVolumeSensitivity(it) },
                    onSliderReset = { settingsViewModel.updateVolumeSensitivity(0.5f) }
                )

                GestureToggleCardWithSlider(
                    icon = Icons.Default.LightMode,
                    title = "Vertical Swipe Brightness",
                    subtitle = "Swipe up/down on left side to adjust brightness",
                    checked = playbackSettings.brightnessGestureEnabled,
                    onCheckedChange = { settingsViewModel.updateBrightnessGesture(it) },
                    sliderTitle = "Brightness Sensitivity",
                    sliderValue = playbackSettings.brightnessSensitivity,
                    sliderDefaultValue = 0.5f,
                    sliderValueRange = 0.1f..1.0f,
                    onSliderValueChange = { settingsViewModel.updateBrightnessSensitivity(it) },
                    onSliderReset = { settingsViewModel.updateBrightnessSensitivity(0.5f) }
                )
            }

            // Press & Tap Section
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                GestureSectionHeader("Press & Tap Controls")

                SettingClickableCard(
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

                SettingClickableCard(
                    icon = Icons.Default.Timer,
                    title = "Double Tap Seek Duration",
                    subtitle = "${playbackSettings.doubleTapSeekDuration / 1000} seconds",
                    onClick = { showSeekDurationDialog = true }
                )

                // Press & Hold Acceleration (Double slider card)
                Card(
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
                        .border(
                            BorderStroke(
                                1.dp,
                                if (playbackSettings.longPressEnabled) MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                                else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                            ),
                            RoundedCornerShape(14.dp)
                        ),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (playbackSettings.longPressEnabled) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.12f)
                        else MaterialTheme.colorScheme.surface
                    )
                ) {
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth().clickable { settingsViewModel.updateLongPressEnabled(!playbackSettings.longPressEnabled) }.padding(horizontal = 16.dp, vertical = 14.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                modifier = Modifier.weight(1f).padding(end = 16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                Box(
                                    modifier = Modifier.size(40.dp).clip(CircleShape).background(
                                        if (playbackSettings.longPressEnabled) MaterialTheme.colorScheme.primaryContainer
                                        else MaterialTheme.colorScheme.secondaryContainer
                                    ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(Icons.Default.Speed, contentDescription = null, modifier = Modifier.size(20.dp), tint = if (playbackSettings.longPressEnabled) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSecondaryContainer)
                                }
                                Column {
                                    Text("Press & Hold Playback Acceleration", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text("Tap and hold screen to temporarily accelerate video", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                            Switch(checked = playbackSettings.longPressEnabled, onCheckedChange = { settingsViewModel.updateLongPressEnabled(it) })
                        }
                        if (playbackSettings.longPressEnabled) {
                            HorizontalDivider(
                                modifier = Modifier.padding(horizontal = 16.dp),
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                            )
                            Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                Column {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text("Tap & Hold Speed Override", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                            Text("${String.format("%.1f", playbackSettings.tapAndHoldSpeed)}x", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                            val isAtDefault = abs(playbackSettings.tapAndHoldSpeed - 2.0f) < 0.001f
                                            IconButton(onClick = { settingsViewModel.updateTapAndHoldSpeed(2.0f) }, enabled = !isAtDefault, modifier = Modifier.size(28.dp)) {
                                                Icon(Icons.Default.Refresh, contentDescription = "Reset", modifier = Modifier.size(16.dp), tint = if (isAtDefault) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f) else MaterialTheme.colorScheme.primary)
                                            }
                                        }
                                    }
                                    Slider(
                                        value = playbackSettings.tapAndHoldSpeed,
                                        onValueChange = { settingsViewModel.updateTapAndHoldSpeed(it) },
                                        valueRange = 1.5f..3.0f,
                                        steps = 2,
                                        modifier = Modifier.padding(top = 4.dp)
                                    )
                                }
                                Column {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text("Long Press Default Speed", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                            Text("${String.format("%.1f", playbackSettings.longPressSpeed)}x", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                            val isAtDefault = abs(playbackSettings.longPressSpeed - 2.0f) < 0.001f
                                            IconButton(onClick = { settingsViewModel.updateLongPressSpeed(2.0f) }, enabled = !isAtDefault, modifier = Modifier.size(28.dp)) {
                                                Icon(Icons.Default.Refresh, contentDescription = "Reset", modifier = Modifier.size(16.dp), tint = if (isAtDefault) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f) else MaterialTheme.colorScheme.primary)
                                            }
                                        }
                                    }
                                    Slider(
                                        value = playbackSettings.longPressSpeed,
                                        onValueChange = { settingsViewModel.updateLongPressSpeed(it) },
                                        valueRange = 1.5f..3.0f,
                                        steps = 2,
                                        modifier = Modifier.padding(top = 4.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                SettingToggleCard(
                    icon = Icons.Default.Forward10,
                    title = "Show 10s Seek Buttons",
                    subtitle = "Display quick rewind/forward buttons in controls overlay",
                    checked = playbackSettings.showSeekButtons,
                    onCheckedChange = { settingsViewModel.updateShowSeekButtons(it) }
                )

                if (playbackSettings.showSeekButtons) {
                    SettingClickableCard(
                        icon = Icons.Default.Timer,
                        title = "Seek Button Duration",
                        subtitle = "${playbackSettings.seekDurationSeconds} seconds",
                        onClick = { showSeekButtonDurationDialog = true }
                    )
                }
            }

            // Multi-finger Actions
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                GestureSectionHeader("Multi-finger Gestures")

                SettingClickableCard(
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

                SettingClickableCard(
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

                if (playbackSettings.twoFingerAction == MultiFingerAction.SCREENSHOT ||
                    playbackSettings.threeFingerAction == MultiFingerAction.SCREENSHOT) {
                    SettingClickableCard(
                        icon = Icons.Default.Folder,
                        title = "Screenshot Save Location",
                        subtitle = getDisplayPath(playbackSettings.screenshotLocation),
                        onClick = { dirLauncher.launch(null) }
                    )
                }
            }

            Spacer(Modifier.height(8.dp))
        }
    }

    // Dialogs
    if (showDoubleTapDialog) {
        AlertDialog(
            onDismissRequest = { showDoubleTapDialog = false },
            title = { Text("Double Tap Action") },
            text = {
                Column(Modifier.selectableGroup()) {
                    listOf(
                        DoubleTapAction.BOTH to "Seek left/right",
                        DoubleTapAction.PLAY_PAUSE to "Play / Pause",
                        DoubleTapAction.FAST_FORWARD to "Fast Forward Only",
                        DoubleTapAction.REWIND to "Rewind Only",
                        DoubleTapAction.NONE to "Disable double-tap"
                    ).forEach { pair ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp)
                                .selectable(
                                    selected = (playbackSettings.doubleTapAction == pair.first),
                                    onClick = {
                                        settingsViewModel.updateDoubleTapAction(pair.first)
                                        showDoubleTapDialog = false
                                    },
                                    role = Role.RadioButton
                                )
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = (playbackSettings.doubleTapAction == pair.first),
                                onClick = null
                            )
                            Spacer(Modifier.width(16.dp))
                            Text(
                                text = pair.second,
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
        AlertDialog(
            onDismissRequest = { showSeekDurationDialog = false },
            title = { Text("Double Tap Seek Duration") },
            text = {
                Column(Modifier.selectableGroup()) {
                    listOf(5000L, 10000L, 15000L, 20000L, 30000L).forEach { durationMs ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp)
                                .selectable(
                                    selected = (playbackSettings.doubleTapSeekDuration == durationMs),
                                    onClick = {
                                        settingsViewModel.updateDoubleTapSeekDuration(durationMs)
                                        showSeekDurationDialog = false
                                    },
                                    role = Role.RadioButton
                                )
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = (playbackSettings.doubleTapSeekDuration == durationMs),
                                onClick = null
                            )
                            Spacer(Modifier.width(16.dp))
                            Text(
                                text = "${durationMs / 1000} seconds",
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

    if (showSeekButtonDurationDialog) {
        AlertDialog(
            onDismissRequest = { showSeekButtonDurationDialog = false },
            title = { Text("Seek Button Duration") },
            text = {
                Column(Modifier.selectableGroup()) {
                    listOf(5, 10, 15, 30, 60, 80).forEach { sec ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp)
                                .selectable(
                                    selected = (playbackSettings.seekDurationSeconds == sec),
                                    onClick = {
                                        settingsViewModel.updateSeekDurationSeconds(sec)
                                        showSeekButtonDurationDialog = false
                                    },
                                    role = Role.RadioButton
                                )
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = (playbackSettings.seekDurationSeconds == sec),
                                onClick = null
                            )
                            Spacer(Modifier.width(16.dp))
                            Text(
                                text = "$sec seconds",
                                style = MaterialTheme.typography.bodyLarge
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showSeekButtonDurationDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showTwoFingerActionDialog) {
        AlertDialog(
            onDismissRequest = { showTwoFingerActionDialog = false },
            title = { Text("Two-Finger Tap Action") },
            text = {
                Column(Modifier.selectableGroup()) {
                    listOf(
                        MultiFingerAction.PLAY_PAUSE to "Play / Pause",
                        MultiFingerAction.FAST_PLAY to "Fast Play (2x)",
                        MultiFingerAction.MUTE to "Mute / Unmute",
                        MultiFingerAction.SCREENSHOT to "Take Screenshot",
                        MultiFingerAction.PINCH_ZOOM to "Pinch to Zoom",
                        MultiFingerAction.NONE to "Disable gesture"
                    ).forEach { pair ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp)
                                .selectable(
                                    selected = (playbackSettings.twoFingerAction == pair.first),
                                    onClick = {
                                        settingsViewModel.updateTwoFingerAction(pair.first)
                                        showTwoFingerActionDialog = false
                                    },
                                    role = Role.RadioButton
                                )
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = (playbackSettings.twoFingerAction == pair.first),
                                onClick = null
                            )
                            Spacer(Modifier.width(16.dp))
                            Text(
                                text = pair.second,
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
            title = { Text("Three-Finger Tap Action") },
            text = {
                Column(Modifier.selectableGroup()) {
                    listOf(
                        MultiFingerAction.PLAY_PAUSE to "Play / Pause",
                        MultiFingerAction.FAST_PLAY to "Fast Play (2x)",
                        MultiFingerAction.MUTE to "Mute / Unmute",
                        MultiFingerAction.SCREENSHOT to "Take Screenshot",
                        MultiFingerAction.PINCH_ZOOM to "Pinch to Zoom",
                        MultiFingerAction.NONE to "Disable gesture"
                    ).forEach { pair ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp)
                                .selectable(
                                    selected = (playbackSettings.threeFingerAction == pair.first),
                                    onClick = {
                                        settingsViewModel.updateThreeFingerAction(pair.first)
                                        showThreeFingerActionDialog = false
                                    },
                                    role = Role.RadioButton
                                )
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = (playbackSettings.threeFingerAction == pair.first),
                                onClick = null
                            )
                            Spacer(Modifier.width(16.dp))
                            Text(
                                text = pair.second,
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
private fun GestureToggleCardWithSlider(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    sliderTitle: String,
    sliderValue: Float,
    sliderDefaultValue: Float = 0.5f,
    sliderValueRange: ClosedFloatingPointRange<Float> = 0.1f..1.0f,
    sliderSteps: Int = 0,
    sliderValueFormatter: (Float) -> String = { "${(it * 100).roundToInt()}%" },
    onSliderValueChange: (Float) -> Unit,
    onSliderReset: (() -> Unit)? = null
) {
    Card(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
            .border(
                BorderStroke(
                    1.dp,
                    if (checked) MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                    else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                ),
                RoundedCornerShape(14.dp)
            ),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (checked) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.12f)
            else MaterialTheme.colorScheme.surface
        )
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth().clickable { onCheckedChange(!checked) }.padding(horizontal = 16.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f).padding(end = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Box(
                        modifier = Modifier.size(40.dp).clip(CircleShape).background(
                            if (checked) MaterialTheme.colorScheme.primaryContainer
                            else MaterialTheme.colorScheme.secondaryContainer
                        ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp), tint = if (checked) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSecondaryContainer)
                    }
                    Column {
                        Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Switch(checked = checked, onCheckedChange = onCheckedChange)
            }
            if (checked) {
                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                )
                Column(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                      ) {
                        Text(sliderTitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(sliderValueFormatter(sliderValue), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                            if (onSliderReset != null) {
                                val isAtDefault = abs(sliderValue - sliderDefaultValue) < 0.001f
                                IconButton(onClick = onSliderReset, enabled = !isAtDefault, modifier = Modifier.size(28.dp)) {
                                    Icon(Icons.Default.Refresh, contentDescription = "Reset", modifier = Modifier.size(16.dp), tint = if (isAtDefault) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f) else MaterialTheme.colorScheme.primary)
                                }
                            }
                        }
                    }
                    Slider(
                        value = sliderValue,
                        onValueChange = onSliderValueChange,
                        valueRange = sliderValueRange,
                        steps = sliderSteps,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun SettingToggleCard(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .then(if (enabled) Modifier.clickable { onCheckedChange(!checked) } else Modifier)
            .alpha(if (enabled) 1f else 0.38f)
            .border(
                BorderStroke(
                    1.dp,
                    if (checked && enabled) MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                    else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                ),
                RoundedCornerShape(14.dp)
            ),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (checked && enabled) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.12f)
            else MaterialTheme.colorScheme.surface
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier.weight(1f).padding(end = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                if (icon != null) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(
                                if (checked && enabled) MaterialTheme.colorScheme.primaryContainer
                                else MaterialTheme.colorScheme.secondaryContainer
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                            tint = if (checked && enabled) MaterialTheme.colorScheme.onPrimaryContainer
                            else MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                }
                Column {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                enabled = enabled
            )
        }
    }
}

@Composable
private fun SettingClickableCard(
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .then(if (enabled) Modifier.clickable { onClick() } else Modifier)
            .alpha(if (enabled) 1f else 0.38f)
            .border(
                BorderStroke(
                    1.dp,
                    MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                ),
                RoundedCornerShape(14.dp)
            ),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier.weight(1f).padding(end = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                if (icon != null) {
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
                }
                Column {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

private fun getDisplayPath(location: String): String {
    if (location.startsWith("content://")) {
        return try {
            val decoded = Uri.decode(location)
            val lastColon = decoded.lastIndexOf(':')
            if (lastColon != -1 && lastColon < decoded.length - 1) {
                decoded.substring(lastColon + 1)
            } else {
                Uri.parse(location).lastPathSegment ?: location
            }
        } catch (e: Exception) {
            location
        }
    }
    return location
}
