package com.devson.nosvedplayer.ui.screens

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.devson.nosvedplayer.R
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.devson.nosvedplayer.ui.components.CustomRenameDialog
import com.devson.nosvedplayer.viewmodel.FileOperationsViewModel
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StorageExplorerScreen(
    operationType: String, // "MOVE" or "COPY"
    sourceUris: List<Uri>,
    onComplete: () -> Unit,
    onCancel: () -> Unit,
    fileOpsViewModel: FileOperationsViewModel = viewModel()
) {
    val context = LocalContext.current
    
    // Permission State Handling
    var hasManageStoragePermission by remember {
        mutableStateOf(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                Environment.isExternalStorageManager()
            } else {
                ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
            }
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasManageStoragePermission = granted
    }

    val manageActionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            hasManageStoragePermission = Environment.isExternalStorageManager()
        }
    }

    LaunchedEffect(Unit) {
        if (!hasManageStoragePermission) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                try {
                    val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                        data = Uri.parse("package:${context.packageName}")
                    }
                    manageActionLauncher.launch(intent)
                } catch (e: Exception) {
                    val intent = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                    manageActionLauncher.launch(intent)
                }
            } else {
                permissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            }
        }
    }

    if (!hasManageStoragePermission) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(16.dp)) {
                val opName = if (operationType == "MOVE") stringResource(R.string.storage_move) else stringResource(R.string.storage_copy)
                Text(stringResource(R.string.storage_permission_required, opName), style = MaterialTheme.typography.bodyLarge)
                Spacer(modifier = Modifier.height(16.dp))
                Button(onClick = {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        try {
                            val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                                data = Uri.parse("package:${context.packageName}")
                            }
                            manageActionLauncher.launch(intent)
                        } catch (e: Exception) {
                            val intent = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                            manageActionLauncher.launch(intent)
                        }
                    } else {
                        permissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                    }
                }) {
                    Text(stringResource(R.string.storage_grant_permission))
                }
                Spacer(modifier = Modifier.height(8.dp))
                TextButton(onClick = onCancel) {
                    Text(stringResource(R.string.history_cancel_button))
                }
            }
        }
        return
    }

    var currentDirectory by remember { mutableStateOf(Environment.getExternalStorageDirectory()) }
    var folders by remember { mutableStateOf(emptyList<File>()) }
    
    var showCreateFolderDialog by remember { mutableStateOf(false) }

    fun refreshFolders() {
        if (currentDirectory.exists() && currentDirectory.isDirectory) {
            val list = currentDirectory.listFiles()?.filter { it.isDirectory && !it.isHidden }?.sortedBy { it.name.lowercase() }
            folders = list ?: emptyList()
        } else {
            folders = emptyList()
        }
    }

    LaunchedEffect(currentDirectory) {
        refreshFolders()
    }

    val operationInProgress by fileOpsViewModel.operationInProgress.collectAsState()
    val opResult by fileOpsViewModel.operationResult.collectAsState()

    LaunchedEffect(opResult) {
        if (opResult != null) {
            onComplete()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(currentDirectory.name.ifEmpty { stringResource(R.string.storage_internal_storage) }) },
                actions = {
                    IconButton(onClick = { showCreateFolderDialog = true }) {
                        Icon(Icons.Filled.CreateNewFolder, contentDescription = stringResource(R.string.cd_create_new_folder))
                    }
                }
            )
        },
        bottomBar = {
            BottomAppBar {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val opStatus = if (operationType == "MOVE") stringResource(R.string.storage_moving) else stringResource(R.string.storage_copying)
                    Text("$opStatus ${stringResource(R.string.storage_items_count, sourceUris.size)}")
                    Row {
                        TextButton(onClick = onCancel, enabled = !operationInProgress) {
                            Text(stringResource(R.string.history_cancel_button))
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = {
                                if (operationType == "MOVE") {
                                    fileOpsViewModel.moveItemsToPath(context, sourceUris, currentDirectory)
                                } else {
                                    fileOpsViewModel.copyItemsToPath(context, sourceUris, currentDirectory)
                                }
                            },
                            enabled = !operationInProgress
                        ) {
                            Text(stringResource(R.string.storage_paste_here))
                        }
                    }
                }
            }
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            // Breadcrumbs
            val pathSegments = currentDirectory.absolutePath.split("/").filter { it.isNotEmpty() }
            val baseIndex = Environment.getExternalStorageDirectory().absolutePath.split("/").filter { it.isNotEmpty() }.size - 1
            
            LazyRow(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val segmentsToShow = if (baseIndex >= 0 && baseIndex < pathSegments.size) pathSegments.drop(baseIndex) else pathSegments
                var currentPath = if (baseIndex >= 0) Environment.getExternalStorageDirectory().absolutePath.substringBeforeLast("/") else ""
                
                items(segmentsToShow.indices.toList()) { index ->
                    val segment = segmentsToShow[index]
                    currentPath += "/$segment"
                    val pathSnapshot = currentPath
                    
                    val displayName = if (index == 0 && segment == Environment.getExternalStorageDirectory().name) stringResource(R.string.storage_internal_storage) else segment
                    
                    Text(
                        text = displayName,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .clickable {
                                currentDirectory = File(pathSnapshot)
                            }
                            .padding(4.dp)
                    )
                    if (index < segmentsToShow.size - 1) {
                        Text(" > ", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            
            HorizontalDivider()
            
            if (operationInProgress) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }

            // Folder list
            LazyColumn(modifier = Modifier.weight(1f)) {
                items(folders) { folder ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { currentDirectory = folder }
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Filled.Folder, contentDescription = stringResource(R.string.cd_folder), tint = MaterialTheme.colorScheme.secondary)
                        Spacer(modifier = Modifier.width(16.dp))
                        Text(folder.name, style = MaterialTheme.typography.bodyLarge)
                    }
                }
                if (folders.isEmpty()) {
                    item {
                        Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                            Text(stringResource(R.string.storage_empty_folder), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }

    if (showCreateFolderDialog) {
        CustomRenameDialog(
            initialName = stringResource(R.string.storage_new_folder),
            title = stringResource(R.string.storage_new_folder),
            onConfirm = { name ->
                val newFolder = File(currentDirectory, name)
                if (!newFolder.exists()) {
                    newFolder.mkdir()
                    refreshFolders()
                }
                showCreateFolderDialog = false
            },
            onDismiss = { showCreateFolderDialog = false }
        )
    }
}
