package com.devson.nvplayer.player.service

import android.R
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.provider.MediaStore
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import android.util.Log
import android.util.Size
import androidx.core.app.NotificationCompat
import com.devson.nvplayer.MainActivity
import com.devson.nvplayer.player.engine.MPVPlayerEngine
import `is`.xyz.mpv.MPVLib
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MediaPlaybackService : Service() {

    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.Main + serviceJob)
    private var mediaSession: MediaSessionCompat? = null
    private var currentThumbnail: Bitmap? = null

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                ACTION_TOGGLE_PLAYBACK -> {
                    MPVPlayerEngine.activeInstance?.togglePlayback()
                }
                ACTION_STOP -> {
                    MPVPlayerEngine.activeInstance?.pause()
                    stopSelf()
                }
                ACTION_PREV -> {
                    val broadcastIntent = Intent("com.devson.nvplayer.PIP_PREV").apply {
                        setPackage(packageName)
                    }
                    sendBroadcast(broadcastIntent)
                }
                ACTION_NEXT -> {
                    val broadcastIntent = Intent("com.devson.nvplayer.PIP_NEXT").apply {
                        setPackage(packageName)
                    }
                    sendBroadcast(broadcastIntent)
                }
                ACTION_REWIND -> {
                    val active = MPVPlayerEngine.activeInstance
                    if (active != null) {
                        val current = active.currentPosition.value
                        active.seekTo((current - 10000L).coerceAtLeast(0L), precise = false)
                    }
                }
                ACTION_FORWARD -> {
                    val active = MPVPlayerEngine.activeInstance
                    if (active != null) {
                        val current = active.currentPosition.value
                        val dur = active.duration.value
                        active.seekTo((current + 10000L).coerceAtMost(dur), precise = false)
                    }
                }
            }
        }
    }

    companion object {
        const val CHANNEL_ID = "media_playback_channel"
        const val NOTIFICATION_ID = 8888

        const val ACTION_TOGGLE_PLAYBACK = "com.devson.nvplayer.ACTION_TOGGLE_PLAYBACK"
        const val ACTION_STOP = "com.devson.nvplayer.ACTION_STOP"
        const val ACTION_PREV = "com.devson.nvplayer.ACTION_PREV"
        const val ACTION_NEXT = "com.devson.nvplayer.ACTION_NEXT"
        const val ACTION_REWIND = "com.devson.nvplayer.ACTION_REWIND"
        const val ACTION_FORWARD = "com.devson.nvplayer.ACTION_FORWARD"

        const val EXTRA_VIDEO_TITLE = "extra_video_title"
    }

    override fun onCreate() {
        super.onCreate()
        Log.d("MediaPlaybackService", "Service created")
        createNotificationChannel()

        mediaSession = MediaSessionCompat(this, "NVPlayerMediaSession").apply {
            isActive = true
            setCallback(object : MediaSessionCompat.Callback() {
                override fun onPlay() {
                    MPVPlayerEngine.activeInstance?.play()
                }
                override fun onPause() {
                    MPVPlayerEngine.activeInstance?.pause()
                }
                override fun onSkipToNext() {
                    val broadcastIntent = Intent("com.devson.nvplayer.PIP_NEXT").apply {
                        setPackage(packageName)
                    }
                    sendBroadcast(broadcastIntent)
                }
                override fun onSkipToPrevious() {
                    val broadcastIntent = Intent("com.devson.nvplayer.PIP_PREV").apply {
                        setPackage(packageName)
                    }
                    sendBroadcast(broadcastIntent)
                }
                override fun onSeekTo(pos: Long) {
                    MPVPlayerEngine.activeInstance?.seekTo(pos, precise = false)
                }
            })
        }

        val filter = IntentFilter().apply {
            addAction(ACTION_TOGGLE_PLAYBACK)
            addAction(ACTION_STOP)
            addAction(ACTION_PREV)
            addAction(ACTION_NEXT)
            addAction(ACTION_REWIND)
            addAction(ACTION_FORWARD)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(receiver, filter, RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(receiver, filter)
        }

        // Observe playing and playback state of activeInstance to update notification on title/track change
        serviceScope.launch {
            val engine = MPVPlayerEngine.activeInstance
            if (engine != null) {
                combine(engine.isPlaying, engine.playbackState) { isPlaying, state ->
                    isPlaying to state
                }.collect {
                    updateNotification()
                }
            }
        }
    }

    private var videoTitle: String = "Video Playback"

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val title = intent?.getStringExtra(EXTRA_VIDEO_TITLE)
        val uri = intent?.data

        currentThumbnail = null

        var resolvedTitle: String? = title
        if (resolvedTitle.isNullOrBlank() || resolvedTitle.all { it.isDigit() } || resolvedTitle.startsWith("content://")) {
            resolvedTitle = uri?.let { getDisplayNameFromUri(this, it) }
        }

        if (resolvedTitle.isNullOrBlank() || resolvedTitle.startsWith("content://")) {
            resolvedTitle = "Video Playback"
        }

        // Strip extension if present
        val dot = resolvedTitle.lastIndexOf('.')
        if (dot > 0 && dot < resolvedTitle.length - 1) {
            resolvedTitle = resolvedTitle.substring(0, dot)
        }

        videoTitle = resolvedTitle

        // Disable video track in MPV to avoid hardware decoder crashes in background
        MPVPlayerEngine.activeInstance?.setVideoTrackEnabled(false)

        updateNotification()

        if (uri != null) {
            serviceScope.launch {
                try {
                    val bitmap = withContext(Dispatchers.IO) {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
                            uri.scheme == "content"
                        ) {
                            try {
                                this@MediaPlaybackService.contentResolver
                                    .loadThumbnail(uri, Size(512, 512), null)
                            } catch (_: Exception) {
                                null
                            }
                        } else {
                            val retriever = MediaMetadataRetriever()
                            try {
                                retriever.setDataSource(this@MediaPlaybackService, uri)
                                val dur = retriever
                                    .extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                                    ?.toLongOrNull() ?: 0L
                                val seekUs = if (dur > 0) dur * 200L else 0L // 20% of duration
                                retriever.getFrameAtTime(
                                    seekUs,
                                    MediaMetadataRetriever.OPTION_PREVIOUS_SYNC
                                )
                            } catch (_: Exception) {
                                null
                            } finally {
                                try {
                                    retriever.release()
                                } catch (_: Exception) {
                                }
                            }
                        }
                    }
                    if (bitmap != null) {
                        currentThumbnail = bitmap
                        updateNotification()
                    }
                } catch (e: Exception) {
                    Log.e("MediaPlaybackService", "Error loading thumbnail for notification", e)
                }
            }
        }

        return START_NOT_STICKY
    }

    private fun updateNotification() {
        val activeEngine = MPVPlayerEngine.activeInstance
        val isPlaying = activeEngine?.isPlaying?.value ?: false

        // Dynamically get the currently playing file title from MPVLib
        var dynamicTitle = MPVLib.getPropertyString("media-title")
        if (dynamicTitle.isNullOrEmpty() || dynamicTitle.all { it.isDigit() } || dynamicTitle.startsWith("content://")) {
            dynamicTitle = videoTitle
        } else {
            // Strip extension
            val dot = dynamicTitle.lastIndexOf('.')
            if (dot > 0) {
                dynamicTitle = dynamicTitle.substring(0, dot)
            }
        }

        // Update MediaSessionCompat metadata
        val metadataBuilder = MediaMetadataCompat.Builder()
            .putString(MediaMetadataCompat.METADATA_KEY_TITLE, dynamicTitle)
            .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, "NVPlayer")
        currentThumbnail?.let {
            metadataBuilder.putBitmap(MediaMetadataCompat.METADATA_KEY_ART, it)
        }
        mediaSession?.setMetadata(metadataBuilder.build())

        // Update PlaybackStateCompat
        val stateBuilder = PlaybackStateCompat.Builder()
            .setActions(
                PlaybackStateCompat.ACTION_PLAY or
                PlaybackStateCompat.ACTION_PAUSE or
                PlaybackStateCompat.ACTION_PLAY_PAUSE or
                PlaybackStateCompat.ACTION_STOP or
                PlaybackStateCompat.ACTION_SKIP_TO_NEXT or
                PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS or
                PlaybackStateCompat.ACTION_SEEK_TO
            )
            .setState(
                if (isPlaying) PlaybackStateCompat.STATE_PLAYING else PlaybackStateCompat.STATE_PAUSED,
                activeEngine?.currentPosition?.value ?: 0L,
                1.0f
            )
        mediaSession?.setPlaybackState(stateBuilder.build())

        val playPauseIcon = if (isPlaying) {
            R.drawable.ic_media_pause
        } else {
            R.drawable.ic_media_play
        }
        val playPauseText = if (isPlaying) "Pause" else "Play"

        val prevIntent = Intent(ACTION_PREV).apply { setPackage(packageName) }
        val rewindIntent = Intent(ACTION_REWIND).apply { setPackage(packageName) }
        val toggleIntent = Intent(ACTION_TOGGLE_PLAYBACK).apply { setPackage(packageName) }
        val forwardIntent = Intent(ACTION_FORWARD).apply { setPackage(packageName) }
        val nextIntent = Intent(ACTION_NEXT).apply { setPackage(packageName) }
        val stopIntent = Intent(ACTION_STOP).apply { setPackage(packageName) }

        val pendingFlags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE

        val prevPendingIntent = PendingIntent.getBroadcast(this, 10, prevIntent, pendingFlags)
        val rewindPendingIntent = PendingIntent.getBroadcast(this, 11, rewindIntent, pendingFlags)
        val togglePendingIntent = PendingIntent.getBroadcast(this, 12, toggleIntent, pendingFlags)
        val forwardPendingIntent = PendingIntent.getBroadcast(this, 13, forwardIntent, pendingFlags)
        val nextPendingIntent = PendingIntent.getBroadcast(this, 14, nextIntent, pendingFlags)
        val stopPendingIntent = PendingIntent.getBroadcast(this, 15, stopIntent, pendingFlags)

        val notificationIntent = Intent(this, MainActivity::class.java)
        val contentPendingIntent = PendingIntent.getActivity(this, 0, notificationIntent, pendingFlags)

        val notificationBuilder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(dynamicTitle)
            .setContentText(if (isPlaying) "Playing in background" else "Paused")
            .setSmallIcon(R.drawable.ic_media_play)
            .setLargeIcon(currentThumbnail)
            .setContentIntent(contentPendingIntent)
            .setDeleteIntent(stopPendingIntent) // Stop service when swiped away
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .addAction(R.drawable.ic_media_previous, "Previous", prevPendingIntent)
            .addAction(R.drawable.ic_media_rew, "Rewind", rewindPendingIntent)
            .addAction(playPauseIcon, playPauseText, togglePendingIntent)
            .addAction(R.drawable.ic_media_ff, "Fast Forward", forwardPendingIntent)
            .addAction(R.drawable.ic_media_next, "Next", nextPendingIntent)
            .setStyle(
                androidx.media.app.NotificationCompat.MediaStyle()
                    .setMediaSession(mediaSession?.sessionToken)
                    .setShowActionsInCompactView(0, 2, 4) // Show Prev, Play/Pause, Next
            )
            .setOngoing(isPlaying)

        val notification = notificationBuilder.build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d("MediaPlaybackService", "Service destroyed")

        mediaSession?.apply {
            isActive = false
            release()
        }
        mediaSession = null

        // Re-enable video track in MPV for when we return to foreground
        MPVPlayerEngine.activeInstance?.setVideoTrackEnabled(true)
        try {
            unregisterReceiver(receiver)
        } catch (e: Exception) {
            Log.e("MediaPlaybackService", "Error unregistering receiver", e)
        }
        serviceJob.cancel()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                CHANNEL_ID,
                "Media Playback Channel",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Background video playback controls"
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(serviceChannel)
        }
    }

    private fun getDisplayNameFromUri(context: Context, uri: Uri): String? {
        if (uri.scheme == "content") {
            try {
                context.contentResolver.query(
                    uri,
                    arrayOf(MediaStore.MediaColumns.DISPLAY_NAME),
                    null, null, null
                )?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val colIdx = cursor.getColumnIndex(MediaStore.MediaColumns.DISPLAY_NAME)
                        if (colIdx >= 0) {
                            val displayName = cursor.getString(colIdx)
                            if (!displayName.isNullOrBlank()) {
                                return displayName
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("MediaPlaybackService", "Error querying display name from ContentResolver", e)
            }
        }
        val lastSegment = uri.lastPathSegment
        if (lastSegment != null && !lastSegment.all { it.isDigit() }) {
            return lastSegment
        }
        return null
    }
}