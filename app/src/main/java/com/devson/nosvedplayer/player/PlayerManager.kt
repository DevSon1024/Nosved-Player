package com.devson.nosvedplayer.player

import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.exoplayer.ExoPlayer
import com.devson.nosvedplayer.model.Video
import androidx.media3.common.PlaybackException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Manages the ExoPlayer instance and its state.
*/
class PlayerManager(private val context: Context) {

    var exoPlayer: ExoPlayer? = null
        private set

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _currentPosition = MutableStateFlow(0L)
    val currentPosition: StateFlow<Long> = _currentPosition.asStateFlow()

    private val _duration = MutableStateFlow(0L)
    val duration: StateFlow<Long> = _duration.asStateFlow()

    private val _bufferedPosition = MutableStateFlow(0L)
    val bufferedPosition: StateFlow<Long> = _bufferedPosition.asStateFlow()

    // null = not yet determined, true = portrait, false = landscape
    private val _isPortraitVideo = MutableStateFlow<Boolean?>(null)
    val isPortraitVideo: StateFlow<Boolean?> = _isPortraitVideo.asStateFlow()

    private val _videoFps = MutableStateFlow(0f)
    val videoFps: StateFlow<Float> = _videoFps.asStateFlow()

    private val _playerError = MutableStateFlow<String?>(null)
    val playerError: StateFlow<String?> = _playerError.asStateFlow()

    fun initializePlayer() {
        if (exoPlayer == null) {
            val renderersFactory = androidx.media3.exoplayer.DefaultRenderersFactory(context)
                .setExtensionRendererMode(androidx.media3.exoplayer.DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER)
                .setEnableDecoderFallback(true)
                .forceDisableMediaCodecAsynchronousQueueing()

            exoPlayer = ExoPlayer.Builder(context)
                .setRenderersFactory(renderersFactory)
                .build().apply {
                addListener(object : Player.Listener {
                    override fun onIsPlayingChanged(isPlaying: Boolean) {
                        _isPlaying.value = isPlaying
                    }

                    override fun onPlaybackStateChanged(playbackState: Int) {
                        super.onPlaybackStateChanged(playbackState)
                        if (playbackState == Player.STATE_READY) {
                            _duration.value = duration.coerceAtLeast(0L)
                        }
                    }

                    override fun onVideoSizeChanged(videoSize: VideoSize) {
                        if (videoSize.width > 0 && videoSize.height > 0) {
                            _isPortraitVideo.value = videoSize.height > videoSize.width
                        }
                    }

                    override fun onTracksChanged(tracks: androidx.media3.common.Tracks) {
                        super.onTracksChanged(tracks)
                        for (group in tracks.groups) {
                            if (group.type == androidx.media3.common.C.TRACK_TYPE_VIDEO && group.isSelected) {
                                val format = group.getTrackFormat(0) // Usually 1 track per selected video group
                                if (format.frameRate > 0f) {
                                    _videoFps.value = format.frameRate
                                }
                            }
                        }
                    }

                    override fun onPlayerError(error: PlaybackException) {
                        super.onPlayerError(error)
                        _playerError.value = error.message ?: "Unknown playback error"
                    }
                })
            }
        }
    }

    fun playVideo(video: Video) {
        val player = exoPlayer ?: return
        _playerError.value = null // Clear previous errors
        val mediaItem = MediaItem.fromUri(video.uri)
        player.setMediaItem(mediaItem)
        player.prepare()
        player.playWhenReady = true
    }

    fun clearError() {
        _playerError.value = null
    }

    fun playPause() {
        val player = exoPlayer ?: return
        if (player.isPlaying) {
            player.pause()
        } else {
            player.play()
        }
    }

    fun pause() {
        exoPlayer?.pause()
    }

    fun resume() {
        exoPlayer?.play()
    }

    fun seekTo(positionMs: Long) {
        exoPlayer?.seekTo(positionMs)
    }

    fun seekForward(ms: Long = 10000L) {
        val player = exoPlayer ?: return
        val newPos = (player.currentPosition + ms).coerceAtMost(player.duration)
        player.seekTo(newPos)
    }

    fun seekBackward(ms: Long = 10000L) {
        val player = exoPlayer ?: return
        val newPos = (player.currentPosition - ms).coerceAtLeast(0)
        player.seekTo(newPos)
    }

    fun updateProgress() {
        val player = exoPlayer ?: return
        _currentPosition.value = player.currentPosition.coerceAtLeast(0L)
        _bufferedPosition.value = player.bufferedPosition.coerceAtLeast(0L)
        _duration.value = player.duration.coerceAtLeast(0L)
    }

    fun releasePlayer() {
        exoPlayer?.release()
        exoPlayer = null
    }
}
