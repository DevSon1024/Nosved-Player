package com.devson.nvplayer.ui.common.components

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import android.view.WindowManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.devson.nvplayer.player.model.DecoderMode
import `is`.xyz.mpv.MPVLib
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.FileReader

data class DeviceStats(
    val cpuPercent: Int = 0,
    val tempCelsius: Float = 0f,
    val usedMemMb: Long = 0,
    val totalMemMb: Long = 0,
    val batteryPercent: Int = 0,
    val batteryCharging: Boolean = false,
    val videoFps: Float = 0f
)

data class DecoderInfo(
    val badge: String,
    val isHardware: Boolean,
    val decoderName: String,
    val technology: String
)

data class MpvPlaybackStats(
    val requestedDecoderMode: String = "HW (Hardware)",
    val requestedAudioDecoderMode: String = "Auto (Hardware / AudioTrack)",
    val videoResolution: String = "N/A",
    val videoCodecMime: String = "N/A",
    val videoCodecFriendly: String = "N/A",
    val videoPixelFormat: String = "N/A",
    val videoColorMatrix: String = "N/A",
    val videoBitrate: String = "N/A",
    val videoFramerate: String = "N/A",
    val videoTracksCount: Int = 0,
    val videoDecoderInfo: DecoderInfo = DecoderInfo("HW", true, "MediaCodec", "Hardware Accelerated MPV Pipeline"),
    val videoDecoderStateDisplay: String = "Actively Decoding",
    val framesDropped: Long = 0L,

    val audioCodecMime: String = "N/A",
    val audioCodecFriendly: String = "N/A",
    val audioSampleRate: String = "N/A",
    val audioChannels: String = "N/A",
    val audioLanguage: String = "None",
    val audioTrackLabel: String = "None",
    val audioBitrate: String = "N/A",
    val audioTracksCount: Int = 0,
    val audioOtherLanguages: String = "",
    val audioDecoderInfo: DecoderInfo = DecoderInfo("HW", true, "Hardware / System AudioTrack", "Direct Audio Pipeline"),
    val audioDecoderStateDisplay: String = "Actively Decoding",

    val hasActiveSubtitle: Boolean = false,
    val subtitleCodec: String = "None",
    val subtitleLanguage: String = "None",
    val subtitleTracksCount: Int = 0,
    val subtitleOtherLanguages: String = "",

    val bufferHealth: String = "0.0s",
    val playbackSpeed: String = "1.00x",
    val displayRefreshRate: String = "N/A",
    val screenResolution: String = "N/A",

    val mpvVersion: String = "0.37.0",
    val ffmpegVersion: String = "6.1"
)

@Composable
fun DeviceStatsOverlay(
    visible: Boolean,
    decoderMode: String,
    useSwAudioDecoder: Boolean = false,
    modifier: Modifier = Modifier,
    onDismiss: () -> Unit = {}
) {
    val mode = remember(decoderMode) {
        runCatching { DecoderMode.valueOf(decoderMode) }.getOrDefault(DecoderMode.HW)
    }
    DeviceStatsOverlay(
        visible = visible,
        decoderMode = mode,
        useSwAudioDecoder = useSwAudioDecoder,
        modifier = modifier,
        onDismiss = onDismiss
    )
}

