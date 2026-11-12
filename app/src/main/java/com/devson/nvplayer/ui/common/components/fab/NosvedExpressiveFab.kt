package com.devson.nvplayer.ui.common.components.fab

import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import com.devson.nvplayer.ui.common.components.NosvedLogoMark

/**
 * Unified Material 3 Expressive Floating Action Button with asymmetric polygon morphing,
 * static Nosved logo branding, and long-press expandable quick actions.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun NosvedExpressiveFab(
    visible: Boolean,
    enablePreview: Boolean,
    previewData: NosvedFabPreviewData?,
    onPlayLastPlayed: () -> Unit,
    onNetworkStreamClick: () -> Unit,
    modifier: Modifier = Modifier,
    state: NosvedFabState = rememberNosvedFabState()
) {
    LaunchedEffect(visible) {
        if (!visible && state.isExpanded) {
            state.collapse()
        }
    }

    if (!visible) return

    BackHandler(enabled = state.isExpanded) {
        state.collapse()
    }

    val haptic = LocalHapticFeedback.current
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val hasValidMedia = previewData != null && previewData.hasValidMedia

    val pressScale by animateFloatAsState(
        targetValue = if (isPressed) 0.94f else 1.0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "nosvedFabPressScale"
    )

    val morphProgress by animateFloatAsState(
        targetValue = if (state.isExpanded) 1f else 0f,
        animationSpec = spring(
            dampingRatio = 0.68f,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "nosvedFabMorph"
    )

    val containerColor by animateColorAsState(
        targetValue = if (state.isExpanded) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.primary
        },
        animationSpec = tween(durationMillis = 200),
        label = "nosvedFabContainerColor"
    )

    val contentColor by animateColorAsState(
        targetValue = if (state.isExpanded) {
            MaterialTheme.colorScheme.onPrimaryContainer
        } else {
            MaterialTheme.colorScheme.onPrimary
        },
        animationSpec = tween(durationMillis = 200),
        label = "nosvedFabContentColor"
    )

    val currentShape = NosvedFabShapes.shapeForProgress(morphProgress)

    val accessibilityLabel = remember(state.isExpanded, hasValidMedia, previewData?.title) {
        when {
            state.isExpanded -> "Close quick actions"
            hasValidMedia -> "Play ${previewData?.title ?: "last played video"}"
            else -> "Quick actions"
        }
    }

    val accessibilityActions = remember(hasValidMedia, onPlayLastPlayed, onNetworkStreamClick, state) {
        buildList {
            add(
                CustomAccessibilityAction("Network Stream") {
                    state.collapse()
                    onNetworkStreamClick()
                    true
                }
            )
            if (hasValidMedia) {
                add(
                    CustomAccessibilityAction("Resume last played") {
                        state.collapse()
                        onPlayLastPlayed()
                        true
                    }
                )
            }
        }
    }

    Box(
        modifier = modifier,
        contentAlignment = Alignment.BottomEnd
    ) {
        NosvedFabOptionsMenu(
            expanded = state.isExpanded,
            enablePreview = enablePreview,
            previewData = previewData,
            onPlayLastPlayed = onPlayLastPlayed,
            onNetworkStreamClick = onNetworkStreamClick,
            onDismiss = { state.collapse() }
        )

        Surface(
            modifier = Modifier
                .size(NosvedFabDefaults.FabSize)
                .graphicsLayer {
                    scaleX = pressScale
                    scaleY = pressScale
                }
                .clip(currentShape)
                .combinedClickable(
                    interactionSource = interactionSource,
                    indication = ripple(bounded = true),
                    role = Role.Button,
                    onClickLabel = if (state.isExpanded) {
                        "Close options"
                    } else if (hasValidMedia) {
                        "Play last played video"
                    } else {
                        "Open quick actions"
                    },
                    onLongClickLabel = if (state.isExpanded) "Close options" else "Expand quick actions",
                    onClick = {
                        when {
                            state.isExpanded -> state.collapse()
                            hasValidMedia -> onPlayLastPlayed()
                            else -> {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                state.expand()
                            }
                        }
                    },
                    onLongClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        state.toggle()
                    }
                )
                .semantics {
                    contentDescription = accessibilityLabel
                    stateDescription = if (state.isExpanded) "Expanded" else "Collapsed"
                    customActions = accessibilityActions
                },
            shape = currentShape,
            color = containerColor,
            shadowElevation = if (state.isExpanded) 3.dp else 6.dp,
            tonalElevation = 0.dp
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier.size(NosvedFabDefaults.IconContainerSize),
                    contentAlignment = Alignment.Center
                ) {
                    if (morphProgress < 0.99f) {
                        NosvedLogoMark(
                            modifier = Modifier
                                .fillMaxSize()
                                .graphicsLayer {
                                    val logoProgress = (1f - morphProgress * 1.4f).coerceIn(0f, 1f)
                                    alpha = logoProgress
                                    val scale = 0.78f + 0.22f * logoProgress
                                    scaleX = scale
                                    scaleY = scale
                                    rotationZ = -45f * morphProgress
                                },
                            tightBounds = true,
                            colorProvider = { contentColor }
                        )
                    }
                    if (morphProgress > 0.01f) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = null,
                            tint = contentColor,
                            modifier = Modifier
                                .size(NosvedFabDefaults.CloseIconSize)
                                .graphicsLayer {
                                    val closeProgress = ((morphProgress - 0.25f) / 0.75f).coerceIn(0f, 1f)
                                    alpha = closeProgress
                                    val scale = 0.75f + 0.25f * closeProgress
                                    scaleX = scale
                                    scaleY = scale
                                    rotationZ = -90f + 90f * morphProgress
                                }
                        )
                    }
                }
            }
        }
    }
}

