package com.recordofp.app.platform.notify

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.core.content.getSystemService
import com.recordofp.app.R
import com.recordofp.app.domain.model.NotificationChannels

/** 알림 채널 2개를 만든다 (스펙 §6.5). 채널 id는 ui와 공유하는 NotificationChannels (최종 리뷰 I4) */
object Notifier {
    fun ensureChannels(context: Context) {
        val nm = context.getSystemService<NotificationManager>() ?: return
        nm.createNotificationChannel(
            NotificationChannel(
                NotificationChannels.NEARBY,
                context.getString(R.string.channel_nearby_name),
                NotificationManager.IMPORTANCE_HIGH,
            ).apply { description = context.getString(R.string.channel_nearby_desc) },
        )
        nm.createNotificationChannel(
            NotificationChannel(
                NotificationChannels.STATUS,
                context.getString(R.string.channel_status_name),
                NotificationManager.IMPORTANCE_LOW,
            ).apply { description = context.getString(R.string.channel_status_desc) },
        )
    }
}
