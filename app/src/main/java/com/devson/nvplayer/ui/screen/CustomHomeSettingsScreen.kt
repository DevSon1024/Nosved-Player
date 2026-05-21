package com.devson.nvplayer.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.devson.nvplayer.model.DefaultScreen
import com.devson.nvplayer.viewmodel.SettingsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomHomeSettingsScreen(
    onNavigateBack: () -> Unit,
    settingsViewModel: SettingsViewModel = viewModel()
) {
    val viewSettings by settingsViewModel.viewSettings.collectAsState()
    val scrollState = rememberScrollState()

    var showDefaultScreenDialog by remember { mutableStateOf(false) }

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
                    containerColor = MaterialTheme.colorScheme.background
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
                .padding(horizontal = 16.dp)
        ) {
            Spacer(Modifier.height(8.dp))

            // Home Cards Section
            CustomHomeHeader("Home Screen Cards")
            CustomHomeCard {
                CustomHomeToggleRow(
                    icon = Icons.Default.History,
                    title = "Show Watch History Card",
                    subtitle = "Display 'Continue Watching' row for recently played videos",
                    checked = viewSettings.showHistoryCard,
                    onCheckedChange = { settingsViewModel.updateShowHistoryCard(it) }
                )

                CustomHomeDivider()

                CustomHomeToggleRow(
                    icon = Icons.Default.VideoLibrary,
                    title = "Show Video List Card",
                    subtitle = "Display recent videos and folders directly on home screen",
                    checked = viewSettings.showVideoCard,
                    onCheckedChange = { settingsViewModel.updateShowVideoCard(it) }
                )

                CustomHomeDivider()

                CustomHomeToggleRow(
                    icon = Icons.Default.PieChart,
                    title = "Show Storage Tracking Card",
                    subtitle = "Display visual storage analyzer showing space statistics",
                    checked = viewSettings.showStorageTracker,
                    onCheckedChange = { settingsViewModel.updateShowStorageTracker(it) }
                )
            }

            Spacer(Modifier.height(20.dp))

            // Quick Access & FAB Section
            CustomHomeHeader("Quick Access Actions")
            CustomHomeCard {
                CustomHomeToggleRow(
                    icon = Icons.Default.SmartButton,
                    title = "Show Floating Action Button",
                    subtitle = "Floating menu button to scan storage, view tools or search",
                    checked = viewSettings.showFloatingButton,
                    onCheckedChange = { settingsViewModel.updateShowFloatingButton(it) }
                )

                if (viewSettings.showFloatingButton) {
                    CustomHomeDivider()

                    CustomHomeToggleRow(
                        icon = Icons.Default.Visibility,
                        title = "Enable FAB Preview Option",
                        subtitle = "Allows long-press or swipe on FAB to preview tools",
                        checked = viewSettings.enableFabPreview,
                        onCheckedChange = { settingsViewModel.updateEnableFabPreview(it) }
                    )
                }
            }

            Spacer(Modifier.height(20.dp))

            // Navigation Section
            CustomHomeHeader("Startup Preference")
            CustomHomeCard {
                CustomHomeRow(
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

            Spacer(Modifier.height(24.dp))
        }
    }

    if (showDefaultScreenDialog) {
        AlertDialog(
            onDismissRequest = { showDefaultScreenDialog = false },
            title = { Text("Default Launch Screen") },
            text = {
                Column(Modifier.selectableGroup()) {
                    DefaultScreen.values().forEach { screen ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp)
                                .selectable(
                                    selected = (viewSettings.defaultScreen == screen),
                                    onClick = {
                                        settingsViewModel.updateDefaultScreen(screen)
                                        showDefaultScreenDialog = false
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
private fun CustomHomeCard(content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        )
    ) {
        Column(content = content)
    }
}

@Composable
private fun CustomHomeDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(start = 56.dp),
        color = MaterialTheme.colorScheme.outlineVariant
    )
}

@Composable
private fun CustomHomeRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(8.dp))
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

        Icon(
            imageVector = Icons.Default.ChevronRight,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun CustomHomeToggleRow(
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
                .clip(RoundedCornerShape(8.dp))
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

        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = Modifier.minimumInteractiveComponentSize()
        )
    }
}
