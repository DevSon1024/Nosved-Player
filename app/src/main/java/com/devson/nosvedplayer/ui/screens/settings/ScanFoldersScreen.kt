package com.devson.nosvedplayer.ui.screens.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import com.devson.nosvedplayer.viewmodel.SettingsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScanFoldersScreen(
    onBack: () -> Unit,
    settingsViewModel: SettingsViewModel
) {
    val viewSettings by settingsViewModel.viewSettings.collectAsState()
    val folders = viewSettings.scanFoldersList.toList()
    
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            val newSet = viewSettings.scanFoldersList.toMutableSet()
            newSet.add(resolveDocumentUriToPath(uri))
            settingsViewModel.updateScanFoldersList(newSet)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Scan Folders", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = {
                        settingsViewModel.updateScanFoldersList(setOf("/storage", "/storage/emulated/0"))
                    }) {
                        Icon(Icons.Default.Restore, contentDescription = "Restore Defaults")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { launcher.launch(null) }) {
                Icon(Icons.Default.Add, contentDescription = "Add Folder")
            }
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            items(folders) { folder ->
                val isHidden = folder.startsWith("HIDDEN:")
                val displayFolder = if (isHidden) folder.removePrefix("HIDDEN:") else folder

                ListItem(
                    headlineContent = { 
                        Text(
                            text = displayFolder,
                            textDecoration = if (isHidden) TextDecoration.LineThrough else null,
                            color = if (isHidden) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f) else MaterialTheme.colorScheme.onSurface
                        ) 
                    },
                    trailingContent = {
                        Row {
                            IconButton(onClick = {
                                val newSet = viewSettings.scanFoldersList.toMutableSet()
                                newSet.remove(folder)
                                newSet.add(if (isHidden) displayFolder else "HIDDEN:$displayFolder")
                                settingsViewModel.updateScanFoldersList(newSet)
                            }) {
                                Icon(if (isHidden) Icons.Default.Visibility else Icons.Default.VisibilityOff, contentDescription = "Toggle Hide")
                            }
                            IconButton(onClick = {
                                val newSet = viewSettings.scanFoldersList.toMutableSet()
                                newSet.remove(folder)
                                settingsViewModel.updateScanFoldersList(newSet)
                            }) {
                                Icon(Icons.Default.Delete, contentDescription = "Remove")
                            }
                        }
                    }
                )
                HorizontalDivider()
            }
        }
    }
}

private fun resolveDocumentUriToPath(uri: android.net.Uri): String {
    val path = uri.path ?: return uri.toString()
    val parts = path.split(":")
    if (parts.size < 2) return uri.toString()
    val volumeId = parts[0].substringAfterLast("/tree/")
    val folderPath = parts[1]
    return if (volumeId == "primary") {
        "/storage/emulated/0/$folderPath"
    } else {
        "/storage/$volumeId/$folderPath"
    }
}
