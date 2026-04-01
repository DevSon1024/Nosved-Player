package com.devson.nosvedplayer.navigation

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.devson.nosvedplayer.model.Video
import com.devson.nosvedplayer.ui.screens.settings.AboutScreen
import com.devson.nosvedplayer.ui.screens.HomeScreen
import com.devson.nosvedplayer.ui.screens.settings.LogScreen
import com.devson.nosvedplayer.ui.screens.settings.PrivacyPolicyScreen
import com.devson.nosvedplayer.ui.screens.settings.SettingsScreen
import com.devson.nosvedplayer.ui.screens.VideoListScreen
import com.devson.nosvedplayer.ui.screens.settings.AppearanceSettingsScreen
import com.devson.nosvedplayer.viewmodel.SettingsViewModel
import com.devson.nosvedplayer.viewmodel.VideoViewModel

@Composable
fun NavGraph(
    navController: NavHostController,
    videoViewModel: VideoViewModel,
    settingsViewModel: SettingsViewModel,
    onVideoSelected: (Video, List<Video>, Long) -> Unit
) {
    NavHost(
        navController = navController,
        startDestination = Screen.Home.route,
        enterTransition = {
            slideIntoContainer(
                AnimatedContentTransitionScope.SlideDirection.Left,
                animationSpec = tween(300)
            )
        },
        exitTransition = {
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
        popExitTransition = {
            slideOutOfContainer(
                AnimatedContentTransitionScope.SlideDirection.Right,
                animationSpec = tween(300)
            )
        }
    ) {
        composable(Screen.Home.route) {
            HomeScreen(
                onVideoSelected      = onVideoSelected,
                onNavigateToSettings = { navController.navigate(Screen.Settings.route) },
                onNavigateToVideos   = { navController.navigate(Screen.Videos.route) }
            )
        }

        composable(Screen.Videos.route) {
            VideoListScreen(
                onVideoSelected      = onVideoSelected,
                onNavigateToSettings = { navController.navigate(Screen.Settings.route) },
                onBack               = { navController.popBackStack() }
            )
        }

        composable(Screen.Settings.route) {
            SettingsScreen(
                onBack                  = { navController.popBackStack() },
                onNavigateToAbout       = { navController.navigate(Screen.About.route) },
                onNavigateToLogs        = { navController.navigate(Screen.Logs.route) },
                onNavigateToPrivacyPolicy = { navController.navigate(Screen.PrivacyPolicy.route) },
                onNavigateToAppearance  = { navController.navigate(Screen.Appearance.route) },
                settingsViewModel       = settingsViewModel
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
                onBack                 = { navController.popBackStack() },
                onEnableDeveloperMode  = { settingsViewModel.enableDeveloperMode() }
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
    }
}


