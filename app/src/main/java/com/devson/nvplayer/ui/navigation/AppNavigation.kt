package com.devson.nvplayer.ui.navigation

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import com.devson.nvplayer.model.DefaultScreen
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.devson.nvplayer.ui.screen.HomeScreen
import com.devson.nvplayer.ui.screen.HistoryScreen
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
import com.devson.nvplayer.ui.screen.FeedScreen
import com.devson.nvplayer.ui.screen.YtdlpSettingsScreen
import com.devson.nvplayer.viewmodel.HomeViewModel
import com.devson.nvplayer.viewmodel.PlayerViewModel
import com.devson.nvplayer.viewmodel.SettingsViewModel
import com.devson.nvplayer.viewmodel.VideoListViewModel
import com.devson.nvplayer.viewmodel.FileOperationsViewModel
import com.devson.nvplayer.model.ViewMode
import com.devson.nvplayer.player.DecoderMode
import com.devson.nvplayer.player.MPVPlayerEngine
import com.devson.nvplayer.ui.screens.settings.PrivacyPolicyScreen
import com.devson.nvplayer.ui.screens.settings.ToolScreen
import com.devson.nvplayer.ui.screens.settings.MilliSecondScreen
import com.devson.nvplayer.ui.screens.settings.MediaStoreFinderScreen

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut

@Composable
fun AppNavigation(
    homeViewModel: HomeViewModel,
    playerViewModel: () -> PlayerViewModel,
    playerEngine: () -> MPVPlayerEngine,
    settingsViewModel: SettingsViewModel,
    videoListViewModel: VideoListViewModel,
    fileOpsViewModel: FileOperationsViewModel,
    isInPipMode: Boolean = false,
    onEnterPip: () -> Unit = {}
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
                onFolderClick = { folderId ->
                    val folder = videoListViewModel.videosByFolder.value.keys.find { it.id == folderId }
                    videoListViewModel.selectFolder(folder)
                    videoListViewModel.updateViewMode(ViewMode.ALL_FOLDERS)
                    navController.navigate("video_list")
                },
                onSettingsClick = {
                    navController.navigate("settings")
                },
                onVideoClick = { uri, playlist ->
                    playerViewModel().prepareVideo(uri, playlist)
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
                },
                onFeedClick = {
                    videoListViewModel.setFeedVideos(null)
                    navController.navigate("feed/0")
                },
                onSeeMoreHistoryClick = {
                    navController.navigate("history")
                }
            )
        }

        composable("video_list") {
            VideoListScreen(
                onVideoSelected = { video, playlist, lastPositionMs ->
                    playerViewModel().prepareVideo(Uri.parse(video.uri), playlist.map { Uri.parse(it.uri) })
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
                onNavigateToFeed = { startIndex ->
                    navController.navigate("feed/$startIndex")
                },
                viewModel = videoListViewModel,
                homeViewModel = homeViewModel
            )
        }

        composable("history") {
            val videosByFolder by videoListViewModel.videosByFolder.collectAsStateWithLifecycle()
            val allVideos = remember(videosByFolder) { videosByFolder.values.flatten() }
            HistoryScreen(
                allVideos = allVideos,
                onVideoSelected = { video, playlist, lastPositionMs ->
                    playerViewModel().prepareVideo(Uri.parse(video.uri), playlist.map { Uri.parse(it.uri) })
                    navController.navigate("player")
                },
                onBack = safePopBackStack,
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
                onNavigateToPrivacyPolicy = { navController.navigate("privacy_policy") },
                onNavigateToAppearance = { navController.navigate("appearance") },
                onNavigateToGestures = { navController.navigate("gestures") },
                onNavigateToCustomHome = { navController.navigate("custom_home") },
                onNavigateToPlayerInterface = { navController.navigate("player_interface") },
                onNavigateToScanFolders = { navController.navigate("folder_settings") },
                onNavigateToTool = { navController.navigate("tools") },
                onNavigateToRecycleBin = { navController.navigate("recycle_bin") },
                onNavigateToYtdlpSettings = { navController.navigate("ytdlp_settings") },
                settingsViewModel = settingsViewModel
            )
        }

        composable("ytdlp_settings") {
            YtdlpSettingsScreen(
                onNavigateBack = safePopBackStack,
                settingsViewModel = settingsViewModel
            )
        }

        composable("privacy_policy") {
            PrivacyPolicyScreen(
                onBack = safePopBackStack
            )
        }

        composable("tools") {
            ToolScreen(
                onBack = safePopBackStack,
                onNavigateToMilliSeconds = { navController.navigate("tools_milliseconds") },
                onNavigateToVideoEditor = {},
                onNavigateToMediaStoreFinder = { navController.navigate("tools_mediastore_finder") }
            )
        }

        composable("tools_milliseconds") {
            MilliSecondScreen(
                onBack = safePopBackStack
            )
        }

        composable("tools_mediastore_finder") {
            MediaStoreFinderScreen(
                onBack = safePopBackStack
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
                onNavigateToControlEditor = {
                    navController.navigate("control_layout_editor")
                },
                settingsViewModel = settingsViewModel
            )
        }

        composable(
            route = "control_layout_editor"
        ) {
            com.devson.nvplayer.ui.screen.ControlLayoutEditorScreen(
                onNavigateBack = safePopBackStack,
                settingsViewModel = settingsViewModel
            )
        }

        //  Feed (Reels/Shorts style) 
        composable(
            route = "feed/{startIndex}",
            arguments = listOf(
                navArgument("startIndex") { type = NavType.IntType; defaultValue = 0 }
            )
        ) { backStackEntry ->
            val startIndex = backStackEntry.arguments?.getInt("startIndex") ?: 0
            val feedVideosState by videoListViewModel.feedVideos.collectAsStateWithLifecycle()
            val videos = remember(feedVideosState) {
                feedVideosState ?: videoListViewModel.videosByFolder.value.values.flatten()
            }
            FeedScreen(
                videos     = videos,
                startIndex = startIndex,
                engine     = playerEngine(),
                onBack     = safePopBackStack,
                onPlayVideoInPlayer = { video, playlist ->
                    playerViewModel().prepareVideo(Uri.parse(video.uri), playlist.map { Uri.parse(it.uri) })
                    navController.navigate("player")
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
                    playerViewModel().prepareVideo(Uri.parse(video.uri), playlist.map { Uri.parse(it.uri) })
                    navController.navigate("player")
                },
                onBack = safePopBackStack
            )
        }

        composable("player") {
            val playerVm = playerViewModel()
            val playbackState by playerVm.playbackState.collectAsStateWithLifecycle()
            val isPlaying by playerVm.isPlaying.collectAsStateWithLifecycle()
            val currentPosition by playerVm.currentPosition.collectAsStateWithLifecycle()
            val duration by playerVm.duration.collectAsStateWithLifecycle()
            val currentUri by playerVm.currentUri.collectAsStateWithLifecycle()
            val videoWidth by playerVm.videoWidth.collectAsStateWithLifecycle()
            val videoHeight by playerVm.videoHeight.collectAsStateWithLifecycle()
            val videoRotation by playerVm.videoRotation.collectAsStateWithLifecycle()
            val playbackSpeed by playerVm.playbackSpeed.collectAsStateWithLifecycle()
            val savedBrightness by playerVm.savedBrightness.collectAsStateWithLifecycle()
            val savedVolume by playerVm.savedVolume.collectAsStateWithLifecycle()
            val playbackSettings by settingsViewModel.playbackSettings.collectAsStateWithLifecycle()
            val seekBarStyle = playbackSettings.seekBarStyle
            val hasNext by playerVm.hasNext.collectAsStateWithLifecycle()
            val hasPrevious by playerVm.hasPrevious.collectAsStateWithLifecycle()
            val isHwSupported by playerVm.isHwSupported.collectAsStateWithLifecycle()

            val currentSubtitleText by playerVm.currentSubtitleText.collectAsStateWithLifecycle()
            val subtitleTracks by playerVm.subtitleTracks.collectAsStateWithLifecycle()
            val audioTracks by playerVm.audioTracks.collectAsStateWithLifecycle()
            val audioBoosterEnabled by playerVm.audioBoosterEnabled.collectAsStateWithLifecycle()
            val audioBoostVolume by playerVm.audioBoostVolume.collectAsStateWithLifecycle()
            val chapters by playerVm.chapters.collectAsStateWithLifecycle()
            val networkSpeedBytesPerSec by playerVm.networkSpeedBytesPerSec.collectAsStateWithLifecycle()
            val bufferDurationSeconds by playerVm.bufferDurationSeconds.collectAsStateWithLifecycle()
            val isNetworkStream by playerVm.isNetworkStream.collectAsStateWithLifecycle()
            val bufferedPosition by playerVm.bufferedPosition.collectAsStateWithLifecycle()

            PlayerScreen(
                networkSpeedBytesPerSec = networkSpeedBytesPerSec,
                bufferDurationSeconds = bufferDurationSeconds,
                isNetworkStream = isNetworkStream,
                bufferedPosition = bufferedPosition,
                audioBoostVolume = audioBoostVolume,
                onSetAudioBoostVolume = { playerVm.setAudioBoostVolume(it) },
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
                onPlayPauseToggle = { playerVm.togglePlayback() },
                onSeek = { pos, precise -> playerVm.seekTo(pos, precise) },
                onSetPlaybackSpeed = { playerVm.setPlaybackSpeed(it) },
                onCycleSubtitle = { playerVm.cycleSubtitle() },
                onCycleAudio = { playerVm.cycleAudio() },
                onBackClick = {
                    playerVm.savePlaybackProgress()
                    safePopBackStack()
                },
                onSurfaceReady = { playerVm.loadVideoIfNeeded() },
                onSaveBrightness = { playerVm.saveBrightness(it) },
                onSaveVolume = { playerVm.saveVolume(it) },
                seekBarStyle = seekBarStyle,
                hasNext = hasNext,
                hasPrevious = hasPrevious,
                onNextClick = { playerVm.playNext() },
                onPrevClick = { playerVm.playPrevious() },
                currentSubtitleText = currentSubtitleText,
                subtitleTracks = subtitleTracks,
                audioTracks = audioTracks,
                audioBoosterEnabled = audioBoosterEnabled,
                onToggleAudioBooster = { playerVm.toggleAudioBooster(it) },
                playbackSettings = playbackSettings,
                onSelectSubtitleTrack = { playerVm.selectSubtitleTrack(it) },
                onSelectAudioTrack = { playerVm.selectAudioTrack(it) },
                onSetSubtitleDelay = { playerVm.setSubtitleDelay(it) },
                onSeekNextSubtitle = { playerVm.seekNextSubtitle() },
                onSeekPrevSubtitle = { playerVm.seekPrevSubtitle() },
                onUpdateUseSystemCaptionStyle = { settingsViewModel.updateUseSystemCaptionStyle(it) },
                onUpdateSubtitleFont = { settingsViewModel.updateSubtitleFont(it) },
                onUpdateIsSubtitleBold = { settingsViewModel.updateIsSubtitleBold(it) },
                onUpdateForceAssSubtitleOverride = { settingsViewModel.updateForceAssSubtitleOverride(it) },
                onUpdateSubtitleTextSizeScale = { settingsViewModel.updateSubtitleTextSizeScale(it) },
                onUpdateSubtitleBgStyle = { settingsViewModel.updateSubtitleBgStyle(it) },
                onUpdateSubtitleDelay = { settingsViewModel.updateSubtitleDelay(it) },
                onUpdateSubtitleVerticalOffset = { settingsViewModel.updateSubtitleVerticalOffset(it) },
                onUpdateSubtitleGesturesEnabled = { settingsViewModel.updateSubtitleGesturesEnabled(it) },
                onUpdateCustomPlaybackSpeed = { playerVm.updateCustomPlaybackSpeed(it) },
                onUpdateTapAndHoldSpeed = { playerVm.updateTapAndHoldSpeed(it) },
                onUpdateDoubleTapSeekDuration = { playerVm.updateDoubleTapSeekDuration(it) },
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
                onUpdateEnhanceMode = { playerVm.updateEnhanceMode(it) },
                onUpdateEnhanceSaturation = { playerVm.updateEnhanceSaturation(it) },
                onUpdateEnhanceContrast = { playerVm.updateEnhanceContrast(it) },
                onUpdateEnhanceBrightness = { playerVm.updateEnhanceBrightness(it) },
                onUpdateEnhanceGamma = { playerVm.updateEnhanceGamma(it) },
                onUpdateEnhanceHue = { playerVm.updateEnhanceHue(it) },
                onTakeVideoScreenshot = { playerVm.takeVideoScreenshot() },
                chapters = chapters,
                onSelectChapter = { playerVm.selectChapter(it) },
                currentDecoder = if (!isHwSupported) DecoderMode.SW.displayName else playbackSettings.decoderMode.displayName,
                isHwSupported = isHwSupported,
                onUpdateDecoderMode = { playerVm.updateDecoderMode(it) },
                onCycleAspectMode = { playerVm.cycleAspectMode() },
                isInPipMode = isInPipMode,
                onEnterPip = onEnterPip,
                onUpdateBackgroundPlayEnabled = { settingsViewModel.updateBackgroundPlayEnabled(it) }
            )
        }
    }
}