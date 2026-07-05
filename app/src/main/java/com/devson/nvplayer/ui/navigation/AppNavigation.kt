package com.devson.nvplayer.ui.navigation

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import com.devson.nvplayer.domain.model.DefaultScreen
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.navigation.NavController
import androidx.lifecycle.Lifecycle
import com.devson.nvplayer.ui.screen.HomeScreen
import com.devson.nvplayer.ui.screen.HistoryScreen
import com.devson.nvplayer.ui.screen.PlayerScreen
import com.devson.nvplayer.ui.screen.SearchResultsScreen
import com.devson.nvplayer.ui.screen.SettingsScreen
import com.devson.nvplayer.ui.screen.settings.AppearanceSettingsScreen
import com.devson.nvplayer.ui.screen.settings.RecycleBinScreen
import com.devson.nvplayer.ui.screen.settings.GestureSettingsScreen
import com.devson.nvplayer.ui.screen.settings.CustomHomeSettingsScreen
import com.devson.nvplayer.ui.screen.settings.PlayerInterfaceSettingsScreen
import com.devson.nvplayer.ui.screen.settings.AboutScreen
import com.devson.nvplayer.ui.screen.settings.CreditsScreen
import com.devson.nvplayer.ui.screen.settings.FolderScreen
import com.devson.nvplayer.ui.screen.videolist.VideoListScreen
import com.devson.nvplayer.ui.screen.FeedScreen
import com.devson.nvplayer.ui.screen.settings.YtdlpSettingsScreen
import com.devson.nvplayer.ui.screen.settings.MpvConfigSettingsScreen
import com.devson.nvplayer.viewmodel.HomeViewModel
import com.devson.nvplayer.viewmodel.PlayerViewModel
import com.devson.nvplayer.viewmodel.SettingsViewModel
import com.devson.nvplayer.viewmodel.VideoListViewModel
import com.devson.nvplayer.viewmodel.FileOperationsViewModel
import com.devson.nvplayer.domain.model.ViewMode
import com.devson.nvplayer.domain.model.Video
import com.devson.nvplayer.domain.model.SortField
import com.devson.nvplayer.domain.model.SortDirection
import com.devson.nvplayer.domain.model.applySort
import com.devson.nvplayer.player.model.DecoderMode
import com.devson.nvplayer.player.model.AspectMode
import com.devson.nvplayer.data.repository.MultiFingerAction
import com.devson.nvplayer.player.engine.MPVPlayerEngine
import com.devson.nvplayer.ui.screens.settings.PrivacyPolicyScreen
import com.devson.nvplayer.ui.screen.settings.ToolScreen
import com.devson.nvplayer.ui.screens.settings.MilliSecondScreen
import com.devson.nvplayer.ui.screens.settings.MediaStoreFinderScreen
import com.devson.nvplayer.ui.screen.editor.MpvHelpScreen
import com.devson.nvplayer.ui.screen.NetworkHistoryScreen

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import com.devson.nvplayer.ui.screen.settings.ControlLayoutEditorScreen