@Composable
fun DeviceStatsOverlay(
    visible: Boolean,
    decoderMode: DecoderMode = DecoderMode.HW,
    useSwAudioDecoder: Boolean = false,
    modifier: Modifier = Modifier,
    onDismiss: () -> Unit = {}
) {
    val context = LocalContext.current
    var deviceStats by remember { mutableStateOf(DeviceStats()) }
    var playbackStats by remember { mutableStateOf(MpvPlaybackStats()) }
    val updatedDecoderMode by rememberUpdatedState(decoderMode)
    val updatedUseSwAudioDecoder by rememberUpdatedState(useSwAudioDecoder)

    LaunchedEffect(visible) {
        if (visible) {
            var prevCpuStat: LongArray? = null
            while (isActive) {
                val (dStats, newCpuStat) = withContext(Dispatchers.IO) {
                    collectStats(context, prevCpuStat)
                }
                prevCpuStat = newCpuStat

                val pStats = withContext(Dispatchers.IO) {
                    collectMpvPlaybackStats(context, updatedDecoderMode, updatedUseSwAudioDecoder)
                }

                deviceStats = dStats
                playbackStats = pStats

                delay(1000)
            }
        }
    }

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = modifier
    ) {
        Card(
            modifier = Modifier
                .widthIn(min = 310.dp, max = 370.dp)
                .heightIn(max = 520.dp)
                .padding(8.dp),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f)
            ),
            border = androidx.compose.foundation.BorderStroke(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f)
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // Header with Close
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primaryContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Analytics,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                        Text(
                            text = "STATS & DIAGNOSTICS",
                            color = MaterialTheme.colorScheme.onSurface,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close Stats",
                            tint = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.20f))

                // 1. VIDEO TRACK & DECODER
                TrackSectionHeader(
                    title = "VIDEO TRACK & DECODER",
                    badge = playbackStats.videoDecoderInfo.badge,
                    isHardware = playbackStats.videoDecoderInfo.isHardware
                )
                StatItem("Decoder Mode", playbackStats.requestedDecoderMode)
                StatItem("Active Decoder", playbackStats.videoDecoderInfo.decoderName)
                StatItem("Decoder State", playbackStats.videoDecoderStateDisplay)
                StatItem("Technology", playbackStats.videoDecoderInfo.technology)
                StatItem("Codec", "${playbackStats.videoCodecFriendly} (${playbackStats.videoCodecMime})")
                StatItem("Resolution", playbackStats.videoResolution)
                StatItem("Framerate", playbackStats.videoFramerate)
                StatItem("Pixel Format", playbackStats.videoPixelFormat)
                if (playbackStats.videoColorMatrix != "N/A") {
                    StatItem("Color Matrix", playbackStats.videoColorMatrix)
                }
                StatItem("Bitrate", playbackStats.videoBitrate)
                if (playbackStats.framesDropped > 0L) {
                    StatItem("Dropped Frames", "${playbackStats.framesDropped} frames", Color(0xFFEF5350))
                }
                if (playbackStats.videoTracksCount > 1) {
                    StatItem("Video Streams", "${playbackStats.videoTracksCount} track(s)")
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.20f))

                // 2. AUDIO TRACK & DECODER
                TrackSectionHeader(
                    title = "AUDIO TRACK & DECODER",
                    badge = playbackStats.audioDecoderInfo.badge,
                    isHardware = playbackStats.audioDecoderInfo.isHardware
                )
                StatItem("Decoder Mode", playbackStats.requestedAudioDecoderMode)
                StatItem(
                    "Active Track",
                    if (playbackStats.audioDecoderInfo.badge == "OFF" || playbackStats.audioTrackLabel == "None") "None (Disabled)"
                    else "${playbackStats.audioTrackLabel} (${playbackStats.audioLanguage})"
                )
                StatItem("Active Decoder", playbackStats.audioDecoderInfo.decoderName)
                StatItem("Decoder State", playbackStats.audioDecoderStateDisplay)
                StatItem("Technology", playbackStats.audioDecoderInfo.technology)
                StatItem(
                    "Codec",
                    if (playbackStats.audioDecoderInfo.badge == "OFF") "None"
                    else "${playbackStats.audioCodecFriendly} (${playbackStats.audioCodecMime})"
                )
                StatItem("Channels", if (playbackStats.audioDecoderInfo.badge == "OFF") "None" else playbackStats.audioChannels)
                StatItem("Sample Rate", if (playbackStats.audioDecoderInfo.badge == "OFF") "None" else playbackStats.audioSampleRate)
                if (playbackStats.audioBitrate != "N/A" && playbackStats.audioDecoderInfo.badge != "OFF") {
                    StatItem("Audio Bitrate", playbackStats.audioBitrate)
                }
                if (playbackStats.audioTracksCount > 1) {
                    val otherLangs = playbackStats.audioOtherLanguages.ifBlank { "None" }
                    StatItem("Tracks (${playbackStats.audioTracksCount})", otherLangs)
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.20f))

                // 3. SUBTITLE TRACK
                TrackSectionHeader(
                    title = "SUBTITLE TRACK",
                    badge = if (playbackStats.hasActiveSubtitle) "ACTIVE" else "OFF",
                    isHardware = !playbackStats.hasActiveSubtitle
                )
                StatItem("Active Subtitle", if (playbackStats.hasActiveSubtitle) playbackStats.subtitleLanguage else "None")
                if (playbackStats.hasActiveSubtitle) {
                    StatItem("Format / Codec", playbackStats.subtitleCodec)
                    StatItem("Renderer", "MPV libass Native Surface")
                }
                if (playbackStats.subtitleTracksCount > 0) {
                    val subText = if (playbackStats.subtitleOtherLanguages.isNotBlank()) playbackStats.subtitleOtherLanguages else "${playbackStats.subtitleTracksCount} track(s)"
                    StatItem("Available Subs", subText)
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.20f))

                // 4. PLAYBACK & DISPLAY
                StatsSectionHeader("PLAYBACK & DISPLAY")
                StatItem("Buffer Cache", playbackStats.bufferHealth)
                StatItem("Playback Speed", playbackStats.playbackSpeed)
                StatItem("Display Refresh", playbackStats.displayRefreshRate)
                StatItem("Screen Resolution", playbackStats.screenResolution)

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.20f))

                // 5. SYSTEM & ENGINE
                StatsSectionHeader("SYSTEM & ENGINE")
                StatItem(
                    "MPV Engine Core",
                    "mpv ${playbackStats.mpvVersion} (FFmpeg ${playbackStats.ffmpegVersion})"
                )
                val defaultValColor = MaterialTheme.colorScheme.onSurface
                StatItem(
                    "CPU Usage",
                    "${deviceStats.cpuPercent}%",
                    cpuWarningColor(deviceStats.cpuPercent, defaultValColor)
                )

                val ramText = if (deviceStats.totalMemMb > 1024) {
                    "%.1f / %.1f GB".format(deviceStats.usedMemMb / 1024f, deviceStats.totalMemMb / 1024f)
                } else {
                    "${deviceStats.usedMemMb} / ${deviceStats.totalMemMb} MB"
                }
                StatItem(
                    "Memory (RAM)",
                    ramText,
                    memWarningColor(deviceStats.usedMemMb, deviceStats.totalMemMb, defaultValColor)
                )
                StatItem(
                    "Battery",
                    "${deviceStats.batteryPercent}% ${if (deviceStats.batteryCharging) "⚡" else ""}",
                    batteryWarningColor(deviceStats.batteryPercent, defaultValColor)
                )
                StatItem(
                    "Temperature",
                    if (deviceStats.tempCelsius > 0f) "%.1f°C".format(deviceStats.tempCelsius) else "N/A",
                    tempWarningColor(deviceStats.tempCelsius, defaultValColor)
                )
            }
        }
    }
}

