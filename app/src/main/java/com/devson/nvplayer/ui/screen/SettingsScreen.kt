package com.devson.nvplayer.ui.screen

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.viewmodel.compose.viewModel
import com.devson.nvplayer.BuildConfig
import com.devson.nvplayer.R
import com.devson.nvplayer.viewmodel.SettingsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onNavigateToAbout: () -> Unit,
    onNavigateToLogs: () -> Unit,
    onNavigateToPrivacyPolicy: () -> Unit,
    onNavigateToAppearance: () -> Unit = {},
    onNavigateToListOption: () -> Unit = {},
    onNavigateToScanFolders: () -> Unit = {},
    onNavigateToTool: () -> Unit = {},
    onNavigateToRecycleBin: () -> Unit = {},
    onNavigateToPlayerInterface: () -> Unit = {},
    onNavigateToCustomHome: () -> Unit = {},
    onNavigateToGestures: () -> Unit = {},
    onNavigateToYtdlpSettings: () -> Unit = {},
    onNavigateToMpvConfig: () -> Unit = {},
    settingsViewModel: SettingsViewModel = viewModel()
) {
    val isDeveloperMode  by settingsViewModel.isDeveloperMode.collectAsState()

    val settingsAppearance = stringResource(R.string.settings_appearance)
    val settingsPlayer = stringResource(R.string.settings_player)
    val settingsApp = stringResource(R.string.settings_app)
    val settingsDeveloper = stringResource(R.string.settings_developer)

    // Version tap easter-egg for developer mode
    var tapCount      by remember { mutableIntStateOf(0) }
    var lastTapTime   by remember { mutableLongStateOf(0L) }

    // Read real version from PackageManager
    val context = LocalContext.current
    val unknownText = stringResource(R.string.unknown)
    val versionName = remember(context, unknownText) {
        runCatching {
            val info = context.packageManager.getPackageInfo(context.packageName, 0)
            info.versionName ?: unknownText
        }.getOrDefault(unknownText)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(R.string.settings_title),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
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
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = paddingValues.calculateTopPadding()),
            contentPadding = PaddingValues(
                start = 16.dp,
                top = 0.dp,
                end = 16.dp,
                bottom = paddingValues.calculateBottomPadding() + 16.dp
            )
        ) {
            item {
                Spacer(Modifier.height(4.dp))
                // App identity card
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
            }

            // APPEARANCE
            settingsSection(settingsAppearance) {
                item {
                    SettingsGroupCard {
                        SettingsItemRow(
                            icon = Icons.Default.Palette,
                            title = stringResource(R.string.settings_display),
                            subtitle = stringResource(R.string.settings_display_desc),
                            onClick = onNavigateToAppearance
                        )
                    }
                }
            }

            // Player Section
            settingsSection(settingsPlayer) {
                item {
                    SettingsGroupCard {
                        SettingsItemRow(
                            icon = Icons.Default.Swipe,
                            title = "Gestures & Taps",
                            subtitle = "Customize swipe and tap behaviors",
                            onClick = onNavigateToGestures
                        )
                        HorizontalDivider(
                            modifier = Modifier.padding(start = 72.dp),
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                        )
                        SettingsItemRow(
                            icon = Icons.Default.Folder,
                            title = "Folders",
                            subtitle = stringResource(R.string.settings_about_folders),
                            onClick = onNavigateToScanFolders
                        )
                        HorizontalDivider(
                            modifier = Modifier.padding(start = 72.dp),
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                        )
                        SettingsItemRow(
                            icon = Icons.Default.Tune,
                            title = "Player Interface",
                            subtitle = "Manage player layout and visibility",
                            onClick = onNavigateToPlayerInterface
                        )
                        HorizontalDivider(
                            modifier = Modifier.padding(start = 72.dp),
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                        )
                        SettingsItemRow(
                            icon = Icons.Default.Home,
                            title = "Custom Home",
                            subtitle = "Customize Home Screen Layout",
                            onClick = onNavigateToCustomHome
                        )
                        HorizontalDivider(
                            modifier = Modifier.padding(start = 72.dp),
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                        )
                        SettingsItemRow(
                            icon = Icons.Default.Cloud,
                            title = "yt-dlp Streaming",
                            subtitle = "Configure format preferences and environment",
                            onClick = onNavigateToYtdlpSettings
                        )
                        HorizontalDivider(
                            modifier = Modifier.padding(start = 72.dp),
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                        )
                        SettingsItemRow(
                            icon = Icons.Default.Settings,
                            title = "mpv.conf Configuration",
                            subtitle = "Customize raw options (vo, ao, hwdec, etc.)",
                            onClick = onNavigateToMpvConfig
                        )
                    }
                }
            }

            // Tools Section
            settingsSection("Tools") {
                item {
                    SettingsGroupCard {
                        SettingsItemRow(
                            icon = Icons.Default.Build,
                            title = "Media Tools",
                            subtitle = "Timestamp converter, video editor and more",
                            onClick = onNavigateToTool
                        )
                        HorizontalDivider(
                            modifier = Modifier.padding(start = 72.dp),
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                        )
                        SettingsItemRow(
                            icon = Icons.Default.Delete,
                            title = "Recycle Bin",
                            subtitle = "Manage deleted videos",
                            onClick = onNavigateToRecycleBin
                        )
                    }
                }
            }

            // App Section
            settingsSection(settingsApp) {
                item {
                    SettingsGroupCard {
                        SettingsItemRow(
                            icon = Icons.Default.Info,
                            title = stringResource(R.string.settings_about),
                            subtitle = stringResource(R.string.settings_about_desc),
                            onClick = onNavigateToAbout
                        )
                        HorizontalDivider(
                            modifier = Modifier.padding(start = 72.dp),
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                        )
                        SettingsItemRow(
                            icon = Icons.Default.Policy,
                            title = stringResource(R.string.settings_privacy),
                            subtitle = stringResource(R.string.settings_privacy_desc),
                            onClick = onNavigateToPrivacyPolicy
                        )
                    }
                }
            }

            // Developer Section
            if (isDeveloperMode) {
                settingsSection(settingsDeveloper) {
                    item {
                        SettingsGroupCard {
                            SettingsItemRow(
                                icon = Icons.Default.Code,
                                title = stringResource(R.string.settings_error_logs),
                                subtitle = stringResource(R.string.settings_error_logs_desc),
                                onClick = onNavigateToLogs
                            )
                        }
                    }
                }
            }
        }
    }
}

