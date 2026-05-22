package com.devson.nvplayer.ui.screens

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.devson.nvplayer.ui.components.CustomRenameDialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

private data class FileNode(
    val file: File,
    val childDirCount: Int
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StorageExplorerScreen(
    operationType: String = "MOVE", // "MOVE" or "COPY"
    sourceUris: List<Uri> = emptyList(),
    isBlacklistMode: Boolean = false,
    onFoldersBlacklisted: (List<String>) -> Unit = {},
    onComplete: () -> Unit = {},
    onCancel: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    val root = remember { Environment.getExternalStorageDirectory() }
    var currentDir by remember { mutableStateOf(root) }
    var entries by remember { mutableStateOf<List<FileNode>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var isProcessing by remember { mutableStateOf(false) }
    var showNewFolderDialog by remember { mutableStateOf(false) }
    val selectedFolders = remember { mutableStateListOf<String>() }

    // Reload entries whenever the directory changes
    LaunchedEffect(currentDir) {
        isLoading = true
        entries = withContext(Dispatchers.IO) {
            (currentDir.listFiles() ?: emptyArray())
                .filter { it.isDirectory && !it.name.startsWith(".") }
                .sortedWith(compareBy { it.name.lowercase() })
                .map { dir ->
                    val childCount = dir.listFiles()?.count { it.isDirectory } ?: 0
                    FileNode(file = dir, childDirCount = childCount)
                }
        }
        isLoading = false
    }

    // Breadcrumb: list of File from root → currentDir
    val breadcrumbs: List<File> = remember(currentDir) {
        val segments = mutableListOf<File>()
        var f: File? = currentDir
        while (f != null) {
            segments.add(0, f)
            if (f.absolutePath == root.absolutePath) break
            f = f.parentFile
        }
        segments
    }

    val breadcrumbState = rememberLazyListState()
    LaunchedEffect(breadcrumbs.size) {
        if (breadcrumbs.isNotEmpty()) breadcrumbState.animateScrollToItem(breadcrumbs.lastIndex)
    }

    // Convert absolute path → MediaStore RELATIVE_PATH (e.g. "Movies/Anime/Season1")
    val targetRelativePath = remember(currentDir) {
        val rootPath = root.absolutePath
        val absPath = currentDir.absolutePath
        if (absPath.startsWith(rootPath)) {
            absPath.removePrefix("$rootPath/").ifEmpty { "Movies" }
        } else "Movies"
    }

    // Create folder uses CustomRenameDialog
    if (showNewFolderDialog) {
        CustomRenameDialog(
            initialName = "",
            title = "New Folder",
            confirmLabel = "Create",
            placeholder = "Folder name",
            subtitle = "Inside: ${if (currentDir == root) "Internal Storage" else currentDir.name}",
            onConfirm = { name ->
                val newDir = File(currentDir, name.trim())
                val created = when {
                    name.isBlank() -> false
                    name.any { it in "/\\:*?\"<>|" } -> false
                    newDir.exists() -> false
                    else -> newDir.mkdirs()
                }
                if (created) {
                    showNewFolderDialog = false
                    coroutineScope.launch {
                        entries = withContext(Dispatchers.IO) {
                            (currentDir.listFiles() ?: emptyArray())
                                .filter { it.isDirectory && !it.name.startsWith(".") }
                                .sortedWith(compareBy { it.name.lowercase() })
                                .map { dir ->
                                    FileNode(file = dir, childDirCount = dir.listFiles()?.count { it.isDirectory } ?: 0)
                                }
                        }
                    }
                } else {
                    Toast.makeText(context, "Could not create folder", Toast.LENGTH_SHORT).show()
                }
            },
            onDismiss = { showNewFolderDialog = false }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = if (isBlacklistMode) "Add to Blacklist" else if (operationType == "MOVE") "Move to…" else "Copy to…",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = if (currentDir == root) "Internal Storage" else currentDir.name,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = {
                        val parent = currentDir.parentFile
                        if (parent != null && currentDir.absolutePath != root.absolutePath) {
                            currentDir = parent
                        } else {
                            onCancel()
                        }
                    }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                actions = {
                    if (!isBlacklistMode) {
                        IconButton(onClick = { showNewFolderDialog = true }) {
                            Icon(
                                imageVector = Icons.Rounded.CreateNewFolder,
                                contentDescription = "New Folder",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceColorAtElevation(3.dp))
            ) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                if (isBlacklistMode) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedButton(
                            onClick = onCancel,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("Cancel")
                        }

                        Button(
                            onClick = {
                                onFoldersBlacklisted(selectedFolders.toList())
                            },
                            enabled = selectedFolders.isNotEmpty(),
                            modifier = Modifier.weight(2f),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary
                            )
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Block,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Blacklist Selected (${selectedFolders.size})",
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                } else {
                    // Destination path indicator
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f))
                            .border(
                                width = 0.5.dp,
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.25f),
                                shape = RoundedCornerShape(8.dp)
                            )
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.FolderSpecial,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = targetRelativePath,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedButton(
                            onClick = onCancel,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("Cancel")
                        }

                        Button(
                            onClick = {
                                isProcessing = true
                                coroutineScope.launch {
                                    val success = executeFileOperation(
                                        context = context,
                                        type = operationType,
                                        uris = sourceUris,
                                        targetRelativePath = targetRelativePath
                                    )
                                    isProcessing = false
                                    val verb = if (operationType == "MOVE") "Moved" else "Copied"
                                    if (success) {
                                        Toast.makeText(
                                            context,
                                            "$verb to ${if (currentDir == root) "Internal Storage" else currentDir.name}",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                        onComplete()
                                    } else {
                                        Toast.makeText(
                                            context,
                                            "$verb to ${if (currentDir == root) "Internal Storage" else currentDir.name} (may need refresh)",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                        onComplete()
                                    }
                                }
                            },
                            modifier = Modifier.weight(2f),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary
                            )
                        ) {
                            Icon(
                                imageVector = if (operationType == "MOVE") Icons.Rounded.DriveFileMove else Icons.Rounded.ContentCopy,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (operationType == "MOVE") "Move Here" else "Copy Here",
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {

                // Breadcrumb row
                LazyRow(
                    state = breadcrumbState,
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(breadcrumbs) { crumb ->
                        val isLast = crumb == breadcrumbs.last()
                        val label = if (crumb.absolutePath == root.absolutePath) "Internal Storage" else crumb.name
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = label,
                                style = MaterialTheme.typography.labelMedium,
                                color = if (isLast) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                fontWeight = if (isLast) FontWeight.SemiBold else FontWeight.Normal,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .then(
                                        if (!isLast) Modifier.clickable { currentDir = crumb }
                                        else Modifier
                                    )
                                    .padding(horizontal = 4.dp, vertical = 2.dp)
                            )
                            if (!isLast) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        }
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                // Folder listing
                when {
                    isLoading -> {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(36.dp),
                                strokeWidth = 3.dp
                            )
                        }
                    }

                    entries.isEmpty() -> {
                        Column(
                            modifier = Modifier.fillMaxSize(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .size(72.dp)
                                    .clip(RoundedCornerShape(18.dp))
                                    .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f))
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.FolderOpen,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
                                    modifier = Modifier.size(36.dp)
                                )
                            }
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                "No subfolders",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                "You can select this folder or create a new one",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                modifier = Modifier.padding(top = 4.dp)
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            OutlinedButton(
                                onClick = { showNewFolderDialog = true },
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.6f))
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.CreateNewFolder,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("New Folder Here")
                            }
                        }
                    }

                    else -> {
                        LazyColumn(
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            items(entries, key = { it.file.absolutePath }) { node ->
                                ExplorerFolderRow(
                                    node = node,
                                    isBlacklistMode = isBlacklistMode,
                                    isSelected = selectedFolders.contains(node.file.absolutePath),
                                    onSelectedChange = { selected ->
                                        if (selected) {
                                            selectedFolders.add(node.file.absolutePath)
                                        } else {
                                            selectedFolders.remove(node.file.absolutePath)
                                        }
                                    },
                                    onClick = { currentDir = node.file }
                                )
                            }
                        }
                    }
                }
            }

            // Processing overlay
            AnimatedVisibility(
                visible = isProcessing,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.fillMaxSize()
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.6f)),
                    contentAlignment = Alignment.Center
                ) {
                    ElevatedCard(
                        shape = RoundedCornerShape(20.dp),
                        modifier = Modifier.padding(32.dp)
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(16.dp),
                            modifier = Modifier.padding(horizontal = 40.dp, vertical = 32.dp)
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(44.dp),
                                strokeWidth = 3.5.dp
                            )
                            Text(
                                text = if (operationType == "MOVE") "Moving…" else "Copying…",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "${sourceUris.size} file(s)",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ExplorerFolderRow(
    node: FileNode,
    isBlacklistMode: Boolean = false,
    isSelected: Boolean = false,
    onSelectedChange: (Boolean) -> Unit = {},
    onClick: () -> Unit
) {
    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (isBlacklistMode) {
                Checkbox(
                    checked = isSelected,
                    onCheckedChange = onSelectedChange,
                    modifier = Modifier.padding(end = 4.dp)
                )
            }

            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f))
            ) {
                Icon(
                    imageVector = Icons.Rounded.Folder,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(22.dp)
                )
            }

            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = node.file.name,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = if (node.childDirCount > 0)
                        "${node.childDirCount} subfolder${if (node.childDirCount != 1) "s" else ""}"
                    else "No subfolders",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(
                        alpha = if (node.childDirCount > 0) 0.8f else 0.5f
                    )
                )
            }

            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

