package com.recordofp.app.ui.common

import androidx.annotation.DrawableRes
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.recordofp.app.R
import com.recordofp.app.domain.model.TriggerCatalog
import com.recordofp.app.domain.model.TriggerSpec
import com.recordofp.app.domain.model.TriggerType
import com.recordofp.app.ui.theme.TileColors
import com.recordofp.app.ui.theme.categoryTileColors

/**
 * 트리거를 화면에 그리는 방법 — 타일 색·아이콘·이름 (개편안 2 §1.3·§2·§4).
 * 홈 카드, 에디터 칩, 주변 보기 그룹 머리가 함께 쓴다. 카탈로그의 이모지는 화면에서 쓰지 않는다(§4).
 */
sealed interface TriggerVisual {
    /** 카테고리. 카탈로그에서 빠진 옛 id도 여기로 온다 — 고유 색 없이 검색 아이콘과 id 그대로 그린다 */
    data class Category(val categoryId: String) : TriggerVisual

    /** 카탈로그의 브랜드 프리셋(다이소·올리브영) — 브랜드 색(칩)과 쇼핑백 아이콘 */
    data class BrandPreset(val categoryId: String) : TriggerVisual

    /** 직접 입력한 브랜드 — 입력한 대소문자 그대로 보인다 */
    data class BrandKeyword(val keyword: String) : TriggerVisual

    /** 특정 지점 — 연블루 */
    data class Place(val name: String) : TriggerVisual
}

/** 카탈로그 id → 카테고리 또는 브랜드 프리셋 */
fun categoryVisual(categoryId: String): TriggerVisual =
    if (TriggerCatalog.byId(categoryId)?.isBrandPreset == true) {
        TriggerVisual.BrandPreset(categoryId)
    } else {
        TriggerVisual.Category(categoryId)
    }

fun TriggerSpec.visual(): TriggerVisual = when (type) {
    TriggerType.CATEGORY -> categoryVisual(categoryId.orEmpty())
    TriggerType.BRAND -> TriggerVisual.BrandKeyword(brandKeyword.orEmpty())
    TriggerType.PLACE -> TriggerVisual.Place(placeName.orEmpty())
}

/** Material Symbols Rounded 아이콘 (개편안 2 §4, tools/design/material_symbols.py) */
@DrawableRes
fun TriggerVisual.iconRes(): Int = when (this) {
    is TriggerVisual.Category -> when (categoryId) {
        "convenience" -> R.drawable.ic_cat_convenience
        "mart" -> R.drawable.ic_cat_mart
        "pharmacy" -> R.drawable.ic_cat_pharmacy
        "bank" -> R.drawable.ic_cat_bank
        "post" -> R.drawable.ic_cat_post
        "fuel" -> R.drawable.ic_cat_fuel
        "laundry" -> R.drawable.ic_cat_laundry
        "cafe" -> R.drawable.ic_cat_cafe
        "hospital" -> R.drawable.ic_cat_hospital
        "subway" -> R.drawable.ic_cat_subway
        else -> R.drawable.ic_trigger_search // 카탈로그에서 빠진 옛 id
    }
    is TriggerVisual.BrandPreset -> R.drawable.ic_trigger_brand
    is TriggerVisual.BrandKeyword -> R.drawable.ic_trigger_search
    is TriggerVisual.Place -> R.drawable.ic_trigger_place
}

/** 화면에 보일 이름. 카탈로그에 없는 id는 id 그대로 — 다른 카테고리 이름으로 잘못 보이지 않게 */
@Composable
fun TriggerVisual.label(): String = when (this) {
    is TriggerVisual.Category -> catalogLabel(categoryId)
    is TriggerVisual.BrandPreset -> catalogLabel(categoryId)
    is TriggerVisual.BrandKeyword -> keyword
    is TriggerVisual.Place -> name
}

@Composable
private fun catalogLabel(categoryId: String): String =
    if (TriggerCatalog.byId(categoryId) != null) stringResource(catalogLabelRes(categoryId)) else categoryId

/** 타일 색: 카테고리는 고유 색, 브랜드는 칩 색, 특정 지점은 연블루 (개편안 2 §1.3) */
@Composable
fun TriggerVisual.tileColors(): TileColors {
    val scheme = MaterialTheme.colorScheme
    val neutral = TileColors(scheme.secondaryContainer, scheme.onSecondaryContainer)
    return when (this) {
        is TriggerVisual.Category -> categoryTileColors(categoryId) ?: neutral
        is TriggerVisual.BrandPreset, is TriggerVisual.BrandKeyword -> neutral
        is TriggerVisual.Place -> TileColors(scheme.primaryContainer, scheme.onPrimaryContainer)
    }
}
