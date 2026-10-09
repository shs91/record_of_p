package com.recordofp.app.domain.engine

import com.recordofp.app.domain.model.GeoPoint

/** 센티널 펜스의 OS requestId·미러 키 — 리시버가 이벤트에 센티널이 있는지 볼 때도 쓴다 */
const val SENTINEL_FENCE_KEY = "sentinel"

enum class FenceKind { SENTINEL, POI, PLACE }

enum class FenceTransition { ENTER, DWELL, EXIT }

/** ReseedPlanner의 출력. 플랫폼 계층이 이를 GeofencingRequest로 변환한다. */
data class PlannedFence(
    /** 등록·차분 비교용 안정 키 */
    val key: String,
    val kind: FenceKind,
    val center: GeoPoint,
    val radiusM: Float,
    val transition: FenceTransition,
    /** DWELL일 때만 사용 */
    val loiteringDelayMs: Int? = null,
    /** 이 펜스가 대변하는 트리거 matchKey 집합 (N:M, 설계 §5.3) */
    val matchKeys: Set<String> = emptySet(),
    val poiName: String? = null,
    val poiId: String? = null,
)

/** 재배치 입력: 트리거 1개와 그 후보 POI 목록 (거리 오름차순 정렬 전제) */
data class TriggerCandidates(
    val matchKey: String,
    val isPlace: Boolean = false,
    val placePoint: GeoPoint? = null,
    val placeName: String? = null,
    val placeKakaoId: String? = null,
    val candidates: List<PoiCandidate> = emptyList(),
)

data class PoiCandidate(
    val id: String,
    val name: String,
    val point: GeoPoint,
    val distanceM: Double,
)

/** 펜스 종류별 전이 (설계 §6.3.5). 미러에는 전이 칼럼이 없어 OS를 되살릴 때도 이 규칙을 쓴다 */
fun FenceKind.transition(): FenceTransition = when (this) {
    FenceKind.SENTINEL -> FenceTransition.EXIT
    FenceKind.POI -> FenceTransition.DWELL
    FenceKind.PLACE -> FenceTransition.ENTER
}

/** DWELL(POI)만 체류 시간을 갖는다 */
fun FenceKind.loiteringDelayMs(): Int? = if (this == FenceKind.POI) EngineParams.LOITERING_DELAY_MS else null
