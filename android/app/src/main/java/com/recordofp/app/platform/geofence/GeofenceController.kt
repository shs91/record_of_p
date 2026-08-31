package com.recordofp.app.platform.geofence

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.google.android.gms.location.GeofencingClient
import com.google.android.gms.location.LocationServices
import com.recordofp.app.domain.engine.PlannedFence
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * ReseedPlanner의 출력(PlannedFence)을 OS 지오펜스로 반영하는 접착 계층.
 * 전체 교체가 아니라 차분 적용으로 이벤트 유실 창을 줄인다 (설계 §6.3.6).
 */
@Singleton
class GeofenceController @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val client: GeofencingClient = LocationServices.getGeofencingClient(context)

    /**
     * TODO(v1): §6.3.6 — 현재 등록(GeofenceRegDao)과 plan의 차분 계산 →
     *  removeGeofences(제거분) / addGeofences(추가분) → DB 미러 갱신(applyDiff).
     *  PlannedFence.transition 매핑: ENTER→GEOFENCE_TRANSITION_ENTER,
     *  DWELL→GEOFENCE_TRANSITION_DWELL(+setLoiteringDelay), EXIT→GEOFENCE_TRANSITION_EXIT.
     */
    @SuppressLint("MissingPermission") // 호출부가 권한 확인 후 진입 (보호 상태 대시보드 §4.3)
    suspend fun applyPlan(plan: List<PlannedFence>) {
        // 스켈레톤 단계 — 구현 예정
    }

    private fun geofencePendingIntent(): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            0,
            Intent(context, GeofenceBroadcastReceiver::class.java),
            // 시스템이 지오펜스 이벤트 extra를 채워야 하므로 MUTABLE 필수
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
        )
}
