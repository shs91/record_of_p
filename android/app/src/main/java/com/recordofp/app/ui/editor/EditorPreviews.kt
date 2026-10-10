package com.recordofp.app.ui.editor

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.recordofp.app.domain.model.GeoPoint
import com.recordofp.app.ui.theme.LightDarkPreviews
import com.recordofp.app.ui.theme.RecordOfPTheme

// 에디터 미리보기 — 라이트·다크 두 벌과 큰 글꼴 (디자인 시스템 §5, 개편안 2 목업 "에디터")

private object NoopEditorActions : EditorActions {
    override fun onTitleChange(v: String) {}
    override fun onMemoChange(v: String) {}
    override fun toggleCategory(id: String) {}
    override fun onBrandInputChange(v: String) {}
    override fun commitBrandInput() {}
    override fun removeBrand(keyword: String) {}
    override fun onPlaceQueryChange(v: String) {}
    override fun searchPlace() {}
    override fun pickPlace(place: PickedPlace) {}
    override fun clearPlace() {}
    override fun save() {}
    override fun delete() {}
}

private val newRecord = EditorUiState(
    title = "건전지 사기",
    selectedCategoryIds = setOf("convenience"),
    brandKeywords = listOf("이마트24"),
    brandInput = "GS25",
)

private val editFailed = EditorUiState(
    editingId = 7,
    title = "셔츠 맡기기",
    memo = "흰 셔츠 2장",
    selectedCategoryIds = setOf("laundry"),
    place = PickedPlace("크린토피아 역삼점", "k1", GeoPoint(37.5, 127.03)),
    saveFailed = true,
)

@LightDarkPreviews
@Composable
private fun EditorNewPreview() {
    RecordOfPTheme { EditorContent(state = newRecord, actions = NoopEditorActions, onClose = {}) }
}

@LightDarkPreviews
@Composable
private fun EditorEditFailedPreview() {
    RecordOfPTheme { EditorContent(state = editFailed, actions = NoopEditorActions, onClose = {}) }
}

@Preview(name = "큰 글꼴", showBackground = true, fontScale = 2f)
@Composable
private fun EditorLargeFontPreview() {
    RecordOfPTheme { EditorContent(state = newRecord, actions = NoopEditorActions, onClose = {}) }
}
