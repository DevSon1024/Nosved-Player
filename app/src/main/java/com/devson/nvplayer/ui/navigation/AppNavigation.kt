package com.devson.nvplayer.ui.navigation

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import com.devson.nvplayer.model.DefaultScreen
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.devson.nvplayer.ui.screen.HomeScreen
import com.devson.nvplayer.ui.screen.PlayerScreen
import com.devson.nvplayer.ui.screen.SearchResultsScreen
import com.devson.nvplayer.ui.screen.SettingsScreen
import com.devson.nvplayer.ui.screen.AppearanceSettingsScreen
import com.devson.nvplayer.ui.screen.RecycleBinScreen
import com.devson.nvplayer.ui.screen.GestureSettingsScreen
import com.devson.nvplayer.ui.screen.CustomHomeSettingsScreen
import com.devson.nvplayer.ui.screen.PlayerInterfaceSettingsScreen
import com.devson.nvplayer.ui.screen.AboutScreen
import com.devson.nvplayer.ui.screen.FolderScreen
import com.devson.nvplayer.ui.screen.StorageExplorerScreen
import com.devson.nvplayer.ui.screen.videolist.VideoListScreen
import com.devson.nvplayer.viewmodel.HomeViewModel
import com.devson.nvplayer.viewmodel.PlayerViewModel
import com.devson.nvplayer.viewmodel.SettingsViewModel
import com.devson.nvplayer.viewmodel.VideoListViewModel
import com.devson.nvplayer.viewmodel.FileOperationsViewModel
import com.devson.nvplayer.model.ViewMode

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut

