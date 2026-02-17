package com.antibet.presentation.navigation

import android.util.Log
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Home
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.antibet.data.local.database.AntibetDatabase
import com.antibet.data.repository.AntibetRepository
import com.antibet.presentation.add.AddEntryScreen
import com.antibet.presentation.add.AddEntryViewModel
import com.antibet.presentation.home.HomeScreen
import com.antibet.presentation.home.HomeViewModel
import com.antibet.presentation.protection.AccessibilityProtectionScreen

object Routes {
    const val HOME = "home"
    const val ADD_SAVING = "add_saving"
    const val PROTECTION = "protection"
}

@Composable
fun AntiBetNavigation(
    navigateToAddSaving: Boolean = false,
    detectedDomain: String? = null
) {
    val navController = rememberNavController()

    val context = LocalContext.current

    val database = remember { AntibetDatabase.getDatabase(context) }
    val repository = remember {
        AntibetRepository(
            database.betDao(),
            database.savedBetDao(),
            database.siteTriggerDao(),
            database.settingDao()
        )
    }

    val homeViewModel: HomeViewModel = viewModel { HomeViewModel(repository) }
    val addEntryViewModel: AddEntryViewModel = viewModel { AddEntryViewModel(repository) }

    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    val showBottomBar = currentRoute in listOf(Routes.HOME, Routes.PROTECTION)

    LaunchedEffect(navigateToAddSaving) {
        if (navigateToAddSaving) {
            navController.navigate("add_entry/true")
        }
    }

    Scaffold(
        bottomBar = {
            if (showBottomBar){
                NavigationBar {
                    NavigationBarItem(
                        icon = { Icon(Icons.Default.Home, contentDescription = "Home") },
                        label = { Text("Início") },
                        selected = currentRoute == Routes.HOME,
                        onClick = { navController.navigate(Routes.HOME)}
                    )
                    NavigationBarItem(
                        icon = { Icon(Icons.Default.Favorite, contentDescription = "Proteção") },
                        label = { Text("Proteção") },
                        selected = currentRoute == Routes.PROTECTION,
                        onClick = { navController.navigate(Routes.PROTECTION) }
                    )
                }
            }
        }
    ) { paddingValues ->
        NavHost(
            navController = navController,
            startDestination = Routes.HOME,
            modifier = Modifier.padding(paddingValues)
        ) {
            composable(Routes.HOME) {
                HomeScreen(
                    onNavigateToAdd = { navController.navigate("add_entry/true") },
                    viewModel = homeViewModel,
                )
            }

            composable(Routes.PROTECTION) {
                AccessibilityProtectionScreen(
                    onNavigateBack = { navController.popBackStack() }
                )
            }

            composable(
                route = "add_entry/{isSaved}",
                arguments = listOf(
                    navArgument("isSaved") { type = NavType.BoolType }
                )
            ) { backStackEntry ->
                val isSaved = backStackEntry.arguments?.getBoolean("isSaved") ?: true

                AddEntryScreen(
                    isSaved = isSaved,
                    domain = detectedDomain,
                    viewModel = addEntryViewModel,
                    onNavigateBack = { navController.popBackStack() }
                )
            }

        }
    }
}
