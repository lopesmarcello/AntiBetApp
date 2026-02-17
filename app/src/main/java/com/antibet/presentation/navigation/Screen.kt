package com.antibet.presentation.navigation

sealed class Screen(val route: String) {
    data object Home : Screen("home")
    data object Protection : Screen("protection")
    data object AddEntry : Screen("add_entry/{isSaved}?domain={domain}") {
        fun createRoute(isSaved: Boolean, domain: String? = null): String {
            return "add_entry/$isSaved" + if (domain != null) "?domain=$domain" else ""
        }
    }
}
