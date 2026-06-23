package com.devson.nvplayer.data.repository

import android.content.Context
import com.devson.nvplayer.player.model.DecoderMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

data class VisualMpvConfig(
    val hwdec: DecoderMode = DecoderMode.AUTO,
    val isHighQualityProfile: Boolean = false,
    val isInterpolationEnabled: Boolean = false,
    val isDebandingEnabled: Boolean = false,
    val showPerformanceOverlay: Boolean = false,
    val audioOutput: String = "aaudio",
    val videoOutput: String = "gpu",
    val deinterlace: Boolean = false
)

object MpvConfigRepository {
    private const val FILE_NAME = "mpv.conf"

    private val DEFAULT_CONFIG = """
        # Nosved Player Custom MPV Configuration (mpv.conf)
        # You can add/modify any standard mpv options here.
        # Lines starting with # are comments.

        # --- Video Output Settings ---
        # vo: specifies the video output driver. e.g. gpu or gpu-next
        vo=gpu
        # gpu-context: specifies the graphics context. e.g. android
        gpu-context=android

        # --- Audio Output Settings ---
        # ao: specifies the audio output driver. e.g. aaudio, audiotrack, opensles
        ao=aaudio

        # --- Hardware Decoding ---
        # hwdec: specifies hardware decoder. e.g. mediacodec, mediacodec-copy, no
        hwdec=mediacodec

        # --- Other Settings ---
        # profile: e.g. fast
        profile=fast
        # keep-open: yes/no
        keep-open=yes
    """.trimIndent()

    suspend fun loadMpvConfig(context: Context): String = withContext(Dispatchers.IO) {
        val file = File(context.filesDir, FILE_NAME)
        if (file.exists()) {
            try {
                file.readText()
            } catch (e: Exception) {
                DEFAULT_CONFIG
            }
        } else {
            // Write default config so the file exists next time
            try {
                file.writeText(DEFAULT_CONFIG)
            } catch (e: Exception) {
                // Ignore writing error and return default
            }
            DEFAULT_CONFIG
        }
    }

    suspend fun saveMpvConfig(context: Context, content: String): Boolean =
        withContext(Dispatchers.IO) {
            val file = File(context.filesDir, FILE_NAME)
            try {
                file.writeText(content)
                true
            } catch (e: Exception) {
                false
            }
        }

    fun parseConfig(content: String): VisualMpvConfig {
        var hwdecVal = "auto"
        var profileVal = "fast"
        var interpolationVal = false
        var debandVal = false
        var statsRingVal = false
        var aoVal = "aaudio"
        var voVal = "gpu"
        var deinterlaceVal = false

        content.lines().forEach { line ->
            val clean = line.trim()
            if (clean.startsWith("#") || !clean.contains("=")) return@forEach
            val parts = clean.split("=", limit = 2)
            if (parts.size != 2) return@forEach
            val key = parts[0].trim()
            val value = parts[1].trim()

            when (key) {
                "hwdec" -> hwdecVal = value
                "profile" -> profileVal = value
                "interpolation" -> interpolationVal = (value == "yes")
                "deband" -> debandVal = (value == "yes")
                "stats-ring" -> statsRingVal = (value == "yes")
                "ao" -> aoVal = value
                "vo" -> voVal = value
                "deinterlace" -> deinterlaceVal = (value == "yes")
            }
        }

        val mode = when (hwdecVal) {
            "mediacodec" -> DecoderMode.HW
            "mediacodec-copy" -> DecoderMode.HW_PLUS
            "no" -> DecoderMode.SW
            else -> DecoderMode.AUTO
        }

        return VisualMpvConfig(
            hwdec = mode,
            isHighQualityProfile = (profileVal == "high-quality"),
            isInterpolationEnabled = interpolationVal,
            isDebandingEnabled = debandVal,
            showPerformanceOverlay = statsRingVal,
            audioOutput = aoVal,
            videoOutput = voVal,
            deinterlace = deinterlaceVal
        )
    }

    fun serializeConfig(config: VisualMpvConfig, originalContent: String): String {
        val keysToManage = mapOf(
            "hwdec" to config.hwdec.value,
            "profile" to if (config.isHighQualityProfile) "high-quality" else "fast",
            "interpolation" to if (config.isInterpolationEnabled) "yes" else "no",
            "tscale" to "oversample",
            "deband" to if (config.isDebandingEnabled) "yes" else "no",
            "stats-ring" to if (config.showPerformanceOverlay) "yes" else "no",
            "ao" to config.audioOutput,
            "vo" to config.videoOutput,
            "deinterlace" to if (config.deinterlace) "yes" else "no"
        )

        val writtenKeys = mutableSetOf<String>()
        val newLines = mutableListOf<String>()

        originalContent.lines().forEach { line ->
            val clean = line.trim()
            if (clean.startsWith("#") || clean.isEmpty()) {
                newLines.add(line)
                return@forEach
            }
            val parts = clean.split("=", limit = 2)
            if (parts.size != 2) {
                newLines.add(line)
                return@forEach
            }
            val key = parts[0].trim()
            if (key in keysToManage) {
                if (key == "tscale" && !config.isInterpolationEnabled) {
                    return@forEach
                }
                val indent = line.substring(0, line.indexOf(parts[0]))
                newLines.add("${indent}${key}=${keysToManage[key]}")
                writtenKeys.add(key)
                return@forEach
            }
            newLines.add(line)
        }

        val keysToAppend = keysToManage.keys - writtenKeys
        if (keysToAppend.isNotEmpty()) {
            var sectionAdded = false
            keysToAppend.forEach { key ->
                if (key == "tscale" && !config.isInterpolationEnabled) {
                    return@forEach
                }
                if (!sectionAdded) {
                    newLines.add("")
                    newLines.add("# --- Custom Config Editor Additions ---")
                    sectionAdded = true
                }
                newLines.add("${key}=${keysToManage[key]}")
            }
        }

        return newLines.joinToString("\n")
    }
}