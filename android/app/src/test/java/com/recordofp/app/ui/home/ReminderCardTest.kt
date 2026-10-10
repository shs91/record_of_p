package com.recordofp.app.ui.home

import com.recordofp.app.domain.model.TriggerSpec
import com.recordofp.app.domain.model.TriggerType
import com.recordofp.app.ui.common.TriggerVisual
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ReminderCardTest {

    private val brand = TriggerSpec(type = TriggerType.BRAND, brandKeyword = "GS25")
    private val daiso = TriggerSpec(type = TriggerType.CATEGORY, categoryId = "daiso")
    private val laundry = TriggerSpec(type = TriggerType.CATEGORY, categoryId = "laundry")
    private val place = TriggerSpec(type = TriggerType.PLACE, placeName = "크린토피아 역삼점")

    @Test
    fun `카드 타일은 카테고리가 있으면 첫 카테고리를 따른다`() {
        assertEquals(TriggerVisual.Category("laundry"), listOf(brand, place, laundry).leadVisual())
    }

    @Test
    fun `카테고리가 없으면 특정 지점, 그것도 없으면 첫 브랜드를 따른다 - 프리셋은 브랜드다`() {
        assertEquals(TriggerVisual.Place("크린토피아 역삼점"), listOf(daiso, brand, place).leadVisual())
        assertEquals(TriggerVisual.BrandPreset("daiso"), listOf(daiso, brand).leadVisual())
        assertEquals(TriggerVisual.BrandKeyword("GS25"), listOf(brand, daiso).leadVisual())
    }

    @Test
    fun `트리거가 없으면 타일이 없다`() {
        assertNull(emptyList<TriggerSpec>().leadVisual())
    }
}
