package com.recordofp.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * 고정 브랜드 팔레트 (디자인 시스템 §2.1).
 * 흰 종이(background) 위의 잉크(onSurface) — 블루는 "행동"에만 아껴 쓴다.
 * 다이나믹 컬러는 쓰지 않는다: 기록 앱의 종이는 기기 벽지를 따라가지 않는다.
 * 라이트의 보조 글자·연블루 위 블루·오류·테두리·성공은 WCAG 대비에 맞춰 보정했다(개편안 2 §1.1) — ThemeContrastTest가 지킨다.
 */

// ── 라이트 ──────────────────────────────────────────────
val PaperLight = Color(0xFFF7F8FA) // background: 종이
val CardLight = Color(0xFFFFFFFF) // surface: 카드
val InkLight = Color(0xFF191F28) // onSurface: 잉크
val SubInkLight = Color(0xFF636E7C) // onSurfaceVariant: 보조
val BlueLight = Color(0xFF1B6EF3) // primary: 행동
val BlueContainerLight = Color(0xFFEAF1FE) // primaryContainer: 연블루
val BlueTextLight = Color(0xFF1762D8) // onPrimaryContainer: 연블루·카드 위 블루 글자(거리 숫자, 저장·추가)
val ChipLight = Color(0xFFF2F4F6) // secondaryContainer: 회색 pill 칩
val ChipInkLight = Color(0xFF4E5968) // onSecondaryContainer
val AmberContainerLight = Color(0xFFFFF4E0) // tertiaryContainer: 보호 배너(경고≠오류)
val AmberInkLight = Color(0xFF96660A) // onTertiaryContainer
val ErrorLight = Color(0xFFD62C3A)
val LineLight = Color(0xFFE5E8EB) // outlineVariant: 헤어라인
val OutlineLight = Color(0xFF878F9B) // outline: 입력 필드 외곽선(카드 위 3:1)
val SuccessLight = Color(0xFF0A8049) // 완료 스와이프 바탕
val OnSuccessLight = Color(0xFFFFFFFF)
val InverseSurfaceLight = Color(0xFF191F28) // 스낵바 바탕
val InverseOnSurfaceLight = Color(0xFFF7F8FA)
val InversePrimaryLight = Color(0xFF9EC1FF) // 스낵바 [실행 취소]
val InverseSuccessLight = Color(0xFF2ED07E) // 스낵바 앞 성공 체크 원

// ── 다크 ────────────────────────────────────────────────
val PaperDark = Color(0xFF101418)
val CardDark = Color(0xFF1B2027)
val InkDark = Color(0xFFE9EDF2)
val SubInkDark = Color(0xFF8B95A1)
val BlueDark = Color(0xFF5B95F8) // 채도 낮춘 블루 — 어둠 속 눈부심 방지. onPrimaryContainer도 이 값
val BlueContainerDark = Color(0xFF1E2C42)
val ChipDark = Color(0xFF242B34)
val ChipInkDark = Color(0xFFB0B8C1)
val AmberContainerDark = Color(0xFF332916)
val AmberInkDark = Color(0xFFF0C070)
val ErrorDark = Color(0xFFFF6B6B)
val LineDark = Color(0xFF232A33)
val OutlineDark = Color(0xFF646E7C)
val SuccessDark = Color(0xFF2ED07E)
val OnSuccessDark = Color(0xFF1B2027)
val InverseSurfaceDark = Color(0xFFE9EDF2)
val InverseOnSurfaceDark = Color(0xFF101418)
val InversePrimaryDark = Color(0xFF1762D8)
val InverseSuccessDark = Color(0xFF0A8049)

// Material 스킴에 없는 시맨틱 컬러는 테마 헬퍼로 제공한다

/** 완료 스와이프 바탕, 진단 APPLIED·PASS 도트 */
@Composable
fun successColor(): Color = if (isSystemInDarkTheme()) SuccessDark else SuccessLight

/** 성공 면 위의 글자·아이콘 — 완료 스와이프의 체크·"완료" */
@Composable
fun onSuccessColor(): Color = if (isSystemInDarkTheme()) OnSuccessDark else OnSuccessLight

/** 스낵바(inverseSurface) 위의 성공 체크 원 */
@Composable
fun inverseSuccessColor(): Color = if (isSystemInDarkTheme()) InverseSuccessDark else InverseSuccessLight
