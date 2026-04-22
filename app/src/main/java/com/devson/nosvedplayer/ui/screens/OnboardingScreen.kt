package com.devson.nosvedplayer.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

// Slide data model
private data class OnboardingSlide(
    val icon: ImageVector,
    val accentColor: Color,
    val tag: String,
    val headline: String,
    val body: String
)

/**
 * Full-screen onboarding flow shown EXACTLY ONCE on first launch.
 *
 * Design:
 * - Adapts to system light/dark mode using MaterialTheme tokens, so it
 *   looks correct whether the device is in light or dark mode.
 * - The per-slide accent colours remain vibrant on both backgrounds.
 * - Calling [onFinished] (via "Start Playing" or "Skip") persists the
 *   HAS_SEEN_ONBOARDING flag via SettingsViewModel.markOnboardingComplete()
 *   and removes this destination from the back-stack in NavGraph, so it
 *   can NEVER be shown again—not on restart, not ever.
 */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun OnboardingScreen(
    onFinished: () -> Unit
) {
    val slides = listOf(
        OnboardingSlide(
            icon        = Icons.Filled.Hd,
            accentColor = Color(0xFF0288D1),   // Blue-ish cyan — readable on both light & dark
            tag         = "ULTIMATE PLAYBACK",
            headline    = "Cinema-Grade\nCodec Support",
            body        = "Nosved Player decodes HEVC H.265 10-bit\nHDR content natively. No transcoding, no\nquality loss. Pure, pixel-perfect playback."
        ),
        OnboardingSlide(
            icon        = Icons.Filled.Tune,
            accentColor = Color(0xFFE65100),   // Deep orange — vivid on both themes
            tag         = "PRO CONTROLS",
            headline    = "Modern-Style UI\n& Gesture Suite",
            body        = "Swipe up/down to control volume & brightness.\nDouble-tap to seek. Our custom scrubber gives\nyou frame-level precision on every scrub."
        ),
        OnboardingSlide(
            icon        = Icons.Filled.Speed,
            accentColor = Color(0xFF6200EA),   // Deep violet — solid contrast on light & dark
            tag         = "REAL-TIME METRICS",
            headline    = "Live Device Stats\nOverlay",
            body        = "Monitor CPU, RAM, battery temperature, and\nframe-rate in real time without leaving the\nplayer. Your personal performance dashboard."
        )
    )

    val isDark    = isSystemInDarkTheme()
    val pagerState = rememberPagerState(pageCount = { slides.size })
    val scope      = rememberCoroutineScope()

    // Resolved background & text colours from the current MaterialTheme so the
    // screen looks native in both light and dark system modes.
    val bgColor      = MaterialTheme.colorScheme.background
    val surfaceColor = MaterialTheme.colorScheme.surfaceContainerHigh
    val onBgColor    = MaterialTheme.colorScheme.onBackground
    val onBgSub      = MaterialTheme.colorScheme.onSurfaceVariant

    Scaffold(
        containerColor = bgColor
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
        ) {
            // Full-screen pager
        HorizontalPager(
            state    = pagerState,
            modifier = Modifier.fillMaxSize()
        ) { page ->
            OnboardingPage(
                slide      = slides[page],
                isDark     = isDark,
                onBgColor  = onBgColor,
                onBgSub    = onBgSub,
                surfaceColor = surfaceColor
            )
        }

        // Bottom controls strip
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(
                    bottom = padding.calculateBottomPadding() + 32.dp,
                    start = 24.dp,
                    end = 24.dp
                ),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // Dot indicators
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment     = Alignment.CenterVertically
            ) {
                slides.indices.forEach { index ->
                    val isActive = pagerState.currentPage == index
                    val width by animateDpAsState(
                        targetValue   = if (isActive) 24.dp else 8.dp,
                        animationSpec = tween(300),
                        label         = "dotWidth"
                    )
                    val dotColor = if (isActive) {
                        slides[index].accentColor
                    } else {
                        // Inactive dot adapts to theme
                        if (isDark) Color.White.copy(alpha = 0.3f)
                        else MaterialTheme.colorScheme.outline
                    }
                    Box(
                        modifier = Modifier
                            .height(8.dp)
                            .width(width)
                            .clip(CircleShape)
                            .background(dotColor)
                    )
                }
            }

            // Primary action button
            val isLast = pagerState.currentPage == slides.lastIndex
            val currentAccent = slides[pagerState.currentPage].accentColor

            // Choose a content colour that has enough contrast on the accent bg
            val buttonContentColor = Color.White

            Button(
                onClick = {
                    if (isLast) {
                        onFinished()
                    } else {
                        scope.launch {
                            pagerState.animateScrollToPage(pagerState.currentPage + 1)
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape  = RoundedCornerShape(6.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = currentAccent,
                    contentColor   = buttonContentColor
                )
            ) {
                Text(
                    text       = if (isLast) "Start Playing" else "Next",
                    fontWeight = FontWeight.Bold,
                    fontSize   = 15.sp
                )
                if (!isLast) {
                    Spacer(modifier = Modifier.width(6.dp))
                    Icon(
                        imageVector        = Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = null,
                        modifier           = Modifier.size(18.dp)
                    )
                }
            }

            // Skip text link — theme-aware colour
            if (!isLast) {
                TextButton(onClick = onFinished) {
                    Text(
                        text     = "Skip",
                        color    = onBgSub,
                        fontSize = 13.sp
                    )
                }
            }
        }
    }
    }
}

