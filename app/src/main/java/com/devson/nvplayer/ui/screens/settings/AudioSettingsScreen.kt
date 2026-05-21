package com.devson.nvplayer.ui.screens.settings

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
import com.devson.nvplayer.viewmodel.SettingsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AudioSettingsScreen(
    onBack: () -> Unit,
    settingsViewModel: SettingsViewModel = viewModel()
) {
    val playbackSettings by settingsViewModel.playbackSettings.collectAsState()
    val defaultAudioLang by settingsViewModel.defaultAudioLang.collectAsState()

    var showAudioLangDialog by remember { mutableStateOf(false) }

    val commonLanguages = listOf(
        "" to "System Default",
        "en" to "English",
        "hi" to "Hindi",
        "fr" to "French",
        "de" to "German",
        "es" to "Spanish",
        "ja" to "Japanese",
        "ko" to "Korean",
        "zh" to "Chinese",
        "ar" to "Arabic",
        "ru" to "Russian",
        "pt" to "Portuguese",
        "it" to "Italian",
        "bn" to "Bengali",
        "mr" to "Marathi"
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Audio Settings",
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

            item { SettingsSectionLabel("Audio Track Settings") }
            item {
                SettingsCard {
                    SettingsClickRow(
                        icon = Icons.Default.Language,
                        title = "Preferred Audio Language",
                        subtitle = commonLanguages.find { it.first == defaultAudioLang }?.second
                            ?: defaultAudioLang.ifEmpty { "System Default" },
                        onClick = { showAudioLangDialog = true }
                    )
                }
                Spacer(Modifier.height(16.dp))
            }

            item { SettingsSectionLabel("Audio Focus & Devices") }
            item {
                SettingsCard {
                    SettingsToggleRow(
                        icon = Icons.Default.Hearing,
                        title = "Require Audio Focus",
                        subtitle = "Pause playback when other apps play audio",
                        checked = playbackSettings.shouldRequireAudioFocus,
                        onCheckedChange = { settingsViewModel.updateRequireAudioFocus(it) }
                    )
                    SettingsDivider()
                    SettingsToggleRow(
                        icon = Icons.Default.HeadsetOff,
                        title = "Pause on Headset Disconnect",
                        subtitle = "Automatically pause when headphones are removed",
                        checked = playbackSettings.shouldPauseOnHeadsetDisconnect,
                        onCheckedChange = { settingsViewModel.updatePauseOnHeadsetDisconnect(it) }
                    )
                    SettingsDivider()
                    SettingsToggleRow(
                        icon = Icons.Default.VolumeUp,
                        title = "Show System Volume Panel",
                        subtitle = "Show system volume UI during adjustment",
                        checked = playbackSettings.shouldShowSystemVolumePanel,
                        onCheckedChange = { settingsViewModel.updateShowSystemVolumePanel(it) }
                    )
                }
                Spacer(Modifier.height(16.dp))
            }

            item { SettingsSectionLabel("Volume Memory") }
            item {
                SettingsCard {
                    SettingsToggleRow(
                        icon = Icons.Default.Save,
                        title = "Remember Volume Level",
                        subtitle = "Restore last used volume for each video",
                        checked = playbackSettings.shouldRememberPlayerVolume,
                        onCheckedChange = { settingsViewModel.updateRememberPlayerVolume(it) }
                    )
                    SettingsDivider()
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.LinearScale,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp),
                                tint = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                            Spacer(Modifier.width(16.dp))
                            Text("Initial Volume Limit", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                            Text("${playbackSettings.maxInitialPlayerVolumePercentage}%", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
                        }
                        Slider(
                            value = playbackSettings.maxInitialPlayerVolumePercentage.toFloat(),
                            onValueChange = { settingsViewModel.updateMaxInitialPlayerVolume(it.toInt()) },
                            valueRange = 0f..100f,
                            enabled = playbackSettings.shouldRememberPlayerVolume,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }
                }
                Spacer(Modifier.height(16.dp))
            }

            item { SettingsSectionLabel("Audio Processing") }
            item {
                SettingsCard {
                    SettingsToggleRow(
                        icon = Icons.Default.Equalizer,
                        title = "Volume Normalization",
                        subtitle = "Reduce volume differences between videos",
                        checked = playbackSettings.isVolumeNormalizationEnabled,
                        onCheckedChange = { settingsViewModel.updateVolumeNormalization(it) }
                    )
                    SettingsDivider()
                    SettingsToggleRow(
                        icon = Icons.Default.ExposurePlus2,
                        title = "Volume Boost",
                        subtitle = "Allow volume to exceed 100%",
                        checked = playbackSettings.isVolumeBoostEnabled,
                        onCheckedChange = { settingsViewModel.updateVolumeBoost(it) }
                    )
                }
            }
        }
    }

    if (showAudioLangDialog) {
        LangPickerDialog(
            title = "Preferred Audio Language",
            options = commonLanguages,
            selected = defaultAudioLang,
            onSelect = { settingsViewModel.updatePreferredAudioLanguage(it); showAudioLangDialog = false },
            onDismiss = { showAudioLangDialog = false }
        )
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
private fun SettingsClickRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.secondaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.size(18.dp)
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(subtitle, style = MaterialTheme.typography.bodySmall,
                 color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Icon(Icons.Default.ChevronRight, contentDescription = null,
             tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun LangPickerDialog(
    title: String,
    options: List<Pair<String, String>>,
    selected: String,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 20.dp, bottom = 8.dp)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)
                )
                Spacer(Modifier.height(8.dp))
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = false)
                        .heightIn(max = 360.dp)
                ) {
                    items(options.size) { index ->
                        val (code, label) = options[index]
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onSelect(code) }
                                .padding(horizontal = 12.dp, vertical = 2.dp)
                        ) {
                            RadioButton(
                                selected = (code == selected),
                                onClick  = { onSelect(code) }
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(label, style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
                Spacer(Modifier.height(4.dp))
                TextButton(
                    onClick  = onDismiss,
                    modifier = Modifier.align(Alignment.End).padding(end = 16.dp)
                ) { Text("Cancel") }
            }
        }
    }
}
