package com.recordofp.app.ui.nearby

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.recordofp.app.domain.engine.PoiCandidate
import com.recordofp.app.domain.model.GeoPoint
import com.recordofp.app.ui.common.TriggerVisual
import com.recordofp.app.ui.theme.LightDarkPreviews
import com.recordofp.app.ui.theme.RecordOfPTheme

// 주변 보기 미리보기 — 라이트·다크 두 벌과 큰 글꼴 (디자인 시스템 §5, 개편안 2 목업 "주변 보기")

private fun poi(name: String, distanceM: Double) = PoiCandidate(name, name, GeoPoint(37.5, 127.03), distanceM)

private val groups = listOf(
    NearbyGroup(
        "cat:convenience", TriggerVisual.Category("convenience"),
        listOf(poi("CU 역삼점", 180.0), poi("GS25 역삼힐스점", 240.0), poi("세븐일레븐 역삼중앙점", 410.0)),
    ),
    NearbyGroup("cat:pharmacy", TriggerVisual.Category("pharmacy"), listOf(poi("온누리약국", 650.0), poi("역삼365약국", 720.0))),
    NearbyGroup("cat:daiso", TriggerVisual.BrandPreset("daiso"), listOf(poi("다이소 역삼점", 1100.0))),
)

@Composable
private fun Nearby(state: NearbyUiState) {
    RecordOfPTheme { NearbyContent(state = state, onBack = {}, onRefresh = {}, onPoiClick = {}) }
}

@LightDarkPreviews
@Composable
private fun NearbyListPreview() = Nearby(NearbyUiState(loading = false, groups = groups))

@LightDarkPreviews
@Composable
private fun NearbyNoLocationPreview() = Nearby(NearbyUiState(loading = false, locationUnavailable = true))

@Preview(name = "큰 글꼴", showBackground = true, fontScale = 2f)
@Composable
private fun NearbyLargeFontPreview() = Nearby(NearbyUiState(loading = false, groups = groups.take(1)))
