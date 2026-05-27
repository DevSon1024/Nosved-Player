package com.devson.nvplayer

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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

class MainActivity : ComponentActivity() {

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

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            homeViewModel.loadFolders()
            videoListViewModel.loadVideos()
        } else {
            Toast.makeText(this, "Permission denied to read videos", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

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
                        fileOpsViewModel = fileOpsViewModel
                    )
                }
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
        } else {
            requestPermissionLauncher.launch(permission)
        }
    }

    override fun onPause() {
        super.onPause()
        if (playerViewModelLazy.isInitialized()) {
            playerViewModel.pause()
        }
    }
}