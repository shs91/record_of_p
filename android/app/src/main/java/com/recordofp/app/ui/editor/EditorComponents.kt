package com.recordofp.app.ui.editor

import androidx.annotation.StringRes
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.InputChip
import androidx.compose.material3.InputChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.recordofp.app.R
import com.recordofp.app.domain.engine.PoiCandidate
import com.recordofp.app.ui.common.DistanceBadge
import com.recordofp.app.ui.common.TriggerVisual
import com.recordofp.app.ui.common.iconRes
import com.recordofp.app.ui.common.label
import com.recordofp.app.ui.common.tileColors
import com.recordofp.app.ui.theme.Motion
import com.recordofp.app.ui.theme.PillShape
import com.recordofp.app.ui.theme.Spacing
import kotlin.math.roundToInt

// 에디터 전용 컴포넌트 (개편안 2 §2 에디터). 크기 값은 컴포넌트 고유값이라 여기에 둔다.

/** 섹션 — 제목 15 SemiBold 잉크 + 내용. 앞 블록과 24dp 떨어진다(바깥 간격 12 + 여기 12) */
@Composable
internal fun EditorSection(
    @StringRes title: Int,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(Modifier.padding(top = Spacing.s), verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
        Text(
            stringResource(title),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.semantics { heading() },
        )
        content()
    }
}

/**
 * 입력 필드 — 카드 바탕, 1dp 테두리(포커스 2dp 블루), 12dp 라운드.
 * 높이는 Material 텍스트 필드 최소 56dp를 따른다(개편안의 48~52dp보다 크다 — TalkBack 힌트·포커스 처리를 그대로 쓰기 위해).
 */
@Composable
internal fun EditorField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    emphasized: Boolean = false,
    singleLine: Boolean = false,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
) {
    val scheme = MaterialTheme.colorScheme
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        placeholder = { Text(placeholder) },
        textStyle = if (emphasized) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyLarge,
        singleLine = singleLine,
        keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions,
        shape = MaterialTheme.shapes.small,
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = scheme.surface,
            unfocusedContainerColor = scheme.surface,
            focusedBorderColor = scheme.primary,
            unfocusedBorderColor = scheme.outline,
            focusedPlaceholderColor = scheme.onSurfaceVariant,
            unfocusedPlaceholderColor = scheme.onSurfaceVariant,
            cursorColor = scheme.primary,
        ),
        modifier = modifier.fillMaxWidth(),
    )
}

/**
 * 카테고리 칩 36dp. 선택 안 됨 = 카드 + 1dp 헤어라인 + 카테고리 색 아이콘,
 * 선택 = 카테고리 tint + 1.5dp ink 테두리 + 체크 + ink 글자. 색은 짧은 전환(150ms)으로 바뀐다 (개편안 2 §1.4·§2)
 */
@Composable
internal fun CategoryChip(
    visual: TriggerVisual,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val tile = visual.tileColors()
    val scheme = MaterialTheme.colorScheme
    val spec = tween<Color>(Motion.SHORT_MS, easing = Motion.easing)
    val container by animateColorAsState(if (selected) tile.tint else scheme.surface, spec, label = "chipContainer")
    val border by animateColorAsState(if (selected) tile.ink else scheme.outlineVariant, spec, label = "chipBorder")
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = {
            Text(
                visual.label(),
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                ),
            )
        },
        leadingIcon = {
            if (selected) {
                Icon(Icons.Filled.Check, contentDescription = null, tint = tile.ink, modifier = Modifier.size(16.dp))
            } else {
                Icon(painterResource(visual.iconRes()), contentDescription = null, tint = tile.ink, modifier = Modifier.size(16.dp))
            }
        },
        shape = PillShape,
        border = BorderStroke(if (selected) 1.5.dp else 1.dp, border),
        colors = FilterChipDefaults.filterChipColors(
            containerColor = container,
            labelColor = scheme.onSurface,
            selectedContainerColor = container,
            selectedLabelColor = tile.ink,
        ),
        modifier = Modifier.heightIn(min = 36.dp),
    )
}

