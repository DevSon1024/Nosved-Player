package com.devson.nvplayer.viewmodel

import android.content.Context
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
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class FilterMode {
    ALL, PORTRAIT, LANDSCAPE
}

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
    val engine: MPVPlayerEngine,
    private val context: Context
) : ViewModel() {

    var skipPauseOnDispose = false

    companion object {
        private const val TAG = "FeedViewModel"
    }

    private val prefs = context.getSharedPreferences("feed_settings_prefs", Context.MODE_PRIVATE)

    // Video list shown in the pager
    private val _videos = MutableStateFlow<List<Video>>(emptyList())
    val videos: StateFlow<List<Video>> = _videos.asStateFlow()

    // Add filter state
    private val _filterMode = MutableStateFlow(
        FilterMode.valueOf(prefs.getString("filter_mode", FilterMode.ALL.name) ?: FilterMode.ALL.name)
    )
    val filterMode: StateFlow<FilterMode> = _filterMode.asStateFlow()

    // Add derived filteredVideos StateFlow
    val filteredVideos: StateFlow<List<Video>> = combine(
        _videos,
        _filterMode
    ) { videosList, mode ->
        when (mode) {
            FilterMode.ALL -> videosList
            FilterMode.PORTRAIT -> videosList.filter { it.height > it.width }
            FilterMode.LANDSCAPE -> videosList.filter { it.width > it.height }
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    // Mirror engine state for the UI
    val playbackState: StateFlow<PlayerState> = engine.playbackState
    val isPlaying: StateFlow<Boolean> = engine.isPlaying

    // Which page is currently playing
    private val _currentIndex = MutableStateFlow(-1)
    val currentIndex: StateFlow<Int> = _currentIndex.asStateFlow()

    // Which video URI is currently playing
    private val _currentVideoUri = MutableStateFlow<String?>(null)

    val currentPosition: StateFlow<Long> = engine.currentPosition
    val duration: StateFlow<Long> = engine.duration

    //  Surface-ready gate 
    // MPV's GPU video output (vo=gpu, gpu-context=android) must have an Android
    // Surface attached BEFORE loadfile is called, otherwise the VO initialises
    // against a null surface, falls back to audio-only, and never retries even
    // after the surface is later attached (= blank black screen on first launch).
    //
    // We therefore:
    //   1. Track whether the MPVSurfaceView surface is currently attached.
    //   2. If onPageSettled() fires before the surface is ready we store the
    //      URI as pendingVideoUri and skip the loadVideo() call.
    //   3. When onSurfaceAttached() is called we immediately load any pending
    //      URI so playback begins the moment the surface is available.
    @Volatile private var isSurfaceReady = false
    @Volatile private var pendingVideoUri: String? = null

    /** Called from FeedPage when MPVSurfaceView.surfaceCreated fires. */
    fun onSurfaceAttached() {
        isSurfaceReady = true
        val uri = pendingVideoUri ?: return
        pendingVideoUri = null
        Log.d(TAG, "Surface attached – loading pending video: $uri")
        viewModelScope.launch {
            try {
                engine.setPlaybackSpeed(1.0f)
                engine.setAspectMode(com.devson.nvplayer.player.AspectMode.FIT)
                engine.loadVideo(Uri.parse(uri))
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load pending video after surface attach", e)
            }
        }
    }

    /** Called from FeedPage when MPVSurfaceView.surfaceDestroyed fires. */
    fun onSurfaceDetached() {
        isSurfaceReady = false
        Log.d(TAG, "Surface detached")
    }

    fun setVideos(list: List<Video>) {
        _videos.value = list
        Log.d(TAG, "Feed loaded with ${list.size} videos")
    }

    fun setFilterMode(mode: FilterMode) {
        _filterMode.value = mode
        prefs.edit().putString("filter_mode", mode.name).apply()
    }

    /** Called when pagerState.settledPage changes. Loads and plays the new page. */
    fun onPageSettled(index: Int) {
        val list = filteredVideos.value
        if (list.isEmpty()) {
            _currentVideoUri.value = null
            _currentIndex.value = -1
            pause()
            return
        }
        if (index < 0 || index >= list.size) return
        val video = list[index]
        if (_currentVideoUri.value == video.uri) return

        _currentVideoUri.value = video.uri
        _currentIndex.value = index
        Log.d(TAG, "Page settled -> index=$index uri=${video.uri}")

        if (!isSurfaceReady) {
            // Surface not attached yet (cold start race). Queue the load so
            // onSurfaceAttached() can execute it as soon as the surface exists.
            Log.d(TAG, "Surface not ready – queuing video load for: ${video.uri}")
            pendingVideoUri = video.uri
            return
        }

        viewModelScope.launch {
            try {
                engine.setPlaybackSpeed(1.0f) // Reset speed to normal on page change
                engine.setAspectMode(com.devson.nvplayer.player.AspectMode.FIT)
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
        // Do NOT release the engine here - it is owned by PlayerViewModel/MainActivity.
        Log.d(TAG, "FeedViewModel cleared (engine not released; owned externally)")
    }

    /** Factory that injects the shared engine instead of creating a new one. */
    class Factory(
        private val engine: MPVPlayerEngine,
        private val context: Context
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(FeedViewModel::class.java)) {
                return FeedViewModel(engine, context) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
        }
    }
}