@Composable
private fun TrackSectionHeader(
    title: String,
    badge: String? = null,
    isHardware: Boolean? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp, bottom = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            color = MaterialTheme.colorScheme.primary,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.8.sp
        )
        if (badge != null && isHardware != null) {
            DecoderBadge(badge = badge, isHardware = isHardware)
        }
    }
}

@Composable
private fun DecoderBadge(badge: String, isHardware: Boolean) {
    val (bgColor, textColor) = if (badge == "PCM") {
        Color(0x222196F3) to Color(0xFF90CAF9)
    } else if (badge == "OFF") {
        Color(0x229E9E9E) to Color(0xFFBDBDBD)
    } else if (isHardware) {
        Color(0x224CAF50) to Color(0xFF81C784)
    } else {
        Color(0x28FF9800) to Color(0xFFFFB74D)
    }
    Surface(
        color = bgColor,
        shape = RoundedCornerShape(4.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, textColor.copy(alpha = 0.45f))
    ) {
        Text(
            text = badge,
            color = textColor,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            fontSize = 10.sp,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp)
        )
    }
}

@Composable
private fun StatsSectionHeader(title: String) {
    Text(
        text = title,
        color = MaterialTheme.colorScheme.primary,
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.Bold,
        letterSpacing = 0.8.sp,
        modifier = Modifier.padding(top = 4.dp, bottom = 2.dp)
    )
}

