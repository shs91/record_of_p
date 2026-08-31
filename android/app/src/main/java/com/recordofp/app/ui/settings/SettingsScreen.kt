package com.recordofp.app.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.recordofp.app.R

/**
 * TODO(v1): 알림 정책(쿨다운·상한·방해금지), 보호 상태 대시보드(§4.3),
 *  진단/문제 해결 화면(§4.4), 제조사별 절전 안내
 */
@Composable
fun SettingsScreen() {
    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text(text = stringResource(R.string.title_settings))
        Text(text = stringResource(R.string.stub_screen_todo))
    }
}
