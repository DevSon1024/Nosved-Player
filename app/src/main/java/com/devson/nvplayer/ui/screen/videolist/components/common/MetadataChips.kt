package com.devson.nvplayer.ui.screen.videolist.components.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.devson.nvplayer.domain.model.Video
import com.devson.nvplayer.domain.model.ViewSettings
import com.devson.nvplayer.util.formatDate
import com.devson.nvplayer.util.formatDuration
import com.devson.nvplayer.util.formatRelativeTime
import com.devson.nvplayer.util.formatResolutionCompact
import com.devson.nvplayer.util.formatSize

import androidx.compose.ui.graphics.Color
import java.util.Locale

enum class MetaChipType {
    DEFAULT,
    PRIMARY,
    EMBEDDED_SUBTITLE,
    EXTERNAL_SUBTITLE
}

data class MetaToken(
    val text: String,
    val type: MetaChipType = MetaChipType.DEFAULT
)

fun getSubtitleTokens(video: Video): List<MetaToken> = buildList {
    // Embedded Subtitles in Green color (MX Player style)
    video.embeddedSubtitles
        .map { it.trim() }
        .filter { it.isNotBlank() }
        .distinct()
        .forEach { sub ->
            val label = if (sub.equals("SUB", ignoreCase = true)) "SUB" else sub.uppercase(Locale.ROOT)
            add(MetaToken(label, MetaChipType.EMBEDDED_SUBTITLE))
        }

    // External Subtitles in Blue color (MX Player style)
    video.externalSubtitles
        .map { it.trim() }
        .filter { it.isNotBlank() }
        .distinct()
        .forEach { sub ->
            add(MetaToken(sub.uppercase(Locale.ROOT), MetaChipType.EXTERNAL_SUBTITLE))
        }
}

@Composable
fun VideoMetadataRow(
    video: Video,
    settings: ViewSettings,
    isGrid: Boolean = false,
    lastPositionMs: Long = 0L
) {
    VideoMetadataChips(video, settings, lastPositionMs, isGrid)
}

@Composable
fun VideoMetadataChips(
    video: Video,
    settings: ViewSettings,
    lastPositionMs: Long = 0L,
    isGrid: Boolean = false
) {
    val standardTokens = buildList {
        if (settings.showLength && !settings.displayLengthOverThumbnail)
            add(MetaToken(formatDuration(video.duration), MetaChipType.PRIMARY))
        if (settings.showPlayedTime && video.lastPlayedAt != null && video.lastPlayedAt > 0)
            add(MetaToken(formatRelativeTime(LocalContext.current, video.lastPlayedAt)))
        if (settings.showResolution && !video.resolution.isNullOrEmpty())
            add(MetaToken(formatResolutionCompact(video.resolution) ?: video.resolution))
        if (settings.showFrameRate && video.frameRate != null && video.frameRate > 0f) {
            val formattedFps = if (video.frameRate % 1f == 0f) {
                "${video.frameRate.toInt()} fps"
            } else {
                String.format(Locale.US, "%.2f fps", video.frameRate)
            }
            add(MetaToken(formattedFps))
        }
        if (settings.showFileExtension)
            add(MetaToken(video.title.substringAfterLast('.', video.uri.substringAfterLast('.', "")).uppercase()))
        if (settings.showSize)
            add(MetaToken(formatSize(video.size)))
        if (settings.showDate && video.dateAdded > 0)
            add(MetaToken(formatDate(video.dateAdded)))
    }.filter { it.text.isNotBlank() }

    val subtitleTokens = getSubtitleTokens(video)

    val visibleTokens = if (isGrid) {
        standardTokens.take(2)
    } else {
        subtitleTokens.take(2) + standardTokens.take(3)
    }

    if (visibleTokens.isEmpty()) return

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        visibleTokens.forEach { token ->
            MetadataChip(token = token, isGrid = isGrid)
        }
    }
}

@Composable
fun SubtitleBadge(
    token: MetaToken,
    isGrid: Boolean = false,
    modifier: Modifier = Modifier
) {
    val (bgColor, textColor) = when (token.type) {
        MetaChipType.EXTERNAL_SUBTITLE -> Color(0xFF1976D2) to Color.White
        else -> Color(0xFF2E7D32) to Color.White
    }
    Box(
        modifier = modifier
            .background(
                color = bgColor,
                shape = RoundedCornerShape(5.dp)
            )
            .padding(horizontal = 5.dp, vertical = 2.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = token.text,
            color = textColor,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            fontSize = if (isGrid) 11.sp else 10.sp,
            maxLines = 1
        )
    }
}

@Composable
fun MetadataChip(token: MetaToken, isGrid: Boolean = false) {
    val (bgColor, textColor, fontWeight) = when (token.type) {
        MetaChipType.EMBEDDED_SUBTITLE -> Triple(
            Color(0xFF2E7D32),
            Color.White,
            FontWeight.Bold
        )
        MetaChipType.EXTERNAL_SUBTITLE -> Triple(
            Color(0xFF1976D2),
            Color.White,
            FontWeight.Bold
        )
        MetaChipType.PRIMARY -> Triple(
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f),
            MaterialTheme.colorScheme.onPrimaryContainer,
            FontWeight.SemiBold
        )
        MetaChipType.DEFAULT -> Triple(
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
            MaterialTheme.colorScheme.onSurfaceVariant,
            FontWeight.Normal
        )
    }

    val fontSize = if (isGrid) 9.5.sp else 10.5.sp

    Box(
        modifier = Modifier
            .background(bgColor, RoundedCornerShape(5.dp))
            .padding(horizontal = 5.dp, vertical = 2.dp)
    ) {
        Text(
            text = token.text,
            style = MaterialTheme.typography.labelSmall,
            fontSize = fontSize,
            fontWeight = fontWeight,
            color = textColor,
            maxLines = 1
        )
    }
}

@Composable
fun MetadataChip(text: String, isPrimary: Boolean, isGrid: Boolean = false) {
    MetadataChip(
        token = MetaToken(text, if (isPrimary) MetaChipType.PRIMARY else MetaChipType.DEFAULT),
        isGrid = isGrid
    )
}
