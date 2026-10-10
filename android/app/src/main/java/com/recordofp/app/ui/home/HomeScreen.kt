package com.recordofp.app.ui.home

import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.recordofp.app.R
import com.recordofp.app.domain.model.Reminder
import com.recordofp.app.ui.common.tabularNums
import com.recordofp.app.ui.permissions.ProtectionIssue
import com.recordofp.app.ui.permissions.rememberPermissionSnapshot
import com.recordofp.app.ui.theme.Motion
import com.recordofp.app.ui.theme.PillShape
import com.recordofp.app.ui.theme.Spacing
import kotlinx.coroutines.launch

/**
 * 활성 항목 목록과 완료 (설계 §4.1). 모양은 개편안 2 §2 홈 — 큰 타이틀 옆 개수 pill, 보호 배너,
 * 카테고리 타일 카드, 빈 상태 그래픽, 완료 스와이프와 [실행 취소] 스낵바, 확장 FAB "＋ 기록".
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
    // 설정·빠른 설정에서 돌아오면 갱신하고, 보호 복구 전이면 재배치한다 (F1)
    val snapshot = rememberPermissionSnapshot(onRead = { viewModel.reportProtection(it.fullyProtected) })
    HomeContent(
        items = items,
        issue = snapshot.topIssue,
        snackbarHostState = snackbarHostState,
        onAddClick = onAddClick,
        onItemClick = onItemClick,
        onNearbyClick = onNearbyClick,
        onSettingsClick = onSettingsClick,
        onComplete = completeWithUndo,
    )
}

/** 홈 본체 — 상태와 동작만 받는다(미리보기는 HomePreviews.kt) */
@Composable
fun HomeContent(
    items: List<Reminder>,
    issue: ProtectionIssue?,
    snackbarHostState: SnackbarHostState,
    onAddClick: () -> Unit,
    onItemClick: (Long) -> Unit,
    onNearbyClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onComplete: (Long) -> Unit,
) {
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
        Column(Modifier.fillMaxSize().padding(padding)) {
            // 앱바 없이 종이 위에 바로 — 오른쪽 위 아이콘, 큰 타이틀 (개편안 §2)
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.xs),
                horizontalArrangement = Arrangement.End,
            ) {
                IconButton(onClick = onNearbyClick) {
                    Icon(Icons.Outlined.Place, stringResource(R.string.title_nearby))
                }
                IconButton(onClick = onSettingsClick) {
                    Icon(Icons.Outlined.Settings, stringResource(R.string.title_settings))
                }
            }
            HomeTitle(count = items.size)
            issue?.let {
                ProtectionBanner(
                    issue = it,
                    modifier = Modifier.padding(start = Spacing.screen, end = Spacing.screen, top = Spacing.m),
                )
            }
            if (items.isEmpty()) {
                HomeEmptyState(Modifier.fillMaxSize())
            } else {
                val motion = tween<Float>(Motion.STANDARD_MS, easing = Motion.easing)
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        start = Spacing.screen,
                        end = Spacing.screen,
                        top = if (issue == null) Spacing.m else Spacing.s,
                        bottom = 100.dp, // 확장 FAB(56) 아래로 마지막 카드가 숨지 않게
                    ),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    // 키에 updatedAt을 넣는다 — 실행 취소로 되살아난 행이 스와이프된 옛 상태를 물려받지 않게 (N1)
                    items(items, key = { "${it.id}:${it.updatedAt}" }) { item ->
                        ReminderRow(
                            item = item,
                            onComplete = { onComplete(item.id) },
                            onClick = { onItemClick(item.id) },
                            // 등장·제거·재배열은 표준 모션 (개편안 2 §1.4)
                            modifier = Modifier.animateItem(
                                fadeInSpec = motion,
                                placementSpec = tween(Motion.STANDARD_MS, easing = Motion.easing),
                                fadeOutSpec = motion,
                            ),
                        )
                    }
                }
            }
        }
    }
}

/** 큰 타이틀 + 개수 pill. 0개면 pill을 숨긴다 (개편안 2 §2, 디자인 시스템 §1.3 숫자 강조) */
@Composable
private fun HomeTitle(count: Int) {
    Row(
        modifier = Modifier.padding(start = Spacing.screen, end = Spacing.screen, top = Spacing.xxs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            text = stringResource(R.string.title_home),
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.semantics { heading() },
        )
        if (count > 0) CountPill(count)
    }
}

@Composable
private fun CountPill(count: Int) {
    val description = pluralStringResource(R.plurals.home_count, count, count)
    Box(
        modifier = Modifier
            .heightIn(min = 28.dp)
            .widthIn(min = 28.dp)
            .background(MaterialTheme.colorScheme.onSurface, PillShape)
            .padding(horizontal = 9.dp)
            // TalkBack은 숫자만이 아니라 "5개"로 읽는다
            .clearAndSetSemantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            count.toString(),
            style = tabularNums(MaterialTheme.typography.titleSmall).copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.background,
        )
    }
}
