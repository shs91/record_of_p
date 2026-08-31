package com.recordofp.app.data.db

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [
        ReminderEntity::class,
        TriggerSpecEntity::class,
        GeofenceRegEntity::class,
        RegTriggerEntity::class,
        NotificationLogEntity::class,
        EngineRunLogEntity::class,
    ],
    version = 1,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun reminderDao(): ReminderDao
    abstract fun triggerSpecDao(): TriggerSpecDao
    abstract fun geofenceRegDao(): GeofenceRegDao
    abstract fun notificationLogDao(): NotificationLogDao
    abstract fun engineRunLogDao(): EngineRunLogDao

    companion object {
        const val NAME = "record_of_p.db"
    }
}
