package com.devson.nosvedplayer.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import com.devson.nosvedplayer.model.Video
import com.devson.nosvedplayer.viewmodel.VideoViewModel

/** App-level navigation state. */
private enum class AppScreen { HOME, VIDEOS, SETTINGS, ABOUT, LOGS, PRIVACY_POLICY }

@Composable
fun MainScreen(
    videoViewModel: VideoViewModel = viewModel()
) {
    var currentVideo by remember { mutableStateOf<Video?>(null) }
    var resumePositionMs by remember { mutableStateOf(0L) }
    var appScreen by remember { mutableStateOf(AppScreen.HOME) }
    var previousScreen by remember { mutableStateOf(AppScreen.HOME) }
    val settingsViewModel: com.devson.nosvedplayer.viewmodel.SettingsViewModel = viewModel()
    
    val saveableStateHolder = rememberSaveableStateHolder()

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
            saveableStateHolder.SaveableStateProvider("PLAYER") {
                VideoScreen(viewModel = videoViewModel)
            }
        }

        // Settings
        appScreen == AppScreen.SETTINGS -> {
            BackHandler { appScreen = previousScreen }
            saveableStateHolder.SaveableStateProvider(AppScreen.SETTINGS.name) {
                SettingsScreen(
                    onBack = { appScreen = previousScreen },
                    onNavigateToAbout = { appScreen = AppScreen.ABOUT },
                    onNavigateToLogs = { appScreen = AppScreen.LOGS },
                    onNavigateToPrivacyPolicy = { appScreen = AppScreen.PRIVACY_POLICY },
                    settingsViewModel = settingsViewModel
                )
            }
        }

        // About
        appScreen == AppScreen.ABOUT -> {
            BackHandler { appScreen = AppScreen.SETTINGS }
            saveableStateHolder.SaveableStateProvider(AppScreen.ABOUT.name) {
                AboutScreen(
                    onBack = { appScreen = AppScreen.SETTINGS },
                    onEnableDeveloperMode = { settingsViewModel.enableDeveloperMode() }
                )
            }
        }
        
        // Logs
        appScreen == AppScreen.LOGS -> {
            BackHandler { appScreen = AppScreen.SETTINGS }
            saveableStateHolder.SaveableStateProvider(AppScreen.LOGS.name) {
                LogScreen(onBack = { appScreen = AppScreen.SETTINGS })
            }
        }

        // Privacy Policy
        appScreen == AppScreen.PRIVACY_POLICY -> {
            BackHandler { appScreen = AppScreen.SETTINGS }
            saveableStateHolder.SaveableStateProvider(AppScreen.PRIVACY_POLICY.name) {
                PrivacyPolicyScreen(onBack = { appScreen = AppScreen.SETTINGS })
            }
        }

        // Videos List
        appScreen == AppScreen.VIDEOS -> {
            BackHandler { appScreen = AppScreen.HOME }
            saveableStateHolder.SaveableStateProvider(AppScreen.VIDEOS.name) {
                VideoListScreen(
                    onVideoSelected = { video, playlist, position ->
                        resumePositionMs = position
                        currentVideo = video
                        videoViewModel.playVideo(video, playlist, position)
                    },
                    onNavigateToSettings = { 
                        previousScreen = appScreen
                        appScreen = AppScreen.SETTINGS 
                    },
                    onBack = { appScreen = AppScreen.HOME }
                )
            }
        }

        // Home
        else -> {
            saveableStateHolder.SaveableStateProvider(AppScreen.HOME.name) {
                HomeScreen(
                    onVideoSelected = { video, playlist, position ->
                        resumePositionMs = position
                        currentVideo = video
                        videoViewModel.playVideo(video, playlist, position)
                    },
                    onNavigateToSettings = { 
                        previousScreen = AppScreen.HOME
                        appScreen = AppScreen.SETTINGS 
                    },
                    onNavigateToVideos = { appScreen = AppScreen.VIDEOS }
                )
            }
        }
    }
}
