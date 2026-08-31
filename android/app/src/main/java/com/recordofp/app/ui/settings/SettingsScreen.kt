package com.recordofp.app.ui.settings

import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
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
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.recordofp.app.R
import com.recordofp.app.data.repo.NotificationPolicySettings
import com.recordofp.app.ui.permissions.PermissionSnapshot
import com.recordofp.app.ui.permissions.appDetailsSettingsIntent
import com.recordofp.app.ui.permissions.readPermissionSnapshot

private val COOLDOWN_HOUR_OPTIONS = listOf(1, 4, 12, 24)
private val DAILY_CAP_OPTIONS = listOf(5, 10, 20, 0)

/**
 * 보호 상태 대시보드 + 알림 정책 편집 (설계 §4.3, §4.5).
 * 배터리 최적화는 목록 화면(ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)으로 안내만 하고
 * 자동으로 예외를 요청하지 않는다 (§4.2).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onDiagnosticsClick: () -> Unit = {},
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val policy by viewModel.policy.collectAsStateWithLifecycle()
    var snapshot by remember { mutableStateOf(readPermissionSnapshot(context)) }
    LifecycleResumeEffect(Unit) { // 시스템 설정에서 돌아오면 갱신
        snapshot = readPermissionSnapshot(context)
        onPauseOrDispose { }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(vertical = 8.dp),
    ) {
        item { SectionHeader(stringResource(R.string.settings_section_protection)) }
        items(protectionRows(context, snapshot)) { row ->
            ProtectionPermissionRow(row, onClick = { context.startActivity(row.intent) })
        }
        item {
            Text(
                text = stringResource(R.string.settings_battery_guide),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }
        item { HorizontalDivider(Modifier.padding(vertical = 8.dp)) }

        item { SectionHeader(stringResource(R.string.settings_section_policy)) }
        item {
            PolicySection(
                policy = policy,
                onCooldownChange = viewModel::setCooldownHours,
                onCapChange = viewModel::setDailyCap,
                onQuietEnabledChange = viewModel::setQuietEnabled,
                onQuietRangeChange = viewModel::setQuietRange,
            )
        }
        item { HorizontalDivider(Modifier.padding(vertical = 8.dp)) }

        item {
            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_diagnostics)) },
                modifier = Modifier.fillMaxWidth().clickable(onClick = onDiagnosticsClick),
            )
        }
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
    )
}

private data class ProtectionRow(val labelRes: Int, val granted: Boolean, val intent: Intent)

/** 4개 보호 상태 행 — 꺼진 행 탭 시 이동할 시스템 설정 화면을 함께 담는다 (§4.3). */
private fun protectionRows(context: Context, snapshot: PermissionSnapshot): List<ProtectionRow> = listOf(
    ProtectionRow(
        labelRes = R.string.settings_perm_notifications,
        granted = snapshot.notifications,
        intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
            .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName),
    ),
    ProtectionRow(
        labelRes = R.string.settings_perm_location,
        granted = snapshot.fineLocation,
        intent = appDetailsSettingsIntent(context),
    ),
    ProtectionRow(
        labelRes = R.string.settings_perm_background,
        granted = snapshot.backgroundLocation,
        intent = appDetailsSettingsIntent(context),
    ),
    ProtectionRow(
        labelRes = R.string.settings_perm_battery,
        granted = snapshot.batteryUnrestricted,
        // 목록 화면만 연다 — ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS(자동 요청)는 절대 쓰지 않는다 (§4.2)
        intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS),
    ),
)

@Composable
private fun ProtectionPermissionRow(row: ProtectionRow, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(stringResource(row.labelRes)) },
        trailingContent = {
            Text(
                text = stringResource(if (row.granted) R.string.settings_perm_ok else R.string.settings_perm_off),
                color = if (row.granted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
            )
        },
        modifier = if (row.granted) {
            Modifier.fillMaxWidth()
        } else {
            Modifier.fillMaxWidth().clickable(onClick = onClick)
        },
    )
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
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Column {
            Text(stringResource(R.string.settings_cooldown), style = MaterialTheme.typography.titleSmall)
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(top = 6.dp)) {
                COOLDOWN_HOUR_OPTIONS.forEachIndexed { index, hours ->
                    SegmentedButton(
                        selected = policy.cooldownHours == hours,
                        onClick = { onCooldownChange(hours) },
                        shape = SegmentedButtonDefaults.itemShape(index = index, count = COOLDOWN_HOUR_OPTIONS.size),
                        label = { Text(stringResource(R.string.settings_hours_fmt, hours)) },
                    )
                }
            }
        }

        Column {
            Text(stringResource(R.string.settings_daily_cap), style = MaterialTheme.typography.titleSmall)
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(top = 6.dp)) {
                DAILY_CAP_OPTIONS.forEachIndexed { index, cap ->
                    SegmentedButton(
                        selected = policy.dailyCapTotal == cap,
                        onClick = { onCapChange(cap) },
                        shape = SegmentedButtonDefaults.itemShape(index = index, count = DAILY_CAP_OPTIONS.size),
                        label = {
                            Text(
                                if (cap == 0) {
                                    stringResource(R.string.settings_cap_unlimited)
                                } else {
                                    stringResource(R.string.settings_count_fmt, cap)
                                },
                            )
                        },
                    )
                }
            }
        }

        QuietHoursEditor(policy, onQuietEnabledChange, onQuietRangeChange)
    }
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
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(stringResource(R.string.settings_quiet), style = MaterialTheme.typography.titleSmall)
            Switch(checked = policy.quietEnabled, onCheckedChange = onEnabledChange)
        }
        if (policy.quietEnabled) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedButton(onClick = { editingField = QuietField.START }, modifier = Modifier.weight(1f)) {
                    Text(formatMinuteOfDay(policy.quietStartMinute))
                }
                OutlinedButton(onClick = { editingField = QuietField.END }, modifier = Modifier.weight(1f)) {
                    Text(formatMinuteOfDay(policy.quietEndMinute))
                }
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

/** 분 단위 [0, 1440) → "HH:mm" (24시간제, §4.5 방해금지 시각 표시) */
private fun formatMinuteOfDay(minute: Int): String {
    val h = minute / 60
    val m = minute % 60
    return "%02d:%02d".format(h, m)
}
