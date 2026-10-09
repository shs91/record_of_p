package com.recordofp.app.data.notify

import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationManagerCompat
import com.recordofp.app.domain.model.NotificationChannels

/**
 * 근처 알림이 실제로 보일 수 있는가 — 앱 알림(A13+는 POST_NOTIFICATIONS 포함)과 "근처 알림" 채널이 모두 켜져 있어야 한다
 * (검토 C2, 최종 리뷰 I4). 발행(platform NearbyNotifier)과 보호 상태 판정(ui PermissionSnapshot)이 같은 판단을 쓰도록 data에 둔다.
 */
fun nearbyAlertsEnabled(context: Context): Boolean {
    val manager = NotificationManagerCompat.from(context)
    if (!manager.areNotificationsEnabled()) return false
    // 채널이 아직 없으면(첫 실행 직후) 꺼진 것이 아니다
    val channel = manager.getNotificationChannel(NotificationChannels.NEARBY) ?: return true
    return channel.importance != NotificationManager.IMPORTANCE_NONE
}
