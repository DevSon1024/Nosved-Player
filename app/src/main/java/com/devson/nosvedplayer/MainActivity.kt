package com.devson.nosvedplayer

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.devson.nosvedplayer.ui.screens.MainScreen
import com.devson.nosvedplayer.ui.theme.AppThemePalette
import com.devson.nosvedplayer.ui.theme.NosvedPlayerTheme
import com.devson.nosvedplayer.viewmodel.SettingsViewModel
import com.devson.nosvedplayer.viewmodel.VideoViewModel
import android.graphics.Color
import android.os.Build
import androidx.activity.SystemBarStyle
import androidx.media3.common.util.UnstableApi

@UnstableApi
class MainActivity : AppCompatActivity() {

    private var videoViewModelRef: VideoViewModel? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            navigationBarStyle = SystemBarStyle.auto(
                Color.TRANSPARENT,
                Color.TRANSPARENT
            )
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = false
        }

        setContent {
            val settingsViewModel: SettingsViewModel = viewModel()
            val forceDark      by settingsViewModel.isDarkTheme.collectAsState()
            val dynamicColor   by settingsViewModel.dynamicColor.collectAsState()
            val selectedPalette by settingsViewModel.selectedPalette.collectAsState()

            NosvedPlayerTheme(
                forceDark    = forceDark,
                dynamicColor = dynamicColor,
                palette      = selectedPalette
            ) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val videoViewModel: VideoViewModel = viewModel()
                    videoViewModelRef = videoViewModel
                    MainScreen(videoViewModel = videoViewModel)
                }
            }
        }
    }

    // Pause playback when app goes to background
    override fun onPause() {
        super.onPause()
        videoViewModelRef?.pauseVideo()
    }

    // Resume playback when app returns to foreground (only if there's an active video)
    override fun onResume() {
        super.onResume()
        videoViewModelRef?.resumeVideo()
    }
}