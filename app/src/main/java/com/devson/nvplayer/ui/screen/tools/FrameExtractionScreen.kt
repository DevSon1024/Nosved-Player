package com.devson.nvplayer.ui.screen.tools

import android.Manifest
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import com.devson.nvplayer.util.OpenDocumentWithInitialUri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.VideoLibrary
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.devson.nvplayer.service.FrameExtractionService
import com.devson.nvplayer.util.formatDuration
import com.devson.nvplayer.viewmodel.FrameExtractionViewModel
import com.devson.nvplayer.viewmodel.FrameFormat
import kotlin.math.roundToInt
import kotlin.math.roundToLong

private enum class ExtractionRateMode {
    DEFAULT, CUSTOM
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FrameExtractionScreen(
    onBack: () -> Unit,
    viewModel: FrameExtractionViewModel = viewModel()
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()

    var selectedVideoUri by remember { mutableStateOf<Uri?>(null) }
    var selectedVideoTitle by remember { mutableStateOf<String?>(null) }
    var selectedVideoDurationMs by remember { mutableLongStateOf(0L) }

    var rateMode by remember { mutableStateOf(ExtractionRateMode.DEFAULT) }
    var customSliderStep by remember { mutableFloatStateOf(0f) } // 0: All Frames, 5: 1 FPS
    var selectedFormat by remember { mutableStateOf(FrameFormat.JPEG) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.values.all { it }
        if (!allGranted) {
            Toast.makeText(context, "Storage and notification permissions are recommended for background frame extraction.", Toast.LENGTH_SHORT).show()
        }
    }

    val videoPickerLauncher = rememberLauncherForActivityResult(
        contract = OpenDocumentWithInitialUri("primary:Movies")
    ) { uri: Uri? ->
        if (uri != null) {
            selectedVideoUri = uri
            val resolvedTitle = FrameExtractionService.resolveVideoDisplayName(context, uri)
            selectedVideoTitle = resolvedTitle

            val retriever = android.media.MediaMetadataRetriever()
            try {
                retriever.setDataSource(context, uri)
                val durationStr = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_DURATION)
                selectedVideoDurationMs = durationStr?.toLongOrNull() ?: 0L
            } catch (e: Exception) {
                selectedVideoDurationMs = 0L
            } finally {
                try { retriever.release() } catch (_: Exception) {}
            }
            viewModel.resetState()
        }
    }

    LaunchedEffect(Unit) {
        val permissionsToRequest = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissionsToRequest.add(Manifest.permission.READ_MEDIA_VIDEO)
            permissionsToRequest.add(Manifest.permission.READ_MEDIA_IMAGES)
            permissionsToRequest.add(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            permissionsToRequest.add(Manifest.permission.READ_EXTERNAL_STORAGE)
            permissionsToRequest.add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        }
        permissionLauncher.launch(permissionsToRequest.toTypedArray())
    }

    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeTopAppBar(
                title = { Text("Batch Frame Extractor", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.largeTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    scrolledContainerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground,
                    navigationIconContentColor = MaterialTheme.colorScheme.onBackground
                ),
                scrollBehavior = scrollBehavior
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState())
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Source Video Selection Card
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Source Video",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )

                    if (selectedVideoUri == null) {
                        Button(
                            onClick = { videoPickerLauncher.launch(arrayOf("video/*")) },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !uiState.isExtracting
                        ) {
                            Icon(Icons.Rounded.VideoLibrary, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Select Video File")
                        }
                    } else {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(
                                imageVector = Icons.Default.Movie,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(32.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = selectedVideoTitle ?: "video",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1
                                )
                                Text(
                                    text = "Duration: ${formatDuration(selectedVideoDurationMs)}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            OutlinedButton(
                                onClick = {
                                    viewModel.resetState()
                                    videoPickerLauncher.launch(arrayOf("video/*"))
                                },
                                enabled = !uiState.isExtracting
                            ) {
                                Text("Change")
                            }
                        }
                    }
                }
            }