// Single onboarding page content
@Composable
private fun OnboardingPage(
    slide: OnboardingSlide,
    isDark: Boolean,
    onBgColor: Color,
    onBgSub: Color,
    surfaceColor: Color
) {
    val iconScale = remember { Animatable(0.5f) }
    val textAlpha = remember { Animatable(0f) }

    LaunchedEffect(slide) {
        iconScale.animateTo(
            1f,
            spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness    = Spring.StiffnessMedium
            )
        )
        textAlpha.animateTo(1f, tween(400, delayMillis = 150))
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 28.dp)
            .padding(top = 96.dp, bottom = 200.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Animated icon cluster
        Box(
            modifier = Modifier
                .scale(iconScale.value)
                .size(140.dp),
            contentAlignment = Alignment.Center
        ) {
            // Outer glow ring
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                slide.accentColor.copy(alpha = if (isDark) 0.22f else 0.15f),
                                Color.Transparent
                            )
                        ),
                        shape = CircleShape
                    )
            )
            // Inner tinted circle
            Box(
                modifier = Modifier
                    .size(96.dp)
                    .background(
                        color = slide.accentColor.copy(alpha = if (isDark) 0.14f else 0.10f),
                        shape = CircleShape
                    )
            )
            Icon(
                imageVector        = slide.icon,
                contentDescription = null,
                tint               = slide.accentColor,
                modifier           = Modifier.size(52.dp)
            )
        }

        Spacer(modifier = Modifier.height(36.dp))

        Column(
            modifier            = Modifier.alpha(textAlpha.value),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Feature tag badge
            Box(
                modifier = Modifier
                    .background(
                        color = slide.accentColor.copy(alpha = if (isDark) 0.18f else 0.12f),
                        shape = RoundedCornerShape(4.dp)
                    )
                    .padding(horizontal = 10.dp, vertical = 4.dp)
            ) {
                Text(
                    text          = slide.tag,
                    color         = slide.accentColor,
                    fontSize      = 10.sp,
                    fontWeight    = FontWeight.Bold,
                    letterSpacing = 2.sp
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Headline — uses theme onBackground for proper contrast
            Text(
                text          = slide.headline,
                color         = onBgColor,
                fontSize      = 26.sp,
                fontWeight    = FontWeight.Bold,
                textAlign     = TextAlign.Center,
                lineHeight    = 34.sp,
                letterSpacing = (-0.5).sp
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Body — uses theme onSurfaceVariant for secondary text
            Text(
                text       = slide.body,
                color      = onBgSub,
                fontSize   = 14.sp,
                textAlign  = TextAlign.Center,
                lineHeight = 22.sp
            )
        }
    }
}
