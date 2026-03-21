package com.devson.nosvedplayer.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewmodel.compose.viewModel
import com.devson.nosvedplayer.model.Video
import com.devson.nosvedplayer.viewmodel.VideoViewModel

/** App-level navigation state. */
private enum class AppScreen { HOME, VIDEOS, SETTINGS, ABOUT, LOGS }

@Composable
fun MainScreen(
    videoViewModel: VideoViewModel = viewModel()
) {
    var currentVideo by remember { mutableStateOf<Video?>(null) }
    var resumePositionMs by remember { mutableStateOf(0L) }
    var appScreen by remember { mutableStateOf(AppScreen.HOME) }
    val settingsViewModel: com.devson.nosvedplayer.viewmodel.SettingsViewModel = viewModel()

    val isPlayerVisible = currentVideo != null

    // Back handler for player
    BackHandler(enabled = isPlayerVisible) {
        videoViewModel.stopVideo()
        currentVideo = null
        resumePositionMs = 0L
    }

    when {
        // Full-screen player overlay (no bottom nav)
        isPlayerVisible -> {
            VideoScreen(viewModel = videoViewModel)
        }

        // Settings
        appScreen == AppScreen.SETTINGS -> {
            BackHandler { appScreen = AppScreen.HOME }
            SettingsScreen(
                onBack = { appScreen = AppScreen.HOME },
                onNavigateToAbout = { appScreen = AppScreen.ABOUT },
                onNavigateToLogs = { appScreen = AppScreen.LOGS },
                settingsViewModel = settingsViewModel
            )
        }

        // About
        appScreen == AppScreen.ABOUT -> {
            BackHandler { appScreen = AppScreen.SETTINGS }
            AboutScreen(
                onBack = { appScreen = AppScreen.SETTINGS },
                onEnableDeveloperMode = { settingsViewModel.enableDeveloperMode() }
            )
        }
        
        // Logs
        appScreen == AppScreen.LOGS -> {
            BackHandler { appScreen = AppScreen.SETTINGS }
            LogScreen(onBack = { appScreen = AppScreen.SETTINGS })
        }

        // Videos List
        appScreen == AppScreen.VIDEOS -> {
            BackHandler { appScreen = AppScreen.HOME }
            VideoListScreen(
                onVideoSelected = { video, playlist ->
                    resumePositionMs = 0L
                    currentVideo = video
                    videoViewModel.playVideo(video, playlist, 0L)
                },
                onNavigateToSettings = { appScreen = AppScreen.SETTINGS },
                onBack = { appScreen = AppScreen.HOME }
            )
        }

        // Home
        else -> {
            HomeScreen(
                onVideoSelected = { video, playlist, position ->
                    resumePositionMs = position
                    currentVideo = video
                    videoViewModel.playVideo(video, playlist, position)
                },
                onNavigateToSettings = { appScreen = AppScreen.SETTINGS },
                onNavigateToVideos = { appScreen = AppScreen.VIDEOS }
            )
        }
    }
}
