package com.recordofp.app.ui.theme

import androidx.compose.ui.unit.dp

/**
 * 간격 단계 (디자인 시스템 §2.4, 개편안 2 §1.4). 화면 코드는 이 단계에 있는 값을 토큰으로 쓴다.
 * 카드 사이 10, 칩 사이 6, 카드 안쪽 14처럼 단계 밖의 값은 그 컴포넌트 안에 둔다.
 */
object Spacing {
    val xxs = 4.dp
    val xs = 8.dp
    val s = 12.dp
    val m = 16.dp
    val l = 20.dp
    val xl = 24.dp
    val xxl = 32.dp

    /** 화면 좌우 여백 */
    val screen = l
}
