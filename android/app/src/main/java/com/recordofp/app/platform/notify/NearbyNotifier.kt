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
    fun show(group: AlertGroup) {
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return
        val notificationId = (group.poiId ?: group.poiName ?: "poi").hashCode()
        val first = group.reminders.first()
        val text = if (group.reminders.size == 1) first.title
        else context.getString(R.string.notif_more_items, first.title, group.reminders.size - 1)

        val builder = NotificationCompat.Builder(context, Notifier.CHANNEL_NEARBY)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(context.getString(R.string.notif_nearby_title, group.poiName ?: ""))
            .setContentText(text)
            .setAutoCancel(true)
            .setContentIntent(
                PendingIntent.getActivity(
                    context, notificationId, Intent(context, MainActivity::class.java),
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
    }
}