@Composable
fun AppNavigation(
    homeViewModel: HomeViewModel,
    playerViewModel: () -> PlayerViewModel,
    playerEngine: () -> MPVPlayerEngine,
    settingsViewModel: SettingsViewModel,
    videoListViewModel: VideoListViewModel,
    fileOpsViewModel: FileOperationsViewModel,
    isInPipMode: Boolean = false,
    onEnterPip: () -> Unit = {},
    initialUri: Uri? = null,
    onDeepLinkHandled: () -> Unit = {}
) {
    val navController = rememberNavController()

    // 1. Create a safe back navigation helper to prevent popping the start destination
    val safePopBackStack: () -> Unit = {
        navController.safePopBackStack()
    }

    val startDestination = remember {
        if (settingsViewModel.getInitialDefaultScreen() == DefaultScreen.VIDEO_LIST) {
            "video_list"
        } else {
            "home"
        }
    }

    LaunchedEffect(initialUri) {
        if (initialUri != null) {
            val playerVm = playerViewModel()
            val flatVideos = videoListViewModel.videosFlat.value
            val foundVideo = flatVideos.find { it.uri == initialUri.toString() }
            val dummyVideo = foundVideo ?: Video(
                uri = initialUri.toString(),
                title = initialUri.lastPathSegment?.substringBeforeLast('.') ?: "Video",
                duration = 0L,
                folderName = "",
                path = initialUri.path ?: "",
                size = 0L,
                width = 0,
                height = 0
            )
            val queueVideos = getLogicalQueue(
                video = dummyVideo,
                playlist = listOf(dummyVideo),
                flatVideos = flatVideos,
                currentViewMode = videoListViewModel.viewSettings.value.viewMode,
                sortField = videoListViewModel.viewSettings.value.sortField,
                sortDirection = videoListViewModel.viewSettings.value.sortDirection
            )
            playerVm.setQueue(queueVideos)
            playerVm.prepareVideo(initialUri, queueVideos.map { Uri.parse(it.uri) })
            navController.navigate("player") {
                launchSingleTop = true
            }
            onDeepLinkHandled()
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
                    navController.navigate("video_list") {
                        launchSingleTop = true
                        restoreState = true
                    }
                },
                onSettingsClick = {
                    navController.navigate("settings") {
                        launchSingleTop = true
                        restoreState = true
                    }
                },
                onVideoClick = { uri, playlist ->
                    val playerVm = playerViewModel()
                    val flatVideos = videoListViewModel.videosFlat.value
                    val currentVideo = flatVideos.find { it.uri == uri.toString() } ?: Video(
                        uri = uri.toString(),
                        title = uri.lastPathSegment?.substringBeforeLast('.') ?: "Video",
                        duration = 0L,
                        folderName = "",
                        path = uri.path ?: "",
                        size = 0L,
                        width = 0,
                        height = 0
                    )
                    val fallbackQueue = playlist.map { pUri ->
                        flatVideos.find { it.uri == pUri.toString() } ?: Video(
                            uri = pUri.toString(),
                            title = pUri.lastPathSegment?.substringBeforeLast('.') ?: "Video",
                            duration = 0L,
                            folderName = "",
                            path = pUri.path ?: "",
                            size = 0L,
                            width = 0,
                            height = 0
                        )
                    }
                    val queueVideos = getLogicalQueue(
                        video = currentVideo,
                        playlist = fallbackQueue,
                        flatVideos = flatVideos,
                        currentViewMode = videoListViewModel.viewSettings.value.viewMode,
                        sortField = videoListViewModel.viewSettings.value.sortField,
                        sortDirection = videoListViewModel.viewSettings.value.sortDirection
                    )
                    playerVm.setQueue(queueVideos)
                    playerVm.prepareVideo(uri, queueVideos.map { Uri.parse(it.uri) })
                    navController.navigate("player") {
                        launchSingleTop = true
                    }
                },
                onRecycleBinClick = {
                    navController.navigate("recycle_bin") {
                        launchSingleTop = true
                    }
                },
                onSearch = { query ->
                    navController.navigate("search_results/${Uri.encode(query)}") {
                        launchSingleTop = true
                    }
                },
                onBrowseClick = {
                    navController.navigate("video_list") {
                        launchSingleTop = true
                        restoreState = true
                    }
                },
                onFeedClick = {
                    videoListViewModel.setFeedVideos(null)
                    navController.navigate("feed/0") {
                        launchSingleTop = true
                    }
                },
                onSeeMoreHistoryClick = {
                    navController.navigate("history") {
                        launchSingleTop = true
                    }
                },
                onNetworkHistoryClick = {
                    navController.navigate("network_history") {
                        launchSingleTop = true
                    }
                }
            )
        }

        composable("network_history") {
            NetworkHistoryScreen(
                homeViewModel = homeViewModel,
                onBack = safePopBackStack,
                onPlayStream = { uri ->
                    val playerVm = playerViewModel()
                    val dummyVideo = Video(
                        uri = uri.toString(),
                        title = uri.lastPathSegment?.substringBeforeLast('.') ?: "Stream",
                        duration = 0L,
                        folderName = "",
                        path = uri.path ?: "",
                        size = 0L,
                        width = 0,
                        height = 0
                    )
                    playerVm.setQueue(listOf(dummyVideo))
                    playerVm.prepareVideo(uri, listOf(uri))
                    navController.navigate("player") {
                        launchSingleTop = true
                    }
                }
            )
        }

        composable("video_list") {
            VideoListScreen(
                onVideoSelected = { video, playlist, lastPositionMs ->
                    val playerVm = playerViewModel()
                    val flatVideos = videoListViewModel.videosFlat.value
                    val queueVideos = getLogicalQueue(
                        video = video,
                        playlist = playlist,
                        flatVideos = flatVideos,
                        currentViewMode = videoListViewModel.viewSettings.value.viewMode,
                        sortField = videoListViewModel.viewSettings.value.sortField,
                        sortDirection = videoListViewModel.viewSettings.value.sortDirection
                    )
                    playerVm.setQueue(queueVideos)
                    playerVm.prepareVideo(Uri.parse(video.uri), queueVideos.map { Uri.parse(it.uri) })
                    navController.navigate("player") {
                        launchSingleTop = true
                    }
                },
                onNavigateToSettings = {
                    navController.navigate("settings") {
                        launchSingleTop = true
                        restoreState = true
                    }
                },
                onBack = {
                    navController.safePopBackStack()
                },
                onNavigateToSearch = { query ->
                    navController.navigate("search_results/${Uri.encode(query)}") {
                        launchSingleTop = true
                    }
                },
                onNavigateToFeed = { startIndex ->
                    navController.navigate("feed/$startIndex") {
                        launchSingleTop = true
                    }
                },
                onPlayStream = { uri ->
                    val playerVm = playerViewModel()
                    val dummyVideo = Video(
                        uri = uri.toString(),
                        title = uri.lastPathSegment?.substringBeforeLast('.') ?: "Stream",
                        duration = 0L,
                        folderName = "",
                        path = uri.path ?: "",
                        size = 0L,
                        width = 0,
                        height = 0
                    )
                    playerVm.setQueue(listOf(dummyVideo))
                    playerVm.prepareVideo(uri, listOf(uri))
                    navController.navigate("player") {
                        launchSingleTop = true
                    }
                },
                onNetworkHistoryClick = {
                    navController.navigate("network_history") {
                        launchSingleTop = true
                    }
                },
                viewModel = videoListViewModel,
                homeViewModel = homeViewModel
            )
        }

        composable("history") {
            val allVideos by videoListViewModel.videosFlat.collectAsStateWithLifecycle()
            HistoryScreen(
                allVideos = allVideos,
                onVideoSelected = { video, playlist, lastPositionMs ->
                    val playerVm = playerViewModel()
                    val flatVideos = videoListViewModel.videosFlat.value
                    val queueVideos = getLogicalQueue(
                        video = video,
                        playlist = playlist,
                        flatVideos = flatVideos,
                        currentViewMode = videoListViewModel.viewSettings.value.viewMode,
                        sortField = videoListViewModel.viewSettings.value.sortField,
                        sortDirection = videoListViewModel.viewSettings.value.sortDirection
                    )
                    playerVm.setQueue(queueVideos)
                    playerVm.prepareVideo(Uri.parse(video.uri), queueVideos.map { Uri.parse(it.uri) })
                    navController.navigate("player") {
                        launchSingleTop = true
                    }
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
                onNavigateToAbout = { navController.navigate("about") { launchSingleTop = true } },
                onNavigateToLogs = {},
                onNavigateToPrivacyPolicy = { navController.navigate("privacy_policy") { launchSingleTop = true } },
                onNavigateToAppearance = { navController.navigate("appearance") { launchSingleTop = true } },
                onNavigateToGestures = { navController.navigate("gestures") { launchSingleTop = true } },
                onNavigateToCustomHome = { navController.navigate("custom_home") { launchSingleTop = true } },
                onNavigateToPlayerInterface = { navController.navigate("player_interface") { launchSingleTop = true } },
                onNavigateToScanFolders = { navController.navigate("folder_settings") { launchSingleTop = true } },
                onNavigateToTool = { navController.navigate("tools") { launchSingleTop = true } },
                onNavigateToRecycleBin = { navController.navigate("recycle_bin") { launchSingleTop = true } },
                onNavigateToYtdlpSettings = { navController.navigate("ytdlp_settings") { launchSingleTop = true } },
                onNavigateToMpvConfig = { navController.navigate("mpv_config") { launchSingleTop = true } },
                settingsViewModel = settingsViewModel
            )
        }

        composable("mpv_config") {
            MpvConfigSettingsScreen(
                onNavigateBack = safePopBackStack,
                onNavigateToHelp = { navController.navigate("mpv_help") { launchSingleTop = true } },
                settingsViewModel = settingsViewModel
            )
        }

        composable("mpv_help") {
            MpvHelpScreen(
                onNavigateBack = safePopBackStack
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
                onNavigateToMilliSeconds = { navController.navigate("tools_milliseconds") { launchSingleTop = true } },
                onNavigateToVideoEditor = {},
                onNavigateToMediaStoreFinder = { navController.navigate("tools_mediastore_finder") { launchSingleTop = true } }
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
                onNavigateToExplorer = {},  // In-app picker used now
                settingsViewModel = settingsViewModel
            )
        }

        composable("about") {
            AboutScreen(
                onBack = safePopBackStack,
                onNavigateToCredits = { navController.navigate("credits") { launchSingleTop = true } }
            )
        }

        composable("credits") {
            CreditsScreen(
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
                    navController.navigate("control_layout_editor") { launchSingleTop = true }
                },
                settingsViewModel = settingsViewModel
            )
        }

        composable(
            route = "control_layout_editor"
        ) {
            ControlLayoutEditorScreen(
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
                feedVideosState ?: videoListViewModel.videosFlat.value
            }
            FeedScreen(
                videos     = videos,
                startIndex = startIndex,
                engine     = playerEngine(),
                onBack     = safePopBackStack,
                onPlayVideoInPlayer = { video, playlist ->
                    val playerVm = playerViewModel()
                    playerVm.setQueue(playlist)
                    playerVm.prepareVideo(Uri.parse(video.uri), playlist.map { Uri.parse(it.uri) })
                    navController.navigate("player") { launchSingleTop = true }
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
                    val playerVm = playerViewModel()
                    val flatVideos = videoListViewModel.videosFlat.value
                    val queueVideos = getLogicalQueue(
                        video = video,
                        playlist = playlist,
                        flatVideos = flatVideos,
                        currentViewMode = videoListViewModel.viewSettings.value.viewMode,
                        sortField = videoListViewModel.viewSettings.value.sortField,
                        sortDirection = videoListViewModel.viewSettings.value.sortDirection
                    )
                    playerVm.setQueue(queueVideos)
                    playerVm.prepareVideo(Uri.parse(video.uri), queueVideos.map { Uri.parse(it.uri) })
                    navController.navigate("player") { launchSingleTop = true }
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

            val isDynamicSpeedActive by playerVm.isDynamicSpeedActive.collectAsStateWithLifecycle()
            val queueList by playerVm.queueList.collectAsStateWithLifecycle()
            val currentVideoId by playerVm.currentVideoId.collectAsStateWithLifecycle()
            val isQueueVisible by playerVm.isQueueVisible.collectAsStateWithLifecycle()

            PlayerScreen(
                isDynamicSpeedActive = isDynamicSpeedActive,
                onSetDynamicSpeedActive = { playerVm.setDynamicSpeedActive(it) },
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
                onUpdateTwoFingerAction = { settingsViewModel.updateTwoFingerAction(it) },
                onUpdateThreeFingerAction = { settingsViewModel.updateThreeFingerAction(it) },
                onUpdateOrientationMode = { settingsViewModel.updateOrientationMode(it) },
                onUpdateFullScreenMode = { settingsViewModel.updateFullScreenMode(it) },
                onUpdateAspectMode = { settingsViewModel.updateAspectMode(it) },
                onUpdateSoftButtonMode = { settingsViewModel.updateSoftButtonMode(it) },
                onUpdateControlIconSize = { settingsViewModel.updateControlIconSize(it) },
                onUpdateSeekBarStyle = { settingsViewModel.updateSeekBarStyle(it) },
                onUpdateAutoPlayEnabled = { settingsViewModel.updateAutoPlayEnabled(it) },
                onUpdateShowSeekButtons = { settingsViewModel.updateShowSeekButtons(it) },
                onUpdateShowNextPrevButtons = { settingsViewModel.updateShowNextPrevButtons(it) },
                onUpdateShowRemainingTime = { settingsViewModel.updateShowRemainingTime(it) },
                onUpdateShowBatteryClockOverlay = { settingsViewModel.updateShowBatteryClockOverlay(it) },
                onUpdatePauseWhenObstructed = { settingsViewModel.updatePauseWhenObstructed(it) },
                onUpdateKeepAwakeAlways = { settingsViewModel.updateKeepAwakeAlways(it) },
                onUpdateIsBottomLayoutEnabled = { settingsViewModel.updateIsBottomLayoutEnabled(it) },
                onUpdateShowControlGradients = { settingsViewModel.updateShowControlGradients(it) },
                onUpdateShowUpNextQueue = { settingsViewModel.updateShowUpNextQueue(it) },
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
                onUpdateBackgroundPlayEnabled = { settingsViewModel.updateBackgroundPlayEnabled(it) },
                queueList = queueList,
                currentVideoId = currentVideoId,
                isQueueVisible = isQueueVisible,
                onQueueVisibleChange = { playerVm.setQueueVisible(it) },
                onQueueVideoClick = { playerVm.selectQueueVideo(it) },
                onUpdateQueueLayoutMode = { settingsViewModel.updateQueueLayoutMode(it) }
            )
        }
    }
}

fun NavController.safePopBackStack(): Boolean {
    val currentEntry = currentBackStackEntry
    return if (previousBackStackEntry != null && 
        currentEntry != null && 
        currentEntry.lifecycle.currentState == Lifecycle.State.RESUMED
    ) {
        popBackStack()
    } else {
        false
    }
}

private fun getLogicalQueue(
    video: Video,
    playlist: List<Video>,
    flatVideos: List<Video>,
    currentViewMode: ViewMode,
    sortField: SortField,
    sortDirection: SortDirection
): List<Video> {
    return if (video.folderName.isNotEmpty()) {
        if (currentViewMode == ViewMode.FILES) {
            flatVideos.applySort(sortField, sortDirection)
        } else {
            flatVideos.filter { it.folderName == video.folderName }.applySort(sortField, sortDirection)
        }
    } else {
        playlist
    }
}