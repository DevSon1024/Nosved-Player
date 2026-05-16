package com.devson.nvplayer.ui.screens.settings

import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.MediaStore
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MediaStoreFinderScreen(
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    val permissionLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.RequestMultiplePermissions()
    ) { _ -> /* Permissions results handled implicitly */ }

    LaunchedEffect(Unit) {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            permissionLauncher.launch(
                arrayOf(
                    android.Manifest.permission.READ_MEDIA_IMAGES,
                    android.Manifest.permission.READ_MEDIA_AUDIO,
                    android.Manifest.permission.READ_MEDIA_VIDEO
                )
            )
        } else {
            permissionLauncher.launch(
                arrayOf(android.Manifest.permission.READ_EXTERNAL_STORAGE)
            )
        }
    }

    var inputId by remember { mutableStateOf("") }
    var fileInfo by remember { mutableStateOf<String?>(null) }
    var fileUri by remember { mutableStateOf<Uri?>(null) }
    var fileMimeType by remember { mutableStateOf<String?>(null) }

    val filePickerLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            val id = getMediaIdFromUri(context, uri)
            if (id != null) {
                fileInfo = "Selected File MediaStore ID:\n$id"
                inputId = id.toString() // Auto-fill the text field
                fileUri = null
                fileMimeType = null
            } else {
                fileInfo = "Could not resolve a MediaStore ID for this file. It might not be indexed."
                fileUri = null
                fileMimeType = null
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("MediaStore Finder", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            OutlinedTextField(
                value = inputId,
                onValueChange = { inputId = it.filter { char -> char.isDigit() } },
                label = { Text("Enter MediaStore ID e.g.: 1000001234") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) }
            )

            Button(
                onClick = {
                    val idLong = inputId.toLongOrNull()
                    if (idLong != null) {
                        coroutineScope.launch {
                            val result = findMediaFile(context, idLong)
                            if (result != null) {
                                fileInfo = "Name: ${result.name}\nPath: ${result.path}\nMIME Type: ${result.mimeType}"
                                fileUri = result.uri
                                fileMimeType = result.mimeType
                            } else {
                                fileInfo = "File not found for ID: $inputId"
                                fileUri = null
                                fileMimeType = null
                            }
                        }
                    } else {
                        Toast.makeText(context, "Please enter a valid numeric ID", Toast.LENGTH_SHORT).show()
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Find File")
            }

            Row(
                verticalAlignment = Alignment.CenterVertically, 
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
            ) {
                HorizontalDivider(modifier = Modifier.weight(1f))
                Text("OR", modifier = Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                HorizontalDivider(modifier = Modifier.weight(1f))
            }
            
            Button(
                onClick = { filePickerLauncher.launch("*/*") },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
            ) {
                Text("Select File to get ID")
            }

            if (fileInfo != null) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Result:",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                        Text(
                            text = fileInfo!!,
                            style = MaterialTheme.typography.bodyMedium
                        )

                        if (fileUri != null && fileMimeType != null) {
                            Spacer(modifier = Modifier.height(16.dp))
                            Button(
                                onClick = {
                                    openFileInExternalApp(context, fileUri!!, fileMimeType!!)
                                },
                                modifier = Modifier.align(Alignment.End)
                            ) {
                                Text("Open File")
                            }
                        }
                    }
                }
            }
        }
    }
}

data class MediaResult(val name: String, val path: String, val mimeType: String, val uri: Uri)

fun getMediaIdFromUri(context: android.content.Context, uri: android.net.Uri): Long? {
    if (uri.toString().startsWith("content://media/")) {
        return try { android.content.ContentUris.parseId(uri) } catch (e: Exception) { null }
    }

    if (android.provider.DocumentsContract.isDocumentUri(context, uri)) {
        val docId = android.provider.DocumentsContract.getDocumentId(uri)
        
        // 1. Handle Media Provider (image:123) or Downloads Provider (msf:123 or just 123)
        if (uri.authority == "com.android.providers.media.documents" || uri.authority == "com.android.providers.downloads.documents") {
            val idStr = docId.split(":").lastOrNull()
            idStr?.toLongOrNull()?.let { return it }
        }

        // 2. Handle External Storage Provider (e.g., primary:Download/video.mp4)
        if (uri.authority == "com.android.externalstorage.documents") {
            val split = docId.split(":")
            if (split.size >= 2 && "primary".equals(split[0], ignoreCase = true)) {
                val fullPath = "${android.os.Environment.getExternalStorageDirectory()}/${split[1]}"
                try {
                    val mediaUri = android.provider.MediaStore.Files.getContentUri("external")
                    val projection = arrayOf(android.provider.MediaStore.MediaColumns._ID)
                    val selection = "${android.provider.MediaStore.MediaColumns.DATA} = ?"
                    context.contentResolver.query(mediaUri, projection, selection, arrayOf(fullPath), null)?.use { cursor ->
                        if (cursor.moveToFirst()) return cursor.getLong(0)
                    }
                } catch (e: Exception) { e.printStackTrace() }
            }
        }
    }

    // 3. Fallback for general content providers
    try {
        context.contentResolver.query(uri, arrayOf(android.provider.MediaStore.MediaColumns._ID), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val index = cursor.getColumnIndex(android.provider.MediaStore.MediaColumns._ID)
                if (index != -1) return cursor.getLong(index)
            }
        }
    } catch (e: Exception) { e.printStackTrace() }
    
    return null
}

suspend fun findMediaFile(context: Context, id: Long): MediaResult? = withContext(Dispatchers.IO) {
    // MediaStore.Files allows us to find *any* file by its ID, not just videos or audio
    val contentUri = MediaStore.Files.getContentUri("external")
    val uri = ContentUris.withAppendedId(contentUri, id)

    val projection = arrayOf(
        MediaStore.MediaColumns.DISPLAY_NAME,
        MediaStore.MediaColumns.DATA, // _data column gives exact file path
        MediaStore.MediaColumns.MIME_TYPE
    )

    try {
        context.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val nameIndex = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)
                val dataIndex = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATA)
                val mimeTypeIndex = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.MIME_TYPE)

                val name = cursor.getString(nameIndex) ?: "Unknown Name"
                val path = cursor.getString(dataIndex) ?: "Unknown Path"
                val mimeType = cursor.getString(mimeTypeIndex) ?: "*/*"

                return@withContext MediaResult(name, path, mimeType, uri)
            }
        }
    } catch (e: Exception) {
        e.printStackTrace()
    }
    return@withContext null
}

fun openFileInExternalApp(context: Context, uri: Uri, mimeType: String) {
    try {
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, mimeType)
            // Important: grant read permission to the target app so it can access our provided Uri
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val chooser = Intent.createChooser(intent, "Open file with...")
        context.startActivity(chooser)
    } catch (e: Exception) {
        Toast.makeText(context, "No app found to open this file", Toast.LENGTH_SHORT).show()
    }
}