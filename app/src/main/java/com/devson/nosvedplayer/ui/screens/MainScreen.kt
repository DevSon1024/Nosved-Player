package com.devson.nosvedplayer.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.VideoLibrary
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.devson.nosvedplayer.model.Video
import com.devson.nosvedplayer.viewmodel.VideoViewModel
import androidx.compose.foundation.layout.consumeWindowInsets

/** Tabs available in the bottom nav bar. */
enum class BottomNavTab { HOME, VIDEOS }

/** App-level navigation state. */
private enum class AppScreen { MAIN, SETTINGS, ABOUT, LOGS }

@Composable
fun MainScreen(
    videoViewModel: VideoViewModel = viewModel()
) {
    var selectedTab by rememberSaveable { mutableStateOf(BottomNavTab.HOME) }
    var currentVideo by remember { mutableStateOf<Video?>(null) }
    var resumePositionMs by remember { mutableStateOf(0L) }
    var appScreen by remember { mutableStateOf(AppScreen.MAIN) }
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
            BackHandler { appScreen = AppScreen.MAIN }
            SettingsScreen(
                onBack = { appScreen = AppScreen.MAIN },
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

        // Main scaffold with bottom nav
        else -> {
            Scaffold(
                bottomBar = {
                    AnimatedVisibility(
                        visible = true,
                        enter = slideInVertically(initialOffsetY = { it }),
                        exit = slideOutVertically(targetOffsetY = { it })
                    ) {
                        NavigationBar {
                            NavigationBarItem(
                                selected = selectedTab == BottomNavTab.HOME,
                                onClick = { selectedTab = BottomNavTab.HOME },
                                icon = {
                                    Icon(
                                        imageVector = if (selectedTab == BottomNavTab.HOME)
                                            Icons.Filled.Home else Icons.Outlined.Home,
                                        contentDescription = "Home"
                                    )
                                },
                                label = { Text("Home") }
                            )
                            NavigationBarItem(
                                selected = selectedTab == BottomNavTab.VIDEOS,
                                onClick = { selectedTab = BottomNavTab.VIDEOS },
                                icon = {
                                    Icon(
                                        imageVector = if (selectedTab == BottomNavTab.VIDEOS)
                                            Icons.Filled.VideoLibrary else Icons.Outlined.VideoLibrary,
                                        contentDescription = "Videos"
                                    )
                                },
                                label = { Text("Videos") }
                            )
                        }
                    }
                }
            ) { paddingValues ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                        .consumeWindowInsets(paddingValues)
                ) {
                    when (selectedTab) {
                        BottomNavTab.HOME -> {
                            HomeScreen(
                                onVideoSelected = { video, playlist, position ->
                                    resumePositionMs = position
                                    currentVideo = video
                                    videoViewModel.playVideo(video, playlist, position)
                                },
                                onNavigateToSettings = { appScreen = AppScreen.SETTINGS }
                            )
                        }
                        BottomNavTab.VIDEOS -> {
                            VideoListScreen(
                                onVideoSelected = { video, playlist ->
                                    resumePositionMs = 0L
                                    currentVideo = video
                                    videoViewModel.playVideo(video, playlist, 0L)
                                },
                                onNavigateToSettings = { appScreen = AppScreen.SETTINGS }
                            )
                        }
                    }
                }
            }
        }
    }
}
