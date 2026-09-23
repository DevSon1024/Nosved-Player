package com.devson.nvplayer.ui.screen

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.devson.nvplayer.R
import com.devson.nvplayer.viewmodel.StreakDayActivity
import com.devson.nvplayer.viewmodel.StreakUiState
import com.devson.nvplayer.viewmodel.TodayStreakStatus
import com.devson.nvplayer.viewmodel.WatchHistoryViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StreakScreen(
    watchHistoryViewModel: WatchHistoryViewModel,
    onBack: () -> Unit
) {
    val streakUiState by watchHistoryViewModel.streakUiState.collectAsState()
    val scrollState = rememberScrollState()

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Watch Streak",
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground,
                    navigationIconContentColor = MaterialTheme.colorScheme.onBackground
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(scrollState)
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Hero Streak Display
            StreakHeroCard(streakUiState = streakUiState)

            // Today's Status Card
            TodayStatusCard(streakUiState = streakUiState)

            // 7-Day Recent Activity Strip
            RecentActivityCalendarStrip(activities = streakUiState.recentDays)

            // Stats Row: Current Streak, Longest Streak, Last Active Date
            StreakStatsGrid(streakUiState = streakUiState)

            // How Streaks Work Rule Explainer
            StreakRulesCard()

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
fun StreakHeroCard(
    streakUiState: StreakUiState,
    modifier: Modifier = Modifier
) {
    val flameColor = if (streakUiState.currentStreak > 0) Color(0xFFFF6D00) else MaterialTheme.colorScheme.outline
    val cardBackground = MaterialTheme.colorScheme.surfaceContainerHigh

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = cardBackground),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .clip(CircleShape)
                    .background(flameColor.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Whatshot,
                    contentDescription = "Streak Flame",
                    tint = flameColor,
                    modifier = Modifier.size(48.dp)
                )
            }

            Text(
                text = "${streakUiState.currentStreak}",
                style = MaterialTheme.typography.displayMedium,
                fontWeight = FontWeight.Black,
                color = MaterialTheme.colorScheme.onSurface
            )

            Text(
                text = if (streakUiState.currentStreak == 1) "Day Streak" else "Days Streak",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (streakUiState.currentStreak == 0) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.6f),
                    modifier = Modifier.padding(top = 4.dp)
                ) {
                    Text(
                        text = "No active streak. Watch a video today to start!",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                }
            } else {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = flameColor.copy(alpha = 0.18f),
                    modifier = Modifier.padding(top = 4.dp)
                ) {
                    Text(
                        text = "🔥 Keep the fire burning!",
                        style = MaterialTheme.typography.bodySmall,
                        color = flameColor,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun TodayStatusCard(
    streakUiState: StreakUiState,
    modifier: Modifier = Modifier
) {
    val (bgColor, icon, title, message) = when (streakUiState.todayStatus) {
        TodayStreakStatus.COMPLETED -> Quadruple(
            MaterialTheme.colorScheme.primaryContainer,
            Icons.Default.CheckCircle,
            "Today's Goal Completed!",
            "You watched at least 10% of a video today. Your streak is safe!"
        )
        TodayStreakStatus.PENDING -> Quadruple(
            MaterialTheme.colorScheme.tertiaryContainer,
            Icons.Default.HourglassTop,
            "Streak At Risk",
            "Watch at least 10% of any video today to extend your streak to ${streakUiState.currentStreak + 1} days."
        )
        TodayStreakStatus.NOT_STARTED -> Quadruple(
            MaterialTheme.colorScheme.surfaceContainerHigh,
            Icons.Default.PlayCircle,
            "Start Your Streak Today",
            "Watch at least 10% of any video today to start day 1 of your streak."
        )
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = bgColor)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(32.dp),
                tint = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun RecentActivityCalendarStrip(
    activities: List<StreakDayActivity>,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Recent Activity",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "Past 7 Days",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                activities.forEach { day ->
                    DayActivityPill(day = day)
                }
            }
        }
    }
}

