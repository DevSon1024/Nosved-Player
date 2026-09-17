package com.devson.nvplayer.ui.screen

import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.devson.nvplayer.player.ytdlp.YtdlpManager
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NetworkStreamDialog(
    onDismiss: () -> Unit,
    onPlay: (Uri) -> Unit,
    onHistoryClick: () -> Unit,
    onNavigateToYtdlpSettings: () -> Unit = {}
) {
    val context = LocalContext.current
    val clipboardManager = remember(context) {
        context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
    }

    val isYtdlpInstalled = remember(context) {
        YtdlpManager.isInstalled(context)
    }

    var urlText by remember { mutableStateOf("") }
    var errorText by remember { mutableStateOf<String?>(null) }
    var showInfoSheet by remember { mutableStateOf(false) }

    val trimmedUrl = urlText.trim()
    val isKnownDirectOrManifest = remember(trimmedUrl) {
        if (trimmedUrl.isBlank()) return@remember false
        val clean = trimmedUrl.lowercase(Locale.ROOT).substringBefore('?').substringBefore('#')
        listOf(
            ".mp4", ".mkv", ".webm", ".avi", ".mov", ".flv", ".ts",
            ".m4v", ".3gp", ".m3u8", ".mpd"
        ).any { clean.endsWith(it) }
    }

    val isPlatformUrl = remember(trimmedUrl) {
        val lower = trimmedUrl.lowercase(Locale.ROOT)
        listOf(
            "youtube.com", "youtu.be", "twitch.tv", "vimeo.com",
            "dailymotion.com", "tiktok.com", "bilibili.com",
            "twitter.com", "x.com", "instagram.com", "facebook.com", "reddit.com"
        ).any { lower.contains(it) }
    }

    val needsYtdlp = remember(trimmedUrl, isKnownDirectOrManifest, isPlatformUrl) {
        trimmedUrl.isNotBlank() && (isPlatformUrl || (!isKnownDirectOrManifest && (trimmedUrl.startsWith("http://") || trimmedUrl.startsWith("https://"))))
    }

    if (showInfoSheet) {
        NetworkStreamInfoSheet(
            isYtdlpInstalled = isYtdlpInstalled,
            onDismiss = { showInfoSheet = false },
            onNavigateToYtdlpSettings = {
                showInfoSheet = false
                onDismiss()
                onNavigateToYtdlpSettings()
            }
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
                    text = "Enter a direct video URL, HLS/DASH manifest (.m3u8, .mpd), or web video link to stream in MPV.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                OutlinedTextField(
                    value = urlText,
                    onValueChange = {
                        urlText = it
                        errorText = null
                    },
                    placeholder = { Text("https://example.com/video.mp4") },
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
                    singleLine = false,
                    maxLines = 3,
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
                                text = "Supports MP4, MKV, HLS (.m3u8), DASH (.mpd), and web links",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp)
                )

                // Warning & prompt if stream requires yt-dlp and it's missing
                AnimatedVisibility(
                    visible = needsYtdlp && !isYtdlpInstalled,
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically()
                ) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.6f),
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            MaterialTheme.colorScheme.error.copy(alpha = 0.4f)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Warning,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(20.dp)
                                )
                                Text(
                                    text = "yt-dlp Engine Required",
                                    fontWeight = FontWeight.SemiBold,
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.onErrorContainer
                                )
                            }
                            Text(
                                text = "This URL does not end with a direct video extension or points to a web platform. The yt-dlp environment is required to parse and play it.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.9f)
                            )
                            FilledTonalButton(
                                onClick = {
                                    onDismiss()
                                    onNavigateToYtdlpSettings()
                                },
                                shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.filledTonalButtonColors(
                                    containerColor = MaterialTheme.colorScheme.error,
                                    contentColor = MaterialTheme.colorScheme.onError
                                ),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Settings,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Install yt-dlp in Settings",
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }
                }

                // Demo links chips
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    SuggestionChip(
                        onClick = {
                            urlText = "https://storage.googleapis.com/exoplayer-test-media-0/BigBuckBunny_320x180.mp4"
                            errorText = null
                        },
                        label = { Text("MP4 Demo") },
                        shape = RoundedCornerShape(8.dp)
                    )
                    SuggestionChip(
                        onClick = {
                            urlText = "https://cph-p2p-msl.akamaized.net/hls/live/2000341/test/master.m3u8"
                            errorText = null
                        },
                        label = { Text("HLS Adaptive Demo") },
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
                    if (needsYtdlp && !isYtdlpInstalled) {
                        errorText = "yt-dlp must be installed in Settings before playing this stream"
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
    isYtdlpInstalled: Boolean,
    onDismiss: () -> Unit,
    onNavigateToYtdlpSettings: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        dragHandle = { BottomSheetDefaults.DragHandle() }
    ) {
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
                        text = "Network Streaming Guide",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Stream engine capabilities and yt-dlp support",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = onDismiss) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = "Close"
                    )
                }
            }

            // yt-dlp Status Card
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = if (isYtdlpInstalled) {
                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                } else {
                    MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f)
                },
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    if (isYtdlpInstalled) MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)
                    else MaterialTheme.colorScheme.error.copy(alpha = 0.4f)
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        imageVector = if (isYtdlpInstalled) Icons.Filled.CheckCircle else Icons.Filled.Warning,
                        contentDescription = null,
                        tint = if (isYtdlpInstalled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(28.dp)
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (isYtdlpInstalled) "yt-dlp Environment Installed" else "yt-dlp Not Installed",
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleSmall
                        )
                        Text(
                            text = if (isYtdlpInstalled) {
                                "YouTube and web platform stream extraction is fully operational."
                            } else {
                                "Required for playing YouTube and other web platform video URLs."
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Section 1: Direct media streams
            InfoCardItem(
                icon = Icons.Filled.Movie,
                title = "Direct Media Files",
                subtitle = "Progressive HTTP/HTTPS",
                description = "Direct files (.mp4, .mkv, .webm, .avi, .mov, .flv, .ts) play directly using native MPV hardware decoders."
            )

            // Section 2: Adaptive streams
            InfoCardItem(
                icon = Icons.Filled.Tune,
                title = "Adaptive Manifests (HLS / DASH)",
                subtitle = ".m3u8 and .mpd Multi-Bitrate",
                description = "Nosved Player inspects real multi-bitrate streams dynamically, allowing you to select specific resolutions (144p to 4K) or toggle Data Saver."
            )

            // Section 3: Web Platform Links & yt-dlp
            InfoCardItem(
                icon = Icons.Filled.Language,
                title = "Web Platforms (YouTube, Twitch, etc.)",
                subtitle = "Requires yt-dlp + Python 3.13",
                description = "Websites do not serve raw video files directly. Nosved Player uses an embedded yt-dlp engine to extract live video/audio formats and stream them into MPV seamlessly."
            )

            // Action button to yt-dlp settings
            Button(
                onClick = onNavigateToYtdlpSettings,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = Icons.Filled.Settings,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (isYtdlpInstalled) "Manage yt-dlp Settings" else "Open yt-dlp Streaming Settings",
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
