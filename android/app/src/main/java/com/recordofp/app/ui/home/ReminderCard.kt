package com.recordofp.app.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.recordofp.app.R
import com.recordofp.app.domain.model.Reminder
import com.recordofp.app.domain.model.TriggerSpec
import com.recordofp.app.ui.common.TriggerTile
import com.recordofp.app.ui.common.TriggerVisual
import com.recordofp.app.ui.common.label
import com.recordofp.app.ui.common.visual
import com.recordofp.app.ui.theme.Spacing
import com.recordofp.app.ui.theme.onSuccessColor
import com.recordofp.app.ui.theme.successColor

/** 카드 타일이 따를 트리거: 카테고리 → 특정 지점 → 브랜드(프리셋 포함) 순으로 첫 것 (개편안 2 §2 기록 카드) */
internal fun List<TriggerSpec>.leadVisual(): TriggerVisual? {
    val visuals = map { it.visual() }
    return visuals.firstOrNull { it is TriggerVisual.Category }
        ?: visuals.firstOrNull { it is TriggerVisual.Place }
        ?: visuals.firstOrNull()
}

/**
 * 기록 한 줄 — 시작→끝 스와이프로 완료하고, TalkBack은 카드의 사용자 지정 동작으로 완료한다 (§4.1, §8).
 * 카드: 왼쪽 48dp 트리거 타일 + 제목 + 트리거 이름을 " · "로 이은 줄 (개편안 2 §2)
 */
@Composable
internal fun ReminderRow(
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
            ReminderCardContent(item)
        }
    }
}

@Composable
private fun ReminderCardContent(item: Reminder) {
    Row(
        modifier = Modifier.padding(start = 14.dp, end = Spacing.m, top = 14.dp, bottom = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item.triggers.leadVisual()?.let { TriggerTile(it, size = 48.dp, cornerRadius = 16.dp, iconSize = 24.dp) }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                item.title,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (item.triggers.isNotEmpty()) {
                Text(
                    item.triggers.map { it.visual().label() }.joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
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
