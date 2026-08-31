package com.recordofp.app.domain.engine

/**
 * 오발화/미발화 튜닝 파라미터 전부가 모이는 파일 (설계 §10.2).
 * 필드 테스트 회차별 조정 이력을 커밋 메시지로 남긴다.
 *
 * iOS 포팅 시 교체값(설계 §6.3): POI_BUDGET=18, PER_TRIGGER_CAP=6, QUERY_RADIUS_M=1500.
 */
object EngineParams {
    // ── 지오펜스 예산 (설계 §6.1)
    const val GEOFENCE_LIMIT_TOTAL = 100
    const val SENTINEL_COUNT = 1
    const val SAFETY_MARGIN = 4
    const val POI_BUDGET = GEOFENCE_LIMIT_TOTAL - SENTINEL_COUNT - SAFETY_MARGIN // 95
    const val PER_TRIGGER_CAP = 20

    // ── 재배치 (설계 §6.2~6.3)
    const val SENTINEL_RADIUS_M = 1000f
    const val QUERY_RADIUS_M = 2000
    const val MAX_QUERY_PAGES = 2
    const val MERGE_DISTANCE_M = 50.0
    const val RESEED_MIN_INTERVAL_MS = 10 * 60_000L
    const val ITEM_CHANGE_COALESCE_MS = 30_000L
    const val APP_OPEN_RESEED_DISTANCE_M = 500.0
    const val APP_OPEN_RESEED_AGE_MS = 6 * 3_600_000L
    const val HEALTH_CHECK_INTERVAL_HOURS = 6L

    // ── 지오펜스 반경·전이 (설계 §6.3.5)
    const val CATEGORY_FENCE_RADIUS_M = 120f
    const val PLACE_FENCE_RADIUS_M = 150f
    /** 도보 통과(~170초 체류)는 잡고 차량 통과(~20초)는 거르는 값 */
    const val LOITERING_DELAY_MS = 60_000

    // ── 에디터 수동 장소 검색 (설계 §4.1 — 재배치 엔진의 QUERY_RADIUS_M과는 별개)
    const val PLACE_SEARCH_RADIUS_M = 20_000
    const val PLACE_SEARCH_MAX_RESULTS = 10

    // ── 알림 정책 기본값 (설계 §4.5)
    const val COOLDOWN_PER_ITEM_MS = 4 * 3_600_000L
    const val COOLDOWN_PER_ITEM_PLACE_MS = 24 * 3_600_000L
    const val DAILY_CAP_TOTAL = 10
    const val DAILY_CAP_PER_ITEM = 3
    const val QUIET_START_MINUTE = 22 * 60 // 22:00
    const val QUIET_END_MINUTE = 8 * 60 // 08:00
    /** "오늘 그만" 해제 시각 (다음날 05:00) */
    const val MUTE_TODAY_RELEASE_HOUR = 5
}
