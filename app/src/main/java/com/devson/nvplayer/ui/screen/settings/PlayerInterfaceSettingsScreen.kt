package com.devson.nvplayer.ui.screen.settings

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
import androidx.compose.material.icons.automirrored.filled.RotateRight
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.rounded.QueuePlayNext
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.ui.res.stringResource
import com.devson.nvplayer.R
import com.devson.nvplayer.data.repository.AmbientBlurStyle
import com.devson.nvplayer.data.repository.FullScreenMode
import com.devson.nvplayer.data.repository.OrientationMode
import com.devson.nvplayer.data.repository.SoftButtonMode
import com.devson.nvplayer.player.model.AspectMode
import com.devson.nvplayer.viewmodel.SettingsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerInterfaceSettingsScreen(
    onNavigateBack: () -> Unit,
    onNavigateToControlEditor: () -> Unit = {},
    settingsViewModel: SettingsViewModel = viewModel()
) {
    val playbackSettings by settingsViewModel.playbackSettings.collectAsState()
    val scrollState = rememberScrollState()

    var showOrientationDialog by remember { mutableStateOf(false) }
    var showScalingDialog by remember { mutableStateOf(false) }
    var showSoftButtonDialog by remember { mutableStateOf(false) }
    var showPlaybackControlPositionDialog by remember { mutableStateOf(false) }
    var showIconSizeDialog by remember { mutableStateOf(false) }
    var showSeekBarStyleDialog by remember { mutableStateOf(false) }

    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeTopAppBar(
                title = {
                    Text(
                        "Player Interface",
                        fontWeight = FontWeight.Bold
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
                colors = TopAppBarDefaults.largeTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    scrolledContainerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground,
                    navigationIconContentColor = MaterialTheme.colorScheme.onBackground
                ),
                scrollBehavior = scrollBehavior
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(paddingValues)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Spacer(Modifier.height(4.dp))

            // Control Region Layout Customization
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                InterfaceSectionHeader("Control Regions Layout Customization")
                SettingClickableCard(
                    icon = Icons.Default.Dashboard,
                    title = "Custom Controls Layout",
                    subtitle = "Customize and reorder control buttons using an interactive player preview",
                    onClick = onNavigateToControlEditor
                )
            }

            // Display & Orientation Section
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                InterfaceSectionHeader("Layout & Orientation")
                SettingClickableCard(
                    icon = Icons.Default.ScreenRotation,
                    title = "Screen Orientation",
                    subtitle = when (playbackSettings.orientationMode) {
                        OrientationMode.SYSTEM_DEFAULT -> "Follow System Setting"
                        OrientationMode.LANDSCAPE -> "Force Landscape Mode"
                        OrientationMode.PORTRAIT -> "Force Portrait Mode"
                        OrientationMode.AUTO -> "Auto-Rotate based on Sensor"
                    },
                    onClick = { showOrientationDialog = true }
                )

                SettingClickableCard(
                    icon = Icons.Default.AspectRatio,
                    title = "Fullscreen Scale Mode",
                    subtitle = when (playbackSettings.aspectMode) {
                        AspectMode.FIT -> "Fit Screen (Letterbox)"
                        AspectMode.STRETCH -> "Stretch to Fill"
                        AspectMode.CROP -> "Crop and Zoom"
                        AspectMode.ORIGINAL -> "100% Original"
                    },
                    onClick = { showScalingDialog = true }
                )

                SettingClickableCard(
                    icon = Icons.Default.Fullscreen,
                    title = "System Navigation Buttons",
                    subtitle = when (playbackSettings.softButtonMode) {
                        SoftButtonMode.AUTO_HIDE -> "Auto-hide with controls"
                        SoftButtonMode.SHOW -> "Always show navigation buttons"
                        SoftButtonMode.HIDE -> "Always hide (Immersive mode)"
                    },
                    onClick = { showSoftButtonDialog = true }
                )

                SettingClickableCard(
                    icon = Icons.Default.PlayCircle,
                    title = "Playback Control Position",
                    subtitle = if (playbackSettings.isBottomLayoutEnabled) "Below Seekbar" else "Center",
                    onClick = { showPlaybackControlPositionDialog = true }
                )

                SettingToggleCard(
                    icon = Icons.Rounded.QueuePlayNext,
                    title = "Enable Show Up Next",
                    subtitle = "Tap the video title during playback to show the queue",
                    checked = playbackSettings.showUpNextQueue,
                    onCheckedChange = { settingsViewModel.updateShowUpNextQueue(it) }
                )
            }

            // Playback Controls customization
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                InterfaceSectionHeader("Player Controls Customization")
                SettingClickableCard(
                    icon = Icons.Default.PhotoSizeSelectLarge,
                    title = "Controls Icon Size",
                    subtitle = playbackSettings.controlIconSize.replaceFirstChar { it.uppercase() },
                    onClick = { showIconSizeDialog = true }
                )

                SettingClickableCard(
                    icon = Icons.Default.Waves,
                    title = "Seekbar Style",
                    subtitle = when (playbackSettings.seekBarStyle) {
                        "wavy" -> "Wavy"
                        "thick" -> "Thick"
                        else -> "Standard"
                    },
                    onClick = { showSeekBarStyleDialog = true }
                )

                SettingToggleCard(
                    icon = Icons.Default.SkipNext,
                    title = "Auto-play Next Video",
                    subtitle = "Automatically load and play next video in folder",
                    checked = playbackSettings.autoPlayEnabled,
                    onCheckedChange = { settingsViewModel.updateAutoPlayEnabled(it) }
                )

                SettingToggleCard(
                    icon = Icons.Default.FastForward,
                    title = "Show Seek Buttons",
                    subtitle = "Show fast forward and rewind seek buttons in player controls",
                    checked = playbackSettings.showSeekButtons,
                    onCheckedChange = { settingsViewModel.updateShowSeekButtons(it) }
                )

                SettingToggleCard(
                    icon = Icons.Default.SkipNext,
                    title = "Show Skip Prev/Next Buttons",
                    subtitle = "Show previous/next chapter skip buttons in player controls",
                    checked = playbackSettings.showNextPrevButtons,
                    onCheckedChange = { settingsViewModel.updateShowNextPrevButtons(it) }
                )
            }

            // Player Appearance Section
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                InterfaceSectionHeader(stringResource(R.string.player_appearance))
                SettingToggleCard(
                    icon = Icons.Default.WbIncandescent,
                    title = stringResource(R.string.ambient_mode),
                    subtitle = stringResource(R.string.ambient_mode_warning),
                    checked = playbackSettings.isAmbientModeEnabled,
                    onCheckedChange = { settingsViewModel.updateIsAmbientModeEnabled(it) }
                )

                if (playbackSettings.isAmbientModeEnabled) {
                    var expanded by remember { mutableStateOf(false) }
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                        ),
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { expanded = !expanded },
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Ambient Blur Style",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = playbackSettings.ambientBlurStyle.displayName,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.primary,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                                Icon(
                                    imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                    contentDescription = "Expand Ambient Styles",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            androidx.compose.animation.AnimatedVisibility(visible = expanded) {
                                Column(
                                    modifier = Modifier.padding(top = 12.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    AmbientBlurStyle.values().forEach { style ->
                                        val isSelected = style == playbackSettings.ambientBlurStyle
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clip(RoundedCornerShape(10.dp))
                                                .background(
                                                    if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                                                    else androidx.compose.ui.graphics.Color.Transparent
                                                )
                                                .selectable(
                                                    selected = isSelected,
                                                    onClick = {
                                                        settingsViewModel.updateAmbientBlurStyle(style)
                                                    },
                                                    role = Role.RadioButton
                                                )
                                                .padding(horizontal = 12.dp, vertical = 8.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            RadioButton(
                                                selected = isSelected,
                                                onClick = { settingsViewModel.updateAmbientBlurStyle(style) }
                                            )
                                            Spacer(modifier = Modifier.width(12.dp))
                                            Column {
                                                Text(
                                                    text = style.displayName,
                                                    style = MaterialTheme.typography.bodyLarge,
                                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                                )
                                                Text(
                                                    text = style.description,
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                SettingToggleCard(
                    icon = Icons.Default.HourglassBottom,
                    title = "Show Remaining Time",
                    subtitle = "Display remaining time instead of total duration",
                    checked = playbackSettings.showRemainingTime,
                    onCheckedChange = { settingsViewModel.updateShowRemainingTime(it) }
                )

                SettingToggleCard(
                    icon = Icons.Default.BatteryChargingFull,
                    title = "Show Battery & Clock Overlay",
                    subtitle = "Display battery status and device clock on control bar",
                    checked = playbackSettings.showBatteryClockOverlay,
                    onCheckedChange = { settingsViewModel.updateShowBatteryClockOverlay(it) }
                )

                SettingToggleCard(
                    icon = Icons.Default.Gradient,
                    title = "Controls Backdrop Dimming",
                    subtitle = "Show top and bottom black fade behind player controls",
                    checked = playbackSettings.showControlGradients,
                    onCheckedChange = { settingsViewModel.updateShowControlGradients(it) }
                )
            }

            // Safety & Automation Section
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                InterfaceSectionHeader("Automation Behavior")
                SettingToggleCard(
                    icon = Icons.Default.LightMode,
                    title = "Save Brightness Level",
                    subtitle = "Save and restore custom brightness across playback sessions instead of matching device brightness",
                    checked = playbackSettings.saveBrightnessLevel,
                    onCheckedChange = { settingsViewModel.updateSaveBrightnessLevel(it) }
                )

                SettingToggleCard(
                    icon = Icons.Default.PauseCircle,
                    title = "Pause on Obstruction",
                    subtitle = "Pause video playback automatically if screen is covered",
                    checked = playbackSettings.pauseWhenObstructed,
                    onCheckedChange = { settingsViewModel.updatePauseWhenObstructed(it) }
                )

                SettingToggleCard(
                    icon = Icons.Default.WbSunny,
                    title = "Keep Screen Awake Always",
                    subtitle = "Prevent screen from turning off when playback is paused or active",
                    checked = playbackSettings.keepAwakeAlways,
                    onCheckedChange = { settingsViewModel.updateKeepAwakeAlways(it) }
                )

                SettingToggleCard(
                    icon = Icons.Default.PlayArrow,
                    title = "Background Playback",
                    subtitle = "Continue playing audio in background when exiting the player",
                    checked = playbackSettings.backgroundPlayEnabled,
                    onCheckedChange = { settingsViewModel.updateBackgroundPlayEnabled(it) }
                )
            }

            Spacer(Modifier.height(8.dp))
        }
    }

    // Dialogs
    if (showOrientationDialog) {
        AlertDialog(
            onDismissRequest = { showOrientationDialog = false },
            title = { Text("Screen Orientation") },
            text = {
                Column(Modifier.selectableGroup()) {
                    OrientationMode.entries.forEach { mode ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp)
                                .selectable(
                                    selected = (playbackSettings.orientationMode == mode),
                                    onClick = {
                                        settingsViewModel.updateOrientationMode(mode)
                                        showOrientationDialog = false
                                    },
                                    role = Role.RadioButton
                                )
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = (playbackSettings.orientationMode == mode),
                                onClick = null
                            )
                            Spacer(Modifier.width(16.dp))
                            Text(
                                text = when (mode) {
                                    OrientationMode.SYSTEM_DEFAULT -> "System Default"
                                    OrientationMode.LANDSCAPE -> "Landscape Only"
                                    OrientationMode.PORTRAIT -> "Portrait Only"
                                    OrientationMode.AUTO -> "Sensor Auto-Rotate"
                                },
                                style = MaterialTheme.typography.bodyLarge
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showOrientationDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showScalingDialog) {
        AlertDialog(
            onDismissRequest = { showScalingDialog = false },
            title = { Text("Fullscreen Scaling") },
            text = {
                Column(Modifier.selectableGroup()) {
                    AspectMode.entries.forEach { mode ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp)
                                .selectable(
                                    selected = (playbackSettings.aspectMode == mode),
                                    onClick = {
                                        settingsViewModel.updateAspectMode(mode)
                                        showScalingDialog = false
                                    },
                                    role = Role.RadioButton
                                )
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = (playbackSettings.aspectMode == mode),
                                onClick = null
                            )
                            Spacer(Modifier.width(16.dp))
                            Text(
                                text = when (mode) {
                                    AspectMode.FIT -> "Fit Screen"
                                    AspectMode.STRETCH -> "Stretch"
                                    AspectMode.CROP -> "Crop"
                                    AspectMode.ORIGINAL -> "100% Original"
                                },
                                style = MaterialTheme.typography.bodyLarge
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showScalingDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showSoftButtonDialog) {
        AlertDialog(
            onDismissRequest = { showSoftButtonDialog = false },
            title = { Text("System Buttons Mode") },
            text = {
                Column(Modifier.selectableGroup()) {
                    SoftButtonMode.entries.forEach { mode ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp)
                                .selectable(
                                    selected = (playbackSettings.softButtonMode == mode),
                                    onClick = {
                                        settingsViewModel.updateSoftButtonMode(mode)
                                        showSoftButtonDialog = false
                                    },
                                    role = Role.RadioButton
                                )
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = (playbackSettings.softButtonMode == mode),
                                onClick = null
                            )
                            Spacer(Modifier.width(16.dp))
                            Text(
                                text = when (mode) {
                                    SoftButtonMode.AUTO_HIDE -> "Auto-hide with controls"
                                    SoftButtonMode.SHOW -> "Always Show Navigation Bar"
                                    SoftButtonMode.HIDE -> "Always hide (Immersive mode)"
                                },
                                style = MaterialTheme.typography.bodyLarge
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showSoftButtonDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showPlaybackControlPositionDialog) {
        val options = listOf(false, true)
        AlertDialog(
            onDismissRequest = { showPlaybackControlPositionDialog = false },
            title = { Text("Playback Control Position") },
            text = {
                Column(Modifier.selectableGroup()) {
                    options.forEach { option ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp)
                                .selectable(
                                    selected = (playbackSettings.isBottomLayoutEnabled == option),
                                    onClick = {
                                        settingsViewModel.updateIsBottomLayoutEnabled(option)
                                        showPlaybackControlPositionDialog = false
                                    },
                                    role = Role.RadioButton
                                )
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = (playbackSettings.isBottomLayoutEnabled == option),
                                onClick = null
                            )
                            Spacer(Modifier.width(16.dp))
                            Text(
                                text = if (option) "Below Seekbar" else "Center",
                                style = MaterialTheme.typography.bodyLarge
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showPlaybackControlPositionDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showIconSizeDialog) {
        val sizes = listOf("small", "medium", "large")
        AlertDialog(
            onDismissRequest = { showIconSizeDialog = false },
            title = { Text("Controls Icon Size") },
            text = {
                Column(Modifier.selectableGroup()) {
                    sizes.forEach { size ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp)
                                .selectable(
                                    selected = (playbackSettings.controlIconSize == size),
                                    onClick = {
                                        settingsViewModel.updateControlIconSize(size)
                                        showIconSizeDialog = false
                                    },
                                    role = Role.RadioButton
                                )
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = (playbackSettings.controlIconSize == size),
                                onClick = null
                            )
                            Spacer(Modifier.width(16.dp))
                            Text(
                                text = size.replaceFirstChar { it.uppercase() },
                                style = MaterialTheme.typography.bodyLarge
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showIconSizeDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showSeekBarStyleDialog) {
        val styles = listOf("standard", "thick", "slim", "wavy")
        AlertDialog(
            onDismissRequest = { showSeekBarStyleDialog = false },
            title = { Text("Seekbar Style") },
            text = {
                Column(Modifier.selectableGroup()) {
                    styles.forEach { style ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp)
                                .selectable(
                                    selected = (playbackSettings.seekBarStyle == style),
                                    onClick = {
                                        settingsViewModel.updateSeekBarStyle(style)
                                        showSeekBarStyleDialog = false
                                    },
                                    role = Role.RadioButton
                                )
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = (playbackSettings.seekBarStyle == style),
                                onClick = null
                            )
                            Spacer(Modifier.width(16.dp))
                            Text(
                                text = when (style) {
                                    "wavy" -> "Wavy (Curve)"
                                    "thick" -> "Thick"
                                    "slim" -> "Slim"
                                    else -> "Standard"
                                },
                                style = MaterialTheme.typography.bodyLarge
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showSeekBarStyleDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun InterfaceSectionHeader(label: String) {
    Text(
        text = label,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(start = 4.dp, bottom = 6.dp)
    )
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
