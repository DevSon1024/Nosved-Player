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
import com.devson.nvplayer.viewmodel.FolderViewModel
import com.devson.nvplayer.viewmodel.HomeViewModel
import com.devson.nvplayer.viewmodel.PlayerViewModel
import com.devson.nvplayer.viewmodel.SettingsViewModel
import coil.ImageLoader
import coil.Coil
import coil.decode.VideoFrameDecoder

class MainActivity : ComponentActivity() {

    private lateinit var playerViewModel: PlayerViewModel
    private lateinit var homeViewModel: HomeViewModel
    private lateinit var folderViewModel: FolderViewModel
    private lateinit var settingsViewModel: SettingsViewModel

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            homeViewModel.loadFolders()
        } else {
            Toast.makeText(this, "Permission denied to read videos", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val imageLoader = ImageLoader.Builder(this)
            .components {
                add(VideoFrameDecoder.Factory())
            }
            .build()
        Coil.setImageLoader(imageLoader)

        val mediaStoreHelper = MediaStoreHelper(this)
        val repository = VideoRepository(mediaStoreHelper)
        
        homeViewModel = HomeViewModel(repository)
        folderViewModel = FolderViewModel(repository)
        settingsViewModel = ViewModelProvider(this)[SettingsViewModel::class.java]

        val playerEngine = MPVPlayerEngine(applicationContext)
        val factory = PlayerViewModel.Factory(application, playerEngine)
        playerViewModel = ViewModelProvider(this, factory)[PlayerViewModel::class.java]

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
                        folderViewModel = folderViewModel,
                        playerViewModel = playerViewModel,
                        settingsViewModel = settingsViewModel
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
        } else {
            requestPermissionLauncher.launch(permission)
        }
    }

    override fun onPause() {
        super.onPause()
        if (::playerViewModel.isInitialized) {
            playerViewModel.pause()
        }
    }
}