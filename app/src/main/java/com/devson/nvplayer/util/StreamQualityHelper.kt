package com.devson.nvplayer.util

import android.content.Context
import android.net.Uri
import android.util.Log
import com.devson.nvplayer.data.model.StreamType
import com.devson.nvplayer.data.model.VideoQualityOption
import com.devson.nvplayer.player.ytdlp.YtdlpManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject
import java.io.File

object StreamQualityHelper {
    private const val TAG = "StreamQualityHelper"
    private const val PROBE_TIMEOUT_MS = 4000L

    private val ADAPTIVE_EXTENSIONS = setOf("m3u8", "m3u", "mpd")
    private val DIRECT_CONTAINER_EXTENSIONS = setOf(
        "mp4", "mkv", "webm", "avi", "flv", "ts", "mov",
        "m4v", "3gp", "wmv", "ogv", "vob"
    )

    fun classifyUrl(url: String): StreamType {
        if (url.isBlank()) return StreamType.UNKNOWN

        val uri = try {
            Uri.parse(url)
        } catch (_: Exception) {
            null
        }

        val scheme = uri?.scheme?.lowercase() ?: ""
        val path = uri?.path?.lowercase() ?: url.substringBefore('?').substringBefore('#').lowercase()
        val extension = path.substringAfterLast('.', "")

        if (ADAPTIVE_EXTENSIONS.contains(extension) ||
            url.contains(".m3u8", ignoreCase = true) ||
            url.contains(".mpd", ignoreCase = true)
        ) {
            return StreamType.ADAPTIVE_MANIFEST
        }

        if (DIRECT_CONTAINER_EXTENSIONS.contains(extension)) {
            return StreamType.DIRECT_FILE
        }

        if (scheme == "http" || scheme == "https") {
            return StreamType.PLATFORM_URL
        }

        return if (scheme == "content" || scheme == "file" || url.startsWith("/")) {
            StreamType.DIRECT_FILE
        } else {
            StreamType.UNKNOWN
        }
    }

    fun formatResolutionLabel(height: Int, width: Int = 0, fps: Int = 0): String {
        val baseLabel = when {
            height >= 2160 -> "2160p (4K)"
            height >= 1440 -> "1440p (2K)"
            height >= 1080 -> "1080p (FHD)"
            height >= 720 -> "720p (HD)"
            height >= 480 -> "480p"
            height >= 360 -> "360p"
            height >= 240 -> "240p"
            height >= 144 -> "144p"
            else -> "${height}p"
        }
        return if (fps > 30) "$baseLabel ${fps}fps" else baseLabel
    }

    suspend fun probePlatformQualities(context: Context, url: String): List<VideoQualityOption> = withContext(Dispatchers.IO) {
        val fallback = listOf(
            VideoQualityOption(
                id = "auto",
                label = "Auto (Recommended)",
                isAuto = true,
                isSelected = true
            )
        )

        val ytdlDir = YtdlpManager.getYtdlDir(context)
        val ytDlpFile = File(ytdlDir, "yt-dlp")
        val pythonBinary = YtdlpManager.getExecutablePath(context)

        if (!ytDlpFile.exists() || !File(pythonBinary).exists()) {
            Log.d(TAG, "yt-dlp binary or script not found; falling back to Auto")
            return@withContext fallback
        }

        val nativeLibDir = context.applicationInfo.nativeLibraryDir
        val command = listOf(
            pythonBinary,
            ytDlpFile.absolutePath,
            "-J",
            "--no-playlist",
            "--no-warnings",
            "--skip-download",
            url
        )

        var process: Process? = null
        val probedOptions = try {
            withTimeoutOrNull(PROBE_TIMEOUT_MS) {
                val builder = ProcessBuilder(command)
                    .directory(ytdlDir)
                    .redirectErrorStream(false)

                val env = builder.environment()
                env.remove("YTDL_SCRIPT")
                env["YTDL_PYTHON"] = File(nativeLibDir, "libpython.so").absolutePath
                env["PYTHONHOME"] = ytdlDir.absolutePath
                env["PYTHONPATH"] = "${ytdlDir.absolutePath}/python313.zip:${ytdlDir.absolutePath}:$nativeLibDir"
                env["SSL_CERT_FILE"] = File(context.filesDir, "cacert.pem").absolutePath
                env["LD_LIBRARY_PATH"] = nativeLibDir

                val p = builder.start()
                process = p

                val jsonText = p.inputStream.bufferedReader().use { it.readText() }
                p.waitFor()

                parseFormatsFromJson(jsonText)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to probe yt-dlp formats: ${e.message}")
            null
        } finally {
            try {
                process?.destroyForcibly()
            } catch (_: Exception) {}
        }

        if (probedOptions.isNullOrEmpty()) {
            fallback
        } else {
            probedOptions
        }
    }

    private fun parseFormatsFromJson(jsonText: String): List<VideoQualityOption> {
        if (jsonText.isBlank()) return emptyList()

        return try {
            val json = JSONObject(jsonText)
            val formats = json.optJSONArray("formats") ?: return emptyList()

            val bestByHeight = mutableMapOf<Int, VideoQualityOption>()

            for (i in 0 until formats.length()) {
                val fmt = formats.optJSONObject(i) ?: continue
                val vcodec = fmt.optString("vcodec", "")
                if (vcodec == "none" || vcodec.isBlank()) continue

                val h = fmt.optInt("height", 0)
                if (h <= 0) continue

                val w = fmt.optInt("width", 0)
                val fps = fmt.optDouble("fps", 0.0).toInt()
                val tbr = (fmt.optDouble("tbr", 0.0) * 1000.0).toLong()
                val formatId = fmt.optString("format_id", "${h}p")

                val option = VideoQualityOption(
                    id = formatId,
                    label = formatResolutionLabel(h, w, fps),
                    height = h,
                    width = w,
                    bitrate = tbr,
                    fps = fps,
                    isSelected = false
                )

                val existing = bestByHeight[h]
                if (existing == null || option.bitrate > existing.bitrate) {
                    bestByHeight[h] = option
                }
            }

            val sorted = bestByHeight.values
                .sortedByDescending { it.height }
                .toList()

            if (sorted.isEmpty()) {
                emptyList()
            } else {
                listOf(
                    VideoQualityOption(
                        id = "auto",
                        label = "Auto (Recommended)",
                        isAuto = true,
                        isSelected = true
                    )
                ) + sorted
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error parsing yt-dlp formats JSON: ${e.message}")
            emptyList()
        }
    }
}
