package com.devson.nosvedplayer.navigation

sealed class Screen(val route: String) {
    object Onboarding : Screen("onboarding")
    object Home       : Screen("home")
    object Videos     : Screen("videos")
    object Settings   : Screen("settings")
    object Appearance : Screen("appearance")
    object About      : Screen("about")
    object Logs       : Screen("logs")
    object PrivacyPolicy : Screen("privacy_policy")
}
