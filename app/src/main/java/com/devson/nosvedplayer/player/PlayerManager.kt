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
import com.devson.nosvedplayer.model.TrackInfo
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.MediaItem.SubtitleConfiguration
import android.net.Uri
import androidx.media3.common.util.UnstableApi
import androidx.annotation.OptIn
import io.github.anilbeesetti.nextlib.media3ext.ffdecoder.NextRenderersFactory

@OptIn(UnstableApi::class)
class PlayerManager(private val context: Context) {

    var exoPlayer: ExoPlayer? = null
        private set

    var onVideoEnded: (() -> Unit)? = null

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

    fun initializePlayer() {
        if (exoPlayer == null) {
            // 1. Use Nextlib's factory instead of ExoPlayer's DefaultRenderersFactory
            // (If it shows in red, click it and press Alt+Enter to import it)
            val renderersFactory = NextRenderersFactory(context)
            // 2. Tell it to prefer FFmpeg over hardware decoders
            .setExtensionRendererMode(androidx.media3.exoplayer.DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER)
            // 3. Fallback to hardware if FFmpeg doesn't support the format
            .setEnableDecoderFallback(true)
            // 4. Stop the MediaTek hardware crash!
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
                                val langName = format.language?.let { java.util.Locale(it).displayLanguage }
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
                                val langName = format.language?.let { java.util.Locale(it).displayLanguage }
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
                        
                        com.devson.nosvedplayer.AppLogger.log("ExoPlayer error: ${error.message} - $error")
                        
                        // CHANGE 2: Intercept format and decoder failures
                        val isFormatUnsupported = error.errorCode == PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED
                        val isDecoderFailed = error.errorCode == PlaybackException.ERROR_CODE_DECODER_INIT_FAILED

                        if (isFormatUnsupported || isDecoderFailed) {
                            _playerError.value = "Hardware unsupported: Your device cannot decode this video format (e.g., HEVC 10-bit)."
                        } else {
                            _playerError.value = error.message ?: "Unknown playback error"
                        }
                    }
                })
                
                addAnalyticsListener(object : androidx.media3.exoplayer.analytics.AnalyticsListener {
                    override fun onVideoDecoderInitialized(
                        eventTime: androidx.media3.exoplayer.analytics.AnalyticsListener.EventTime,
                        decoderName: String,
                        initializedTimestampMs: Long,
                        initializationDurationMs: Long
                    ) {
                        _videoDecoderName.value = decoderName
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
            // Disable subtitles
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
        
        // Force text tracks enabled so the new external sub shows
        player.trackSelectionParameters = player.trackSelectionParameters
            .buildUpon()
            .setTrackTypeDisabled(androidx.media3.common.C.TRACK_TYPE_TEXT, false)
            .build()
    }
}
