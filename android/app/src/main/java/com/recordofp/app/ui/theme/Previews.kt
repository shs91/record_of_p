package com.recordofp.app.ui.theme

import android.content.res.Configuration
import androidx.compose.ui.tooling.preview.Preview

/**
 * 화면 미리보기 라이트·다크 두 벌 (디자인 시스템 §5). 미리보기 안에서는 `RecordOfPTheme { }`를 기본값으로 감싼다 —
 * 기본값이 uiMode를 따르므로 successColor() 같은 헬퍼와 스킴이 같은 테마를 본다.
 */
@Preview(name = "라이트", showBackground = true)
@Preview(name = "다크", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES or Configuration.UI_MODE_TYPE_NORMAL)
annotation class LightDarkPreviews
