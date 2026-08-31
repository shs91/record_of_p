package com.recordofp.app.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.recordofp.app.R

/**
 * TODO(v1): 활성 항목 리스트(카테고리 칩 그룹핑), 완료 스와이프,
 *  권한 미완 시 보호 상태 배너 (설계 §4.1, §4.3)
 */
@Composable
fun HomeScreen(
    onAddClick: () -> Unit,
    onNearbyClick: () -> Unit,
    onSettingsClick: () -> Unit,
) {
    Scaffold(
        floatingActionButton = {
            FloatingActionButton(onClick = onAddClick) {
                Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.action_add))
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(text = stringResource(R.string.title_home))
            Text(text = stringResource(R.string.stub_screen_todo))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onNearbyClick) { Text(stringResource(R.string.title_nearby)) }
                TextButton(onClick = onSettingsClick) { Text(stringResource(R.string.title_settings)) }
            }
        }
    }
}