fun LazyListScope.settingsSection(
    title: String,
    content: LazyListScope.() -> Unit
) {
    item {
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(start = 8.dp, top = 16.dp, bottom = 8.dp)
        )
    }
    content()
    item {
        Spacer(modifier = Modifier.height(12.dp))
    }
}

@Composable
fun SettingsGroupCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp)),
            content = content
        )
    }
}

@Composable
fun SettingsItemRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
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
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Icon(
            imageVector = Icons.Default.ChevronRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp)
        )
    }
}

@Composable
private fun AppIdentityCard(
    versionName: String,
    onVersionTap: () -> Unit = {}
) {
    Card(
        modifier       = Modifier.fillMaxWidth(),
        shape          = RoundedCornerShape(20.dp),
        colors          = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
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
                    text       = stringResource(R.string.app_name),
                    style      = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color      = MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    text  = stringResource(R.string.settings_version, versionName),
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
                    text      = if (BuildConfig.DEBUG) "DEBUG" else stringResource(R.string.settings_stable),
                    style     = MaterialTheme.typography.labelSmall,
                    color     = if (BuildConfig.DEBUG) MaterialTheme.colorScheme.onTertiaryContainer else MaterialTheme.colorScheme.onPrimaryContainer,
                    fontWeight = FontWeight.Bold,
                    modifier  = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
        }
    }
}