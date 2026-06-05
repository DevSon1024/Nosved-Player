package com.devson.nvplayer.player.ytdlp

import com.devson.nvplayer.repository.PlaybackSettings

enum class YtdlCodecPreference(val title: String) {
    AUTO("Auto"),
    H264("H.264 / AVC"),
    HEVC("HEVC / H.265"),
    VP9("VP9"),
    VP9_PROFILE2("VP9 Profile 2"),
    AV1("AV1"),
}

enum class YtdlContainerPreference(val title: String) {
    ANY("Any"),
    MP4("MP4"),
    WEBM("WebM"),
}

enum class YtdlHdrPreference(val title: String) {
    ANY("Any"),
    SDR("Prefer SDR"),
    HDR("Prefer HDR"),
}

enum class YtdlPlaylistMode(val title: String) {
    DEFAULT("yt-dlp default"),
    SINGLE_VIDEO("Single video only"),
    WHOLE_PLAYLIST("Whole playlist"),
}

data class YtdlpOptionSettings(
    val codecPreference: YtdlCodecPreference = YtdlCodecPreference.AUTO,
    val legacyPreferH264: Boolean = false,
    val maxHeight: Int = -1,
    val maxFps: Int = 0,
    val hdrPreference: YtdlHdrPreference = YtdlHdrPreference.ANY,
    val containerPreference: YtdlContainerPreference = YtdlContainerPreference.ANY,
    val formatSort: String = "",
    val mergeOutputFormat: String = "",
    val writeSubs: Boolean = true,
    val writeAutoSubs: Boolean = false,
    val subtitleLanguages: String = "",
    val userAgent: String = "",
    val referer: String = "",
    val cookiesFile: String = "",
    val proxy: String = "",
    val extractorArgs: String = "",
    val geoBypass: Boolean = false,
    val playlistMode: YtdlPlaylistMode = YtdlPlaylistMode.DEFAULT,
    val liveFromStart: Boolean = false,
    val sponsorBlockMark: String = "",
    val sponsorBlockRemove: String = "",
    val rawOptions: String = "",
) {
    companion object {
        fun fromSettings(settings: PlaybackSettings): YtdlpOptionSettings {
            return YtdlpOptionSettings(
                codecPreference = settings.codecPreference,
                legacyPreferH264 = settings.preferH264,
                maxHeight = settings.ytdlQuality,
                maxFps = settings.maxFps,
                hdrPreference = settings.hdrPreference,
                containerPreference = settings.containerPreference,
                formatSort = settings.formatSort,
                mergeOutputFormat = settings.mergeOutputFormat,
                writeSubs = settings.writeSubs,
                writeAutoSubs = settings.writeAutoSubs,
                subtitleLanguages = settings.subtitleLanguages,
                userAgent = settings.customUserAgent,
                referer = settings.referer,
                cookiesFile = settings.cookiesFile,
                proxy = settings.proxy,
                extractorArgs = settings.extractorArgs,
                geoBypass = settings.geoBypass,
                playlistMode = settings.playlistMode,
                liveFromStart = settings.liveFromStart,
                sponsorBlockMark = settings.sponsorBlockMark,
                sponsorBlockRemove = settings.sponsorBlockRemove,
                rawOptions = settings.customRawOptions,
            )
        }
    }
}

data class YtdlpResolvedOptions(
    val format: String,
    val rawOptions: String,
    val rawOptionItems: List<String>,
)

object YtdlpOptionsBuilder {
    const val DEFAULT_USER_AGENT =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"

    fun build(settings: YtdlpOptionSettings): YtdlpResolvedOptions {
        val format = buildFormat(settings)
        val rawOptions = linkedMapOf<String, String?>()

        fun add(key: String, value: String? = "") {
            val cleanedKey = key.trim().trimStart('-')
            if (cleanedKey.isNotEmpty()) {
                rawOptions[cleanedKey] = value?.trim()
            }
        }

        add("user-agent", settings.userAgent.ifBlank { DEFAULT_USER_AGENT })
        if (settings.writeSubs) add("write-subs")
        if (settings.writeAutoSubs) add("write-auto-subs")
        settings.subtitleLanguages.ifBlank { "all" }.let { add("sub-langs", it) }
        settings.referer.ifNotBlank { add("referer", it) }
        settings.cookiesFile.ifNotBlank { add("cookies", it) }
        settings.proxy.ifNotBlank { add("proxy", it) }
        settings.extractorArgs.ifNotBlank { add("extractor-args", it) }
        settings.formatSort.ifNotBlank { add("format-sort", it) }
        settings.mergeOutputFormat.ifNotBlank { add("merge-output-format", it) }
        if (settings.geoBypass) add("geo-bypass")
        if (settings.liveFromStart) add("live-from-start")
        when (settings.playlistMode) {
            YtdlPlaylistMode.DEFAULT -> {}
            YtdlPlaylistMode.SINGLE_VIDEO -> add("no-playlist")
            YtdlPlaylistMode.WHOLE_PLAYLIST -> add("yes-playlist")
        }
        settings.sponsorBlockMark.ifNotBlank { add("sponsorblock-mark", it) }
        settings.sponsorBlockRemove.ifNotBlank { add("sponsorblock-remove", it) }

        parseRawOptions(settings.rawOptions).forEach { parsed ->
            add(parsed.key, parsed.value)
        }

        val items = rawOptions.map { (key, value) -> "$key=${escapeRawOptionValue(value.orEmpty())}" }
        return YtdlpResolvedOptions(
            format = format,
            rawOptions = items.joinToString(","),
            rawOptionItems = items,
        )
    }

