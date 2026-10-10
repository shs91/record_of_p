package com.recordofp.app.ui.settings

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.recordofp.app.R
import com.recordofp.app.ui.common.BackButton
import com.recordofp.app.data.repo.NotificationPolicySettings
import com.recordofp.app.ui.common.tabularNums
import com.recordofp.app.ui.permissions.PermissionSnapshot
import com.recordofp.app.ui.permissions.appDetailsSettingsIntent
import com.recordofp.app.ui.permissions.appNotificationSettingsIntent
import com.recordofp.app.ui.permissions.batteryOptimizationSettingsIntent
import com.recordofp.app.ui.permissions.locationSourceSettingsIntent
import com.recordofp.app.ui.permissions.openSettings
import com.recordofp.app.ui.permissions.rememberPermissionSnapshot
import com.recordofp.app.ui.theme.PillShape

private val COOLDOWN_HOUR_OPTIONS = listOf(1, 4, 12, 24)
private val DAILY_CAP_OPTIONS = listOf(5, 10, 20, 0)

/**
 * 보호 상태 대시보드 + 알림 정책 편집 (설계 §4.3, §4.5).
 * 배터리 최적화는 목록 화면(ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)으로 안내만 하고
 * 자동으로 예외를 요청하지 않는다 (§4.2).
 * 클린 미니멀 개편 (개편안 §2 설정): TopAppBar + 그룹 카드 3개(보호 상태/알림 정책/문제 해결),
 * 상태 pill. Scaffold 도입으로 상태바 겹침·다크 배경(F3)도 함께 해소.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onDiagnosticsClick: () -> Unit = {},
    onBack: () -> Unit = {},
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val policy by viewModel.policy.collectAsStateWithLifecycle()
    // 시스템 설정·빠른 설정에서 돌아오면 갱신하고, 보호 복구 전이면 재배치한다 (F1)
    val snapshot = rememberPermissionSnapshot(onRead = { viewModel.reportProtection(it.fullyProtected) })

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                navigationIcon = { BackButton(onClick = onBack) },
                title = { Text(stringResource(R.string.title_settings)) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(start = 20.dp, end = 20.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            GroupCard {
                SectionLabel(stringResource(R.string.settings_section_protection))
                val rows = protectionRows(context, snapshot)
                rows.forEachIndexed { index, row ->
                    ProtectionPermissionRow(row, onClick = { context.openSettings(row.intent) })
                    if (index != rows.lastIndex) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    }
                }
                Text(
                    text = stringResource(R.string.settings_battery_guide),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 10.dp),
                )
            }

            GroupCard {
                SectionLabel(stringResource(R.string.settings_section_policy))
                PolicySection(
                    policy = policy,
                    onCooldownChange = viewModel::setCooldownHours,
                    onCapChange = viewModel::setDailyCap,
                    onQuietEnabledChange = viewModel::setQuietEnabled,
                    onQuietRangeChange = viewModel::setQuietRange,
                )
            }

            Card(
                onClick = onDiagnosticsClick,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 16.dp),
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(stringResource(R.string.settings_diagnostics), style = MaterialTheme.typography.bodyLarge)
                    Icon(
                        Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

/** 그룹 카드 — 20dp 라운드, 그림자 대신 톤 차이 (개편안 §1) */
@Composable
private fun GroupCard(content: @Composable () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(horizontal = 18.dp, vertical = 16.dp)) {
            content()
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(bottom = 4.dp),
    )
}

private data class ProtectionRow(val labelRes: Int, val granted: Boolean, val intent: Intent)

/** 보호 상태 행 — 꺼진 행 탭 시 이동할 시스템 설정 화면을 함께 담는다 (§4.3). */
private fun protectionRows(context: Context, snapshot: PermissionSnapshot): List<ProtectionRow> = listOf(
    ProtectionRow(R.string.settings_perm_notifications, snapshot.notifications, appNotificationSettingsIntent(context)),
    ProtectionRow(R.string.settings_perm_location, snapshot.fineLocation, appDetailsSettingsIntent(context)),
    ProtectionRow(R.string.settings_perm_background, snapshot.backgroundLocation, appDetailsSettingsIntent(context)),
    // 기기 위치가 꺼지면 OS가 펜스를 전부 지운다 — 권한이 다 있어도 알림이 오지 않는 이유 (최종 리뷰 I4)
    ProtectionRow(R.string.settings_perm_location_services, snapshot.locationServicesOn, locationSourceSettingsIntent()),
    // 목록 화면만 연다 — ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS(자동 요청)는 절대 쓰지 않는다 (§4.2)
    ProtectionRow(R.string.settings_perm_battery, snapshot.batteryUnrestricted, batteryOptimizationSettingsIntent()),
)

@Composable
private fun ProtectionPermissionRow(row: ProtectionRow, onClick: () -> Unit) {
    Row(
        modifier = if (row.granted) {
            Modifier.fillMaxWidth()
        } else {
            Modifier.fillMaxWidth().clickable(onClick = onClick)
        }.padding(vertical = 12.dp),
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(stringResource(row.labelRes), style = MaterialTheme.typography.bodyLarge)
        StatusPill(granted = row.granted)
    }
}

