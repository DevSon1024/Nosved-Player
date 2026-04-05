package com.devson.nosvedplayer.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.systemBars
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.material.icons.filled.Forward5
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.Forward30
import androidx.compose.material.icons.filled.Replay5
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.Replay30
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.devson.nosvedplayer.model.TrackInfo
import com.devson.nosvedplayer.model.Video
import com.devson.nosvedplayer.viewmodel.SeekBarStyle
import kotlinx.coroutines.delay
import coil.compose.AsyncImage
import coil.request.ImageRequest
import coil.request.CachePolicy
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.foundation.Canvas
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun YoutubeStylePlayerControls(
    modifier: Modifier = Modifier,
    isVisible: Boolean,
    isPlaying: Boolean,
    title: String,
    currentPosition: Long,
    duration: Long,
    seekDurationSeconds: Int = 10,
    seekBarStyle: SeekBarStyle = SeekBarStyle.DEFAULT,
    fastplaySpeed: Float = 2.0f,
    hasPrevious: Boolean = false,
    hasNext: Boolean = false,
    isLandscape: Boolean = false,
    isLocked: Boolean = false,
    audioTracks: List<TrackInfo> = emptyList(),
    selectedAudioIndex: Int = -1,
    subtitleTracks: List<TrackInfo> = emptyList(),
    selectedSubtitleIndex: Int = -1,
    playlist: List<Video> = emptyList(),
    currentPlaylistIndex: Int = -1,
    onPlayPauseToggle: () -> Unit,
    onSeekTo: (Long) -> Unit,
    onSeekForward: () -> Unit,
    onSeekBackward: () -> Unit,
    onBack: () -> Unit,
    onToggleLock: () -> Unit,
    onSelectAudio: (Int) -> Unit,
    onSelectSubtitle: (Int?) -> Unit,
    onSpeedChange: (Float) -> Unit,
    onPlayPrevious: () -> Unit = {},
    onPlayNext: () -> Unit = {},
    onPlayFromPlaylist: (Int) -> Unit = {},
    onToggleResizeMode: (() -> Unit)? = null,
    onPipToggle: (() -> Unit)? = null,
    onControlsStateChange: () -> Unit = {},
    currentPlaybackSpeed: Float = 1f,
    onFastForwardActive: (Boolean) -> Unit = {},
    onOpenPlaybackSettings: () -> Unit = {},
    onOpenAudioTracks: () -> Unit = {},
    onOpenSubtitles: () -> Unit = {},
    showSeekLeft: Boolean = false,
    showSeekRight: Boolean = false,
    isFastForwarding: Boolean = false,
    showSeekButtons: Boolean = true
) {
    var showPlaylistPanel by remember { mutableStateOf(false) }
    var isSeeking by remember { mutableStateOf(false) }
    var seekPreview by remember { mutableStateOf(0L) }
    var lastDraggedPos by remember { mutableStateOf(0L) }

    val displayedPosition = if (isSeeking) seekPreview else currentPosition

    Box(modifier = modifier.fillMaxSize()) {
        SeekIndicator(
            side = SeekSide.Left,
            visible = showSeekLeft,
            seconds = seekDurationSeconds,
            modifier = Modifier.align(Alignment.CenterStart)
        )
        SeekIndicator(
            side = SeekSide.Right,
            visible = showSeekRight,
            seconds = seekDurationSeconds,
            modifier = Modifier.align(Alignment.CenterEnd)
        )

        AnimatedVisibility(
            visible = isVisible && !isFastForwarding,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.fillMaxSize()
        ) {
            if (isLocked) {
                Box(modifier = Modifier.fillMaxSize()) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(start = 8.dp, top = 4.dp)
                            .background(Color.Black.copy(0.5f), RoundedCornerShape(8.dp))
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Spacer(Modifier.height(48.dp))
                            IconButton(
                                onClick = onToggleLock,
                                modifier = Modifier
                                    .background(Color.Black.copy(0.5f), RoundedCornerShape(8.dp))
                            ) {
                                Icon(
                                    Icons.Filled.Lock,
                                    contentDescription = "Unlock",
                                    tint = Color.White,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }
            } else {
                YtControlsLayout(
                    title = title,
                    isPlaying = isPlaying,
                    displayedPosition = displayedPosition,
                    duration = duration,
                    hasPrevious = hasPrevious,
                    hasNext = hasNext,
                    isLandscape = isLandscape,
                    isSeeking = isSeeking,
                    seekBarStyle = seekBarStyle,
                    currentPlaybackSpeed = currentPlaybackSpeed,
                    showPlaylistPanel = showPlaylistPanel,
                    isLocked = isLocked,
                    showSeekButtons = showSeekButtons,
                    seekDurationSeconds = seekDurationSeconds,
                    onBack = onBack,
                    onPlayPauseToggle = onPlayPauseToggle,
                    onSeekForward = onSeekForward,
                    onSeekBackward = onSeekBackward,
                    onPlayPrevious = onPlayPrevious,
                    onPlayNext = onPlayNext,
                    onToggleLock = onToggleLock,
                    onToggleResizeMode = onToggleResizeMode,
                    onPipToggle = onPipToggle,
                    onSettingsClick = onOpenPlaybackSettings,
                    onSubtitleClick = onOpenSubtitles,
                    onAudioTrackClick = onOpenAudioTracks,
                    onTogglePlaylist = { showPlaylistPanel = !showPlaylistPanel },
                    onSeekStart = { pos ->
                        isSeeking = true
                        seekPreview = pos
                        lastDraggedPos = pos
                    },
                    onSeekChange = { pos ->
                        seekPreview = pos
                        lastDraggedPos = pos
                    },
                    onSeekEnd = {
                        isSeeking = false
                        onSeekTo(lastDraggedPos)
                    }
                )
            }
        }

        AnimatedVisibility(
            visible = showPlaylistPanel && playlist.isNotEmpty(),
            enter = slideInVertically { it } + fadeIn(),
            exit = slideOutVertically { it } + fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            UpNextPanel(
                playlist = playlist,
                currentIndex = currentPlaylistIndex,
                onSelectVideo = { index ->
                    onPlayFromPlaylist(index)
                    showPlaylistPanel = false
                },
                onDismiss = { showPlaylistPanel = false }
            )
        }
    }
}

@Composable
fun getRewindIcon(seconds: Int) = when (seconds) {
    5  -> Icons.Filled.Replay5
    10 -> Icons.Filled.Replay10
    30 -> Icons.Filled.Replay30
    else -> Icons.Filled.FastRewind
}

@Composable
fun getForwardIcon(seconds: Int) = when (seconds) {
    5  -> Icons.Filled.Forward5
    10 -> Icons.Filled.Forward10
    30 -> Icons.Filled.Forward30
    else -> Icons.Filled.FastForward
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun YtControlsLayout(
    title: String,
    isPlaying: Boolean,
    displayedPosition: Long,
    duration: Long,
    hasPrevious: Boolean,
    hasNext: Boolean,
    isLandscape: Boolean,
    isSeeking: Boolean,
    seekBarStyle: SeekBarStyle,
    currentPlaybackSpeed: Float,
    showPlaylistPanel: Boolean,
    isLocked: Boolean,
    showSeekButtons: Boolean,
    seekDurationSeconds: Int,
    onBack: () -> Unit,
    onPlayPauseToggle: () -> Unit,
    onSeekForward: () -> Unit,
    onSeekBackward: () -> Unit,
    onPlayPrevious: () -> Unit,
    onPlayNext: () -> Unit,
    onToggleLock: () -> Unit,
    onToggleResizeMode: (() -> Unit)?,
    onPipToggle: (() -> Unit)?,
    onSettingsClick: () -> Unit,
    onSubtitleClick: () -> Unit,
    onAudioTrackClick: () -> Unit,
    onTogglePlaylist: () -> Unit,
    onSeekStart: (Long) -> Unit,
    onSeekChange: (Long) -> Unit,
    onSeekEnd: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color.Black.copy(alpha = 0.7f),
                        Color.Transparent,
                        Color.Black.copy(alpha = 0.85f)
                    )
                )
            )
            .windowInsetsPadding(WindowInsets.systemBars.only(WindowInsetsSides.Vertical))
    ) {
        var showRemainingTime by remember { mutableStateOf(false) }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopStart)
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.Top
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                }
                IconButton(
                    onClick = onToggleLock,
                    modifier = Modifier
                        .background(Color.Black.copy(0.35f), RoundedCornerShape(8.dp))
                ) {
                    Icon(
                        imageVector = Icons.Filled.LockOpen,
                        contentDescription = "Lock screen",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
            Column(modifier = Modifier.weight(1f).padding(horizontal = 4.dp).padding(top = 12.dp)) {
                Text(
                    text = title,
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (onPipToggle != null) {
                IconButton(onClick = onPipToggle) {
                    Icon(Icons.Filled.PictureInPictureAlt, contentDescription = "PIP", tint = Color.White)
                }
            }
            IconButton(onClick = onSubtitleClick) {
                Icon(Icons.Filled.ClosedCaption, contentDescription = "CC / Subtitles", tint = Color.White)
            }
            IconButton(onClick = onAudioTrackClick) {
                Icon(Icons.Filled.Audiotrack, contentDescription = "Audio Track", tint = Color.White)
            }
            IconButton(onClick = onSettingsClick) {
                Icon(Icons.Filled.Settings, contentDescription = "Settings", tint = Color.White)
            }
        }

        Row(
            modifier = Modifier.align(Alignment.Center),
            horizontalArrangement = Arrangement.spacedBy(24.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onPlayPrevious,
                enabled = hasPrevious,
                modifier = Modifier.size(52.dp)
            ) {
                Icon(
                    Icons.Filled.SkipPrevious,
                    contentDescription = "Previous",
                    tint = if (hasPrevious) Color.White else Color.White.copy(0.35f),
                    modifier = Modifier.size(36.dp)
                )
            }

            if (showSeekButtons) {
                IconButton(
                    onClick = onSeekBackward,
                    modifier = Modifier.size(52.dp)
                ) {
                    Icon(
                        getRewindIcon(seekDurationSeconds),
                        contentDescription = "Rewind",
                        tint = Color.White,
                        modifier = Modifier.size(36.dp)
                    )
                }
            }

            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(0.45f)),
                contentAlignment = Alignment.Center
            ) {
                IconButton(onClick = onPlayPauseToggle, modifier = Modifier.size(64.dp)) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        contentDescription = if (isPlaying) "Pause" else "Play",
                        tint = Color.White,
                        modifier = Modifier.size(44.dp)
                    )
                }
            }

            if (showSeekButtons) {
                IconButton(
                    onClick = onSeekForward,
                    modifier = Modifier.size(52.dp)
                ) {
                    Icon(
                        getForwardIcon(seekDurationSeconds),
                        contentDescription = "Fast Forward",
                        tint = Color.White,
                        modifier = Modifier.size(36.dp)
                    )
                }
            }

            IconButton(
                onClick = onPlayNext,
                enabled = hasNext,
                modifier = Modifier.size(52.dp)
            ) {
                Icon(
                    Icons.Filled.SkipNext,
                    contentDescription = "Next",
                    tint = if (hasNext) Color.White else Color.White.copy(0.35f),
                    modifier = Modifier.size(36.dp)
                )
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomStart)
                .padding(horizontal = 12.dp)
                .padding(bottom = if (isLandscape) 8.dp else 16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (showRemainingTime) {
                        val remaining = (duration - displayedPosition).coerceAtLeast(0L)
                        "${formatTime(displayedPosition)} / -${formatTime(remaining)}"
                    } else {
                        "${formatTime(displayedPosition)} / ${formatTime(duration)}"
                    },
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.clickable { showRemainingTime = !showRemainingTime }
                )
                Spacer(Modifier.weight(1f))
                if (currentPlaybackSpeed != 1f) {
                    Text(
                        text = "${currentPlaybackSpeed}x",
                        color = Color.White.copy(0.75f),
                        fontSize = 11.sp,
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(Color.White.copy(0.15f))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                }
                if (onToggleResizeMode != null) {
                    IconButton(onClick = onToggleResizeMode, modifier = Modifier.size(36.dp)) {
                        Icon(Icons.Filled.Crop, contentDescription = "Resize", tint = Color.White, modifier = Modifier.size(20.dp))
                    }
                }
            }

            YtSeekBar(
                position = displayedPosition,
                duration = duration,
                isSeeking = isSeeking,
                onSeekStart = onSeekStart,
                onSeekChange = onSeekChange,
                onSeekEnd = onSeekEnd
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .pointerInput(showPlaylistPanel) {
                        detectVerticalDragGestures { _, dragAmount ->
                            if (dragAmount < -10f && !showPlaylistPanel) {
                                onTogglePlaylist()
                            } else if (dragAmount > 10f && showPlaylistPanel) {
                                onTogglePlaylist()
                            }
                        }
                    }
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onTogglePlaylist
                    )
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = if (showPlaylistPanel) Icons.Filled.KeyboardArrowDown else Icons.Filled.KeyboardArrowUp,
                    contentDescription = if (showPlaylistPanel) "Close playlist" else "Up Next",
                    tint = Color.White.copy(alpha = 0.75f),
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    text = "Up Next",
                    color = Color.White.copy(alpha = 0.75f),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun YtSeekBar(
    position: Long,
    duration: Long,
    isSeeking: Boolean,
    onSeekStart: (Long) -> Unit,
    onSeekChange: (Long) -> Unit,
    onSeekEnd: () -> Unit
) {
    val safeDuration = duration.coerceAtLeast(1L).toFloat()

    // FIX: Decouple local slider state to stop the visual slider jumping backwards while dragging
    var sliderPosition by remember { mutableFloatStateOf(position.toFloat()) }
    var isDragging by remember { mutableStateOf(false) }
    var draggingJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(position) {
        if (!isDragging) {
            sliderPosition = position.toFloat()
        }
    }

    // Safely enforce bounds
    val safeSliderPos = sliderPosition.coerceIn(0f, safeDuration)

    Slider(
        value = safeSliderPos,
        onValueChange = { newVal ->
            draggingJob?.cancel()
            isDragging = true
            sliderPosition = newVal
            val newPos = newVal.toLong()
            if (!isSeeking) onSeekStart(newPos)
            else onSeekChange(newPos)
        },
        onValueChangeFinished = {
            onSeekEnd()
            // Provide ExoPlayer an 800ms debounce buffer to actually update the position
            draggingJob = scope.launch {
                delay(800)
                isDragging = false
            }
        },
        valueRange = 0f..safeDuration,
        modifier = Modifier.fillMaxWidth(),
        colors = SliderDefaults.colors(
            thumbColor = Color(0xFFFF0000),
            activeTrackColor = Color(0xFFFF0000),
            inactiveTrackColor = Color.White.copy(alpha = 0.3f)
        ),
        thumb = {
            Box(
                modifier = Modifier
                    .size(if (isDragging || isSeeking) 18.dp else 14.dp)
                    .clip(CircleShape)
                    .background(Color(0xFFFF0000))
            )
        },
        track = { sliderState ->
            val rawFraction = ((sliderState.value - 0f) / safeDuration)
            val safeFraction = if (rawFraction.isNaN()) 0f else rawFraction.coerceIn(0f, 1f)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(Color.White.copy(alpha = 0.3f))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(safeFraction.coerceAtLeast(0.001f))
                            .height(4.dp)
                            .background(Color(0xFFFF0000))
                    )
                }
            }
        }
    )
}

