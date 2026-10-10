package com.recordofp.app.ui.nearby

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.recordofp.app.R
import com.recordofp.app.domain.engine.PoiCandidate
import com.recordofp.app.ui.common.BackButton
import com.recordofp.app.ui.common.TriggerTile
import com.recordofp.app.ui.common.distanceParts
import com.recordofp.app.ui.common.isFarDistance
import com.recordofp.app.ui.common.label
import com.recordofp.app.ui.theme.AppTextStyles
import com.recordofp.app.ui.theme.Spacing
import kotlin.math.roundToInt

/**
 * 백그라운드 권한 없이도(위치 '사용 중'만으로) 앱을 열면 지금 주변에서 처리할 수 있는 일이
 * 보이는 화면 — 열화 모드의 핵심 (설계 §3.1, §4.2). 지도 SDK는 v1.1 — 카카오맵 앱 딥링크로
 * 대체한다(§3.2). 모양은 개편안 2 §2 주변 보기 — 그룹 머리 타일, 그룹당 카드 하나, 큰 거리 숫자.
 */
@Composable
fun NearbyScreen(
    onBack: () -> Unit = {},
    viewModel: NearbyViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    LaunchedEffect(Unit) { viewModel.load() }
    NearbyContent(
        state = state,
        onBack = onBack,
        onRefresh = viewModel::load,
        onPoiClick = { openInKakaoMap(context, it) },
    )
}

/** 주변 보기 본체 — 상태와 동작만 받는다(미리보기는 NearbyPreviews.kt) */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NearbyContent(
    state: NearbyUiState,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onPoiClick: (PoiCandidate) -> Unit,
) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                navigationIcon = { BackButton(onClick = onBack) },
                title = { Text(stringResource(R.string.title_nearby)) },
                actions = {
                    IconButton(onClick = onRefresh) {
                        Icon(Icons.Filled.Refresh, contentDescription = stringResource(R.string.action_refresh))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
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
                    contentPadding = PaddingValues(
                        start = Spacing.screen, end = Spacing.screen, top = Spacing.xs, bottom = Spacing.xl,
                    ),
                    verticalArrangement = Arrangement.spacedBy(Spacing.l),
                ) {
                    items(state.groups, key = { it.matchKey }) { group ->
                        NearbyGroupSection(group = group, onPoiClick = onPoiClick)
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
        modifier = Modifier.align(Alignment.Center).padding(Spacing.xl),
    )
}

/** 그룹 — 머리(32dp 타일 + 이름 + "N곳") 아래 카드 하나에 지점 행을 헤어라인으로 나눈다 */
@Composable
private fun NearbyGroupSection(group: NearbyGroup, onPoiClick: (PoiCandidate) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        Row(
            modifier = Modifier.semantics(mergeDescendants = true) { heading() },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            TriggerTile(group.visual, size = 32.dp, cornerRadius = 10.dp, iconSize = 18.dp)
            Text(group.visual.label(), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            Text(
                pluralStringResource(R.plurals.nearby_place_count, group.pois.size, group.pois.size),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surface) {
            Column {
                group.pois.forEachIndexed { index, poi ->
                    if (index > 0) {
                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = 18.dp),
                            color = MaterialTheme.colorScheme.outlineVariant,
                        )
                    }
                    NearbyPoiRow(poi = poi, onClick = { onPoiClick(poi) })
                }
            }
        }
    }
}

/** 지점 행 64dp — 이름 + "카카오맵에서 보기", 오른쪽에 거리 숫자 22 Bold + 단위. 1km를 넘으면 숫자를 보조 글자색으로 */
@Composable
private fun NearbyPoiRow(poi: PoiCandidate, onClick: () -> Unit) {
    val distanceM = poi.distanceM.roundToInt()
    val (number, unit) = distanceParts(distanceM)
    val numberColor = if (isFarDistance(distanceM)) {
        MaterialTheme.colorScheme.onSurfaceVariant
    } else {
        MaterialTheme.colorScheme.onPrimaryContainer
    }
    val openMapLabel = stringResource(R.string.nearby_open_map)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .clickable(onClickLabel = openMapLabel, onClick = onClick)
            .padding(horizontal = 18.dp, vertical = Spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.s),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(poi.name, style = MaterialTheme.typography.titleSmall)
            Text(openMapLabel, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Row {
            Text(number, style = AppTextStyles.numberLarge, color = numberColor, modifier = Modifier.alignByBaseline())
            Text(
                unit,
                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                color = numberColor,
                modifier = Modifier.alignByBaseline().padding(start = 1.dp),
            )
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
