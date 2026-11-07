package com.devson.nvplayer.util

import android.net.Uri

object StreamUrlValidator {

    private val BLOCKED_HOST_PATTERNS = listOf(
        "youtube.com",
        "youtu.be",
        "instagram.com",
        "tiktok.com",
        "facebook.com",
        "fb.watch",
        "twitter.com",
        "x.com",
        "vimeo.com",
        "dailymotion.com",
        "twitch.tv"
    )

    private val SUPPORTED_STREAM_REGEX = Regex(
        pattern = """^(?:https?://[^\s?#]+?\.(?:m3u8|m3u|mpd|mp4|mkv|webm|avi|mov|flv|ts|m4v|3gp|ogv|vob)(?:[?#]\S*)?$|https?://[^\s?#]+/(?:hls|dash)(?:/\S*)?$|(?:rtsp|rtmp|mms)://\S+$)""",
        option = RegexOption.IGNORE_CASE
    )

    private val SUPPORTED_EXTENSIONS = listOf(
        ".m3u8", ".m3u", ".mpd", ".mp4", ".mkv", ".webm",
        ".avi", ".mov", ".flv", ".ts", ".m4v", ".3gp", ".ogv", ".vob"
    )

    fun isValidStreamingUrl(url: String): Boolean {
        val trimmed = url.trim()
        if (trimmed.isBlank()) return false

        val lower = trimmed.lowercase()

        // Block known non-direct video web platforms that required yt-dlp
        if (BLOCKED_HOST_PATTERNS.any { lower.contains(it) }) {
            return false
        }

        // Direct regex match for HLS/DASH/Direct video extensions and streaming protocols
        if (SUPPORTED_STREAM_REGEX.matches(trimmed)) {
            return true
        }

        // Check path component in case query parameters obscure the extension
        val uri = runCatching { Uri.parse(trimmed) }.getOrNull() ?: return false
        val path = uri.path?.lowercase() ?: ""
        return SUPPORTED_EXTENSIONS.any { path.endsWith(it) }
    }
}

