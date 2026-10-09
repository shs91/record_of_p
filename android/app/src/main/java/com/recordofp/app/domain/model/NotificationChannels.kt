package com.recordofp.app.domain.model

/**
 * 알림 채널 id (스펙 §6.5). platform(채널 생성·발행)과 ui·data(보호 상태 판단)가 함께 쓴다 —
 * ui가 platform을 import하지 않도록 순수 Kotlin 쪽에 둔다 (최종 리뷰 I4)
 */
object NotificationChannels {
    /** 근처 알림 (중요도 높음) */
    const val NEARBY = "nearby"

    /** 서비스 상태 (중요도 낮음) */
    const val STATUS = "status"
}
