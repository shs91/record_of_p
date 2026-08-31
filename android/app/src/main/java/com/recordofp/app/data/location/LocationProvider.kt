package com.recordofp.app.data.location

import com.recordofp.app.domain.model.GeoPoint

interface LocationProvider {
    /** 현재 위치(BALANCED) → 실패 시 마지막 위치 → 둘 다 없으면 null (§6.4) */
    suspend fun currentOrLast(): GeoPoint?
}
