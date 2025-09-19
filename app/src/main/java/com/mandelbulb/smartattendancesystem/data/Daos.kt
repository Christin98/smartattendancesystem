package com.mandelbulb.smartattendancesystem.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface UserProfileDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(profile: UserProfileEntity)
    
    @Query("SELECT * FROM user_profile WHERE id = 1")
    suspend fun getUserProfile(): UserProfileEntity?
    
    @Query("SELECT * FROM user_profile WHERE id = 1")
    fun getUserProfileFlow(): Flow<UserProfileEntity?>
    
    @Query("DELETE FROM user_profile")
    suspend fun deleteUserProfile()
    
    @Query("UPDATE user_profile SET lastSync = :timestamp WHERE id = 1")
    suspend fun updateLastSync(timestamp: Long)
}

@Dao
interface AttendanceDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(attendance: AttendanceEntity)
    
    @Query("SELECT * FROM attendance WHERE synced = 0 ORDER BY timestamp ASC")
    suspend fun getUnsynced(): List<AttendanceEntity>

    @Query("SELECT * FROM attendance WHERE synced = 0 ORDER BY timestamp ASC")
    suspend fun getUnsyncedAttendance(): List<AttendanceEntity>

    @Query("SELECT COUNT(*) FROM attendance WHERE synced = 0")
    suspend fun getUnsyncedCount(): Int

    @Query("SELECT COUNT(*) FROM attendance WHERE synced = 0")
    fun getUnsyncedCountFlow(): Flow<Int>

    @Query("UPDATE attendance SET synced = 1, syncedAt = :timestamp WHERE id = :id")
    suspend fun markSynced(id: String, timestamp: Long = System.currentTimeMillis())
    
    @Query("SELECT * FROM attendance WHERE date(timestamp/1000, 'unixepoch') = :date ORDER BY timestamp DESC")
    suspend fun getAttendanceForDate(date: String): List<AttendanceEntity>
    
    @Query("SELECT * FROM attendance WHERE date(timestamp/1000, 'unixepoch') = :date AND checkType = :type ORDER BY timestamp DESC LIMIT 1")
    suspend fun getLastCheckForDateAndType(date: String, type: String): AttendanceEntity?
    
    @Query("SELECT COUNT(*) FROM attendance WHERE date(timestamp/1000, 'unixepoch') = :date")
    suspend fun getAttendanceCountForDate(date: String): Int
    
    @Query("SELECT * FROM attendance ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getRecentAttendance(limit: Int = 100): List<AttendanceEntity>
    
    @Query("SELECT * FROM attendance ORDER BY timestamp DESC")
    fun getAllAttendanceFlow(): Flow<List<AttendanceEntity>>
    
    @Query("DELETE FROM attendance WHERE timestamp < :before")
    suspend fun deleteOldRecords(before: Long)
    
    @Query("DELETE FROM attendance WHERE id = :id")
    suspend fun deleteById(id: String)
}

@Dao
interface AppSettingsDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(settings: AppSettingsEntity)
    
    @Query("SELECT * FROM app_settings WHERE id = 1")
    suspend fun getSettings(): AppSettingsEntity?
    
    @Query("SELECT * FROM app_settings WHERE id = 1")
    fun getSettingsFlow(): Flow<AppSettingsEntity?>
    
    @Query("UPDATE app_settings SET offlineMode = :offline WHERE id = 1")
    suspend fun updateOfflineMode(offline: Boolean)
}
