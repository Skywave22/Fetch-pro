package com.fetchpro.downloadmanager.presentation.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Title
import androidx.compose.material.icons.filled.Block
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.navArgument
import com.fetchpro.downloadmanager.presentation.ui.screens.details.DetailsScreen
import com.fetchpro.downloadmanager.presentation.ui.screens.history.HistoryScreen
import com.fetchpro.downloadmanager.presentation.ui.screens.home.HomeScreen
import com.fetchpro.downloadmanager.presentation.ui.screens.settings.SettingsScreen

sealed class Screen(val route: String, val label: String, val icon: ImageVector) {
    object Home : Screen("home", "Downloads", Icons.Default.Download)
    object Browser : Screen("browser", "Browser", Icons.Default.Language)
    object Torrent : Screen("torrent", "Torrent", Icons.Default.CloudDownload)
    object History : Screen("history", "History", Icons.Default.History)
    object Settings : Screen("settings", "Settings", Icons.Default.Settings)
    object Details : Screen("details/{downloadId}", "Details", Icons.Default.Download) {
        fun createRoute(id: String) = "details/$id"
    }
    object SiteProfiles : Screen("site_profiles", "Sites", Icons.Default.Person)
    object TemplateEditor : Screen("template_editor", "Template", Icons.Default.Title)
    object SponsorBlock : Screen("sponsorblock", "SponsorBlock", Icons.Default.Block)
}

@Composable
fun AppBottomNav(navController: NavHostController) {
    val items = listOf(Screen.Home, Screen.Browser, Screen.Torrent, Screen.History, Screen.Settings)
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    NavigationBar {
        items.forEach { screen ->
            NavigationBarItem(
                icon = { Icon(screen.icon, contentDescription = screen.label) },
                label = { Text(screen.label) },
                selected = currentRoute == screen.route,
                onClick = {
                    navController.navigate(screen.route) {
                        popUpTo(navController.graph.startDestinationId) { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                }
            )
        }
    }
}

@Composable
fun AppNavGraph(navController: NavHostController, initialUrl: String? = null) {
    NavHost(navController = navController, startDestination = Screen.Home.route) {
        composable(Screen.Home.route) {
            com.fetchpro.downloadmanager.presentation.ui.screens.home.WhiteSoft3DHomeScreen(initialUrl = initialUrl)
        }
        composable(Screen.Browser.route) {
            com.fetchpro.downloadmanager.presentation.ui.screens.browser.BrowserScreen()
        }
        composable(Screen.Torrent.route) {
            com.fetchpro.downloadmanager.presentation.ui.screens.torrent.TorrentScreen()
        }
        composable(Screen.History.route) {
            HistoryScreen()
        }
        composable(Screen.Settings.route) {
            SettingsScreen(
                onNavigateToSiteProfiles = { navController.navigate(Screen.SiteProfiles.route) },
                onNavigateToTemplate = { navController.navigate(Screen.TemplateEditor.route) },
                onNavigateToSponsorBlock = { navController.navigate(Screen.SponsorBlock.route) }
            )
        }
        composable(Screen.SiteProfiles.route) {
            com.fetchpro.downloadmanager.presentation.ui.screens.profiles.SiteProfilesScreen()
        }
        composable(Screen.TemplateEditor.route) {
            com.fetchpro.downloadmanager.presentation.ui.screens.template.TemplateEditorScreen()
        }
        composable(Screen.SponsorBlock.route) {
            com.fetchpro.downloadmanager.presentation.ui.screens.sponsor.SponsorBlockScreen()
        }
        composable(
            route = Screen.Details.route,
            arguments = listOf(navArgument("downloadId") { type = NavType.StringType })
        ) {
            DetailsScreen(onBack = { navController.popBackStack() })
        }
    }
}
