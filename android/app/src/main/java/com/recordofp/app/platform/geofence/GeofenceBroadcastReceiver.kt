package com.recordofp.app.platform.geofence

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.google.android.gms.location.GeofencingEvent
import com.recordofp.app.data.engine.GeofenceEventHandler
import com.recordofp.app.domain.engine.ReseedCause
import com.recordofp.app.platform.notify.NearbyNotifier
import com.recordofp.app.platform.work.ReseedWorker
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** 지오펜스 전이 이벤트 진입점 (스펙 §6.5) */
@AndroidEntryPoint
class GeofenceBroadcastReceiver : BroadcastReceiver() {

    @Inject lateinit var handler: GeofenceEventHandler
    @Inject lateinit var notifier: NearbyNotifier

    override fun onReceive(context: Context, intent: Intent) {
        val event = GeofencingEvent.fromIntent(intent) ?: return
        if (event.hasError()) return
        val ids = event.triggeringGeofences?.map { it.requestId } ?: return

        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val outcome = handler.onFenceEvent(ids)
                outcome.groups.forEach { notifier.show(it) }
                if (outcome.sentinelExited) ReseedWorker.runNow(context, ReseedCause.SENTINEL_EXIT)
            } finally {
                pending.finish()
            }
        }
    }
}
