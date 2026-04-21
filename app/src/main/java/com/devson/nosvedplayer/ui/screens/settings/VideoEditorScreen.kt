package com.devson.nosvedplayer.ui.screens.settings

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.OptIn as MediaOptIn
import kotlin.OptIn as KOptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.devson.nosvedplayer.viewmodel.ExportAudioFormat
import com.devson.nosvedplayer.viewmodel.ExportOperation
import com.devson.nosvedplayer.viewmodel.ExportUiState
import com.devson.nosvedplayer.viewmodel.MediaExportViewModel

@KOptIn(ExperimentalMaterial3Api::class)
@MediaOptIn(UnstableApi::class)
@Composable
fun VideoEditorScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val vm: MediaExportViewModel = viewModel()
    val exportState by vm.state.collectAsStateWithLifecycle()

    var pickedUri by remember { mutableStateOf<Uri?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }

    val prefs = remember { context.getSharedPreferences("video_editor_prefs", Context.MODE_PRIVATE) }
    var outputFolderUriString by remember { mutableStateOf(prefs.getString("output_folder_uri", null)) }
    val outputFolderUri = remember(outputFolderUriString) { outputFolderUriString?.let { Uri.parse(it) } }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let {
            pickedUri = it
            vm.resetState()
        }
    }

    val dirPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        uri?.let {
            try {
                context.contentResolver.takePersistableUriPermission(it, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                prefs.edit().putString("output_folder_uri", it.toString()).apply()
                outputFolderUriString = it.toString()
            } catch (e: Exception) { e.printStackTrace() }
        }
    }

    LaunchedEffect(exportState) {
        when (val s = exportState) {
            is ExportUiState.Success -> snackbarHostState.showSnackbar("Saved: ${s.outputPath}", duration = SnackbarDuration.Long)
            is ExportUiState.Error -> snackbarHostState.showSnackbar("Error: ${s.msg}")
            else -> Unit
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Video -> Audio Converter", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = {
                        if (exportState is ExportUiState.Processing) vm.cancelExport()
                        onBack()
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(
                        top = padding.calculateTopPadding() + 16.dp,
                        bottom = padding.calculateBottomPadding() + 16.dp,
                        start = 16.dp,
                        end = 16.dp
                    ),
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                PickerCard(pickedUri = pickedUri, onPick = { picker.launch("video/*") })

                if (pickedUri != null) {
                    AudioExtractionSection(
                        uri = pickedUri!!,
                        enabled = exportState !is ExportUiState.Processing,
                        onExtract = { format, startMs, endMs ->
                            vm.startExport(context, pickedUri!!, ExportOperation.ExtractAudio(format, startMs, endMs), outputFolderUri)
                        }
                    )
                }

                LocationCard(
                    currentUri = outputFolderUriString,
                    onChange = { dirPicker.launch(null) }
                )
            }

            AnimatedVisibility(
                visible = exportState is ExportUiState.Processing,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.align(Alignment.BottomCenter)
            ) {
                val progress = (exportState as? ExportUiState.Processing)?.progress ?: 0f
                ExportProgressBar(
                    progress = progress,
                    onCancel = { vm.cancelExport() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainer)
                        .padding(16.dp)
                )
            }
        }
    }
}

