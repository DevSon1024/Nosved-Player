package com.devson.nvplayer.ui.screen.settings

import android.app.PendingIntent
import android.content.Intent
import android.os.Process
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Launch
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.devson.nvplayer.domain.model.DefaultScreen
import com.devson.nvplayer.viewmodel.SettingsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomHomeSettingsScreen(
    onNavigateBack: () -> Unit,
    settingsViewModel: SettingsViewModel = viewModel()
) {
    val context = LocalContext.current
    val viewSettings by settingsViewModel.viewSettings.collectAsState()
    val scrollState = rememberScrollState()

    var showDefaultScreenDialog by remember { mutableStateOf(false) }
    var showRestartDialog by remember { mutableStateOf(false) }

    // Restarts the app by relaunching MainActivity from scratch via PendingIntent
    val restartApp: () -> Unit = {
        val intent = context.packageManager.getLaunchIntentForPackage(context.packageName)!!
            .apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            }
        val pendingIntent = PendingIntent.getActivity(
            context, 0, intent,
            PendingIntent.FLAG_CANCEL_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        pendingIntent.send()
        Process.killProcess(Process.myPid())
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Custom Home",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = onNavigateBack,
                        modifier = Modifier.minimumInteractiveComponentSize()
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
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
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Spacer(Modifier.height(4.dp))

            // Home Cards Section
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                CustomHomeHeader("Home Screen Cards")
                SettingToggleCard(
                    icon = Icons.Default.History,
                    title = "Show Watch History Card",
                    subtitle = "Display 'Continue Watching' row for recently played videos",
                    checked = viewSettings.showHistoryCard,
                    onCheckedChange = { settingsViewModel.updateShowHistoryCard(it) }
                )

                SettingToggleCard(
                    icon = Icons.Default.VideoLibrary,
                    title = "Show Latest Videos Card",
                    subtitle = "Display a horizontal carousel of your newly added videos",
                    checked = viewSettings.showLatestVideos,
                    onCheckedChange = { settingsViewModel.updateShowLatestVideos(it) }
                )

                SettingToggleCard(
                    icon = Icons.Default.PieChart,
                    title = "Show Storage Tracking Card",
                    subtitle = "Display visual storage analyzer showing space statistics",
                    checked = viewSettings.showStorageTracker,
                    onCheckedChange = { settingsViewModel.updateShowStorageTracker(it) }
                )
            }

            // Quick Access & FAB Section
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                CustomHomeHeader("Quick Access Actions")
                SettingToggleCard(
                    icon = Icons.Default.SmartButton,
                    title = "Show Floating Action Button",
                    subtitle = "Floating menu button to scan storage, view tools or search",
                    checked = viewSettings.showQuickFab,
                    onCheckedChange = { settingsViewModel.updateShowQuickFab(it) }
                )

                if (viewSettings.showQuickFab) {
                    SettingToggleCard(
                        icon = Icons.Default.Visibility,
                        title = "Enable FAB Preview Option",
                        subtitle = "Allows long-press or swipe on FAB to preview tools",
                        checked = viewSettings.enableFabPreview,
                        onCheckedChange = { settingsViewModel.updateEnableFabPreview(it) }
                    )
                }
            }

            // Navigation Section
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                CustomHomeHeader("Startup Preference")
                SettingClickableCard(
                    icon = Icons.AutoMirrored.Filled.Launch,
                    title = "Default Launch Screen",
                    subtitle = when (viewSettings.defaultScreen) {
                        DefaultScreen.HOME -> "Home Screen (Dashboard)"
                        DefaultScreen.FOLDERS -> "Folders Screen"
                        DefaultScreen.HISTORY -> "Watch History List"
                        DefaultScreen.VIDEO_LIST -> "All Videos List"
                    },
                    onClick = { showDefaultScreenDialog = true }
                )
            }

            Spacer(Modifier.height(8.dp))
        }
    }

    if (showDefaultScreenDialog) {
        AlertDialog(
            onDismissRequest = { showDefaultScreenDialog = false },
            title = { Text("Default Launch Screen") },
            text = {
                Column(Modifier.selectableGroup()) {
                    listOf(DefaultScreen.HOME, DefaultScreen.VIDEO_LIST).forEach { screen ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp)
                                .selectable(
                                    selected = (viewSettings.defaultScreen == screen),
                                    onClick = {
                                        if (viewSettings.defaultScreen != screen) {
                                            settingsViewModel.updateDefaultScreen(screen)
                                            showDefaultScreenDialog = false
                                            showRestartDialog = true
                                        } else {
                                            showDefaultScreenDialog = false
                                        }
                                    },
                                    role = Role.RadioButton
                                )
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = (viewSettings.defaultScreen == screen),
                                onClick = null
                            )
                            Spacer(Modifier.width(16.dp))
                            Text(
                                text = when (screen) {
                                    DefaultScreen.HOME -> "Home Screen (Recommended)"
                                    DefaultScreen.FOLDERS -> "Folders Screen"
                                    DefaultScreen.HISTORY -> "Watch History Screen"
                                    DefaultScreen.VIDEO_LIST -> "All Videos Screen"
                                },
                                style = MaterialTheme.typography.bodyLarge
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showDefaultScreenDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showRestartDialog) {
        AlertDialog(
            onDismissRequest = { showRestartDialog = false },
            title = { Text("Restart Required") },
            text = {
                Text("The default launch screen has been changed. Restart the app now to apply this setting?")
            },
            confirmButton = {
                Button(onClick = restartApp) {
                    Text("Restart Now")
                }
            },
            dismissButton = {
                TextButton(onClick = { showRestartDialog = false }) {
                    Text("Later")
                }
            }
        )
    }
}

@Composable
private fun CustomHomeHeader(label: String) {
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
private fun SettingClickableCard(
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .then(if (enabled) Modifier.clickable { onClick() } else Modifier)
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
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}