            // Controls Card
            if (selectedVideoUri != null) {
                val maxDurationMs = remember(selectedVideoDurationMs) {
                    selectedVideoDurationMs.coerceAtLeast(1000L)
                }

                var sliderRange by remember(maxDurationMs) {
                    val initialEnd = minOf(maxDurationMs, FrameExtractionViewModel.MAX_EXTRACTION_DURATION_MS)
                    mutableStateOf(0f..initialEnd.toFloat())
                }

                val startTimeMs = sliderRange.start.roundToLong()
                val endTimeMs = sliderRange.endInclusive.roundToLong()
                val durationSelectedMs = (endTimeMs - startTimeMs).coerceAtLeast(0L)

                // Slider mapping: Step 0: All Frames (0ms), Step 1: 15 FPS (66ms), Step 2: 10 FPS (100ms), Step 3: 5 FPS (200ms), Step 4: 2 FPS (500ms), Step 5: 1 FPS (1000ms)
                val (intervalMs, intervalLabel) = remember(rateMode, customSliderStep) {
                    if (rateMode == ExtractionRateMode.DEFAULT) {
                        0L to "All Frames (Native Video Framerate)"
                    } else {
                        when (customSliderStep.roundToInt().coerceIn(0, 5)) {
                            0 -> 0L to "All Frames (~30 FPS)"
                            1 -> 66L to "15 FPS (every 66ms)"
                            2 -> 100L to "10 FPS (every 100ms)"
                            3 -> 200L to "5 FPS (every 200ms)"
                            4 -> 500L to "2 FPS (every 500ms)"
                            else -> 1000L to "1 FPS (every 1000ms)"
                        }
                    }
                }

                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // Segment Selection
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Segment Selection",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "Max 2 mins (${formatDuration(durationSelectedMs)})",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (durationSelectedMs >= FrameExtractionViewModel.MAX_EXTRACTION_DURATION_MS) {
                                    MaterialTheme.colorScheme.error
                                } else {
                                    MaterialTheme.colorScheme.primary
                                }
                            )
                        }

                        RangeSlider(
                            value = sliderRange,
                            onValueChange = { range ->
                                val start = range.start
                                var end = range.endInclusive
                                val maxAllowedSpan = FrameExtractionViewModel.MAX_EXTRACTION_DURATION_MS.toFloat()
                                if (end - start > maxAllowedSpan) {
                                    if (start != sliderRange.start) {
                                        end = start + maxAllowedSpan
                                    } else {
                                        val newStart = end - maxAllowedSpan
                                        sliderRange = newStart.coerceAtLeast(0f)..end.coerceAtMost(maxDurationMs.toFloat())
                                        return@RangeSlider
                                    }
                                }
                                sliderRange = start.coerceAtLeast(0f)..end.coerceAtMost(maxDurationMs.toFloat())
                            },
                            valueRange = 0f..maxDurationMs.toFloat(),
                            enabled = !uiState.isExtracting,
                            modifier = Modifier.fillMaxWidth()
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Start: ${formatDuration(startTimeMs)}",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = "End: ${formatDuration(endTimeMs)}",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Medium
                            )
                        }

                        // Extraction Rate Mode Segmented Control
                        Text(
                            text = "Extraction Mode",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold
                        )

                        SingleChoiceSegmentedButtonRow(
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            SegmentedButton(
                                selected = rateMode == ExtractionRateMode.DEFAULT,
                                onClick = { if (!uiState.isExtracting) rateMode = ExtractionRateMode.DEFAULT },
                                shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                                enabled = !uiState.isExtracting
                            ) {
                                Text("Default (All Frames)")
                            }
                            SegmentedButton(
                                selected = rateMode == ExtractionRateMode.CUSTOM,
                                onClick = { if (!uiState.isExtracting) rateMode = ExtractionRateMode.CUSTOM },
                                shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                                enabled = !uiState.isExtracting
                            ) {
                                Text("Custom Interval")
                            }
                        }

                        // Custom Interval Slider Node 1 ("All Frames") to Node 2 ("1 FPS")
                        AnimatedVisibility(
                            visible = rateMode == ExtractionRateMode.CUSTOM,
                            enter = fadeIn() + expandVertically(),
                            exit = fadeOut() + shrinkVertically()
                        ) {
                            Column(
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = "Extraction Rate",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = intervalLabel,
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }

                                Slider(
                                    value = customSliderStep,
                                    onValueChange = { customSliderStep = it },
                                    valueRange = 0f..5f,
                                    steps = 4,
                                    enabled = !uiState.isExtracting,
                                    modifier = Modifier.fillMaxWidth()
                                )

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("All Frames", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                                    Text("1 FPS (1000ms)", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                                }
                            }
                        }

                        // Format Selection
                        Text(
                            text = "Image Format",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            FrameFormat.entries.forEach { fmt ->
                                FilterChip(
                                    selected = selectedFormat == fmt,
                                    onClick = { if (!uiState.isExtracting) selectedFormat = fmt },
                                    label = { Text(fmt.name) },
                                    enabled = !uiState.isExtracting
                                )
                            }
                        }
                    }
                }

                // Extraction Progress & Foreground Service States
                if (uiState.isExtracting) {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            LinearProgressIndicator(
                                progress = { uiState.progress },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(8.dp)
                                    .clip(RoundedCornerShape(4.dp))
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = uiState.statusMessage ?: "Extracting frames in background service...",
                                    style = MaterialTheme.typography.bodySmall
                                )
                                Text(
                                    text = "${(uiState.progress * 100).toInt()}%",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            OutlinedButton(
                                onClick = { viewModel.cancelExtraction(context) },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Cancel Extraction Service")
                            }
                        }
                    }
                } else if (uiState.isSuccess) {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.CheckCircle,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Column {
                                Text(
                                    text = uiState.statusMessage ?: "Extraction completed!",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                                uiState.outputFolderPath?.let { folder ->
                                    Text(
                                        text = "Saved in $folder",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                                    )
                                }
                            }
                        }
                    }
                } else {
                    uiState.error?.let { err ->
                        Text(
                            text = err,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }

                    Button(
                        onClick = {
                            val uri = selectedVideoUri ?: return@Button
                            val title = selectedVideoTitle ?: "video"
                            viewModel.startFrameExtraction(
                                context = context,
                                videoUri = uri,
                                videoName = title,
                                startTimeMs = startTimeMs,
                                endTimeMs = endTimeMs,
                                intervalMs = intervalMs,
                                format = selectedFormat
                            )
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Start Foreground Frame Extraction")
                    }
                }
            }
        }
    }
}
