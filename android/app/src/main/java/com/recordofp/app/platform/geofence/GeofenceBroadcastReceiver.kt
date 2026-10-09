package com.recordofp.app.platform.geofence

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.google.android.gms.location.GeofenceStatusCodes
import com.google.android.gms.location.GeofencingEvent
import com.recordofp.app.data.engine.GeofenceEventHandler
import com.recordofp.app.data.engine.ReseedService
import com.recordofp.app.domain.engine.ReseedCause
import com.recordofp.app.domain.model.GeoPoint
import com.recordofp.app.platform.notify.NearbyNotifier
import com.recordofp.app.platform.work.ReseedWorker
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** 지오펜스 전이 이벤트 진입점 (스펙 §6.5) */
@AndroidEntryPoint
class GeofenceBroadcastReceiver : BroadcastReceiver() {

    @Inject lateinit var handler: GeofenceEventHandler
    @Inject lateinit var notifier: NearbyNotifier
    @Inject lateinit var reseedService: ReseedService

    override fun onReceive(context: Context, intent: Intent) {
        val event = GeofencingEvent.fromIntent(intent) ?: return
        if (event.hasError()) {
            // 위치가 꺼지면 OS가 이 앱의 펜스를 전부 지우고 이 오류를 보낸다. 전체 재등록을 예약한다 —
            // 펜스 소실 표시를 먼저 남긴다. 위치가 다시 켜질 때까지 워커는 NO_LOCATION/FAILED로 백오프 재시도한다 (검토 B1)
            if (event.errorCode == GeofenceStatusCodes.GEOFENCE_NOT_AVAILABLE) {
                markFencesLostThenReseed(context, reseedService, ReseedCause.FENCE_LOST)
            }
            return
        }
        val ids = event.triggeringGeofences?.map { it.requestId } ?: return
        val triggeringPoint = event.triggeringLocation?.let { GeoPoint(it.latitude, it.longitude) }

        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val outcome = handler.onFenceEvent(ids, triggeringPoint)
                outcome.groups.forEach { group ->
                    // 표시가 실제로 성공했을 때만 쿨다운을 소모한다 (M1, 검토 C2)
                    if (notifier.show(group)) handler.recordShown(group) else handler.recordNotShown(group)
                }
                if (outcome.sentinelExited) ReseedWorker.runNow(context, ReseedCause.SENTINEL_EXIT)
            } catch (c: CancellationException) {
                throw c
            } catch (e: Exception) {
                // EngineRunLog에 남길 방법이 없는 지점(리시버 자체 예외) — 시스템 로그로만 남긴다 (M2)
                Log.w("RecordOfP", "지오펜스 이벤트 처리 실패", e)
            } finally {
                pending.finish()
            }
        }
    }
}
