package com.recordofp.app.platform.notify

import com.recordofp.app.data.engine.AlertGroup

/** 근처 알림 액션 (스펙 §4.1 [완료] [오늘 그만]) */
internal enum class AlertAction { COMPLETE, MUTE_TODAY }

/** 여러 항목 묶음에는 [완료]를 두지 않는다 — 어느 항목인지 모호하다. [오늘 그만]은 묶음 전체를 억제한다 */
internal fun alertActionsFor(group: AlertGroup): List<AlertAction> =
    if (group.reminders.size == 1) listOf(AlertAction.COMPLETE, AlertAction.MUTE_TODAY) else listOf(AlertAction.MUTE_TODAY)
