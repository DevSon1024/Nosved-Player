package com.devson.nvplayer.player.model

enum class AudioDecoderMode(
    val value: String,
    val displayName: String
) {
    AUTOMATIC("all", "Automatic"),
    SOFTWARE("lavc:*", "Software (Lavc)");

    val id: String get() = if (this == SOFTWARE) "sw" else "auto"

    fun isSoftware(): Boolean = this == SOFTWARE

    companion object {
        fun fromValue(value: String): AudioDecoderMode {
            return entries.firstOrNull {
                it.value.equals(value, ignoreCase = true) || it.name.equals(value, ignoreCase = true)
            } ?: AUTOMATIC
        }

        fun fromId(id: String?): AudioDecoderMode {
            return if (id.equals("sw", ignoreCase = true)) SOFTWARE else AUTOMATIC
        }
    }
}
