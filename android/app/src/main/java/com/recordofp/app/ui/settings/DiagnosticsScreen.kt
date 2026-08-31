package com.recordofp.app.ui.settings

import android.content.Intent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.recordofp.app.R
import com.recordofp.app.data.db.EngineRunLogEntity
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * "왜 알림이 안 왔는지"를 앱이 스스로 답하는 화면 (설계 §4.4).
 * 엔진 실행 이력을 최신순으로 보여주고, 텍스트로 내보내 공유할 수 있게 한다 —
 * 기기 밖 자동 전송은 없다. 사용자가 공유 버튼을 눌러야만 나간다.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiagnosticsScreen(viewModel: DiagnosticsViewModel = hiltViewModel()) {
    val entries by viewModel.entries.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val exportLabel = stringResource(R.string.diag_export)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.diag_title)) },
                actions = {
                    IconButton(onClick = {
                        val send = Intent(Intent.ACTION_SEND)
                            .setType("text/plain")
                            .putExtra(Intent.EXTRA_TEXT, viewModel.buildExport())
                        context.startActivity(Intent.createChooser(send, null))
                    }) {
                        Icon(Icons.Filled.Share, contentDescription = exportLabel)
                    }
                },
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            if (entries.isEmpty()) {
                Text(
                    stringResource(R.string.diag_empty),
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.align(Alignment.Center).padding(24.dp),
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(vertical = 8.dp),
                ) {
                    items(entries, key = { it.id }) { entry ->
                        DiagnosticsRow(entry)
                    }
                }
            }
        }
    }
}

@Composable
private fun DiagnosticsRow(entry: EngineRunLogEntity) {
    val fmt = remember { DateTimeFormatter.ofPattern("MM-dd HH:mm") }
    val time = fmt.format(Instant.ofEpochMilli(entry.at).atZone(ZoneId.systemDefault()))
    ListItem(
        headlineContent = { Text("${entry.cause} → ${entry.result}") },
        supportingContent = { Text(entry.note?.let { "$time · $it" } ?: time) },
        trailingContent = { Text(entry.registeredCount.toString()) },
    )
}
