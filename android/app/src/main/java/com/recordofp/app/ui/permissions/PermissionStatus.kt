package com.recordofp.app.ui.permissions

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.LocationManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import com.recordofp.app.data.notify.nearbyAlertsEnabled

data class PermissionSnapshot(
    /** 앱 알림과 "근처 알림" 채널이 모두 켜져 있는가 — 채널만 꺼도 알림은 오지 않는다 (최종 리뷰 I4) */
    val notifications: Boolean,
    /** "정확한 위치" — 사용자가 "대략적 위치"만 허용하면 false다. 지오펜스는 FINE이 필요하다 (검토 B2) */
    val fineLocation: Boolean,
    val backgroundLocation: Boolean,
    val batteryUnrestricted: Boolean,
    /** 기기 위치(시스템 토글)가 켜져 있는가 — 꺼지면 OS가 펜스를 전부 지운다 (최종 리뷰 I4) */
    val locationServicesOn: Boolean,
) {
    // 배터리 최적화는 의도적으로 제외 — 배너 과잉 노출 방지, 대시보드(§4.3)에서만 표시
    val fullyProtected: Boolean
        get() = notifications && fineLocation && backgroundLocation && locationServicesOn
}

fun readPermissionSnapshot(context: Context): PermissionSnapshot {
    fun granted(p: String) =
        ContextCompat.checkSelfPermission(context, p) == PackageManager.PERMISSION_GRANTED
    val pm = context.getSystemService(PowerManager::class.java)
    return PermissionSnapshot(
        notifications = nearbyAlertsEnabled(context),
        fineLocation = granted(Manifest.permission.ACCESS_FINE_LOCATION),
        backgroundLocation = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            granted(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
        } else {
            granted(Manifest.permission.ACCESS_FINE_LOCATION)
        },
        batteryUnrestricted = pm?.isIgnoringBatteryOptimizations(context.packageName) ?: false,
        locationServicesOn = context.getSystemService(LocationManager::class.java)
            ?.let { LocationManagerCompat.isLocationEnabled(it) } ?: false,
    )
}

/** A11+ 백그라운드 위치는 앱 설정에서만 켤 수 있다 (§4.2) — 설정 화면 딥링크 */
fun appDetailsSettingsIntent(context: Context): Intent =
    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))

/** 앱 알림 설정 — 채널 목록도 여기서 보인다 */
fun appNotificationSettingsIntent(context: Context): Intent =
    Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)

/** 기기 위치 켜기 (최종 리뷰 I4) */
fun locationSourceSettingsIntent(): Intent = Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)

/** 배터리 최적화 목록 — 예외를 직접 요청하지 않고 안내만 한다 (Play 정책, §4.2) */
fun batteryOptimizationSettingsIntent(): Intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)

/**
 * 시스템 설정 화면을 연다. 일부 제조사 빌드에 없는 화면이면 앱 상세 설정으로,
 * 그것마저 없으면 아무것도 하지 않는다 (최종 리뷰 M8)
 */
fun Context.openSettings(intent: Intent) {
    try {
        startActivity(intent)
    } catch (_: ActivityNotFoundException) {
        try {
            startActivity(appDetailsSettingsIntent(this))
        } catch (_: ActivityNotFoundException) {
        }
    }
}
