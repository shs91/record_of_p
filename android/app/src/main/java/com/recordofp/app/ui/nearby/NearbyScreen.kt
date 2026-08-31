package com.recordofp.app.ui.nearby

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
 * TODO(v1): 현재 위치 기준 활성 트리거 매칭 POI 거리순 리스트 +
 *  카카오맵 앱 딥링크(kakaomap://look?p=lat,lng). 백그라운드 권한 없이도 동작하는
 *  열화 모드의 핵심 화면 (설계 §3.1, §4.2)
 */
@Composable
fun NearbyScreen() {
    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text(text = stringResource(R.string.title_nearby))
        Text(text = stringResource(R.string.stub_screen_todo))
    }
}
