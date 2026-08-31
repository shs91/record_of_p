package com.recordofp.app.ui.nearby

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.recordofp.app.R
import com.recordofp.app.domain.engine.PoiCandidate
import com.recordofp.app.domain.model.TriggerCatalog
import com.recordofp.app.ui.common.catalogLabelRes
import kotlin.math.roundToInt

/**
 * 백그라운드 권한 없이도(위치 '사용 중'만으로) 앱을 열면 지금 주변에서 처리할 수 있는 일이
 * 보이는 화면 — 열화 모드의 핵심 (설계 §3.1, §4.2). 지도 SDK는 v1.1 — 카카오맵 앱 딥링크로
 * 대체한다(§3.2).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NearbyScreen(viewModel: NearbyViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    LaunchedEffect(Unit) { viewModel.load() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.title_nearby)) },
                actions = {
                    IconButton(onClick = viewModel::load) {
                        Icon(Icons.Filled.Refresh, contentDescription = stringResource(R.string.action_refresh))
                    }
                },
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when {
                state.loading -> CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                state.locationUnavailable -> Text(
                    stringResource(R.string.nearby_no_location),
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.align(Alignment.Center).padding(24.dp),
                )
                state.groups.isEmpty() -> Text(
                    stringResource(R.string.nearby_empty),
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.align(Alignment.Center).padding(24.dp),
                )
                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    state.groups.forEach { group ->
                        item(key = "header:${group.matchKey}") {
                            Text(
                                group.headerText(),
                                style = MaterialTheme.typography.titleSmall,
                                modifier = Modifier.padding(top = 12.dp, bottom = 4.dp),
                            )
                        }
                        items(group.pois, key = { "${group.matchKey}:${it.id}" }) { poi ->
                            NearbyPoiRow(poi = poi, onClick = { openInKakaoMap(context, poi) })
                        }
                    }
                }
            }
        }
    }
}

/** 그룹 헤더 표시: matchKey 접두로 트리거 종류를 분기한다 (cat:/brand:/place:, 스펙 §5.3) */
@Composable
private fun NearbyGroup.headerText(): String = when {
    matchKey.startsWith("cat:") -> {
        val entry = TriggerCatalog.byId(matchKey.removePrefix("cat:"))
        val label = entry?.let { stringResource(catalogLabelRes(it.id)) } ?: matchKey
        "${entry?.emoji ?: ""} $label".trim()
    }
    matchKey.startsWith("brand:") -> "🔎 ${matchKey.removePrefix("brand:")}"
    matchKey.startsWith("place:") -> "📌 ${pois.firstOrNull()?.name.orEmpty()}"
    else -> matchKey
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NearbyPoiRow(poi: PoiCandidate, onClick: () -> Unit) {
    val openMapLabel = stringResource(R.string.nearby_open_map)
    ListItem(
        headlineContent = { Text(poi.name) },
        supportingContent = { Text(stringResource(R.string.nearby_distance_fmt, poi.distanceM.roundToInt())) },
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClickLabel = openMapLabel, onClick = onClick),
    )
}

/** 카카오맵 앱으로 지점 위치를 연다. 미설치 등으로 실패하면 웹 지도로 대체한다 (설계 §3.2) */
private fun openInKakaoMap(context: Context, poi: PoiCandidate) {
    val uri = "kakaomap://look?p=${poi.point.lat},${poi.point.lng}"
    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(uri))) }
        .onFailure { // 카카오맵 미설치 → 웹 지도
            context.startActivity(
                Intent(
                    Intent.ACTION_VIEW,
                    Uri.parse("https://map.kakao.com/link/map/${Uri.encode(poi.name)},${poi.point.lat},${poi.point.lng}"),
                ),
            )
        }
}