@Composable
private fun PickerCard(pickedUri: Uri?, onPick: () -> Unit) {
    val context = LocalContext.current
    var displayName by remember(pickedUri) { mutableStateOf(pickedUri?.lastPathSegment ?: pickedUri?.toString() ?: "") }

    LaunchedEffect(pickedUri) {
        if (pickedUri?.scheme == "content") {
            try {
                context.contentResolver.query(pickedUri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val idx = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                        if (idx != -1) {
                            cursor.getString(idx)?.let { displayName = it }
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    EditorCard(icon = Icons.Default.VideoFile, title = "Source Video") {
        if (pickedUri != null) {
            Text(
                text = displayName,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(8.dp))
        }
        OutlinedButton(
            onClick = onPick,
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Default.FolderOpen, contentDescription = null, Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(if (pickedUri == null) "Choose Video" else "Change Video")
        }
    }
}

@KOptIn(ExperimentalMaterial3Api::class)
@MediaOptIn(UnstableApi::class)
@Composable
private fun AudioExtractionSection(uri: Uri, enabled: Boolean, onExtract: (ExportAudioFormat, Long, Long) -> Unit) {
    val context = LocalContext.current
    val exoPlayer = remember { ExoPlayer.Builder(context).build() }
    var durationMs by remember { mutableLongStateOf(1000L) }
    var sliderValues by remember { mutableStateOf(0f..1000f) }
    var selectedFormat by remember { mutableStateOf(ExportAudioFormat.M4A) }

    DisposableEffect(uri) {
        val mediaItem = MediaItem.fromUri(uri)
        exoPlayer.setMediaItem(mediaItem)
        exoPlayer.prepare()
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_READY) {
                    val d = exoPlayer.duration.coerceAtLeast(1000L)
                    durationMs = d
                    sliderValues = 0f..d.toFloat()
                }
            }
        }
        exoPlayer.addListener(listener)
        onDispose {
            exoPlayer.removeListener(listener)
            exoPlayer.clearMediaItems()
        }
    }

    DisposableEffect(Unit) {
        onDispose { exoPlayer.release() }
    }

    EditorCard(icon = Icons.Default.MusicNote, title = "Extract & Trim Audio") {
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    player = exoPlayer
                    useController = true
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
        )
        
        Spacer(Modifier.height(16.dp))
        Text("Trim Range (Seconds)", style = MaterialTheme.typography.bodySmall)
        RangeSlider(
            value = sliderValues,
            onValueChange = { sliderValues = it },
            valueRange = 0f..durationMs.toFloat(),
            enabled = enabled
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("${(sliderValues.start / 1000).toInt()}s", style = MaterialTheme.typography.labelSmall)
            Text("${(sliderValues.endInclusive / 1000).toInt()}s", style = MaterialTheme.typography.labelSmall)
        }

        Spacer(Modifier.height(16.dp))
        Text("Format", style = MaterialTheme.typography.bodySmall)
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            ExportAudioFormat.entries.forEachIndexed { index, fmt ->
                SegmentedButton(
                    selected = selectedFormat == fmt,
                    onClick = { selectedFormat = fmt },
                    shape = SegmentedButtonDefaults.itemShape(index = index, count = ExportAudioFormat.entries.size),
                    enabled = enabled
                ) {
                    Text(fmt.name)
                }
            }
        }

        Spacer(Modifier.height(16.dp))
        Button(
            onClick = { onExtract(selectedFormat, sliderValues.start.toLong(), sliderValues.endInclusive.toLong()) },
            enabled = enabled,
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Default.AudioFile, contentDescription = null, Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("Extract Audio")
        }
    }
}

@Composable
private fun LocationCard(currentUri: String?, onChange: () -> Unit) {
    EditorCard(icon = Icons.Default.Folder, title = "Save Location") {
        val pathText = if (currentUri != null) "Custom: ${Uri.parse(currentUri).lastPathSegment ?: currentUri}"
        else "Default: Movies/NosvedPlayer/Music"
        Text(
            text = pathText,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(Modifier.height(12.dp))
        OutlinedButton(onClick = onChange, modifier = Modifier.fillMaxWidth()) {
            Text("Change Directory")
        }
    }
}

@Composable
private fun ExportProgressBar(progress: Float, onCancel: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Exporting... ${(progress * 100).toInt()}%", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Medium)
            TextButton(onClick = onCancel) { Text("Cancel") }
        }
        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier.fillMaxWidth(),
            trackColor = MaterialTheme.colorScheme.surfaceVariant
        )
    }
}

@Composable
private fun EditorCard(icon: ImageVector, title: String, content: @Composable ColumnScope.() -> Unit) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        tonalElevation = 1.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            }
            Spacer(Modifier.height(4.dp))
            content()
        }
    }
}
