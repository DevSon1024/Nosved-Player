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
import com.devson.nvplayer.viewmodel.FolderViewModel
import com.devson.nvplayer.viewmodel.HomeViewModel
import com.devson.nvplayer.viewmodel.PlayerViewModel

@Composable
fun AppNavigation(
    homeViewModel: HomeViewModel,
    folderViewModel: FolderViewModel,
    playerViewModel: PlayerViewModel
) {
    val navController = rememberNavController()

    NavHost(navController = navController, startDestination = "home") {
        composable("home") {
            HomeScreen(
                viewModel = homeViewModel,
                onFolderClick = { folderName ->
                    navController.navigate("folder/${Uri.encode(folderName)}")
                }
            )
        }

        composable(
            route = "folder/{folderName}",
            arguments = listOf(navArgument("folderName") { type = NavType.StringType })
        ) { backStackEntry ->
            val folderName = backStackEntry.arguments?.getString("folderName") ?: ""
            FolderScreen(
                folderName = folderName,
                viewModel = folderViewModel,
                onBackClick = { navController.popBackStack() },
                onVideoClick = { uri ->
                    playerViewModel.loadVideo(uri)
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

            PlayerScreen(
                playbackState = playbackState,
                isPlaying = isPlaying,
                currentPosition = currentPosition,
                duration = duration,
                currentUri = currentUri,
                onPlayPauseToggle = { playerViewModel.togglePlayback() },
                onSeek = { playerViewModel.seekTo(it) },
                onSetPlaybackSpeed = { playerViewModel.setPlaybackSpeed(it) },
                onCycleSubtitle = { playerViewModel.cycleSubtitle() },
                onCycleAudio = { playerViewModel.cycleAudio() },
                onBackClick = {
                    navController.popBackStack()
                }
            )
        }
    }
}