@Composable
private fun UpNextPanel(
    playlist: List<Video>,
    currentIndex: Int,
    onSelectVideo: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.95f))
            .pointerInput(Unit) {
                detectVerticalDragGestures { _, dragAmount ->
                    if (dragAmount > 10f) {
                        onDismiss()
                    }
                }
            }
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = {}
            )
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .width(40.dp)
                        .height(4.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f))
                        .align(Alignment.Center)
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Up Next",
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 18.sp,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                    Icon(
                        Icons.Filled.KeyboardArrowDown,
                        contentDescription = "Close",
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                contentPadding = PaddingValues(start = 16.dp, top = 0.dp, end = 16.dp, bottom = 24.dp)
            ) {
                itemsIndexed(playlist) { index, video ->
                    VideoCarouselItem(
                        video = video,
                        isCurrent = index == currentIndex,
                        onClick = { onSelectVideo(index) }
                    )
                }
            }
        }
    }
}

@Composable
private fun VideoCarouselItem(
    video: Video,
    isCurrent: Boolean,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .width(160.dp)
            .clickable(enabled = !isCurrent, onClick = onClick)
    ) {
        Box(
            modifier = Modifier
                .width(160.dp)
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(12.dp))
                .background(Color.Black)
                .then(
                    if (isCurrent) Modifier.border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(12.dp))
                    else Modifier
                ),
            contentAlignment = Alignment.Center
        ) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(video.uri)
                    .size(512, 512)
                    .memoryCachePolicy(CachePolicy.ENABLED)
                    .diskCachePolicy(CachePolicy.ENABLED)
                    .crossfade(false)
                    .build(),
                placeholder = ColorPainter(MaterialTheme.colorScheme.surfaceVariant),
                error = ColorPainter(MaterialTheme.colorScheme.surfaceVariant),
                contentDescription = "Video Thumbnail",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )

            if (video.duration > 0L) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(bottom = 6.dp, end = 6.dp)
                        .background(Color.Black.copy(alpha = 0.7f), RoundedCornerShape(4.dp))
                        .padding(horizontal = 4.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = formatTime(video.duration),
                        color = Color.White,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            if (isCurrent) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.6f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.GraphicEq,
                        contentDescription = "Playing",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = video.title,
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = 13.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}

private enum class SeekSide { Left, Right }

@Composable
private fun SeekIndicator(
    side: SeekSide,
    visible: Boolean,
    seconds: Int,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = modifier
    ) {
        Box(
            modifier = Modifier
                .width(120.dp)
                .fillMaxHeight()
                .clip(
                    if (side == SeekSide.Left) RoundedCornerShape(topEnd = 120.dp, bottomEnd = 120.dp)
                    else RoundedCornerShape(topStart = 120.dp, bottomStart = 120.dp)
                )
                .background(Color.White.copy(alpha = 0.18f)),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    imageVector = if (side == SeekSide.Left) Icons.Filled.FastRewind else Icons.Filled.FastForward,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(32.dp)
                )
                Text(
                    text = "$seconds seconds",
                    color = Color.White,
                    fontSize = 12.sp
                )
            }
        }
    }
}

private fun formatTime(timeMs: Long): String {
    val totalSeconds = timeMs / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) "%d:%02d:%02d".format(hours, minutes, seconds)
    else "%02d:%02d".format(minutes, seconds)
}