package com.recordofp.app.ui.permissions

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.location.LocationManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LifecycleResumeEffect

/**
 * 화면이 보이는 동안의 보호 상태 (§4.3). 재개(onResume)할 때 다시 읽고, 재개된 동안 기기 위치 토글이 바뀌어도 다시 읽는다 —
 * 빠른 설정(알림창)에서 위치를 켜면 액티비티가 멈추지 않아 onResume이 돌지 않기 때문이다 (묶음 B 인계).
 * 다시 읽을 때마다 onRead를 부른다 — 보호 복구 전이 보고(F1)에 쓴다.
 */
@Composable
fun rememberPermissionSnapshot(onRead: (PermissionSnapshot) -> Unit = {}): PermissionSnapshot {
    val context = LocalContext.current
    var snapshot by remember { mutableStateOf(readPermissionSnapshot(context)) }
    val currentOnRead by rememberUpdatedState(onRead)
    LifecycleResumeEffect(context) {
        fun refresh() {
            snapshot = readPermissionSnapshot(context)
            currentOnRead(snapshot)
        }
        refresh()
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context, intent: Intent) = refresh()
        }
        // 시스템 방송이라 내보내지 않아도 받는다
        ContextCompat.registerReceiver(
            context, receiver, IntentFilter(LocationManager.MODE_CHANGED_ACTION), ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        onPauseOrDispose { context.unregisterReceiver(receiver) }
    }
    return snapshot
}
