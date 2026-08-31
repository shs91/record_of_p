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
        setContent {
            RecordOfPTheme {
                AppNavHost()
            }
        }

        // 앱 진입 시 기회적 재배치 (§6.2 APP_OPEN) — 판정은 거버너가 한다
        ReseedWorker.runNow(this, ReseedCause.APP_OPEN)
    }
}
