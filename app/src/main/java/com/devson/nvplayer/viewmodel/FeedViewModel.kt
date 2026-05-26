package com.devson.nvplayer.viewmodel

import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.devson.nvplayer.model.Video
import com.devson.nvplayer.player.MPVPlayerEngine
import com.devson.nvplayer.player.PlayerState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * ViewModel for the Reels/Shorts-style vertical video feed.
 *
 * KEY CONSTRAINT: MPVLib is a native process-wide singleton. Only ONE
 * MPVPlayerEngine may exist at a time. FeedViewModel therefore receives
 * the already-initialised engine that MainActivity/PlayerViewModel own,
 * rather than creating its own. It must never call engine.release().
 */
class FeedViewModel(
    // Shared engine owned by PlayerViewModel / MainActivity. Do NOT release it here.
    val engine: MPVPlayerEngine
) : ViewModel() {

    var skipPauseOnDispose = false

    companion object {
        private const val TAG = "FeedViewModel"
    }

    // Video list shown in the pager
    private val _videos = MutableStateFlow<List<Video>>(emptyList())
    val videos: StateFlow<List<Video>> = _videos.asStateFlow()

    // Mirror engine state for the UI
    val playbackState: StateFlow<PlayerState> = engine.playbackState
    val isPlaying: StateFlow<Boolean> = engine.isPlaying

    // Which page is currently playing
    private val _currentIndex = MutableStateFlow(-1)
    val currentIndex: StateFlow<Int> = _currentIndex.asStateFlow()

    val currentPosition: StateFlow<Long> = engine.currentPosition
    val duration: StateFlow<Long> = engine.duration

    fun setVideos(list: List<Video>) {
        _videos.value = list
        Log.d(TAG, "Feed loaded with ${list.size} videos")
    }

    /** Called when pagerState.settledPage changes. Loads and plays the new page. */
    fun onPageSettled(index: Int) {
        val list = _videos.value
        if (index < 0 || index >= list.size) return
        if (_currentIndex.value == index) return

        _currentIndex.value = index
        val video = list[index]
        Log.d(TAG, "Page settled -> index=$index uri=${video.uri}")

        viewModelScope.launch {
            try {
                engine.setPlaybackSpeed(1.0f) // Reset speed to normal on page change
                engine.loadVideo(Uri.parse(video.uri))
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load video at index $index", e)
            }
        }
    }

    fun seekTo(position: Long) = try { engine.seekTo(position, precise = true) } catch (e: Exception) { Log.w(TAG, "seekTo() failed", e) }
    fun setPlaybackSpeed(speed: Float) = try { engine.setPlaybackSpeed(speed) } catch (e: Exception) { Log.w(TAG, "setPlaybackSpeed() failed", e) }
    fun pause() = try { engine.pause() } catch (e: Exception) { Log.w(TAG, "pause() failed", e) }
    fun play()  = try { engine.play()  } catch (e: Exception) { Log.w(TAG, "play() failed",  e) }
    fun togglePlayback() = try { engine.togglePlayback() } catch (e: Exception) { Log.w(TAG, "toggle failed", e) }

    override fun onCleared() {
        super.onCleared()
        // Do NOT release the engine here — it is owned by PlayerViewModel/MainActivity.
        Log.d(TAG, "FeedViewModel cleared (engine not released; owned externally)")
    }

    /** Factory that injects the shared engine instead of creating a new one. */
    class Factory(private val engine: MPVPlayerEngine) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(FeedViewModel::class.java)) {
                return FeedViewModel(engine) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
        }
    }
}
