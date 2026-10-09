package com.recordofp.app.platform.notify

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationManagerCompat
import com.recordofp.app.data.db.EngineRunLogDao
import com.recordofp.app.data.repo.ReminderRepository
import com.recordofp.app.domain.engine.MuteToday
import com.recordofp.app.platform.logReceiverError
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
    @Inject lateinit var runLogDao: EngineRunLogDao

    override fun onReceive(context: Context, intent: Intent) {
        val reminderIds = intent.getLongArrayExtra(EXTRA_REMINDER_IDS)
        if (reminderIds == null || reminderIds.isEmpty()) return
        val notificationId = intent.getIntExtra(EXTRA_NOTIFICATION_ID, 0)
        val action = intent.action
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                when (action) {
                    ACTION_COMPLETE -> reminderIds.forEach { repository.complete(it) }
                    ACTION_MUTE_TODAY -> {
                        val release = MuteToday.releaseInstant(clock.instant(), zone).toEpochMilli()
                        reminderIds.forEach { repository.muteUntil(it, release) }
                    }
                }
                NotificationManagerCompat.from(context).cancel(notificationId)
            } catch (c: CancellationException) {
                throw c
            } catch (e: Exception) {
                // 코루틴 밖으로 나간 예외는 프로세스를 죽인다. 알림은 남겨 두어 다시 누를 수 있게 한다 (최종 리뷰 I1)
                runLogDao.logReceiverError(clock, LOG_CAUSE, e)
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        private const val LOG_CAUSE = "NOTIFICATION_ACTION"
        const val ACTION_COMPLETE = "com.recordofp.app.action.COMPLETE"
        const val ACTION_MUTE_TODAY = "com.recordofp.app.action.MUTE_TODAY"
        const val EXTRA_REMINDER_IDS = "reminder_ids"
        const val EXTRA_NOTIFICATION_ID = "notification_id"

        fun pendingIntent(context: Context, action: String, reminderIds: List<Long>, notificationId: Int): PendingIntent =
            PendingIntent.getBroadcast(
                context,
                // 알림·액션마다 다른 requestCode — 다른 알림의 액션 extra를 덮어쓰지 않는다
                (action + notificationId).hashCode(),
                Intent(context, NotificationActionReceiver::class.java)
                    .setAction(action)
                    .putExtra(EXTRA_REMINDER_IDS, reminderIds.toLongArray())
                    .putExtra(EXTRA_NOTIFICATION_ID, notificationId),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
    }
}
