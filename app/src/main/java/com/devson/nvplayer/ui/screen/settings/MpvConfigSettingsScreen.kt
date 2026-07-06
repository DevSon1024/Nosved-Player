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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Help
import androidx.compose.material.icons.automirrored.filled.VolumeUp
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.devson.nvplayer.viewmodel.SettingsViewModel
import com.devson.nvplayer.ui.screen.editor.MpvScriptEditor
import com.devson.nvplayer.data.repository.MpvConfigRepository
import com.devson.nvplayer.data.repository.VisualMpvConfig
import com.devson.nvplayer.player.model.DecoderMode

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MpvConfigSettingsScreen(
    onNavigateBack: () -> Unit,
    onNavigateToHelp: () -> Unit,
    settingsViewModel: SettingsViewModel = viewModel()
) {
    val context = LocalContext.current
    val mpvConfigText by settingsViewModel.mpvConfigFlow.collectAsState()

    var showRawEditor by remember { mutableStateOf(false) }
    var configTextState by remember { mutableStateOf("") }
    var isLoaded by remember { mutableStateOf(false) }
    var currentVisualConfig by remember { mutableStateOf(VisualMpvConfig()) }

    var hwdecExpanded by remember { mutableStateOf(false) }
    var aoExpanded by remember { mutableStateOf(false) }
    var voExpanded by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        settingsViewModel.loadMpvConfig()
    }

    if (!isLoaded && mpvConfigText.isNotEmpty()) {
        configTextState = mpvConfigText
        currentVisualConfig = MpvConfigRepository.parseConfig(mpvConfigText)
        isLoaded = true
    }

    val toggleEditorMode = {
        if (showRawEditor) {
            currentVisualConfig = MpvConfigRepository.parseConfig(configTextState)
        } else {
            configTextState = MpvConfigRepository.serializeConfig(currentVisualConfig, mpvConfigText)
        }
        showRawEditor = !showRawEditor
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = if (showRawEditor) "Edit mpv.conf" else "mpv Engine Config",
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
                actions = {
                    IconButton(onClick = toggleEditorMode) {
                        Icon(
                            imageVector = if (showRawEditor) Icons.Default.Dashboard else Icons.Default.Code,
                            contentDescription = if (showRawEditor) "Switch to Dashboard" else "Switch to Raw Editor"
                        )
                    }
                    IconButton(onClick = onNavigateToHelp) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Help,
                            contentDescription = "Help & Documentation"
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

            if (!showRawEditor) {
                // Section: Decoding & Engine
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    MpvSectionHeader("Decoding & Engine Drivers")

                    SettingDropdownCard(
                        icon = Icons.Default.Hardware,
                        title = "Hardware Decoding",
                        subtitle = "Decoder mode used by the engine (hwdec)",
                        currentValue = currentVisualConfig.hwdec.displayName,
                        isExpanded = hwdecExpanded,
                        onExpandChange = { hwdecExpanded = it }
                    ) {
                        DecoderMode.entries.forEach { mode ->
                            val desc = when (mode) {
                                DecoderMode.AUTO -> "Auto-detect best hardware decoder"
                                DecoderMode.HW -> "Standard MediaCodec (hardware)"
                                DecoderMode.HW_PLUS -> "MediaCodec Copy-Back (allows filters)"
                                DecoderMode.SW -> "Software rendering fallback (no acceleration)"
                            }
                            DropdownMenuItem(
                                text = {
                                    Column {
                                        Text(mode.displayName, fontWeight = FontWeight.Bold)
                                        Text(desc, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                },
                                onClick = {
                                    currentVisualConfig = currentVisualConfig.copy(hwdec = mode)
                                    hwdecExpanded = false
                                }
                            )
                        }
                    }

                    SettingDropdownCard(
                        icon = Icons.Default.Tv,
                        title = "Video Output Driver",
                        subtitle = "Rendering engine driver (vo)",
                        currentValue = currentVisualConfig.videoOutput.uppercase(),
                        isExpanded = voExpanded,
                        onExpandChange = { voExpanded = it }
                    ) {
                        listOf("gpu", "gpu-next").forEach { driver ->
                            val desc = when (driver) {
                                "gpu" -> "Standard OpenGL/Vulkan rendering"
                                "gpu-next" -> "Next-gen rendering (required for advanced shaders)"
                                else -> ""
                            }
                            DropdownMenuItem(
                                text = {
                                    Column {
                                        Text(driver.uppercase(), fontWeight = FontWeight.Bold)
                                        Text(desc, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                },
                                onClick = {
                                    currentVisualConfig = currentVisualConfig.copy(videoOutput = driver)
                                    voExpanded = false
                                }
                            )
                        }
                    }

                    SettingDropdownCard(
                        icon = Icons.AutoMirrored.Filled.VolumeUp,
                        title = "Audio Output Driver",
                        subtitle = "Native audio output driver (ao)",
                        currentValue = currentVisualConfig.audioOutput.uppercase(),
                        isExpanded = aoExpanded,
                        onExpandChange = { aoExpanded = it }
                    ) {
                        listOf("aaudio", "opensles").forEach { driver ->
                            val desc = when (driver) {
                                "aaudio" -> "Android native AAudio API (recommended)"
                                "opensles" -> "OpenSL ES (legacy compatibility fallback)"
                                else -> ""
                            }
                            DropdownMenuItem(
                                text = {
                                    Column {
                                        Text(driver.uppercase(), fontWeight = FontWeight.Bold)
                                        Text(desc, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                },
                                onClick = {
                                    currentVisualConfig = currentVisualConfig.copy(audioOutput = driver)
                                    aoExpanded = false
                                }
                            )
                        }
                    }
                }

                // Section: Video Processing Quality
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    MpvSectionHeader("Video Processing Shaders")

                    SettingToggleCard(
                        icon = Icons.Default.HighQuality,
                        title = "High Quality Profile",
                        subtitle = "Enable high-quality scaling and rendering presets (profile)",
                        checked = currentVisualConfig.isHighQualityProfile,
                        onCheckedChange = {
                            currentVisualConfig = currentVisualConfig.copy(isHighQualityProfile = it)
                        }
                    )

                    SettingToggleCard(
                        icon = Icons.Default.MotionPhotosOn,
                        title = "Smooth Motion (Interpolation)",
                        subtitle = "Reduce jitter via frame oversampling (requires vo=gpu/gpu-next)",
                        checked = currentVisualConfig.isInterpolationEnabled,
                        onCheckedChange = {
                            currentVisualConfig = currentVisualConfig.copy(isInterpolationEnabled = it)
                        }
                    )

                    SettingToggleCard(
                        icon = Icons.Default.BlurOn,
                        title = "Video Debanding",
                        subtitle = "Reduce color banding artifacts in dark/gradient scenes",
                        checked = currentVisualConfig.isDebandingEnabled,
                        onCheckedChange = {
                            currentVisualConfig = currentVisualConfig.copy(isDebandingEnabled = it)
                        }
                    )

                    SettingToggleCard(
                        icon = Icons.Default.GridOn,
                        title = "Deinterlacing Filter",
                        subtitle = "Removes interlaced comb line artifacts from video feed",
                        checked = currentVisualConfig.deinterlace,
                        onCheckedChange = {
                            currentVisualConfig = currentVisualConfig.copy(deinterlace = it)
                        }
                    )
                }

                // Section: Diagnostics & Overlays
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    MpvSectionHeader("Diagnostics")

                    SettingToggleCard(
                        icon = Icons.Default.QueryStats,
                        title = "Show Performance Overlay",
                        subtitle = "Displays live FPS, frame drops, and CPU/GPU load overlay",
                        checked = currentVisualConfig.showPerformanceOverlay,
                        onCheckedChange = {
                            currentVisualConfig = currentVisualConfig.copy(showPerformanceOverlay = it)
                        }
                    )
                }

                // Action Buttons
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Button(
                        onClick = {
                            val serialized = MpvConfigRepository.serializeConfig(currentVisualConfig, mpvConfigText)
                            settingsViewModel.saveMpvConfig(serialized) { success ->
                                if (success) {
                                    Toast.makeText(context, "Configuration saved successfully", Toast.LENGTH_SHORT).show()
                                } else {
                                    Toast.makeText(context, "Failed to save configuration", Toast.LENGTH_LONG).show()
                                }
                            }
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        )
                    ) {
                        Icon(Icons.Default.Save, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Save Settings", fontWeight = FontWeight.SemiBold)
                    }

                    OutlinedButton(
                        onClick = {
                            currentVisualConfig = VisualMpvConfig()
                            Toast.makeText(context, "Settings reset to defaults (Click Save to apply)", Toast.LENGTH_LONG).show()
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.error
                        ),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.5f))
                    ) {
                        Icon(Icons.Default.Restore, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Reset Dashboard", fontWeight = FontWeight.SemiBold)
                    }
                }
            } else {
                // Warning Banner
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.15f)
                    ),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.3f))
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.Top,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = "Warning",
                            tint = MaterialTheme.colorScheme.error
                        )
                        Column {
                            Text(
                                text = "Warning: Advanced Options Only",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = "Invalid configuration values in mpv.conf can cause player malfunctions, audio glitches, or unexpected app crashes. Apply presets or edit with care. \n\n Lines starting with # are comments.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                // Code Editor Label
                Text(
                    text = "Edit mpv.conf Text",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(start = 4.dp)
                )

                // Text Editor Container
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(360.dp),
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                    tonalElevation = 1.dp
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(vertical = 12.dp, horizontal = 4.dp)
                    ) {
                        MpvScriptEditor(
                            content = configTextState,
                            onContentChange = { configTextState = it },
                            language = "mpv.conf",
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }

                // Action Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Button(
                        onClick = {
                            settingsViewModel.saveMpvConfig(configTextState) { success ->
                                if (success) {
                                    Toast.makeText(context, "Configuration saved successfully", Toast.LENGTH_SHORT).show()
                                } else {
                                    Toast.makeText(context, "Failed to save configuration", Toast.LENGTH_LONG).show()
                                }
                            }
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        )
                    ) {
                        Icon(Icons.Default.Save, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Save Config", fontWeight = FontWeight.SemiBold)
                    }

                    OutlinedButton(
                        onClick = {
                            val defaultText = """
                                # Video Output Settings
                                vo=gpu
                                gpu-context=android

                                # Audio Output Settings
                                ao=aaudio

                                # Hardware Decoding
                                hwdec=mediacodec

                                # Other Settings
                                profile=fast
                                keep-open=yes
                            """.trimIndent()
                            configTextState = defaultText
                            Toast.makeText(context, "Reset to default template (Click save to apply)", Toast.LENGTH_LONG).show()
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.error
                        ),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.5f))
                    ) {
                        Icon(Icons.Default.Restore, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Reset Defaults", fontWeight = FontWeight.SemiBold)
                    }
                }

                Spacer(Modifier.height(8.dp))

                // Quick Insertion Presets List
                Text(
                    text = "Quick Presets",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(start = 4.dp)
                )

                Text(
                    text = "Tap a preset to insert it into your configuration editor.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 4.dp)
                )

                // Presets Cards
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    val presets = listOf(
                        MpvPreset("vo=gpu-next", "GPU Next Video Output", "Recommended for next-generation rendering shaders", Icons.Default.Tv),
                        MpvPreset("ao=aaudio", "AAudio Driver", "Modern high-performance Android native audio API", Icons.AutoMirrored.Filled.VolumeUp),
                        MpvPreset("ao=opensles", "OpenSL ES Driver", "Standard legacy audio API fallback", Icons.Default.Audiotrack),
                        MpvPreset("hwdec=mediacodec-copy", "MediaCodec Copy-Back", "Decodes in hardware, copies back to RAM (allows filters)", Icons.Default.Hardware),
                        MpvPreset("profile=fast", "Fast Performance Profile", "Disables heavy parameters to save battery and reduce CPU/GPU load", Icons.Default.Bolt),
                        MpvPreset("cache=yes", "Stream Caching Enabled", "Pre-buffers online videos to prevent buffering pauses", Icons.Default.Cached),
                        MpvPreset("deinterlace=yes", "Deinterlacing Active", "Removes scan lines/artifacts from interlaced video sources", Icons.Default.GridOn)
                    )

                    presets.forEach { preset ->
                        PresetItemCard(preset = preset) {
                            val separator = if (configTextState.endsWith("\n") || configTextState.isEmpty()) "" else "\n"
                            configTextState += separator + preset.code
                            Toast.makeText(context, "Added: ${preset.code}", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun MpvSectionHeader(label: String) {
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

private data class MpvPreset(
    val code: String,
    val title: String,
    val description: String,
    val icon: ImageVector
)

@Composable
private fun PresetItemCard(
    preset: MpvPreset,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable { onClick() }
            .border(
                BorderStroke(
                    1.dp,
                    MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                ),
                RoundedCornerShape(12.dp)
            ),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.secondaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = preset.icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.size(20.dp)
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = preset.title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = preset.code,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = preset.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = "Add preset",
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
