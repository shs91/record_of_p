package com.recordofp.app.domain.engine

import com.recordofp.app.domain.model.GeoPoint
import com.recordofp.app.domain.model.PoiResolution
import com.recordofp.app.domain.model.TriggerCatalog
import com.recordofp.app.domain.model.TriggerSpec
import com.recordofp.app.domain.model.TriggerType

/** 재배치·주변 보기의 조회 단위. matchKey는 TriggerSpec.matchKey와 동일 규칙 (스펙 §5.3) */
sealed interface TriggerRequest { val matchKey: String }

data class QueryRequest(
    override val matchKey: String,
    val resolution: PoiResolution,
    val query: String,
) : TriggerRequest

data class PlaceRequest(
    override val matchKey: String,
    val point: GeoPoint,
    val name: String?,
    /** 카카오 지점 id — PLACE 펜스의 poiId가 되어 "같은 항목·같은 지점 24h" 쿨다운의 키가 된다 (§4.5, 검토 C1) */
    val kakaoId: String? = null,
) : TriggerRequest

/** 활성 TriggerSpec 목록을 matchKey 중복 제거된 조회 요청으로 해석한다 (스펙 §3.3, §6.4 쿼터 방어) */
class TriggerResolver {

    fun resolve(triggers: List<TriggerSpec>): List<TriggerRequest> =
        triggers.mapNotNull { toRequest(it) }.distinctBy { it.matchKey }

    private fun toRequest(spec: TriggerSpec): TriggerRequest? = when (spec.type) {
        TriggerType.CATEGORY -> {
            val entry = spec.categoryId?.let { TriggerCatalog.byId(it) } ?: return null
            QueryRequest(spec.matchKey, entry.resolution, entry.query)
        }
        TriggerType.BRAND -> {
            val keyword = spec.brandKeyword?.trim().takeUnless { it.isNullOrEmpty() } ?: return null
            QueryRequest(spec.matchKey, PoiResolution.KEYWORD, keyword)
        }
        TriggerType.PLACE -> {
            val point = spec.placePoint ?: return null
            // 편집 화면은 id 없는 옛 PLACE를 ""로 다시 저장할 수 있다 — 빈 id는 없는 것으로 본다
            PlaceRequest(spec.matchKey, point, spec.placeName, spec.placeKakaoId?.takeIf { it.isNotBlank() })
        }
    }
}
