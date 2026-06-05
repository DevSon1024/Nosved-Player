package com.devson.nvplayer

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.content.Intent
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModelProvider
import com.devson.nvplayer.data.media.MediaStoreHelper
import com.devson.nvplayer.data.repository.VideoRepository
import com.devson.nvplayer.player.MPVPlayerEngine
import com.devson.nvplayer.ui.navigation.AppNavigation
import com.devson.nvplayer.ui.theme.NosvedPlayerTheme
import com.devson.nvplayer.viewmodel.HomeViewModel
import com.devson.nvplayer.viewmodel.PlayerViewModel
import com.devson.nvplayer.viewmodel.SettingsViewModel
import com.devson.nvplayer.viewmodel.VideoListViewModel
import com.devson.nvplayer.viewmodel.FileOperationsViewModel
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.video.VideoFrameDecoder
import com.devson.nvplayer.util.VideoThumbnailFetcher
import coil3.disk.DiskCache
import coil3.disk.directory
import android.content.res.Configuration
import android.util.Log
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.IntentFilter
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.Lifecycle
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val _isInPipMode = mutableStateOf(false)

    private val pipReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent == null || !playerViewModelLazy.isInitialized()) return
            when (intent.action) {
                "com.devson.nvplayer.PIP_PREV" -> playerViewModel.playPrevious()
                "com.devson.nvplayer.PIP_PLAY_PAUSE" -> {
                    playerViewModel.togglePlayback()
                    updatePipActions()
                }
                "com.devson.nvplayer.PIP_NEXT" -> playerViewModel.playNext()
            }
        }
    }

    private fun buildPipActionsList(): List<android.app.RemoteAction> {
        val actions = mutableListOf<android.app.RemoteAction>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // 1. Prev Action
            val prevIntent = Intent("com.devson.nvplayer.PIP_PREV")
            val prevPendingIntent = PendingIntent.getBroadcast(
                this,
                1,
                prevIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val prevIcon = android.graphics.drawable.Icon.createWithResource(this, android.R.drawable.ic_media_previous)
            val prevAction = android.app.RemoteAction(prevIcon, "Previous", "Previous", prevPendingIntent)
            prevAction.setEnabled(playerViewModel.hasPrevious.value)
            actions.add(prevAction)

            // 2. Play/Pause Action
            val isPlaying = playerViewModel.isPlaying.value
            val playPauseIconRes = if (isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play
            val playPauseTitle = if (isPlaying) "Pause" else "Play"
            val playPauseIntent = Intent("com.devson.nvplayer.PIP_PLAY_PAUSE")
            val playPausePendingIntent = PendingIntent.getBroadcast(
                this,
                2,
                playPauseIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val playPauseIcon = android.graphics.drawable.Icon.createWithResource(this, playPauseIconRes)
            val playPauseAction = android.app.RemoteAction(playPauseIcon, playPauseTitle, playPauseTitle, playPausePendingIntent)
            actions.add(playPauseAction)

            // 3. Next Action
            val nextIntent = Intent("com.devson.nvplayer.PIP_NEXT")
            val nextPendingIntent = PendingIntent.getBroadcast(
                this,
                3,
                nextIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val nextIcon = android.graphics.drawable.Icon.createWithResource(this, android.R.drawable.ic_media_next)
            val nextAction = android.app.RemoteAction(nextIcon, "Next", "Next", nextPendingIntent)
            nextAction.setEnabled(playerViewModel.hasNext.value)
            actions.add(nextAction)
        }
        return actions
    }

    private fun updatePipActions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val params = android.app.PictureInPictureParams.Builder()
                .setActions(buildPipActionsList())
                .build()
            setPictureInPictureParams(params)
        }
    }

    private val mediaStoreHelper by lazy { MediaStoreHelper(this) }
    private val repository by lazy { VideoRepository(mediaStoreHelper, this) }
    private val viewSettingsRepo by lazy { com.devson.nvplayer.repository.ViewSettingsRepository.getInstance(applicationContext) }

    private val homeViewModel by lazy { HomeViewModel(applicationContext, repository) }
    private val settingsViewModel by lazy { ViewModelProvider(this)[SettingsViewModel::class.java] }
    private val videoListViewModel by lazy { VideoListViewModel(repository, viewSettingsRepo) }
    private val fileOpsViewModel by lazy { ViewModelProvider(this)[FileOperationsViewModel::class.java] }

    private val playerEngine by lazy { MPVPlayerEngine(applicationContext) }
    private val playerViewModelLazy = lazy {
        val factory = PlayerViewModel.Factory(application, playerEngine)
        ViewModelProvider(this, factory)[PlayerViewModel::class.java]
    }
    private val playerViewModel by playerViewModelLazy

    private val requestNotificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (!isGranted) {
            Toast.makeText(this, "Notification permission is required for Background Play controls", Toast.LENGTH_LONG).show()
        }
    }

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            homeViewModel.loadFolders()
            videoListViewModel.loadVideos()
            checkNotificationPermission()
        } else {
            Toast.makeText(this, "Permission denied to read videos", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Asynchronously copy yt-dlp assets
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                com.devson.nvplayer.player.ytdlp.YtdlpManager.copyAssets(applicationContext)
                Log.d("MainActivity", "yt-dlp assets copy completed")
            } catch (e: Exception) {
                Log.e("MainActivity", "Failed to copy yt-dlp assets at startup", e)
            }
        }

        // Register PiP action receiver
        val filter = IntentFilter().apply {
            addAction("com.devson.nvplayer.PIP_PREV")
            addAction("com.devson.nvplayer.PIP_PLAY_PAUSE")
            addAction("com.devson.nvplayer.PIP_NEXT")
        }
        ContextCompat.registerReceiver(
            this,
            pipReceiver,
            filter,
            ContextCompat.RECEIVER_NOT_EXPORTED
        )

        // Listen for playback and navigation state changes to update PiP actions dynamically
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                combine(
                    playerViewModel.isPlaying,
                    playerViewModel.hasNext,
                    playerViewModel.hasPrevious
                ) { _, _, _ -> }.collect {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && isInPictureInPictureMode) {
                        updatePipActions()
                    }
                }
            }
        }

        val imageLoader = ImageLoader.Builder(this)
            .components {
                add(VideoThumbnailFetcher.Factory(applicationContext))
                add(VideoThumbnailFetcher.StringFactory(applicationContext))
                add(VideoFrameDecoder.Factory())
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("image_cache"))
                    .maxSizeBytes(512 * 1024 * 1024) // 512 MB
                    .build()
            }
            .build()
        SingletonImageLoader.setSafe { imageLoader }

        checkAndRequestPermissions()

        setContent {
            val isDark by settingsViewModel.isDarkTheme.collectAsState()
            val dynamicColor by settingsViewModel.dynamicColor.collectAsState()
            val selectedPalette by settingsViewModel.selectedPalette.collectAsState()
            val isNavBarTransparent by settingsViewModel.isNavBarTransparent.collectAsState()
            val isAmoledTheme by settingsViewModel.isAmoledTheme.collectAsState()

            NosvedPlayerTheme(
                forceDark = isDark,
                dynamicColor = dynamicColor,
                palette = selectedPalette,
                isNavBarTransparent = isNavBarTransparent,
                isAmoledTheme = isAmoledTheme
            ) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = Color.Transparent
                ) {
                    AppNavigation(
                        homeViewModel = homeViewModel,
                        playerViewModel = { playerViewModel },
                        playerEngine = { playerEngine },
                        settingsViewModel = settingsViewModel,
                        videoListViewModel = videoListViewModel,
                        fileOpsViewModel = fileOpsViewModel,
                        isInPipMode = _isInPipMode.value,
                        onEnterPip = { enterPipMode() }
                    )
                }
            }
        }
    }

    private fun checkNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                requestNotificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    private fun checkAndRequestPermissions() {
        val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_VIDEO
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }

        if (ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED) {
            homeViewModel.loadFolders()
            videoListViewModel.loadVideos()
            checkNotificationPermission()
        } else {
            requestPermissionLauncher.launch(permission)
        }
    }

    override fun onPause() {
        super.onPause()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && isInPictureInPictureMode) {
            return
        }
        val bgPlayEnabled = if (playerViewModelLazy.isInitialized()) playerViewModel.playbackSettings.value.backgroundPlayEnabled else false
        if (bgPlayEnabled && playerViewModelLazy.isInitialized() && playerViewModel.isPlaying.value) {
            return
        }
        if (playerViewModelLazy.isInitialized()) {
            playerViewModel.pause()
        }
    }

    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean, newConfig: Configuration) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        _isInPipMode.value = isInPictureInPictureMode
    }

    private fun enterPipMode() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    val width = playerViewModel.videoWidth.value.coerceAtLeast(1)
                    val height = playerViewModel.videoHeight.value.coerceAtLeast(1)
                    val ratio = width.toFloat() / height.toFloat()
                    val builder = android.app.PictureInPictureParams.Builder()
                    if (ratio in 0.4184f..2.39f) {
                        builder.setAspectRatio(android.util.Rational(width.toInt(), height.toInt()))
                    }
                    builder.setActions(buildPipActionsList())
                    enterPictureInPictureMode(builder.build())
                } else {
                    enterPictureInPictureMode()
                }
            } catch (e: Exception) {
                Log.e("MainActivity", "Failed to enter PiP mode", e)
            }
        }
    }

    override fun onStart() {
        super.onStart()
        val serviceIntent = Intent(this, com.devson.nvplayer.player.MediaPlaybackService::class.java)
        stopService(serviceIntent)
    }

    override fun onStop() {
        super.onStop()
        val bgPlayEnabled = if (playerViewModelLazy.isInitialized()) playerViewModel.playbackSettings.value.backgroundPlayEnabled else false
        if (bgPlayEnabled && playerViewModelLazy.isInitialized() && playerViewModel.isPlaying.value) {
            val title = playerViewModel.currentUri.value?.lastPathSegment ?: "Video Playback"
            val serviceIntent = Intent(this, com.devson.nvplayer.player.MediaPlaybackService::class.java).apply {
                putExtra(com.devson.nvplayer.player.MediaPlaybackService.EXTRA_VIDEO_TITLE, title)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(serviceIntent)
            } else {
                startService(serviceIntent)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            unregisterReceiver(pipReceiver)
        } catch (_: Exception) {}
        val serviceIntent = Intent(this, com.devson.nvplayer.player.MediaPlaybackService::class.java)
        stopService(serviceIntent)
    }
}