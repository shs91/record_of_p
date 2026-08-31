package com.recordofp.app.platform.geofence

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.recordofp.app.platform.work.ReseedWorker

/** 재부팅·앱 업데이트 시 지오펜스가 소멸하므로 재배치를 예약한다 (설계 §6.2) */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            -> ReseedWorker.runNow(context, cause = "BOOT")
        }
    }
}
