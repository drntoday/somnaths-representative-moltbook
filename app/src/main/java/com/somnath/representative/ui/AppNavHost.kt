package com.somnath.representative.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable

private object Destinations {
    const val Home = "home"
    const val Settings = "settings"
}

@Composable
fun AppNavHost(navController: NavHostController, modifier: Modifier = Modifier) {
    NavHost(
        navController = navController,
        startDestination = Destinations.Home,
        modifier = modifier
    ) {
        composable(Destinations.Home) {
            HomeScreen(onOpenSettings = { navController.navigate(Destinations.Settings) })
        }
        composable(Destinations.Settings) {
            SettingsScreen(onBack = { navController.popBackStack() })
        }
    }
}
