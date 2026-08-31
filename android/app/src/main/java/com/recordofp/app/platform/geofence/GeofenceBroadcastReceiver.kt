package com.recordofp.app.platform.geofence

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.google.android.gms.location.GeofencingEvent
import dagger.hilt.android.AndroidEntryPoint

/** 지오펜스 전이 이벤트 진입점 (설계 §6.5 파이프라인의 시작) */
@AndroidEntryPoint
class GeofenceBroadcastReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val event = GeofencingEvent.fromIntent(intent) ?: return
        if (event.hasError()) return

        // TODO(v1): §6.5 — goAsync() 후:
        //  1. geofenceId가 GeofenceRegDao에 유효한지 검증 (stale 이벤트 폐기)
        //  2. RegTrigger → TriggerSpec → 활성 Reminder 로드
        //  3. NotificationGate 필터 체인 평가 (차단 사유는 EngineRunLog 기록)
        //  4. 통과 항목을 POI 단위로 그룹핑해 Notifier로 알림 1건 발행
        //  5. 센티널 EXIT면 ReseedWorker.runNow()
    }
}
