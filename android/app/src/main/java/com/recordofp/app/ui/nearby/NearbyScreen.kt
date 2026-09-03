package com.recordofp.app.ui.nearby

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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
import com.recordofp.app.ui.common.DistanceBadge
import com.recordofp.app.ui.common.catalogLabelRes
import kotlin.math.roundToInt

/**
 * 백그라운드 권한 없이도(위치 '사용 중'만으로) 앱을 열면 지금 주변에서 처리할 수 있는 일이
 * 보이는 화면 — 열화 모드의 핵심 (설계 §3.1, §4.2). 지도 SDK는 v1.1 — 카카오맵 앱 딥링크로
 * 대체한다(§3.2).
 * 클린 미니멀 개편 (개편안 §2 주변 보기): 그룹 헤더 이모지+SemiBold, POI 행 카드화,
 * 거리는 오른쪽 연블루 배지.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NearbyScreen(
    onBack: () -> Unit = {},
    viewModel: NearbyViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    LaunchedEffect(Unit) { viewModel.load() }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
                title = { Text(stringResource(R.string.title_nearby)) },
                actions = {
                    IconButton(onClick = viewModel::load) {
                        Icon(Icons.Filled.Refresh, contentDescription = stringResource(R.string.action_refresh))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when {
                state.loading -> CircularProgressIndicator(
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.align(Alignment.Center),
                )
                state.locationUnavailable -> CenteredNote(stringResource(R.string.nearby_no_location))
                state.groups.isEmpty() -> CenteredNote(stringResource(R.string.nearby_empty))
                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    state.groups.forEach { group ->
                        item(key = "header:${group.matchKey}") {
                            Text(
                                group.headerText(),
                                style = MaterialTheme.typography.titleSmall,
                                modifier = Modifier.padding(top = 12.dp, bottom = 2.dp),
                            )
                        }
                        items(group.pois, key = { "${group.matchKey}:${it.id}" }) { poi ->
                            NearbyPoiCard(poi = poi, onClick = { openInKakaoMap(context, poi) })
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BoxScope.CenteredNote(text: String) {
    Text(
        text,
        textAlign = TextAlign.Center,
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.align(Alignment.Center).padding(24.dp),
    )
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

/** POI 행 카드 — 이름 + 거리 배지. 탭하면 카카오맵 (개편안 §2) */
@Composable
private fun NearbyPoiCard(poi: PoiCandidate, onClick: () -> Unit) {
    val openMapLabel = stringResource(R.string.nearby_open_map)
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClickLabel = openMapLabel, onClick = onClick),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                poi.name,
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.weight(1f, fill = false).padding(end = 12.dp),
            )
            DistanceBadge(poi.distanceM.roundToInt())
        }
    }
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
