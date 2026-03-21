package com.devson.nosvedplayer.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.WindowInsets
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

    //  Hoisted state from VideoListScreen for dynamic bottom nav visibility 
    var isInsideFolder by remember { mutableStateOf(false) }
    var isSelectionActive by remember { mutableStateOf(false) }

    // Bottom nav is hidden when user drills into a folder OR activates selection mode
    val showBottomNav = !isInsideFolder && !isSelectionActive

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
                // contentWindowInsets=WindowInsets(0) so the outer Scaffold does NOT consume
                // status bar insets — inner screens handle them via their own TopAppBar's
                // windowInsets = TopAppBarDefaults.windowInsets (= WindowInsets.statusBars)
                contentWindowInsets = WindowInsets(0, 0, 0, 0),
                bottomBar = {
                    if (showBottomNav) {
                        NavigationBar {
                            NavigationBarItem(
                                selected = selectedTab == BottomNavTab.HOME,
                                onClick = { selectedTab = BottomNavTab.HOME },
                                // alwaysShowLabel = false,
                                icon = {
                                    Icon(
                                        imageVector = if (selectedTab == BottomNavTab.HOME)
                                            Icons.Filled.Home else Icons.Outlined.Home,
                                        contentDescription = "Home"
                                    )
                                },
                                // label = { Text("Home") }
                            )
                            NavigationBarItem(
                                selected = selectedTab == BottomNavTab.VIDEOS,
                                onClick = { selectedTab = BottomNavTab.VIDEOS },
                                // alwaysShowLabel = false,
                                icon = {
                                    Icon(
                                        imageVector = if (selectedTab == BottomNavTab.VIDEOS)
                                            Icons.Filled.VideoLibrary else Icons.Outlined.VideoLibrary,
                                        contentDescription = "Videos"
                                    )
                                },
                                // label = { Text("Videos") }
                            )
                        }
                    }
                }
            ) { paddingValues ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        // Only apply BOTTOM padding (= NavigationBar height + system nav height).
                        // Inner screens apply TOP (status bar) padding via their own TopAppBar.
                        .padding(bottom = paddingValues.calculateBottomPadding())
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
                                onNavigateToSettings = { appScreen = AppScreen.SETTINGS },
                                // Notify MainScreen so it can hide/show the bottom nav
                                onFolderStateChange = { isInsideFolder = it },
                                onSelectionStateChange = { isSelectionActive = it }
                            )
                        }
                    }
                }
            }
        }
    }
}
