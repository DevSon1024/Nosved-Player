package com.devson.nvplayer.player.model

enum class AspectMode(val displayName: String) {
    FIT("Fit Screen"),
    STRETCH("Stretch"),
    CROP("Crop"),
    ORIGINAL("100% Original");

    fun next(): AspectMode {
        val values = entries
        return values[(ordinal + 1) % values.size]
    }
}