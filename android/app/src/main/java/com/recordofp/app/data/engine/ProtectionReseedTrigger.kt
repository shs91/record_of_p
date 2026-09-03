package com.recordofp.app.data.engine

import com.recordofp.app.data.repo.ReseedRequester
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 보호 상태(권한 스냅샷) 전이 감시 (F1, 실기기 1차 검증).
 * 사용자가 시스템 설정에서 '항상 허용'을 켜고 앱으로 돌아오면 onCreate의 APP_OPEN은
 * 이미 지나간 뒤다 — 홈/설정 화면이 재개 시점 스냅샷을 보고하면, 미보호→완전 보호
 * 전이에서만 기회적 재배치를 요청한다.
 */
@Singleton
class ProtectionReseedTrigger @Inject constructor(
    private val requester: ReseedRequester,
) {
    /** 마지막으로 보고된 상태. null = 아직 보고 없음(앱 시작 직후 — APP_OPEN이 담당하는 구간) */
    private var last: Boolean? = null

    /** 메인 스레드(Compose LifecycleResumeEffect)에서만 호출된다 */
    fun onSnapshot(fullyProtected: Boolean) {
        val previous = last
        last = fullyProtected
        if (previous == false && fullyProtected) requester.requestOpportunistic()
    }
}
