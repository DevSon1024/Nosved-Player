package com.devson.nosvedplayer.ui.screens.settings

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
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.viewmodel.compose.viewModel
import com.devson.nosvedplayer.repository.FullScreenMode
import com.devson.nosvedplayer.repository.OrientationMode
import com.devson.nosvedplayer.repository.SoftButtonMode
import com.devson.nosvedplayer.viewmodel.SettingsViewModel

// Maps raw enum names to user-readable labels
private fun String.toDisplayLabel(): String = this
    .replace('_', ' ')
    .lowercase()
    .replaceFirstChar { it.uppercase() }

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
                .padding(horizontal = 16.dp),
            contentPadding = PaddingValues(
                top = padding.calculateTopPadding(),
                bottom = padding.calculateBottomPadding() + 32.dp
            )
        ) {
            item { Spacer(Modifier.height(8.dp)) }

            item { SettingsSectionLabel("Control Settings") }
            item {
                SettingsCard {
                    SettingsRadioItem(
                        icon = Icons.Default.ScreenRotation,
                        title = "Orientation Mode",
                        subtitle = "How the video orientation behaves",
                        options = OrientationMode.values().map { it.name },
                        selectedOption = playbackSettings.orientationMode.name,
                        onOptionSelected = {
                            settingsViewModel.updateOrientationMode(OrientationMode.valueOf(it))
                        }
                    )
                    SettingsDivider()
                    SettingsRadioItem(
                        icon = Icons.Default.Fullscreen,
                        title = "Full Screen Mode",
                        subtitle = "Full screen behaviour during playback",
                        options = FullScreenMode.values().map { it.name },
                        selectedOption = playbackSettings.fullScreenMode.name,
                        onOptionSelected = {
                            settingsViewModel.updateFullScreenMode(FullScreenMode.valueOf(it))
                        }
                    )
                    SettingsDivider()
                    SettingsRadioItem(
                        icon = Icons.Default.SmartButton,
                        title = "Soft Button",
                        subtitle = "Configure on-screen soft buttons",
                        options = SoftButtonMode.values().map { it.name },
                        selectedOption = playbackSettings.softButtonMode.name,
                        onOptionSelected = {
                            settingsViewModel.updateSoftButtonMode(SoftButtonMode.valueOf(it))
                        }
                    )
                }
                Spacer(Modifier.height(16.dp))
            }

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

        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun SettingsRadioItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    options: List<String>,
    selectedOption: String,
    onOptionSelected: (String) -> Unit
) {
    var showDialog by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { showDialog = true }
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
            Text(
                text = selectedOption.toDisplayLabel(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(top = 2.dp)
            )
        }

        Icon(
            imageVector = Icons.Default.ChevronRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp)
        )
    }

    if (showDialog) {
        RadioPickerDialog(
            title = title,
            options = options,
            selectedOption = selectedOption,
            onOptionSelected = {
                onOptionSelected(it)
                showDialog = false
            },
            onDismiss = { showDialog = false }
        )
    }
}

@Composable
private fun RadioPickerDialog(
    title: String,
    options: List<String>,
    selectedOption: String,
    onOptionSelected: (String) -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 6.dp
        ) {
            Column(modifier = Modifier.padding(top = 24.dp, bottom = 8.dp)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp)
                )
                Spacer(Modifier.height(8.dp))
                options.forEach { option ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onOptionSelected(option) }
                            .padding(horizontal = 16.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = option == selectedOption,
                            onClick = { onOptionSelected(option) }
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = option.toDisplayLabel(),
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(end = 16.dp, bottom = 8.dp),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancel")
                    }
                }
            }
        }
    }
}
