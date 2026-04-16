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
    object History    : Screen("history")
    object RecycleBin : Screen("recycle_bin")
    object SearchResults : Screen("search_results/{query}") {
        fun createRoute(query: String) = "search_results/${java.net.URLEncoder.encode(query, "UTF-8")}"
    }
    object ListOption : Screen("list_option")
    object ScanFolders : Screen("scan_folders")
    object Tool : Screen("tool_screen")
    object PlayerInterface : Screen("player_interface")
}
