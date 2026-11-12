package com.devson.nvplayer.ui.common.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.devson.nvplayer.ui.common.components.fab.NosvedExpressiveFab
import com.devson.nvplayer.ui.common.components.fab.NosvedFabPreviewData

/**
 * Compatibility wrapper delegating legacy quick-FAB callers to [NosvedExpressiveFab].
 */
@Composable
fun PreviewFloatingActionButton(
    enablePreview: Boolean,
    previewUri: String?,
    previewTitle: String?,
    previewDurationMs: Long,
    previewLastPositionMs: Long,
    onPlay: () -> Unit,
    modifier: Modifier = Modifier,
    onNetworkStreamClick: () -> Unit = {}
) {
    val previewData = remember(previewUri, previewTitle, previewDurationMs, previewLastPositionMs) {
        previewUri?.let { uri ->
            NosvedFabPreviewData(
                uri = uri,
                title = previewTitle,
                durationMs = previewDurationMs,
                lastPositionMs = previewLastPositionMs
            )
        }
    }

    NosvedExpressiveFab(
        visible = true,
        enablePreview = enablePreview,
        previewData = previewData,
        onPlayLastPlayed = onPlay,
        onNetworkStreamClick = onNetworkStreamClick,
        modifier = modifier
    )
}
