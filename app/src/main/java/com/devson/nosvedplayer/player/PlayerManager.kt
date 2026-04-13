package com.devson.nosvedplayer.player

import android.annotation.SuppressLint
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
import com.devson.nosvedplayer.model.TrackInfo
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.MediaItem.SubtitleConfiguration
import android.net.Uri
import androidx.media3.common.util.UnstableApi
import androidx.annotation.OptIn
import io.github.anilbeesetti.nextlib.media3ext.ffdecoder.NextRenderersFactory
import androidx.media3.exoplayer.upstream.Loader
import android.media.audiofx.LoudnessEnhancer
import com.devson.nosvedplayer.util.AppLogger
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.ForwardingPlayer
import androidx.media3.session.MediaSession

@OptIn(UnstableApi::class)
class PlayerManager(private val context: Context) {

    var exoPlayer: ExoPlayer? = null
        private set

    var mediaSession: MediaSession? = null
        private set

    var onVideoEnded: (() -> Unit)? = null

    var onPlaybackError: ((String) -> Unit)? = null

    var onPlayNext: (() -> Unit)? = null

    var onPlayPrevious: (() -> Unit)? = null

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _currentPosition = MutableStateFlow(0L)
    val currentPosition: StateFlow<Long> = _currentPosition.asStateFlow()

    private val _duration = MutableStateFlow(0L)
    val duration: StateFlow<Long> = _duration.asStateFlow()

    private val _bufferedPosition = MutableStateFlow(0L)
    val bufferedPosition: StateFlow<Long> = _bufferedPosition.asStateFlow()

    private val _isPortraitVideo = MutableStateFlow<Boolean?>(null)
    val isPortraitVideo: StateFlow<Boolean?> = _isPortraitVideo.asStateFlow()

    private val _videoFps = MutableStateFlow(0f)
    val videoFps: StateFlow<Float> = _videoFps.asStateFlow()

    private val _videoDecoderName = MutableStateFlow<String?>(null)
    val videoDecoderName: StateFlow<String?> = _videoDecoderName.asStateFlow()

    private val _playerError = MutableStateFlow<String?>(null)
    val playerError: StateFlow<String?> = _playerError.asStateFlow()

    // --- Audio Tracks ---
    private val _audioTracks = MutableStateFlow<List<TrackInfo>>(emptyList())
    val audioTracks: StateFlow<List<TrackInfo>> = _audioTracks.asStateFlow()

    private val _selectedAudioIndex = MutableStateFlow(-1)
    val selectedAudioIndex: StateFlow<Int> = _selectedAudioIndex.asStateFlow()

    // --- Subtitle Tracks ---
    private val _subtitleTracks = MutableStateFlow<List<TrackInfo>>(emptyList())
    val subtitleTracks: StateFlow<List<TrackInfo>> = _subtitleTracks.asStateFlow()

    private val _selectedSubtitleIndex = MutableStateFlow(-1)
    val selectedSubtitleIndex: StateFlow<Int> = _selectedSubtitleIndex.asStateFlow()

    private val _isAudioBoostEnabled = MutableStateFlow(false)
    val isAudioBoostEnabled: StateFlow<Boolean> = _isAudioBoostEnabled.asStateFlow()

    private var loudnessEnhancer: LoudnessEnhancer? = null

