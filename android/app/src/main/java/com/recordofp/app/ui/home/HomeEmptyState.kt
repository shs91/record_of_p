package com.recordofp.app.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.recordofp.app.R
import com.recordofp.app.ui.common.TriggerTile
import com.recordofp.app.ui.common.categoryVisual
import com.recordofp.app.ui.theme.AppTextStyles
import com.recordofp.app.ui.theme.Spacing

/** 빈 상태 — 연블루 원 안의 P-핀, 둘레에 카테고리 타일 셋, 제목과 두 줄 본문 (개편안 2 §2, 디자인 시스템 §1.3 브랜드 그래픽) */
@Composable
internal fun HomeEmptyState(modifier: Modifier = Modifier) {
    Column(
        // 높이는 내용만큼 — 세로 가운데·스크롤은 HomeScreen이 맡는다.
        // 아래 88dp = 확장 FAB(56) + 여백(32): 스크롤 끝에서 본문이 FAB에 가리지 않게
        modifier = modifier.padding(start = Spacing.xxl, end = Spacing.xxl, bottom = 88.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(28.dp),
    ) {
        EmptyGraphic()
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            Text(stringResource(R.string.home_empty_title), style = AppTextStyles.emptyTitle, textAlign = TextAlign.Center)
            Text(
                stringResource(R.string.home_empty_body),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

/** 장식 그래픽 — TalkBack은 건너뛴다 */
@Composable
private fun EmptyGraphic() {
    Box(Modifier.size(width = 220.dp, height = 196.dp).clearAndSetSemantics {}) {
        Box(
            modifier = Modifier
                .offset(x = 42.dp, y = 20.dp)
                .size(136.dp)
                .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painterResource(R.drawable.ic_pin_mark),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(84.dp),
            )
        }
        TriggerTile(
            categoryVisual("convenience"), size = 44.dp, cornerRadius = 14.dp, iconSize = 22.dp,
            modifier = Modifier.offset(x = 0.dp, y = 8.dp).rotate(-8f),
        )
        TriggerTile(
            categoryVisual("pharmacy"), size = 44.dp, cornerRadius = 14.dp, iconSize = 22.dp,
            modifier = Modifier.align(Alignment.TopEnd).offset(x = (-2).dp, y = 52.dp).rotate(10f),
        )
        TriggerTile(
            categoryVisual("cafe"), size = 44.dp, cornerRadius = 14.dp, iconSize = 22.dp,
            modifier = Modifier.align(Alignment.BottomStart).offset(x = 22.dp, y = (-6).dp).rotate(6f),
        )
    }
}
