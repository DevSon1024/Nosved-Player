package com.devson.nvplayer.ui.common.components.fab

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Shared layout dimensions and positioning constants for [NosvedExpressiveFab].
 */
object NosvedFabDefaults {
    val FabSize = 56.dp
    val IconContainerSize = 28.dp
    val CloseIconSize = 24.dp
    val BottomOffsetAboveCapsuleNav = 88.dp
    val ExpandedMenuBottomSpacing = 12.dp
    val ExpandedMenuEndSpacing = 12.dp
    val OptionSpacing = 10.dp
    val PreviewCardWidth = 212.dp
    val PreviewImageHeight = 104.dp
    val CompactPreviewImageHeight = 76.dp
    val CompactHeightThresholdDp = 480
}

/**
 * Immutable metadata describing the last-played or continue-watching video for the FAB.
 */
@Immutable
data class NosvedFabPreviewData(
    val uri: String?,
    val title: String?,
    val durationMs: Long,
    val lastPositionMs: Long
) {
    val hasValidMedia: Boolean
        get() = !uri.isNullOrBlank()
}

/**
 * State holder for [NosvedExpressiveFab] keeping expansion state isolated from parent screens.
 */
@Stable
class NosvedFabState(initialExpanded: Boolean = false) {
    var isExpanded: Boolean by mutableStateOf(initialExpanded)
        private set

    fun expand() {
        isExpanded = true
    }

    fun collapse() {
        isExpanded = false
    }

    fun toggle() {
        isExpanded = !isExpanded
    }
}

/**
 * Remembers a [NosvedFabState] scoped to the composable's lifecycle.
 */
@Composable
fun rememberNosvedFabState(initialExpanded: Boolean = false): NosvedFabState {
    return remember { NosvedFabState(initialExpanded = initialExpanded) }
}

/**
 * Applies consistent screen-space positioning for [NosvedExpressiveFab] inside a Material 3
 * `Scaffold`'s `floatingActionButton` slot.
 *
 * When [scaffoldHandlesBottomInsets] is false (e.g. `LibraryHomeScreen` with zeroed
 * `contentWindowInsets` so lists scroll edge-to-edge behind the bottom bar), this modifier adds
 * navigation bar and display cutout bottom insets explicitly so the FAB aligns identically with
 * screens whose `Scaffold` applies `ScaffoldDefaults.contentWindowInsets` (`VideoListScreen`).
 * End insets are always applied because Material 3 `Scaffold` does not offset `FabPosition.End`
 * horizontally for landscape navigation bars or display cutouts.
 */
@Composable
fun Modifier.nosvedQuickFabPosition(
    scaffoldHandlesBottomInsets: Boolean
): Modifier {
    val systemSafeInsets = WindowInsets.navigationBars.union(WindowInsets.displayCutout)
    val endAwareModifier = this.windowInsetsPadding(systemSafeInsets.only(WindowInsetsSides.End))
    return if (scaffoldHandlesBottomInsets) {
        endAwareModifier.padding(bottom = NosvedFabDefaults.BottomOffsetAboveCapsuleNav)
    } else {
        endAwareModifier
            .windowInsetsPadding(systemSafeInsets.only(WindowInsetsSides.Bottom))
            .padding(bottom = NosvedFabDefaults.BottomOffsetAboveCapsuleNav)
    }
}

