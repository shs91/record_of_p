package com.recordofp.app.ui.common

import com.recordofp.app.R
import com.recordofp.app.domain.model.TriggerCatalog
import com.recordofp.app.domain.model.TriggerSpec
import com.recordofp.app.domain.model.TriggerType
import com.recordofp.app.ui.theme.CategoryPalette
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class TriggerVisualTest {

    @Test
    fun `카탈로그 카테고리는 Category, 브랜드 프리셋은 BrandPreset이 된다`() {
        for (entry in TriggerCatalog.entries) {
            val visual = TriggerSpec(type = TriggerType.CATEGORY, categoryId = entry.id).visual()
            val expected = if (entry.isBrandPreset) {
                TriggerVisual.BrandPreset(entry.id)
            } else {
                TriggerVisual.Category(entry.id)
            }
            assertEquals(expected, visual)
        }
    }

    @Test
    fun `카테고리마다 아이콘이 다르고 브랜드·지점은 공용 아이콘을 쓴다`() {
        val categoryIcons = TriggerCatalog.entries.filterNot { it.isBrandPreset }
            .map { TriggerVisual.Category(it.id).iconRes() }
        assertEquals(categoryIcons.size, categoryIcons.toSet().size)
        assertFalse(R.drawable.ic_trigger_search in categoryIcons)
        assertEquals(R.drawable.ic_trigger_brand, TriggerVisual.BrandPreset("daiso").iconRes())
        assertEquals(R.drawable.ic_trigger_search, TriggerVisual.BrandKeyword("GS25").iconRes())
        assertEquals(R.drawable.ic_trigger_place, TriggerVisual.Place("크린토피아 역삼점").iconRes())
    }

    @Test
    fun `카탈로그에서 빠진 옛 카테고리 id도 그릴 수 있다 - 고유 색 없이 검색 아이콘`() {
        val visual = TriggerSpec(type = TriggerType.CATEGORY, categoryId = "removed_cat").visual()
        assertEquals(TriggerVisual.Category("removed_cat"), visual)
        assertEquals(R.drawable.ic_trigger_search, visual.iconRes())
        assertNull(CategoryPalette.of("removed_cat", dark = false))
    }

    @Test
    fun `값이 빠진 브랜드·지점 트리거도 빈 이름으로 그린다`() {
        assertEquals(TriggerVisual.BrandKeyword(""), TriggerSpec(type = TriggerType.BRAND).visual())
        assertEquals(TriggerVisual.Place(""), TriggerSpec(type = TriggerType.PLACE).visual())
        assertEquals(TriggerVisual.Category(""), TriggerSpec(type = TriggerType.CATEGORY).visual())
    }
}
