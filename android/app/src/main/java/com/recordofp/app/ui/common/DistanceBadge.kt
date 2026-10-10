package com.recordofp.app.ui.common

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import com.recordofp.app.ui.theme.PillShape

/**
 * 거리 배지 — 연블루 pill + 블루 숫자, tabular-nums (개편안 §2).
 * 숫자는 onPrimaryContainer — 연블루 위 4.5:1 (개편안 2 §1.1). 에디터 장소 검색 결과와 주변 보기 POI 행이 함께 쓴다.
 */
@Composable
fun DistanceBadge(distanceM: Int) {
    Surface(shape = PillShape, color = MaterialTheme.colorScheme.primaryContainer) {
        Text(
            text = formatDistance(distanceM),
            style = tabularNums(MaterialTheme.typography.labelSmall),
            color = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
        )
    }
}

/** 999m까지는 m, 그 위는 km 한 자리 — 배지 폭을 짧게 유지한다 */
fun formatDistance(distanceM: Int): String =
    if (distanceM < 1000) "${distanceM}m" else "%.1fkm".format(distanceM / 1000.0)

/** 숫자 열 정렬(tnum) — Pretendard가 지원한다 */
fun tabularNums(base: TextStyle): TextStyle = base.copy(fontFeatureSettings = "tnum")
