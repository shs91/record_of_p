package com.recordofp.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.recordofp.app.domain.engine.ReseedCause
import com.recordofp.app.platform.work.ReseedWorker
import com.recordofp.app.ui.AppNavHost
import com.recordofp.app.ui.theme.RecordOfPTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // 근처 알림 탭으로 열렸으면 해당 기록으로 딥링크 (§4.1.2)
        val deepLinkReminderId = intent.getLongExtra("reminder_id", -1L).takeIf { it >= 0 }
        setContent {
            RecordOfPTheme {
                AppNavHost(deepLinkReminderId = deepLinkReminderId)
            }
        }

        // 앱 진입 시 기회적 재배치 (§6.2 APP_OPEN) — 판정은 거버너가 한다
        ReseedWorker.runNow(this, ReseedCause.APP_OPEN)
    }
}
