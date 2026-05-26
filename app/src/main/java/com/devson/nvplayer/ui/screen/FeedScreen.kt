package com.devson.nvplayer.ui.screen

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.devson.nvplayer.model.Video
import com.devson.nvplayer.player.MPVSurfaceView
import com.devson.nvplayer.player.PlayerState
import com.devson.nvplayer.util.formatDuration
import com.devson.nvplayer.viewmodel.FeedViewModel
import kotlinx.coroutines.delay

// 
// FeedScreen
// 

/**
 * Reels/Shorts-style vertical video feed.
 *
 * Design decisions:
 *  • **Single engine**: [FeedViewModel] holds ONE [com.devson.nvplayer.player.MPVPlayerEngine].
 *    MPVLib is a native singleton; instantiating it per-page would crash.
 *  • **Smart rendering**: Only the *settled* (active) page gets the real
 *    [MPVSurfaceView]. All other pages show a lightweight [AsyncImage] thumbnail.
 *    This prevents MPVLib from needing to juggle multiple surfaces.
 *  • **Auto-play on settle**: [LaunchedEffect(pagerState.settledPage)] fires once
 *    after the user lifts their finger and the page animation completes. Only then
 *    does [FeedViewModel.onPageSettled] load + play the new video.
 *  • **Lifecycle**: A [DisposableEffect] tied to the [LifecycleOwner] pauses
 *    the engine on ON_STOP and resumes it on ON_START, matching system expectations.
 *
 * @param videos    The ordered list of videos to display in the feed.
 * @param startIndex The page index to open at (defaults to 0).
 * @param onBack    Called when the user presses the system back button.
 */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun FeedScreen(
    videos: List<Video>,
    engine: com.devson.nvplayer.player.MPVPlayerEngine,
    startIndex: Int = 0,
    onBack: () -> Unit = {}
) {
    val viewModel: FeedViewModel = viewModel(
        factory = FeedViewModel.Factory(engine)
    )

    // Push the video list into the VM once (or when the list reference changes).
    LaunchedEffect(videos) {
        viewModel.setVideos(videos)
    }

    val pagerState = rememberPagerState(
        initialPage = startIndex.coerceIn(0, (videos.size - 1).coerceAtLeast(0)),
        pageCount   = { videos.size }
    )

    val isPlaying    by viewModel.isPlaying.collectAsState()
    val playerState  by viewModel.playbackState.collectAsState()
    val currentIndex by viewModel.currentIndex.collectAsState()

    //  Auto-play on settle 
    LaunchedEffect(pagerState.settledPage) {
        viewModel.onPageSettled(pagerState.settledPage)
    }

    // Pause as soon as the composable leaves the tree (user presses Back).
    // This fires before ON_STOP so video decoding stops the moment they navigate away.
    DisposableEffect(Unit) {
        onDispose { viewModel.pause() }
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
        if (videos.isEmpty()) {
            FeedEmptyState()
            return@Box
        }

        VerticalPager(
            state    = pagerState,
            modifier = Modifier.fillMaxSize()
        ) { pageIndex ->
            val video      = videos[pageIndex]
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
                onTogglePlay = { viewModel.togglePlayback() }
            )
        }

        //  Top bar overlay 
        FeedTopBar(
            modifier = Modifier.align(Alignment.TopCenter),
            onBack   = onBack
        )

        //  Page indicator dots 
        FeedPageIndicator(
            total    = videos.size,
            current  = pagerState.currentPage,
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 12.dp)
        )
    }
}

// 
// FeedPage – one cell of the pager
// 

@Composable
private fun FeedPage(
    video: Video,
    isActive: Boolean,
    engine: com.devson.nvplayer.player.MPVPlayerEngine,
    isPlaying: Boolean,
    playerState: PlayerState,
    onTogglePlay: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Show/hide the play-icon feedback for a moment after a tap.
    var showPlayIcon by remember { mutableStateOf(false) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInput(Unit) {
                detectTapGestures {
                    onTogglePlay()
                    showPlayIcon = true
                }
            }
    ) {
        //  Video surface (active page only) or thumbnail (inactive pages) 
        if (isActive) {
            // The real MPV surface. AndroidView keeps the SurfaceView instance
            // stable across recompositions so MPVLib's surface callbacks fire
            // correctly (created → attached → changed → detached).
            AndroidView(
                factory = { ctx ->
                    MPVSurfaceView(ctx).also { view ->
                        // surfaceCreated fires automatically via SurfaceHolder.Callback.
                        // Nothing extra needed here.
                    }
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
                contentScale = ContentScale.Crop,
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
        FeedMetadata(
            video    = video,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 20.dp, end = 72.dp, bottom = 40.dp)
        )

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
    }
}

// 
// Sub-composables
// 

@Composable
private fun FeedTopBar(
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
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
    }
}

@Composable
private fun FeedMetadata(
    video: Video,
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
            overflow   = TextOverflow.Ellipsis
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
