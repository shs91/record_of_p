package com.recordofp.app.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import com.recordofp.app.domain.model.TriggerCatalog
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 개편안 2 §1.1·§1.3의 대비 약속을 지킨다 — WCAG 글자 4.5:1, 테두리·아이콘 3:1 */
class ThemeContrastTest {

    private fun contrast(a: Color, b: Color): Double {
        val la = a.luminance() + 0.05
        val lb = b.luminance() + 0.05
        return maxOf(la, lb).toDouble() / minOf(la, lb)
    }

    private fun assertContrast(name: String, fg: Color, bg: Color, min: Double) {
        val c = contrast(fg, bg)
        assertTrue("$name 대비 ${"%.2f".format(c)} < $min", c >= min)
    }

    @Test
    fun `보조 글자·연블루 위 블루·오류는 쓰이는 면 위에서 글자 대비 기준을 넘는다`() {
        for ((theme, s) in listOf("라이트" to LightColors, "다크" to DarkColors)) {
            assertContrast("$theme 보조 글자/종이", s.onSurfaceVariant, s.background, 4.5)
            assertContrast("$theme 보조 글자/칩", s.onSurfaceVariant, s.secondaryContainer, 4.5)
            assertContrast("$theme 보조 글자/카드", s.onSurfaceVariant, s.surface, 4.5)
            assertContrast("$theme 블루 글자/연블루", s.onPrimaryContainer, s.primaryContainer, 4.5)
            assertContrast("$theme 블루 글자/카드", s.onPrimaryContainer, s.surface, 4.5)
            assertContrast("$theme 블루 글자/종이", s.onPrimaryContainer, s.background, 4.5)
            assertContrast("$theme 오류/카드", s.error, s.surface, 4.5)
            assertContrast("$theme 오류/종이", s.error, s.background, 4.5)
            assertContrast("$theme 오류 면 글자", s.onErrorContainer, s.errorContainer, 4.5)
            assertContrast("$theme 경고 글자/경고 면", s.onTertiaryContainer, s.tertiaryContainer, 4.5)
            assertContrast("$theme 경고 버튼 글자", s.surface, s.onTertiaryContainer, 4.5)
            assertContrast("$theme 개수 pill", s.background, s.onSurface, 4.5)
        }
        assertContrast("라이트 흰 글자/오류", Color.White, LightColors.error, 4.5)
    }

    @Test
    fun `입력 테두리는 카드 위에서 3대1을 넘는다`() {
        assertContrast("라이트 테두리", LightColors.outline, LightColors.surface, 3.0)
        assertContrast("다크 테두리", DarkColors.outline, DarkColors.surface, 3.0)
    }

    @Test
    fun `완료 면과 스낵바의 글자는 글자 대비 기준을 넘는다`() {
        assertContrast("라이트 완료", OnSuccessLight, SuccessLight, 4.5)
        assertContrast("다크 완료", OnSuccessDark, SuccessDark, 4.5)
        for ((theme, s) in listOf("라이트" to LightColors, "다크" to DarkColors)) {
            assertContrast("$theme 스낵바 글자", s.inverseOnSurface, s.inverseSurface, 4.5)
            assertContrast("$theme 스낵바 실행 취소", s.inversePrimary, s.inverseSurface, 4.5)
        }
        assertContrast("라이트 스낵바 체크 원", InverseSuccessLight, InverseSurfaceLight, 3.0)
        assertContrast("다크 스낵바 체크 원", InverseSuccessDark, InverseSurfaceDark, 3.0)
    }

    @Test
    fun `브랜드 프리셋을 뺀 카탈로그 카테고리마다 라이트·다크 타일 색이 있다`() {
        for (entry in TriggerCatalog.entries) {
            if (entry.isBrandPreset) {
                assertNull(entry.id, CategoryPalette.of(entry.id, dark = false))
            } else {
                assertNotNull(entry.id, CategoryPalette.of(entry.id, dark = false))
                assertNotNull(entry.id, CategoryPalette.of(entry.id, dark = true))
            }
        }
    }

    @Test
    fun `카테고리 ink는 tint 위에서 라이트 4·5대1·다크 6대1을, 카드 위에서 4·5대1을 넘는다`() {
        for (entry in TriggerCatalog.entries.filterNot { it.isBrandPreset }) {
            val light = CategoryPalette.of(entry.id, dark = false)!!
            val dark = CategoryPalette.of(entry.id, dark = true)!!
            assertContrast("${entry.id} 라이트 타일", light.ink, light.tint, 4.5)
            assertContrast("${entry.id} 다크 타일", dark.ink, dark.tint, 6.0)
            // 에디터의 선택 안 된 칩은 카드 위에 카테고리 ink 아이콘을 그린다
            assertContrast("${entry.id} 라이트 카드", light.ink, LightColors.surface, 4.5)
            assertContrast("${entry.id} 다크 카드", dark.ink, DarkColors.surface, 4.5)
        }
    }
}
