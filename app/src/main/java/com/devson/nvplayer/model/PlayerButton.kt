package com.devson.nvplayer.model

import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.FormatListBulleted
import androidx.compose.material.icons.rounded.*

enum class PlayerButton(val displayName: String, val icon: ImageVector) {
    BACK_ARROW("Back", Icons.AutoMirrored.Rounded.ArrowBack),
    VIDEO_TITLE("Title", Icons.Rounded.Title),
    SUBTITLES("Subtitles", Icons.Rounded.Subtitles),
    AUDIO_TRACK("Audio", Icons.Rounded.Audiotrack),
    DECODER("Decoder", Icons.Rounded.Memory),
    CHAPTERS("Chapters", Icons.AutoMirrored.Rounded.FormatListBulleted),
    SMART_ENHANCE("Enhance", Icons.Rounded.AutoAwesome),
    SCREEN_ROTATION("Rotate", Icons.Rounded.ScreenRotation),
    LOCK_CONTROLS("Lock", Icons.Rounded.LockOpen),
    PICTURE_IN_PICTURE("PiP", Icons.Rounded.PictureInPicture),
    ASPECT_RATIO("Aspect", Icons.Rounded.FitScreen),
    MORE_OPTIONS("More", Icons.Rounded.MoreHoriz),
    BACKGROUND_PLAY("Background Play", Icons.Rounded.Headphones),
    STREAM_QUALITY("Quality", Icons.Rounded.Settings),
    NONE("None", Icons.Rounded.Close)
}

val allPlayerButtons: List<PlayerButton> = PlayerButton.entries.filter {
    it != PlayerButton.BACK_ARROW && it != PlayerButton.VIDEO_TITLE && it != PlayerButton.NONE
}