@Composable
private fun StatItem(
    label: String,
    value: String,
    valueColor: Color = MaterialTheme.colorScheme.onSurface
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 1.5.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.70f),
            style = MaterialTheme.typography.bodySmall,
            fontSize = 11.sp,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = value,
            color = valueColor,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium,
            fontFamily = FontFamily.Monospace,
            fontSize = 11.5.sp,
            textAlign = TextAlign.End,
            modifier = Modifier.padding(start = 8.dp)
        )
    }
}

private fun friendlyCodec(codec: String?): String {
    if (codec.isNullOrBlank()) return "N/A"
    val lower = codec.lowercase()
    return when {
        lower.contains("hevc") || lower.contains("h265") -> "HEVC / H.265"
        lower.contains("avc") || lower.contains("h264") -> "AVC / H.264"
        lower.contains("av1") || lower.contains("av01") -> "AV1"
        lower.contains("vp9") -> "VP9"
        lower.contains("vp8") -> "VP8"
        lower.contains("mpeg4") || lower.contains("mp4v") -> "MPEG-4 Part 2"
        lower.contains("mpeg2") -> "MPEG-2"
        lower.contains("vc1") || lower.contains("wmv3") -> "VC-1 / WMV3"
        lower.contains("prores") -> "Apple ProRes"
        lower.contains("eac3") -> "Dolby Digital Plus (E-AC-3)"
        lower.contains("ac3") -> "Dolby Digital (AC-3)"
        lower.contains("aac") -> "AAC"
        lower.contains("opus") -> "Opus"
        lower.contains("flac") -> "FLAC"
        lower.contains("truehd") -> "Dolby TrueHD"
        lower.contains("dts") -> "DTS"
        lower.contains("vorbis") -> "Vorbis"
        lower.contains("pcm") -> "Linear PCM"
        lower.contains("mp3") -> "MP3"
        lower.contains("ass") || lower.contains("ssa") -> "Advanced SSA / ASS"
        lower.contains("subrip") || lower.contains("srt") -> "SubRip (SRT)"
        lower.contains("vtt") -> "WebVTT"
        lower.contains("pgs") -> "PGS Bitmap"
        else -> codec.uppercase()
    }
}

private fun cpuWarningColor(percent: Int, defaultColor: Color): Color = when {
    percent >= 85 -> Color(0xFFEF5350)
    percent >= 70 -> Color(0xFFFFB74D)
    else -> defaultColor
}

private fun tempWarningColor(temp: Float, defaultColor: Color): Color = when {
    temp >= 48f -> Color(0xFFEF5350)
    temp >= 42f -> Color(0xFFFFB74D)
    else -> defaultColor
}

private fun batteryWarningColor(percent: Int, defaultColor: Color): Color = when {
    percent <= 15 -> Color(0xFFEF5350)
    percent <= 25 -> Color(0xFFFFB74D)
    else -> defaultColor
}

private fun memWarningColor(usedMb: Long, totalMb: Long, defaultColor: Color): Color {
    if (totalMb <= 0) return defaultColor
    val pct = usedMb.toFloat() / totalMb.toFloat()
    return when {
        pct >= 0.90f -> Color(0xFFEF5350)
        pct >= 0.80f -> Color(0xFFFFB74D)
        else -> defaultColor
    }
}

