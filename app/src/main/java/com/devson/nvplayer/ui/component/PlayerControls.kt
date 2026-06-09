package com.devson.nvplayer.ui.component

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import android.os.BatteryManager
import android.content.res.Configuration
import androidx.compose.ui.platform.LocalConfiguration
import com.devson.nvplayer.model.PlayerButton
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.geometry.Offset
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.FormatListBulleted
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerControls(
    title: String,
    isPlaying: Boolean,
    isSmartEnhanceEnabled: Boolean = false,
    currentPosition: Long,
    bufferedPosition: Long = 0L,
    isNetworkStream: Boolean = false,
    duration: Long,
    isDragging: Boolean,
    onDraggingChanged: (Boolean) -> Unit,
    onPlayPauseToggle: () -> Unit,
    onSeek: (Long, Boolean) -> Unit,
    onSpeedClick: () -> Unit,
    onEnhanceClick: () -> Unit,
    onShowChapters: () -> Unit = {},
    hasChapters: Boolean = false,
    currentDecoder: String = "AUTO",
    onShowDecoder: () -> Unit = {},
    onCycleSubtitle: () -> Unit,
    onCycleAudio: () -> Unit,
    onBackClick: () -> Unit,
    playbackSpeed: Float,
    seekBarStyle: String = "standard",
    hasNext: Boolean = false,
    hasPrevious: Boolean = false,
    onNextClick: () -> Unit = {},
    onPrevClick: () -> Unit = {},
    showSeekButtons: Boolean = true,
    showNextPrevButtons: Boolean = true,
    showElapsedTimeOverlay: Boolean = false,
    showRemainingTime: Boolean = false,
    showBatteryClockOverlay: Boolean = false,
    showScreenRotationButton: Boolean = true,
    seekDurationSeconds: Int = 10,
    controlIconSize: String = "medium",
    topLeftButtons: List<PlayerButton> = emptyList(),
    topRightButtons: List<PlayerButton> = emptyList(),
    bottomLeftButtons: List<PlayerButton> = emptyList(),
    bottomRightButtons: List<PlayerButton> = emptyList(),
    portraitTopLeftButtons: List<PlayerButton> = emptyList(),
    portraitTopRightButtons: List<PlayerButton> = emptyList(),
    portraitBottomButtons: List<PlayerButton> = emptyList(),
    onLockClick: () -> Unit = {},
    onAspectClick: () -> Unit = {},
    onPipClick: () -> Unit = {},
    currentAspectMode: com.devson.nvplayer.player.AspectMode = com.devson.nvplayer.player.AspectMode.FIT,
    isBackgroundPlayEnabled: Boolean = false,
    onBackgroundPlayClick: () -> Unit = {},
    ytdlQuality: Int = -1,
    onShowQuality: () -> Unit = {},
    isBottomLayoutEnabled: Boolean = false,
    showControlGradients: Boolean = true,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    val configuration = LocalConfiguration.current
    val isPortrait = configuration.orientation == Configuration.ORIENTATION_PORTRAIT

    var isLeftExpanded by remember { mutableStateOf(false) }
    var isRightExpanded by remember { mutableStateOf(false) }

    val phaseShiftState = remember { mutableFloatStateOf(0f) }
    LaunchedEffect(isPlaying) {
        if (isPlaying) {
            var lastTime = withFrameNanos { it }
            while (true) {
                withFrameNanos { frameTime ->
                    val deltaSeconds = (frameTime - lastTime) / 1_000_000_000f
                    lastTime = frameTime
                    val deltaPhase = (deltaSeconds / 1.5f) * (2f * Math.PI).toFloat()
                    phaseShiftState.floatValue = (phaseShiftState.floatValue + deltaPhase) % (2f * Math.PI).toFloat()
                }
            }
        }
    }

    val glowAlpha by if (isSmartEnhanceEnabled && isPlaying) {
        val infiniteTransition = rememberInfiniteTransition(label = "glowPulse")
        infiniteTransition.animateFloat(
            initialValue = 0.3f,
            targetValue = 0.9f,
            animationSpec = infiniteRepeatable(
                animation = tween(1200, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "glowAlpha"
        )
    } else {
        remember { mutableStateOf(if (isSmartEnhanceEnabled) 0.8f else 0f) }
    }

    val themePrimary = MaterialTheme.colorScheme.primary
    val glowBrush = if (isSmartEnhanceEnabled) {
        Brush.linearGradient(
            colors = listOf(
                themePrimary.copy(alpha = glowAlpha),
                MaterialTheme.colorScheme.secondary.copy(alpha = glowAlpha)
            )
        )
    } else null

    // Decouple local slider state to stop the visual slider jumping backwards while dragging
    val safeDuration = duration.coerceAtLeast(1L).toFloat()
    var sliderPosition by remember { mutableFloatStateOf(currentPosition.toFloat()) }
    var lastSeekTime by remember { mutableLongStateOf(0L) }
    var draggingJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(currentPosition) {
        if (!isDragging) {
            sliderPosition = currentPosition.toFloat()
        }
    }

    val safeSliderPos = sliderPosition.coerceIn(0f, safeDuration)
    val playScale by animateFloatAsState(targetValue = if (isPlaying) 1.0f else 1.05f, label = "PlayScale")

    Box(
        modifier = modifier
            .fillMaxSize()
            .then(
                if (showControlGradients) {
                    Modifier.background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Black.copy(alpha = 0.65f),
                                Color.Transparent,
                                Color.Black.copy(alpha = 0.8f)
                            )
                        )
                    )
                } else {
                    Modifier
                }
            )
    ) {
        val filterChapters = { list: List<PlayerButton> ->
            if (hasChapters) list else list.filter { it != PlayerButton.CHAPTERS }
        }

        val sizeKey = controlIconSize.lowercase()
        val playCircleSize = when (sizeKey) {
            "small" -> if (isPortrait) 46.dp else 52.dp
            "large" -> if (isPortrait) 64.dp else 72.dp
            else    -> if (isPortrait) 54.dp else 60.dp  // "medium" default
        }
        val playIconSize = when (sizeKey) {
            "small" -> if (isPortrait) 22.dp else 26.dp
            "large" -> if (isPortrait) 36.dp else 42.dp
            else    -> if (isPortrait) 28.dp else 32.dp
        }
        val actionCircleSize = when (sizeKey) {
            "small" -> if (isPortrait) 40.dp else 46.dp
            "large" -> if (isPortrait) 54.dp else 60.dp
            else    -> if (isPortrait) 46.dp else 52.dp
        }
        val actionIconSize = when (sizeKey) {
            "small" -> if (isPortrait) 18.dp else 22.dp
            "large" -> if (isPortrait) 26.dp else 30.dp
            else    -> if (isPortrait) 22.dp else 26.dp
        }
        val centerSpacing = if (isPortrait) 14.dp else 24.dp

        val controlButton = @Composable { button: com.devson.nvplayer.model.PlayerButton ->
            RenderPlayerButton(
                button = button,
                modifier = Modifier,
                isPortrait = false,
                title = title,
                showElapsedTimeOverlay = showElapsedTimeOverlay,
                currentPosition = currentPosition,
                currentDecoder = currentDecoder,
                hasChapters = hasChapters,
                isSmartEnhanceEnabled = isSmartEnhanceEnabled,
                glowBrush = glowBrush,
                glowAlpha = glowAlpha,
                themePrimary = themePrimary,
                onBackClick = onBackClick,
                onShowDecoder = onShowDecoder,
                onShowChapters = onShowChapters,
                onCycleSubtitle = onCycleSubtitle,
                onCycleAudio = onCycleAudio,
                onEnhanceClick = onEnhanceClick,
                onSpeedClick = onSpeedClick,
                onLockClick = onLockClick,
                onAspectClick = onAspectClick,
                onPipClick = onPipClick,
                activity = activity,
                currentAspectMode = currentAspectMode,
                isBackgroundPlayEnabled = isBackgroundPlayEnabled,
                onBackgroundPlayClick = onBackgroundPlayClick,
                ytdlQuality = ytdlQuality,
                onShowQuality = onShowQuality
            )
        }

        val chevronButton = @Composable { icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit ->
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.08f))
                    .border(1.dp, Color.White.copy(alpha = 0.15f), CircleShape)
                    .clickable { onClick() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = "Toggle Expand",
                    tint = Color.White.copy(alpha = 0.85f),
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        val leftRegion = @Composable { buttons: List<com.devson.nvplayer.model.PlayerButton>, isExpanded: Boolean, onExpandedChange: (Boolean) -> Unit ->
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (buttons.size <= 2) {
                    buttons.forEach { button ->
                        controlButton(button)
                    }
                } else {
                    if (!isExpanded) {
                        buttons.take(2).forEach { button ->
                            controlButton(button)
                        }
                        chevronButton(Icons.Rounded.ChevronRight) { onExpandedChange(true) }
                    } else {
                        buttons.take(2).forEach { button ->
                            controlButton(button)
                        }
                        AnimatedVisibility(
                            visible = isExpanded,
                            enter = fadeIn() + expandHorizontally(),
                            exit = fadeOut() + shrinkHorizontally()
                        ) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                buttons.drop(2).forEach { button ->
                                    controlButton(button)
                                }
                            }
                        }
                        chevronButton(Icons.Rounded.ChevronLeft) { onExpandedChange(false) }
                    }
                }
            }
        }

        val rightRegion = @Composable { buttons: List<com.devson.nvplayer.model.PlayerButton>, isExpanded: Boolean, onExpandedChange: (Boolean) -> Unit ->
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (buttons.size <= 2) {
                    buttons.forEach { button ->
                        controlButton(button)
                    }
                } else {
                    if (!isExpanded) {
                        chevronButton(Icons.Rounded.ChevronLeft) { onExpandedChange(true) }
                        buttons.take(2).forEach { button ->
                            controlButton(button)
                        }
                    } else {
                        chevronButton(Icons.Rounded.ChevronRight) { onExpandedChange(false) }
                        AnimatedVisibility(
                            visible = isExpanded,
                            enter = fadeIn() + expandHorizontally(),
                            exit = fadeOut() + shrinkHorizontally()
                        ) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                buttons.drop(2).forEach { button ->
                                    controlButton(button)
                                }
                            }
                        }
                        buttons.take(2).forEach { button ->
                            controlButton(button)
                        }
                    }
                }
            }
        }

        val bottomPlaybackControls = @Composable {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Skip Previous
                if (showNextPrevButtons) {
                    Box(
                        modifier = Modifier
                            .size(actionCircleSize)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = if (hasPrevious) 0.06f else 0.02f))
                            .border(1.dp, Color.White.copy(alpha = if (hasPrevious) 0.1f else 0.03f), CircleShape)
                            .clickable(enabled = hasPrevious) { onPrevClick() },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.SkipPrevious,
                            contentDescription = "Previous Video",
                            tint = Color.White.copy(alpha = if (hasPrevious) 1f else 0.3f),
                            modifier = Modifier.size(actionIconSize)
                        )
                    }
                }

                // Rewind Button
                if (showSeekButtons) {
                    val rewindIcon = when (seekDurationSeconds) {
                        5 -> Icons.Rounded.Replay5
                        30 -> Icons.Rounded.Replay30
                        else -> Icons.Rounded.Replay10
                    }
                    Box(
                        modifier = Modifier
                            .size(actionCircleSize)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.06f))
                            .border(1.dp, Color.White.copy(alpha = 0.1f), CircleShape)
                            .clickable { onSeek((currentPosition - seekDurationSeconds * 1000L).coerceAtLeast(0L), true) },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = rewindIcon,
                            contentDescription = "Rewind ${seekDurationSeconds}s",
                            tint = Color.White,
                            modifier = Modifier.size(actionIconSize)
                        )
                    }
                }

                // Play/Pause Button
                val playScale by animateFloatAsState(targetValue = if (isPlaying) 1.0f else 1.05f, label = "PlayScale")
                Box(
                    modifier = Modifier
                        .size(playCircleSize)
                        .clip(CircleShape)
                        .background(
                            Brush.radialGradient(
                                colors = listOf(Color.White.copy(alpha = 0.15f), Color.White.copy(alpha = 0.05f))
                            )
                        )
                        .border(1.dp, Color.White.copy(alpha = 0.25f), CircleShape)
                        .scale(playScale)
                        .clickable { onPlayPauseToggle() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Rounded.Pause else Icons.Filled.PlayArrow,
                        contentDescription = if (isPlaying) "Pause" else "Play",
                        tint = Color.White,
                        modifier = Modifier.size(playIconSize)
                    )
                }

                // Forward Button
                if (showSeekButtons) {
                    val forwardIcon = when (seekDurationSeconds) {
                        5 -> Icons.Rounded.Forward5
                        30 -> Icons.Rounded.Forward30
                        else -> Icons.Rounded.Forward10
                    }
                    Box(
                        modifier = Modifier
                            .size(actionCircleSize)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.06f))
                            .border(1.dp, Color.White.copy(alpha = 0.1f), CircleShape)
                            .clickable { onSeek((currentPosition + seekDurationSeconds * 1000L).coerceAtMost(duration), true) },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = forwardIcon,
                            contentDescription = "Forward ${seekDurationSeconds}s",
                            tint = Color.White,
                            modifier = Modifier.size(actionIconSize)
                        )
                    }
                }

                // Skip Next
                if (showNextPrevButtons) {
                    Box(
                        modifier = Modifier
                            .size(actionCircleSize)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = if (hasNext) 0.06f else 0.02f))
                            .border(1.dp, Color.White.copy(alpha = if (hasNext) 0.1f else 0.03f), CircleShape)
                            .clickable(enabled = hasNext) { onNextClick() },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.SkipNext,
                            contentDescription = "Next Video",
                            tint = Color.White.copy(alpha = if (hasNext) 1f else 0.3f),
                            modifier = Modifier.size(actionIconSize)
                        )
                    }
                }
            }
        }

        // 1. TOP PANEL - separate lists for landscape vs portrait orientations
        val effectiveTopLeft = filterChapters(if (isPortrait) portraitTopLeftButtons else topLeftButtons)
        val rawTopRight = filterChapters(if (isPortrait) portraitTopRightButtons else topRightButtons)
        val effectiveTopRight = if (isNetworkStream && !rawTopRight.contains(PlayerButton.STREAM_QUALITY)) {
            rawTopRight + PlayerButton.STREAM_QUALITY
        } else {
            rawTopRight
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopStart)
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                effectiveTopLeft.forEach { button ->
                    RenderPlayerButton(
                        button = button,
                        modifier = if (button == PlayerButton.VIDEO_TITLE) Modifier.weight(1f) else Modifier,
                        isPortrait = isPortrait,
                        title = title,
                        showElapsedTimeOverlay = showElapsedTimeOverlay,
                        currentPosition = currentPosition,
                        currentDecoder = currentDecoder,
                        hasChapters = hasChapters,
                        isSmartEnhanceEnabled = isSmartEnhanceEnabled,
                        glowBrush = glowBrush,
                        glowAlpha = glowAlpha,
                        themePrimary = themePrimary,
                        onBackClick = onBackClick,
                        onShowDecoder = onShowDecoder,
                        onShowChapters = onShowChapters,
                        onCycleSubtitle = onCycleSubtitle,
                        onCycleAudio = onCycleAudio,
                        onEnhanceClick = onEnhanceClick,
                        onSpeedClick = onSpeedClick,
                        onLockClick = onLockClick,
                        onAspectClick = onAspectClick,
                        onPipClick = onPipClick,
                        activity = activity,
                        currentAspectMode = currentAspectMode,
                        isBackgroundPlayEnabled = isBackgroundPlayEnabled,
                        onBackgroundPlayClick = onBackgroundPlayClick,
                        ytdlQuality = ytdlQuality,
                        onShowQuality = onShowQuality
                    )
                }
            }
            Spacer(modifier = Modifier.width(8.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                effectiveTopRight.forEach { button ->
                    RenderPlayerButton(
                        button = button,
                        modifier = Modifier,
                        isPortrait = isPortrait,
                        title = title,
                        showElapsedTimeOverlay = showElapsedTimeOverlay,
                        currentPosition = currentPosition,
                        currentDecoder = currentDecoder,
                        hasChapters = hasChapters,
                        isSmartEnhanceEnabled = isSmartEnhanceEnabled,
                        glowBrush = glowBrush,
                        glowAlpha = glowAlpha,
                        themePrimary = themePrimary,
                        onBackClick = onBackClick,
                        onShowDecoder = onShowDecoder,
                        onShowChapters = onShowChapters,
                        onCycleSubtitle = onCycleSubtitle,
                        onCycleAudio = onCycleAudio,
                        onEnhanceClick = onEnhanceClick,
                        onSpeedClick = onSpeedClick,
                        onLockClick = onLockClick,
                        onAspectClick = onAspectClick,
                        onPipClick = onPipClick,
                        activity = activity,
                        currentAspectMode = currentAspectMode,
                        isBackgroundPlayEnabled = isBackgroundPlayEnabled,
                        onBackgroundPlayClick = onBackgroundPlayClick,
                        ytdlQuality = ytdlQuality,
                        onShowQuality = onShowQuality
                    )
                }
            }
        }

        // 2. CENTER PANEL (Playback Actions)
        if (!isBottomLayoutEnabled) {
            Row(
                modifier = Modifier.align(Alignment.Center),
                horizontalArrangement = Arrangement.spacedBy(centerSpacing),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Skip Previous
                if (showNextPrevButtons) {
                    Box(
                        modifier = Modifier
                            .size(actionCircleSize)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = if (hasPrevious) 0.06f else 0.02f))
                            .border(1.dp, Color.White.copy(alpha = if (hasPrevious) 0.1f else 0.03f), CircleShape)
                            .clickable(enabled = hasPrevious) { onPrevClick() },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.SkipPrevious,
                            contentDescription = "Previous Video",
                            tint = Color.White.copy(alpha = if (hasPrevious) 1f else 0.3f),
                            modifier = Modifier.size(actionIconSize)
                        )
                    }
                }

                // Rewind Button
                if (showSeekButtons) {
                    val rewindIcon = when (seekDurationSeconds) {
                        5 -> Icons.Rounded.Replay5
                        30 -> Icons.Rounded.Replay30
                        else -> Icons.Rounded.Replay10
                    }
                    Box(
                        modifier = Modifier
                            .size(actionCircleSize)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.06f))
                            .border(1.dp, Color.White.copy(alpha = 0.1f), CircleShape)
                            .clickable { onSeek((currentPosition - seekDurationSeconds * 1000L).coerceAtLeast(0L), true) },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = rewindIcon,
                            contentDescription = "Rewind ${seekDurationSeconds}s",
                            tint = Color.White,
                            modifier = Modifier.size(actionIconSize)
                        )
                    }
                }

                // Modern Play/Pause Circle Button
                Box(
                    modifier = Modifier
                        .size(playCircleSize)
                        .clip(CircleShape)
                        .background(
                            Brush.radialGradient(
                                colors = listOf(Color.White.copy(alpha = 0.15f), Color.White.copy(alpha = 0.05f))
                            )
                        )
                        .border(1.dp, Color.White.copy(alpha = 0.25f), CircleShape)
                        .scale(playScale)
                        .clickable { onPlayPauseToggle() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Rounded.Pause else Icons.Filled.PlayArrow,
                        contentDescription = if (isPlaying) "Pause" else "Play",
                        tint = Color.White,
                        modifier = Modifier.size(playIconSize)
                    )
                }

                // Forward Button
                if (showSeekButtons) {
                    val forwardIcon = when (seekDurationSeconds) {
                        5 -> Icons.Rounded.Forward5
                        30 -> Icons.Rounded.Forward30
                        else -> Icons.Rounded.Forward10
                    }
                    Box(
                        modifier = Modifier
                            .size(actionCircleSize)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.06f))
                            .border(1.dp, Color.White.copy(alpha = 0.1f), CircleShape)
                            .clickable { onSeek((currentPosition + seekDurationSeconds * 1000L).coerceAtMost(duration), true) },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = forwardIcon,
                            contentDescription = "Forward ${seekDurationSeconds}s",
                            tint = Color.White,
                            modifier = Modifier.size(actionIconSize)
                        )
                    }
                }

                // Skip Next
                if (showNextPrevButtons) {
                    Box(
                        modifier = Modifier
                            .size(actionCircleSize)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = if (hasNext) 0.06f else 0.02f))
                            .border(1.dp, Color.White.copy(alpha = if (hasNext) 0.1f else 0.03f), CircleShape)
                            .clickable(enabled = hasNext) { onNextClick() },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.SkipNext,
                            contentDescription = "Next Video",
                            tint = Color.White.copy(alpha = if (hasNext) 1f else 0.3f),
                            modifier = Modifier.size(actionIconSize)
                        )
                    }
                }
            }
        }

        // 3. BOTTOM PANEL (Timers & Seekbar)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomStart)
                .navigationBarsPadding()
                .padding(start = 16.dp, end = 16.dp, bottom = 20.dp)
        ) {
            // Timers Row
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                val durationText = if (showRemainingTime) {
                    "-${formatTime((duration - currentPosition).coerceAtLeast(0L))}"
                } else {
                    formatTime(duration)
                }
                Text(
                    text = "${formatTime(currentPosition)} / $durationText",
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )
            }

            Spacer(modifier = Modifier.height(2.dp))

            // Premium Theme Slider Seekbar
            Slider(
                value = safeSliderPos,
                onValueChange = { newVal ->
                    draggingJob?.cancel()
                    onDraggingChanged(true)
                    sliderPosition = newVal
                    val now = System.currentTimeMillis()
                    if (now - lastSeekTime > 150L) {
                        lastSeekTime = now
                        onSeek(newVal.toLong(), false)
                    }
                },
                onValueChangeFinished = {
                    onSeek(sliderPosition.toLong(), true)
                    draggingJob = scope.launch {
                        delay(800) // Provides ExoPlayer/MPV debounce buffering window
                        onDraggingChanged(false)
                    }
                },
                valueRange = 0f..safeDuration,
                modifier = Modifier.fillMaxWidth().height(24.dp),
                colors = SliderDefaults.colors(
                    thumbColor = themePrimary,
                    
                    activeTrackColor = themePrimary,
                    inactiveTrackColor = Color.White.copy(alpha = 0.3f)
                ),
                thumb = { _ ->
                    Box(
                        modifier = Modifier
                            .size(if (isDragging) 16.dp else 12.dp)
                            .clip(CircleShape)
                            .background(themePrimary)
                    )
                },
                track = { sliderState ->
                    val rawFraction = ((sliderState.value - 0f) / safeDuration)
                    val safeFraction = if (rawFraction.isNaN()) 0f else rawFraction.coerceIn(0f, 1f)
                    val bufferFraction = if (safeDuration > 0f) (bufferedPosition.toFloat() / safeDuration).coerceIn(0f, 1f) else 0f
                    val inactiveColor = Color.White.copy(alpha = 0.15f)
                    val bufferedColor = themePrimary.copy(alpha = 0.45f)
                    
                    when (seekBarStyle) {
                        "wavy" -> {
                            val phaseShift = phaseShiftState.floatValue

                            androidx.compose.foundation.Canvas(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(24.dp)
                            ) {
                                val width = size.width
                                val height = size.height
                                val centerY = height / 2
                                val activeWidth = width * safeFraction
                                val bufferedWidth = width * bufferFraction
                                
                                val amplitude = 4.dp.toPx()
                                val wavelength = 20.dp.toPx()
                                
                                // 1. Draw inactive flat track (only from activeProgress to end of canvas)
                                if (activeWidth < width) {
                                    drawLine(
                                        color = inactiveColor,
                                        start = Offset(activeWidth, centerY),
                                        end = Offset(width, centerY),
                                        strokeWidth = 4.dp.toPx(),
                                        cap = StrokeCap.Round
                                    )
                                }
                                
                                // 2. Draw buffered flat track (only for network streams from activeProgress to bufferedProgress)
                                if (isNetworkStream && bufferedWidth > activeWidth) {
                                    drawLine(
                                        color = bufferedColor,
                                        start = Offset(activeWidth, centerY),
                                        end = Offset(bufferedWidth, centerY),
                                        strokeWidth = 4.dp.toPx(),
                                        cap = StrokeCap.Round
                                    )
                                }
                                
                                // 3. Draw active wavy track (clip to activeProgress to prevent drawing past thumb)
                                if (activeWidth > 0) {
                                    val activePath = Path().apply {
                                        moveTo(0f, centerY)
                                        var x = 0f
                                        val endX = activeWidth + wavelength
                                        while (x <= endX) {
                                            val y = centerY + amplitude * kotlin.math.sin((2 * Math.PI * x / wavelength).toFloat() - phaseShift)
                                            lineTo(x, y)
                                            x += 2f
                                        }
                                    }
                                    clipRect(right = activeWidth) {
                                        drawPath(
                                            path = activePath,
                                            color = themePrimary,
                                            style = Stroke(
                                                width = 3.dp.toPx(),
                                                cap = StrokeCap.Round
                                            )
                                        )
                                    }
                                }
                            }
                        }
                        "thick" -> {
                            Box(
                                modifier = Modifier.fillMaxWidth().height(20.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Box(
                                    modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)).background(inactiveColor)
                                ) {
                                    // Buffered Layer
                                    if (isNetworkStream && bufferFraction > 0f) {
                                        Box(modifier = Modifier.fillMaxWidth(bufferFraction.coerceIn(0f, 1f)).height(8.dp).background(bufferedColor))
                                    }
                                    // Active Layer
                                    Box(modifier = Modifier.fillMaxWidth(safeFraction.coerceIn(0f, 1f)).height(8.dp).background(themePrimary))
                                }
                            }
                        }
                        else -> {
                            // Standard/Line Style
                            Box(
                                modifier = Modifier.fillMaxWidth().height(20.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Box(
                                    modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)).background(inactiveColor)
                                ) {
                                    // Buffered Layer
                                    if (isNetworkStream && bufferFraction > 0f) {
                                        Box(modifier = Modifier.fillMaxWidth(bufferFraction.coerceIn(0f, 1f)).height(4.dp).background(bufferedColor))
                                    }
                                    // Active Layer
                                    Box(modifier = Modifier.fillMaxWidth(safeFraction.coerceIn(0f, 1f)).height(4.dp).background(themePrimary))
                                }
                            }
                        }
                    }
                }
            )

            Spacer(modifier = Modifier.height(8.dp))

            if (isPortrait) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val filteredButtons = filterChapters(portraitBottomButtons)
                    if (isBottomLayoutEnabled) {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                filteredButtons.forEach { button ->
                                    RenderPlayerButton(
                                        button = button,
                                        modifier = Modifier,
                                        isPortrait = true,
                                        title = title,
                                        showElapsedTimeOverlay = showElapsedTimeOverlay,
                                        currentPosition = currentPosition,
                                        currentDecoder = currentDecoder,
                                        hasChapters = hasChapters,
                                        isSmartEnhanceEnabled = isSmartEnhanceEnabled,
                                        glowBrush = glowBrush,
                                        glowAlpha = glowAlpha,
                                        themePrimary = themePrimary,
                                        onBackClick = onBackClick,
                                        onShowDecoder = onShowDecoder,
                                        onShowChapters = onShowChapters,
                                        onCycleSubtitle = onCycleSubtitle,
                                        onCycleAudio = onCycleAudio,
                                        onEnhanceClick = onEnhanceClick,
                                        onSpeedClick = onSpeedClick,
                                        onLockClick = onLockClick,
                                        onAspectClick = onAspectClick,
                                        onPipClick = onPipClick,
                                        activity = activity,
                                        currentAspectMode = currentAspectMode,
                                        isBackgroundPlayEnabled = isBackgroundPlayEnabled,
                                        onBackgroundPlayClick = onBackgroundPlayClick
                                    )
                                }
                            }
                            bottomPlaybackControls()
                        }
                    } else {
                        filteredButtons.forEach { button ->
                            RenderPlayerButton(
                                button = button,
                                modifier = Modifier,
                                isPortrait = true,
                                title = title,
                                showElapsedTimeOverlay = showElapsedTimeOverlay,
                                currentPosition = currentPosition,
                                currentDecoder = currentDecoder,
                                hasChapters = hasChapters,
                                isSmartEnhanceEnabled = isSmartEnhanceEnabled,
                                glowBrush = glowBrush,
                                glowAlpha = glowAlpha,
                                themePrimary = themePrimary,
                                onBackClick = onBackClick,
                                onShowDecoder = onShowDecoder,
                                onShowChapters = onShowChapters,
                                onCycleSubtitle = onCycleSubtitle,
                                onCycleAudio = onCycleAudio,
                                onEnhanceClick = onEnhanceClick,
                                onSpeedClick = onSpeedClick,
                                onLockClick = onLockClick,
                                onAspectClick = onAspectClick,
                                onPipClick = onPipClick,
                                activity = activity,
                                currentAspectMode = currentAspectMode,
                                isBackgroundPlayEnabled = isBackgroundPlayEnabled,
                                onBackgroundPlayClick = onBackgroundPlayClick
                            )
                        }
                    }
                }
            } else {
                Row(
                    modifier = Modifier
                        .fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (isBottomLayoutEnabled) {
                        Row(
                            modifier = Modifier.weight(1f),
                            horizontalArrangement = Arrangement.Start,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            leftRegion(filterChapters(bottomLeftButtons), isLeftExpanded) { isLeftExpanded = it }
                        }

                        Box(
                            modifier = Modifier.padding(horizontal = 16.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            bottomPlaybackControls()
                        }

                        Row(
                            modifier = Modifier.weight(1f),
                            horizontalArrangement = Arrangement.End,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            rightRegion(filterChapters(bottomRightButtons), isRightExpanded) { isRightExpanded = it }
                        }
                    } else {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            leftRegion(filterChapters(bottomLeftButtons), isLeftExpanded) { isLeftExpanded = it }
                        }

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            rightRegion(filterChapters(bottomRightButtons), isRightExpanded) { isRightExpanded = it }
                        }
                    }
                }
            }
        }
    }
}

