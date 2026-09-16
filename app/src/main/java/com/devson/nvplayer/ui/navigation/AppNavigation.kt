package com.devson.nvplayer.ui.navigation

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import com.devson.nvplayer.domain.model.DefaultScreen
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.currentBackStackEntryAsState
import com.devson.nvplayer.ui.navigation.components.CapsuleNavigationBar
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.navigation.NavController
import androidx.lifecycle.Lifecycle
import com.devson.nvplayer.ui.screen.HistoryScreen
import com.devson.nvplayer.ui.screen.PlayerScreen
import com.devson.nvplayer.ui.screen.SearchResultsScreen
import com.devson.nvplayer.ui.screen.SettingsScreen
import com.devson.nvplayer.ui.screen.ProfileScreen
import com.devson.nvplayer.ui.screen.settings.AppearanceSettingsScreen
import com.devson.nvplayer.ui.screen.settings.RecycleBinScreen
import com.devson.nvplayer.ui.screen.settings.GestureSettingsScreen
import com.devson.nvplayer.ui.screen.settings.CustomProfileSettingsScreen
import com.devson.nvplayer.ui.screen.settings.StorageAnalyzeScreen
import com.devson.nvplayer.ui.screen.settings.PlayerInterfaceSettingsScreen
import com.devson.nvplayer.ui.screen.settings.AboutScreen
import com.devson.nvplayer.ui.screen.settings.CreditsScreen
import com.devson.nvplayer.ui.screen.settings.FolderScreen
import com.devson.nvplayer.ui.screen.videolist.VideoListScreen
import com.devson.nvplayer.ui.screen.FeedScreen
import com.devson.nvplayer.ui.screen.settings.YtdlpSettingsScreen
import com.devson.nvplayer.ui.screen.settings.MpvConfigSettingsScreen
import com.devson.nvplayer.ui.screen.library.LibraryHomeScreen
import com.devson.nvplayer.ui.screen.library.SeriesDetailScreen
import com.devson.nvplayer.ui.screen.vault.VaultScreen
import com.devson.nvplayer.data.database.AppDatabase
import com.devson.nvplayer.data.security.VaultFileManager
import com.devson.nvplayer.data.security.VaultSecurityManager
import com.devson.nvplayer.viewmodel.VaultAuthViewModel
import com.devson.nvplayer.viewmodel.VaultGalleryViewModel
import com.devson.nvplayer.viewmodel.LibraryViewModel
import com.devson.nvplayer.viewmodel.HomeViewModel
import com.devson.nvplayer.viewmodel.PlayerViewModel
import com.devson.nvplayer.viewmodel.PreFetchedVideoMetadata
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
import com.devson.nvplayer.ui.screen.settings.LanguageSettingsScreen
import com.devson.nvplayer.ui.screen.editor.MpvHelpScreen
import com.devson.nvplayer.ui.screen.NetworkHistoryScreen

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.EaseOutCubic
import androidx.compose.animation.core.EaseInCubic
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
    onDeepLinkHandled: () -> Unit = {},
    shortcutDestination: String? = null,
    onShortcutHandled: () -> Unit = {}
) {
    val navController = rememberNavController()

    // 1. Create a safe back navigation helper to prevent popping the start destination
    val safePopBackStack: () -> Unit = {
        navController.safePopBackStack()
    }

    val context = androidx.compose.ui.platform.LocalContext.current
    val libraryViewModel: LibraryViewModel = androidx.lifecycle.viewmodel.compose.viewModel(
        factory = LibraryViewModel.Factory(
            application = context.applicationContext as android.app.Application,
            videoListViewModel = videoListViewModel
        )
    )

    val database = remember { AppDatabase.getDatabase(context) }
    val vaultSecurityManager = remember { VaultSecurityManager(context) }
    val vaultFileManager = remember { VaultFileManager(context, database.vaultDao()) }
    val vaultAuthViewModel: VaultAuthViewModel = androidx.lifecycle.viewmodel.compose.viewModel(
        factory = VaultAuthViewModel.Factory(
            application = context.applicationContext as android.app.Application,
            securityManager = vaultSecurityManager,
            vaultFileManager = vaultFileManager
        )
    )
    val vaultGalleryViewModel: VaultGalleryViewModel = androidx.lifecycle.viewmodel.compose.viewModel(
        factory = VaultGalleryViewModel.Factory(
            application = context.applicationContext as android.app.Application,
            vaultDao = database.vaultDao(),
            vaultFileManager = vaultFileManager,
            vaultSecurityManager = vaultSecurityManager
        )
    )

    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    val isSelectionActive by videoListViewModel.isSelectionActive.collectAsStateWithLifecycle()
    val topLevelRoutes = setOf("library", "video_list", "vault", "settings")
    val topLevelOrder = remember { listOf("library", "video_list", "vault", "settings") }
    val showBottomBar = currentRoute in topLevelRoutes && !isSelectionActive

    val initialScreen = remember { settingsViewModel.getInitialDefaultScreen() }
    val startDestination = remember(initialScreen) {
        when (initialScreen) {
            DefaultScreen.VIDEO_LIST, DefaultScreen.FOLDERS -> "video_list"
            DefaultScreen.VAULT -> "vault"
            DefaultScreen.SETTINGS -> "settings"
            else -> "library"
        }
    }

    val navigateToTopLevel: (String) -> Unit = { targetRoute ->
        if (currentRoute == targetRoute) {
            if (targetRoute == "video_list") {
                videoListViewModel.selectFolder(null)
                videoListViewModel.setSelectionActive(false)
                if (videoListViewModel.viewSettings.value.viewMode == ViewMode.FOLDERS) {
                    videoListViewModel.resetExplorerToRoot()
                }
            } else if (targetRoute == "settings") {
                navController.popBackStack("settings", inclusive = false)
            }
        } else {
            if (targetRoute == "video_list") {
                videoListViewModel.selectFolder(null)
                videoListViewModel.setSelectionActive(false)
            }
            if (targetRoute == "settings") {
                navController.popBackStack("settings", inclusive = false)
            }
            navController.navigate(targetRoute) {
                popUpTo(navController.graph.findStartDestination().id) {
                    saveState = true
                }
                launchSingleTop = true
                restoreState = (targetRoute != "settings")
            }
        }
    }

    BackHandler(enabled = currentRoute in topLevelRoutes && currentRoute != startDestination) {
        navigateToTopLevel(startDestination)
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

    LaunchedEffect(shortcutDestination) {
        if (shortcutDestination != null) {
            if (shortcutDestination in topLevelRoutes) {
                navigateToTopLevel(shortcutDestination)
            } else {
                navController.navigate(shortcutDestination) {
                    launchSingleTop = true
                }
            }
            onShortcutHandled()
        }
    }

    Box(
        modifier = Modifier.fillMaxSize()
    ) {
        NavHost(
            navController = navController,
            startDestination = startDestination,
            modifier = Modifier.fillMaxSize(),
        enterTransition = {
            val initialRoute = initialState.destination.route
            val targetRoute = targetState.destination.route
            val initialIndex = topLevelOrder.indexOf(initialRoute)
            val targetIndex = topLevelOrder.indexOf(targetRoute)
            val towards = if (initialIndex != -1 && targetIndex != -1) {
                if (targetIndex > initialIndex) {
                    AnimatedContentTransitionScope.SlideDirection.Left
                } else {
                    AnimatedContentTransitionScope.SlideDirection.Right
                }
            } else {
                AnimatedContentTransitionScope.SlideDirection.Left
            }
            slideIntoContainer(
                towards = towards,
                animationSpec = tween(400, easing = EaseOutCubic)
            ) + fadeIn(animationSpec = tween(300))
        },
        exitTransition = {
            val initialRoute = initialState.destination.route
            val targetRoute = targetState.destination.route
            val initialIndex = topLevelOrder.indexOf(initialRoute)
            val targetIndex = topLevelOrder.indexOf(targetRoute)
            val towards = if (initialIndex != -1 && targetIndex != -1) {
                if (targetIndex > initialIndex) {
                    AnimatedContentTransitionScope.SlideDirection.Left
                } else {
                    AnimatedContentTransitionScope.SlideDirection.Right
                }
            } else {
                AnimatedContentTransitionScope.SlideDirection.Left
            }
            slideOutOfContainer(
                towards = towards,
                animationSpec = tween(400, easing = EaseInCubic)
            ) + fadeOut(animationSpec = tween(300))
        },
        popEnterTransition = {
            val initialRoute = initialState.destination.route
            val targetRoute = targetState.destination.route
            val initialIndex = topLevelOrder.indexOf(initialRoute)
            val targetIndex = topLevelOrder.indexOf(targetRoute)
            val towards = if (initialIndex != -1 && targetIndex != -1) {
                if (targetIndex > initialIndex) {
                    AnimatedContentTransitionScope.SlideDirection.Left
                } else {
                    AnimatedContentTransitionScope.SlideDirection.Right
                }
            } else {
                AnimatedContentTransitionScope.SlideDirection.Right
            }
            slideIntoContainer(
                towards = towards,
                animationSpec = tween(400, easing = EaseOutCubic)
            ) + fadeIn(animationSpec = tween(300))
        },
        popExitTransition = {
            val initialRoute = initialState.destination.route
            val targetRoute = targetState.destination.route
            val initialIndex = topLevelOrder.indexOf(initialRoute)
            val targetIndex = topLevelOrder.indexOf(targetRoute)
            val towards = if (initialIndex != -1 && targetIndex != -1) {
                if (targetIndex > initialIndex) {
                    AnimatedContentTransitionScope.SlideDirection.Left
                } else {
                    AnimatedContentTransitionScope.SlideDirection.Right
                }
            } else {
                AnimatedContentTransitionScope.SlideDirection.Right
            }
            slideOutOfContainer(
                towards = towards,
                animationSpec = tween(400, easing = EaseInCubic)
            ) + fadeOut(animationSpec = tween(300))
        }
    ) {
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
                    navigateToTopLevel("settings")
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
                homeViewModel = homeViewModel,
                vaultGalleryViewModel = vaultGalleryViewModel,
                vaultSecurityManager = vaultSecurityManager,
                onNavigateToVault = {
                    navigateToTopLevel("vault")
                }
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
                onBack = {
                    if (startDestination != "settings") {
                        navigateToTopLevel(startDestination)
                    } else {
                        safePopBackStack()
                    }
                },
                onNavigateToAbout = { navController.navigate("about") { launchSingleTop = true } },
                onNavigateToLogs = {},
                onNavigateToPrivacyPolicy = { navController.navigate("privacy_policy") { launchSingleTop = true } },
                onNavigateToAppearance = { navController.navigate("appearance") { launchSingleTop = true } },
                onNavigateToGestures = { navController.navigate("gestures") { launchSingleTop = true } },
                onNavigateToProfile = { navController.navigate("profile") { launchSingleTop = true } },
                onNavigateToCustomHome = { navController.navigate("custom_profile") { launchSingleTop = true } },
                onNavigateToStorageAnalyzer = { navController.navigate("storage_analyzer") { launchSingleTop = true } },
                onNavigateToVault = { navigateToTopLevel("vault") },
                onNavigateToPlayerInterface = { navController.navigate("player_interface") { launchSingleTop = true } },
                onNavigateToScanFolders = { navController.navigate("folder_settings") { launchSingleTop = true } },
                onNavigateToTool = { navController.navigate("tools") { launchSingleTop = true } },
                onNavigateToRecycleBin = { navController.navigate("recycle_bin") { launchSingleTop = true } },
                onNavigateToYtdlpSettings = { navController.navigate("ytdlp_settings") { launchSingleTop = true } },
                onNavigateToMpvConfig = { navController.navigate("mpv_config") { launchSingleTop = true } },
                onNavigateToLanguageSettings = { navController.navigate("language_settings") { launchSingleTop = true } },
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

        composable("language_settings") {
            LanguageSettingsScreen(
                onNavigateBack = safePopBackStack,
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
                onNavigateToMediaStoreFinder = { navController.navigate("tools_mediastore_finder") { launchSingleTop = true } },
                onNavigateToFrameExtractor = { navController.navigate("tools_frame_extractor") { launchSingleTop = true } },
                onNavigateToSubtitleExtractor = { navController.navigate("tools_subtitle_extractor") { launchSingleTop = true } }
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

        composable("tools_frame_extractor") {
            com.devson.nvplayer.ui.screen.tools.FrameExtractionScreen(
                onBack = safePopBackStack
            )
        }

        composable("tools_subtitle_extractor") {
            com.devson.nvplayer.ui.screen.tools.SubtitleExtractionScreen(
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

        composable("profile") {
            ProfileScreen(
                viewModel = videoListViewModel,
                fileOpsViewModel = fileOpsViewModel,
                homeViewModel = homeViewModel,
                onBack = safePopBackStack,
                onCustomizeClick = { navController.navigate("custom_profile") { launchSingleTop = true } },
                onFolderClick = { folderId ->
                    val folder = videoListViewModel.videosByFolder.value.keys.find { it.id == folderId }
                    videoListViewModel.selectFolder(folder)
                    videoListViewModel.updateViewMode(ViewMode.ALL_FOLDERS)
                    navController.popBackStack("profile", inclusive = true)
                    navigateToTopLevel("video_list")
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
                    navController.popBackStack("profile", inclusive = true)
                    navigateToTopLevel("video_list")
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
                onStorageAnalyzerClick = {
                    navController.navigate("storage_analyzer") {
                        launchSingleTop = true
                    }
                },
                onVaultClick = {
                    navController.popBackStack("profile", inclusive = true)
                    navigateToTopLevel("vault")
                }
            )
        }

        composable("custom_profile") {
            CustomProfileSettingsScreen(
                onNavigateBack = safePopBackStack,
                settingsViewModel = settingsViewModel
            )
        }

        composable("custom_home") {
            CustomProfileSettingsScreen(
                onNavigateBack = safePopBackStack,
                settingsViewModel = settingsViewModel
            )
        }

        composable("storage_analyzer") {
            StorageAnalyzeScreen(
                onBack = safePopBackStack,
                videoListViewModel = videoListViewModel,
                onNavigateToRecycleBin = {
                    navController.navigate("recycle_bin") {
                        launchSingleTop = true
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
                    val queueVideos = getLogicalQueue(
                        video = currentVideo,
                        playlist = playlist.map { pUri -> flatVideos.find { it.uri == pUri.toString() } ?: currentVideo },
                        flatVideos = flatVideos,
                        currentViewMode = videoListViewModel.viewSettings.value.viewMode,
                        sortField = videoListViewModel.viewSettings.value.sortField,
                        sortDirection = videoListViewModel.viewSettings.value.sortDirection
                    )
                    playerVm.setQueue(queueVideos)
                    playerVm.prepareVideo(uri, playlist)
                    navController.navigate("player") {
                        launchSingleTop = true
                    }
                }
            )
        }

        composable("library") {
            LibraryHomeScreen(
                viewModel = libraryViewModel,
                onMediaClick = { item ->
                    val playerVm = playerViewModel()
                    val flatVideos = videoListViewModel.videosFlat.value
                    val currentVideo = flatVideos.find { it.uri == item.videoUri } ?: Video(
                        uri = item.videoUri,
                        title = item.title,
                        duration = item.durationMs,
                        folderName = "",
                        path = item.videoUri,
                        size = 0L,
                        width = 0,
                        height = 0
                    )
                    playerVm.setQueue(listOf(currentVideo))
                    playerVm.prepareVideo(Uri.parse(item.videoUri), listOf(Uri.parse(item.videoUri)))
                    navController.navigate("player") {
                        launchSingleTop = true
                    }
                },
                onSeriesClick = { seriesId ->
                    navController.navigate("series_detail/$seriesId") {
                        launchSingleTop = true
                    }
                },
                onNavigateToSearch = { query ->
                    navController.navigate("search_results/${Uri.encode(query)}") {
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
                }
            )
        }

        composable(
            route = "series_detail/{seriesId}",
            arguments = listOf(navArgument("seriesId") { type = NavType.LongType })
        ) { backStackEntry ->
            val seriesId = backStackEntry.arguments?.getLong("seriesId") ?: 0L
            SeriesDetailScreen(
                seriesId = seriesId,
                viewModel = libraryViewModel,
                onEpisodeSelected = { episode, episodeList ->
                    val playerVm = playerViewModel()
                    val flatVideos = videoListViewModel.videosFlat.value
                    val currentVideo = flatVideos.find { it.uri == episode.fileUri } ?: Video(
                        uri = episode.fileUri,
                        title = episode.title ?: "Episode ${episode.episodeNumber}",
                        duration = 0L,
                        folderName = "",
                        path = episode.fileUri,
                        size = 0L,
                        width = 0,
                        height = 0
                    )
                    val queueVideos = episodeList.map { ep ->
                        flatVideos.find { it.uri == ep.fileUri } ?: Video(
                            uri = ep.fileUri,
                            title = ep.title ?: "Episode ${ep.episodeNumber}",
                            duration = 0L,
                            folderName = "",
                            path = ep.fileUri,
                            size = 0L,
                            width = 0,
                            height = 0
                        )
                    }
                    playerVm.setQueue(queueVideos)
                    playerVm.prepareVideo(Uri.parse(episode.fileUri), queueVideos.map { Uri.parse(it.uri) })
                    navController.navigate("player") {
                        launchSingleTop = true
                    }
                },
                onBack = safePopBackStack
            )
        }

        composable("vault") {
            VaultScreen(
                authViewModel = vaultAuthViewModel,
                galleryViewModel = vaultGalleryViewModel,
                onPlayMedia = { entity, file, video ->
                    val playerVm = playerViewModel()
                    playerVm.setQueue(listOf(video))
                    playerVm.prepareVideo(Uri.fromFile(file), listOf(Uri.fromFile(file)))
                    navController.navigate("player") {
                        launchSingleTop = true
                    }
                }
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
            val preFetchedMetadata by playerVm.preFetchedMetadata.collectAsStateWithLifecycle()
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
                preFetchedMetadata = preFetchedMetadata,
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
                onUpdateIsAmbientModeEnabled = { settingsViewModel.updateIsAmbientModeEnabled(it) },
                onUpdateAmbientBlurStyle = { settingsViewModel.updateAmbientBlurStyle(it) },
                onUpdateSaveBrightnessLevel = { settingsViewModel.updateSaveBrightnessLevel(it) },
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

        CapsuleNavigationBar(
            visible = showBottomBar,
            currentRoute = currentRoute,
            onNavigate = { route -> navigateToTopLevel(route) },
            onTabReselect = { route -> navigateToTopLevel(route) },
            modifier = Modifier.align(Alignment.BottomCenter)
        )
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