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
import java.util.Locale

/**
 * 거리 배지 — 연블루 pill + 블루 숫자, tabular-nums (개편안 §2).
 * 숫자는 onPrimaryContainer — 연블루 위 4.5:1 (개편안 2 §1.1). 에디터 장소 검색 결과가 쓴다.
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
fun formatDistance(distanceM: Int): String = distanceParts(distanceM).let { (number, unit) -> number + unit }

/**
 * 거리를 숫자와 단위로 나눈다 — 주변 보기는 숫자를 크게, 단위를 작게 그린다 (개편안 2 §2).
 * 소수점은 기기 언어와 상관없이 점으로 쓴다(앱 문자열은 ko·en뿐이다).
 */
fun distanceParts(distanceM: Int): Pair<String, String> =
    if (distanceM < 1000) "$distanceM" to "m" else "%.1f".format(Locale.ROOT, distanceM / 1000.0) to "km"

/** 1km를 넘으면 걸어서 들르기엔 멀다 — 주변 보기는 숫자를 보조 글자색으로 낮춘다 (개편안 2 §2) */
fun isFarDistance(distanceM: Int): Boolean = distanceM > FAR_DISTANCE_M

private const val FAR_DISTANCE_M = 1000

/** 숫자 열 정렬(tnum) — Pretendard가 지원한다 */
fun tabularNums(base: TextStyle): TextStyle = base.copy(fontFeatureSettings = "tnum")