fun formatTime(milliseconds: Long): String {
    val totalSeconds = milliseconds / 1000
    val seconds = totalSeconds % 60
    val minutes = (totalSeconds / 60) % 60
    val hours = totalSeconds / 3600

    return if (hours > 0) {
        String.format(Locale.getDefault(), "%02d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds)
    }
}

@Composable
private fun BatteryAndClockOverlay(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var batteryPercentage by remember { mutableIntStateOf(100) }
    var currentTime by remember { mutableStateOf("") }
    LaunchedEffect(Unit) {
        val batteryManager = context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
        val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
        while (true) {
            batteryPercentage = batteryManager?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) ?: 100
            currentTime = timeFormat.format(Date())
            delay(10000L)
        }
    }
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(Color.White.copy(alpha = 0.08f))
            .border(1.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(12.dp))
            .padding(horizontal = 10.dp, vertical = 6.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                imageVector = Icons.Rounded.BatteryChargingFull,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(16.dp)
            )
            Text(
                text = "$batteryPercentage%",
                color = Color.White,
                fontWeight = FontWeight.SemiBold,
                fontSize = 13.sp
            )
            Spacer(
                modifier = Modifier
                    .width(1.dp)
                    .height(14.dp)
                    .background(Color.White.copy(alpha = 0.25f))
            )
            Icon(
                imageVector = Icons.Rounded.Schedule,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(16.dp)
            )
            Text(
                text = currentTime,
                color = Color.White,
                fontWeight = FontWeight.SemiBold,
                fontSize = 13.sp
            )
        }
    }
}

