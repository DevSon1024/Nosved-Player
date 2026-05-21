package com.devson.nvplayer.ui.screens.settings

import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.viewmodel.compose.viewModel
import com.devson.nvplayer.repository.SubtitleFont
import com.devson.nvplayer.viewmodel.SettingsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SubtitleSettingsScreen(
    onBack: () -> Unit,
    settingsViewModel: SettingsViewModel = viewModel()
) {
    val playbackSettings by settingsViewModel.playbackSettings.collectAsState()
    val defaultSubtitleLang by settingsViewModel.defaultSubtitleLang.collectAsState()
    val context = LocalContext.current

    var showSubtitleLangDialog by remember { mutableStateOf(false) }
    var showFontDialog by remember { mutableStateOf(false) }
    var showEncodingDialog by remember { mutableStateOf(false) }

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

    val encodings = listOf("UTF-8", "ISO-8859-1", "Windows-1252", "UTF-16", "GBK", "Big5")

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Subtitle Settings",
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

            item { SettingsSectionLabel("Subtitle Loading") }
            item {
                SettingsCard {
                    SettingsToggleRow(
                        icon = Icons.Default.CloudDownload,
                        title = "Subtitle Auto-load",
                        subtitle = "Automatically search and load subtitles",
                        checked = playbackSettings.isSubtitleAutoLoadEnabled,
                        onCheckedChange = { settingsViewModel.updateSubtitleAutoLoad(it) }
                    )
                    SettingsDivider()
                    SettingsClickRow(
                        icon = Icons.Default.Language,
                        title = "Preferred Subtitle Language",
                        subtitle = commonLanguages.find { it.first == defaultSubtitleLang }?.second
                            ?: defaultSubtitleLang.ifEmpty { "System Default" },
                        onClick = { showSubtitleLangDialog = true }
                    )
                    SettingsDivider()
                    SettingsClickRow(
                        icon = Icons.Default.TextFormat,
                        title = "Subtitle Text Encoding",
                        subtitle = playbackSettings.subtitleTextEncoding,
                        onClick = { showEncodingDialog = true }
                    )
                }
                Spacer(Modifier.height(16.dp))
            }

            item { SettingsSectionLabel("Subtitle Style Source") }
            item {
                SettingsCard {
                    SettingsToggleRow(
                        icon = Icons.Default.ClosedCaption,
                        title = "Use System Caption Style",
                        subtitle = "Follow Android system captioning settings",
                        checked = playbackSettings.useSystemCaptionStyle,
                        onCheckedChange = { settingsViewModel.updateUseSystemCaptionStyle(it) }
                    )
                    if (playbackSettings.useSystemCaptionStyle) {
                        SettingsDivider()
                        SettingsClickRow(
                            icon = Icons.Default.OpenInNew,
                            title = "System Caption Settings",
                            subtitle = "Open system settings to configure style",
                            onClick = { context.startActivity(Intent(Settings.ACTION_CAPTIONING_SETTINGS)) }
                        )
                    }
                    SettingsDivider()
                    SettingsToggleRow(
                        icon = Icons.Default.Style,
                        title = "Apply Embedded Styles",
                        subtitle = "Use styles embedded in MKV/ASS files",
                        checked = playbackSettings.shouldApplyEmbeddedStyles,
                        onCheckedChange = { settingsViewModel.updateApplyEmbeddedStyles(it) }
                    )
                }
                Spacer(Modifier.height(16.dp))
            }

            item { SettingsSectionLabel("Subtitle Font Settings") }
            item {
                SettingsCard {
                    SettingsRadioItem(
                        icon = Icons.Default.FontDownload,
                        title = "Subtitle Font",
                        subtitle = "Select font for text-based subtitles",
                        options = SubtitleFont.values().map { it.name },
                        selectedOption = playbackSettings.subtitleFont.name,
                        onOptionSelected = { settingsViewModel.updateSubtitleFont(SubtitleFont.valueOf(it)) }
                    )
                    SettingsDivider()
                    SettingsToggleRow(
                        icon = Icons.Default.FormatBold,
                        title = "Bold Subtitles",
                        subtitle = "Make subtitle text thicker",
                        checked = playbackSettings.isSubtitleBold,
                        onCheckedChange = { settingsViewModel.updateIsSubtitleBold(it) }
                    )
                }
            }
        }
    }

    if (showSubtitleLangDialog) {
        LangPickerDialog(
            title = "Preferred Subtitle Language",
            options = commonLanguages,
            selected = defaultSubtitleLang,
            onSelect = { settingsViewModel.updatePreferredSubtitleLanguage(it); showSubtitleLangDialog = false },
            onDismiss = { showSubtitleLangDialog = false }
        )
    }

    if (showEncodingDialog) {
        SimpleOptionsDialog(
            title = "Subtitle Encoding",
            options = encodings,
            selected = playbackSettings.subtitleTextEncoding,
            onSelect = { settingsViewModel.updateSubtitleTextEncoding(it); showEncodingDialog = false },
            onDismiss = { showEncodingDialog = false }
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
                text = selectedOption,
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
        SimpleOptionsDialog(
            title = title,
            options = options,
            selected = selectedOption,
            onSelect = {
                onOptionSelected(it)
                showDialog = false
            },
            onDismiss = { showDialog = false }
        )
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

@Composable
private fun SimpleOptionsDialog(
    title: String,
    options: List<String>,
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
                        val option = options[index]
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onSelect(option) }
                                .padding(horizontal = 12.dp, vertical = 2.dp)
                        ) {
                            RadioButton(
                                selected = (option == selected),
                                onClick  = { onSelect(option) }
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(option, style = MaterialTheme.typography.bodyLarge)
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