/**
 * 고른 브랜드·지점 칩 — 칩 전체를 누르면 뺀다(터치 영역 48dp). 브랜드는 회색 + 검색 아이콘,
 * 지점은 연블루 + 핀 (개편안 2 §2). 접근성 의미는 "<이름> 삭제" 버튼 하나로 바꾼다 —
 * InputChip(selected = false)의 체크박스·선택 안 됨 의미를 지우고 칩 전체를 버튼으로 읽게 한다 (최종 리뷰 M3).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun RemovableChip(visual: TriggerVisual, onRemove: () -> Unit) {
    val tile = visual.tileColors()
    val removeDescription = "${visual.label()} ${stringResource(R.string.action_remove)}"
    InputChip(
        selected = false,
        onClick = onRemove,
        modifier = Modifier.clearAndSetSemantics {
            contentDescription = removeDescription
            role = Role.Button
            onClick(label = removeDescription) { onRemove(); true }
        },
        label = {
            Text(
                visual.label(),
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontWeight = if (visual is TriggerVisual.Place) FontWeight.SemiBold else FontWeight.Medium,
                ),
            )
        },
        leadingIcon = {
            Icon(painterResource(visual.iconRes()), contentDescription = null, tint = tile.ink, modifier = Modifier.size(14.dp))
        },
        trailingIcon = {
            Icon(
                Icons.Filled.Close,
                contentDescription = null, // 칩 전체의 설명이 읽는다
                tint = tile.ink,
                modifier = Modifier.size(14.dp),
            )
        },
        shape = PillShape,
        border = null,
        colors = InputChipDefaults.inputChipColors(containerColor = tile.tint, labelColor = tile.ink),
    )
}

/** 브랜드 [추가] — 연블루 면 + 블루 글자, 입력란과 같은 높이 */
@Composable
internal fun AddBrandButton(enabled: Boolean, onClick: () -> Unit) {
    FilledTonalButton(
        onClick = onClick,
        enabled = enabled,
        shape = MaterialTheme.shapes.small,
        colors = ButtonDefaults.filledTonalButtonColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        ),
        contentPadding = PaddingValues(horizontal = Spacing.m),
        modifier = Modifier.heightIn(min = 56.dp),
    ) {
        Text(stringResource(R.string.editor_brand_add), style = MaterialTheme.typography.labelLarge)
    }
}

/** 장소 검색 버튼 — 회색 정사각, 입력란과 같은 높이 */
@Composable
internal fun SearchPlaceButton(onClick: () -> Unit) {
    FilledIconButton(
        onClick = onClick,
        shape = MaterialTheme.shapes.small,
        colors = IconButtonDefaults.filledIconButtonColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSurface,
        ),
        modifier = Modifier.sizeIn(minWidth = 56.dp, minHeight = 56.dp),
    ) {
        Icon(
            painterResource(R.drawable.ic_trigger_search),
            contentDescription = stringResource(R.string.editor_place_hint),
            modifier = Modifier.size(24.dp),
        )
    }
}

/** 저장·삭제 실패 — 본문 맨 위 오류 면. 나타나면 TalkBack이 읽는다 */
@Composable
internal fun SaveFailedNotice() {
    Surface(
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.errorContainer,
        contentColor = MaterialTheme.colorScheme.onErrorContainer,
        modifier = Modifier
            .fillMaxWidth()
            .semantics { liveRegion = LiveRegionMode.Polite },
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = Spacing.s),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Icon(painterResource(R.drawable.ic_alert_error), contentDescription = null, modifier = Modifier.size(20.dp))
            Text(
                stringResource(R.string.editor_save_failed),
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
            )
        }
    }
}

/** 장소 검색 결과 카드 — 이름 + 거리 배지 */
@Composable
internal fun PlaceResultCard(candidate: PoiCandidate, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = Spacing.m, vertical = 13.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                candidate.name,
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.weight(1f, fill = false).padding(end = Spacing.s),
            )
            DistanceBadge(candidate.distanceM.roundToInt())
        }
    }
}