private fun Context.findActivity(): Activity? {
    var context = this
    while (context is ContextWrapper) {
        if (context is Activity) return context
        context = context.baseContext
    }
    return null
}

@Composable
fun RenderPlayerButton(
    button: com.devson.nvplayer.model.PlayerButton,
    modifier: Modifier = Modifier,
    isPortrait: Boolean = false,
    title: String,
    showElapsedTimeOverlay: Boolean,
    currentPosition: Long,
    currentDecoder: String,
    hasChapters: Boolean,
    isSmartEnhanceEnabled: Boolean,
    glowBrush: Brush?,
    glowAlpha: Float,
    themePrimary: Color,
    onBackClick: () -> Unit,
    onShowDecoder: () -> Unit,
    onShowChapters: () -> Unit,
    onCycleSubtitle: () -> Unit,
    onCycleAudio: () -> Unit,
    onEnhanceClick: () -> Unit,
    onSpeedClick: () -> Unit,
    onLockClick: () -> Unit,
    onAspectClick: () -> Unit,
    onPipClick: () -> Unit,
    activity: Activity?,
    currentAspectMode: com.devson.nvplayer.player.AspectMode = com.devson.nvplayer.player.AspectMode.FIT,
    isBackgroundPlayEnabled: Boolean = false,
    onBackgroundPlayClick: () -> Unit = {},
    ytdlQuality: Int = -1,
    onShowQuality: () -> Unit = {}
) {
    if (button == com.devson.nvplayer.model.PlayerButton.VIDEO_TITLE) {
        Column(modifier = modifier) {
            Text(
                text = title,
                color = Color.White,
                fontWeight = FontWeight.SemiBold,
                fontSize = 15.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (showElapsedTimeOverlay) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "Elapsed: ${formatTime(currentPosition)}",
                    color = Color.White.copy(alpha = 0.7f),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Normal
                )
            }
        }
        return
    }

    if (button == com.devson.nvplayer.model.PlayerButton.NONE) {
        return
    }

    val onClick = {
        when (button) {
            com.devson.nvplayer.model.PlayerButton.BACK_ARROW -> onBackClick()
            com.devson.nvplayer.model.PlayerButton.DECODER -> onShowDecoder()
            com.devson.nvplayer.model.PlayerButton.CHAPTERS -> if (hasChapters) onShowChapters() else {}
            com.devson.nvplayer.model.PlayerButton.SUBTITLES -> onCycleSubtitle()
            com.devson.nvplayer.model.PlayerButton.AUDIO_TRACK -> onCycleAudio()
            com.devson.nvplayer.model.PlayerButton.SMART_ENHANCE -> onEnhanceClick()
            com.devson.nvplayer.model.PlayerButton.MORE_OPTIONS -> onSpeedClick()
            com.devson.nvplayer.model.PlayerButton.LOCK_CONTROLS -> onLockClick()
            com.devson.nvplayer.model.PlayerButton.PICTURE_IN_PICTURE -> onPipClick()
            com.devson.nvplayer.model.PlayerButton.ASPECT_RATIO -> onAspectClick()
            com.devson.nvplayer.model.PlayerButton.BACKGROUND_PLAY -> onBackgroundPlayClick()
            com.devson.nvplayer.model.PlayerButton.STREAM_QUALITY -> onShowQuality()
            com.devson.nvplayer.model.PlayerButton.SCREEN_ROTATION -> {
                activity?.let { act ->
                    val currentOrientation = act.requestedOrientation
                    if (currentOrientation == ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE ||
                        currentOrientation == ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE) {
                        act.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                    } else {
                        act.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                    }
                }
            }
            else -> {}
        }
    }

    val isSmartEnhance = button == com.devson.nvplayer.model.PlayerButton.SMART_ENHANCE
    val isBgPlay = button == com.devson.nvplayer.model.PlayerButton.BACKGROUND_PLAY

    if (isPortrait) {
        Box(
            modifier = modifier
                .clip(RoundedCornerShape(12.dp))
                .then(
                    if (isSmartEnhance && isSmartEnhanceEnabled && glowBrush != null) {
                        Modifier.background(glowBrush)
                    } else if (isBgPlay && isBackgroundPlayEnabled) {
                        Modifier.background(themePrimary.copy(alpha = 0.25f))
                    } else {
                        Modifier.background(Color.White.copy(alpha = 0.08f))
                    }
                )
                .then(
                    if (isSmartEnhance && isSmartEnhanceEnabled) {
                        Modifier.border(
                            width = 1.5.dp,
                            brush = Brush.linearGradient(
                                colors = listOf(
                                    themePrimary.copy(alpha = (glowAlpha + 0.15f).coerceAtMost(1f)),
                                    themePrimary.copy(alpha = (glowAlpha + 0.15f).coerceAtMost(1f))
                                )
                            ),
                            shape = RoundedCornerShape(12.dp)
                        )
                    } else if (isBgPlay && isBackgroundPlayEnabled) {
                        Modifier.border(1.dp, themePrimary, RoundedCornerShape(12.dp))
                    } else {
                        Modifier.border(1.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(12.dp))
                    }
                )
                .clickable { onClick() }
                .padding(horizontal = 10.dp, vertical = 6.dp),
            contentAlignment = Alignment.Center
        ) {
            if (button == com.devson.nvplayer.model.PlayerButton.DECODER) {
                Text(
                    text = currentDecoder,
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 4.dp)
                )
            } else if (button == com.devson.nvplayer.model.PlayerButton.STREAM_QUALITY) {
                val label = if (ytdlQuality == -1) "Auto" else "${ytdlQuality}p"
                Text(
                    text = label,
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 4.dp)
                )
            } else {
                val icon = if (button == com.devson.nvplayer.model.PlayerButton.ASPECT_RATIO) {
                    when (currentAspectMode) {
                        com.devson.nvplayer.player.AspectMode.FIT -> AspectIcons.Fit
                        com.devson.nvplayer.player.AspectMode.STRETCH -> AspectIcons.Stretch
                        com.devson.nvplayer.player.AspectMode.CROP -> AspectIcons.Crop
                        com.devson.nvplayer.player.AspectMode.ORIGINAL -> AspectIcons.Original
                    }
                } else {
                    button.icon
                }
                Icon(
                    imageVector = icon,
                    contentDescription = button.displayName,
                    tint = if ((isSmartEnhance && isSmartEnhanceEnabled) || (isBgPlay && isBackgroundPlayEnabled)) themePrimary else Color.White.copy(alpha = 0.85f),
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    } else {
        Box(
            modifier = modifier
                .size(40.dp)
                .clip(CircleShape)
                .then(
                    if (isSmartEnhance && isSmartEnhanceEnabled && glowBrush != null) {
                        Modifier.background(glowBrush)
                    } else if (isBgPlay && isBackgroundPlayEnabled) {
                        Modifier.background(themePrimary.copy(alpha = 0.25f))
                    } else {
                        Modifier.background(Color.White.copy(alpha = 0.08f))
                    }
                )
                .then(
                    if (isSmartEnhance && isSmartEnhanceEnabled) {
                        Modifier.border(
                            width = 1.5.dp,
                            brush = Brush.linearGradient(
                                colors = listOf(
                                    themePrimary.copy(alpha = (glowAlpha + 0.15f).coerceAtMost(1f)),
                                    themePrimary.copy(alpha = (glowAlpha + 0.15f).coerceAtMost(1f))
                                )
                            ),
                            shape = CircleShape
                        )
                    } else if (isBgPlay && isBackgroundPlayEnabled) {
                        Modifier.border(1.dp, themePrimary, CircleShape)
                    } else {
                        Modifier.border(1.dp, Color.White.copy(alpha = 0.15f), CircleShape)
                    }
                )
                .clickable { onClick() },
            contentAlignment = Alignment.Center
        ) {
            if (button == com.devson.nvplayer.model.PlayerButton.DECODER) {
                Text(
                    text = currentDecoder,
                    color = Color.White,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            } else if (button == com.devson.nvplayer.model.PlayerButton.STREAM_QUALITY) {
                val label = if (ytdlQuality == -1) "Auto" else "${ytdlQuality}p"
                Text(
                    text = label,
                    color = Color.White,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            } else {
                val icon = if (button == com.devson.nvplayer.model.PlayerButton.ASPECT_RATIO) {
                    when (currentAspectMode) {
                        com.devson.nvplayer.player.AspectMode.FIT -> AspectIcons.Fit
                        com.devson.nvplayer.player.AspectMode.STRETCH -> AspectIcons.Stretch
                        com.devson.nvplayer.player.AspectMode.CROP -> AspectIcons.Crop
                        com.devson.nvplayer.player.AspectMode.ORIGINAL -> AspectIcons.Original
                    }
                } else {
                    button.icon
                }
                Icon(
                    imageVector = icon,
                    contentDescription = button.displayName,
                    tint = if ((isSmartEnhance && isSmartEnhanceEnabled) || (isBgPlay && isBackgroundPlayEnabled)) themePrimary else Color.White.copy(alpha = 0.85f),
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}