    fun initializePlayer() {
        if (exoPlayer == null) {
            val renderersFactory = NextRenderersFactory(context)
                .setExtensionRendererMode(androidx.media3.exoplayer.DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER)
                .setEnableDecoderFallback(true)
                .forceDisableMediaCodecAsynchronousQueueing()

            val loadControl = DefaultLoadControl.Builder()
                .setBufferDurationsMs(
                    DefaultLoadControl.DEFAULT_MIN_BUFFER_MS,
                    DefaultLoadControl.DEFAULT_MAX_BUFFER_MS,
                    500,
                    1000
                )
                .build()

            val audioAttributes = AudioAttributes.Builder()
                .setUsage(C.USAGE_MEDIA)
                .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                .build()

            exoPlayer = ExoPlayer.Builder(context)
                .setRenderersFactory(renderersFactory)
                .setLoadControl(loadControl)
                .setAudioAttributes(audioAttributes, true)
                .setHandleAudioBecomingNoisy(true)
                .build().apply {
                    setSeekParameters(androidx.media3.exoplayer.SeekParameters.CLOSEST_SYNC)
                    addListener(object : Player.Listener {
                        override fun onIsPlayingChanged(isPlaying: Boolean) {
                            _isPlaying.value = isPlaying
                        }

                        override fun onPlaybackStateChanged(playbackState: Int) {
                            super.onPlaybackStateChanged(playbackState)
                            if (playbackState == Player.STATE_READY) {
                                _duration.value = duration.coerceAtLeast(0L)
                            } else if (playbackState == Player.STATE_ENDED) {
                                onVideoEnded?.invoke()
                            }
                        }

                        override fun onVideoSizeChanged(videoSize: VideoSize) {
                            if (videoSize.width > 0 && videoSize.height > 0) {
                                _isPortraitVideo.value = videoSize.height > videoSize.width
                            }
                        }

                        override fun onTracksChanged(tracks: androidx.media3.common.Tracks) {
                            super.onTracksChanged(tracks)
                            // Keep all your existing audio/subtitle track parsing logic here exactly as it is...

                            val newAudioTracks = mutableListOf<TrackInfo>()
                            val newSubtitleTracks = mutableListOf<TrackInfo>()
                            var currentAudioIdx = -1
                            var currentSubIdx = -1

                            val audioGroupCount = tracks.groups.count { it.type == androidx.media3.common.C.TRACK_TYPE_AUDIO }
                            val subtitleGroupCount = tracks.groups.count { it.type == androidx.media3.common.C.TRACK_TYPE_TEXT }

                            for (group in tracks.groups) {
                                if (group.type == androidx.media3.common.C.TRACK_TYPE_VIDEO && group.isSelected) {
                                    val format = group.getTrackFormat(0)
                                    if (format.frameRate > 0f) {
                                        _videoFps.value = format.frameRate
                                    }
                                } else if (group.type == androidx.media3.common.C.TRACK_TYPE_AUDIO) {
                                    val format = group.getTrackFormat(0)
                                    val trackIndex = newAudioTracks.size
                                    val langName = format.language?.let { java.util.Locale.forLanguageTag(it).displayLanguage }
                                    val label = if (audioGroupCount == 1) {
                                        langName ?: "Default"
                                    } else {
                                        "Track ${trackIndex + 1}" + (if (langName != null) " - $langName" else "")
                                    }
                                    newAudioTracks.add(TrackInfo(trackIndex, label, format.language))
                                    if (group.isSelected) currentAudioIdx = trackIndex
                                } else if (group.type == androidx.media3.common.C.TRACK_TYPE_TEXT) {
                                    val format = group.getTrackFormat(0)
                                    val trackIndex = newSubtitleTracks.size
                                    val langName = format.language?.let { java.util.Locale.forLanguageTag(it).displayLanguage }
                                    val label = if (subtitleGroupCount == 1) {
                                        langName ?: "Default"
                                    } else {
                                        "Track ${trackIndex + 1}" + (if (langName != null) " - $langName" else "")
                                    }
                                    newSubtitleTracks.add(TrackInfo(trackIndex, label, format.language))
                                    if (group.isSelected) currentSubIdx = trackIndex
                                }
                            }

                            _audioTracks.value = newAudioTracks
                            _selectedAudioIndex.value = currentAudioIdx

                            _subtitleTracks.value = newSubtitleTracks
                            _selectedSubtitleIndex.value = currentSubIdx
                        }

                        override fun onPlayerError(error: PlaybackException) {
                            super.onPlayerError(error)

                            AppLogger.log("ExoPlayer error: ${error.message} - $error")

                            val isFormatUnsupported = error.errorCode == PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED
                            val isDecoderFailed    = error.errorCode == PlaybackException.ERROR_CODE_DECODER_INIT_FAILED

                            val isSourceError = error.errorCode == PlaybackException.ERROR_CODE_IO_UNSPECIFIED ||
                                    error.cause?.cause is IllegalStateException ||
                                    error.cause is Loader.UnexpectedLoaderException

                            val isMediaCodecRendererError = error.message?.contains("MediaCodecVideoRenderer") == true ||
                                    error.errorCode == PlaybackException.ERROR_CODE_DECODING_FAILED

                            when {
                                isMediaCodecRendererError -> {
                                    AppLogger.log("Recovering from MediaCodecVideoRenderer crash...")
                                    val lastPos = exoPlayer?.currentPosition ?: 0L
                                    exoPlayer?.prepare()
                                    exoPlayer?.seekTo(lastPos)
                                }
                                isFormatUnsupported || isDecoderFailed -> {
                                    _playerError.value = "Hardware unsupported: Your device cannot decode this video format (e.g., HEVC 10-bit)."
                                }
                                isSourceError -> {
                                    // Skip the broken file – notify the ViewModel
                                    val msg = error.message ?: "Source error"
                                    onPlaybackError?.invoke(msg)
                                }
                                else -> {
                                    _playerError.value = error.message ?: "Unknown playback error"
                                }
                            }
                        }
                    })

                    addAnalyticsListener(object : androidx.media3.exoplayer.analytics.AnalyticsListener {
                        override fun onAudioSessionIdChanged(
                            eventTime: androidx.media3.exoplayer.analytics.AnalyticsListener.EventTime,
                            audioSessionId: Int
                        ) {
                            if (_isAudioBoostEnabled.value) {
                                applyLoudnessEnhancer(audioSessionId)
                            }
                        }

                        override fun onVideoDecoderInitialized(
                            eventTime: androidx.media3.exoplayer.analytics.AnalyticsListener.EventTime,
                            decoderName: String,
                            initializedTimestampMs: Long,
                            initializationDurationMs: Long
                        ) {
                            _videoDecoderName.value = decoderName
                        }
                    })

                    // Dynamically calculate FPS for SW decoders which often lack format fps
                    setVideoFrameMetadataListener(object : androidx.media3.exoplayer.video.VideoFrameMetadataListener {
                        private var frameCount = 0
                        private var lastTimeMs = System.currentTimeMillis()

                        override fun onVideoFrameAboutToBeRendered(
                            presentationTimeUs: Long,
                            releaseTimeNs: Long,
                            format: androidx.media3.common.Format,
                            mediaFormat: android.media.MediaFormat?
                        ) {
                            frameCount++
                            val now = System.currentTimeMillis()
                            val diff = now - lastTimeMs
                            if (diff >= 1000) {
                                val currentFps = (frameCount * 1000f) / diff
                                _videoFps.value = currentFps
                                frameCount = 0
                                lastTimeMs = now
                            }
                        }
                    })
                }

            val player = exoPlayer!!
            val forwardingPlayer = object : ForwardingPlayer(player) {
                override fun seekToNext() {
                    onPlayNext?.invoke()
                }
                override fun seekToNextMediaItem() {
                    onPlayNext?.invoke()
                }
                override fun seekToPrevious() {
                    onPlayPrevious?.invoke()
                }
                override fun seekToPreviousMediaItem() {
                    onPlayPrevious?.invoke()
                }
                override fun getAvailableCommands(): Player.Commands {
                    return super.getAvailableCommands().buildUpon()
                        .add(Player.COMMAND_SEEK_TO_NEXT)
                        .add(Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM)
                        .add(Player.COMMAND_SEEK_TO_PREVIOUS)
                        .add(Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM)
                        .build()
                }
            }

            mediaSession = MediaSession.Builder(context, forwardingPlayer).build()
        }
    }

