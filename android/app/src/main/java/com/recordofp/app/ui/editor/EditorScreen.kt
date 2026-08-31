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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.InputChipDefaults
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.recordofp.app.R
import com.recordofp.app.domain.engine.PoiCandidate
import com.recordofp.app.domain.model.TriggerCatalog
import com.recordofp.app.ui.common.catalogLabelRes
import kotlin.math.roundToInt

/**
 * 제목 입력 → 트리거 선택(카테고리 칩 다중 선택 / 브랜드 입력 / 장소 검색) → 저장.
 * "3탭 + 타이핑 이내" 목표 (설계 §4.1)
 */
@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun EditorScreen(
    onDone: () -> Unit,
    viewModel: EditorViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var brandInput by remember { mutableStateOf("") }

    LaunchedEffect(state.saved) { if (state.saved) onDone() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        OutlinedTextField(
            value = state.title,
            onValueChange = viewModel::onTitleChange,
            label = { Text(stringResource(R.string.editor_title_hint)) },
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = state.memo,
            onValueChange = viewModel::onMemoChange,
            label = { Text(stringResource(R.string.editor_memo_hint)) },
            minLines = 2,
            modifier = Modifier.fillMaxWidth(),
        )

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.editor_section_category), style = MaterialTheme.typography.titleSmall)
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                TriggerCatalog.entries.forEach { entry ->
                    FilterChip(
                        selected = entry.id in state.selectedCategoryIds,
                        onClick = { viewModel.toggleCategory(entry.id) },
                        label = { Text("${entry.emoji} ${stringResource(catalogLabelRes(entry.id))}") },
                    )
                }
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(
                    value = brandInput,
                    onValueChange = { brandInput = it },
                    label = { Text(stringResource(R.string.editor_brand_hint)) },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = { viewModel.addBrand(brandInput); brandInput = "" }) {
                    Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.action_add_brand))
                }
            }
            if (state.brandKeywords.isNotEmpty()) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    state.brandKeywords.forEach { keyword ->
                        InputChip(
                            selected = false,
                            onClick = {},
                            label = { Text(keyword) },
                            trailingIcon = {
                                Icon(
                                    Icons.Filled.Close,
                                    contentDescription = stringResource(R.string.action_remove),
                                    modifier = Modifier
                                        .size(InputChipDefaults.IconSize)
                                        .clickable { viewModel.removeBrand(keyword) },
                                )
                            },
                        )
                    }
                }
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(
                    value = state.placeQuery,
                    onValueChange = viewModel::onPlaceQueryChange,
                    label = { Text(stringResource(R.string.editor_place_hint)) },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = viewModel::searchPlace) {
                    Icon(Icons.Filled.Search, contentDescription = stringResource(R.string.editor_place_hint))
                }
            }
            if (state.placeSearchFailed) {
                Text(
                    stringResource(R.string.editor_place_search_failed),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            state.placeResults.forEach { candidate ->
                PlaceResultItem(
                    candidate = candidate,
                    onClick = { viewModel.pickPlace(PickedPlace(candidate.name, candidate.id, candidate.point)) },
                )
            }
            state.place?.let { place ->
                InputChip(
                    selected = true,
                    onClick = {},
                    label = { Text(place.name) },
                    trailingIcon = {
                        Icon(
                            Icons.Filled.Close,
                            contentDescription = stringResource(R.string.action_remove),
                            modifier = Modifier
                                .size(InputChipDefaults.IconSize)
                                .clickable { viewModel.clearPlace() },
                        )
                    },
                )
            }
        }

        Button(
            onClick = viewModel::save,
            enabled = state.canSave,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.editor_save))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PlaceResultItem(candidate: PoiCandidate, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(candidate.name) },
        supportingContent = { Text(stringResource(R.string.editor_place_distance, candidate.distanceM.roundToInt())) },
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
    )
}
