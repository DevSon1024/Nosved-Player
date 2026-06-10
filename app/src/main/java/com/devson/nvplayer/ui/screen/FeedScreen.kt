package com.devson.nvplayer.ui.screen

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.devson.nvplayer.domain.model.Video
import com.devson.nvplayer.player.engine.MPVSurfaceView
import com.devson.nvplayer.player.engine.PlayerState
import com.devson.nvplayer.util.formatDuration
import com.devson.nvplayer.viewmodel.FeedViewModel
import com.devson.nvplayer.viewmodel.FilterMode
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.FilterList
import com.devson.nvplayer.player.engine.MPVPlayerEngine
import kotlinx.coroutines.delay

// FeedScreen 

// Reels/Shorts-style vertical video feed.
@OptIn(
    androidx.compose.foundation.ExperimentalFoundationApi::class,
    androidx.compose.material3.ExperimentalMaterial3Api::class
)
@Composable
fun FeedScreen(
    videos: List<Video>,
    engine: MPVPlayerEngine,
    startIndex: Int = 0,
    onBack: () -> Unit = {},
    onPlayVideoInPlayer: (Video, List<Video>) -> Unit = { _, _ -> }
) {
    val context = LocalContext.current
    val viewModel: FeedViewModel = viewModel(
        factory = FeedViewModel.Factory(engine, context.applicationContext)
    )

    var controlsVisible by remember { mutableStateOf(true) }

    //  Status bar: force white icons so they are visible on the black feed background,
    //  and restore the original status bar appearance when leaving FeedScreen.
    val view = LocalView.current
    DisposableEffect(view) {
        val window = (view.context as? android.app.Activity)?.window
        val controller = window?.let { WindowInsetsControllerCompat(it, view) }
        val originalLightStatusBars = controller?.isAppearanceLightStatusBars ?: true

        controller?.isAppearanceLightStatusBars = false  // false = white icons

        onDispose {
            controller?.isAppearanceLightStatusBars = originalLightStatusBars
        }
    }

    // Push the video list into the VM once (or when the list reference changes).
    LaunchedEffect(videos) {
        viewModel.setVideos(videos)
    }

    val filteredVideos by viewModel.filteredVideos.collectAsState()

    val hasVideos = filteredVideos.isNotEmpty()
    val pagerState = key(hasVideos) {
        rememberPagerState(
            initialPage = startIndex.coerceIn(0, (filteredVideos.size - 1).coerceAtLeast(0)),
            pageCount   = { filteredVideos.size }
        )
    }

    val isPlaying    by viewModel.isPlaying.collectAsState()
    val playerState  by viewModel.playbackState.collectAsState()
    val currentIndex by viewModel.currentIndex.collectAsState()

    //  Auto-play on settle or filter change
    LaunchedEffect(pagerState.settledPage, filteredVideos) {
        viewModel.onPageSettled(pagerState.settledPage)
    }

    // Pause as soon as the composable leaves the tree (user presses Back).
    // This fires before ON_STOP so video decoding stops the moment they navigate away.
    DisposableEffect(Unit) {
        onDispose {
            if (!viewModel.skipPauseOnDispose) {
                viewModel.pause()
            }
        }
    }

    // Pause / resume when the Activity goes to background / foreground.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> viewModel.play()
                Lifecycle.Event.ON_STOP  -> viewModel.pause()
                else                     -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    //  Root container 
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        if (filteredVideos.isEmpty()) {
            FeedEmptyState()
            return@Box
        }

        VerticalPager(
            state    = pagerState,
            modifier = Modifier.fillMaxSize()
        ) { pageIndex ->
            val video = filteredVideos.getOrNull(pageIndex)
            if (video != null) {
                // Use settledPage (not currentPage) so the surface is only attached/detached
                // once the scroll animation is fully done. Using currentPage would destroy and
                // recreate the MPVSurfaceView mid-swipe, causing a GPU spike on every scroll.
                val isActivePage = pagerState.settledPage == pageIndex

                FeedPage(
                    video       = video,
                    isActive    = isActivePage,
                    engine      = viewModel.engine,
                    isPlaying   = isPlaying,
                    playerState = playerState,
                    controlsVisible = controlsVisible,
                    onControlsVisibleChange = { controlsVisible = it },
                    onSpeedChange = { speed -> viewModel.setPlaybackSpeed(speed) },
                    onSurfaceAttached = { viewModel.onSurfaceAttached() },
                    onSurfaceDetached = { viewModel.onSurfaceDetached() },
                    onTitleClick = {
                        viewModel.skipPauseOnDispose = true
                        onPlayVideoInPlayer(video, filteredVideos)
                    },
                    onTogglePlay = { viewModel.togglePlayback() }
                )
            }
        }

        //  Top bar overlay 
        AnimatedVisibility(
            visible = controlsVisible,
            enter = fadeIn(tween(200)),
            exit = fadeOut(tween(200)),
            modifier = Modifier.align(Alignment.TopCenter)
        ) {
            val filterMode by viewModel.filterMode.collectAsState()
            FeedTopBar(
                onBack = onBack,
                currentFilterMode = filterMode,
                onFilterModeChange = { viewModel.setFilterMode(it) }
            )
        }

        //  Page indicator dots 
        AnimatedVisibility(
            visible = controlsVisible,
            enter = fadeIn(tween(200)),
            exit = fadeOut(tween(200)),
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 12.dp)
        ) {
            FeedPageIndicator(
                total    = filteredVideos.size,
                current  = pagerState.currentPage
            )
        }

        // Slim seekbar + remaining-time at the bottom
        AnimatedVisibility(
            visible = controlsVisible,
            enter = fadeIn(tween(200)),
            exit = fadeOut(tween(200)),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(bottom = 8.dp, start = 16.dp, end = 16.dp)
        ) {
            val currentPos by viewModel.currentPosition.collectAsState()
            val videoDuration by viewModel.duration.collectAsState()
            val remaining = (videoDuration - currentPos).coerceAtLeast(0L)

            Column(modifier = Modifier.fillMaxWidth()) {
                // Remaining time label aligned to the right
                Text(
                    text = "-${formatDuration(remaining)}",
                    color = Color.White.copy(alpha = 0.75f),
                    fontSize = 11.sp,
                    textAlign = TextAlign.End,
                    modifier = Modifier.fillMaxWidth()
                )

                Slider(
                    value = currentPos.toFloat(),
                    onValueChange = { newVal ->
                        viewModel.seekTo(newVal.toLong())
                    },
                    valueRange = 0f..videoDuration.coerceAtLeast(1L).toFloat(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(16.dp),
                    colors = SliderDefaults.colors(
                        activeTrackColor = MaterialTheme.colorScheme.primary,
                        inactiveTrackColor = Color.White.copy(alpha = 0.25f)
                    ),
                    track = { sliderState ->
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(4.dp)
                                .background(Color.White.copy(alpha = 0.25f), RoundedCornerShape(2.dp))
                        ) {
                            val fraction = if (videoDuration > 0) sliderState.value / videoDuration.toFloat() else 0f
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(fraction.coerceIn(0f, 1f))
                                    .height(4.dp)
                                    .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(2.dp))
                            )
                        }
                    },
                    thumb = {}
                )
            }
        }
    }
}

