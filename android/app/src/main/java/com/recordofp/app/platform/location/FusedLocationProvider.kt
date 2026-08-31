package com.recordofp.app.platform.location

import android.annotation.SuppressLint
import android.content.Context
import com.google.android.gms.location.CurrentLocationRequest
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import com.recordofp.app.domain.model.GeoPoint
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.tasks.await
import com.recordofp.app.data.location.LocationProvider

@Singleton
class FusedLocationProvider @Inject constructor(
    @ApplicationContext context: Context,
) : LocationProvider {

    private val client = LocationServices.getFusedLocationProviderClient(context)

    @SuppressLint("MissingPermission") // 호출부(Worker)가 권한 확인 후 진입
    override suspend fun currentOrLast(): GeoPoint? {
        val current = runCatching {
            client.getCurrentLocation(
                CurrentLocationRequest.Builder()
                    .setPriority(Priority.PRIORITY_BALANCED_POWER_ACCURACY)
                    .setDurationMillis(15_000)
                    .build(),
                CancellationTokenSource().token,
            ).await()
        }.getOrNull()
        val location = current ?: runCatching { client.lastLocation.await() }.getOrNull() ?: return null
        return GeoPoint(location.latitude, location.longitude)
    }
}
