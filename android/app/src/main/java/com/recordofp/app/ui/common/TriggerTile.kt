package com.recordofp.app.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp

/**
 * 트리거 타일 — 둥근 사각 tint 위에 ink 아이콘 (개편안 2 §2). 장식이라 설명이 없다 — 이름은 옆 글자가 말한다.
 * 크기: 홈 카드 48/16/24, 빈 상태 44/14/22, 주변 보기 그룹 머리 32/10/18 (타일/모서리/아이콘 dp)
 */
@Composable
fun TriggerTile(
    visual: TriggerVisual,
    size: Dp,
    cornerRadius: Dp,
    iconSize: Dp,
    modifier: Modifier = Modifier,
) {
    val colors = visual.tileColors()
    Box(
        modifier = modifier.size(size).background(colors.tint, RoundedCornerShape(cornerRadius)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painterResource(visual.iconRes()),
            contentDescription = null,
            tint = colors.ink,
            modifier = Modifier.size(iconSize),
        )
    }
}