private fun collectMpvPlaybackStats(
    context: Context,
    decoderMode: DecoderMode,
    useSwAudioDecoder: Boolean = false
): MpvPlaybackStats {
    val reqModeStr = when (decoderMode) {
        DecoderMode.SW -> "SW (Software Only)"
        DecoderMode.HW -> "HW (Hardware)"
        DecoderMode.HW_PLUS -> "HW+ (Hardware Auto)"
    }

    var vCodec = "N/A"
    var vFormat = "N/A"
    var vPixelFormat = "N/A"
    var vColorMatrix = "N/A"
    var vRes = "N/A"
    var vFps = "N/A"
    var vBitrate = "N/A"
    var dropFrames = 0L
    var videoTracksCount = 0

    var aCodec = "N/A"
    var aChannels = "N/A"
    var aSampleRate = "N/A"
    var aLang = "None"
    var aLabel = "None"
    var aBitrate = "N/A"
    var audioTracksCount = 0
    val audioLangs = mutableListOf<String>()

    var hasSub = false
    var sCodec = "None"
    var sLang = "None"
    var subTracksCount = 0
    val subLangs = mutableListOf<String>()

    var hwdecCurrent = "no"
    var isPaused = false
    var speed = "1.00x"
    var cacheDuration = "0.0s"
    var mpvVer = "0.37.0"
    var ffmpegVer = "6.1"

    try {
        vCodec = MPVLib.getPropertyString("video-codec") ?: "N/A"
        vFormat = MPVLib.getPropertyString("video-format") ?: "N/A"
        vPixelFormat = MPVLib.getPropertyString("video-params/pixelformat") ?: "N/A"
        val colormatrix = MPVLib.getPropertyString("video-params/colormatrix")
        val primaries = MPVLib.getPropertyString("video-params/primaries")
        vColorMatrix = if (!colormatrix.isNullOrBlank()) "$colormatrix / ${primaries ?: "standard"}" else "N/A"

        val w = MPVLib.getPropertyInt("video-params/w") ?: MPVLib.getPropertyInt("width") ?: 0
        val h = MPVLib.getPropertyInt("video-params/h") ?: MPVLib.getPropertyInt("height") ?: 0
        if (w > 0 && h > 0) {
            vRes = "${w}x${h}"
        }

        val fps = MPVLib.getPropertyDouble("estimated-vf-fps")
            ?: MPVLib.getPropertyDouble("container-fps")
            ?: MPVLib.getPropertyDouble("fps")
            ?: 0.0
        if (fps > 0.0) {
            vFps = "%.2f fps".format(fps)
        }

        val vBr = MPVLib.getPropertyDouble("video-bitrate") ?: 0.0
        if (vBr > 0.0) {
            vBitrate = "%.0f kbps".format(vBr / 1000.0)
        }

        dropFrames = (MPVLib.getPropertyInt("drop-frame-count") ?: MPVLib.getPropertyInt("vo-drop-frame-count") ?: 0).toLong()
        hwdecCurrent = MPVLib.getPropertyString("hwdec-current") ?: "no"
        isPaused = MPVLib.getPropertyBoolean("pause") ?: false

        val spd = MPVLib.getPropertyDouble("speed") ?: 1.0
        speed = "%.2fx".format(spd)

        val cacheSec = MPVLib.getPropertyDouble("demuxer-cache-duration") ?: 0.0
        cacheDuration = "%.1fs".format(cacheSec)

        mpvVer = MPVLib.getPropertyString("mpv-version") ?: "0.37.0"
        ffmpegVer = MPVLib.getPropertyString("ffmpeg-version") ?: "6.1"

        // Track inspection
        val trackCount = MPVLib.getPropertyInt("track-list/count") ?: 0
        for (i in 0 until trackCount) {
            val type = MPVLib.getPropertyString("track-list/$i/type") ?: ""
            val selected = MPVLib.getPropertyBoolean("track-list/$i/selected") ?: false
            val title = MPVLib.getPropertyString("track-list/$i/title") ?: ""
            val lang = MPVLib.getPropertyString("track-list/$i/lang") ?: ""
            val codec = MPVLib.getPropertyString("track-list/$i/codec") ?: ""

            when (type) {
                "video" -> {
                    videoTracksCount++
                }
                "audio" -> {
                    audioTracksCount++
                    val dispLang = if (lang.isNotBlank()) lang else "Track $audioTracksCount"
                    if (!audioLangs.contains(dispLang)) audioLangs.add(dispLang)
                    if (selected) {
                        aCodec = codec.ifBlank { MPVLib.getPropertyString("audio-codec") ?: "N/A" }
                        aLang = if (lang.isNotBlank()) lang else "Default"
                        aLabel = if (title.isNotBlank()) title else aLang
                    }
                }
                "sub" -> {
                    subTracksCount++
                    val dispLang = if (lang.isNotBlank()) lang else "Sub $subTracksCount"
                    if (!subLangs.contains(dispLang)) subLangs.add(dispLang)
                    if (selected) {
                        hasSub = true
                        sCodec = codec.ifBlank { "Text" }
                        sLang = if (title.isNotBlank()) "$title ($lang)" else if (lang.isNotBlank()) lang else "Active"
                    }
                }
            }
        }

        aChannels = MPVLib.getPropertyString("audio-params/channels") ?: "N/A"
        val sr = MPVLib.getPropertyInt("audio-params/samplerate") ?: 0
        if (sr > 0) aSampleRate = "$sr Hz"

        val aBr = MPVLib.getPropertyDouble("audio-bitrate") ?: 0.0
        if (aBr > 0.0) aBitrate = "%.0f kbps".format(aBr / 1000.0)

    } catch (_: Exception) {}

    val isHw = hwdecCurrent != "no" && hwdecCurrent.isNotBlank()
    val videoDecoderInfo = if (isHw) {
        DecoderInfo(
            badge = "HW",
            isHardware = true,
            decoderName = "Android MediaCodec ($hwdecCurrent)",
            technology = "Hardware Accelerated Surface Pipeline"
        )
    } else {
        DecoderInfo(
            badge = "SW",
            isHardware = false,
            decoderName = "FFmpeg libavcodec Software",
            technology = "Multi-threaded CPU Decoding"
        )
    }

    val aid = try {
        MPVLib.getPropertyInt("aid") ?: -1
    } catch (_: Exception) {
        -1
    }
    val ad = try {
        MPVLib.getPropertyString("ad") ?: ""
    } catch (_: Exception) {
        ""
    }

    val isAudioDisabled = aid <= 0
    val isSwAudio = useSwAudioDecoder || ad.contains("lavc")

    val audioDecoderInfo = when {
        isAudioDisabled -> DecoderInfo(
            badge = "OFF",
            isHardware = false,
            decoderName = "Disabled (Audio Muted)",
            technology = "Audio Stream Inactive"
        )
        isSwAudio -> DecoderInfo(
            badge = "SW",
            isHardware = false,
            decoderName = "FFmpeg libavcodec ($aCodec)",
            technology = "Software CPU Audio Decoding"
        )
        else -> DecoderInfo(
            badge = "HW",
            isHardware = true,
            decoderName = "Android AudioTrack / System ($aCodec)",
            technology = "Direct Hardware / AudioTrack Pipeline"
        )
    }

    val reqAudioModeStr = when {
        isAudioDisabled -> "Disabled (Audio Muted)"
        isSwAudio -> "SW (Software FFmpeg)"
        else -> "Auto (Hardware / AudioTrack)"
    }

    val display = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        context.display
    } else {
        @Suppress("DEPRECATION")
        (context.getSystemService(Context.WINDOW_SERVICE) as WindowManager).defaultDisplay
    }
    val refreshRate = display?.refreshRate ?: 0f
    val screenRes = context.resources.displayMetrics.run { "${widthPixels}x${heightPixels}" }

    return MpvPlaybackStats(
        requestedDecoderMode = reqModeStr,
        requestedAudioDecoderMode = reqAudioModeStr,
        videoResolution = vRes,
        videoCodecMime = vCodec,
        videoCodecFriendly = friendlyCodec(vCodec),
        videoPixelFormat = vPixelFormat,
        videoColorMatrix = vColorMatrix,
        videoBitrate = vBitrate,
        videoFramerate = vFps,
        videoTracksCount = videoTracksCount,
        videoDecoderInfo = videoDecoderInfo,
        videoDecoderStateDisplay = if (isPaused) "Paused" else "Actively Decoding",
        framesDropped = dropFrames,

        audioCodecMime = aCodec,
        audioCodecFriendly = friendlyCodec(aCodec),
        audioSampleRate = aSampleRate,
        audioChannels = aChannels,
        audioLanguage = aLang,
        audioTrackLabel = aLabel,
        audioBitrate = aBitrate,
        audioTracksCount = audioTracksCount,
        audioOtherLanguages = audioLangs.filter { it != aLang }.joinToString(", "),
        audioDecoderInfo = audioDecoderInfo,
        audioDecoderStateDisplay = if (isAudioDisabled) "Muted / Inactive" else if (isPaused) "Paused" else "Actively Decoding",

        hasActiveSubtitle = hasSub,
        subtitleCodec = friendlyCodec(sCodec),
        subtitleLanguage = sLang,
        subtitleTracksCount = subTracksCount,
        subtitleOtherLanguages = subLangs.filter { it != sLang }.joinToString(", "),

        bufferHealth = cacheDuration,
        playbackSpeed = speed,
        displayRefreshRate = if (refreshRate > 0f) "%.1f Hz".format(refreshRate) else "N/A",
        screenResolution = screenRes,

        mpvVersion = mpvVer,
        ffmpegVersion = ffmpegVer
    )
}

