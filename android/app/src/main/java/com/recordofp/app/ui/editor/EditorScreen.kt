package com.recordofp.app.ui.editor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.recordofp.app.R
import com.recordofp.app.domain.model.TriggerCatalog
import com.recordofp.app.ui.common.TriggerVisual
import com.recordofp.app.ui.common.categoryVisual
import com.recordofp.app.ui.theme.Spacing

/**
 * 제목 입력 → 트리거 선택(카테고리 칩 다중 선택 / 브랜드 입력 / 장소 검색) → 저장.
 * "3탭 + 타이핑 이내" 목표 (설계 §4.1). 모양은 개편안 2 §2 에디터 — 카드 바탕 입력, 카테고리 색 칩, 브랜드 안내, 저장 실패 면.
 */
@Composable
fun EditorScreen(
    onDone: () -> Unit,
    viewModel: EditorViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(state.saved) { if (state.saved) onDone() }
    EditorContent(state = state, actions = viewModel, onClose = onDone)
}

/** 에디터 본체 — 상태와 동작만 받는다(미리보기는 EditorPreviews.kt) */
@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun EditorContent(
    state: EditorUiState,
    actions: EditorActions,
    onClose: () -> Unit,
) {
    val scrollState = rememberScrollState()
    // 저장 실패 면은 본문 맨 위에 있다 — 아래 섹션까지 내려가 있어도 보이게 올린다 (최종 리뷰 M6)
    LaunchedEffect(state.saveFailed) { if (state.saveFailed) scrollState.animateScrollTo(0) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text(stringResource(R.string.editor_delete)) },
            text = { Text(stringResource(R.string.editor_delete_confirm)) },
            confirmButton = {
                TextButton(onClick = { showDeleteConfirm = false; actions.delete() }) {
                    Text(stringResource(android.R.string.ok), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text(stringResource(android.R.string.cancel))
                }
            },
        )
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(Icons.Filled.Close, contentDescription = stringResource(android.R.string.cancel))
                    }
                },
                title = {
                    Text(stringResource(if (state.editingId != null) R.string.editor_title_edit else R.string.title_editor))
                },
                actions = {
                    // 종이 위 블루 글자는 onPrimaryContainer — primary는 종이 위 4.5:1이 안 된다 (개편안 2 §1.1)
                    TextButton(
                        onClick = actions::save,
                        enabled = state.canSave,
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onPrimaryContainer),
                    ) {
                        Text(stringResource(R.string.editor_save), style = MaterialTheme.typography.labelLarge)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                // 키보드가 아래쪽 장소 검색란·결과를 가리지 않게 — Scaffold가 이미 준 시스템 바 여백은 빼고 더한다
                .consumeWindowInsets(padding)
                .imePadding()
                .verticalScroll(scrollState)
                .padding(start = Spacing.screen, end = Spacing.screen, top = Spacing.xs, bottom = Spacing.xl),
            verticalArrangement = Arrangement.spacedBy(Spacing.s),
        ) {
            // 저장·삭제 실패 — 입력은 그대로 남아 있으니 다시 시도하면 된다 (최종 리뷰 I6)
            if (state.saveFailed) SaveFailedNotice()
            EditorField(
                value = state.title,
                onValueChange = actions::onTitleChange,
                placeholder = stringResource(R.string.editor_title_hint),
                emphasized = true,
            )
            EditorField(
                value = state.memo,
                onValueChange = actions::onMemoChange,
                placeholder = stringResource(R.string.editor_memo_hint),
            )

            EditorSection(R.string.editor_section_category) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                    verticalArrangement = Arrangement.spacedBy(Spacing.xs),
                ) {
                    TriggerCatalog.entries.forEach { entry ->
                        CategoryChip(
                            visual = categoryVisual(entry.id),
                            selected = entry.id in state.selectedCategoryIds,
                            onClick = { actions.toggleCategory(entry.id) },
                        )
                    }
                }
            }

            EditorSection(R.string.editor_section_brand) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                ) {
                    EditorField(
                        value = state.brandInput,
                        onValueChange = actions::onBrandInputChange,
                        placeholder = stringResource(R.string.editor_brand_example),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { actions.commitBrandInput() }),
                        modifier = Modifier.weight(1f),
                    )
                    AddBrandButton(enabled = state.brandInput.isNotBlank(), onClick = actions::commitBrandInput)
                }
                if (state.brandInput.isNotBlank()) {
                    // [추가]를 누르지 않아도 저장할 때 확정된다는 것을 입력하는 동안 알려 준다 (개편안 2 §3)
                    Text(
                        stringResource(R.string.editor_brand_pending_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (state.brandKeywords.isNotEmpty()) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
                    ) {
                        state.brandKeywords.forEach { keyword ->
                            RemovableChip(
                                visual = TriggerVisual.BrandKeyword(keyword),
                                onRemove = { actions.removeBrand(keyword) },
                            )
                        }
                    }
                }
            }

            EditorSection(R.string.editor_section_place) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                ) {
                    EditorField(
                        value = state.placeQuery,
                        onValueChange = actions::onPlaceQueryChange,
                        placeholder = stringResource(R.string.editor_place_hint),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = { actions.searchPlace() }),
                        modifier = Modifier.weight(1f),
                    )
                    SearchPlaceButton(onClick = actions::searchPlace)
                }
                state.placeSearchError?.let { error ->
                    // F4: 원인별 안내 — 401(서비스)을 "네트워크 확인"으로 오인시키지 않는다
                    Text(
                        stringResource(
                            when (error) {
                                PlaceSearchError.NO_LOCATION -> R.string.nearby_no_location
                                PlaceSearchError.NETWORK -> R.string.editor_place_error_network
                                PlaceSearchError.SERVICE -> R.string.editor_place_error_service
                            },
                        ),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                state.placeResults.forEach { candidate ->
                    PlaceResultCard(
                        candidate = candidate,
                        onClick = { actions.pickPlace(PickedPlace(candidate.name, candidate.id, candidate.point)) },
                    )
                }
                state.place?.let { place ->
                    RemovableChip(visual = TriggerVisual.Place(place.name), onRemove = actions::clearPlace)
                }
            }

            if (state.editingId != null) {
                // 삭제는 저장과 동선을 분리해 맨 아래 가운데 빨간 텍스트 버튼으로 (개편안 2 §2)
                Box(Modifier.fillMaxWidth().padding(top = Spacing.xs), contentAlignment = Alignment.Center) {
                    TextButton(
                        onClick = { showDeleteConfirm = true },
                        enabled = !state.saving,
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    ) {
                        Text(stringResource(R.string.editor_delete), style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
        }
    }
}