// 
// FeedPage – one cell of the pager
// 

@Composable
private fun FeedPage(
    video: Video,
    isActive: Boolean,
    engine: MPVPlayerEngine,
    isPlaying: Boolean,
    playerState: PlayerState,
    controlsVisible: Boolean,
    onControlsVisibleChange: (Boolean) -> Unit,
    onSpeedChange: (Float) -> Unit,
    onSurfaceAttached: () -> Unit,
    onSurfaceDetached: () -> Unit,
    onTitleClick: () -> Unit,
    onTogglePlay: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Show/hide the play-icon feedback for a moment after a tap.
    var showPlayIcon by remember { mutableStateOf(false) }
    var is2xSpeedActive by remember { mutableStateOf(false) }

    val currentControlsVisible by rememberUpdatedState(controlsVisible)
    val currentOnControlsVisibleChange by rememberUpdatedState(onControlsVisibleChange)
    val currentOnSpeedChange by rememberUpdatedState(onSpeedChange)
    val currentOnTogglePlay by rememberUpdatedState(onTogglePlay)

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val startX = down.position.x
                        val width = size.width.toFloat()
                        val isLeft = startX < width * 0.35f
                        val isRight = startX > width * 0.65f
                        val isCenter = !isLeft && !isRight

                        var currentChange = down
                        val change = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
                            while (true) {
                                val event = awaitPointerEvent()
                                val anyDown = event.changes.any { it.pressed }
                                if (!anyDown) {
                                    break
                                }
                                val checkChange = event.changes.firstOrNull { it.id == down.id }
                                if (checkChange != null) {
                                    currentChange = checkChange
                                    val dx = checkChange.position.x - down.position.x
                                    val dy = checkChange.position.y - down.position.y
                                    if (dx * dx + dy * dy > 900f) {
                                        break
                                    }
                                }
                            }
                            currentChange
                        }

                        if (change == null) {
                            // Long press detected!
                            var controlsHiddenByLongPress = false

                            if (!currentControlsVisible) {
                                currentOnControlsVisibleChange(true)
                            } else {
                                if (isCenter) {
                                    currentOnControlsVisibleChange(false)
                                    controlsHiddenByLongPress = true
                                } else {
                                    is2xSpeedActive = true
                                    currentOnSpeedChange(2.0f)
                                }
                            }
                            // Always wait for release to avoid restarting gestures pre-maturely
                            while (true) {
                                val event = awaitPointerEvent()
                                val anyPressed = event.changes.any { it.pressed }
                                if (!anyPressed) {
                                    break
                                }
                            }
                            if (is2xSpeedActive) {
                                is2xSpeedActive = false
                                currentOnSpeedChange(1.0f)
                            }
                            // Restore visibility when the finger is lifted
                            if (controlsHiddenByLongPress) {
                                currentOnControlsVisibleChange(true)
                            }
                        } else {
                            // Tap detected!
                            val dx = currentChange.position.x - down.position.x
                            val dy = currentChange.position.y - down.position.y
                            if (dx * dx + dy * dy < 900f) {
                                if (currentControlsVisible && isCenter) {
                                    currentOnTogglePlay()
                                    showPlayIcon = true
                                }
                            }
                        }
                    }
                }
            }
    ) {
        //  Video surface (active page only) or thumbnail (inactive pages) 
        if (isActive) {
            // Keep callbacks fresh across recompositions
            val latestOnSurfaceAttached by rememberUpdatedState(onSurfaceAttached)
            val latestOnSurfaceDetached by rememberUpdatedState(onSurfaceDetached)

            // AndroidView keeps the MPVSurfaceView instance stable so MPVLib
            // surface callbacks fire correctly (created → changed → destroyed).
            // Surface callbacks are wired to notify the ViewModel so it can
            // gate loadVideo() until a surface is actually available (fixes the
            // blank-screen-on-cold-start race condition).
            AndroidView(
                factory = { ctx ->
                    MPVSurfaceView(ctx).also { mpvView ->
                        mpvView.onSurfaceCreatedListener = { latestOnSurfaceAttached() }
                        mpvView.onSurfaceDestroyedListener = { latestOnSurfaceDetached() }
                    }
                },
                update = { mpvView ->
                    // Refresh listener references so the latest ViewModel callbacks are used
                    mpvView.onSurfaceCreatedListener = { latestOnSurfaceAttached() }
                    mpvView.onSurfaceDestroyedListener = { latestOnSurfaceDetached() }
                },
                modifier = Modifier.fillMaxSize()
            )
        } else {
            // Lightweight placeholder shown while this page is off-screen.
            // Coil's coil-video integration decodes a frame from the video URI.
            val thumbnailData = video.thumbnailUri?.let { android.net.Uri.parse(it) }
                ?: android.net.Uri.parse(video.uri)

            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(thumbnailData)
                    .crossfade(true)
                    .build(),
                contentDescription = video.title,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize()
            )
        }

        //  Gradient scrim (bottom) 
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.45f)
                .align(Alignment.BottomCenter)
                .background(
                    Brush.verticalGradient(
                        listOf(Color.Transparent, Color.Black.copy(alpha = 0.85f))
                    )
                )
        )

        //  Video metadata overlay 
        AnimatedVisibility(
            visible = controlsVisible,
            enter = fadeIn(tween(200)),
            exit = fadeOut(tween(200)),
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 20.dp, end = 72.dp, bottom = 50.dp)
        ) {
            FeedMetadata(
                video        = video,
                onTitleClick = onTitleClick
            )
        }

        //  Loading indicator 
        if (isActive && playerState is PlayerState.Loading) {
            CircularProgressIndicator(
                modifier = Modifier.align(Alignment.Center),
                color    = Color.White,
                strokeWidth = 2.dp
            )
        }

        //  Tap-to-play/pause icon flash 
        LaunchedEffect(showPlayIcon) {
            if (showPlayIcon) {
                delay(700)
                showPlayIcon = false
            }
        }
        AnimatedVisibility(
            visible = showPlayIcon,
            enter   = fadeIn(tween(120)),
            exit    = fadeOut(tween(400)),
            modifier = Modifier.align(Alignment.Center)
        ) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.55f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (isPlaying) Icons.Default.Pause
                    else Icons.Default.PlayArrow,
                    contentDescription = if (isPlaying) "Pause" else "Play",
                    tint   = Color.White,
                    modifier = Modifier.size(36.dp)
                )
            }
        }

        // 2X Speed boost overlay
        AnimatedVisibility(
            visible = is2xSpeedActive,
            enter = fadeIn(tween(150)),
            exit = fadeOut(tween(150)),
            modifier = Modifier.align(Alignment.TopCenter)
        ) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = Color.Black.copy(alpha = 0.6f),
                modifier = Modifier.padding(top = 80.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.FastForward,
                        contentDescription = "2x Speed",
                        tint = Color.White
                    )
                    Text(
                        text = "2X Speed",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                }
            }
        }
    }
}

