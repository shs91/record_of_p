package com.recordofp.app.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.recordofp.app.ui.theme.LightDarkPreviews
import com.recordofp.app.ui.theme.RecordOfPTheme
import com.recordofp.app.ui.theme.Spacing
import com.recordofp.app.ui.theme.inverseSuccessColor

/**
 * 완료 뒤 [실행 취소] 스낵바 — inverse 면 16dp 라운드, 성공 체크 원, 블루 액션 (개편안 2 §1.2·§2).
 * SnackbarHost 안에서 그린다 — 호스트가 TalkBack 안내(liveRegion)와 접근성 표시 시간을 맡는다.
 */
@Composable
fun UndoSnackbar(
    message: String,
    actionLabel: String,
    onAction: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = scheme.inverseSurface,
        contentColor = scheme.inverseOnSurface,
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.heightIn(min = 56.dp).padding(start = Spacing.m, end = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(
                modifier = Modifier.size(24.dp).background(inverseSuccessColor(), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Filled.Check, contentDescription = null, tint = scheme.inverseSurface, modifier = Modifier.size(16.dp))
            }
            Text(
                message,
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                modifier = Modifier.weight(1f),
            )
            TextButton(
                onClick = onAction,
                colors = ButtonDefaults.textButtonColors(contentColor = scheme.inversePrimary),
            ) {
                Text(actionLabel, style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

@LightDarkPreviews
@Composable
private fun UndoSnackbarPreview() {
    RecordOfPTheme { UndoSnackbar(message = "완료했어요", actionLabel = "실행 취소", onAction = {}) }
}
