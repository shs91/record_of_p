package com.recordofp.app.ui.common

import androidx.annotation.StringRes
import com.recordofp.app.R

@StringRes
fun catalogLabelRes(categoryId: String): Int = when (categoryId) {
    "convenience" -> R.string.cat_convenience
    "mart" -> R.string.cat_mart
    "pharmacy" -> R.string.cat_pharmacy
    "bank" -> R.string.cat_bank
    "post" -> R.string.cat_post
    "fuel" -> R.string.cat_fuel
    "laundry" -> R.string.cat_laundry
    "cafe" -> R.string.cat_cafe
    "hospital" -> R.string.cat_hospital
    "subway" -> R.string.cat_subway
    "daiso" -> R.string.cat_daiso
    "oliveyoung" -> R.string.cat_oliveyoung
    else -> R.string.cat_convenience // 카탈로그에 없는 id는 저장 경로에서 걸러진다
}
