package com.mandelbulb.smartattendancesystem.sync

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.util.Log
import com.mandelbulb.smartattendancesystem.data.AppDatabase
import com.mandelbulb.smartattendancesystem.data.AttendanceEntity
import com.mandelbulb.smartattendancesystem.data.UserPreferences
import com.mandelbulb.smartattendancesystem.network.PostgresApiService
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*

class SyncManager(private val context: Context) {
    companion object {
        private const val TAG = "SyncManager"
        private const val SYNC_INTERVAL_MS = 30000L // Sync every 30 seconds when online
    }

    private val database = AppDatabase.getInstance(context)
    private val postgresApi = PostgresApiService()
    private val userPreferences = UserPreferences(context)

    private val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    private var syncJob: Job? = null
    private var isNetworkAvailable = false

    // Flow to emit sync status updates
    private val _syncStatus = MutableStateFlow(SyncStatus())
    val syncStatus: StateFlow<SyncStatus> = _syncStatus.asStateFlow()

    // Flow to emit network status
    private val _networkStatus = MutableStateFlow(NetworkStatus.UNKNOWN)
    val networkStatus: StateFlow<NetworkStatus> = _networkStatus.asStateFlow()

    data class SyncStatus(
        val isSyncing: Boolean = false,
        val lastSyncTime: Long? = null,
        val pendingRecords: Int = 0,
        val syncedRecords: Int = 0,
        val failedRecords: Int = 0,
        val message: String = "Ready to sync",
        val progress: Float = 0f
    )

    enum class NetworkStatus {
        ONLINE,
        OFFLINE,
        UNKNOWN
    }

    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            Log.d(TAG, "Network became available")
            isNetworkAvailable = true
            _networkStatus.value = NetworkStatus.ONLINE

