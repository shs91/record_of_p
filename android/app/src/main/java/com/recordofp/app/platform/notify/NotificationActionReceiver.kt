package com.recordofp.app.platform.notify

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.app.NotificationManagerCompat
import com.recordofp.app.data.repo.ReminderRepository
import com.recordofp.app.domain.engine.MuteToday
import dagger.hilt.android.AndroidEntryPoint
import java.time.Clock
import java.time.ZoneId
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** 알림 액션 [완료][오늘 그만] (스펙 §4.1.2) */
@AndroidEntryPoint
class NotificationActionReceiver : BroadcastReceiver() {

    @Inject lateinit var repository: ReminderRepository
    @Inject lateinit var clock: Clock
    @Inject lateinit var zone: ZoneId

    override fun onReceive(context: Context, intent: Intent) {
        val reminderId = intent.getLongExtra(EXTRA_REMINDER_ID, -1L)
        if (reminderId < 0) return
        val notificationId = intent.getIntExtra(EXTRA_NOTIFICATION_ID, 0)
        val action = intent.action
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                when (action) {
                    ACTION_COMPLETE -> repository.complete(reminderId)
                    ACTION_MUTE_TODAY -> repository.muteUntil(
                        reminderId, MuteToday.releaseInstant(clock.instant(), zone).toEpochMilli(),
                    )
                }
                NotificationManagerCompat.from(context).cancel(notificationId)
            } catch (c: CancellationException) {
                throw c
            } catch (e: Exception) {
                // EngineRunLog에 남길 방법이 없는 지점(리시버 자체 예외) — 시스템 로그로만 남긴다 (M2)
                Log.w("RecordOfP", "알림 액션 처리 실패", e)
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val ACTION_COMPLETE = "com.recordofp.app.action.COMPLETE"
        const val ACTION_MUTE_TODAY = "com.recordofp.app.action.MUTE_TODAY"
        const val EXTRA_REMINDER_ID = "reminder_id"
        const val EXTRA_NOTIFICATION_ID = "notification_id"

        fun pendingIntent(context: Context, action: String, reminderId: Long, notificationId: Int): PendingIntent =
            PendingIntent.getBroadcast(
                context,
                (action + reminderId + notificationId).hashCode(),
                Intent(context, NotificationActionReceiver::class.java)
                    .setAction(action)
                    .putExtra(EXTRA_REMINDER_ID, reminderId)
                    .putExtra(EXTRA_NOTIFICATION_ID, notificationId),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
    }
}
