package com.recordofp.app.ui.theme

import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FastOutSlowInEasing

/** 모션 (디자인 시스템 §2.5, 개편안 2 §1.4) */
object Motion {
    /** 짧은 전환 — 칩 선택 */
    const val SHORT_MS = 150

    /** 표준 — 카드 등장·제거, 스낵바 */
    const val STANDARD_MS = 250

    /** Material 표준 이징 */
    val easing: Easing = FastOutSlowInEasing
}
