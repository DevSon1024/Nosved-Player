package com.devson.nvplayer.ui.navigation.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.VideoLibrary
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

data class NavigationTabItem(
    val route: String,
    val title: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
)

@Composable
fun CapsuleNavigationBar(
    visible: Boolean,
    currentRoute: String?,
    onNavigate: (String) -> Unit,
    modifier: Modifier = Modifier,
    onTabReselect: ((String) -> Unit)? = null
) {
    val haptic = LocalHapticFeedback.current
    var lastClickTime by remember { mutableLongStateOf(0L) }

    val items = remember {
        listOf(
            NavigationTabItem(
                route = "library",
                title = "Library",
                selectedIcon = Icons.Filled.VideoLibrary,
                unselectedIcon = Icons.Outlined.VideoLibrary
            ),
            NavigationTabItem(
                route = "video_list",
                title = "Folders",
                selectedIcon = Icons.Filled.Folder,
                unselectedIcon = Icons.Outlined.Folder
            ),
            NavigationTabItem(
                route = "vault",
                title = "Vault",
                selectedIcon = Icons.Filled.Lock,
                unselectedIcon = Icons.Outlined.Lock
            ),
            NavigationTabItem(
                route = "settings",
                title = "Settings",
                selectedIcon = Icons.Filled.Settings,
                unselectedIcon = Icons.Outlined.Settings
            )
        )
    }

    val activeIndex = items.indexOfFirst { it.route == currentRoute }
    var targetIndex by remember {
        mutableIntStateOf(if (activeIndex >= 0) activeIndex else 0)
    }
    var movingForward by remember { mutableStateOf(true) }

    LaunchedEffect(activeIndex) {
        if (activeIndex >= 0 && activeIndex != targetIndex) {
            movingForward = activeIndex > targetIndex
            targetIndex = activeIndex
        }
    }

    var barWidthPx by remember { mutableIntStateOf(0) }
    var barHeightPx by remember { mutableIntStateOf(0) }

    val tabWidthPx = if (items.isNotEmpty() && barWidthPx > 0) {
        barWidthPx.toFloat() / items.size
    } else {
        0f
    }

    val leftTargetPx = tabWidthPx * targetIndex
    val rightTargetPx = tabWidthPx * (targetIndex + 1)

    // Liquid fluid springs: leading edge stretches rapidly with organic overshoot,
    // while trailing edge flows behind with viscosity and snaps into destination.
    val leadingSpring = remember {
        spring<Float>(
            dampingRatio = 0.72f,
            stiffness = 500f
        )
    }
    val trailingSpring = remember {
        spring<Float>(
            dampingRatio = 0.82f,
            stiffness = 300f
        )
    }

    val animatedLeftPx by animateFloatAsState(
        targetValue = leftTargetPx,
        animationSpec = if (movingForward) trailingSpring else leadingSpring,
        label = "liquid_pill_left"
    )

    val animatedRightPx by animateFloatAsState(
        targetValue = rightTargetPx,
        animationSpec = if (movingForward) leadingSpring else trailingSpring,
        label = "liquid_pill_right"
    )

    AnimatedVisibility(
        visible = visible,
        enter = slideInVertically(
            animationSpec = spring(
                dampingRatio = 0.75f,
                stiffness = Spring.StiffnessLow
            ),
            initialOffsetY = { it * 2 }
        ) + fadeIn(
            animationSpec = spring(stiffness = Spring.StiffnessLow)
        ),
        exit = slideOutVertically(
            animationSpec = spring(
                dampingRatio = 0.75f,
                stiffness = Spring.StiffnessLow
            ),
            targetOffsetY = { it * 2 }
        ) + fadeOut(
            animationSpec = spring(stiffness = Spring.StiffnessLow)
        ),
        modifier = modifier
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(bottom = 12.dp, start = 16.dp, end = 16.dp),
            contentAlignment = Alignment.Center
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.94f),
                contentColor = MaterialTheme.colorScheme.onSurface,
                border = BorderStroke(
                    1.dp,
                    MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                ),
                tonalElevation = 6.dp,
                shadowElevation = 10.dp,
                modifier = Modifier
                    .widthIn(max = 380.dp)
                    .fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 6.dp, vertical = 6.dp)
                ) {
                    LiquidIndicatorPill(
                        leftPx = animatedLeftPx,
                        rightPx = animatedRightPx,
                        barHeightPx = barHeightPx,
                        tabWidthPx = tabWidthPx
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .onSizeChanged { size ->
                                barWidthPx = size.width
                                barHeightPx = size.height
                            },
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        items.forEach { item ->
                            val isSelected = currentRoute == item.route
                            CapsuleNavItem(
                                item = item,
                                isSelected = isSelected,
                                onClick = {
                                    val now = System.currentTimeMillis()
                                    if (now - lastClickTime > 200L) {
                                        lastClickTime = now
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        if (isSelected) {
                                            onTabReselect?.invoke(item.route)
                                        } else {
                                            onNavigate(item.route)
                                        }
                                    }
                                },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LiquidIndicatorPill(
    leftPx: Float,
    rightPx: Float,
    barHeightPx: Int,
    tabWidthPx: Float,
    modifier: Modifier = Modifier
) {
    if (tabWidthPx <= 0f || barHeightPx <= 0) return

    val density = LocalDensity.current
    val minWidthPx = tabWidthPx * 0.45f
    val currentWidthPx = maxOf(rightPx - leftPx, minWidthPx)
    val widthDp = with(density) { currentWidthPx.toDp() }
    val heightDp = with(density) { barHeightPx.toDp() }

    val stretchFactor = ((currentWidthPx - tabWidthPx) / tabWidthPx).coerceIn(0f, 1f)
    val scaleY = 1f - (stretchFactor * 0.10f)
    val scaleX = 1f + (stretchFactor * 0.03f)

    Box(
        modifier = modifier
            .offset { IntOffset(x = leftPx.roundToInt(), y = 0) }
            .width(widthDp)
            .height(heightDp)
            .graphicsLayer {
                this.scaleY = scaleY
                this.scaleX = scaleX
            }
            .padding(horizontal = 2.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.secondaryContainer)
            .border(
                width = 0.5.dp,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
                shape = CircleShape
            )
    )
}

@Composable
private fun CapsuleNavItem(
    item: NavigationTabItem,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val iconColor by animateColorAsState(
        targetValue = if (isSelected) {
            MaterialTheme.colorScheme.onSecondaryContainer
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
        animationSpec = spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessMediumLow),
        label = "nav_icon_color"
    )

    val textColor by animateColorAsState(
        targetValue = if (isSelected) {
            MaterialTheme.colorScheme.onSurface
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
        animationSpec = spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessMediumLow),
        label = "nav_text_color"
    )

    val scale by animateFloatAsState(
        targetValue = if (isSelected) 1.04f else 1f,
        animationSpec = spring(dampingRatio = 0.7f, stiffness = Spring.StiffnessMediumLow),
        label = "nav_scale"
    )

    Box(
        modifier = modifier
            .scale(scale)
            .padding(horizontal = 2.dp)
            .clip(CircleShape)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(bounded = true)
            ) { onClick() }
            .padding(vertical = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = if (isSelected) item.selectedIcon else item.unselectedIcon,
                contentDescription = item.title,
                tint = iconColor,
                modifier = Modifier.size(22.dp)
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = item.title,
                fontSize = 11.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                color = textColor,
                maxLines = 1
            )
        }
    }
}
