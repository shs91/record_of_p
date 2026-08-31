package com.recordofp.app.platform.notify

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.recordofp.app.MainActivity
import com.recordofp.app.R
import com.recordofp.app.data.engine.AlertGroup
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NearbyNotifier @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    /** @return 알림이 실제로 발행됐으면 true (알림 권한 꺼짐이면 false — 호출부가 표시 기록 여부를 가른다, M1) */
    fun show(group: AlertGroup): Boolean {
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return false
        val notificationId = (group.poiId ?: group.poiName ?: "poi").hashCode()
        val first = group.reminders.first()
        val text = if (group.reminders.size == 1) first.title
        else context.getString(R.string.notif_more_items, first.title, group.reminders.size - 1)
        val title = group.distanceM?.let {
            context.getString(R.string.notif_nearby_title_dist, group.poiName ?: "", it)
        } ?: context.getString(R.string.notif_nearby_title, group.poiName ?: "")

        val contentIntent = Intent(context, MainActivity::class.java).apply {
            // 딥링크는 그룹이 항목 1건일 때만 — 다건이면 어디로 갈지 모호하니 홈으로 (§4.1.2)
            if (group.reminders.size == 1) putExtra("reminder_id", first.id)
        }
        val builder = NotificationCompat.Builder(context, Notifier.CHANNEL_NEARBY)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(text)
            .setAutoCancel(true)
            .setContentIntent(
                PendingIntent.getActivity(
                    context, notificationId, contentIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                ),
            )
        if (group.reminders.size == 1) {
            builder
                .addAction(0, context.getString(R.string.action_complete),
                    NotificationActionReceiver.pendingIntent(context, NotificationActionReceiver.ACTION_COMPLETE, first.id, notificationId))
                .addAction(0, context.getString(R.string.action_mute_today),
                    NotificationActionReceiver.pendingIntent(context, NotificationActionReceiver.ACTION_MUTE_TODAY, first.id, notificationId))
        }
        NotificationManagerCompat.from(context).notify(notificationId, builder.build())
        return true
    }
}
