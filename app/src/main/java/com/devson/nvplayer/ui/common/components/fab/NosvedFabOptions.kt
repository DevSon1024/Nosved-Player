package com.devson.nvplayer.ui.common.components.fab

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import coil3.request.crossfade
import coil3.size.Precision
import coil3.video.videoFrameMillis
import com.devson.nvplayer.util.formatDuration

private val OptionPillShape = RoundedCornerShape(18.dp)
private val PreviewCardShape = RoundedCornerShape(20.dp)
private val PreviewImageShape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)

/**
 * Renders the expandable quick-action menu around [NosvedExpressiveFab] without overlapping it.
 *
 * Options live inside [AnimatedVisibility] so thumbnail decoding and image requests are only
 * created while expanded and are cancelled as soon as the menu closes.
 */
@Composable
internal fun NosvedFabOptionsMenu(
    expanded: Boolean,
    enablePreview: Boolean,
    previewData: NosvedFabPreviewData?,
    onPlayLastPlayed: () -> Unit,
    onNetworkStreamClick: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val configuration = LocalConfiguration.current
    val isCompactHeight = configuration.screenHeightDp < NosvedFabDefaults.CompactHeightThresholdDp
    val maxMenuWidth = (configuration.screenWidthDp - 32).coerceAtLeast(220).dp
    val maxMenuHeight = (configuration.screenHeightDp - 196).coerceAtLeast(120).dp
    val hasValidPreview = previewData != null && previewData.hasValidMedia

    AnimatedVisibility(
        visible = expanded,
        enter = fadeIn(animationSpec = tween(durationMillis = 120)),
        exit = fadeOut(animationSpec = tween(durationMillis = 110)),
        modifier = modifier
            .padding(bottom = NosvedFabDefaults.FabSize + NosvedFabDefaults.ExpandedMenuBottomSpacing)
            .widthIn(max = maxMenuWidth)
            .heightIn(max = maxMenuHeight)
    ) {
        if (isCompactHeight && hasValidPreview) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(NosvedFabDefaults.OptionSpacing),
                verticalAlignment = Alignment.Bottom
            ) {
                NetworkStreamOptionItem(
                    delayMillis = 40,
                    onClick = {
                        onDismiss()
                        onNetworkStreamClick()
                    }
                )
                LastPlayedOptionContent(
                    enablePreview = enablePreview,
                    previewData = previewData,
                    imageHeight = NosvedFabDefaults.CompactPreviewImageHeight,
                    onClick = {
                        onDismiss()
                        onPlayLastPlayed()
                    }
                )
            }
        } else {
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(NosvedFabDefaults.OptionSpacing)
            ) {
                NetworkStreamOptionItem(
                    delayMillis = if (hasValidPreview) 45 else 0,
                    onClick = {
                        onDismiss()
                        onNetworkStreamClick()
                    }
                )
                if (hasValidPreview) {
                    LastPlayedOptionContent(
                        enablePreview = enablePreview,
                        previewData = previewData,
                        imageHeight = NosvedFabDefaults.PreviewImageHeight,
                        onClick = {
                            onDismiss()
                            onPlayLastPlayed()
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun AnimatedVisibilityScope.NetworkStreamOptionItem(
    delayMillis: Int,
    onClick: () -> Unit
) {
    NosvedFabOptionItem(
        label = "Network Stream",
        icon = Icons.Rounded.Language,
        onClick = onClick,
        modifier = staggeredMenuItemModifier(delayMillis = delayMillis)
    )
}

@Composable
private fun AnimatedVisibilityScope.LastPlayedOptionContent(
    enablePreview: Boolean,
    previewData: NosvedFabPreviewData,
    imageHeight: Dp,
    onClick: () -> Unit
) {
    val itemModifier = staggeredMenuItemModifier(delayMillis = 0)
    if (enablePreview) {
        LastPlayedPreviewCard(
            previewData = previewData,
            imageHeight = imageHeight,
            onClick = onClick,
            modifier = itemModifier
        )
    } else {
        NosvedFabOptionItem(
            label = "Last Played",
            icon = Icons.Rounded.PlayArrow,
            onClick = onClick,
            modifier = itemModifier
        )
    }
}

@Composable
private fun AnimatedVisibilityScope.staggeredMenuItemModifier(delayMillis: Int): Modifier {
    return Modifier.animateEnterExit(
        enter = fadeIn(
            animationSpec = tween(durationMillis = 160, delayMillis = delayMillis, easing = FastOutSlowInEasing)
        ) + slideInVertically(
            initialOffsetY = { it / 4 },
            animationSpec = tween(durationMillis = 180, delayMillis = delayMillis, easing = FastOutSlowInEasing)
        ) + scaleIn(
            initialScale = 0.92f,
            transformOrigin = TransformOrigin(0.95f, 1f),
            animationSpec = tween(durationMillis = 180, delayMillis = delayMillis, easing = FastOutSlowInEasing)
        ),
        exit = fadeOut(
            animationSpec = tween(durationMillis = 100)
        ) + slideOutVertically(
            targetOffsetY = { it / 5 },
            animationSpec = tween(durationMillis = 110)
        ) + scaleOut(
            targetScale = 0.92f,
            transformOrigin = TransformOrigin(0.95f, 1f),
            animationSpec = tween(durationMillis = 110)
        )
    )
}

@Composable
private fun NosvedFabOptionItem(
    label: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        shape = OptionPillShape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = MaterialTheme.colorScheme.onSurface,
        tonalElevation = 4.dp,
        shadowElevation = 4.dp,
        border = BorderStroke(
            width = 1.dp,
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
        ),
        modifier = modifier
            .heightIn(min = 48.dp)
            .semantics {
                role = Role.Button
                contentDescription = label
            }
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
        ) {
            Surface(
                modifier = Modifier.size(32.dp),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun LastPlayedPreviewCard(
    previewData: NosvedFabPreviewData,
    imageHeight: Dp,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val uri = previewData.uri ?: return
    val context = LocalContext.current
    val lastPositionMs = previewData.lastPositionMs.coerceAtLeast(0L)
    val durationMs = previewData.durationMs.coerceAtLeast(0L)

    val playedFormatted = remember(lastPositionMs) { formatDuration(lastPositionMs) }
    val remainingMs = remember(durationMs, lastPositionMs) { (durationMs - lastPositionMs).coerceAtLeast(0L) }
    val remainingFormatted = remember(remainingMs) { formatDuration(remainingMs) }
    val framePositionMs = remember(lastPositionMs) { lastPositionMs.coerceAtLeast(1000L) }

    val imageRequest = remember(context, uri, framePositionMs) {
        ImageRequest.Builder(context)
            .data(uri)
            .videoFrameMillis(framePositionMs)
            .size(424, 208)
            .precision(Precision.INEXACT)
            .memoryCachePolicy(CachePolicy.ENABLED)
            .diskCachePolicy(CachePolicy.ENABLED)
            .crossfade(180)
            .build()
    }

    val titleText = previewData.title ?: "Unknown"
    val progress = remember(durationMs, lastPositionMs) {
        if (durationMs > 0L) (lastPositionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f) else 0f
    }

    Card(
        modifier = modifier
            .width(NosvedFabDefaults.PreviewCardWidth)
            .clip(PreviewCardShape)
            .clickable(onClick = onClick)
            .semantics(mergeDescendants = true) {
                role = Role.Button
                contentDescription = "Resume $titleText, $playedFormatted played, $remainingFormatted remaining"
            },
        shape = PreviewCardShape,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.98f)
        ),
        border = BorderStroke(
            width = 1.dp,
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
    ) {
        Column {
            Box(
                modifier = Modifier
                    .height(imageHeight)
                    .fillMaxWidth()
                    .clip(PreviewImageShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                AsyncImage(
                    model = imageRequest,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.52f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.PlayArrow,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }

            if (durationMs > 0L) {
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(3.dp),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                )
            }

            Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
                Text(
                    text = titleText,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(3.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = playedFormatted,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = "/",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 10.sp
                    )
                    Text(
                        text = remainingFormatted,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