/** 상태 pill — 켜짐 연블루 / 꺼짐 빨강: 스캔 가능한 상태 표시 (개편안 §2) */
@Composable
private fun StatusPill(granted: Boolean) {
    Surface(
        shape = PillShape,
        color = if (granted) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.errorContainer
        },
    ) {
        Text(
            text = stringResource(if (granted) R.string.settings_perm_ok else R.string.settings_perm_off),
            style = MaterialTheme.typography.labelSmall,
            color = if (granted) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onErrorContainer
            },
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PolicySection(
    policy: NotificationPolicySettings,
    onCooldownChange: (Int) -> Unit,
    onCapChange: (Int) -> Unit,
    onQuietEnabledChange: (Boolean) -> Unit,
    onQuietRangeChange: (Int, Int) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Column {
            Text(stringResource(R.string.settings_cooldown), style = MaterialTheme.typography.bodyLarge)
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(top = 8.dp)) {
                COOLDOWN_HOUR_OPTIONS.forEachIndexed { index, hours ->
                    PolicySegment(
                        selected = policy.cooldownHours == hours,
                        onClick = { onCooldownChange(hours) },
                        index = index,
                        count = COOLDOWN_HOUR_OPTIONS.size,
                        label = stringResource(R.string.settings_hours_fmt, hours),
                    )
                }
            }
        }

        Column {
            Text(stringResource(R.string.settings_daily_cap), style = MaterialTheme.typography.bodyLarge)
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(top = 8.dp)) {
                DAILY_CAP_OPTIONS.forEachIndexed { index, cap ->
                    PolicySegment(
                        selected = policy.dailyCapTotal == cap,
                        onClick = { onCapChange(cap) },
                        index = index,
                        count = DAILY_CAP_OPTIONS.size,
                        label = if (cap == 0) {
                            stringResource(R.string.settings_cap_unlimited)
                        } else {
                            stringResource(R.string.settings_count_fmt, cap)
                        },
                    )
                }
            }
        }

        QuietHoursEditor(policy, onQuietEnabledChange, onQuietRangeChange)
    }
}

/** 세그먼트 — 선택 시 연블루+블루 텍스트로 팔레트 정합 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun androidx.compose.material3.SingleChoiceSegmentedButtonRowScope.PolicySegment(
    selected: Boolean,
    onClick: () -> Unit,
    index: Int,
    count: Int,
    label: String,
) {
    SegmentedButton(
        selected = selected,
        onClick = onClick,
        shape = SegmentedButtonDefaults.itemShape(index = index, count = count),
        colors = SegmentedButtonDefaults.colors(
            activeContainerColor = MaterialTheme.colorScheme.primaryContainer,
            activeContentColor = MaterialTheme.colorScheme.primary,
            activeBorderColor = MaterialTheme.colorScheme.outlineVariant,
            inactiveContainerColor = MaterialTheme.colorScheme.surface,
            inactiveContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            inactiveBorderColor = MaterialTheme.colorScheme.outlineVariant,
        ),
        icon = {}, // 체크 아이콘 없이 색으로만 — 미니멀
        label = { Text(label, style = MaterialTheme.typography.labelSmall) },
    )
}

private enum class QuietField { START, END }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun QuietHoursEditor(
    policy: NotificationPolicySettings,
    onEnabledChange: (Boolean) -> Unit,
    onRangeChange: (Int, Int) -> Unit,
) {
    var editingField by remember { mutableStateOf<QuietField?>(null) }

    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(stringResource(R.string.settings_quiet), style = MaterialTheme.typography.bodyLarge)
            Switch(checked = policy.quietEnabled, onCheckedChange = onEnabledChange)
        }
        if (policy.quietEnabled) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                QuietTimeButton(
                    text = formatMinuteOfDay(policy.quietStartMinute),
                    onClick = { editingField = QuietField.START },
                    modifier = Modifier.weight(1f),
                )
                QuietTimeButton(
                    text = formatMinuteOfDay(policy.quietEndMinute),
                    onClick = { editingField = QuietField.END },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }

    editingField?.let { field ->
        val initialMinute = if (field == QuietField.START) policy.quietStartMinute else policy.quietEndMinute
        val timeState = rememberTimePickerState(
            initialHour = initialMinute / 60,
            initialMinute = initialMinute % 60,
            is24Hour = true,
        )
        AlertDialog(
            onDismissRequest = { editingField = null },
            confirmButton = {
                TextButton(onClick = {
                    val newMinute = timeState.hour * 60 + timeState.minute
                    if (field == QuietField.START) {
                        onRangeChange(newMinute, policy.quietEndMinute)
                    } else {
                        onRangeChange(policy.quietStartMinute, newMinute)
                    }
                    editingField = null
                }) { Text(stringResource(android.R.string.ok)) }
            },
            dismissButton = {
                TextButton(onClick = { editingField = null }) { Text(stringResource(android.R.string.cancel)) }
            },
            text = { TimePicker(state = timeState) },
        )
    }
}

@Composable
private fun QuietTimeButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    OutlinedButton(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        modifier = modifier,
    ) {
        Text(
            text,
            style = tabularNums(MaterialTheme.typography.labelLarge),
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

/** 분 단위 [0, 1440) → "HH:mm" (24시간제, §4.5 방해금지 시각 표시) */
private fun formatMinuteOfDay(minute: Int): String {
    val h = minute / 60
    val m = minute % 60
    return "%02d:%02d".format(h, m)
}
