package com.devson.nosvedplayer.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.devson.nosvedplayer.BuildConfig
import com.devson.nosvedplayer.viewmodel.SettingsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onNavigateToAbout: () -> Unit,
    onNavigateToLogs: () -> Unit,
    onNavigateToPrivacyPolicy: () -> Unit,
    onNavigateToAppearance: () -> Unit = {},
    settingsViewModel: SettingsViewModel = viewModel()
) {
    val isDark           by settingsViewModel.isDarkTheme.collectAsState()
    val useYoutubeStyle  by settingsViewModel.useYoutubePlayerStyle.collectAsState()
    val isDeveloperMode  by settingsViewModel.isDeveloperMode.collectAsState()

    // Version tap easter-egg for developer mode
    var tapCount      by remember { mutableIntStateOf(0) }
    var lastTapTime   by remember { mutableLongStateOf(0L) }

    // Read real version from PackageManager
    val context = LocalContext.current
    val versionName = remember(context) {
        runCatching {
            val info = context.packageManager.getPackageInfo(context.packageName, 0)
            info.versionName ?: "Unknown"
        }.getOrDefault("Unknown")
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Settings",
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(padding)
                .padding(horizontal = 16.dp)
                .padding(bottom = 32.dp)
        ) {
            Spacer(Modifier.height(4.dp))

            //  App identity card 
            AppIdentityCard(
                versionName = versionName,
                onVersionTap = {
                    if (!isDeveloperMode) {
                        val now = System.currentTimeMillis()
                        if (now - lastTapTime > 600) tapCount = 1 else tapCount++
                        lastTapTime = now
                        if (tapCount >= 8) {
                            settingsViewModel.enableDeveloperMode()
                            tapCount = 0
                        }
                    }
                }
            )

            Spacer(Modifier.height(24.dp))

            //  APPEARANCE 
            SettingsSectionLabel("Appearance")
            SettingsCard {
                SettingsRow(
                    icon     = Icons.Default.Palette,
                    title    = "Display",
                    subtitle = "Theme, dynamic colour & language",
                    onClick  = onNavigateToAppearance
                )
            }

            Spacer(Modifier.height(16.dp))

            //  PLAYER 
            SettingsSectionLabel("Player")
            SettingsCard {
                SettingsToggleRow(
                    icon     = Icons.Default.PlayCircleOutline,
                    title    = "YouTube-style controls",
                    subtitle = if (useYoutubeStyle)
                        "Inline settings panel active"
                    else
                        "Bottom sheet settings active",
                    checked  = useYoutubeStyle,
                    onCheckedChange = { settingsViewModel.setYoutubePlayerStyle(it) }
                )
            }

            Spacer(Modifier.height(16.dp))

            //  APP 
            SettingsSectionLabel("App")
            SettingsCard {
                SettingsRow(
                    icon     = Icons.Default.Info,
                    title    = "About Nosved Player",
                    subtitle = "Version, credits & open-source libraries",
                    onClick  = onNavigateToAbout
                )
                SettingsDivider()
                SettingsRow(
                    icon     = Icons.Default.Policy,
                    title    = "Privacy Policy",
                    subtitle = "How we handle permissions & your data",
                    onClick  = onNavigateToPrivacyPolicy
                )
            }

            //  DEVELOPER (hidden until unlocked) 
            if (isDeveloperMode) {
                Spacer(Modifier.height(16.dp))
                SettingsSectionLabel("Developer")
                SettingsCard {
                    SettingsRow(
                        icon     = Icons.Default.Code,
                        title    = "Error Logs",
                        subtitle = "View app and playback errors",
                        onClick  = onNavigateToLogs
                    )
                }
            }
        }
    }
}

//  App identity header card 

@Composable
private fun AppIdentityCard(
    versionName: String,
    onVersionTap: () -> Unit = {}
) {
    Surface(
        modifier       = Modifier.fillMaxWidth(),
        shape          = RoundedCornerShape(20.dp),
        color          = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 3.dp
    ) {
        Row(
            modifier            = Modifier
                .fillMaxWidth()
                .clickable { onVersionTap() }
                .padding(horizontal = 20.dp, vertical = 20.dp),
            verticalAlignment   = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Gradient app logo circle
            Box(
                modifier = Modifier
                    .size(68.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.linearGradient(
                            colors = listOf(
                                MaterialTheme.colorScheme.primary,
                                MaterialTheme.colorScheme.secondary
                            )
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector     = Icons.Default.OndemandVideo,
                    contentDescription = null,
                    modifier        = Modifier.size(36.dp),
                    tint            = MaterialTheme.colorScheme.onPrimary
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text       = "Nosved Player",
                    style      = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color      = MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    text  = "Version $versionName",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Subtle badge
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = if (BuildConfig.DEBUG) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.padding(end = 2.dp)
            ) {
                Text(
                    text      = if (BuildConfig.DEBUG) "DEBUG" else "Stable",
                    style     = MaterialTheme.typography.labelSmall,
                    color     = if (BuildConfig.DEBUG) MaterialTheme.colorScheme.onTertiaryContainer else MaterialTheme.colorScheme.onPrimaryContainer,
                    fontWeight = FontWeight.Bold,
                    modifier  = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
        }
    }
}

//  Section label 

@Composable
private fun SettingsSectionLabel(label: String) {
    Text(
        text       = label,
        style      = MaterialTheme.typography.labelLarge,
        color      = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.SemiBold,
        modifier   = Modifier.padding(start = 4.dp, bottom = 6.dp)
    )
}

//  Rounded card wrapper 

@Composable
private fun SettingsCard(content: @Composable ColumnScope.() -> Unit) {
    Surface(
        modifier       = Modifier.fillMaxWidth(),
        shape          = RoundedCornerShape(16.dp),
        color          = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 2.dp
    ) {
        Column(content = content)
    }
}

//  Thin in-card divider 

@Composable
private fun SettingsDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(start = 56.dp),
        color    = MaterialTheme.colorScheme.outlineVariant
    )
}

//  Clickable row with chevron 

@Composable
private fun SettingsRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Row(
        modifier            = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment   = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Icon in tinted circle
        Box(
            modifier          = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.secondaryContainer),
            contentAlignment  = Alignment.Center
        ) {
            Icon(
                imageVector     = icon,
                contentDescription = null,
                modifier        = Modifier.size(20.dp),
                tint            = MaterialTheme.colorScheme.onSecondaryContainer
            )
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.bodyLarge)
            Text(
                text  = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Icon(
            imageVector     = Icons.AutoMirrored.Filled.ArrowForwardIos,
            contentDescription = null,
            modifier        = Modifier.size(16.dp),
            tint            = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

//  Toggle row 

@Composable
private fun SettingsToggleRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier            = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment   = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Box(
            modifier          = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.secondaryContainer),
            contentAlignment  = Alignment.Center
        ) {
            Icon(
                imageVector     = icon,
                contentDescription = null,
                modifier        = Modifier.size(20.dp),
                tint            = MaterialTheme.colorScheme.onSecondaryContainer
            )
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.bodyLarge)
            Text(
                text  = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Switch(
            checked         = checked,
            onCheckedChange = onCheckedChange
        )
    }
}