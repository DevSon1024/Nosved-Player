package com.devson.nosvedplayer.ui.screens.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.devson.nosvedplayer.repository.FullScreenMode
import com.devson.nosvedplayer.repository.OrientationMode
import com.devson.nosvedplayer.repository.SoftButtonMode
import com.devson.nosvedplayer.viewmodel.SettingsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerScreen(
    onBack: () -> Unit,
    settingsViewModel: SettingsViewModel = viewModel()
) {
    val playbackSettings by settingsViewModel.playbackSettings.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Player Interface",
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
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            contentPadding = PaddingValues(bottom = 32.dp)
        ) {
            item { Spacer(Modifier.height(8.dp)) }

            // Section A: Control Settings
            item { SettingsSectionLabel("Control Settings") }
            item {
                SettingsCard {
                    SettingsDropdownItem(
                        icon = Icons.Default.ScreenRotation,
                        title = "Orientation Mode",
                        subtitle = "Select how the video orientation behaves",
                        options = OrientationMode.values().map { it.name },
                        selectedOption = playbackSettings.orientationMode.name,
                        onOptionSelected = { settingsViewModel.updateOrientationMode(OrientationMode.valueOf(it)) }
                    )
                    SettingsDivider()
                    SettingsDropdownItem(
                        icon = Icons.Default.Fullscreen,
                        title = "Full Screen Mode",
                        subtitle = "Select full screen behaviour",
                        options = FullScreenMode.values().map { it.name },
                        selectedOption = playbackSettings.fullScreenMode.name,
                        onOptionSelected = { settingsViewModel.updateFullScreenMode(FullScreenMode.valueOf(it)) }
                    )
                    SettingsDivider()
                    SettingsDropdownItem(
                        icon = Icons.Default.SmartButton,
                        title = "Soft Button Mode",
                        subtitle = "Configure on-screen soft buttons",
                        options = SoftButtonMode.values().map { it.name },
                        selectedOption = playbackSettings.softButtonMode.name,
                        onOptionSelected = { settingsViewModel.updateSoftButtonMode(SoftButtonMode.valueOf(it)) }
                    )
                }
                Spacer(Modifier.height(16.dp))
            }

            // Section B: Brightness
            item { SettingsSectionLabel("Brightness") }
            item {
                SettingsCard {
                    SettingsToggleRow(
                        icon = Icons.Default.BrightnessMedium,
                        title = "Override System Brightness",
                        subtitle = "Use custom brightness for player",
                        checked = playbackSettings.isCustomBrightnessEnabled,
                        onCheckedChange = { settingsViewModel.updateIsCustomBrightnessEnabled(it) }
                    )
                    AnimatedVisibility(visible = playbackSettings.isCustomBrightnessEnabled) {
                        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                            Text(
                                text = "Brightness Level",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Slider(
                                value = playbackSettings.customBrightnessLevel,
                                onValueChange = { settingsViewModel.updateCustomBrightnessLevel(it) },
                                valueRange = 0f..1f
                            )
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))
            }

            // Section C: Video Player Overlays
            item { SettingsSectionLabel("Video Player Overlays") }
            item {
                SettingsCard {
                    SettingsToggleRow(
                        icon = Icons.Default.Timer,
                        title = "Show Elapsed Time Overlay",
                        subtitle = "Display elapsed time overlay on video",
                        checked = playbackSettings.showElapsedTimeOverlay,
                        onCheckedChange = { settingsViewModel.updateShowElapsedTimeOverlay(it) }
                    )
                    SettingsDivider()
                    SettingsToggleRow(
                        icon = Icons.Default.BatteryFull,
                        title = "Show Battery & Clock Overlay",
                        subtitle = "Display battery and time over video",
                        checked = playbackSettings.showBatteryClockOverlay,
                        onCheckedChange = { settingsViewModel.updateShowBatteryClockOverlay(it) }
                    )
                }
                Spacer(Modifier.height(16.dp))
            }

            // Section D: Playback Behavior
            item { SettingsSectionLabel("Playback Behavior") }
            item {
                SettingsCard {
                    SettingsToggleRow(
                        icon = Icons.Default.ScreenRotationAlt,
                        title = "Show Screen Rotation Button",
                        subtitle = "Show rotation button when device is rotated",
                        checked = playbackSettings.showScreenRotationButton,
                        onCheckedChange = { settingsViewModel.updateShowScreenRotationButton(it) }
                    )
                    SettingsDivider()
                    SettingsToggleRow(
                        icon = Icons.Default.PauseCircle,
                        title = "Pause when Obstructed",
                        subtitle = "Pause video when screen is obstructed",
                        checked = playbackSettings.pauseWhenObstructed,
                        onCheckedChange = { settingsViewModel.updatePauseWhenObstructed(it) }
                    )
                }
            }
        }
    }
}

@Composable
private fun SettingsSectionLabel(label: String) {
    Text(
        text = label,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(start = 4.dp, bottom = 6.dp)
    )
}

@Composable
private fun SettingsCard(content: @Composable ColumnScope.() -> Unit) {
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
private fun SettingsDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(start = 56.dp),
        color = MaterialTheme.colorScheme.outlineVariant
    )
}

@Composable
private fun SettingsToggleRow(
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
            onCheckedChange = onCheckedChange
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsDropdownItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    options: List<String>,
    selectedOption: String,
    onOptionSelected: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { expanded = true }
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
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

        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            
            ExposedDropdownMenuBox(
                expanded = expanded,
                onExpandedChange = { expanded = !expanded }
            ) {
                OutlinedTextField(
                    value = selectedOption,
                    onValueChange = {},
                    readOnly = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor()
                        .padding(top = 8.dp),
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                    colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors()
                )
                ExposedDropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false }
                ) {
                    options.forEach { option ->
                        DropdownMenuItem(
                            text = { Text(option) },
                            onClick = {
                                onOptionSelected(option)
                                expanded = false
                            }
                        )
                    }
                }
            }
        }
    }
}
