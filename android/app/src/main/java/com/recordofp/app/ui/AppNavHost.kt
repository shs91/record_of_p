package com.recordofp.app.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.recordofp.app.ui.editor.EditorScreen
import com.recordofp.app.ui.home.HomeScreen
import com.recordofp.app.ui.nearby.NearbyScreen
import com.recordofp.app.ui.onboarding.OnboardingScreen
import com.recordofp.app.ui.onboarding.OnboardingViewModel
import com.recordofp.app.ui.settings.SettingsScreen

object Routes {
    const val HOME = "home"
    const val EDITOR = "editor"
    const val NEARBY = "nearby"
    const val SETTINGS = "settings"
}

/**
 * 앱 진입점 분기: 온보딩 완료 여부(§4.2)에 따라 온보딩 또는 메인 그래프로.
 * done == null인 동안은 DataStore 첫 로드 대기 (수 ms) — 빈 화면으로 깜빡임 없이 넘어간다.
 */
@Composable
fun AppNavHost(onboardingViewModel: OnboardingViewModel = hiltViewModel()) {
    val done by onboardingViewModel.done.collectAsStateWithLifecycle()
    when (done) {
        null -> Box(Modifier.fillMaxSize()) {} // DataStore 첫 로드 대기 (수 ms)
        false -> OnboardingScreen()
        true -> MainGraph()
    }
}

@Composable
private fun MainGraph() {
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
