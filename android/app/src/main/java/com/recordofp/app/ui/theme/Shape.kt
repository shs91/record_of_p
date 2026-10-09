package com.recordofp.app.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/**
 * 형태 (디자인 개편안 §1): 카드 20dp 라운드, 칩은 pill, 그림자 대신 톤 차이.
 * medium이 카드의 기본 셰이프다 (Material3 Card = shapes.medium).
 */
val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp), // 입력 필드·작은 면
    medium = RoundedCornerShape(20.dp), // 카드
    large = RoundedCornerShape(24.dp), // 시트·다이얼로그
    extraLarge = RoundedCornerShape(28.dp),
)

/** 칩·배지·상태 pill 공용 셰이프 */
val PillShape = RoundedCornerShape(percent = 50)
