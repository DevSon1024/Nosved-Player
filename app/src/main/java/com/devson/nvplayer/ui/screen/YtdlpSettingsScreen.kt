package com.devson.nvplayer.ui.screen

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.devson.nvplayer.player.ytdlp.YtdlCodecPreference
import com.devson.nvplayer.player.ytdlp.YtdlContainerPreference
import com.devson.nvplayer.player.ytdlp.YtdlHdrPreference
import com.devson.nvplayer.player.ytdlp.YtdlPlaylistMode
import com.devson.nvplayer.player.ytdlp.YtdlpManager
import com.devson.nvplayer.viewmodel.SettingsViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun YtdlpSettingsScreen(
    onNavigateBack: () -> Unit,
    settingsViewModel: SettingsViewModel = viewModel()
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val playbackSettings by settingsViewModel.playbackSettings.collectAsState()

    var consoleText by remember { mutableStateOf("Terminal idle.\nClick 'Install/Reinstall' to set up Python and yt-dlp environment.\n") }
    var isRunningTask by remember { mutableStateOf(false) }
    var expandedDropdown by remember { mutableStateOf<String?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "yt-dlp Streaming Settings",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // Section 1: Python & yt-dlp Compiler Environment
            YtdlpSectionHeader(title = "Environment & Compilation", icon = Icons.Default.Terminal)
            ElevatedCard(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.elevatedCardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Python Execution Bypass (Android 10+)",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Due to API 29+ security restrictions, binary execution from the data folder is blocked. NVPlayer bypasses this by wrapping Python in a shared JNI library (`libytdl.so`). Click Compile to link and build the environment.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    // Console Log Output
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(180.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFF1E1E1E))
                            .padding(8.dp)
                    ) {
                        val consoleScrollState = rememberScrollState()
                        // Auto-scroll to bottom of console logs
                        LaunchedEffect(consoleText) {
                            consoleScrollState.animateScrollTo(consoleScrollState.maxValue)
                        }

                        Text(
                            text = consoleText,
                            color = Color(0xFF00FF00),
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp,
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(consoleScrollState)
                        )
                    }

                    // Action Buttons
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                if (isRunningTask) return@Button
                                isRunningTask = true
                                consoleText = "Initializing installation...\n"
                                coroutineScope.launch {
                                    val result = YtdlpManager.runInstall(context) { logLine ->
                                        consoleText += logLine
                                    }
                                    if (result) {
                                        consoleText += "\nSetup succeeded. Ready to stream.\n"
                                        Toast.makeText(context, "Environment installed successfully", Toast.LENGTH_SHORT).show()
                                    } else {
                                        consoleText += "\nSetup failed. Check errors.\n"
                                        Toast.makeText(context, "Installation failed", Toast.LENGTH_LONG).show()
                                    }
                                    isRunningTask = false
                                }
                            },
                            enabled = !isRunningTask,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.Build, contentDescription = null)
                            Spacer(Modifier.width(6.dp))
                            Text(text = "Compile & Install", fontWeight = FontWeight.SemiBold)
                        }

                        OutlinedButton(
                            onClick = {
                                if (isRunningTask) return@OutlinedButton
                                isRunningTask = true
                                consoleText = "Checking for yt-dlp updates...\n"
                                coroutineScope.launch {
                                    val result = YtdlpManager.runUpdate(context) { logLine ->
                                        consoleText += logLine
                                    }
                                    if (result) {
                                        consoleText += "\nUpdate succeeded.\n"
                                        Toast.makeText(context, "yt-dlp updated successfully", Toast.LENGTH_SHORT).show()
                                    } else {
                                        consoleText += "\nUpdate failed or already up to date.\n"
                                        Toast.makeText(context, "Update failed", Toast.LENGTH_SHORT).show()
                                    }
                                    isRunningTask = false
                                }
                            },
                            enabled = !isRunningTask,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = null)
                            Spacer(Modifier.width(6.dp))
                            Text(text = "Update yt-dlp", fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }

            // Section 2: Video & Audio Quality Format Options
            YtdlpSectionHeader(title = "Format & Quality", icon = Icons.Default.Tune)
            SettingsCard {
                // 1. Resolution / Quality Limit
                val qualityLabel = when (playbackSettings.ytdlQuality) {
                    -1 -> "Auto / Maximum Quality"
                    4320 -> "4320p (8K)"
                    2160 -> "2160p (4K)"
                    1440 -> "1440p (2K)"
                    1080 -> "1080p (Full HD)"
                    720 -> "720p (HD)"
                    480 -> "480p"
                    360 -> "360p"
                    240 -> "240p"
                    144 -> "144p"
                    else -> "${playbackSettings.ytdlQuality}p"
                }
                DropdownSettingRow(
                    title = "Max Quality Limit",
                    subtitle = "Upper limit for video height format selection",
                    currentValue = qualityLabel,
                    isExpanded = expandedDropdown == "quality",
                    onExpandChange = { expandedDropdown = if (it) "quality" else null }
                ) {
                    val qualities = listOf(-1, 4320, 2160, 1440, 1080, 720, 480, 360, 240, 144)
                    qualities.forEach { q ->
                        val text = when (q) {
                            -1 -> "Auto / Maximum"
                            4320 -> "4320p (8K)"
                            2160 -> "2160p (4K)"
                            1440 -> "1440p (2K)"
                            1080 -> "1080p (Full HD)"
                            720 -> "720p (HD)"
                            else -> "${q}p"
                        }
                        DropdownMenuItem(
                            text = { Text(text) },
                            onClick = {
                                settingsViewModel.updateYtdlQuality(q)
                                expandedDropdown = null
                            }
                        )
                    }
                }

                SettingsDivider()

                // 2. Codec Preference
                DropdownSettingRow(
                    title = "Preferred Video Codec",
                    subtitle = "Filter and prioritize specific stream codecs",
                    currentValue = playbackSettings.codecPreference.title,
                    isExpanded = expandedDropdown == "codec",
                    onExpandChange = { expandedDropdown = if (it) "codec" else null }
                ) {
                    YtdlCodecPreference.values().forEach { codec ->
                        DropdownMenuItem(
                            text = { Text(codec.title) },
                            onClick = {
                                settingsViewModel.updateYtdlCodecPreference(codec)
                                expandedDropdown = null
                            }
                        )
                    }
                }

                SettingsDivider()

                // 3. Max FPS
                val fpsLabel = if (playbackSettings.maxFps <= 0) "Auto / Unlimited" else "${playbackSettings.maxFps} fps"
                DropdownSettingRow(
                    title = "Max Frame Rate",
                    subtitle = "Limit video stream frames per second",
                    currentValue = fpsLabel,
                    isExpanded = expandedDropdown == "fps",
                    onExpandChange = { expandedDropdown = if (it) "fps" else null }
                ) {
                    listOf(0, 60, 50, 30, 24).forEach { fps ->
                        DropdownMenuItem(
                            text = { Text(if (fps <= 0) "Auto / Unlimited" else "$fps fps") },
                            onClick = {
                                settingsViewModel.updateYtdlMaxFps(fps)
                                expandedDropdown = null
                            }
                        )
                    }
                }

                SettingsDivider()

                // 4. Container Preference
                DropdownSettingRow(
                    title = "Preferred Container Format",
                    subtitle = "Request specific video container extensions",
                    currentValue = playbackSettings.containerPreference.title,
                    isExpanded = expandedDropdown == "container",
                    onExpandChange = { expandedDropdown = if (it) "container" else null }
                ) {
                    YtdlContainerPreference.values().forEach { container ->
                        DropdownMenuItem(
                            text = { Text(container.title) },
                            onClick = {
                                settingsViewModel.updateYtdlContainerPreference(container)
                                expandedDropdown = null
                            }
                        )
                    }
                }

                SettingsDivider()

                // 5. HDR Preference
                DropdownSettingRow(
                    title = "HDR Preference",
                    subtitle = "Choose between dynamic range standards",
                    currentValue = playbackSettings.hdrPreference.title,
                    isExpanded = expandedDropdown == "hdr",
                    onExpandChange = { expandedDropdown = if (it) "hdr" else null }
                ) {
                    YtdlHdrPreference.values().forEach { hdr ->
                        DropdownMenuItem(
                            text = { Text(hdr.title) },
                            onClick = {
                                settingsViewModel.updateYtdlHdrPreference(hdr)
                                expandedDropdown = null
                            }
                        )
                    }
                }

                SettingsDivider()

                // 6. Playlist Mode
                DropdownSettingRow(
                    title = "Playlist Load Mode",
                    subtitle = "Behavior when loading media playlist URLs",
                    currentValue = playbackSettings.playlistMode.title,
                    isExpanded = expandedDropdown == "playlist",
                    onExpandChange = { expandedDropdown = if (it) "playlist" else null }
                ) {
                    YtdlPlaylistMode.values().forEach { mode ->
                        DropdownMenuItem(
                            text = { Text(mode.title) },
                            onClick = {
                                settingsViewModel.updateYtdlPlaylistMode(mode)
                                expandedDropdown = null
                            }
                        )
                    }
                }

                SettingsDivider()

                // 7. Live stream from start
                SwitchSettingRow(
                    title = "Live Stream from Start",
                    subtitle = "Force player to buffer dynamic live streams from the beginning",
                    checked = playbackSettings.liveFromStart,
                    onCheckedChange = { settingsViewModel.updateYtdlLiveFromStart(it) }
                )
            }

            // Section 3: Subtitles & Localization
            YtdlpSectionHeader(title = "Subtitles & Localization", icon = Icons.Default.Subtitles)
            SettingsCard {
                SwitchSettingRow(
                    title = "Download Subtitles",
                    subtitle = "Search and inject embedded or external text tracks",
                    checked = playbackSettings.writeSubs,
                    onCheckedChange = { settingsViewModel.updateYtdlWriteSubs(it) }
                )

                SettingsDivider()

                SwitchSettingRow(
                    title = "Download Auto-Generated Subtitles",
                    subtitle = "Include machine translations or auto-transcripts",
                    checked = playbackSettings.writeAutoSubs,
                    onCheckedChange = { settingsViewModel.updateYtdlWriteAutoSubs(it) }
                )

                SettingsDivider()

                // Subtitle Languages list
                EditableTextSettingRow(
                    title = "Subtitle Languages",
                    subtitle = "Comma-separated language codes (e.g. en,mr,hi). Empty fetches all.",
                    value = playbackSettings.subtitleLanguages,
                    placeholder = "all",
                    onValueChange = { settingsViewModel.updateYtdlSubtitleLanguages(it) }
                )
            }

            // Section 4: Network, Security & Proxies
            YtdlpSectionHeader(title = "Network & Proxy Settings", icon = Icons.Default.Security)
            SettingsCard {
                SwitchSettingRow(
                    title = "Geographic Bypass",
                    subtitle = "Bypass location restrictions using extractor IP spoofing",
                    checked = playbackSettings.geoBypass,
                    onCheckedChange = { settingsViewModel.updateYtdlGeoBypass(it) }
                )

                SettingsDivider()

                EditableTextSettingRow(
                    title = "User-Agent Header",
                    subtitle = "Custom client identification to prevent platform blocks",
                    value = playbackSettings.customUserAgent,
                    placeholder = "Default User-Agent string",
                    onValueChange = { settingsViewModel.updateYtdlCustomUserAgent(it) }
                )

                SettingsDivider()

                EditableTextSettingRow(
                    title = "Http Referer",
                    subtitle = "Specify origin referrer request header",
                    value = playbackSettings.referer,
                    placeholder = "https://example.com",
                    onValueChange = { settingsViewModel.updateYtdlReferer(it) }
                )

                SettingsDivider()

                EditableTextSettingRow(
                    title = "Cookies File Path",
                    subtitle = "Absolute path to netscape format cookie text file",
                    value = playbackSettings.cookiesFile,
                    placeholder = "/storage/emulated/0/cookies.txt",
                    onValueChange = { settingsViewModel.updateYtdlCookiesFile(it) }
                )

                SettingsDivider()

                EditableTextSettingRow(
                    title = "Proxy Address",
                    subtitle = "Proxy connection URL (e.g. socks5://127.0.0.1:1080)",
                    value = playbackSettings.proxy,
                    placeholder = "socks5://ip:port",
                    onValueChange = { settingsViewModel.updateYtdlProxy(it) }
                )

                SettingsDivider()

                EditableTextSettingRow(
                    title = "Extractor Arguments",
                    subtitle = "Format: 'key:val' or multiple separated by commas",
                    value = playbackSettings.extractorArgs,
                    placeholder = "youtube:player_client=android",
                    onValueChange = { settingsViewModel.updateYtdlExtractorArgs(it) }
                )
            }

            // Section 5: SponsorBlock Integration
            YtdlpSectionHeader(title = "SponsorBlock Integration", icon = Icons.Default.Block)
            SettingsCard {
                EditableTextSettingRow(
                    title = "Mark Categories",
                    subtitle = "Insert chapters for specific segments (comma-separated, e.g. sponsor,intro)",
                    value = playbackSettings.sponsorBlockMark,
                    placeholder = "sponsor,selfpromo",
                    onValueChange = { settingsViewModel.updateYtdlSponsorBlockMark(it) }
                )

                SettingsDivider()

                EditableTextSettingRow(
                    title = "Remove Categories",
                    subtitle = "Automatically skip specific video categories during playback",
                    value = playbackSettings.sponsorBlockRemove,
                    placeholder = "sponsor",
                    onValueChange = { settingsViewModel.updateYtdlSponsorBlockRemove(it) }
                )
            }

            // Section 6: Advanced & Raw Options
            YtdlpSectionHeader(title = "Advanced Options", icon = Icons.Default.Build)
            SettingsCard {
                EditableTextSettingRow(
                    title = "Custom Format Selector String",
                    subtitle = "Overrides all quality, container and codec selectors (advanced)",
                    value = playbackSettings.ytdlFormat,
                    placeholder = "bestvideo+bestaudio/best",
                    onValueChange = { settingsViewModel.updateYtdlFormat(it) }
                )

                SettingsDivider()

                EditableTextSettingRow(
                    title = "Merge Output Format",
                    subtitle = "Force container file format when muxing (e.g. mkv, mp4)",
                    value = playbackSettings.mergeOutputFormat,
                    placeholder = "mkv",
                    onValueChange = { settingsViewModel.updateYtdlMergeOutputFormat(it) }
                )

                SettingsDivider()

                EditableTextSettingRow(
                    title = "Format Sort Order",
                    subtitle = "Rules for prioritizing stream formats (e.g. res,fps,codec)",
                    value = playbackSettings.formatSort,
                    placeholder = "res,fps,codec",
                    onValueChange = { settingsViewModel.updateYtdlFormatSort(it) }
                )

                SettingsDivider()

                EditableTextSettingRow(
                    title = "Custom Raw Option Flags",
                    subtitle = "Additional command-line parameters (e.g. --no-check-certificates)",
                    value = playbackSettings.customRawOptions,
                    placeholder = "--no-check-certificates",
                    onValueChange = { settingsViewModel.updateYtdlCustomRawOptions(it) }
                )
            }

            Spacer(Modifier.height(32.dp))
        }
    }
}

