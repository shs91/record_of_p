package com.recordofp.app.platform.geofence

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.recordofp.app.data.engine.ReseedService
import com.recordofp.app.domain.engine.ReseedCause
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/** 재부팅·앱 업데이트 시 지오펜스가 소멸하므로 펜스 소실을 표시하고 재배치를 예약한다 (설계 §6.1, §6.2) */
@AndroidEntryPoint
class BootReceiver : BroadcastReceiver() {

    @Inject lateinit var reseedService: ReseedService

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            -> markFencesLostThenReseed(context, reseedService, ReseedCause.BOOT)
        }
    }
}
