package com.devson.nvplayer.ui.common

import android.app.Activity
import com.devson.nvplayer.ui.common.components.CustomSeekbar
import android.content.Context
import com.devson.nvplayer.util.findActivity
import android.content.pm.ActivityInfo
import android.os.BatteryManager
import android.content.res.Configuration
import androidx.compose.ui.platform.LocalConfiguration
import com.devson.nvplayer.domain.model.PlayerButton
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.devson.nvplayer.player.model.AspectMode
import com.devson.nvplayer.player.model.HdrType
import com.devson.nvplayer.ui.common.icons.AspectIcons
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val GlassSurface = Color(0xFF121316).copy(alpha = 0.68f)
private val GlassBorder = Color.White.copy(alpha = 0.14f)
private val AccentBlue = Color(0xFF3B82F6)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerControls(
    title: String,
    isPlaying: Boolean,
    isSmartEnhanceEnabled: Boolean = false,
    currentPosition: Long = 0L,
    bufferedPosition: Long = 0L,
    currentPositionProvider: (() -> Long)? = null,
    bufferedPositionProvider: (() -> Long)? = null,
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
    currentDecoder: String = "HW",
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
    showRemainingTime: Boolean = false,
    showBatteryClockOverlay: Boolean = false,
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
    currentAspectMode: AspectMode = AspectMode.FIT,
    isBackgroundPlayEnabled: Boolean = false,
    onBackgroundPlayClick: () -> Unit = {},
    ytdlQuality: Int = -1,
    currentQualityLabel: String = "",
    onShowQuality: () -> Unit = {},
    isBottomLayoutEnabled: Boolean = false,
    showControlGradients: Boolean = true,
    onTitleClick: () -> Unit = {},
    onScreenshotClick: () -> Unit = {},
    resolutionLabel: String? = null,
    videoCodecLabel: String? = null,
    fileSizeLabel: String? = null,
    hdrType: HdrType = HdrType.NONE,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    val configuration = LocalConfiguration.current
    val isPortrait = configuration.orientation == Configuration.ORIENTATION_PORTRAIT

    var isLeftExpanded by remember { mutableStateOf(false) }
    var isRightExpanded by remember { mutableStateOf(false) }

    val themePrimary = MaterialTheme.colorScheme.primary
    val posProvider: () -> Long = currentPositionProvider ?: { currentPosition }
    val bufProvider: () -> Long = bufferedPositionProvider ?: { bufferedPosition }
    val safeDuration = duration.coerceAtLeast(1L).toFloat()
    var lastSeekTime by remember { mutableLongStateOf(0L) }
    var draggingJob by remember { mutableStateOf<Job?>(null) }
    val scope = rememberCoroutineScope()

    Box(
        modifier = modifier
            .fillMaxSize()
    ) {
        // 0. Top & Bottom Vertical Gradient Scrims (Non-clickable)
        if (showControlGradients) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp)
                    .align(Alignment.TopCenter)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Black.copy(alpha = 0.8f),
                                Color.Transparent
                            )
                        )
                    )
            )

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(190.dp)
                    .align(Alignment.BottomCenter)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Transparent,
                                Color.Black.copy(alpha = 0.8f)
                            )
                        )
                    )
            )
        }

        val filterButtons = { list: List<PlayerButton> ->
            list.filter { button ->
                if (button == PlayerButton.CHAPTERS && !hasChapters) return@filter false
                if (button == PlayerButton.STREAM_QUALITY && !isNetworkStream) return@filter false
                true
            }
        }

        val onButtonClick: (PlayerButton) -> Unit = { button ->
            when (button) {
                PlayerButton.BACK_ARROW -> onBackClick()
                PlayerButton.DECODER -> onShowDecoder()
                PlayerButton.CHAPTERS -> if (hasChapters) onShowChapters() else {}
                PlayerButton.SUBTITLES -> onCycleSubtitle()
                PlayerButton.AUDIO_TRACK -> onCycleAudio()
                PlayerButton.SMART_ENHANCE -> onEnhanceClick()
                PlayerButton.MORE_OPTIONS -> onSpeedClick()
                PlayerButton.LOCK_CONTROLS -> onLockClick()
                PlayerButton.PICTURE_IN_PICTURE -> onPipClick()
                PlayerButton.ASPECT_RATIO -> onAspectClick()
                PlayerButton.BACKGROUND_PLAY -> onBackgroundPlayClick()
                PlayerButton.STREAM_QUALITY -> onShowQuality()
                PlayerButton.SCREENSHOT -> onScreenshotClick()
                PlayerButton.SCREEN_ROTATION -> {
                    activity?.let { act ->
                        val currentOrientation = act.requestedOrientation
                        if (currentOrientation == ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE ||
                            currentOrientation == ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                        ) {
                            act.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                        } else {
                            act.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                        }
                    }
                }
                else -> {}
            }
        }

        // 1. TOP PANEL
        val rawTopLeft = filterButtons(if (isPortrait) portraitTopLeftButtons else topLeftButtons)
        val extraTopLeft = rawTopLeft.filter { it != PlayerButton.BACK_ARROW && it != PlayerButton.VIDEO_TITLE }

        val rawTopRight = filterButtons(if (isPortrait) portraitTopRightButtons else topRightButtons)
        val allActiveButtons = if (isPortrait) {
            portraitTopLeftButtons + portraitTopRightButtons + portraitBottomButtons
        } else {
            topLeftButtons + topRightButtons + bottomLeftButtons + bottomRightButtons
        }
        val effectiveTopRight = if (isNetworkStream && !allActiveButtons.contains(PlayerButton.STREAM_QUALITY)) {
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
            // Left Group: Back Button + Two-line Title & Metadata + Extra Top-Left Capsule
            Row(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Back Button (42.dp Circle with glassSurface & glassBorder)
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(GlassSurface)
                        .border(1.dp, GlassBorder, CircleShape)
                        .clickable { onBackClick() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                        contentDescription = "Back",
                        tint = Color.White,
                        modifier = Modifier.size(22.dp)
                    )
                }

                // Two-Line Title & Dynamic Metadata Column
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clickable { onTitleClick() },
                    verticalArrangement = Arrangement.Center
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )

                        if (hdrType != HdrType.NONE) {
                            HdrIndicatorBadge(hdrType = hdrType)
                        }
                    }

                    val metadataSubtitle = listOfNotNull(
                        resolutionLabel,
                        videoCodecLabel,
                        hdrType.badgeText.takeIf { it.isNotBlank() },
                        fileSizeLabel
                    )
                        .filter { it.isNotBlank() }
                        .joinToString(" • ")

                    if (metadataSubtitle.isNotBlank()) {
                        Text(
                            text = metadataSubtitle,
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White.copy(alpha = 0.68f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                if (extraTopLeft.isNotEmpty()) {
                    DynamicControlCapsule(
                        controls = extraTopLeft,
                        currentDecoder = currentDecoder,
                        ytdlQuality = ytdlQuality,
                        currentQualityLabel = currentQualityLabel,
                        currentAspectMode = currentAspectMode,
                        isSmartEnhanceEnabled = isSmartEnhanceEnabled,
                        isBackgroundPlayEnabled = isBackgroundPlayEnabled,
                        themePrimary = themePrimary,
                        onButtonClick = onButtonClick
                    )
                }
            }

            Spacer(modifier = Modifier.width(10.dp))

            // Right Group: Battery & Clock Overlay + Top-Right Capsule
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (showBatteryClockOverlay) {
                    BatteryAndClockOverlay()
                }

                if (effectiveTopRight.isNotEmpty()) {
                    DynamicControlCapsule(
                        controls = effectiveTopRight,
                        currentDecoder = currentDecoder,
                        ytdlQuality = ytdlQuality,
                        currentQualityLabel = currentQualityLabel,
                        currentAspectMode = currentAspectMode,
                        isSmartEnhanceEnabled = isSmartEnhanceEnabled,
                        isBackgroundPlayEnabled = isBackgroundPlayEnabled,
                        themePrimary = themePrimary,
                        onButtonClick = onButtonClick
                    )
                }
            }
        }

        // 2. CENTER PANEL (Playback Transport Dock in Center if not bottom layout)
        if (!isBottomLayoutEnabled) {
            Box(
                modifier = Modifier.align(Alignment.Center)
            ) {
                CenterTransportDock(
                    isPlaying = isPlaying,
                    onPlayPauseToggle = onPlayPauseToggle,
                    showSeekButtons = showSeekButtons,
                    seekDurationSeconds = seekDurationSeconds,
                    onRewindClick = {
                        onSeek((posProvider() - seekDurationSeconds * 1000L).coerceAtLeast(0L), true)
                    },
                    onForwardClick = {
                        onSeek((posProvider() + seekDurationSeconds * 1000L).coerceAtMost(duration), true)
                    },
                    showNextPrevButtons = showNextPrevButtons,
                    hasPrevious = hasPrevious,
                    hasNext = hasNext,
                    onPrevClick = onPrevClick,
                    onNextClick = onNextClick
                )
            }
        }

        // 3. BOTTOM PANEL (Timers, Seekbar & Controls)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomStart)
                .navigationBarsPadding()
                .padding(start = 16.dp, end = 16.dp, bottom = 20.dp)
        ) {
            // Balanced Timestamp Row Above Seekbar
            var trackedPosition by remember { mutableLongStateOf(posProvider()) }
            LaunchedEffect(Unit) {
                while (true) {
                    trackedPosition = posProvider()
                    delay(200L)
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Left Pill: currentPosition / totalDuration
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(GlassSurface)
                        .border(1.dp, GlassBorder, RoundedCornerShape(50))
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "${formatTime(trackedPosition)} / ${formatTime(duration)}",
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        fontFamily = FontFamily.Monospace
                    )
                }

                // Right Pill: remaining time and/or current playback speed
                val remainingTimeText = "-${formatTime((duration - trackedPosition).coerceAtLeast(0L))}"
                val speedText = if (playbackSpeed != 1.0f) "${playbackSpeed}x" else "1.0x"
                val rightPillText = if (showRemainingTime) {
                    "$speedText • $remainingTimeText"
                } else {
                    speedText
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(GlassSurface)
                        .border(1.dp, GlassBorder, RoundedCornerShape(50))
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = rightPillText,
                        color = Color.White.copy(alpha = 0.9f),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }

            Spacer(modifier = Modifier.height(2.dp))

            // Material 3 Expressive Gapped Slider
            CustomSeekbar(
                duration = safeDuration,
                onSeek = { targetPos, isFinished ->
                    if (isFinished) {
                        onSeek(targetPos.toLong(), true)
                        draggingJob = scope.launch {
                            delay(800)
                            onDraggingChanged(false)
                        }
                    } else {
                        draggingJob?.cancel()
                        val now = System.currentTimeMillis()
                        if (now - lastSeekTime > 120L) {
                            lastSeekTime = now
                            onSeek(targetPos.toLong(), false)
                        }
                    }
                },
                onDraggingChanged = { onDraggingChanged(it) },
                seekbarStyle = seekBarStyle,
                isPaused = !isPlaying,
                positionProvider = { posProvider().toFloat() },
                bufferedDurationProvider = {
                    if (isNetworkStream) {
                        val cPos = posProvider()
                        val bPos = bufProvider()
                        if (bPos > cPos) (bPos - cPos).toFloat() else 0f
                    } else 0f
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Below Seekbar Controls
            if (isPortrait) {
                val filteredButtons = filterButtons(portraitBottomButtons)
                if (isBottomLayoutEnabled) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        if (filteredButtons.isNotEmpty()) {
                            DynamicControlCapsule(
                                controls = filteredButtons,
                                currentDecoder = currentDecoder,
                                ytdlQuality = ytdlQuality,
                                currentQualityLabel = currentQualityLabel,
                                currentAspectMode = currentAspectMode,
                                isSmartEnhanceEnabled = isSmartEnhanceEnabled,
                                isBackgroundPlayEnabled = isBackgroundPlayEnabled,
                                themePrimary = themePrimary,
                                onButtonClick = onButtonClick
                            )
                        }

                        CenterTransportDock(
                            isPlaying = isPlaying,
                            onPlayPauseToggle = onPlayPauseToggle,
                            showSeekButtons = showSeekButtons,
                            seekDurationSeconds = seekDurationSeconds,
                            onRewindClick = {
                                onSeek((posProvider() - seekDurationSeconds * 1000L).coerceAtLeast(0L), true)
                            },
                            onForwardClick = {
                                onSeek((posProvider() + seekDurationSeconds * 1000L).coerceAtMost(duration), true)
                            },
                            showNextPrevButtons = showNextPrevButtons,
                            hasPrevious = hasPrevious,
                            hasNext = hasNext,
                            onPrevClick = onPrevClick,
                            onNextClick = onNextClick
                        )
                    }
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (filteredButtons.isNotEmpty()) {
                            DynamicControlCapsule(
                                controls = filteredButtons,
                                currentDecoder = currentDecoder,
                                ytdlQuality = ytdlQuality,
                                currentQualityLabel = currentQualityLabel,
                                currentAspectMode = currentAspectMode,
                                isSmartEnhanceEnabled = isSmartEnhanceEnabled,
                                isBackgroundPlayEnabled = isBackgroundPlayEnabled,
                                themePrimary = themePrimary,
                                onButtonClick = onButtonClick
                            )
                        }
                    }
                }
            } else {
                // Landscape Bottom Controls
                val leftButtons = filterButtons(bottomLeftButtons)
                val rightButtons = filterButtons(bottomRightButtons)

                if (isBottomLayoutEnabled) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Bottom
                    ) {
                        Box(
                            modifier = Modifier.weight(1f),
                            contentAlignment = Alignment.BottomStart
                        ) {
                            BottomZoneWithOverflow(
                                buttons = leftButtons,
                                isExpanded = isLeftExpanded,
                                onExpandedChange = { isLeftExpanded = it },
                                isAlignRight = false,
                                floatingOverflow = true,
                                currentDecoder = currentDecoder,
                                ytdlQuality = ytdlQuality,
                                currentQualityLabel = currentQualityLabel,
                                currentAspectMode = currentAspectMode,
                                isSmartEnhanceEnabled = isSmartEnhanceEnabled,
                                isBackgroundPlayEnabled = isBackgroundPlayEnabled,
                                themePrimary = themePrimary,
                                onButtonClick = onButtonClick
                            )
                        }

                        Box(
                            modifier = Modifier.padding(horizontal = 8.dp),
                            contentAlignment = Alignment.BottomCenter
                        ) {
                            CenterTransportDock(
                                isPlaying = isPlaying,
                                onPlayPauseToggle = onPlayPauseToggle,
                                showSeekButtons = showSeekButtons,
                                seekDurationSeconds = seekDurationSeconds,
                                onRewindClick = {
                                    onSeek((posProvider() - seekDurationSeconds * 1000L).coerceAtLeast(0L), true)
                                },
                                onForwardClick = {
                                    onSeek((posProvider() + seekDurationSeconds * 1000L).coerceAtMost(duration), true)
                                },
                                showNextPrevButtons = showNextPrevButtons,
                                hasPrevious = hasPrevious,
                                hasNext = hasNext,
                                onPrevClick = onPrevClick,
                                onNextClick = onNextClick
                            )
                        }

                        Box(
                            modifier = Modifier.weight(1f),
                            contentAlignment = Alignment.BottomEnd
                        ) {
                            BottomZoneWithOverflow(
                                buttons = rightButtons,
                                isExpanded = isRightExpanded,
                                onExpandedChange = { isRightExpanded = it },
                                isAlignRight = true,
                                floatingOverflow = true,
                                currentDecoder = currentDecoder,
                                ytdlQuality = ytdlQuality,
                                currentQualityLabel = currentQualityLabel,
                                currentAspectMode = currentAspectMode,
                                isSmartEnhanceEnabled = isSmartEnhanceEnabled,
                                isBackgroundPlayEnabled = isBackgroundPlayEnabled,
                                themePrimary = themePrimary,
                                onButtonClick = onButtonClick
                            )
                        }
                    }
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        BottomZoneWithOverflow(
                            buttons = leftButtons,
                            isExpanded = isLeftExpanded,
                            onExpandedChange = { isLeftExpanded = it },
                            isAlignRight = false,
                            floatingOverflow = false,
                            currentDecoder = currentDecoder,
                            ytdlQuality = ytdlQuality,
                            currentQualityLabel = currentQualityLabel,
                            currentAspectMode = currentAspectMode,
                            isSmartEnhanceEnabled = isSmartEnhanceEnabled,
                            isBackgroundPlayEnabled = isBackgroundPlayEnabled,
                            themePrimary = themePrimary,
                            onButtonClick = onButtonClick
                        )

                        BottomZoneWithOverflow(
                            buttons = rightButtons,
                            isExpanded = isRightExpanded,
                            onExpandedChange = { isRightExpanded = it },
                            isAlignRight = true,
                            floatingOverflow = false,
                            currentDecoder = currentDecoder,
                            ytdlQuality = ytdlQuality,
                            currentQualityLabel = currentQualityLabel,
                            currentAspectMode = currentAspectMode,
                            isSmartEnhanceEnabled = isSmartEnhanceEnabled,
                            isBackgroundPlayEnabled = isBackgroundPlayEnabled,
                            themePrimary = themePrimary,
                            onButtonClick = onButtonClick
                        )
                    }
                }
            }
        }
    }
}

