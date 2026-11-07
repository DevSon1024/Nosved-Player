package com.devson.nvplayer.ui.screen

import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material3.*
import com.devson.nvplayer.ui.common.sheets.SmoothModalBottomSheet
import com.devson.nvplayer.util.StreamUrlValidator
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NetworkStreamDialog(
    onDismiss: () -> Unit,
    onPlay: (Uri) -> Unit,
    onHistoryClick: () -> Unit
) {
    val context = LocalContext.current
    val clipboardManager = remember(context) {
        context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
    }

    var urlText by remember { mutableStateOf("") }
    var errorText by remember { mutableStateOf<String?>(null) }
    var showInfoSheet by remember { mutableStateOf(false) }

    val trimmedUrl = urlText.trim()

    if (showInfoSheet) {
        NetworkStreamInfoSheet(
            onDismiss = { showInfoSheet = false }
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.size(40.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Filled.PlayCircle,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                    Column {
                        Text(
                            text = "Play Stream",
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleLarge
                        )
                        Text(
                            text = "Network video stream",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { showInfoSheet = true }) {
                        Icon(
                            imageVector = Icons.Outlined.Info,
                            contentDescription = "Streaming Information",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                    IconButton(onClick = onHistoryClick) {
                        Icon(
                            imageVector = Icons.Filled.History,
                            contentDescription = "Stream History",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "Enter an HLS stream (.m3u8), DASH manifest (.mpd), or direct media URL to stream with MPV.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                OutlinedTextField(
                    value = urlText,
                    onValueChange = {
                        urlText = it
                        errorText = null
                    },
                    placeholder = {
                        Text(
                            text = "https://example.com/stream.m3u8",
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Outlined.Link,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    },
                    trailingIcon = {
                        if (urlText.isNotEmpty()) {
                            IconButton(onClick = { urlText = ""; errorText = null }) {
                                Icon(
                                    imageVector = Icons.Filled.Clear,
                                    contentDescription = "Clear Text"
                                )
                            }
                        } else {
                            IconButton(onClick = {
                                val clipText = clipboardManager?.primaryClip
                                    ?.takeIf { it.itemCount > 0 }
                                    ?.getItemAt(0)?.text?.toString()
                                if (!clipText.isNullOrBlank()) {
                                    urlText = clipText
                                    errorText = null
                                }
                            }) {
                                Icon(
                                    imageVector = Icons.Filled.ContentPaste,
                                    contentDescription = "Paste Clipboard"
                                )
                            }
                        }
                    },
                    singleLine = true,
                    maxLines = 1,
                    isError = errorText != null,
                    supportingText = {
                        if (errorText != null) {
                            Text(
                                text = errorText.orEmpty(),
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodySmall
                            )
                        } else {
                            Text(
                                text = "Supports HLS (.m3u8), DASH (.mpd), and direct media (MP4, MKV)",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp)
                )

                // Demo links chips with horizontal scroll
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    SuggestionChip(
                        onClick = {
                            urlText = "https://cph-p2p-msl.akamaized.net/hls/live/2000341/test/master.m3u8"
                            errorText = null
                        },
                        label = { Text("HLS Demo") },
                        shape = RoundedCornerShape(8.dp)
                    )
                    SuggestionChip(
                        onClick = {
                            urlText = "https://dash.akamaized.net/akamai/bbb_30fps/bbb_30fps.mpd"
                            errorText = null
                        },
                        label = { Text("DASH Demo") },
                        shape = RoundedCornerShape(8.dp)
                    )
                    SuggestionChip(
                        onClick = {
                            urlText = "https://storage.googleapis.com/exoplayer-test-media-0/BigBuckBunny_320x180.mp4"
                            errorText = null
                        },
                        label = { Text("MP4 Demo") },
                        shape = RoundedCornerShape(8.dp)
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (trimmedUrl.isBlank()) {
                        errorText = "URL cannot be empty"
                        return@Button
                    }
                    val parsedUri = runCatching { Uri.parse(trimmedUrl) }.getOrNull()
                    if (parsedUri == null || parsedUri.scheme.isNullOrBlank()) {
                        errorText = "Please enter a valid URL (http:// or https://)"
                        return@Button
                    }
                    if (!StreamUrlValidator.isValidStreamingUrl(trimmedUrl)) {
                        errorText = "Unsupported stream URL. Only HLS (.m3u8), DASH (.mpd), and direct video files (.mp4, .mkv) are supported."
                        return@Button
                    }
                    onPlay(parsedUri)
                },
                shape = RoundedCornerShape(10.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.PlayArrow,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text("Play")
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                shape = RoundedCornerShape(10.dp)
            ) {
                Text("Cancel")
            }
        },
        shape = RoundedCornerShape(24.dp)
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NetworkStreamInfoSheet(
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)

    SmoothModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        dragHandle = { BottomSheetDefaults.DragHandle() }
    ) { dismissWithAnimation ->
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .verticalScroll(rememberScrollState())
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Online Streaming Guide",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Native HLS, DASH, and progressive streaming",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = dismissWithAnimation) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = "Close"
                    )
                }
            }

            // Section 1: HLS
            InfoCardItem(
                icon = Icons.Filled.LiveTv,
                title = "HTTP Live Streaming (HLS)",
                subtitle = ".m3u8 Multi-Bitrate Playlists",
                description = "Nosved Player natively demuxes HLS master manifests with adaptive multi-bitrate tracks, fast segment seeking, and live stream playback."
            )

            // Section 2: DASH
            InfoCardItem(
                icon = Icons.Filled.Tune,
                title = "Dynamic Adaptive Streaming (DASH)",
                subtitle = ".mpd Media Presentation Descriptions",
                description = "Seamlessly parses multi-adaptation sets, offering resolutions from 144p to 4K Ultra HD and multi-language audio track switching."
            )

            // Section 3: Direct media streams
            InfoCardItem(
                icon = Icons.Filled.Movie,
                title = "Direct Media Files",
                subtitle = "Progressive HTTP/HTTPS Streams",
                description = "Direct files (.mp4, .mkv, .webm, .ts, etc.) stream directly with hardware-accelerated MediaCodec decoders and smart network buffering."
            )

            Button(
                onClick = dismissWithAnimation,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "Got it",
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

@Composable
private fun InfoCardItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    description: String
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.Top
        ) {
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.size(36.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = title,
                    fontWeight = FontWeight.SemiBold,
                    style = MaterialTheme.typography.titleSmall
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
