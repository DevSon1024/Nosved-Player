package com.devson.nvplayer.player.model

enum class HdrType(val badgeText: String) {
    NONE(""),
    DOLBY_VISION("DOLBY VISION"),
    HDR10_PLUS("HDR10+"),
    HDR10("HDR10"),
    HLG("HLG"),
    HDR("HDR");

    val isHdr: Boolean
        get() = this != NONE
}