    fun buildFormat(settings: YtdlpOptionSettings): String {
        val codec = if (settings.codecPreference == YtdlCodecPreference.AUTO && settings.legacyPreferH264) {
            YtdlCodecPreference.H264
        } else {
            settings.codecPreference
        }

        val videoSelectors = codec.videoSelectors(settings.containerPreference)
            .map { selector -> selector + settings.formatFilters() }
        val videoGroup = videoSelectors.joinToString("/")
        val singleGroup = "b*" + settings.formatFilters()
        val primary = if (videoGroup.isBlank()) {
            singleGroup
        } else {
            "($videoGroup)+ba/$singleGroup"
        }
        return "$primary/bv*+ba/b"
    }

    fun parseRawOptions(raw: String): List<RawYtdlpOption> =
        splitRawOptionTokens(raw)
            .mapNotNull { token ->
                val trimmed = token.trim()
                if (trimmed.isEmpty()) return@mapNotNull null
                val normalized = trimmed.removePrefix("--")
                val eqIndex = normalized.indexOf('=')
                if (eqIndex < 0) {
                    RawYtdlpOption(normalized.trim(), "")
                } else {
                    RawYtdlpOption(
                        key = normalized.substring(0, eqIndex).trim(),
                        value = normalized.substring(eqIndex + 1).trim().trimMatchingQuotes(),
                    )
                }
            }
            .filter { it.key.isNotBlank() }

    fun escapeRawOptionValue(value: String): String {
        if (value.isBlank()) return ""
        val needsQuotes = value.any { it == ',' || it.isWhitespace() || it == '"' || it == '\'' }
        val escaped = value.replace("\\", "\\\\").replace("\"", "\\\"")
        return if (needsQuotes) "\"$escaped\"" else escaped
    }

    private fun splitRawOptionTokens(raw: String): List<String> {
        val result = mutableListOf<String>()
        val current = StringBuilder()
        var quote: Char? = null
        var escaped = false
        raw.forEach { ch ->
            when {
                escaped -> {
                    current.append(ch)
                    escaped = false
                }
                ch == '\\' -> {
                    current.append(ch)
                    escaped = true
                }
                quote != null -> {
                    current.append(ch)
                    if (ch == quote) quote = null
                }
                ch == '"' || ch == '\'' -> {
                    current.append(ch)
                    quote = ch
                }
                ch == ',' || ch == '\n' || ch == '\r' -> {
                    result += current.toString()
                    current.clear()
                }
                else -> current.append(ch)
            }
        }
        result += current.toString()
        return result
    }

    private fun YtdlCodecPreference.videoSelectors(container: YtdlContainerPreference): List<String> {
        val containerFilter = when (container) {
            YtdlContainerPreference.ANY -> ""
            YtdlContainerPreference.MP4 -> "[ext=mp4]"
            YtdlContainerPreference.WEBM -> "[ext=webm]"
        }
        return when (this) {
            YtdlCodecPreference.AUTO -> listOf("bv*$containerFilter")
            YtdlCodecPreference.H264 -> listOf(
                "bv*[vcodec^=?avc]$containerFilter",
                "bv*[vcodec^=?h264]$containerFilter",
                "bv*[ext=mp4]",
            )
            YtdlCodecPreference.HEVC -> listOf(
                "bv*[vcodec^=?hev1]$containerFilter",
                "bv*[vcodec^=?hvc1]$containerFilter",
                "bv*[vcodec^=?hevc]$containerFilter",
            )
            YtdlCodecPreference.VP9 -> listOf(
                "bv*[vcodec^=?vp9]$containerFilter",
                "bv*[vcodec^=?vp09]$containerFilter",
            )
            YtdlCodecPreference.VP9_PROFILE2 -> listOf(
                "bv*[vcodec^=?vp9.2]$containerFilter",
                "bv*[vcodec^=?vp09.02]$containerFilter",
            )
            YtdlCodecPreference.AV1 -> listOf(
                "bv*[vcodec^=?av01]$containerFilter",
                "bv*[vcodec^=?av1]$containerFilter",
            )
        }
    }

    private fun YtdlpOptionSettings.formatFilters(): String =
        buildString {
            if (maxHeight > 0) append("[height<=?$maxHeight]")
            if (maxFps > 0) append("[fps<=?$maxFps]")
            when (hdrPreference) {
                YtdlHdrPreference.ANY -> {}
                YtdlHdrPreference.SDR -> append("[dynamic_range=SDR]")
                YtdlHdrPreference.HDR -> append("[dynamic_range!=SDR]")
            }
            when (containerPreference) {
                YtdlContainerPreference.ANY -> {}
                YtdlContainerPreference.MP4 -> append("[ext=mp4]")
                YtdlContainerPreference.WEBM -> append("[ext=webm]")
            }
        }

    private inline fun String.ifNotBlank(block: (String) -> Unit) {
        if (isNotBlank()) block(trim())
    }

    private fun String.trimMatchingQuotes(): String {
        if (length < 2) return this
        val first = first()
        val last = last()
        return if ((first == '"' && last == '"') || (first == '\'' && last == '\'')) {
            substring(1, length - 1)
        } else {
            this
        }
    }
}

data class RawYtdlpOption(
    val key: String,
    val value: String?,
)
