package com.mandelbulb.smartattendancesystem.sync

import android.content.Context
import android.util.Log
import com.mandelbulb.smartattendancesystem.data.AppDatabase
import com.mandelbulb.smartattendancesystem.network.NetworkMonitor
import com.mandelbulb.smartattendancesystem.network.PostgresApiService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class AttendanceSyncService(private val context: Context) {
    
    private val database = AppDatabase.getInstance(context)
    private val postgresApi = PostgresApiService()
    private val networkMonitor = NetworkMonitor(context)
    private val TAG = "AttendanceSyncService"
    
    suspend fun syncPendingAttendance(): Boolean = withContext(Dispatchers.IO) {
        try {
            if (!networkMonitor.isOnline()) {
                Log.d(TAG, "Device is offline, skipping sync")
                return@withContext false
            }
            
            val unsyncedRecords = database.attendanceDao().getUnsynced()
            
            if (unsyncedRecords.isEmpty()) {
                Log.d(TAG, "No unsynced records found")
                return@withContext true
            }
            
            Log.d(TAG, "Found ${unsyncedRecords.size} unsynced records")
            
            val attendanceRecords = unsyncedRecords.map { entity ->
                PostgresApiService.AttendanceRecord(
                    id = entity.id,
                    employeeId = entity.employeeId,
                    checkType = entity.checkType,
                    timestamp = entity.timestamp,
                    location = entity.location,
                    deviceId = entity.deviceId,
                    syncStatus = "PENDING"
                )
            }
            
            val success = postgresApi.syncAttendanceRecords(attendanceRecords)
            
            if (success) {
                unsyncedRecords.forEach { record ->
                    database.attendanceDao().markSynced(
                        id = record.id,
                        timestamp = System.currentTimeMillis()
                    )
                }
                Log.d(TAG, "Successfully synced ${unsyncedRecords.size} records")
                return@withContext true
            } else {
                Log.e(TAG, "Failed to sync records with server")
                return@withContext false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error during sync", e)
            return@withContext false
        }
    }
    
    suspend fun cleanOldRecords(daysToKeep: Int = 30) = withContext(Dispatchers.IO) {
        try {
            val cutoffTime = System.currentTimeMillis() - (daysToKeep * 24 * 60 * 60 * 1000L)
            database.attendanceDao().deleteOldRecords(cutoffTime)
            Log.d(TAG, "Cleaned old records before $cutoffTime")
        } catch (e: Exception) {
            Log.e(TAG, "Error cleaning old records", e)
        }
    }
}