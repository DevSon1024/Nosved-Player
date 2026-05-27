package com.devson.nvplayer.model

import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.FormatListBulleted
import androidx.compose.material.icons.rounded.*

enum class PlayerButton(val displayName: String, val icon: ImageVector) {
    BACK_ARROW("Back Arrow", Icons.AutoMirrored.Rounded.ArrowBack),
    VIDEO_TITLE("Video Title", Icons.Rounded.Title),
    SUBTITLES("Subtitles", Icons.Rounded.Subtitles),
    AUDIO_TRACK("Audio Track", Icons.Rounded.Audiotrack),
    DECODER("Decoder", Icons.Rounded.Code),
    CHAPTERS("Chapters", Icons.AutoMirrored.Rounded.FormatListBulleted),
    SMART_ENHANCE("Smart Enhance", Icons.Rounded.AutoAwesome),
    SCREEN_ROTATION("Screen Rotation", Icons.Rounded.ScreenRotation),
    LOCK_CONTROLS("Lock Controls", Icons.Rounded.Lock),
    PICTURE_IN_PICTURE("Picture in Picture", Icons.Rounded.PictureInPicture),
    ASPECT_RATIO("Aspect Ratio", Icons.Rounded.AspectRatio),
    MORE_OPTIONS("More Options", Icons.Rounded.MoreVert),
    NONE("None", Icons.Rounded.Close)
}

val allPlayerButtons: List<PlayerButton> = PlayerButton.entries.filter {
    it != PlayerButton.BACK_ARROW && it != PlayerButton.VIDEO_TITLE && it != PlayerButton.NONE
}
