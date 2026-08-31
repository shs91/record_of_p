package com.recordofp.app.data.engine

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import com.recordofp.app.domain.engine.ReseedStamp
import com.recordofp.app.domain.model.GeoPoint
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first

@Singleton
class EngineStateStore @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) {
    private val atKey = longPreferencesKey("last_reseed_at")
    private val latKey = doublePreferencesKey("last_reseed_lat")
    private val lngKey = doublePreferencesKey("last_reseed_lng")

    suspend fun lastReseed(): ReseedStamp? {
        val p = dataStore.data.first()
        val at = p[atKey] ?: return null
        val lat = p[latKey] ?: return null
        val lng = p[lngKey] ?: return null
        return ReseedStamp(at, GeoPoint(lat, lng))
    }

    suspend fun recordReseed(stamp: ReseedStamp) {
        dataStore.edit {
            it[atKey] = stamp.atMs
            it[latKey] = stamp.point.lat
            it[lngKey] = stamp.point.lng
        }
    }
}