    private fun applyLoudnessEnhancer(audioSessionId: Int) {
        if (audioSessionId == androidx.media3.common.C.AUDIO_SESSION_ID_UNSET) return
        try {
            loudnessEnhancer?.enabled = false
            loudnessEnhancer?.release()
            loudnessEnhancer = LoudnessEnhancer(audioSessionId).apply {
                // 1500 mB = 15 dB boost (roughly 2x to 3x perceived loudness)
                setTargetGain(1500)
                enabled = true
            }
        } catch (e: Exception) {
            AppLogger.log("Failed to initialize LoudnessEnhancer: ${e.message}")
        }
    }

    @SuppressLint("Range")
    fun toggleAudioBoost(enabled: Boolean) {
        _isAudioBoostEnabled.value = enabled
        val player = exoPlayer ?: return

        if (enabled) {
            player.volume = 2.0f
            applyLoudnessEnhancer(player.audioSessionId)
        } else {
            player.volume = 1.0f
            try {
                loudnessEnhancer?.enabled = false
                loudnessEnhancer?.release()
            } catch (e: Exception) {}
            loudnessEnhancer = null
        }
    }

    fun playVideo(video: Video) {
        val player = exoPlayer ?: return
        _playerError.value = null
        val mediaItem = MediaItem.fromUri(video.uri)
        player.setMediaItem(mediaItem)
        player.prepare()
        player.playWhenReady = true
    }

    fun clearError() {
        _playerError.value = null
    }

