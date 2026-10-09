package com.recordofp.app.platform.geofence

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingClient
import com.google.android.gms.location.GeofencingRequest
import com.google.android.gms.location.LocationServices
import com.recordofp.app.data.engine.FenceApplier
import com.recordofp.app.domain.engine.*
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.tasks.await

/**
 * ReseedPlanner의 출력(PlannedFence)을 OS 지오펜스로 반영하는 접착 계층.
 * 기본은 차분 적용(apply)으로 이벤트 유실 창을 줄인다. OS를 믿을 수 없을 때와 PERIODIC은 replaceAll로
 * 이 앱 펜스 전체를 교체하고, 센티널은 INITIAL_TRIGGER_EXIT로 따로 등록한다 (설계 §6.3).
 */
@Singleton
class GeofenceController @Inject constructor(
    @ApplicationContext private val context: Context,
) : FenceApplier {
    private val client: GeofencingClient = LocationServices.getGeofencingClient(context)

    override suspend fun apply(diff: FenceDiff) {
        if (diff.removeIds.isNotEmpty()) client.removeGeofences(diff.removeIds).await()
        add(diff.add)
    }

    override suspend fun replaceAll(fences: List<PlannedFence>) {
        // 이 PendingIntent로 등록된 펜스 전부 — 미러에 없는 고아 펜스까지 지운다 (검토 B1)
        client.removeGeofences(geofencePendingIntent()).await()
        add(fences)
    }

    /**
     * 초기 트리거가 달라 센티널은 요청을 따로 만든다 (최종 리뷰 C1).
     * - POI·PLACE: 재배치 순간 이미 영역 안이어도 즉발 금지 — 자연 전이만 (§6.5 스팸 방지)
     * - SENTINEL: 등록 순간 이미 밖이면(빠른 차량, 오래된 lastLocation) 곧바로 EXIT를 낸다 — 센티널 자가 복구.
     *   그 이탈은 방금 재배치했으므로 거버너가 디바운스하고, 워커가 10분 뒤로 다시 예약한다 (루프 없음)
     */
    @SuppressLint("MissingPermission") // 호출부(Worker)가 권한 확인 후 진입 (§4.3)
    private suspend fun add(fences: List<PlannedFence>) {
        val (sentinels, others) = fences.partition { it.kind == FenceKind.SENTINEL }
        if (others.isNotEmpty()) {
            client.addGeofences(request(others, initialTrigger = 0), geofencePendingIntent()).await()
        }
        if (sentinels.isNotEmpty()) {
            client.addGeofences(
                request(sentinels, initialTrigger = GeofencingRequest.INITIAL_TRIGGER_EXIT), geofencePendingIntent(),
            ).await()
        }
    }

    private fun request(fences: List<PlannedFence>, initialTrigger: Int): GeofencingRequest =
        GeofencingRequest.Builder()
            .setInitialTrigger(initialTrigger)
            .addGeofences(fences.map { it.toGeofence() })
            .build()

    private fun PlannedFence.toGeofence(): Geofence = Geofence.Builder()
        .setRequestId(key)
        .setCircularRegion(center.lat, center.lng, radiusM)
        .setExpirationDuration(Geofence.NEVER_EXPIRE)
        .apply {
            when (transition) {
                FenceTransition.ENTER -> setTransitionTypes(Geofence.GEOFENCE_TRANSITION_ENTER)
                FenceTransition.EXIT -> setTransitionTypes(Geofence.GEOFENCE_TRANSITION_EXIT)
                FenceTransition.DWELL -> {
                    setTransitionTypes(Geofence.GEOFENCE_TRANSITION_DWELL)
                    setLoiteringDelay(loiteringDelayMs ?: EngineParams.LOITERING_DELAY_MS)
                }
            }
        }
        .build()

    private fun geofencePendingIntent(): PendingIntent = PendingIntent.getBroadcast(
        context, 0, Intent(context, GeofenceBroadcastReceiver::class.java),
        // 시스템이 지오펜스 이벤트 extra를 채워야 하므로 MUTABLE 필수
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
    )
}