private suspend fun executeFileOperation(
    context: Context,
    type: String,
    uris: List<Uri>,
    targetRelativePath: String
): Boolean = withContext(Dispatchers.IO) {
    var anySuccess = false
    try {
        val resolver = context.contentResolver
        val relativePath = if (targetRelativePath.endsWith("/")) targetRelativePath
        else "$targetRelativePath/"

        uris.forEach { uri ->
            runCatching {
                var displayName = "video_${System.currentTimeMillis()}.mp4"
                resolver.query(
                    uri,
                    arrayOf(MediaStore.Video.Media.DISPLAY_NAME),
                    null, null, null
                )?.use { cursor ->
                    if (cursor.moveToFirst()) displayName = cursor.getString(0) ?: displayName
                }

                val mimeType = resolver.getType(uri) ?: "video/mp4"
                val values = ContentValues().apply {
                    put(MediaStore.Video.Media.DISPLAY_NAME, displayName)
                    put(MediaStore.Video.Media.MIME_TYPE, mimeType)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        put(MediaStore.Video.Media.RELATIVE_PATH, relativePath)
                        put(MediaStore.Video.Media.IS_PENDING, 1)
                    }
                }

                val collection = MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
                val destinationUri = resolver.insert(collection, values)
                    ?: throw IllegalStateException("MediaStore insert returned null")

                resolver.openInputStream(uri)?.use { input ->
                    resolver.openOutputStream(destinationUri)?.use { output ->
                        val buffer = ByteArray(8192)
                        var bytesRead: Int
                        while (input.read(buffer).also { bytesRead = it } != -1) {
                            output.write(buffer, 0, bytesRead)
                        }
                    }
                }

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    val updateValues = ContentValues().apply {
                        put(MediaStore.Video.Media.IS_PENDING, 0)
                    }
                    resolver.update(destinationUri, updateValues, null, null)
                }

                if (type == "MOVE") {
                    runCatching { resolver.delete(uri, null, null) }
                }
                anySuccess = true
            }.onFailure { it.printStackTrace() }
        }
        anySuccess
    } catch (e: Exception) {
        e.printStackTrace()
        anySuccess
    }
}
