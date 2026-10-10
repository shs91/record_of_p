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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
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
import com.recordofp.app.ui.theme.successColor

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
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
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
                    items(items, key = { it.id }) { item ->
                        ReminderRow(
                            item = item,
                            onComplete = { viewModel.complete(item.id) },
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
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            if (value != SwipeToDismissBoxValue.Settled) { onComplete(); true } else false
        },
    )
    SwipeToDismissBox(
        state = dismissState,
        modifier = modifier,
        backgroundContent = {
            // 완료 스와이프: 초록 배경 + 체크 (개편안 §2). 방향에 따라 정렬을 맞춘다.
            val alignment = when (dismissState.dismissDirection) {
                SwipeToDismissBoxValue.EndToStart -> Alignment.CenterEnd
                else -> Alignment.CenterStart
            }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(MaterialTheme.shapes.medium)
                    .background(successColor())
                    .padding(horizontal = 20.dp),
                contentAlignment = alignment,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Filled.Check, contentDescription = null, tint = MaterialTheme.colorScheme.surface)
                    Text(
                        stringResource(R.string.home_completed),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.surface,
                    )
                }
            }
        },
    ) {
        Card(
            onClick = onClick,
            modifier = Modifier.fillMaxWidth(),
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
