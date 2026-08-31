package com.recordofp.app.ui

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.recordofp.app.ui.editor.EditorScreen
import com.recordofp.app.ui.home.HomeScreen
import com.recordofp.app.ui.nearby.NearbyScreen
import com.recordofp.app.ui.settings.SettingsScreen

object Routes {
    const val HOME = "home"
    const val EDITOR = "editor"
    const val NEARBY = "nearby"
    const val SETTINGS = "settings"
}

@Composable
fun AppNavHost() {
    val navController = rememberNavController()
    NavHost(navController = navController, startDestination = Routes.HOME) {
        composable(Routes.HOME) {
            HomeScreen(
                onAddClick = { navController.navigate(Routes.EDITOR) },
                onNearbyClick = { navController.navigate(Routes.NEARBY) },
                onSettingsClick = { navController.navigate(Routes.SETTINGS) },
            )
        }
        composable(Routes.EDITOR) { EditorScreen(onDone = { navController.popBackStack() }) }
        composable(Routes.NEARBY) { NearbyScreen() }
        composable(Routes.SETTINGS) { SettingsScreen() }
    }
}
