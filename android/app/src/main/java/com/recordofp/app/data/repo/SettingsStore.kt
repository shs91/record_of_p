package com.recordofp.app.data.repo

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

interface SettingsStore {
    val onboardingDone: Flow<Boolean>
    suspend fun setOnboardingDone()
}

@Singleton
class DataStoreSettingsStore @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) : SettingsStore {
    private val onboardingKey = booleanPreferencesKey("onboarding_done")

    override val onboardingDone: Flow<Boolean> = dataStore.data.map { it[onboardingKey] ?: false }

    override suspend fun setOnboardingDone() {
        dataStore.edit { it[onboardingKey] = true }
    }
}
