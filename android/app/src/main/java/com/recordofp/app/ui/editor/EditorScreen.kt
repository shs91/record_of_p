package com.recordofp.app.ui.editor

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.recordofp.app.R

/**
 * TODO(v1): 제목 입력 → 트리거 선택(카테고리 칩 다중 선택 / 브랜드 입력 / 장소 검색) → 저장.
 *  "3탭 + 타이핑 이내" 목표 (설계 §4.1)
 */
@Composable
fun EditorScreen(onDone: () -> Unit) {
    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text(text = stringResource(R.string.title_editor))
        Text(text = stringResource(R.string.stub_screen_todo))
        TextButton(onClick = onDone) { Text(stringResource(R.string.title_home)) }
    }
}
