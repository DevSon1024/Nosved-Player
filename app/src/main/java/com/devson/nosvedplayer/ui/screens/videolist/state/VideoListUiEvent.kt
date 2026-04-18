package com.devson.nosvedplayer.ui.screens.videolist.state

import android.net.Uri
import com.devson.nosvedplayer.model.Video

/**
 * Represents one-off UI events (like showing a Toast, navigating to a player)
 * that should be handled by the UI and then consumed.
 */
sealed interface VideoListUiEvent {
    data class ShowToast(val message: String) : VideoListUiEvent
    data class NavigateToPlayer(val video: Video, val playlist: List<Video>, val startPosition: Long) : VideoListUiEvent
    data class ShareVideos(val uris: List<Uri>) : VideoListUiEvent
    object NavigateUp : VideoListUiEvent
}
