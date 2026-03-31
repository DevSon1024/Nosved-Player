package com.devson.nosvedplayer.navigation

sealed class Screen(val route: String) {
    object Home : Screen("home")
    object Videos : Screen("videos")
    object Settings : Screen("settings")
    object About : Screen("about")
    object Logs : Screen("logs")
    object PrivacyPolicy : Screen("privacy_policy")
}
