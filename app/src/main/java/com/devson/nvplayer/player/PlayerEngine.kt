package com.devson.nvplayer.player

import android.net.Uri
import kotlinx.coroutines.flow.StateFlow

interface PlayerEngine {
    val isPlaying: StateFlow<Boolean>
    val currentPosition: StateFlow<Long> // in milliseconds
    val duration: StateFlow<Long> // in milliseconds
    val playbackState: StateFlow<PlayerState>

    fun loadVideo(uri: Uri)
    fun play()
    fun pause()
    fun togglePlayback()
    fun seekTo(position: Long) // position in milliseconds
    fun setPlaybackSpeed(speed: Float)
    fun cycleSubtitle()
    fun cycleAudio()
    fun release()
}
