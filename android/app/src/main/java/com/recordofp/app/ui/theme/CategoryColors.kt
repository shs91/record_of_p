package com.recordofp.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

/** 타일 한 쌍 — 바탕(tint)과 그 위의 아이콘·글자(ink) */
@Immutable
data class TileColors(val tint: Color, val ink: Color)

/**
 * 카테고리 10색 (디자인 시스템 §2.6, 개편안 2 §1.3). Material 슬롯이 아니라서 카탈로그 id로 찾는다.
 * 도메인 카탈로그(TriggerCatalog)에는 색을 넣지 않는다. 색은 아이콘·이름과 함께 쓴다 — 색만으로 구분하지 않는다.
 * ink는 tint 위에서 라이트 4.5:1, 다크 6:1을 넘는다 — ThemeContrastTest가 지킨다.
 */
object CategoryPalette {
    private val light: Map<String, TileColors> = mapOf(
        "convenience" to TileColors(Color(0xFFE5F6EC), Color(0xFF137444)),
        "mart" to TileColors(Color(0xFFFFF0E2), Color(0xFFB4520A)),
        "pharmacy" to TileColors(Color(0xFFFCEAF3), Color(0xFFB42A72)),
        "bank" to TileColors(Color(0xFFECEEFC), Color(0xFF3A49B8)),
        "post" to TileColors(Color(0xFFFDECE7), Color(0xFFB83A18)),
        "fuel" to TileColors(Color(0xFFE2F4F4), Color(0xFF0C7276)),
        "laundry" to TileColors(Color(0xFFF1EAFD), Color(0xFF6A3CBC)),
        "cafe" to TileColors(Color(0xFFF4EDE6), Color(0xFF835532)),
        "hospital" to TileColors(Color(0xFFE3F2F9), Color(0xFF0B6A8F)),
        "subway" to TileColors(Color(0xFFEDF5DE), Color(0xFF4F7212)),
    )

    private val dark: Map<String, TileColors> = mapOf(
        "convenience" to TileColors(Color(0xFF163024), Color(0xFF5CCB8C)),
        "mart" to TileColors(Color(0xFF3A2614), Color(0xFFFF9F57)),
        "pharmacy" to TileColors(Color(0xFF3A1A2D), Color(0xFFF27DBB)),
        "bank" to TileColors(Color(0xFF1F2444), Color(0xFF9AA5F7)),
        "post" to TileColors(Color(0xFF3B1F17), Color(0xFFFF8C69)),
        "fuel" to TileColors(Color(0xFF123335), Color(0xFF52C7CC)),
        "laundry" to TileColors(Color(0xFF2A1F42), Color(0xFFB99AF7)),
        "cafe" to TileColors(Color(0xFF2F251D), Color(0xFFD9A67E)),
        "hospital" to TileColors(Color(0xFF12303D), Color(0xFF5CC0E6)),
        "subway" to TileColors(Color(0xFF243016), Color(0xFFB0D66A)),
    )

    /** 고유 색이 없으면(브랜드 프리셋, 카탈로그에 없는 id) null — 호출부가 칩 색을 쓴다 */
    fun of(categoryId: String, dark: Boolean): TileColors? = (if (dark) this.dark else light)[categoryId]
}

/** 지금 테마(라이트·다크)의 카테고리 타일 색 */
@Composable
fun categoryTileColors(categoryId: String): TileColors? = CategoryPalette.of(categoryId, isSystemInDarkTheme())
