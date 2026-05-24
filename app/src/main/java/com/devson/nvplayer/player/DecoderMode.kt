package com.devson.nvplayer.player

enum class DecoderMode(val value: String, val displayName: String) {
    AUTO("auto", "AUTO"),
    HW("mediacodec", "HW"),
    HW_PLUS("mediacodec-copy", "HW+"),
    SW("no", "SW");
    
    fun next(): DecoderMode {
        val values = entries.toTypedArray()
        return values[(ordinal + 1) % values.size]
    }
}
