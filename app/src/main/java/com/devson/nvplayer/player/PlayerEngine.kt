package com.devson.nvplayer.player

import android.net.Uri
import kotlinx.coroutines.flow.StateFlow

interface PlayerEngine {
    val isPlaying: StateFlow<Boolean>
    val currentPosition: StateFlow<Long> // in milliseconds
    val duration: StateFlow<Long> // in milliseconds
    val playbackState: StateFlow<PlayerState>
    val videoWidth: StateFlow<Long>
    val videoHeight: StateFlow<Long>
    val videoRotation: StateFlow<Long>
    val currentSubtitleText: StateFlow<String>
    val subtitleTracks: StateFlow<List<TrackInfo>>
    val audioTracks: StateFlow<List<TrackInfo>>
    val chapters: StateFlow<List<ChapterInfo>>
    val hwdecCurrent: StateFlow<String>
    val networkSpeedBytesPerSec: StateFlow<Long>
    val bufferDurationSeconds: StateFlow<Double>
    val bufferedPosition: StateFlow<Long>
    val mediaTitle: StateFlow<String>

    fun loadVideo(uri: Uri)
    fun play()
    fun pause()
    fun togglePlayback()
    fun seekTo(position: Long, precise: Boolean = true) // position in milliseconds
    fun setPlaybackSpeed(speed: Float)
    fun cycleSubtitle()
    fun cycleAudio()
    fun selectSubtitleTrack(id: Int)
    fun selectAudioTrack(id: Int)
    fun selectChapter(index: Int)
    fun setSubtitleDelay(delayMs: Long)
    fun setSubtitleStyle(scale: Float, font: String, bold: Boolean)
    fun seekNextSubtitle()
    fun seekPrevSubtitle()
    fun setAudioBoost(boost: Boolean)
    fun setMpvVolume(volume: Double)
    fun setDecoder(mode: DecoderMode)
    fun setAspectMode(mode: AspectMode)
    fun setVideoTrackEnabled(enabled: Boolean)
    fun release()
}
