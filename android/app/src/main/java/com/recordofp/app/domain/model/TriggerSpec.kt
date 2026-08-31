package com.recordofp.app.domain.model

enum class TriggerType { CATEGORY, BRAND, PLACE }

/**
 * 리마인더가 어디서 울릴지를 정의한다. 한 리마인더에 여러 개를 붙일 수 있다
 * (예: "휴지 사기" → 편의점 + 대형마트).
 */
data class TriggerSpec(
    val id: Long = 0L,
    val reminderId: Long = 0L,
    val type: TriggerType,
    /** CATEGORY: TriggerCatalog의 항목 id */
    val categoryId: String? = null,
    /** BRAND: 사용자가 입력한 상호 키워드 (예: "GS25") */
    val brandKeyword: String? = null,
    /** PLACE: 사용자가 검색해 고정한 특정 지점 */
    val placeName: String? = null,
    val placeKakaoId: String? = null,
    val placePoint: GeoPoint? = null,
) {
    /** 지오펜스 등록·이벤트 매칭에 쓰는 안정 키 */
    val matchKey: String
        get() = when (type) {
            TriggerType.CATEGORY -> "cat:$categoryId"
            TriggerType.BRAND -> "brand:${brandKeyword?.trim()?.lowercase()}"
            TriggerType.PLACE -> "place:$id"
        }
}
