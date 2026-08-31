package com.recordofp.app.domain.model

enum class ReminderStatus { ACTIVE, DONE, ARCHIVED }

data class Reminder(
    val id: Long = 0L,
    val title: String,
    val memo: String? = null,
    val status: ReminderStatus = ReminderStatus.ACTIVE,
    /** "오늘 그만"·스누즈: 이 시각(epoch ms)까지 알림 억제 */
    val snoozeUntil: Long? = null,
    val createdAt: Long,
    val updatedAt: Long,
    val completedAt: Long? = null,
    val triggers: List<TriggerSpec> = emptyList(),
)