@Composable
fun AppNavigation(
    homeViewModel: HomeViewModel,
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

    val startDestination = remember {
        if (settingsViewModel.getInitialDefaultScreen() == DefaultScreen.VIDEO_LIST) {
            "video_list"
        } else {
            "home"
        }
    }

    NavHost(
        navController = navController,
        startDestination = startDestination,
        enterTransition = {
            slideIntoContainer(
                towards = AnimatedContentTransitionScope.SlideDirection.Left,
                animationSpec = tween(350, easing = FastOutSlowInEasing)
            ) + fadeIn(animationSpec = tween(350))
        },
        exitTransition = {
            slideOutOfContainer(
                towards = AnimatedContentTransitionScope.SlideDirection.Left,
                animationSpec = tween(350, easing = FastOutSlowInEasing)
            ) + fadeOut(animationSpec = tween(350))
        },
        popEnterTransition = {
            slideIntoContainer(
                towards = AnimatedContentTransitionScope.SlideDirection.Right,
                animationSpec = tween(350, easing = FastOutSlowInEasing)
            ) + fadeIn(animationSpec = tween(350))
        },
        popExitTransition = {
            slideOutOfContainer(
                towards = AnimatedContentTransitionScope.SlideDirection.Right,
                animationSpec = tween(350, easing = FastOutSlowInEasing)
            ) + fadeOut(animationSpec = tween(350))
        }
    ) {
        composable("home") {
            HomeScreen(
                viewModel = videoListViewModel,
                fileOpsViewModel = fileOpsViewModel,
                homeViewModel = homeViewModel,
                onFolderClick = { folderName ->
                    val folder = videoListViewModel.videosByFolder.value.keys.find { it.name == folderName }
                    videoListViewModel.selectFolder(folder)
                    videoListViewModel.updateViewMode(ViewMode.ALL_FOLDERS)
                    navController.navigate("video_list")
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
                onNavigateToAbout = { navController.navigate("about") },
                onNavigateToLogs = {},
                onNavigateToPrivacyPolicy = {},
                onNavigateToAppearance = { navController.navigate("appearance") },
                onNavigateToGestures = { navController.navigate("gestures") },
                onNavigateToCustomHome = { navController.navigate("custom_home") },
                onNavigateToPlayerInterface = { navController.navigate("player_interface") },
                onNavigateToScanFolders = { navController.navigate("folder_settings") },
                settingsViewModel = settingsViewModel
            )
        }

        composable("folder_settings") {
            FolderScreen(
                onNavigateBack = safePopBackStack,
                onNavigateToExplorer = { navController.navigate("storage_explorer_blacklist") },
                settingsViewModel = settingsViewModel
            )
        }

        composable("storage_explorer_blacklist") {
            StorageExplorerScreen(
                isBlacklistMode = true,
                onFoldersBlacklisted = { selectedPaths ->
                    settingsViewModel.addToBlacklist(selectedPaths)
                    navController.popBackStack()
                },
                onCancel = {
                    navController.popBackStack()
                }
            )
        }

        composable("about") {
            AboutScreen(
                onBack = safePopBackStack
            )
        }

        composable("appearance") {
            AppearanceSettingsScreen(
                onNavigateBack = safePopBackStack, // 2. Use the safe helper
                settingsViewModel = settingsViewModel
            )
        }

        composable("gestures") {
            GestureSettingsScreen(
                onNavigateBack = safePopBackStack,
                settingsViewModel = settingsViewModel
            )
        }

        composable("custom_home") {
            CustomHomeSettingsScreen(
                onNavigateBack = safePopBackStack,
                settingsViewModel = settingsViewModel
            )
        }

        composable("player_interface") {
            PlayerInterfaceSettingsScreen(
                onNavigateBack = safePopBackStack,
                settingsViewModel = settingsViewModel
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

            val currentSubtitleText by playerViewModel.currentSubtitleText.collectAsState()
            val subtitleTracks by playerViewModel.subtitleTracks.collectAsState()
            val audioTracks by playerViewModel.audioTracks.collectAsState()
            val audioBoosterEnabled by playerViewModel.audioBoosterEnabled.collectAsState()
            val chapters by playerViewModel.chapters.collectAsState()

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
                onSeek = { pos, precise -> playerViewModel.seekTo(pos, precise) },
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
                onPrevClick = { playerViewModel.playPrevious() },
                currentSubtitleText = currentSubtitleText,
                subtitleTracks = subtitleTracks,
                audioTracks = audioTracks,
                audioBoosterEnabled = audioBoosterEnabled,
                onToggleAudioBooster = { playerViewModel.toggleAudioBooster(it) },
                playbackSettings = playbackSettings,
                onSelectSubtitleTrack = { playerViewModel.selectSubtitleTrack(it) },
                onSelectAudioTrack = { playerViewModel.selectAudioTrack(it) },
                onSetSubtitleDelay = { playerViewModel.setSubtitleDelay(it) },
                onSeekNextSubtitle = { playerViewModel.seekNextSubtitle() },
                onSeekPrevSubtitle = { playerViewModel.seekPrevSubtitle() },
                onUpdateUseSystemCaptionStyle = { settingsViewModel.updateUseSystemCaptionStyle(it) },
                onUpdateSubtitleFont = { settingsViewModel.updateSubtitleFont(it) },
                onUpdateIsSubtitleBold = { settingsViewModel.updateIsSubtitleBold(it) },
                onUpdateForceAssSubtitleOverride = { settingsViewModel.updateForceAssSubtitleOverride(it) },
                onUpdateSubtitleTextSizeScale = { settingsViewModel.updateSubtitleTextSizeScale(it) },
                onUpdateSubtitleBgStyle = { settingsViewModel.updateSubtitleBgStyle(it) },
                onUpdateSubtitleDelay = { settingsViewModel.updateSubtitleDelay(it) },
                onUpdateSubtitleVerticalOffset = { settingsViewModel.updateSubtitleVerticalOffset(it) },
                onUpdateSubtitleGesturesEnabled = { settingsViewModel.updateSubtitleGesturesEnabled(it) },
                onUpdateCustomPlaybackSpeed = { playerViewModel.updateCustomPlaybackSpeed(it) },
                onUpdateTapAndHoldSpeed = { playerViewModel.updateTapAndHoldSpeed(it) },
                onUpdateDoubleTapSeekDuration = { playerViewModel.updateDoubleTapSeekDuration(it) },
                onUpdateLongPressEnabled = { settingsViewModel.updateLongPressEnabled(it) },
                onUpdateLongPressSpeed = { settingsViewModel.updateLongPressSpeed(it) },
                onUpdateDoubleTapAction = { settingsViewModel.updateDoubleTapAction(it) },
                onUpdateOrientationMode = { settingsViewModel.updateOrientationMode(it) },
                onUpdateFullScreenMode = { settingsViewModel.updateFullScreenMode(it) },
                onUpdateSoftButtonMode = { settingsViewModel.updateSoftButtonMode(it) },
                onUpdateControlIconSize = { settingsViewModel.updateControlIconSize(it) },
                onUpdateSeekBarStyle = { settingsViewModel.updateSeekBarStyle(it) },
                onUpdateAutoPlayEnabled = { settingsViewModel.updateAutoPlayEnabled(it) },
                onUpdateShowSeekButtons = { settingsViewModel.updateShowSeekButtons(it) },
                onUpdateShowNextPrevButtons = { settingsViewModel.updateShowNextPrevButtons(it) },
                onUpdateShowElapsedTimeOverlay = { settingsViewModel.updateShowElapsedTimeOverlay(it) },
                onUpdateShowRemainingTime = { settingsViewModel.updateShowRemainingTime(it) },
                onUpdateShowBatteryClockOverlay = { settingsViewModel.updateShowBatteryClockOverlay(it) },
                onUpdateShowScreenRotationButton = { settingsViewModel.updateShowScreenRotationButton(it) },
                onUpdatePauseWhenObstructed = { settingsViewModel.updatePauseWhenObstructed(it) },
                onUpdateKeepAwakeAlways = { settingsViewModel.updateKeepAwakeAlways(it) },
                onTakeVideoScreenshot = { playerViewModel.takeVideoScreenshot() },
                chapters = chapters,
                onSelectChapter = { playerViewModel.selectChapter(it) },
                currentDecoder = playbackSettings.decoderMode.displayName,
                onCycleDecoder = { playerViewModel.cycleDecoder() },
                onUpdateDecoderMode = { settingsViewModel.updateDecoderMode(it) }
            )
        }
    }
}