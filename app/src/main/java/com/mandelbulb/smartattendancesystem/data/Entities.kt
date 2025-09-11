package com.mandelbulb.smartattendancesystem.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Index

// Single user profile stored locally
@Entity(tableName = "user_profile")
data class UserProfileEntity(
    @PrimaryKey val id: Int = 1, // Only one user per device
    val employeeId: String,
    val employeeCode: String,
    val name: String,
    val department: String,
    val embedding: ByteArray, // Local face embedding for offline
    val faceId: String? = null, // Azure Face API person ID
    val registrationDate: Long,
    val lastSync: Long = 0
)

// Local attendance records
@Entity(tableName = "attendance")
data class AttendanceEntity(
    @PrimaryKey val id: String = java.util.UUID.randomUUID().toString(),
    val employeeId: String,
    val employeeCode: String,
    val checkType: String, // "IN" or "OUT"
    val timestamp: Long,
    val mode: String, // "ONLINE" or "OFFLINE"
    val confidence: Double = 0.0,
    val location: String? = null,
    val deviceId: String,
    var synced: Boolean = false,
    val syncedAt: Long? = null
)

// Settings and configuration
@Entity(tableName = "app_settings")
data class AppSettingsEntity(
    @PrimaryKey val id: Int = 1,
    val apiBaseUrl: String = "",
    val azureEndpoint: String = "",
    val azureSubscriptionKey: String = "",
    val autoSync: Boolean = true,
    val offlineMode: Boolean = false,
    val lastUpdated: Long = System.currentTimeMillis()
)
