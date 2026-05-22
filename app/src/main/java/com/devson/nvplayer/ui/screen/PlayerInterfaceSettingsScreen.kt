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
import androidx.compose.material.icons.automirrored.filled.RotateRight
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
import androidx.lifecycle.viewmodel.compose.viewModel
import com.devson.nvplayer.repository.FullScreenMode
import com.devson.nvplayer.repository.OrientationMode
import com.devson.nvplayer.repository.SoftButtonMode
import com.devson.nvplayer.viewmodel.SettingsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerInterfaceSettingsScreen(
    onNavigateBack: () -> Unit,
    settingsViewModel: SettingsViewModel = viewModel()
) {
    val playbackSettings by settingsViewModel.playbackSettings.collectAsState()
    val scrollState = rememberScrollState()

    var showOrientationDialog by remember { mutableStateOf(false) }
    var showScalingDialog by remember { mutableStateOf(false) }
    var showSoftButtonDialog by remember { mutableStateOf(false) }
    var showIconSizeDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Player Interface",
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

            // Display & Orientation Section
            InterfaceSectionHeader("Layout & Orientation")
            InterfaceCard {
                InterfaceRow(
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

                InterfaceDivider()

                InterfaceRow(
                    icon = Icons.Default.AspectRatio,
                    title = "Fullscreen Scale Mode",
                    subtitle = when (playbackSettings.fullScreenMode) {
                        FullScreenMode.AUTO_SWITCH -> "Auto Switch aspect ratio"
                        FullScreenMode.STRETCH -> "Stretch to fill screen"
                        FullScreenMode.CROP -> "Crop and zoom to fill"
                        FullScreenMode.FIT -> "Fit to screen (Letterbox)"
                    },
                    onClick = { showScalingDialog = true }
                )

                InterfaceDivider()

                InterfaceRow(
                    icon = Icons.Default.Fullscreen,
                    title = "System Navigation Buttons",
                    subtitle = when (playbackSettings.softButtonMode) {
                        SoftButtonMode.AUTO_HIDE -> "Auto-hide with controls"
                        SoftButtonMode.SHOW -> "Always show navigation buttons"
                        SoftButtonMode.HIDE -> "Always hide (Immersive mode)"
                    },
                    onClick = { showSoftButtonDialog = true }
                )
            }

            Spacer(Modifier.height(20.dp))

            // Playback Controls customization
            InterfaceSectionHeader("Player Controls Customization")
            InterfaceCard {
                InterfaceRow(
                    icon = Icons.Default.PhotoSizeSelectLarge,
                    title = "Controls Icon Size",
                    subtitle = playbackSettings.controlIconSize.replaceFirstChar { it.uppercase() },
                    onClick = { showIconSizeDialog = true }
                )

                InterfaceDivider()

                InterfaceToggleRow(
                    icon = Icons.Default.SkipNext,
                    title = "Auto-play Next Video",
                    subtitle = "Automatically load and play next video in folder",
                    checked = playbackSettings.autoPlayEnabled,
                    onCheckedChange = { settingsViewModel.updateAutoPlayEnabled(it) }
                )

                InterfaceDivider()

                InterfaceToggleRow(
                    icon = Icons.Default.FastForward,
                    title = "Show Seek Buttons",
                    subtitle = "Show fast forward and rewind seek buttons in player controls",
                    checked = playbackSettings.showSeekButtons,
                    onCheckedChange = { settingsViewModel.updateShowSeekButtons(it) }
                )

                InterfaceDivider()

                InterfaceToggleRow(
                    icon = Icons.Default.SkipNext,
                    title = "Show Skip Prev/Next Buttons",
                    subtitle = "Show previous/next chapter skip buttons in player controls",
                    checked = playbackSettings.showNextPrevButtons,
                    onCheckedChange = { settingsViewModel.updateShowNextPrevButtons(it) }
                )
            }

            Spacer(Modifier.height(20.dp))

            // Info Overlays Section
            InterfaceSectionHeader("Info Overlays")
            InterfaceCard {
                InterfaceToggleRow(
                    icon = Icons.Default.Schedule,
                    title = "Show Elapsed Time",
                    subtitle = "Always show current playback time at top edge",
                    checked = playbackSettings.showElapsedTimeOverlay,
                    onCheckedChange = { settingsViewModel.updateShowElapsedTimeOverlay(it) }
                )

                InterfaceDivider()

                InterfaceToggleRow(
                    icon = Icons.Default.HourglassBottom,
                    title = "Show Remaining Time",
                    subtitle = "Display remaining time instead of total duration",
                    checked = playbackSettings.showRemainingTime,
                    onCheckedChange = { settingsViewModel.updateShowRemainingTime(it) }
                )

                InterfaceDivider()

                InterfaceToggleRow(
                    icon = Icons.Default.BatteryChargingFull,
                    title = "Show Battery & Clock Overlay",
                    subtitle = "Display battery status and device clock on control bar",
                    checked = playbackSettings.showBatteryClockOverlay,
                    onCheckedChange = { settingsViewModel.updateShowBatteryClockOverlay(it) }
                )

                InterfaceDivider()

                InterfaceToggleRow(
                    icon = Icons.AutoMirrored.Filled.RotateRight,
                    title = "Show Quick Rotation Button",
                    subtitle = "Display rotation lock toggle button in player interface",
                    checked = playbackSettings.showScreenRotationButton,
                    onCheckedChange = { settingsViewModel.updateShowScreenRotationButton(it) }
                )
            }

            Spacer(Modifier.height(20.dp))

            // Safety & Automation Section
            InterfaceSectionHeader("Automation Behavior")
            InterfaceCard {
                InterfaceToggleRow(
                    icon = Icons.Default.PauseCircle,
                    title = "Pause on Obstruction",
                    subtitle = "Pause video playback automatically if screen is covered",
                    checked = playbackSettings.pauseWhenObstructed,
                    onCheckedChange = { settingsViewModel.updatePauseWhenObstructed(it) }
                )

                InterfaceDivider()

                InterfaceToggleRow(
                    icon = Icons.Default.WbSunny,
                    title = "Keep Screen Awake Always",
                    subtitle = "Prevent screen from turning off when playback is paused or active",
                    checked = playbackSettings.keepAwakeAlways,
                    onCheckedChange = { settingsViewModel.updateKeepAwakeAlways(it) }
                )
            }

            Spacer(Modifier.height(24.dp))
        }
    }

    // Dialogs
    if (showOrientationDialog) {
        AlertDialog(
            onDismissRequest = { showOrientationDialog = false },
            title = { Text("Screen Orientation") },
            text = {
                Column(Modifier.selectableGroup()) {
                    OrientationMode.values().forEach { mode ->
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
                    FullScreenMode.values().forEach { mode ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp)
                                .selectable(
                                    selected = (playbackSettings.fullScreenMode == mode),
                                    onClick = {
                                        settingsViewModel.updateFullScreenMode(mode)
                                        showScalingDialog = false
                                    },
                                    role = Role.RadioButton
                                )
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = (playbackSettings.fullScreenMode == mode),
                                onClick = null
                            )
                            Spacer(Modifier.width(16.dp))
                            Text(
                                text = when (mode) {
                                    FullScreenMode.AUTO_SWITCH -> "Auto Stretch/Crop"
                                    FullScreenMode.STRETCH -> "Stretch to Fill"
                                    FullScreenMode.CROP -> "Zoom Crop"
                                    FullScreenMode.FIT -> "Fit Letterbox"
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
                    SoftButtonMode.values().forEach { mode ->
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
                                    SoftButtonMode.AUTO_HIDE -> "Auto Hide Navigation Bar"
                                    SoftButtonMode.SHOW -> "Always Show Navigation Bar"
                                    SoftButtonMode.HIDE -> "Always Hide Navigation Bar"
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
private fun InterfaceCard(content: @Composable ColumnScope.() -> Unit) {
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
private fun InterfaceDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(start = 56.dp),
        color = MaterialTheme.colorScheme.outlineVariant
    )
}

@Composable
private fun InterfaceRow(
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
private fun InterfaceToggleRow(
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
