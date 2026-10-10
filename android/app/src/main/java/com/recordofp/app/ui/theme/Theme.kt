package com.recordofp.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * 고정 브랜드 테마 (디자인 시스템 §2).
 * 다이나믹 컬러를 제거하고 라이트/다크 모두 고정 팔레트를 쓴다 — 콘텐츠가 주인공,
 * 크롬은 물러난다. 토큰 값은 Color.kt·CategoryColors.kt, 타입은 Type.kt, 형태는 Shape.kt, 간격·모션은 Spacing.kt·Motion.kt.
 * 스킴은 ThemeContrastTest가 대비를 확인하도록 internal로 둔다.
 */
internal val LightColors = lightColorScheme(
    primary = BlueLight,
    onPrimary = Color.White,
    primaryContainer = BlueContainerLight,
    onPrimaryContainer = BlueTextLight,
    secondary = ChipInkLight,
    onSecondary = Color.White,
    secondaryContainer = ChipLight,
    onSecondaryContainer = ChipInkLight,
    tertiaryContainer = AmberContainerLight,
    onTertiaryContainer = AmberInkLight,
    background = PaperLight,
    onBackground = InkLight,
    surface = CardLight,
    onSurface = InkLight,
    surfaceVariant = ChipLight,
    onSurfaceVariant = SubInkLight,
    // 다이얼로그·타임피커 등 컨테이너 계열도 종이/카드 톤으로 정합
    surfaceContainerLowest = CardLight,
    surfaceContainerLow = CardLight,
    surfaceContainer = CardLight,
    surfaceContainerHigh = CardLight,
    surfaceContainerHighest = ChipLight,
    error = ErrorLight,
    onError = Color.White,
    errorContainer = Color(0xFFFDE8EA),
    onErrorContainer = Color(0xFFB92330),
    outline = OutlineLight,
    outlineVariant = LineLight,
    // 스낵바 (개편안 2 §1.2)
    inverseSurface = InverseSurfaceLight,
    inverseOnSurface = InverseOnSurfaceLight,
    inversePrimary = InversePrimaryLight,
)

internal val DarkColors = darkColorScheme(
    primary = BlueDark,
    onPrimary = Color(0xFF0B1D3A),
    primaryContainer = BlueContainerDark,
    onPrimaryContainer = BlueDark,
    secondary = ChipInkDark,
    onSecondary = Color(0xFF11151A),
    secondaryContainer = ChipDark,
    onSecondaryContainer = ChipInkDark,
    tertiaryContainer = AmberContainerDark,
    onTertiaryContainer = AmberInkDark,
    background = PaperDark,
    onBackground = InkDark,
    surface = CardDark,
    onSurface = InkDark,
    surfaceVariant = ChipDark,
    onSurfaceVariant = SubInkDark,
    surfaceContainerLowest = PaperDark,
    surfaceContainerLow = CardDark,
    surfaceContainer = CardDark,
    surfaceContainerHigh = Color(0xFF232A33),
    surfaceContainerHighest = Color(0xFF2C3440),
    error = ErrorDark,
    onError = Color(0xFF2B0A0D),
    errorContainer = Color(0xFF3A1D20),
    onErrorContainer = Color(0xFFFFB3B8),
    outline = OutlineDark,
    outlineVariant = LineDark,
    inverseSurface = InverseSurfaceDark,
    inverseOnSurface = InverseOnSurfaceDark,
    inversePrimary = InversePrimaryDark,
)

@Composable
fun RecordOfPTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = AppTypography,
        shapes = AppShapes,
        content = content,
    )
}