/**
 * Reusable Dynamic Control Capsule that renders any assigned list of PlayerButtons
 */
@Composable
fun DynamicControlCapsule(
    controls: List<PlayerButton>,
    modifier: Modifier = Modifier,
    currentDecoder: String = "HW",
    ytdlQuality: Int = -1,
    currentQualityLabel: String = "",
    currentAspectMode: AspectMode = AspectMode.FIT,
    isSmartEnhanceEnabled: Boolean = false,
    isBackgroundPlayEnabled: Boolean = false,
    themePrimary: Color = MaterialTheme.colorScheme.primary,
    onButtonClick: (PlayerButton) -> Unit
) {
    if (controls.isEmpty()) return

    Row(
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(GlassSurface)
            .border(1.dp, GlassBorder, RoundedCornerShape(50))
            .padding(horizontal = 4.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        controls.forEach { button ->
            CapsuleControlItem(
                button = button,
                currentDecoder = currentDecoder,
                ytdlQuality = ytdlQuality,
                currentQualityLabel = currentQualityLabel,
                currentAspectMode = currentAspectMode,
                isSmartEnhanceEnabled = isSmartEnhanceEnabled,
                isBackgroundPlayEnabled = isBackgroundPlayEnabled,
                themePrimary = themePrimary,
                onClick = { onButtonClick(button) }
            )
        }
    }
}

/**
 * UI Indicator badge for HDR / Dolby Vision formats.
 */
@Composable
fun HdrIndicatorBadge(
    hdrType: HdrType,
    modifier: Modifier = Modifier
) {
    if (hdrType == HdrType.NONE) return

    val (bgColor, borderColor, textColor) = when (hdrType) {
        HdrType.DOLBY_VISION -> Triple(
            Color(0xFF2E1065).copy(alpha = 0.85f),
            Color(0xFFA855F7).copy(alpha = 0.75f),
            Color(0xFFF5EEFF)
        )
        HdrType.HDR10_PLUS -> Triple(
            Color(0xFF78350F).copy(alpha = 0.85f),
            Color(0xFFF59E0B).copy(alpha = 0.85f),
            Color(0xFFFEF3C7)
        )
        HdrType.HLG -> Triple(
            Color(0xFF0C4A6E).copy(alpha = 0.85f),
            Color(0xFF38BDF8).copy(alpha = 0.8f),
            Color(0xFFE0F2FE)
        )
        else -> Triple(
            Color(0xFF78350F).copy(alpha = 0.85f),
            Color(0xFFF59E0B).copy(alpha = 0.8f),
            Color(0xFFFEF3C7)
        )
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(4.dp))
            .background(bgColor)
            .border(1.dp, borderColor, RoundedCornerShape(4.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = hdrType.badgeText,
            color = textColor,
            fontSize = 10.sp,
            fontWeight = FontWeight.Black,
            letterSpacing = 0.5.sp,
            textAlign = TextAlign.Center
        )
    }
}

/**
 * Individual item inside the capsule:
 * - Decoder & Quality render as distinct Material 3 Tonal Pills
 * - Standard controls render as borderless 40.dp IconButtons
 */
@Composable
fun CapsuleControlItem(
    button: PlayerButton,
    currentDecoder: String,
    ytdlQuality: Int,
    currentQualityLabel: String,
    currentAspectMode: AspectMode,
    isSmartEnhanceEnabled: Boolean,
    isBackgroundPlayEnabled: Boolean,
    themePrimary: Color,
    onClick: () -> Unit
) {
    if (button == PlayerButton.NONE || button == PlayerButton.VIDEO_TITLE) return

    if (button == PlayerButton.DECODER || button == PlayerButton.STREAM_QUALITY) {
        val label = if (button == PlayerButton.DECODER) {
            currentDecoder
        } else {
            when {
                currentQualityLabel.isNotBlank() -> currentQualityLabel.substringBefore(" (").trim()
                ytdlQuality == -1 -> "Auto"
                else -> "${ytdlQuality}p"
            }
        }

        Box(
            modifier = Modifier
                .padding(horizontal = 4.dp, vertical = 2.dp)
                .clip(RoundedCornerShape(50))
                .background(AccentBlue.copy(alpha = 0.25f))
                .border(1.dp, AccentBlue.copy(alpha = 0.6f), RoundedCornerShape(50))
                .clickable { onClick() }
                .padding(horizontal = 10.dp, vertical = 4.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = label,
                color = Color(0xFF93C5FD),
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
        }
        return
    }

    val icon = if (button == PlayerButton.ASPECT_RATIO) {
        when (currentAspectMode) {
            AspectMode.FIT -> AspectIcons.Fit
            AspectMode.STRETCH -> AspectIcons.Stretch
            AspectMode.CROP -> AspectIcons.Crop
            AspectMode.ORIGINAL -> AspectIcons.Original
        }
    } else {
        button.icon
    }

    val isSmartEnhance = button == PlayerButton.SMART_ENHANCE
    val isBgPlay = button == PlayerButton.BACKGROUND_PLAY
    val isActive = (isSmartEnhance && isSmartEnhanceEnabled) || (isBgPlay && isBackgroundPlayEnabled)
    val tint = if (isActive) AccentBlue else Color.White.copy(alpha = 0.9f)

    IconButton(
        onClick = onClick,
        modifier = Modifier.size(40.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = button.displayName,
            tint = tint,
            modifier = Modifier.size(20.dp)
        )
    }
}

/**
 * Bottom Zone container with Expand/Collapse arrow and Glassmorphic Overflow Tray
 */
@Composable
fun BottomZoneWithOverflow(
    buttons: List<PlayerButton>,
    isExpanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    isAlignRight: Boolean = false,
    floatingOverflow: Boolean = false,
    currentDecoder: String,
    ytdlQuality: Int,
    currentQualityLabel: String,
    currentAspectMode: AspectMode,
    isSmartEnhanceEnabled: Boolean,
    isBackgroundPlayEnabled: Boolean,
    themePrimary: Color,
    onButtonClick: (PlayerButton) -> Unit
) {
    if (buttons.isEmpty()) return

    if (buttons.size <= 2) {
        DynamicControlCapsule(
            controls = buttons,
            currentDecoder = currentDecoder,
            ytdlQuality = ytdlQuality,
            currentQualityLabel = currentQualityLabel,
            currentAspectMode = currentAspectMode,
            isSmartEnhanceEnabled = isSmartEnhanceEnabled,
            isBackgroundPlayEnabled = isBackgroundPlayEnabled,
            themePrimary = themePrimary,
            onButtonClick = onButtonClick
        )
        return
    }

    val primaryButtons = buttons.take(2)
    val overflowButtons = buttons.drop(2)
    val rotationAngle by animateFloatAsState(
        targetValue = if (isExpanded) 180f else 0f,
        animationSpec = tween(250),
        label = "arrowRotation"
    )

    val expandArrowButton = @Composable {
        IconButton(
            onClick = { onExpandedChange(!isExpanded) },
            modifier = Modifier.size(40.dp)
        ) {
            Icon(
                imageVector = if (isAlignRight) Icons.Rounded.ChevronLeft else Icons.Rounded.ChevronRight,
                contentDescription = if (isExpanded) "Collapse Controls" else "Expand Controls",
                tint = Color.White.copy(alpha = 0.85f),
                modifier = Modifier
                    .size(20.dp)
                    .graphicsLayer(rotationZ = rotationAngle)
            )
        }
    }

    if (floatingOverflow) {
        // Floating pill row above bottom dock - guarantees zero crushing of transport dock
        Column(
            horizontalAlignment = if (isAlignRight) Alignment.End else Alignment.Start,
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            AnimatedVisibility(
                visible = isExpanded,
                enter = fadeIn(tween(200)) + expandVertically(expandFrom = Alignment.Bottom),
                exit = fadeOut(tween(150)) + shrinkVertically(shrinkTowards = Alignment.Bottom)
            ) {
                DynamicControlCapsule(
                    controls = overflowButtons,
                    currentDecoder = currentDecoder,
                    ytdlQuality = ytdlQuality,
                    currentQualityLabel = currentQualityLabel,
                    currentAspectMode = currentAspectMode,
                    isSmartEnhanceEnabled = isSmartEnhanceEnabled,
                    isBackgroundPlayEnabled = isBackgroundPlayEnabled,
                    themePrimary = themePrimary,
                    onButtonClick = onButtonClick
                )
            }

            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(GlassSurface)
                    .border(1.dp, GlassBorder, RoundedCornerShape(50))
                    .padding(horizontal = 4.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                if (isAlignRight) {
                    expandArrowButton()
                    primaryButtons.forEach { button ->
                        CapsuleControlItem(
                            button = button,
                            currentDecoder = currentDecoder,
                            ytdlQuality = ytdlQuality,
                            currentQualityLabel = currentQualityLabel,
                            currentAspectMode = currentAspectMode,
                            isSmartEnhanceEnabled = isSmartEnhanceEnabled,
                            isBackgroundPlayEnabled = isBackgroundPlayEnabled,
                            themePrimary = themePrimary,
                            onClick = { onButtonClick(button) }
                        )
                    }
                } else {
                    primaryButtons.forEach { button ->
                        CapsuleControlItem(
                            button = button,
                            currentDecoder = currentDecoder,
                            ytdlQuality = ytdlQuality,
                            currentQualityLabel = currentQualityLabel,
                            currentAspectMode = currentAspectMode,
                            isSmartEnhanceEnabled = isSmartEnhanceEnabled,
                            isBackgroundPlayEnabled = isBackgroundPlayEnabled,
                            themePrimary = themePrimary,
                            onClick = { onButtonClick(button) }
                        )
                    }
                    expandArrowButton()
                }
            }
        }
    } else {
        // Inline expansion when plenty of horizontal space exists
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            if (isAlignRight) {
                AnimatedVisibility(
                    visible = isExpanded,
                    enter = fadeIn(tween(200)) + expandHorizontally(),
                    exit = fadeOut(tween(150)) + shrinkHorizontally()
                ) {
                    DynamicControlCapsule(
                        controls = overflowButtons,
                        currentDecoder = currentDecoder,
                        ytdlQuality = ytdlQuality,
                        currentQualityLabel = currentQualityLabel,
                        currentAspectMode = currentAspectMode,
                        isSmartEnhanceEnabled = isSmartEnhanceEnabled,
                        isBackgroundPlayEnabled = isBackgroundPlayEnabled,
                        themePrimary = themePrimary,
                        onButtonClick = onButtonClick
                    )
                }

                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(GlassSurface)
                        .border(1.dp, GlassBorder, RoundedCornerShape(50))
                        .padding(horizontal = 4.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    expandArrowButton()
                    primaryButtons.forEach { button ->
                        CapsuleControlItem(
                            button = button,
                            currentDecoder = currentDecoder,
                            ytdlQuality = ytdlQuality,
                            currentQualityLabel = currentQualityLabel,
                            currentAspectMode = currentAspectMode,
                            isSmartEnhanceEnabled = isSmartEnhanceEnabled,
                            isBackgroundPlayEnabled = isBackgroundPlayEnabled,
                            themePrimary = themePrimary,
                            onClick = { onButtonClick(button) }
                        )
                    }
                }
            } else {
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(GlassSurface)
                        .border(1.dp, GlassBorder, RoundedCornerShape(50))
                        .padding(horizontal = 4.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    primaryButtons.forEach { button ->
                        CapsuleControlItem(
                            button = button,
                            currentDecoder = currentDecoder,
                            ytdlQuality = ytdlQuality,
                            currentQualityLabel = currentQualityLabel,
                            currentAspectMode = currentAspectMode,
                            isSmartEnhanceEnabled = isSmartEnhanceEnabled,
                            isBackgroundPlayEnabled = isBackgroundPlayEnabled,
                            themePrimary = themePrimary,
                            onClick = { onButtonClick(button) }
                        )
                    }
                    expandArrowButton()
                }

                AnimatedVisibility(
                    visible = isExpanded,
                    enter = fadeIn(tween(200)) + expandHorizontally(),
                    exit = fadeOut(tween(150)) + shrinkHorizontally()
                ) {
                    DynamicControlCapsule(
                        controls = overflowButtons,
                        currentDecoder = currentDecoder,
                        ytdlQuality = ytdlQuality,
                        currentQualityLabel = currentQualityLabel,
                        currentAspectMode = currentAspectMode,
                        isSmartEnhanceEnabled = isSmartEnhanceEnabled,
                        isBackgroundPlayEnabled = isBackgroundPlayEnabled,
                        themePrimary = themePrimary,
                        onButtonClick = onButtonClick
                    )
                }
            }
        }
    }
}

/**
 * Flagship Center Playback Transport Dock Container:
 * - Floating elevated glassmorphic capsule
 * - Hero Morphing Play/Pause button (Full Circle 27.dp when paused -> Squircle 16.dp when playing)
 * - Rewind/Forward 44.dp buttons with tactile press scale
 * - Skip Prev/Next 40.dp borderless icon buttons
 */
@Composable
fun CenterTransportDock(
    isPlaying: Boolean,
    onPlayPauseToggle: () -> Unit,
    showSeekButtons: Boolean,
    seekDurationSeconds: Int,
    onRewindClick: () -> Unit,
    onForwardClick: () -> Unit,
    showNextPrevButtons: Boolean,
    hasPrevious: Boolean,
    hasNext: Boolean,
    onPrevClick: () -> Unit,
    onNextClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val dockShape = RoundedCornerShape(50)
    val dockBackground = Color(0xFF121316).copy(alpha = 0.78f)
    val dockBorder = Color.White.copy(alpha = 0.16f)

    Row(
        modifier = modifier
            .clip(dockShape)
            .background(dockBackground)
            .border(1.dp, dockBorder, dockShape)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 1. Previous Track Button
        if (showNextPrevButtons) {
            IconButton(
                onClick = onPrevClick,
                enabled = hasPrevious,
                modifier = Modifier.size(40.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.SkipPrevious,
                    contentDescription = "Previous Track",
                    tint = Color.White.copy(alpha = if (hasPrevious) 0.9f else 0.38f),
                    modifier = Modifier.size(24.dp)
                )
            }
        }

        // 2. Rewind Button
        if (showSeekButtons) {
            val rewindInteraction = remember { MutableInteractionSource() }
            val isRewindPressed by rewindInteraction.collectIsPressedAsState()
            val rewindScale by animateFloatAsState(
                targetValue = if (isRewindPressed) 0.88f else 1.0f,
                animationSpec = spring(stiffness = Spring.StiffnessMedium),
                label = "rewindScale"
            )
            val rewindIcon = when (seekDurationSeconds) {
                5 -> Icons.Rounded.Replay5
                30 -> Icons.Rounded.Replay30
                else -> Icons.Rounded.Replay10
            }

            Box(
                modifier = Modifier
                    .size(44.dp)
                    .scale(rewindScale)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.08f))
                    .clickable(
                        interactionSource = rewindInteraction,
                        indication = null
                    ) { onRewindClick() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = rewindIcon,
                    contentDescription = "Rewind ${seekDurationSeconds}s",
                    tint = Color.White,
                    modifier = Modifier.size(22.dp)
                )
            }
        }

        // 3. Hero Morphing Play/Pause Button (Center)
        val playInteraction = remember { MutableInteractionSource() }
        val isPlayPressed by playInteraction.collectIsPressedAsState()
        val playScale by animateFloatAsState(
            targetValue = if (isPlayPressed) 0.90f else 1.0f,
            animationSpec = spring(stiffness = Spring.StiffnessMedium),
            label = "playPressScale"
        )
        val animatedCornerRadius by animateDpAsState(
            targetValue = if (isPlaying) 16.dp else 27.dp,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessLow
            ),
            label = "heroMorphShape"
        )

        Box(
            modifier = Modifier
                .size(54.dp)
                .scale(playScale)
                .clip(RoundedCornerShape(animatedCornerRadius))
                .background(Color.White)
                .clickable(
                    interactionSource = playInteraction,
                    indication = null
                ) { onPlayPauseToggle() },
            contentAlignment = Alignment.Center
        ) {
            AnimatedContent(
                targetState = isPlaying,
                transitionSpec = {
                    (fadeIn(tween(200)) + scaleIn(initialScale = 0.8f, animationSpec = tween(200)))
                        .togetherWith(fadeOut(tween(150)) + scaleOut(targetScale = 0.8f, animationSpec = tween(150)))
                },
                label = "playPauseIcon"
            ) { playing ->
                Icon(
                    imageVector = if (playing) Icons.Rounded.Pause else Icons.Filled.PlayArrow,
                    contentDescription = if (playing) "Pause" else "Play",
                    tint = Color(0xFF0F172A),
                    modifier = Modifier.size(30.dp)
                )
            }
        }

        // 4. Fast-Forward Button
        if (showSeekButtons) {
            val forwardInteraction = remember { MutableInteractionSource() }
            val isForwardPressed by forwardInteraction.collectIsPressedAsState()
            val forwardScale by animateFloatAsState(
                targetValue = if (isForwardPressed) 0.88f else 1.0f,
                animationSpec = spring(stiffness = Spring.StiffnessMedium),
                label = "forwardScale"
            )
            val forwardIcon = when (seekDurationSeconds) {
                5 -> Icons.Rounded.Forward5
                30 -> Icons.Rounded.Forward30
                else -> Icons.Rounded.Forward10
            }

            Box(
                modifier = Modifier
                    .size(44.dp)
                    .scale(forwardScale)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.08f))
                    .clickable(
                        interactionSource = forwardInteraction,
                        indication = null
                    ) { onForwardClick() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = forwardIcon,
                    contentDescription = "Forward ${seekDurationSeconds}s",
                    tint = Color.White,
                    modifier = Modifier.size(22.dp)
                )
            }
        }

        // 5. Next Track Button
        if (showNextPrevButtons) {
            IconButton(
                onClick = onNextClick,
                enabled = hasNext,
                modifier = Modifier.size(40.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.SkipNext,
                    contentDescription = "Next Track",
                    tint = Color.White.copy(alpha = if (hasNext) 0.9f else 0.38f),
                    modifier = Modifier.size(24.dp)
                )
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
fun BatteryAndClockOverlay(modifier: Modifier = Modifier) {
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
            .clip(RoundedCornerShape(50))
            .background(GlassSurface)
            .border(1.dp, GlassBorder, RoundedCornerShape(50))
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
                fontSize = 12.sp,
                fontFamily = FontFamily.Monospace
            )
            Spacer(
                modifier = Modifier
                    .width(1.dp)
                    .height(12.dp)
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
                fontSize = 12.sp,
                fontFamily = FontFamily.Monospace
            )
        }
    }
}

