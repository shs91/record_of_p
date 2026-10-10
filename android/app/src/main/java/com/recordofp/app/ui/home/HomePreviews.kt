package com.recordofp.app.ui.home

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.tooling.preview.Preview
import com.recordofp.app.domain.model.Reminder
import com.recordofp.app.domain.model.TriggerSpec
import com.recordofp.app.domain.model.TriggerType
import com.recordofp.app.ui.permissions.ProtectionIssue
import com.recordofp.app.ui.theme.LightDarkPreviews
import com.recordofp.app.ui.theme.RecordOfPTheme

// 홈 미리보기 — 라이트·다크 두 벌과 큰 글꼴 (디자인 시스템 §5, 개편안 2 목업 "홈")

private fun reminder(id: Long, title: String, vararg triggers: TriggerSpec) =
    Reminder(id = id, title = title, createdAt = 0, updatedAt = 0, triggers = triggers.toList())

private fun category(id: String) = TriggerSpec(type = TriggerType.CATEGORY, categoryId = id)
private fun place(name: String) = TriggerSpec(type = TriggerType.PLACE, placeName = name)

private val sample = listOf(
    reminder(1, "건전지 사기", category("convenience")),
    reminder(2, "감기약 사기", category("pharmacy")),
    reminder(3, "셔츠 맡기기", category("laundry"), place("크린토피아 역삼점")),
    reminder(4, "택배 찾기", place("역삼동 무인택배함")),
    reminder(5, "수납함 사기", category("daiso")),
)

/** 긴 제목·트리거가 많은 카드 — 제목 두 줄, 트리거 줄 한 줄에서 말줄임 */
private val crowded = reminder(
    6, "주말 집들이 선물로 디퓨저와 향초, 예쁜 컵 두 개를 한꺼번에 사기",
    category("mart"), category("convenience"), category("cafe"), category("daiso"),
    TriggerSpec(type = TriggerType.BRAND, brandKeyword = "GS25"),
)

@Composable
private fun Home(items: List<Reminder>, issue: ProtectionIssue?) {
    RecordOfPTheme {
        HomeContent(
            items = items,
            issue = issue,
            snackbarHostState = remember { SnackbarHostState() },
            onAddClick = {},
            onItemClick = {},
            onNearbyClick = {},
            onSettingsClick = {},
            onComplete = {},
        )
    }
}

@LightDarkPreviews
@Composable
private fun HomeListPreview() = Home(sample, ProtectionIssue.BACKGROUND_LOCATION_OFF)

@LightDarkPreviews
@Composable
private fun HomeEmptyPreview() = Home(emptyList(), issue = null)

@Preview(name = "큰 글꼴", showBackground = true, fontScale = 2f)
@Composable
private fun HomeLargeFontPreview() = Home(listOf(crowded) + sample.take(2), issue = null)

/** 첫 화면 — 기록 0개 + '항상 허용' 배너. 360x740dp에서 빈 상태가 가운데에 오고, 모자라면 스크롤된다 */
@Preview(name = "빈 상태 + 배너", showBackground = true, device = "spec:width=360dp,height=740dp")
@Composable
private fun HomeEmptyBannerPreview() = Home(emptyList(), ProtectionIssue.BACKGROUND_LOCATION_OFF)

@Preview(
    name = "빈 상태 + 배너, 큰 글꼴", showBackground = true, fontScale = 2f,
    device = "spec:width=360dp,height=740dp",
)
@Composable
private fun HomeEmptyBannerLargeFontPreview() = Home(emptyList(), ProtectionIssue.BACKGROUND_LOCATION_OFF)
