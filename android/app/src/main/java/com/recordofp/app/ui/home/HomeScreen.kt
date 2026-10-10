package com.recordofp.app.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.recordofp.app.R
import com.recordofp.app.domain.model.Reminder
import com.recordofp.app.domain.model.TriggerCatalog
import com.recordofp.app.domain.model.TriggerSpec
import com.recordofp.app.domain.model.TriggerType
import com.recordofp.app.ui.common.catalogLabelRes
import com.recordofp.app.ui.permissions.rememberPermissionSnapshot
import com.recordofp.app.ui.theme.PillShape
import com.recordofp.app.ui.theme.Spacing
import com.recordofp.app.ui.theme.onSuccessColor
import com.recordofp.app.ui.theme.successColor
import kotlinx.coroutines.launch

/**
 * 활성 항목 리스트(카테고리 칩 그룹핑), 완료 스와이프 (설계 §4.1).
 * 클린 미니멀 개편 (개편안 §2 홈): 큰 타이틀 28 Bold, 카드 20dp + 회색 pill 칩,
 * 스와이프 완료 초록 배경+체크, 보호 배너 앰버, 확장 FAB "＋ 기록", 항목 등장/제거 애니메이션.
 */
@Composable
fun HomeScreen(
    onAddClick: () -> Unit,
    onItemClick: (Long) -> Unit,
    onNearbyClick: () -> Unit,
    onSettingsClick: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val items by viewModel.items.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val completedMessage = stringResource(R.string.home_completed)
    val undoLabel = stringResource(R.string.action_undo)
    // 완료는 되돌릴 수 있어야 한다 — 스와이프·TalkBack 완료 뒤 [실행 취소] 스낵바 (§4.1 처리, 최종 리뷰 I5)
    val completeWithUndo: (Long) -> Unit = { id ->
        viewModel.complete(id)
        scope.launch {
            snackbarHostState.currentSnackbarData?.dismiss() // 연속 완료면 마지막 것만
            val result = snackbarHostState.showSnackbar(
                message = completedMessage, actionLabel = undoLabel, duration = SnackbarDuration.Short,
            )
            if (result == SnackbarResult.ActionPerformed) viewModel.reactivate(id)
        }
    }
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        // 떠 있는 동안 FAB은 Scaffold가 스낵바 위로 올린다 (개편안 2 §2)
        snackbarHost = {
            SnackbarHost(snackbarHostState, Modifier.padding(horizontal = Spacing.m, vertical = Spacing.xs)) { data ->
                UndoSnackbar(
                    message = data.visuals.message,
                    actionLabel = data.visuals.actionLabel.orEmpty(),
                    onAction = data::performAction,
                )
            }
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onAddClick,
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text(stringResource(R.string.home_fab)) },
            )
        },
    ) { padding ->
        // 설정·빠른 설정에서 돌아오면 갱신하고, 보호 복구 전이면 재배치한다 (F1)
        val snapshot = rememberPermissionSnapshot(onRead = { viewModel.reportProtection(it.fullyProtected) })
        Column(Modifier.fillMaxSize().padding(padding)) {
            // 큰 타이틀 헤더 — 앱바 없이 종이 위에 바로 (개편안 §2)
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                horizontalArrangement = Arrangement.End,
            ) {
                IconButton(onClick = onNearbyClick) {
                    Icon(Icons.Outlined.Place, stringResource(R.string.title_nearby))
                }
                IconButton(onClick = onSettingsClick) {
                    Icon(Icons.Outlined.Settings, stringResource(R.string.title_settings))
                }
            }
            Text(
                text = stringResource(R.string.title_home),
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
            )
            snapshot.topIssue?.let { issue ->
                ProtectionBanner(
                    issue = issue,
                    modifier = Modifier.padding(start = Spacing.screen, end = Spacing.screen, top = Spacing.m, bottom = Spacing.xxs),
                )
            }
            if (items.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        stringResource(R.string.home_empty), textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                LazyColumn(
                    Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 100.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    // 키에 updatedAt을 넣는다 — 실행 취소로 되살아난 행이 스와이프된 옛 상태를 물려받지 않게 (N1)
                    items(items, key = { "${it.id}:${it.updatedAt}" }) { item ->
                        ReminderRow(
                            item = item,
                            onComplete = { completeWithUndo(item.id) },
                            onClick = { onItemClick(item.id) },
                            modifier = Modifier.animateItem(), // 등장/제거/재배열 애니메이션 (개편안 §2)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ReminderRow(
    item: Reminder,
    onComplete: () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // 부수효과는 확정된 상태 변화에서 한 번만 — confirmValueChange는 같은 스와이프에 여러 번 불릴 수 있다 (N1)
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { it == SwipeToDismissBoxValue.StartToEnd },
    )
    LaunchedEffect(dismissState.currentValue) {
        if (dismissState.currentValue == SwipeToDismissBoxValue.StartToEnd) onComplete()
    }
    val completeLabel = stringResource(R.string.action_complete)
    SwipeToDismissBox(
        state = dismissState,
        modifier = modifier,
        // 시작→끝 방향만 — 실행 취소 스낵바와 짝을 이루는 한 가지 제스처 (최종 리뷰 I5)
        enableDismissFromEndToStart = false,
        backgroundContent = { CompleteSwipeBackground() },
    ) {
        Card(
            onClick = onClick,
            modifier = Modifier
                .fillMaxWidth()
                // 스와이프를 못 하는 TalkBack 사용자도 완료할 수 있게 사용자 지정 동작을 단다 (§8, 최종 리뷰 I7)
                .semantics {
                    customActions = listOf(CustomAccessibilityAction(completeLabel) { onComplete(); true })
                },
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        ) {
            Column(Modifier.padding(horizontal = 18.dp, vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(item.title, style = MaterialTheme.typography.titleMedium)
                if (item.triggers.isNotEmpty()) {
                    TriggerChips(item.triggers)
                }
            }
        }
    }
}

/** 완료 스와이프 뒤 — 성공 면, 32dp 원 체크, "완료" (개편안 2 §2) */
@Composable
private fun CompleteSwipeBackground() {
    val success = successColor()
    val onSuccess = onSuccessColor()
    Row(
        modifier = Modifier
            .fillMaxSize()
            .clip(MaterialTheme.shapes.medium)
            .background(success)
            .padding(horizontal = 22.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            modifier = Modifier.size(32.dp).background(onSuccess, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Filled.Check, contentDescription = null, tint = success, modifier = Modifier.size(20.dp))
        }
        Text(
            stringResource(R.string.action_complete),
            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
            color = onSuccess,
        )
    }
}

/** 트리거를 회색 pill 칩으로 — 텍스트 나열을 대체한다 (개편안 §2) */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TriggerChips(triggers: List<TriggerSpec>) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        triggers.forEach { trigger ->
            Surface(
                shape = PillShape,
                color = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
            ) {
                Text(
                    text = trigger.chipLabel(),
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                )
            }
        }
    }
}

@Composable
private fun TriggerSpec.chipLabel(): String = when (type) {
    TriggerType.CATEGORY -> {
        val entry = categoryId?.let { TriggerCatalog.byId(it) }
        "${entry?.emoji ?: ""} ${entry?.let { stringResource(catalogLabelRes(it.id)) } ?: ""}".trim()
    }
    TriggerType.BRAND -> "🔎 $brandKeyword"
    TriggerType.PLACE -> "📌 $placeName"
}