            // Automatically start syncing when network becomes available
            CoroutineScope(Dispatchers.IO).launch {
                delay(2000) // Wait 2 seconds for network to stabilize
                if (isNetworkAvailable) {
                    Log.d(TAG, "Auto-syncing after network became available")
                    syncNow("Network connected - auto sync")
                }
            }
        }

        override fun onLost(network: Network) {
            Log.d(TAG, "Network lost")
            isNetworkAvailable = false
            _networkStatus.value = NetworkStatus.OFFLINE
            stopPeriodicSync()

            _syncStatus.value = _syncStatus.value.copy(
                message = "Offline - data will sync when connected"
            )
        }

        override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
            val hasInternet = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            val isValidated = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)

            if (hasInternet && isValidated && !isNetworkAvailable) {
                Log.d(TAG, "Network capabilities confirmed - Internet available")
                onAvailable(network)
            }
        }
    }

    init {
        // Register network callback to monitor connectivity changes
        registerNetworkCallback()

        // Check initial network status
        checkInitialNetworkStatus()
    }

    private fun registerNetworkCallback() {
        try {
            val networkRequest = NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .build()

            connectivityManager.registerNetworkCallback(networkRequest, networkCallback)
            Log.d(TAG, "Network callback registered")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to register network callback", e)
        }
    }

    private fun checkInitialNetworkStatus() {
        val network = connectivityManager.activeNetwork
        val capabilities = network?.let { connectivityManager.getNetworkCapabilities(it) }

        isNetworkAvailable = capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
        _networkStatus.value = if (isNetworkAvailable) NetworkStatus.ONLINE else NetworkStatus.OFFLINE

        Log.d(TAG, "Initial network status: ${_networkStatus.value}")

        if (isNetworkAvailable) {
            startPeriodicSync()
        }
    }

    fun startPeriodicSync() {
        if (syncJob?.isActive == true) {
            Log.d(TAG, "Periodic sync already running")
            return
        }

        syncJob = CoroutineScope(Dispatchers.IO).launch {
            while (isActive && isNetworkAvailable) {
                syncNow("Periodic sync")
                delay(SYNC_INTERVAL_MS)
            }
        }

        Log.d(TAG, "Started periodic sync")
    }

    fun stopPeriodicSync() {
        syncJob?.cancel()
        syncJob = null
        Log.d(TAG, "Stopped periodic sync")
    }

    suspend fun syncNow(reason: String = "Manual sync"): Boolean {
        if (_syncStatus.value.isSyncing) {
            Log.d(TAG, "Sync already in progress")
            return false
        }

        Log.d(TAG, "Starting sync: $reason")

        return withContext(Dispatchers.IO) {
            try {
                _syncStatus.value = _syncStatus.value.copy(
                    isSyncing = true,
                    message = "Checking for pending records...",
                    progress = 0.1f
                )

                // First, ensure the employee profile is synced to backend
                val profileSynced = syncEmployeeProfile()
                if (!profileSynced) {
                    Log.w(TAG, "Failed to sync employee profile, attendance sync may fail")
                }

                // Get unsynced attendance records
                val unsyncedRecords = database.attendanceDao().getUnsyncedAttendance()
                val totalRecords = unsyncedRecords.size

                _syncStatus.value = _syncStatus.value.copy(
                    pendingRecords = totalRecords,
                    message = if (totalRecords > 0) "Syncing $totalRecords records..." else "No records to sync",
                    progress = 0.2f
                )

                if (totalRecords == 0) {
                    _syncStatus.value = _syncStatus.value.copy(
                        isSyncing = false,
                        lastSyncTime = System.currentTimeMillis(),
                        message = "All data is synced",
                        progress = 1f
                    )

                    // Update last sync time in preferences
                    userPreferences.updateLastSync()
                    return@withContext true
                }

                var syncedCount = 0
                var failedCount = 0

                // Sync each record
                unsyncedRecords.forEachIndexed { index, record ->
                    try {
                        _syncStatus.value = _syncStatus.value.copy(
                            message = "Syncing record ${index + 1} of $totalRecords...",
                            progress = 0.2f + (0.7f * index / totalRecords)
                        )

                        val success = syncAttendanceRecord(record)

                        if (success) {
                            syncedCount++
                            database.attendanceDao().markSynced(record.id)
                            Log.d(TAG, "Synced record ${record.id}: ${record.employeeCode} - ${record.checkType}")
                        } else {
                            failedCount++
                            Log.e(TAG, "Failed to sync record ${record.id}")
                        }

                    } catch (e: Exception) {
                        failedCount++
                        Log.e(TAG, "Error syncing record ${record.id}", e)
                    }
                }

                // Update final status
                val syncTime = System.currentTimeMillis()
                _syncStatus.value = _syncStatus.value.copy(
                    isSyncing = false,
                    lastSyncTime = syncTime,
                    pendingRecords = 0,
                    syncedRecords = syncedCount,
                    failedRecords = failedCount,
                    message = buildSyncResultMessage(syncedCount, failedCount, totalRecords),
                    progress = 1f
                )

                // Update last sync time in preferences
                if (syncedCount > 0) {
                    userPreferences.updateLastSync()
                }

                Log.d(TAG, "Sync completed: $syncedCount synced, $failedCount failed out of $totalRecords")

                return@withContext failedCount == 0

            } catch (e: Exception) {
                Log.e(TAG, "Sync failed", e)

                _syncStatus.value = _syncStatus.value.copy(
                    isSyncing = false,
                    message = "Sync failed: ${e.message}",
                    progress = 0f
                )

                return@withContext false
            }
        }
    }

    private suspend fun syncAttendanceRecord(record: AttendanceEntity): Boolean {
        return try {
            // Check if we're online
            if (!isNetworkAvailable) {
                Log.d(TAG, "Network not available, skipping sync")
                return false
            }

            // First try to sync with employeeId
            val result = try {
                postgresApi.recordAttendance(
                    employeeId = record.employeeId,
                    checkType = record.checkType,
                    deviceId = record.deviceId,
                    embedding = null, // Already verified during check-in
                    timestamp = record.timestamp
                )
            } catch (e: IOException) {
                when {
                    e.message?.contains("Employee not found") == true -> {
                        // Employee not found, try to register them first
                        Log.w(TAG, "Employee not found in backend, attempting to sync profile first")
                        val profileSynced = syncEmployeeProfile()
                        if (profileSynced) {
                            // Retry the attendance sync
                            postgresApi.recordAttendance(
                                employeeId = record.employeeId,
                                checkType = record.checkType,
                                deviceId = record.deviceId,
                                embedding = null,
                                timestamp = record.timestamp
                            )
                        } else {
                            Log.e(TAG, "Failed to sync employee profile, cannot sync attendance")
                            false
                        }
                    }
                    e.message?.contains("Duplicate check-in/out detected") == true -> {
                        // This is a duplicate record that was already recorded on the server
                        // Mark it as synced to prevent repeated sync attempts
                        Log.w(TAG, "Record ${record.id} is a duplicate, marking as synced")
                        database.attendanceDao().markSynced(record.id)
                        true // Return true since the record exists on server
                    }
                    else -> throw e
                }
            }

            result
        } catch (e: Exception) {
            Log.e(TAG, "Failed to sync attendance record", e)
            false
        }
    }

    private suspend fun syncEmployeeProfile(): Boolean {
        return try {
            val userProfile = database.userProfileDao().getUserProfile()
            if (userProfile == null) {
                Log.w(TAG, "No user profile to sync")
                return false
            }

            // Convert ByteArray back to FloatArray for the API
            val embedding = if (userProfile.embedding.isNotEmpty()) {
                val buffer = java.nio.ByteBuffer.wrap(userProfile.embedding)
                val floatArray = FloatArray(userProfile.embedding.size / 4)
                for (i in floatArray.indices) {
                    floatArray[i] = buffer.getFloat()
                }
                floatArray
            } else {
                FloatArray(0)
            }

            // Try to register/update employee in backend with our local ID
            val employee = postgresApi.registerEmployee(
                employeeCode = userProfile.employeeCode,
                name = userProfile.name,
                department = userProfile.department,
                embedding = embedding,
                faceId = userProfile.faceId,
                employeeId = userProfile.employeeId  // Send our local ID to backend
            )

            if (employee != null) {
                // Update last sync time
                val updatedProfile = userProfile.copy(lastSync = System.currentTimeMillis())
                database.userProfileDao().insert(updatedProfile) // insert with REPLACE will update
                Log.d(TAG, "Employee profile synced successfully")
                true
            } else {
                Log.e(TAG, "Failed to sync employee profile to backend")
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error syncing employee profile", e)
            false
        }
    }

    private fun buildSyncResultMessage(synced: Int, failed: Int, total: Int): String {
        return when {
            failed == 0 && synced == total -> "✓ All $total records synced successfully"
            failed == 0 && synced > 0 -> "✓ Synced $synced records"
            synced == 0 && failed > 0 -> "✗ Failed to sync $failed records"
            else -> "Synced $synced, failed $failed out of $total records"
        }
    }

    fun getFormattedLastSyncTime(): String {
        val lastSync = _syncStatus.value.lastSyncTime ?: return "Never synced"

        val now = System.currentTimeMillis()
        val diff = now - lastSync

        return when {
            diff < 60000 -> "Just now"
            diff < 3600000 -> "${diff / 60000} minutes ago"
            diff < 86400000 -> "${diff / 3600000} hours ago"
            else -> {
                val sdf = SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault())
                sdf.format(Date(lastSync))
            }
        }
    }

    suspend fun getPendingRecordsCount(): Int {
        return withContext(Dispatchers.IO) {
            database.attendanceDao().getUnsyncedCount()
        }
    }

    fun cleanup() {
        try {
            connectivityManager.unregisterNetworkCallback(networkCallback)
            stopPeriodicSync()
            Log.d(TAG, "SyncManager cleaned up")
        } catch (e: Exception) {
            Log.e(TAG, "Error during cleanup", e)
        }
    }
}