    fun setPlaybackSpeed(speed: Float) {
        val player = exoPlayer ?: return
        player.playbackParameters = androidx.media3.common.PlaybackParameters(speed)
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

    fun seekSmooth(positionMs: Long) {
        val player = exoPlayer ?: return
        val duration = player.duration.coerceAtLeast(0L)
        player.seekTo(positionMs.coerceIn(0L, duration))
    }

    fun seekForward(ms: Long = 10000L) {
        val player = exoPlayer ?: return
        val currentPos = player.currentPosition
        val newPos = (currentPos + ms).coerceAtMost(player.duration.coerceAtLeast(0L))
        player.seekTo(newPos)
    }

    fun seekBackward(ms: Long = 10000L) {
        val player = exoPlayer ?: return
        val currentPos = player.currentPosition
        val newPos = (currentPos - ms).coerceAtLeast(0L)
        player.seekTo(newPos)
    }

    fun updateProgress() {
        val player = exoPlayer ?: return
        _currentPosition.value = player.currentPosition.coerceAtLeast(0L)
        _bufferedPosition.value = player.bufferedPosition.coerceAtLeast(0L)
        _duration.value = player.duration.coerceAtLeast(0L)
        
        player.videoFormat?.frameRate?.let { fps ->
            if (fps > 0f) {
                _videoFps.value = fps
            }
        }
    }

    fun releasePlayer() {
        mediaSession?.release()
        mediaSession = null
        exoPlayer?.release()
        exoPlayer = null
        try {
            loudnessEnhancer?.enabled = false
            loudnessEnhancer?.release()
        } catch (e: Exception) {}
        loudnessEnhancer = null
    }

    // --- Audio & Subtitle Selection ---

    fun selectAudioTrack(index: Int) {
        val player = exoPlayer ?: return
        var audioGroupIndex = -1
        var matchCount = 0

        val tracks = player.currentTracks
        for (i in tracks.groups.indices) {
            val group = tracks.groups[i]
            if (group.type == androidx.media3.common.C.TRACK_TYPE_AUDIO) {
                if (matchCount == index) {
                    audioGroupIndex = i
                    break
                }
                matchCount++
            }
        }

        if (audioGroupIndex != -1) {
            val trackGroup = tracks.groups[audioGroupIndex].mediaTrackGroup
            player.trackSelectionParameters = player.trackSelectionParameters
                .buildUpon()
                .setOverrideForType(TrackSelectionOverride(trackGroup, 0))
                .build()
        }
    }

    fun selectSubtitleTrack(index: Int) {
        val player = exoPlayer ?: return

        if (index == -1) {
            player.trackSelectionParameters = player.trackSelectionParameters
                .buildUpon()
                .setTrackTypeDisabled(androidx.media3.common.C.TRACK_TYPE_TEXT, true)
                .build()
            return
        }

        var textGroupIndex = -1
        var matchCount = 0

        val tracks = player.currentTracks
        for (i in tracks.groups.indices) {
            val group = tracks.groups[i]
            if (group.type == androidx.media3.common.C.TRACK_TYPE_TEXT) {
                if (matchCount == index) {
                    textGroupIndex = i
                    break
                }
                matchCount++
            }
        }

        if (textGroupIndex != -1) {
            val trackGroup = tracks.groups[textGroupIndex].mediaTrackGroup
            player.trackSelectionParameters = player.trackSelectionParameters
                .buildUpon()
                .setTrackTypeDisabled(androidx.media3.common.C.TRACK_TYPE_TEXT, false)
                .setOverrideForType(TrackSelectionOverride(trackGroup, 0))
                .build()
        }
    }

    fun loadExternalSubtitle(uri: Uri, mimeType: String) {
        val player = exoPlayer ?: return
        val currentMediaItem = player.currentMediaItem ?: return

        val subtitleConfig = SubtitleConfiguration.Builder(uri)
            .setMimeType(mimeType)
            .setLanguage("en")
            .setSelectionFlags(androidx.media3.common.C.SELECTION_FLAG_DEFAULT)
            .build()

        val newMediaItem = currentMediaItem.buildUpon()
            .setSubtitleConfigurations(listOf(subtitleConfig))
            .build()

        val currentPos = player.currentPosition
        val isPlaying = player.isPlaying

        player.setMediaItem(newMediaItem)
        player.prepare()
        player.seekTo(currentPos)
        player.playWhenReady = isPlaying

        player.trackSelectionParameters = player.trackSelectionParameters
            .buildUpon()
            .setTrackTypeDisabled(androidx.media3.common.C.TRACK_TYPE_TEXT, false)
            .build()
    }
}