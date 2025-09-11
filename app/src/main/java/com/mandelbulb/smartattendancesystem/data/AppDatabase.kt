package com.mandelbulb.smartattendancesystem.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(
    entities = [UserProfileEntity::class, AttendanceEntity::class, AppSettingsEntity::class], 
    version = 3, 
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun userProfileDao(): UserProfileDao
    abstract fun attendanceDao(): AttendanceDao
    abstract fun appSettingsDao(): AppSettingsDao

    companion object {
        @Volatile private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val inst = Room.databaseBuilder(
                    context.applicationContext, 
                    AppDatabase::class.java, 
                    "smart-attendance-db"
                )
                    .fallbackToDestructiveMigration() // For development
                    .build()
                INSTANCE = inst
                inst
            }
        }
    }
}
