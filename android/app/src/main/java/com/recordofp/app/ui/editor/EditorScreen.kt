package com.recordofp.app.ui.editor

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.InputChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.recordofp.app.R
import com.recordofp.app.domain.engine.PoiCandidate
import com.recordofp.app.domain.model.TriggerCatalog
import com.recordofp.app.ui.common.DistanceBadge
import com.recordofp.app.ui.common.catalogLabelRes
import com.recordofp.app.ui.theme.PillShape
import kotlin.math.roundToInt

/**
 * 제목 입력 → 트리거 선택(카테고리 칩 다중 선택 / 브랜드 입력 / 장소 검색) → 저장.
 * "3탭 + 타이핑 이내" 목표 (설계 §4.1).
 * 클린 미니멀 개편 (개편안 §2 에디터): TopAppBar(✕ · 제목 · 저장)로 상태바 겹침(F3) 해소,
 * 섹션 레이블 12sp, 선택 칩 연블루+블루 텍스트, 장소 결과 카드화, 삭제는 하단 빨간 텍스트버튼.
 */
@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun EditorScreen(
    onDone: () -> Unit,
    viewModel: EditorViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var showDeleteConfirm by remember { mutableStateOf(false) }

    LaunchedEffect(state.saved) { if (state.saved) onDone() }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text(stringResource(R.string.editor_delete)) },
            text = { Text(stringResource(R.string.editor_delete_confirm)) },
            confirmButton = {
                TextButton(onClick = { showDeleteConfirm = false; viewModel.delete() }) {
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
                    IconButton(onClick = onDone) {
                        Icon(Icons.Filled.Close, contentDescription = stringResource(android.R.string.cancel))
                    }
                },
                title = {
                    Text(
                        stringResource(
                            if (state.editingId != null) R.string.editor_title_edit else R.string.title_editor,
                        ),
                    )
                },
                actions = {
                    TextButton(onClick = viewModel::save, enabled = state.canSave) {
                        Text(stringResource(R.string.editor_save), style = MaterialTheme.typography.labelLarge)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(start = 20.dp, end = 20.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (state.saveFailed) {
                // 저장·삭제 실패 — 입력은 그대로 남아 있으니 다시 시도하면 된다 (최종 리뷰 I6)
                Text(
                    stringResource(R.string.editor_save_failed),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            EditorField(
                value = state.title,
                onValueChange = viewModel::onTitleChange,
                placeholder = stringResource(R.string.editor_title_hint),
                emphasized = true,
            )
            EditorField(
                value = state.memo,
                onValueChange = viewModel::onMemoChange,
                placeholder = stringResource(R.string.editor_memo_hint),
                minLines = 2,
            )

            SectionLabel(stringResource(R.string.editor_section_category))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                TriggerCatalog.entries.forEach { entry ->
                    val selected = entry.id in state.selectedCategoryIds
                    FilterChip(
                        selected = selected,
                        onClick = { viewModel.toggleCategory(entry.id) },
                        label = { Text("${entry.emoji} ${stringResource(catalogLabelRes(entry.id))}") },
                        shape = PillShape,
                        border = null,
                        colors = FilterChipDefaults.filterChipColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer,
                            labelColor = MaterialTheme.colorScheme.onSecondaryContainer,
                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            selectedLabelColor = MaterialTheme.colorScheme.primary,
                        ),
                    )
                }
            }

            SectionLabel(stringResource(R.string.editor_section_brand))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                EditorField(
                    value = state.brandInput,
                    onValueChange = viewModel::onBrandInputChange,
                    placeholder = stringResource(R.string.editor_brand_example),
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = viewModel::commitBrandInput) {
                    Icon(
                        Icons.Filled.Add,
                        contentDescription = stringResource(R.string.action_add_brand),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            if (state.brandKeywords.isNotEmpty()) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    state.brandKeywords.forEach { keyword ->
                        RemovableChip(label = keyword, onRemove = { viewModel.removeBrand(keyword) })
                    }
                }
            }

            SectionLabel(stringResource(R.string.editor_section_place))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                EditorField(
                    value = state.placeQuery,
                    onValueChange = viewModel::onPlaceQueryChange,
                    placeholder = stringResource(R.string.editor_place_hint),
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = viewModel::searchPlace) {
                    Icon(
                        Icons.Filled.Search,
                        contentDescription = stringResource(R.string.editor_place_hint),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
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
                    onClick = { viewModel.pickPlace(PickedPlace(candidate.name, candidate.id, candidate.point)) },
                )
            }
            state.place?.let { place ->
                RemovableChip(label = "📌 ${place.name}", onRemove = viewModel::clearPlace)
            }

            if (state.editingId != null) {
                // 삭제는 저장과 동선을 분리해 맨 아래 빨간 텍스트버튼으로 (개편안 §2)
                TextButton(
                    onClick = { showDeleteConfirm = true },
                    enabled = !state.saving,
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                ) {
                    Text(
                        stringResource(R.string.editor_delete),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
            }
        }
    }
}

/** 섹션 레이블 12sp (개편안 §2 에디터) */
@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 10.dp),
    )
}

/** 클린 미니멀 필드 — 외곽선 대신 톤 차이(회색 면), 14dp 라운드 */
@Composable
private fun EditorField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    emphasized: Boolean = false,
    singleLine: Boolean = false,
    minLines: Int = 1,
) {
    TextField(
        value = value,
        onValueChange = onValueChange,
        placeholder = { Text(placeholder, color = MaterialTheme.colorScheme.onSurfaceVariant) },
        textStyle = if (emphasized) {
            MaterialTheme.typography.titleSmall
        } else {
            MaterialTheme.typography.bodyLarge
        },
        singleLine = singleLine,
        minLines = minLines,
        shape = RoundedCornerShape(14.dp),
        colors = TextFieldDefaults.colors(
            focusedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
            unfocusedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
            cursorColor = MaterialTheme.colorScheme.primary,
        ),
        modifier = modifier.fillMaxWidth(),
    )
}

/** 선택된 브랜드·장소 칩 — 연블루 pill + 제거 ✕ */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RemovableChip(label: String, onRemove: () -> Unit) {
    InputChip(
        selected = true,
        onClick = {},
        label = { Text(label) },
        shape = PillShape,
        border = null,
        colors = InputChipDefaults.inputChipColors(
            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
            selectedLabelColor = MaterialTheme.colorScheme.primary,
            selectedTrailingIconColor = MaterialTheme.colorScheme.primary,
        ),
        trailingIcon = {
            Icon(
                Icons.Filled.Close,
                contentDescription = stringResource(R.string.action_remove),
                modifier = Modifier
                    .size(InputChipDefaults.IconSize)
                    .clickable(onClick = onRemove),
            )
        },
    )
}

/** 장소 검색 결과 카드 — 이름 + 거리 배지 (개편안 §2) */
@Composable
private fun PlaceResultCard(candidate: PoiCandidate, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 13.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                candidate.name,
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.weight(1f, fill = false),
            )
            DistanceBadge(candidate.distanceM.roundToInt())
        }
    }
}

