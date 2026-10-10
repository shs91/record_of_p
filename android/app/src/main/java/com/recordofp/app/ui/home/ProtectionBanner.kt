package com.recordofp.app.ui.home

import android.content.Context
import android.content.Intent
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.recordofp.app.R
import com.recordofp.app.ui.permissions.ProtectionIssue
import com.recordofp.app.ui.permissions.appDetailsSettingsIntent
import com.recordofp.app.ui.permissions.appNotificationSettingsIntent
import com.recordofp.app.ui.permissions.locationSourceSettingsIntent
import com.recordofp.app.ui.permissions.openSettings
import com.recordofp.app.ui.theme.LightDarkPreviews
import com.recordofp.app.ui.theme.PillShape
import com.recordofp.app.ui.theme.RecordOfPTheme
import com.recordofp.app.ui.theme.Spacing

/**
 * 근처 알림을 막고 있는 것 하나를 이름으로 알리고 고칠 곳으로 바로 보낸다 (§4.2 4단계 업셀, §4.3, 최종 리뷰 I3).
 * 여럿이면 우선순위가 높은 하나만 — 고치고 돌아오면 다음 것이 보인다. 앰버 면: 경고이지 오류가 아니다.
 * 모양은 개편안 2 §2 보호 배너 — 36dp 원 아이콘, 제목·본문, '항상 허용' 단계 칩, [설정 열기] pill.
 */
@Composable
fun ProtectionBanner(issue: ProtectionIssue, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val scheme = MaterialTheme.colorScheme
    val hasSteps = issue == ProtectionIssue.BACKGROUND_LOCATION_OFF
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = scheme.tertiaryContainer,
        contentColor = scheme.onTertiaryContainer,
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(Spacing.m),
            verticalArrangement = Arrangement.spacedBy(if (hasSteps) Spacing.s else 10.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s), verticalAlignment = Alignment.Top) {
                Box(
                    modifier = Modifier.size(36.dp).background(scheme.onTertiaryContainer, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        painterResource(issue.iconRes()),
                        contentDescription = null,
                        tint = scheme.tertiaryContainer,
                        modifier = Modifier.size(20.dp),
                    )
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(stringResource(issue.titleRes()), style = MaterialTheme.typography.titleSmall)
                    Text(stringResource(issue.bodyRes()), style = MaterialTheme.typography.bodySmall)
                }
            }
            // A11+ "항상 허용"은 시스템 설정에서만 켤 수 있다 — 단계를 적어 준다 (§4.2)
            if (hasSteps) BackgroundLocationSteps()
            Button(
                onClick = { context.openSettings(issue.settingsIntent(context)) },
                shape = PillShape,
                colors = ButtonDefaults.buttonColors(
                    containerColor = scheme.onTertiaryContainer,
                    contentColor = scheme.surface,
                ),
                contentPadding = PaddingValues(horizontal = Spacing.m),
                modifier = Modifier.align(Alignment.End),
            ) {
                Text(stringResource(R.string.banner_action_open_settings), style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

/** 설정 → 권한 → 위치 → 항상 허용. 마지막 칩만 앰버 바탕. TalkBack은 한 줄로 읽는다 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BackgroundLocationSteps() {
    val scheme = MaterialTheme.colorScheme
    val steps = listOf(
        R.string.banner_background_step_settings,
        R.string.banner_background_step_permissions,
        R.string.banner_background_step_location,
        R.string.banner_background_step_always,
    )
    FlowRow(
        // 원 아이콘(36) + 간격(12) 만큼 들여 제목과 줄을 맞춘다
        modifier = Modifier.padding(start = 48.dp).semantics(mergeDescendants = true) {},
        horizontalArrangement = Arrangement.spacedBy(Spacing.xxs),
        verticalArrangement = Arrangement.spacedBy(Spacing.xxs),
    ) {
        steps.forEachIndexed { index, res ->
            val last = index == steps.lastIndex
            Surface(
                shape = PillShape,
                color = if (last) scheme.onTertiaryContainer else scheme.surface,
                contentColor = if (last) scheme.surface else scheme.onTertiaryContainer,
            ) {
                Text(
                    stringResource(res),
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontWeight = if (last) FontWeight.Bold else FontWeight.SemiBold,
                    ),
                    modifier = Modifier.padding(horizontal = 9.dp, vertical = Spacing.xxs),
                )
            }
            if (!last) {
                Icon(
                    Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    modifier = Modifier.size(12.dp).align(Alignment.CenterVertically),
                )
            }
        }
    }
}

@DrawableRes
private fun ProtectionIssue.iconRes(): Int = when (this) {
    ProtectionIssue.NOTIFICATIONS_OFF -> R.drawable.ic_banner_notifications_off
    ProtectionIssue.PRECISE_LOCATION_OFF -> R.drawable.ic_banner_precise
    ProtectionIssue.BACKGROUND_LOCATION_OFF -> R.drawable.ic_trigger_place
    ProtectionIssue.LOCATION_SERVICES_OFF -> R.drawable.ic_banner_location_off
}

@StringRes
private fun ProtectionIssue.titleRes(): Int = when (this) {
    ProtectionIssue.NOTIFICATIONS_OFF -> R.string.banner_notifications_title
    ProtectionIssue.PRECISE_LOCATION_OFF -> R.string.banner_precise_title
    ProtectionIssue.BACKGROUND_LOCATION_OFF -> R.string.banner_background_title
    ProtectionIssue.LOCATION_SERVICES_OFF -> R.string.banner_location_off_title
}

@StringRes
private fun ProtectionIssue.bodyRes(): Int = when (this) {
    ProtectionIssue.NOTIFICATIONS_OFF -> R.string.banner_notifications_body
    ProtectionIssue.PRECISE_LOCATION_OFF -> R.string.banner_precise_body
    ProtectionIssue.BACKGROUND_LOCATION_OFF -> R.string.banner_background_body
    ProtectionIssue.LOCATION_SERVICES_OFF -> R.string.banner_location_off_body
}

/** 고칠 시스템 화면 — 없는 제조사 빌드에서는 openSettings가 앱 상세 설정으로 대신 연다 */
private fun ProtectionIssue.settingsIntent(context: Context): Intent = when (this) {
    ProtectionIssue.NOTIFICATIONS_OFF -> appNotificationSettingsIntent(context)
    ProtectionIssue.PRECISE_LOCATION_OFF, ProtectionIssue.BACKGROUND_LOCATION_OFF -> appDetailsSettingsIntent(context)
    ProtectionIssue.LOCATION_SERVICES_OFF -> locationSourceSettingsIntent()
}

@LightDarkPreviews
@Composable
private fun ProtectionBannerPreview() {
    RecordOfPTheme {
        Column(
            modifier = Modifier.background(MaterialTheme.colorScheme.background).padding(Spacing.screen),
            verticalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            ProtectionIssue.entries.forEach { ProtectionBanner(it) }
        }
    }
}

@Preview(name = "큰 글꼴", showBackground = true, fontScale = 2f)
@Composable
private fun ProtectionBannerLargeFontPreview() {
    RecordOfPTheme { ProtectionBanner(ProtectionIssue.BACKGROUND_LOCATION_OFF) }
}
