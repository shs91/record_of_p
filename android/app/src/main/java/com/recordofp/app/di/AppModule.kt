package com.recordofp.app.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import androidx.room.Room
import com.recordofp.app.BuildConfig
import com.recordofp.app.data.db.AppDatabase
import com.recordofp.app.data.engine.EngineStateStore
import com.recordofp.app.data.engine.FenceApplier
import com.recordofp.app.data.engine.ReseedStateStore
import com.recordofp.app.data.engine.DefaultGatePolicyProvider
import com.recordofp.app.data.engine.GatePolicyProvider
import com.recordofp.app.data.poi.KakaoLocalApi
import com.recordofp.app.data.poi.KakaoPoiRepository
import com.recordofp.app.data.poi.PoiRepository
import com.recordofp.app.data.repo.ReminderRepository
import com.recordofp.app.data.repo.RoomReminderRepository
import com.recordofp.app.domain.engine.DiffCalculator
import com.recordofp.app.domain.engine.NotificationGate
import com.recordofp.app.domain.engine.ReseedGovernor
import com.recordofp.app.domain.engine.ReseedPlanner
import com.recordofp.app.domain.engine.TriggerResolver
import com.recordofp.app.platform.geofence.GeofenceController
import com.recordofp.app.data.location.LocationProvider
import com.recordofp.app.data.repo.ReseedRequester
import com.recordofp.app.platform.location.FusedLocationProvider
import com.recordofp.app.platform.work.WorkManagerReseedRequester
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.time.Clock
import java.time.ZoneId
import javax.inject.Singleton
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

private val Context.appDataStore by preferencesDataStore(name = "record_of_p_prefs")

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    fun clock(): Clock = Clock.systemDefaultZone()

    @Provides
    fun zoneId(): ZoneId = ZoneId.systemDefault()

    @Provides
    @Singleton
    fun database(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, AppDatabase.NAME).build()

    @Provides
    fun reminderDao(db: AppDatabase) = db.reminderDao()

    @Provides
    fun triggerSpecDao(db: AppDatabase) = db.triggerSpecDao()

    @Provides
    fun geofenceRegDao(db: AppDatabase) = db.geofenceRegDao()

    @Provides
    fun notificationLogDao(db: AppDatabase) = db.notificationLogDao()

    @Provides
    fun engineRunLogDao(db: AppDatabase) = db.engineRunLogDao()

    @Provides
    @Singleton
    fun json(): Json = Json { ignoreUnknownKeys = true }

    @Provides
    @Singleton
    fun okHttpClient(): OkHttpClient =
        OkHttpClient.Builder()
            .addInterceptor { chain ->
                chain.proceed(
                    chain.request().newBuilder()
                        .header("Authorization", "KakaoAK ${BuildConfig.KAKAO_REST_KEY}")
                        .build(),
                )
            }
            .build()

    @Provides
    @Singleton
    fun kakaoLocalApi(client: OkHttpClient, json: Json): KakaoLocalApi =
        Retrofit.Builder()
            .baseUrl(KakaoLocalApi.BASE_URL)
            .client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(KakaoLocalApi::class.java)

    @Provides
    @Singleton
    fun dataStore(@ApplicationContext context: Context): DataStore<Preferences> = context.appDataStore

    @Provides
    fun triggerResolver() = TriggerResolver()

    @Provides
    fun reseedPlanner() = ReseedPlanner()

    @Provides
    fun diffCalculator() = DiffCalculator()

    @Provides
    fun reseedGovernor() = ReseedGovernor()

    @Provides
    fun notificationGate(zone: ZoneId) = NotificationGate(zone)
}

@Module
@InstallIn(SingletonComponent::class)
abstract class BindsModule {

    @Binds
    abstract fun poiRepository(impl: KakaoPoiRepository): PoiRepository

    @Binds
    abstract fun reminderRepository(impl: RoomReminderRepository): ReminderRepository

    @Binds
    abstract fun fenceApplier(impl: GeofenceController): FenceApplier

    @Binds
    abstract fun reseedStateStore(impl: EngineStateStore): ReseedStateStore

    @Binds
    abstract fun locationProvider(impl: FusedLocationProvider): LocationProvider

    @Binds
    abstract fun gatePolicyProvider(impl: DefaultGatePolicyProvider): GatePolicyProvider

    @Binds
    abstract fun reseedRequester(impl: WorkManagerReseedRequester): ReseedRequester
}