@Composable
private fun YtdlpSectionHeader(
    title: String,
    icon: ImageVector
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(10.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium.copy(
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.5.sp
            ),
            color = MaterialTheme.colorScheme.primary
        )
    }
}

@Composable
private fun SettingsCard(
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ),
        content = content
    )
}

@Composable
private fun SettingsDivider() {
    HorizontalDivider(
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
        modifier = Modifier.padding(horizontal = 16.dp)
    )
}

@Composable
private fun SwitchSettingRow(
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
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = MaterialTheme.colorScheme.primary,
                checkedTrackColor = MaterialTheme.colorScheme.primaryContainer
            )
        )
    }
}

@Composable
private fun DropdownSettingRow(
    title: String,
    subtitle: String,
    currentValue: String,
    isExpanded: Boolean,
    onExpandChange: (Boolean) -> Unit,
    dropdownContent: @Composable ColumnScope.() -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onExpandChange(!isExpanded) }
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        
        Spacer(modifier = Modifier.width(12.dp))

        Box {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = currentValue,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary
                )
                Icon(
                    imageVector = Icons.Default.KeyboardArrowDown,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
            }
            DropdownMenu(
                expanded = isExpanded,
                onDismissRequest = { onExpandChange(false) },
                modifier = Modifier.background(MaterialTheme.colorScheme.surfaceContainerHigh),
                content = dropdownContent
            )
        }
    }
}

@Composable
private fun EditableTextSettingRow(
    title: String,
    subtitle: String,
    value: String,
    placeholder: String,
    onValueChange: (String) -> Unit
) {
    var textValue by remember(value) { mutableStateOf(value) }
    var isEditing by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            IconButton(
                onClick = { isEditing = !isEditing }
            ) {
                Icon(
                    imageVector = if (isEditing) Icons.Default.Check else Icons.Default.Edit,
                    contentDescription = if (isEditing) "Confirm" else "Edit",
                    tint = if (isEditing) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        AnimatedVisibility(
            visible = isEditing,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically()
        ) {
            OutlinedTextField(
                value = textValue,
                onValueChange = {
                    textValue = it
                    onValueChange(it)
                },
                placeholder = { Text(placeholder) },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Text,
                    imeAction = ImeAction.Done
                ),
                modifier = Modifier.fillMaxWidth()
            )
        }

        if (!isEditing && value.isNotBlank()) {
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f))
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            )
        }
    }
}