@Composable
private fun DayActivityPill(day: StreakDayActivity) {
    val flameColor = Color(0xFFFF6D00)
    val checkColor = Color(0xFF2E7D32)
    val inactiveBg = MaterialTheme.colorScheme.surfaceContainerHighest

    val borderColor = if (day.isToday) MaterialTheme.colorScheme.primary else Color.Transparent

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(
            text = day.dayOfWeek,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = if (day.isToday) FontWeight.Bold else FontWeight.Normal,
            color = if (day.isToday) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
        )

        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(
                    when {
                        day.isQualified -> flameColor.copy(alpha = 0.2f)
                        day.isToday -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                        else -> inactiveBg
                    }
                )
                .border(
                    width = if (day.isToday) 2.dp else 0.dp,
                    color = borderColor,
                    shape = CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            if (day.isQualified) {
                Icon(
                    imageVector = Icons.Default.Whatshot,
                    contentDescription = "Watched",
                    tint = flameColor,
                    modifier = Modifier.size(22.dp)
                )
            } else if (day.isToday) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary)
                )
            } else {
                Text(
                    text = "-",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline
                )
            }
        }

        Text(
            text = "${day.dayOfMonth}",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = if (day.isToday) FontWeight.Bold else FontWeight.Normal,
            color = if (day.isToday) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun StreakStatsGrid(
    streakUiState: StreakUiState,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        StatMiniCard(
            modifier = Modifier.weight(1f),
            icon = Icons.Default.Whatshot,
            iconTint = Color(0xFFFF6D00),
            title = "Current",
            value = "${streakUiState.currentStreak} d"
        )
        StatMiniCard(
            modifier = Modifier.weight(1f),
            icon = Icons.Default.EmojiEvents,
            iconTint = Color(0xFFFFB300),
            title = "Longest",
            value = "${streakUiState.longestStreak} d"
        )
        StatMiniCard(
            modifier = Modifier.weight(1f),
            icon = Icons.Default.CalendarToday,
            iconTint = MaterialTheme.colorScheme.primary,
            title = "Last Watch",
            value = streakUiState.lastQualifyingDate?.takeIf { it.isNotBlank() }?.takeLast(5) ?: "None"
        )
    }
}

@Composable
fun StatMiniCard(
    modifier: Modifier = Modifier,
    icon: ImageVector,
    iconTint: Color,
    title: String,
    value: String
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size(24.dp)
            )
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = title,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun StreakRulesCard(modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "How Streaks Work",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            RuleBulletItem(
                number = "1",
                text = "Watch at least 10% (or 30 seconds for videos 1 minute or longer) to qualify for the day."
            )
            RuleBulletItem(
                number = "2",
                text = "Each calendar day with at least one qualifying video adds 1 day to your streak."
            )
            RuleBulletItem(
                number = "3",
                text = "Multiple videos watched on the same day count toward the same qualifying day."
            )
            RuleBulletItem(
                number = "4",
                text = "Missing a day resets the current streak, but your longest record is always preserved."
            )
        }
    }
}

@Composable
private fun RuleBulletItem(number: String, text: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top
    ) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primaryContainer,
            modifier = Modifier.size(20.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    text = number,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }
        Spacer(modifier = Modifier.width(10.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * Compact banner card placed at the top of HistoryScreen to present streak status and open StreakScreen.
 */
@Composable
fun WatchStreakBannerCard(
    streakUiState: StreakUiState,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val flameColor = if (streakUiState.currentStreak > 0) Color(0xFFFF6D00) else MaterialTheme.colorScheme.outline
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(CircleShape)
                    .background(flameColor.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Whatshot,
                    contentDescription = "Watch Streak",
                    tint = flameColor,
                    modifier = Modifier.size(28.dp)
                )
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "${streakUiState.currentStreak} Day Streak",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    if (streakUiState.isQualifiedToday) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = Color(0xFF2E7D32).copy(alpha = 0.2f)
                        ) {
                            Text(
                                text = "Today Done",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color(0xFF2E7D32),
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = if (streakUiState.isQualifiedToday) {
                        "Longest: ${streakUiState.longestStreak} days"
                    } else if (streakUiState.currentStreak > 0) {
                        "Watch 10% today to keep streak!"
                    } else {
                        "Watch 10% today to start a streak"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = "View Streak",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

private data class Quadruple<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)
