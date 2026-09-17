package com.devson.nvplayer.player.engine

import android.net.Uri
import com.devson.nvplayer.player.model.AspectMode
import com.devson.nvplayer.player.model.ChapterInfo
import com.devson.nvplayer.player.model.DecoderMode
import com.devson.nvplayer.player.model.TrackInfo
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
    fun applySubtitleSettings(settings: com.devson.nvplayer.data.repository.PlaybackSettings)
    fun seekNextSubtitle()
    fun seekPrevSubtitle()
    fun addExternalSubtitle(path: String, select: Boolean = true)
    fun setAudioBoost(boost: Boolean)
    fun setMpvVolume(volume: Double)
    fun setDecoder(mode: DecoderMode)
    fun setAspectMode(mode: AspectMode)
    fun setVideoTrackEnabled(enabled: Boolean)
    fun stepFrameForward()
    fun stepFrameBackward()
    fun setMute(isMuted: Boolean)
    fun isMuted(): Boolean
    fun getEstimatedFrameCount(): Long
    fun getEstimatedFrameNumber(): Long
    fun seekToFrame(targetFrame: Long)
    fun setAmbientMode(
        isEnabled: Boolean,
        style: com.devson.nvplayer.data.repository.AmbientBlurStyle = com.devson.nvplayer.data.repository.AmbientBlurStyle.GLOW
    )
    fun getAdaptiveTracks(): List<com.devson.nvplayer.data.model.VideoQualityOption> = emptyList()
    fun changeStreamQuality(
        quality: com.devson.nvplayer.data.model.VideoQualityOption,
        streamUrl: String,
        streamType: com.devson.nvplayer.data.model.StreamType
    ) {}
    fun release()
}