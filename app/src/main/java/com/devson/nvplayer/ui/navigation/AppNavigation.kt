package com.devson.nvplayer.ui.navigation

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.devson.nvplayer.ui.screen.FolderScreen
import com.devson.nvplayer.ui.screen.HomeScreen
import com.devson.nvplayer.ui.screen.PlayerScreen
import com.devson.nvplayer.ui.screen.SettingsScreen
import com.devson.nvplayer.ui.screen.AppearanceSettingsScreen
import com.devson.nvplayer.viewmodel.HomeViewModel
import com.devson.nvplayer.viewmodel.FolderViewModel
import com.devson.nvplayer.viewmodel.PlayerViewModel
import com.devson.nvplayer.viewmodel.SettingsViewModel
import com.devson.nvplayer.viewmodel.VideoListViewModel
import com.devson.nvplayer.viewmodel.FileOperationsViewModel

@Composable
fun AppNavigation(
    homeViewModel: HomeViewModel,
    folderViewModel: FolderViewModel,
    playerViewModel: PlayerViewModel,
    settingsViewModel: SettingsViewModel,
    videoListViewModel: VideoListViewModel,
    fileOpsViewModel: FileOperationsViewModel
) {
    val navController = rememberNavController()

    // 1. Create a safe back navigation helper to prevent popping the start destination
    val safePopBackStack: () -> Unit = {
        if (navController.previousBackStackEntry != null) {
            navController.popBackStack()
        }
    }

    NavHost(navController = navController, startDestination = "home") {
        composable("home") {
            HomeScreen(
                viewModel = videoListViewModel,
                fileOpsViewModel = fileOpsViewModel,
                homeViewModel = homeViewModel,
                onFolderClick = { folderName ->
                    navController.navigate("folder/${Uri.encode(folderName)}")
                },
                onSettingsClick = {
                    navController.navigate("settings")
                },
                onVideoClick = { uri ->
                    playerViewModel.prepareVideo(uri)
                    navController.navigate("player")
                }
            )
        }

        composable("settings") {
            SettingsScreen(
                onBack = safePopBackStack, // 2. Use the safe helper
                onNavigateToAbout = {},
                onNavigateToLogs = {},
                onNavigateToPrivacyPolicy = {},
                onNavigateToAppearance = { navController.navigate("appearance") },
                settingsViewModel = settingsViewModel
            )
        }

        composable("appearance") {
            AppearanceSettingsScreen(
                onNavigateBack = safePopBackStack, // 2. Use the safe helper
                settingsViewModel = settingsViewModel
            )
        }

        composable(
            route = "folder/{folderName}",
            arguments = listOf(navArgument("folderName") { type = NavType.StringType })
        ) { backStackEntry ->
            val folderName = backStackEntry.arguments?.getString("folderName") ?: ""
            FolderScreen(
                folderName = folderName,
                viewModel = videoListViewModel,
                fileOpsViewModel = fileOpsViewModel,
                homeViewModel = homeViewModel,
                onBackClick = safePopBackStack, // 2. Use the safe helper
                onVideoClick = { uri ->
                    playerViewModel.prepareVideo(uri)
                    navController.navigate("player")
                }
            )
        }

        composable("player") {
            val playbackState by playerViewModel.playbackState.collectAsState()
            val isPlaying by playerViewModel.isPlaying.collectAsState()
            val currentPosition by playerViewModel.currentPosition.collectAsState()
            val duration by playerViewModel.duration.collectAsState()
            val currentUri by playerViewModel.currentUri.collectAsState()
            val videoWidth by playerViewModel.videoWidth.collectAsState()
            val videoHeight by playerViewModel.videoHeight.collectAsState()
            val videoRotation by playerViewModel.videoRotation.collectAsState()

            PlayerScreen(
                playbackState = playbackState,
                isPlaying = isPlaying,
                currentPosition = currentPosition,
                duration = duration,
                currentUri = currentUri,
                videoWidth = videoWidth,
                videoHeight = videoHeight,
                videoRotation = videoRotation,
                onPlayPauseToggle = { playerViewModel.togglePlayback() },
                onSeek = { playerViewModel.seekTo(it) },
                onSetPlaybackSpeed = { playerViewModel.setPlaybackSpeed(it) },
                onCycleSubtitle = { playerViewModel.cycleSubtitle() },
                onCycleAudio = { playerViewModel.cycleAudio() },
                onBackClick = safePopBackStack, // 2. Use the safe helper
                onSurfaceReady = { playerViewModel.loadVideoIfNeeded() }
            )
        }
    }
}