@Composable
fun PlaybackTimerText(
    positionProvider: () -> Long,
    duration: Long,
    showRemainingTime: Boolean,
    modifier: Modifier = Modifier
) {
    var pos by remember { mutableLongStateOf(positionProvider()) }
    LaunchedEffect(positionProvider) {
        while (true) {
            val newPos = positionProvider()
            if (pos != newPos) {
                pos = newPos
            }
            delay(200L)
        }
    }
    val durationText = if (showRemainingTime) {
        "-${formatTime((duration - pos).coerceAtLeast(0L))}"
    } else {
        formatTime(duration)
    }
    Text(
        text = "${formatTime(pos)} / $durationText",
        color = Color.White,
        fontSize = 12.sp,
        fontWeight = FontWeight.Medium,
        fontFamily = FontFamily.Monospace,
        modifier = modifier
    )
}

/**
 * Backward compatibility fallback for any external references to RenderPlayerButton
 */
@Composable
fun RenderPlayerButton(
    button: PlayerButton,
    modifier: Modifier = Modifier,
    isPortrait: Boolean = false,
    title: String = "",
    currentPosition: Long = 0L,
    currentDecoder: String = "HW",
    hasChapters: Boolean = false,
    isSmartEnhanceEnabled: Boolean = false,
    glowBrush: Brush? = null,
    glowAlpha: Float = 0f,
    themePrimary: Color = MaterialTheme.colorScheme.primary,
    onBackClick: () -> Unit = {},
    onShowDecoder: () -> Unit = {},
    onShowChapters: () -> Unit = {},
    onCycleSubtitle: () -> Unit = {},
    onCycleAudio: () -> Unit = {},
    onEnhanceClick: () -> Unit = {},
    onSpeedClick: () -> Unit = {},
    onLockClick: () -> Unit = {},
    onAspectClick: () -> Unit = {},
    onPipClick: () -> Unit = {},
    activity: Activity? = null,
    currentAspectMode: AspectMode = AspectMode.FIT,
    isBackgroundPlayEnabled: Boolean = false,
    onBackgroundPlayClick: () -> Unit = {},
    ytdlQuality: Int = -1,
    currentQualityLabel: String = "",
    onShowQuality: () -> Unit = {},
    onTitleClick: () -> Unit = {},
    onScreenshotClick: () -> Unit = {}
) {
    if (button == PlayerButton.VIDEO_TITLE) {
        Column(modifier = modifier.clickable { onTitleClick() }) {
            Text(
                text = title,
                color = Color.White,
                fontWeight = FontWeight.SemiBold,
                fontSize = 15.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        return
    }

    val onClick: () -> Unit = {
        when (button) {
            PlayerButton.BACK_ARROW -> onBackClick()
            PlayerButton.DECODER -> onShowDecoder()
            PlayerButton.CHAPTERS -> if (hasChapters) onShowChapters() else {}
            PlayerButton.SUBTITLES -> onCycleSubtitle()
            PlayerButton.AUDIO_TRACK -> onCycleAudio()
            PlayerButton.SMART_ENHANCE -> onEnhanceClick()
            PlayerButton.MORE_OPTIONS -> onSpeedClick()
            PlayerButton.LOCK_CONTROLS -> onLockClick()
            PlayerButton.PICTURE_IN_PICTURE -> onPipClick()
            PlayerButton.ASPECT_RATIO -> onAspectClick()
            PlayerButton.BACKGROUND_PLAY -> onBackgroundPlayClick()
            PlayerButton.STREAM_QUALITY -> onShowQuality()
            PlayerButton.SCREENSHOT -> onScreenshotClick()
            PlayerButton.SCREEN_ROTATION -> {
                activity?.let { act ->
                    val currentOrientation = act.requestedOrientation
                    if (currentOrientation == ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE ||
                        currentOrientation == ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                    ) {
                        act.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                    } else {
                        act.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                    }
                }
                Unit
            }
            else -> {}
        }
    }

    CapsuleControlItem(
        button = button,
        currentDecoder = currentDecoder,
        ytdlQuality = ytdlQuality,
        currentQualityLabel = currentQualityLabel,
        currentAspectMode = currentAspectMode,
        isSmartEnhanceEnabled = isSmartEnhanceEnabled,
        isBackgroundPlayEnabled = isBackgroundPlayEnabled,
        themePrimary = themePrimary,
        onClick = onClick
    )
}