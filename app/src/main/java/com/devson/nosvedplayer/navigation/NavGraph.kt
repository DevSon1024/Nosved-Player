package com.devson.nosvedplayer.navigation

import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.media3.common.util.UnstableApi
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.devson.nosvedplayer.model.Video
import com.devson.nosvedplayer.ui.screens.settings.AboutScreen
import com.devson.nosvedplayer.ui.screens.HomeScreen
import com.devson.nosvedplayer.ui.screens.OnboardingScreen
import com.devson.nosvedplayer.ui.screens.settings.LogScreen
import com.devson.nosvedplayer.ui.screens.settings.PrivacyPolicyScreen
import com.devson.nosvedplayer.ui.screens.settings.SettingsScreen
import com.devson.nosvedplayer.ui.screens.VideoListScreen
import com.devson.nosvedplayer.ui.screens.settings.AppearanceSettingsScreen
import com.devson.nosvedplayer.viewmodel.SettingsViewModel
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
    }
}
