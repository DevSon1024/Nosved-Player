package com.devson.nosvedplayer.ui.components

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Divider
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
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

@Composable
fun DeviceStatsOverlay(
    visible: Boolean,
    modifier: Modifier = Modifier,
    videoFps: Float = 0f,
    videoDecoderName: String? = null
) {
    val context = LocalContext.current
    var stats by remember { mutableStateOf(DeviceStats(videoFps = videoFps)) }

    // Poll stats every second while visible
    LaunchedEffect(visible) {
        while (visible) {
            stats = collectStats(context, videoFps)
            delay(1000)
        }
    }

    LaunchedEffect(videoFps) {
        stats = stats.copy(videoFps = videoFps)
    }

    AnimatedVisibility(
        visible = visible,
        enter = slideInHorizontally { it },
        exit = slideOutHorizontally { it },
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxHeight()
                .widthIn(min = 200.dp, max = 240.dp)
                .background(
                    color = Color.Black.copy(alpha = 0.82f),
                    shape = RoundedCornerShape(topStart = 16.dp, bottomStart = 16.dp)
                )
                .padding(vertical = 20.dp, horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(0.dp)
        ) {
            StatsHeader("Device Stats")
            Spacer(Modifier.padding(4.dp))
            HorizontalDivider(color = Color.White.copy(alpha = 0.2f))
            Spacer(Modifier.padding(4.dp))

            StatRow("CPU", "${stats.cpuPercent}%", levelColor(stats.cpuPercent.toFloat() / 100f))
            StatRow("Memory", "${stats.usedMemMb} / ${stats.totalMemMb} MB", Color.Cyan.copy(alpha = 0.85f))
            StatRow(
                "Battery",
                "${stats.batteryPercent}% ${if (stats.batteryCharging) "⚡" else ""}",
                batteryColor(stats.batteryPercent)
            )
            StatRow(
                "Temp",
                if (stats.tempCelsius > 0f) "%.1f°C".format(stats.tempCelsius) else "N/A",
                tempColor(stats.tempCelsius)
            )

            Spacer(Modifier.padding(4.dp))
            HorizontalDivider(color = Color.White.copy(alpha = 0.2f))
            Spacer(Modifier.padding(4.dp))
            StatsHeader("Video")
            Spacer(Modifier.padding(4.dp))

            StatRow(
                "Frame Rate",
                if (stats.videoFps > 0f) "%.1f fps".format(stats.videoFps) else "-",
                Color.White
            )
            val decoderLabel = when {
                videoDecoderName == null -> "-"
                videoDecoderName.contains("c2.android", ignoreCase = true) -> "[SW] $videoDecoderName"
                videoDecoderName.contains("omx.google", ignoreCase = true) -> "[SW] $videoDecoderName"
                videoDecoderName.contains("ffmpeg", ignoreCase = true) -> "[SW] $videoDecoderName"
                else -> "[HW] $videoDecoderName"
            }
            StatRow(
                "Decoder",
                decoderLabel,
                if (decoderLabel.contains("[SW]")) Color(0xFFFF9800) else Color(0xFF4CAF50)
            )
        }
    }
}

@Composable
private fun StatsHeader(title: String) {
    Text(
        text = title.uppercase(),
        color = Color.White.copy(alpha = 0.6f),
        fontSize = 10.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.5.sp,
        fontFamily = FontFamily.Monospace
    )
}

@Composable
private fun StatRow(label: String, value: String, valueColor: Color) {
    Row(
        modifier = Modifier
            .padding(vertical = 6.dp)
            .width(208.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            color = Color.White.copy(alpha = 0.7f),
            fontSize = 12.sp,
            fontFamily = FontFamily.Monospace
        )
        Text(
            text = value,
            color = valueColor,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            fontFamily = FontFamily.Monospace,
            textAlign = androidx.compose.ui.text.style.TextAlign.End,
            modifier = Modifier.padding(start = 8.dp).weight(1f, fill = false)
        )
    }
}

private fun levelColor(level: Float): Color = when {
    level < 0.5f -> Color(0xFF4CAF50)   // green
    level < 0.75f -> Color(0xFFFF9800)  // orange
    else -> Color(0xFFF44336)           // red
}

private fun tempColor(temp: Float): Color = when {
    temp <= 0f -> Color.White
    temp < 40f -> Color(0xFF4CAF50)
    temp < 50f -> Color(0xFFFF9800)
    else -> Color(0xFFF44336)
}

private fun batteryColor(percent: Int): Color = when {
    percent > 50 -> Color(0xFF4CAF50)
    percent > 20 -> Color(0xFFFF9800)
    else -> Color(0xFFF44336)
}

private fun collectStats(context: Context, videoFps: Float): DeviceStats {
    // CPU
    val cpu = readCpuUsage()

    // Memory
    val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
    val memInfo = ActivityManager.MemoryInfo()
    am.getMemoryInfo(memInfo)
    val totalMb = memInfo.totalMem / 1_048_576L
    val availMb = memInfo.availMem / 1_048_576L
    val usedMb = totalMb - availMb

    // Battery
    val batteryIntent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
    val level = batteryIntent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
    val scale = batteryIntent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
    val batteryPct = if (level >= 0 && scale > 0) (level * 100 / scale) else 0
    val plugged = batteryIntent?.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1) ?: 0
    val charging = plugged != 0

    // Temperature (from battery broadcast - most reliable cross-device)
    val rawTemp = batteryIntent?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) ?: 0
    val tempC = rawTemp / 10f

    return DeviceStats(
        cpuPercent = cpu,
        tempCelsius = tempC,
        usedMemMb = usedMb,
        totalMemMb = totalMb,
        batteryPercent = batteryPct,
        batteryCharging = charging,
        videoFps = videoFps
    )
}

/** Reads /proc/stat twice 200ms apart for a real CPU usage percentage. */
private fun readCpuUsage(): Int {
    return try {
        fun readStat(): LongArray {
            val line = BufferedReader(FileReader("/proc/stat")).use { it.readLine() } ?: return longArrayOf()
            val parts = line.trim().split("\\s+".toRegex()).drop(1)
            return parts.take(8).map { it.toLongOrNull() ?: 0L }.toLongArray()
        }

        val stat1 = readStat()
        Thread.sleep(100)
        val stat2 = readStat()
        if (stat1.isEmpty() || stat2.isEmpty()) return 0

        val idle1 = stat1.getOrElse(3) { 0L }
        val idle2 = stat2.getOrElse(3) { 0L }
        val total1 = stat1.sum()
        val total2 = stat2.sum()

        val totalDiff = total2 - total1
        val idleDiff = idle2 - idle1
        if (totalDiff <= 0) 0
        else ((totalDiff - idleDiff) * 100L / totalDiff).toInt().coerceIn(0, 100)
    } catch (_: Exception) {
        0
    }
}
