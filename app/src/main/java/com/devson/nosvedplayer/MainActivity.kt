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
import android.content.Intent
import android.graphics.Color
import android.os.Build
import androidx.compose.runtime.LaunchedEffect
import androidx.media3.common.util.UnstableApi
import com.devson.nosvedplayer.model.Video

@UnstableApi
class MainActivity : AppCompatActivity() {

    private var videoViewModelRef: VideoViewModel? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = false
        }

        setContent {
            val settingsViewModel: SettingsViewModel = viewModel()
            val forceDark      by settingsViewModel.isDarkTheme.collectAsState()
            val dynamicColor   by settingsViewModel.dynamicColor.collectAsState()
            val selectedPalette by settingsViewModel.selectedPalette.collectAsState()
            val navBarTransparent by settingsViewModel.isNavBarTransparent.collectAsState()

            NosvedPlayerTheme(
                forceDark         = forceDark,
                dynamicColor      = dynamicColor,
                palette           = selectedPalette,
                isNavBarTransparent = navBarTransparent
            ) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val videoViewModel: VideoViewModel = viewModel()
                    videoViewModelRef = videoViewModel
                    MainScreen(videoViewModel = videoViewModel)

                    LaunchedEffect(intent) {
                        handleIntent(intent)
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        if (intent?.action == Intent.ACTION_VIEW) {
            intent.data?.let { uri ->
                val video = Video(
                    uri = uri.toString(),
                    title = uri.lastPathSegment ?: "External Video"
                )
                videoViewModelRef?.playVideo(video)
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