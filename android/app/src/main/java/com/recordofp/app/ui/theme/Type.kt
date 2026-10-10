package com.recordofp.app.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.recordofp.app.R

/**
 * Pretendard 정적 4웨이트 (디자인 개편안 §1). 진한 잉크색 타이포가 위계를 만든다:
 * 큰 타이틀 28 Bold → 카드 제목 17 SemiBold → 본문 15 → 보조 13 → 섹션 레이블 12.
 */
val Pretendard = FontFamily(
    Font(R.font.pretendard_regular, FontWeight.Normal),
    Font(R.font.pretendard_medium, FontWeight.Medium),
    Font(R.font.pretendard_semibold, FontWeight.SemiBold),
    Font(R.font.pretendard_bold, FontWeight.Bold),
)

private fun style(
    size: Int,
    weight: FontWeight,
    lineHeight: Int,
    letterSpacing: Double = -0.005,
) = TextStyle(
    fontFamily = Pretendard,
    fontSize = size.sp,
    fontWeight = weight,
    lineHeight = lineHeight.sp,
    letterSpacing = letterSpacing.em,
)

val AppTypography = Typography(
    // 홈 큰 타이틀 (§2 홈)
    headlineMedium = style(28, FontWeight.Bold, 36, letterSpacing = -0.02),
    // 온보딩 타이틀
    headlineSmall = style(24, FontWeight.Bold, 32, letterSpacing = -0.02),
    // TopAppBar 타이틀
    titleLarge = style(18, FontWeight.SemiBold, 24, letterSpacing = -0.01),
    // 카드(기록) 제목
    titleMedium = style(17, FontWeight.SemiBold, 24, letterSpacing = -0.01),
    // 행 제목·강조 본문
    titleSmall = style(15, FontWeight.SemiBold, 21),
    // 기본 본문
    bodyLarge = style(15, FontWeight.Normal, 22),
    bodyMedium = style(14, FontWeight.Normal, 20),
    // 보조 캡션
    bodySmall = style(13, FontWeight.Normal, 18),
    // 버튼
    labelLarge = style(15, FontWeight.SemiBold, 20),
    // 섹션 레이블 12sp (§2 에디터)
    labelMedium = style(12, FontWeight.SemiBold, 16, letterSpacing = 0.02),
    // 칩·배지
    labelSmall = style(12, FontWeight.Medium, 16, letterSpacing = 0.0),
)

/**
 * Material 슬롯 밖의 글자 스타일 (디자인 시스템 §2.2 추가 스타일, 개편안 2 §2).
 * 굵기만 바꿀 때는 슬롯 스타일의 copy(fontWeight = …)를 쓴다 — 크기를 새로 만들지 않는다.
 */
object AppTextStyles {
    /** 판단에 쓰는 숫자 — 주변 보기 거리 22/26 Bold, tabular */
    val numberLarge: TextStyle =
        style(22, FontWeight.Bold, 26, letterSpacing = -0.02).copy(fontFeatureSettings = "tnum")

    /** 빈 상태 제목 20/28 Bold */
    val emptyTitle: TextStyle = style(20, FontWeight.Bold, 28, letterSpacing = -0.01)
}
