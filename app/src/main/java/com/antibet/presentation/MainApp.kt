package com.antibet.presentation

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import com.antibet.presentation.navigation.Screen
import com.antibet.presentation.protection.AccessibilityProtectionScreen

@Composable
fun MainApp() {
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
    
    var isVpnRunning by remember { mutableStateOf(false) }
    
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    
    val showBottomBar = currentRoute in listOf(Screen.Home.route, Screen.Protection.route)
    
    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                NavigationBar {
                    NavigationBarItem(
                        icon = { Icon(Icons.Default.Home, contentDescription = "Home") },
                        label = { Text("Início") },
                        selected = currentRoute == Screen.Home.route,
                        onClick = { navController.navigate(Screen.Home.route) }
                    )
                    NavigationBarItem(
                        icon = { Icon(Icons.Default.Favorite, contentDescription = "Proteção") },
                        label = { Text("Proteção") },
                        selected = currentRoute == Screen.Protection.route,
                        onClick = { navController.navigate(Screen.Protection.route) }
                    )
                }
            }
        }
    ) { paddingValues ->
        NavHost(
            navController = navController,
            startDestination = Screen.Home.route,
            modifier = Modifier.padding(paddingValues)
        ) {
            composable(Screen.Home.route) {
                HomeScreen(
                    viewModel = homeViewModel,
                    onNavigateToAdd = { isSaved ->
                        navController.navigate(Screen.AddEntry.createRoute(isSaved))
                    }
                )
            }
            
            composable(Screen.Protection.route) {
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
                    domain = null,
                    viewModel = addEntryViewModel,
                    onNavigateBack = { navController.popBackStack() }
                )
            }
            
            composable(
                route = "add_entry/{isSaved}?domain={domain}",
                arguments = listOf(
                    navArgument("isSaved") { type = NavType.BoolType },
                    navArgument("domain") { 
                        type = NavType.StringType 
                        nullable = true 
                        defaultValue = null 
                    }
                )
            ) { backStackEntry ->
                val isSaved = backStackEntry.arguments?.getBoolean("isSaved") ?: true
                val domain = backStackEntry.arguments?.getString("domain")
                
                AddEntryScreen(
                    isSaved = isSaved,
                    domain = domain,
                    viewModel = addEntryViewModel,
                    onNavigateBack = { navController.popBackStack() }
                )
            }
        }
    }
}
