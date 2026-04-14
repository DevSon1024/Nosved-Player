package com.devson.nosvedplayer.navigation

import android.app.Activity
import androidx.activity.ComponentActivity
import androidx.activity.compose.LocalActivity
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.util.UnstableApi
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.devson.nosvedplayer.model.Video
import com.devson.nosvedplayer.ui.screens.settings.AboutScreen
import com.devson.nosvedplayer.ui.screens.HistoryScreen
import com.devson.nosvedplayer.ui.screens.HomeScreen
import com.devson.nosvedplayer.ui.screens.OnboardingScreen
import com.devson.nosvedplayer.ui.screens.settings.LogScreen
import com.devson.nosvedplayer.ui.screens.settings.PrivacyPolicyScreen
import com.devson.nosvedplayer.ui.screens.SearchResultsScreen
import com.devson.nosvedplayer.ui.screens.settings.SettingsScreen
import com.devson.nosvedplayer.ui.screens.VideoListScreen
import com.devson.nosvedplayer.ui.screens.settings.AppearanceSettingsScreen
import androidx.navigation.NavType
import androidx.navigation.navArgument
import com.devson.nosvedplayer.viewmodel.SettingsViewModel
import com.devson.nosvedplayer.viewmodel.VideoListViewModel
import com.devson.nosvedplayer.viewmodel.VideoViewModel

@OptIn(UnstableApi::class)
@Composable
fun NavGraph(
    navController: NavHostController,
    videoViewModel: VideoViewModel,
    settingsViewModel: SettingsViewModel,
    onVideoSelected: (Video, List<Video>, Long) -> Unit
) {
    val hasSeenOnboarding by settingsViewModel.hasSeenOnboarding.collectAsState()
    val startDestination = remember(hasSeenOnboarding) {
        if (hasSeenOnboarding == true) Screen.Home.route else Screen.Onboarding.route
    }

    NavHost(
        navController    = navController,
        startDestination = startDestination,
        enterTransition  = {
            slideIntoContainer(
                AnimatedContentTransitionScope.SlideDirection.Left,
                animationSpec = tween(300)
            )
        },
        exitTransition   = {
            slideOutOfContainer(
                AnimatedContentTransitionScope.SlideDirection.Left,
                animationSpec = tween(300)
            )
        },
        popEnterTransition = {
            slideIntoContainer(
                AnimatedContentTransitionScope.SlideDirection.Right,
                animationSpec = tween(300)
            )
        },
        popExitTransition  = {
            slideOutOfContainer(
                AnimatedContentTransitionScope.SlideDirection.Right,
                animationSpec = tween(300)
            )
        }
    ) {
        //  Onboarding (first-launch only) 
        composable(Screen.Onboarding.route) {
            OnboardingScreen(
                onFinished = {
                    settingsViewModel.markOnboardingComplete()
                    navController.navigate(Screen.Home.route) {
                        popUpTo(Screen.Onboarding.route) { inclusive = true }
                    }
                }
            )
        }

        composable(Screen.Home.route) {
            HomeScreen(
                onVideoSelected       = onVideoSelected,
                onNavigateToSettings  = { navController.navigate(Screen.Settings.route) },
                onNavigateToVideos    = { navController.navigate(Screen.Videos.route) },
                onNavigateToHistory   = { navController.navigate(Screen.History.route) },
                onNavigateToSearch    = { query -> navController.navigate(Screen.SearchResults.createRoute(query)) },
                onNavigateToRecycleBin = { navController.navigate(Screen.RecycleBin.route) }
            )
        }

        composable(Screen.Videos.route) {
            val activity = LocalActivity.current as ComponentActivity
            val videoListViewModel: VideoListViewModel = viewModel(activity)
            VideoListScreen(
                onVideoSelected      = onVideoSelected,
                onNavigateToSettings = { navController.navigate(Screen.Settings.route) },
                onBack               = { navController.popBackStack() },
                onNavigateToSearch   = { query -> navController.navigate(Screen.SearchResults.createRoute(query)) },
                viewModel            = videoListViewModel
            )
        }

        composable(Screen.Settings.route) {
            SettingsScreen(
                onBack                    = { navController.popBackStack() },
                onNavigateToAbout         = { navController.navigate(Screen.About.route) },
                onNavigateToLogs          = { navController.navigate(Screen.Logs.route) },
                onNavigateToPrivacyPolicy = { navController.navigate(Screen.PrivacyPolicy.route) },
                onNavigateToAppearance    = { navController.navigate(Screen.Appearance.route) },
                settingsViewModel         = settingsViewModel
            )
        }

        composable(Screen.Appearance.route) {
            AppearanceSettingsScreen(
                onNavigateBack    = { navController.popBackStack() },
                settingsViewModel = settingsViewModel
            )
        }

        composable(Screen.About.route) {
            AboutScreen(
                onBack                = { navController.popBackStack() },
                onEnableDeveloperMode = { settingsViewModel.enableDeveloperMode() }
            )
        }

        composable(Screen.Logs.route) {
            LogScreen(
                onBack = { navController.popBackStack() }
            )
        }

        composable(Screen.PrivacyPolicy.route) {
            PrivacyPolicyScreen(
                onBack = { navController.popBackStack() }
            )
        }

        composable(Screen.History.route) {
            HistoryScreen(
                onVideoSelected = onVideoSelected,
                onBack = { navController.popBackStack() }
            )
        }

        composable(Screen.RecycleBin.route) {
            com.devson.nosvedplayer.ui.screens.RecycleBinScreen(
                onBack = { navController.popBackStack() }
            )
        }

        composable(
            route = Screen.SearchResults.route,
            arguments = listOf(navArgument("query") { type = NavType.StringType })
        ) { backStackEntry ->
            val raw = backStackEntry.arguments?.getString("query") ?: ""
            val query = java.net.URLDecoder.decode(raw, "UTF-8")
            SearchResultsScreen(
                query = query,
                onVideoSelected = onVideoSelected,
                onBack = { navController.popBackStack() }
            )
        }
    }
}
