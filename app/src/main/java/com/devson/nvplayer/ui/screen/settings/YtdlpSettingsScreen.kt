package com.devson.nvplayer.ui.screen.settings

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.draw.alpha
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
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun YtdlpSettingsScreen(
    onNavigateBack: () -> Unit,
    settingsViewModel: SettingsViewModel = viewModel()
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val playbackSettings by settingsViewModel.playbackSettings.collectAsState()

    var consoleText by remember { mutableStateOf("Terminal idle.\nClick 'Compile & Install' to set up Python and yt-dlp environment.\n") }
    var isRunningTask by remember { mutableStateOf(false) }
    var expandedDropdown by remember { mutableStateOf<String?>(null) }

    var installedVersion by remember { mutableStateOf<String?>(null) }
    var latestVersion by remember { mutableStateOf<String?>(null) }
    var isUpdateChecking by remember { mutableStateOf(false) }
    var showUpdateDialog by remember { mutableStateOf(false) }
    var isInstalled by remember { mutableStateOf(false) }

    val checkStatus: () -> Unit = {
        val ytdlDir = YtdlpManager.getYtdlDir(context)
        isInstalled = File(ytdlDir, "yt-dlp").exists()
        if (isInstalled) {
            isUpdateChecking = true
            coroutineScope.launch {
                installedVersion = YtdlpManager.getInstalledVersion(context)
                latestVersion = YtdlpManager.getLatestReleaseTag()
                isUpdateChecking = false
                if (installedVersion != null && latestVersion != null && installedVersion != latestVersion) {
                    showUpdateDialog = true
                }
            }
        } else {
            installedVersion = null
            latestVersion = null
        }
    }

    LaunchedEffect(Unit) {
        checkStatus()
    }

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
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground,
                    navigationIconContentColor = MaterialTheme.colorScheme.onBackground
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
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Spacer(Modifier.height(4.dp))

            // Section 1: Python & yt-dlp Compiler Environment
            YtdlpSectionHeader(title = "Environment & Compilation", icon = Icons.Default.Terminal)
            
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                ),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
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
                        text = "Due to API 29+ security restrictions, binary execution from the data folder is blocked. Nosved Player bypasses this by wrapping Python in a shared JNI library (`libytdl.so`). Click Compile to link and build the environment.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    // Status Card Row
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(
                                if (isInstalled) {
                                    if (installedVersion != null && latestVersion != null && installedVersion != latestVersion) {
                                        MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.25f)
                                    } else {
                                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.15f)
                                    }
                                } else {
                                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                }
                            )
                            .border(
                                1.dp,
                                if (isInstalled) {
                                    if (installedVersion != null && latestVersion != null && installedVersion != latestVersion) {
                                        MaterialTheme.colorScheme.error.copy(alpha = 0.5f)
                                    } else {
                                        MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
                                    }
                                } else {
                                    MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
                                },
                                RoundedCornerShape(12.dp)
                            )
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (isInstalled) Icons.Default.CheckCircle else Icons.Default.Info,
                            contentDescription = null,
                            tint = if (isInstalled) {
                                if (installedVersion != null && latestVersion != null && installedVersion != latestVersion) {
                                    MaterialTheme.colorScheme.error
                                } else {
                                    MaterialTheme.colorScheme.primary
                                }
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = if (isInstalled) {
                                    if (installedVersion != null && latestVersion != null && installedVersion != latestVersion) {
                                        "yt-dlp Update Available"
                                    } else {
                                        "Environment Installed & Active"
                                    }
                                } else {
                                    "Environment Not Installed"
                                },
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            if (isInstalled) {
                                Text(
                                    text = buildString {
                                        append("Installed Version: ${installedVersion ?: "Querying..."}")
                                        if (latestVersion != null) {
                                            append(" | Latest: $latestVersion")
                                        }
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            } else {
                                Text(
                                    text = "Requires installation to play network streams",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        if (isInstalled && installedVersion != null && latestVersion != null && installedVersion != latestVersion) {
                            Button(
                                onClick = { showUpdateDialog = true },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.error,
                                    contentColor = MaterialTheme.colorScheme.onError
                                ),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text("Update", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }

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
                                    checkStatus()
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
                                    checkStatus()
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

            SettingDropdownCard(
                icon = Icons.Default.HighQuality,
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

            SettingDropdownCard(
                icon = Icons.Default.SettingsSuggest,
                title = "Preferred Video Codec",
                subtitle = "Filter and prioritize specific stream codecs",
                currentValue = playbackSettings.codecPreference.title,
                isExpanded = expandedDropdown == "codec",
                onExpandChange = { expandedDropdown = if (it) "codec" else null }
            ) {
                YtdlCodecPreference.entries.forEach { codec ->
                    DropdownMenuItem(
                        text = { Text(codec.title) },
                        onClick = {
                            settingsViewModel.updateYtdlCodecPreference(codec)
                            expandedDropdown = null
                        }
                    )
                }
            }

            val fpsLabel = if (playbackSettings.maxFps <= 0) "Auto / Unlimited" else "${playbackSettings.maxFps} fps"
            SettingDropdownCard(
                icon = Icons.Default.Speed,
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

            SettingDropdownCard(
                icon = Icons.Default.FolderZip,
                title = "Preferred Container Format",
                subtitle = "Request specific video container extensions",
                currentValue = playbackSettings.containerPreference.title,
                isExpanded = expandedDropdown == "container",
                onExpandChange = { expandedDropdown = if (it) "container" else null }
            ) {
                YtdlContainerPreference.entries.forEach { container ->
                    DropdownMenuItem(
                        text = { Text(container.title) },
                        onClick = {
                            settingsViewModel.updateYtdlContainerPreference(container)
                            expandedDropdown = null
                        }
                    )
                }
            }

            SettingDropdownCard(
                icon = Icons.Default.HdrOn,
                title = "HDR Preference",
                subtitle = "Choose between dynamic range standards",
                currentValue = playbackSettings.hdrPreference.title,
                isExpanded = expandedDropdown == "hdr",
                onExpandChange = { expandedDropdown = if (it) "hdr" else null }
            ) {
                YtdlHdrPreference.entries.forEach { hdr ->
                    DropdownMenuItem(
                        text = { Text(hdr.title) },
                        onClick = {
                            settingsViewModel.updateYtdlHdrPreference(hdr)
                            expandedDropdown = null
                        }
                    )
                }
            }

            SettingDropdownCard(
                icon = Icons.Default.Queue,
                title = "Playlist Load Mode",
                subtitle = "Behavior when loading media playlist URLs",
                currentValue = playbackSettings.playlistMode.title,
                isExpanded = expandedDropdown == "playlist",
                onExpandChange = { expandedDropdown = if (it) "playlist" else null }
            ) {
                YtdlPlaylistMode.entries.forEach { mode ->
                    DropdownMenuItem(
                        text = { Text(mode.title) },
                        onClick = {
                            settingsViewModel.updateYtdlPlaylistMode(mode)
                            expandedDropdown = null
                        }
                    )
                }
            }

            SettingToggleCard(
                icon = Icons.Default.PlayArrow,
                title = "Live Stream from Start",
                subtitle = "Force player to buffer dynamic live streams from the beginning",
                checked = playbackSettings.liveFromStart,
                onCheckedChange = { settingsViewModel.updateYtdlLiveFromStart(it) }
            )

            // Section 3: Subtitles & Localization
            YtdlpSectionHeader(title = "Subtitles & Localization", icon = Icons.Default.Subtitles)

            SettingToggleCard(
                icon = Icons.Default.Subtitles,
                title = "Download Subtitles",
                subtitle = "Search and inject embedded or external text tracks",
                checked = playbackSettings.writeSubs,
                onCheckedChange = { settingsViewModel.updateYtdlWriteSubs(it) }
            )

            SettingToggleCard(
                icon = Icons.Default.Translate,
                title = "Download Auto-Generated Subtitles",
                subtitle = "Include machine translations or auto-transcripts",
                checked = playbackSettings.writeAutoSubs,
                onCheckedChange = { settingsViewModel.updateYtdlWriteAutoSubs(it) }
            )

            SettingEditableTextCard(
                title = "Subtitle Languages",
                subtitle = "Comma-separated language codes (e.g. en,mr,hi). Empty fetches all.",
                value = playbackSettings.subtitleLanguages,
                placeholder = "all",
                onValueChange = { settingsViewModel.updateYtdlSubtitleLanguages(it) }
            )

            // Section 4: Network, Security & Proxies
            YtdlpSectionHeader(title = "Network & Proxy Settings", icon = Icons.Default.Security)

            SettingToggleCard(
                icon = Icons.Default.Public,
                title = "Geographic Bypass",
                subtitle = "Bypass location restrictions using extractor IP spoofing",
                checked = playbackSettings.geoBypass,
                onCheckedChange = { settingsViewModel.updateYtdlGeoBypass(it) }
            )

            SettingEditableTextCard(
                title = "User-Agent Header",
                subtitle = "Custom client identification to prevent platform blocks",
                value = playbackSettings.customUserAgent,
                placeholder = "Default User-Agent string",
                onValueChange = { settingsViewModel.updateYtdlCustomUserAgent(it) }
            )

            SettingEditableTextCard(
                title = "Http Referer",
                subtitle = "Specify origin referrer request header",
                value = playbackSettings.referer,
                placeholder = "https://example.com",
                onValueChange = { settingsViewModel.updateYtdlReferer(it) }
            )

            SettingEditableTextCard(
                title = "Cookies File Path",
                subtitle = "Absolute path to netscape format cookie text file",
                value = playbackSettings.cookiesFile,
                placeholder = "/storage/emulated/0/cookies.txt",
                onValueChange = { settingsViewModel.updateYtdlCookiesFile(it) }
            )

            SettingEditableTextCard(
                title = "Proxy Address",
                subtitle = "Proxy connection URL (e.g. socks5://127.0.0.1:1080)",
                value = playbackSettings.proxy,
                placeholder = "socks5://ip:port",
                onValueChange = { settingsViewModel.updateYtdlProxy(it) }
            )

            SettingEditableTextCard(
                title = "Extractor Arguments",
                subtitle = "Format: 'key:val' or multiple separated by commas",
                value = playbackSettings.extractorArgs,
                placeholder = "youtube:player_client=android",
                onValueChange = { settingsViewModel.updateYtdlExtractorArgs(it) }
            )

            // Section 5: SponsorBlock Integration
            YtdlpSectionHeader(title = "SponsorBlock Integration", icon = Icons.Default.Block)

            SettingEditableTextCard(
                title = "Mark Categories",
                subtitle = "Insert chapters for specific segments (comma-separated, e.g. sponsor,intro)",
                value = playbackSettings.sponsorBlockMark,
                placeholder = "sponsor,selfpromo",
                onValueChange = { settingsViewModel.updateYtdlSponsorBlockMark(it) }
            )

            SettingEditableTextCard(
                title = "Remove Categories",
                subtitle = "Automatically skip specific video categories during playback",
                value = playbackSettings.sponsorBlockRemove,
                placeholder = "sponsor",
                onValueChange = { settingsViewModel.updateYtdlSponsorBlockRemove(it) }
            )

            // Section 6: Advanced & Raw Options
            YtdlpSectionHeader(title = "Advanced Options", icon = Icons.Default.Build)

            SettingEditableTextCard(
                title = "Custom Format Selector String",
                subtitle = "Overrides all quality, container and codec selectors (advanced)",
                value = playbackSettings.ytdlFormat,
                placeholder = "bestvideo+bestaudio/best",
                onValueChange = { settingsViewModel.updateYtdlFormat(it) }
            )

            SettingEditableTextCard(
                title = "Merge Output Format",
                subtitle = "Force container file format when muxing (e.g. mkv, mp4)",
                value = playbackSettings.mergeOutputFormat,
                placeholder = "mkv",
                onValueChange = { settingsViewModel.updateYtdlMergeOutputFormat(it) }
            )

            SettingEditableTextCard(
                title = "Format Sort Order",
                subtitle = "Rules for prioritizing stream formats (e.g. res,fps,codec)",
                value = playbackSettings.formatSort,
                placeholder = "res,fps,codec",
                onValueChange = { settingsViewModel.updateYtdlFormatSort(it) }
            )

            SettingEditableTextCard(
                title = "Custom Raw Option Flags",
                subtitle = "Additional command-line parameters (e.g. --no-check-certificates)",
                value = playbackSettings.customRawOptions,
                placeholder = "--no-check-certificates",
                onValueChange = { settingsViewModel.updateYtdlCustomRawOptions(it) }
            )

            Spacer(Modifier.height(32.dp))
        }
    }

    if (showUpdateDialog) {
        AlertDialog(
            onDismissRequest = { showUpdateDialog = false },
            title = {
                Text(text = "Update Available", fontWeight = FontWeight.Bold)
            },
            text = {
                Text(text = "A new version of yt-dlp is available ($latestVersion). Your installed version is $installedVersion. Would you like to update now?")
            },
            confirmButton = {
                Button(
                    onClick = {
                        showUpdateDialog = false
                        if (!isRunningTask) {
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
                                checkStatus()
                            }
                        }
                    }
                ) {
                    Text("Update Now")
                }
            },
            dismissButton = {
                TextButton(onClick = { showUpdateDialog = false }) {
                    Text("Later")
                }
            }
        )
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
private fun SettingDropdownCard(
    title: String,
    subtitle: String,
    currentValue: String,
    isExpanded: Boolean,
    onExpandChange: (Boolean) -> Unit,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
    dropdownContent: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .then(if (enabled) Modifier.clickable { onExpandChange(!isExpanded) } else Modifier)
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
}

@Composable
private fun SettingEditableTextCard(
    title: String,
    subtitle: String,
    value: String,
    placeholder: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var textValue by remember(value) { mutableStateOf(value) }
    var isEditing by remember { mutableStateOf(false) }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .border(
                BorderStroke(
                    1.dp,
                    if (isEditing) MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                    else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                ),
                RoundedCornerShape(14.dp)
            ),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isEditing) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.12f)
            else MaterialTheme.colorScheme.surface
        )
    ) {
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
                        fontWeight = FontWeight.SemiBold,
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
}
