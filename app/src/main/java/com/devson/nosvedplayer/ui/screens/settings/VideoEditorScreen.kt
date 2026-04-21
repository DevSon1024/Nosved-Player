package com.devson.nosvedplayer.ui.screens.settings

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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.util.UnstableApi
import com.devson.nosvedplayer.viewmodel.AspectRatio
import com.devson.nosvedplayer.viewmodel.ExportOperation
import com.devson.nosvedplayer.viewmodel.ExportUiState
import com.devson.nosvedplayer.viewmodel.MediaExportViewModel

private val rotationOptions = listOf(45f, 90f, 180f)
private val cropOptions = AspectRatio.entries

@KOptIn(ExperimentalMaterial3Api::class)
@MediaOptIn(UnstableApi::class)
@Composable
fun VideoEditorScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val vm: MediaExportViewModel = viewModel()
    val exportState by vm.state.collectAsStateWithLifecycle()

    var pickedUri by remember { mutableStateOf<Uri?>(null) }
    var selectedRotation by remember { mutableStateOf<Float?>(null) }
    var selectedCrop by remember { mutableStateOf<AspectRatio?>(null) }

    val snackbarHostState = remember { SnackbarHostState() }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let {
            pickedUri = it
            selectedRotation = null
            selectedCrop = null
            vm.resetState()
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
                title = { Text("Video Editor", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold) },
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
                    RotateCard(
                        selected = selectedRotation,
                        enabled = exportState !is ExportUiState.Processing,
                        onSelect = { deg ->
                            selectedRotation = deg
                            selectedCrop = null
                            vm.startExport(context, pickedUri!!, ExportOperation.Rotate(deg))
                        }
                    )

                    CropCard(
                        selected = selectedCrop,
                        enabled = exportState !is ExportUiState.Processing,
                        onSelect = { ratio ->
                            selectedCrop = ratio
                            selectedRotation = null
                            vm.startExport(context, pickedUri!!, ExportOperation.CropVideo(ratio))
                        }
                    )

                    AudioCard(
                        enabled = exportState !is ExportUiState.Processing,
                        onExtract = {
                            selectedRotation = null
                            selectedCrop = null
                            vm.startExport(context, pickedUri!!, ExportOperation.ExtractAudio)
                        }
                    )
                }
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
    EditorCard(icon = Icons.Default.VideoFile, title = "Source Video") {
        if (pickedUri != null) {
            Text(
                text = pickedUri.lastPathSegment ?: pickedUri.toString(),
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
@Composable
private fun RotateCard(selected: Float?, enabled: Boolean, onSelect: (Float) -> Unit) {
    EditorCard(icon = Icons.Default.RotateRight, title = "Rotate") {
        Text(
            "Apply clockwise rotation to the video",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(12.dp))
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            rotationOptions.forEachIndexed { index, deg ->
                SegmentedButton(
                    selected = selected == deg,
                    onClick = { if (enabled) onSelect(deg) },
                    shape = SegmentedButtonDefaults.itemShape(index = index, count = rotationOptions.size),
                    enabled = enabled
                ) {
                    Text("${deg.toInt()}\u00b0")
                }
            }
        }
    }
}

@KOptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CropCard(selected: AspectRatio?, enabled: Boolean, onSelect: (AspectRatio) -> Unit) {
    EditorCard(icon = Icons.Default.Crop, title = "Crop") {
        Text(
            "Centre-crop to a target aspect ratio",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(12.dp))
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            cropOptions.forEachIndexed { index, ratio ->
                SegmentedButton(
                    selected = selected == ratio,
                    onClick = { if (enabled) onSelect(ratio) },
                    shape = SegmentedButtonDefaults.itemShape(index = index, count = cropOptions.size),
                    enabled = enabled
                ) {
                    Text(ratio.label)
                }
            }
        }
    }
}

@Composable
private fun AudioCard(enabled: Boolean, onExtract: () -> Unit) {
    EditorCard(icon = Icons.Default.MusicNote, title = "Extract Audio") {
        Text(
            "Strip the video track and save the audio as .m4a (AAC)",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(12.dp))
        Button(
            onClick = onExtract,
            enabled = enabled,
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Default.AudioFile, contentDescription = null, Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("Extract to M4A")
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
            Text(
                "Exporting\u2026 ${(progress * 100).toInt()}%",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Medium
            )
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