private fun collectStats(
    context: Context,
    prevCpuStat: LongArray?
): Pair<DeviceStats, LongArray?> {
    val (cpu, newCpuStat) = readCpuUsage(prevCpuStat)

    val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
    val memInfo = ActivityManager.MemoryInfo()
    am.getMemoryInfo(memInfo)
    val totalMb = memInfo.totalMem / 1_048_576L
    val availMb = memInfo.availMem / 1_048_576L
    val usedMb = totalMb - availMb

    val batteryIntent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
    val level = batteryIntent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
    val scale = batteryIntent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
    val batteryPct = if (level >= 0 && scale > 0) (level * 100 / scale) else 0
    val plugged = batteryIntent?.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1) ?: 0
    val charging = plugged != 0

    val rawTemp = batteryIntent?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) ?: 0
    val tempC = rawTemp / 10f

    val stats = DeviceStats(
        cpuPercent = cpu,
        tempCelsius = tempC,
        usedMemMb = usedMb,
        totalMemMb = totalMb,
        batteryPercent = batteryPct,
        batteryCharging = charging,
        videoFps = 0f
    )
    return Pair(stats, newCpuStat)
}

private fun readCpuUsage(prevStat: LongArray?): Pair<Int, LongArray?> {
    return try {
        val line = BufferedReader(FileReader("/proc/stat")).use { it.readLine() } ?: return Pair(0, null)
        val parts = line.trim().split("\\s+".toRegex()).drop(1)
        val currStat = parts.take(8).map { it.toLongOrNull() ?: 0L }.toLongArray()
        if (currStat.isEmpty()) return Pair(0, null)
        if (prevStat == null || prevStat.isEmpty()) {
            return Pair(0, currStat)
        }

        val idle1 = prevStat.getOrElse(3) { 0L }
        val idle2 = currStat.getOrElse(3) { 0L }
        val total1 = prevStat.sum()
        val total2 = currStat.sum()

        val totalDiff = total2 - total1
        val idleDiff = idle2 - idle1
        val usage = if (totalDiff <= 0) 0
        else ((totalDiff - idleDiff) * 100L / totalDiff).toInt().coerceIn(0, 100)
        Pair(usage, currStat)
    } catch (_: Exception) {
        Pair(0, null)
    }
}

