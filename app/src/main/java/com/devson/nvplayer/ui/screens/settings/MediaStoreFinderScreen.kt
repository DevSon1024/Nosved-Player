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