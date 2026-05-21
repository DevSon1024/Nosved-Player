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
import com.devson.nvplayer.ui.screen.SearchResultsScreen
import com.devson.nvplayer.ui.screen.SettingsScreen
import com.devson.nvplayer.ui.screen.AppearanceSettingsScreen
import com.devson.nvplayer.ui.screen.RecycleBinScreen
import com.devson.nvplayer.ui.screens.videolist.VideoListScreen
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
                onVideoClick = { uri, playlist ->
                    playerViewModel.prepareVideo(uri, playlist)
                    navController.navigate("player")
                },
                onRecycleBinClick = {
                    navController.navigate("recycle_bin")
                },
                onSearch = { query ->
                    navController.navigate("search_results/${Uri.encode(query)}")
                },
                onBrowseClick = {
                    navController.navigate("video_list")
                }
            )
        }

        composable("video_list") {
            VideoListScreen(
                onVideoSelected = { video, playlist, lastPositionMs ->
                    playerViewModel.prepareVideo(Uri.parse(video.uri), playlist.map { Uri.parse(it.uri) })
                    navController.navigate("player")
                },
                onNavigateToSettings = {
                    navController.navigate("settings")
                },
                onBack = {
                    navController.popBackStack()
                },
                onNavigateToSearch = { query ->
                    navController.navigate("search_results/${Uri.encode(query)}")
                },
                viewModel = videoListViewModel,
                homeViewModel = homeViewModel
            )
        }

        composable("recycle_bin") {
            RecycleBinScreen(
                onBack = safePopBackStack,
                fileOpsViewModel = fileOpsViewModel
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
                onVideoClick = { uri, playlist ->
                    playerViewModel.prepareVideo(uri, playlist)
                    navController.navigate("player")
                },
                onSettingsClick = {
                    navController.navigate("settings")
                },
                onSearch = { query ->
                    navController.navigate("search_results/${Uri.encode(query)}")
                }
            )
        }

        composable(
            route = "search_results/{query}",
            arguments = listOf(navArgument("query") { type = NavType.StringType })
        ) { backStackEntry ->
            val query = backStackEntry.arguments?.getString("query") ?: ""
            SearchResultsScreen(
                query = query,
                viewModel = videoListViewModel,
                homeViewModel = homeViewModel,
                onVideoSelected = { video, playlist, lastPositionMs ->
                    playerViewModel.prepareVideo(Uri.parse(video.uri), playlist.map { Uri.parse(it.uri) })
                    navController.navigate("player")
                },
                onBack = safePopBackStack
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
            val playbackSpeed by playerViewModel.playbackSpeed.collectAsState()
            val savedBrightness by playerViewModel.savedBrightness.collectAsState()
            val savedVolume by playerViewModel.savedVolume.collectAsState()
            val playbackSettings by settingsViewModel.playbackSettings.collectAsState()
            val seekBarStyle = playbackSettings.seekBarStyle
            val hasNext by playerViewModel.hasNext.collectAsState()
            val hasPrevious by playerViewModel.hasPrevious.collectAsState()

            PlayerScreen(
                playbackState = playbackState,
                isPlaying = isPlaying,
                currentPosition = currentPosition,
                duration = duration,
                currentUri = currentUri,
                videoWidth = videoWidth,
                videoHeight = videoHeight,
                videoRotation = videoRotation,
                playbackSpeed = playbackSpeed,
                savedBrightness = savedBrightness,
                savedVolume = savedVolume,
                onPlayPauseToggle = { playerViewModel.togglePlayback() },
                onSeek = { playerViewModel.seekTo(it) },
                onSetPlaybackSpeed = { playerViewModel.setPlaybackSpeed(it) },
                onCycleSubtitle = { playerViewModel.cycleSubtitle() },
                onCycleAudio = { playerViewModel.cycleAudio() },
                onBackClick = safePopBackStack, // 2. Use the safe helper
                onSurfaceReady = { playerViewModel.loadVideoIfNeeded() },
                onSaveBrightness = { playerViewModel.saveBrightness(it) },
                onSaveVolume = { playerViewModel.saveVolume(it) },
                seekBarStyle = seekBarStyle,
                hasNext = hasNext,
                hasPrevious = hasPrevious,
                onNextClick = { playerViewModel.playNext() },
                onPrevClick = { playerViewModel.playPrevious() }
            )
        }
    }
}