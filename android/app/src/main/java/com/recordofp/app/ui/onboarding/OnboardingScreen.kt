package com.recordofp.app.ui.onboarding

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
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
 * 클린 미니멀 개편 (개편안 §2 온보딩): 하단 고정 버튼+페이지 도트, 큰 핀 그래픽,
 * 권한 단계에 "왜 필요한가" 카드. 카피는 유지.
 */
@Composable
fun OnboardingScreen(viewModel: OnboardingViewModel = hiltViewModel()) {
    var step by rememberSaveable { mutableIntStateOf(0) }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { _ -> step = 2 }

    // Android 12+는 FINE을 COARSE 없이 요청하면 요청 자체를 무시한다 — 둘을 함께 요청한다 (검토 B2).
    // 사용자가 "대략적 위치"만 고르면 FINE이 없어 엔진이 대기한다(근처 알림 없음). 보호 상태가 '정확한 위치 꺼짐'으로 안내한다
    val locationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { _ -> viewModel.finish() }

    // SDK 33 미만에는 알림 런타임 권한이 존재하지 않는다 — 해당 step을 자동 통과한다
    val notificationStepNeeded = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU

    when (step) {
        0 -> OnboardingStepLayout(
            dotIndex = 0,
            graphic = { PinMarkGraphic() },
            title = stringResource(R.string.onboard_1_title),
            body = stringResource(R.string.onboard_1_body),
        ) {
            PrimaryButton(
                text = stringResource(R.string.onboard_next),
                onClick = { step = if (notificationStepNeeded) 1 else 2 },
            )
        }

        1 -> OnboardingStepLayout(
            dotIndex = 1,
            graphic = { EmojiGraphic("🔔") },
            title = stringResource(R.string.onboard_2_title),
            body = stringResource(R.string.onboard_2_body),
        ) {
            PrimaryButton(
                text = stringResource(R.string.onboard_allow),
                onClick = { notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) },
            )
            TextButton(onClick = { step = 2 }, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.onboard_skip), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        else -> OnboardingStepLayout(
            dotIndex = 2,
            graphic = { EmojiGraphic("📍") },
            title = stringResource(R.string.onboard_3_title),
            body = stringResource(R.string.onboard_3_body),
            extra = { WhyCard() },
        ) {
            PrimaryButton(
                text = stringResource(R.string.onboard_allow),
                onClick = {
                    locationPermissionLauncher.launch(
                        arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION),
                    )
                },
            )
            TextButton(onClick = { viewModel.finish() }, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.onboard_start), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun OnboardingStepLayout(
    dotIndex: Int,
    graphic: @Composable () -> Unit,
    title: String,
    body: String,
    extra: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Scaffold(containerColor = MaterialTheme.colorScheme.background) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                graphic()
                Spacer(Modifier.height(26.dp))
                Text(title, style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
                Spacer(Modifier.height(12.dp))
                Text(
                    body,
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                extra?.let {
                    Spacer(Modifier.height(24.dp))
                    it()
                }
            }
            // 하단 고정: 페이지 도트 + 버튼 (개편안 §2)
            PageDots(active = dotIndex)
            Spacer(Modifier.height(16.dp))
            content()
            Spacer(Modifier.height(20.dp))
        }
    }
}

/** 브랜드 P-핀 마크 — 연블루 원 위 블루 마크 (개편안 §2) */
@Composable
private fun PinMarkGraphic() {
    Box(
        modifier = Modifier
            .size(112.dp)
            .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_pin_mark),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(58.dp),
        )
    }
}

@Composable
private fun EmojiGraphic(emoji: String) {
    Box(
        modifier = Modifier
            .size(96.dp)
            .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text(emoji, fontSize = 40.sp)
    }
}

/** 권한 단계 "왜 필요한가" 카드 — 신뢰를 먼저, 요청은 다음 (개편안 §2) */
@Composable
private fun WhyCard() {
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(horizontal = 18.dp, vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                stringResource(R.string.onboard_why_title),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text("✓ ${stringResource(R.string.onboard_why_1)}", style = MaterialTheme.typography.bodySmall)
            Text("✓ ${stringResource(R.string.onboard_why_2)}", style = MaterialTheme.typography.bodySmall)
            Text("✓ ${stringResource(R.string.onboard_why_3)}", style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun PageDots(active: Int, count: Int = 3) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        repeat(count) { index ->
            val isActive = index == active
            Box(
                modifier = Modifier
                    .height(6.dp)
                    .width(if (isActive) 18.dp else 6.dp)
                    .background(
                        if (isActive) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.outlineVariant
                        },
                        RoundedCornerShape(3.dp),
                    ),
            )
        }
    }
}

@Composable
private fun PrimaryButton(text: String, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
        ),
        modifier = Modifier.fillMaxWidth().height(54.dp),
    ) {
        Text(text, style = MaterialTheme.typography.labelLarge)
    }
}
