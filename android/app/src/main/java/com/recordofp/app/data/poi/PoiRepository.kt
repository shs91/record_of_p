package com.recordofp.app.data.poi

import com.recordofp.app.domain.engine.EngineParams
import com.recordofp.app.domain.engine.PoiCandidate
import com.recordofp.app.domain.model.GeoPoint
import com.recordofp.app.domain.model.PoiResolution
import javax.inject.Inject
import javax.inject.Singleton

/** POI 공급자 추상화 (설계 §8 — 지역 확장 대비). v1 구현은 카카오 단일. */
interface PoiRepository {
    /**
     * 중심 좌표 주변에서 트리거 후보 POI를 거리 오름차순으로 조회한다.
     * 실패 시 예외를 던진다 — 호출부(재배치)는 기존 등록 유지 + 백오프 재시도 (설계 §6.4).
     */
    suspend fun search(
        resolution: PoiResolution,
        query: String,
        center: GeoPoint,
        radiusM: Int = EngineParams.QUERY_RADIUS_M,
        maxResults: Int = EngineParams.PER_TRIGGER_CAP,
    ): List<PoiCandidate>
}

@Singleton
class KakaoPoiRepository @Inject constructor(
    private val api: KakaoLocalApi,
) : PoiRepository {

    override suspend fun search(
        resolution: PoiResolution,
        query: String,
        center: GeoPoint,
        radiusM: Int,
        maxResults: Int,
    ): List<PoiCandidate> {
        val out = mutableListOf<PoiCandidate>()
        var page = 1
        while (out.size < maxResults && page <= EngineParams.MAX_QUERY_PAGES) {
            val res = when (resolution) {
                PoiResolution.KAKAO_CODE -> api.searchByCategory(
                    categoryGroupCode = query,
                    lng = center.lng.toString(),
                    lat = center.lat.toString(),
                    radiusM = radiusM,
                    page = page,
                )
                PoiResolution.KEYWORD -> api.searchByKeyword(
                    query = query,
                    lng = center.lng.toString(),
                    lat = center.lat.toString(),
                    radiusM = radiusM,
                    page = page,
                )
            }
            out += res.documents.mapNotNull { it.toCandidate() }
            if (res.meta.isEnd) break
            page++
        }
        return out.take(maxResults)
    }

    private fun KakaoPlaceDto.toCandidate(): PoiCandidate? {
        val lat = y.toDoubleOrNull() ?: return null
        val lng = x.toDoubleOrNull() ?: return null
        return PoiCandidate(
            id = id,
            name = placeName,
            point = GeoPoint(lat = lat, lng = lng),
            distanceM = distance.toDoubleOrNull() ?: 0.0,
        )
    }
}