// 
// Sub-composables
// 

@Composable
private fun FeedTopBar(
    onBack: () -> Unit,
    currentFilterMode: FilterMode,
    onFilterModeChange: (FilterMode) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onBack) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Back",
                tint = Color.White
            )
        }
        Text(
            text       = "Feed",
            color      = Color.White,
            fontWeight = FontWeight.Bold,
            fontSize   = 20.sp,
            modifier   = Modifier.padding(start = 4.dp)
        )

        Spacer(modifier = Modifier.weight(1f))

        Box {
            IconButton(onClick = { expanded = true }) {
                Icon(
                    imageVector = Icons.Default.FilterList,
                    contentDescription = "Filter",
                    tint = Color.White
                )
            }
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
                modifier = Modifier.background(MaterialTheme.colorScheme.surface)
            ) {
                DropdownMenuItem(
                    text = { Text("All") },
                    onClick = {
                        onFilterModeChange(FilterMode.ALL)
                        expanded = false
                    },
                    leadingIcon = {
                        if (currentFilterMode == FilterMode.ALL) {
                            Icon(Icons.Default.Check, contentDescription = null)
                        }
                    }
                )
                DropdownMenuItem(
                    text = { Text("Portrait") },
                    onClick = {
                        onFilterModeChange(FilterMode.PORTRAIT)
                        expanded = false
                    },
                    leadingIcon = {
                        if (currentFilterMode == FilterMode.PORTRAIT) {
                            Icon(Icons.Default.Check, contentDescription = null)
                        }
                    }
                )
                DropdownMenuItem(
                    text = { Text("Landscape") },
                    onClick = {
                        onFilterModeChange(FilterMode.LANDSCAPE)
                        expanded = false
                    },
                    leadingIcon = {
                        if (currentFilterMode == FilterMode.LANDSCAPE) {
                            Icon(Icons.Default.Check, contentDescription = null)
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun FeedMetadata(
    video: Video,
    onTitleClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(
            text       = video.title,
            color      = Color.White,
            fontWeight = FontWeight.Bold,
            fontSize   = 15.sp,
            maxLines   = 2,
            overflow   = TextOverflow.Ellipsis,
            modifier   = Modifier.clickable { onTitleClick() }
        )
        Text(
            text     = video.folderName,
            color    = Color.White.copy(alpha = 0.7f),
            fontSize = 12.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Surface(
            shape  = RoundedCornerShape(4.dp),
            color  = Color.White.copy(alpha = 0.15f)
        ) {
            Text(
                text     = formatDuration(video.duration),
                color    = Color.White,
                fontSize = 11.sp,
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
            )
        }
    }
}

@Composable
private fun FeedPageIndicator(
    total: Int,
    current: Int,
    modifier: Modifier = Modifier
) {
    if (total <= 1) return
    Column(
        modifier          = modifier,
        verticalArrangement = Arrangement.spacedBy(4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        val visibleCount = minOf(total, 7)
        val startIndex   = (current - visibleCount / 2).coerceIn(0, (total - visibleCount).coerceAtLeast(0))
        repeat(visibleCount) { i ->
            val dotIndex  = startIndex + i
            val isSelected = dotIndex == current
            Box(
                modifier = Modifier
                    .size(if (isSelected) 8.dp else 5.dp)
                    .clip(CircleShape)
                    .background(
                        if (isSelected) Color.White
                        else Color.White.copy(alpha = 0.35f)
                    )
            )
        }
    }
}

@Composable
private fun FeedEmptyState() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text     = "No videos to show",
            color    = Color.White.copy(alpha = 0.6f),
            fontSize = 16.sp
        )
    }
}
