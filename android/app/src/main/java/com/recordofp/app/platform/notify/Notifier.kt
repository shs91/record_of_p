package com.recordofp.app.platform.notify

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.core.content.getSystemService
import com.recordofp.app.R

object Notifier {
    const val CHANNEL_NEARBY = "nearby"
    const val CHANNEL_STATUS = "status"

    fun ensureChannels(context: Context) {
        val nm = context.getSystemService<NotificationManager>() ?: return
        nm.createNotificationChannel(
            NotificationChannel(
                CHANNEL_NEARBY,
                context.getString(R.string.channel_nearby_name),
                NotificationManager.IMPORTANCE_HIGH,
            ).apply { description = context.getString(R.string.channel_nearby_desc) },
        )
        nm.createNotificationChannel(
            NotificationChannel(
                CHANNEL_STATUS,
                context.getString(R.string.channel_status_name),
                NotificationManager.IMPORTANCE_LOW,
            ).apply { description = context.getString(R.string.channel_status_desc) },
        )
    }
}
