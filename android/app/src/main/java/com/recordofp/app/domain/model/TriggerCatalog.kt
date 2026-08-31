package com.recordofp.app.domain.model

/** POI 해석 방식: 카카오 카테고리 그룹 코드 검색 vs 키워드 검색 (설계 §3.3) */
enum class PoiResolution { KAKAO_CODE, KEYWORD }

data class CatalogEntry(
    val id: String,
    val emoji: String,
    val resolution: PoiResolution,
    /** KAKAO_CODE면 그룹 코드(CS2 등), KEYWORD면 검색어 */
    val query: String,
    val isBrandPreset: Boolean = false,
)

/**
 * v1 빌트인 트리거 카탈로그. 표시명은 strings.xml에서 id로 매핑한다.
 * 코드에 내장하며 추가·변경은 앱 업데이트로 배포한다 (설계 §3.3).
 */
object TriggerCatalog {
    val entries: List<CatalogEntry> = listOf(
        CatalogEntry("convenience", "🏪", PoiResolution.KAKAO_CODE, "CS2"),
        CatalogEntry("mart", "🛒", PoiResolution.KAKAO_CODE, "MT1"),
        CatalogEntry("pharmacy", "💊", PoiResolution.KAKAO_CODE, "PM9"),
        CatalogEntry("bank", "🏦", PoiResolution.KAKAO_CODE, "BK9"),
        CatalogEntry("post", "📮", PoiResolution.KEYWORD, "우체국"),
        CatalogEntry("fuel", "⛽", PoiResolution.KAKAO_CODE, "OL7"),
        CatalogEntry("laundry", "🧺", PoiResolution.KEYWORD, "세탁소"),
        CatalogEntry("cafe", "☕", PoiResolution.KAKAO_CODE, "CE7"),
        CatalogEntry("hospital", "🏥", PoiResolution.KAKAO_CODE, "HP8"),
        CatalogEntry("subway", "🚇", PoiResolution.KAKAO_CODE, "SW8"),
        CatalogEntry("daiso", "🧰", PoiResolution.KEYWORD, "다이소", isBrandPreset = true),
        CatalogEntry("oliveyoung", "🧴", PoiResolution.KEYWORD, "올리브영", isBrandPreset = true),
    )

    private val byId = entries.associateBy { it.id }

    fun byId(id: String): CatalogEntry? = byId[id]
}
