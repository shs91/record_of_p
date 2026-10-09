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
            PlaceRequest(spec.matchKey, point, spec.placeName)
        }
    }
}
