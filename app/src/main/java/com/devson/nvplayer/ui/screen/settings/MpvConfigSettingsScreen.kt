package com.devson.nvplayer.ui.screen.settings

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.automirrored.filled.Help
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.devson.nvplayer.viewmodel.SettingsViewModel
import com.devson.nvplayer.ui.screen.editor.MpvScriptEditor

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MpvConfigSettingsScreen(
    onNavigateBack: () -> Unit,
    onNavigateToHelp: () -> Unit,
    settingsViewModel: SettingsViewModel = viewModel()
) {
    val context = LocalContext.current
    val mpvConfigText by settingsViewModel.mpvConfigFlow.collectAsState()

    var configTextState by remember { mutableStateOf("") }
    var isLoaded by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        settingsViewModel.loadMpvConfig()
    }

    // Update text state once config text is loaded from repository
    if (!isLoaded && mpvConfigText.isNotEmpty()) {
        configTextState = mpvConfigText
        isLoaded = true
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "mpv.conf Configuration",
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
                    IconButton(onClick = onNavigateToHelp) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Help,
                            contentDescription = "Help & Documentation"
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
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Warning Banner
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.15f)
                ),
                border = borderStroke(MaterialTheme.colorScheme.error.copy(alpha = 0.3f))
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
                text = "Edit mpv.conf",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )

            // Text Editor Container
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(360.dp),
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                ),
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
                    border = borderStroke(MaterialTheme.colorScheme.error.copy(alpha = 0.5f))
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
                color = MaterialTheme.colorScheme.primary
            )
            
            Text(
                text = "Tap a preset to insert it into your configuration editor.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
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
                    MpvPreset("hwdec=mediacodec-copy", "MediaCodec Copy-Back", "Decodes in hardware, copies back to RAM (slower, but allows filters)", Icons.Default.Hardware),
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

            Spacer(Modifier.height(16.dp))
        }
    }
}

// Helper to compile border stroke safely without relying on androidx.compose.foundation.BorderStroke in a non-standard way
@Composable
private fun borderStroke(color: Color) = androidx.compose.foundation.BorderStroke(1.dp, color)

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
            .clickable { onClick() },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
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
                    .clip(RoundedCornerShape(8.dp))
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
