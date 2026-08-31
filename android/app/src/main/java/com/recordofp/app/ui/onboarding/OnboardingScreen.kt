package com.recordofp.app.ui.onboarding

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.recordofp.app.R

/**
 * 가치 소개 → 알림 권한 → 위치(사용 중) 권한 → 홈.
 * 백그라운드 "항상 허용"과 배터리 최적화 예외는 여기서 요구하지 않는다 — 홈 배너/설정에서 업셀 (§4.2).
 * 모든 권한 step은 거부해도 다음으로 진행된다 (열화 모드).
 */
@Composable
fun OnboardingScreen(viewModel: OnboardingViewModel = hiltViewModel()) {
    var step by rememberSaveable { mutableIntStateOf(0) }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { _ -> step = 2 }

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { _ -> viewModel.finish() }

    // SDK 33 미만에는 알림 런타임 권한이 존재하지 않는다 — 해당 step을 자동 통과한다
    val notificationStepNeeded = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU

    when (step) {
        0 -> OnboardingStepLayout(
            emoji = "🔔📍",
            title = stringResource(R.string.onboard_1_title),
            body = stringResource(R.string.onboard_1_body),
        ) {
            Button(
                onClick = { step = if (notificationStepNeeded) 1 else 2 },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.onboard_next))
            }
        }

        1 -> OnboardingStepLayout(
            emoji = "🔔",
            title = stringResource(R.string.onboard_2_title),
            body = stringResource(R.string.onboard_2_body),
        ) {
            Button(
                onClick = { notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) },
                modifier = Modifier.fillMaxWidth(),
            ) { Text(stringResource(R.string.onboard_allow)) }
            TextButton(onClick = { step = 2 }, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.onboard_skip))
            }
        }

        else -> OnboardingStepLayout(
            emoji = "📍",
            title = stringResource(R.string.onboard_3_title),
            body = stringResource(R.string.onboard_3_body),
        ) {
            Button(
                onClick = { locationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION) },
                modifier = Modifier.fillMaxWidth(),
            ) { Text(stringResource(R.string.onboard_allow)) }
            TextButton(onClick = { viewModel.finish() }, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.onboard_start))
            }
        }
    }
}

@Composable
private fun OnboardingStepLayout(
    emoji: String,
    title: String,
    body: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(emoji, fontSize = 64.sp)
        Spacer(Modifier.height(24.dp))
        Text(title, style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
        Spacer(Modifier.height(12.dp))
        Text(
            body,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(32.dp))
        content()
    }
}
