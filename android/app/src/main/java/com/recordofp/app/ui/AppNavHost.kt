package com.recordofp.app.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.recordofp.app.ui.editor.EditorScreen
import com.recordofp.app.ui.home.HomeScreen
import com.recordofp.app.ui.nearby.NearbyScreen
import com.recordofp.app.ui.onboarding.OnboardingScreen
import com.recordofp.app.ui.onboarding.OnboardingViewModel
import com.recordofp.app.ui.settings.DiagnosticsScreen
import com.recordofp.app.ui.settings.SettingsScreen

object Routes {
    const val HOME = "home"
    /** reminderId 생략 시 새 기록, 지정 시 해당 기록 편집 (§3.1 I4) */
    const val EDITOR = "editor?reminderId={reminderId}"
    const val NEARBY = "nearby"
    const val SETTINGS = "settings"
    const val DIAGNOSTICS = "diagnostics"
}

/**
 * 앱 진입점 분기: 온보딩 완료 여부(§4.2)에 따라 온보딩 또는 메인 그래프로.
 * done == null인 동안은 DataStore 첫 로드 대기 (수 ms) — 빈 화면으로 깜빡임 없이 넘어간다.
 *
 * @param deepLinkReminderId 근처 알림 탭으로 진입했을 때 바로 열어야 할 기록 id (§4.1.2)
 */
@Composable
fun AppNavHost(
    deepLinkReminderId: Long? = null,
    onboardingViewModel: OnboardingViewModel = hiltViewModel(),
) {
    val done by onboardingViewModel.done.collectAsStateWithLifecycle()
    when (done) {
        null -> Box(Modifier.fillMaxSize()) {} // DataStore 첫 로드 대기 (수 ms)
        false -> OnboardingScreen()
        true -> MainGraph(deepLinkReminderId)
    }
}

@Composable
private fun MainGraph(deepLinkReminderId: Long? = null) {
    val navController = rememberNavController()
    LaunchedEffect(deepLinkReminderId) {
        if (deepLinkReminderId != null && deepLinkReminderId >= 0) {
            navController.navigate("editor?reminderId=$deepLinkReminderId")
        }
    }
    NavHost(navController = navController, startDestination = Routes.HOME) {
        composable(Routes.HOME) {
            HomeScreen(
                onAddClick = { navController.navigate("editor") },
                onItemClick = { id -> navController.navigate("editor?reminderId=$id") },
                onNearbyClick = { navController.navigate(Routes.NEARBY) },
                onSettingsClick = { navController.navigate(Routes.SETTINGS) },
            )
        }
        composable(
            route = Routes.EDITOR,
            arguments = listOf(navArgument("reminderId") { type = NavType.LongType; defaultValue = -1L }),
        ) { EditorScreen(onDone = { navController.popBackStack() }) }
        composable(Routes.NEARBY) { NearbyScreen() }
        composable(Routes.SETTINGS) {
            SettingsScreen(onDiagnosticsClick = { navController.navigate(Routes.DIAGNOSTICS) })
        }
        composable(Routes.DIAGNOSTICS) { DiagnosticsScreen() }
    }
}
