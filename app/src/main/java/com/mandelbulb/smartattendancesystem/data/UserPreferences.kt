package com.mandelbulb.smartattendancesystem.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

// Extension property to get DataStore instance
private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "user_preferences")

class UserPreferences(private val context: Context) {
    
    companion object {
        val IS_REGISTERED = booleanPreferencesKey("is_registered")
        val EMPLOYEE_ID = stringPreferencesKey("employee_id")
        val EMPLOYEE_CODE = stringPreferencesKey("employee_code")
        val EMPLOYEE_NAME = stringPreferencesKey("employee_name")
        val DEPARTMENT = stringPreferencesKey("department")
        val FACE_ID = stringPreferencesKey("face_id") // Azure Face API person ID
        val REGISTRATION_DATE = longPreferencesKey("registration_date")
        val LAST_SYNC = longPreferencesKey("last_sync")
        val ANIMATIONS_ENABLED = booleanPreferencesKey("animations_enabled")
    }
    
    // Check if user is registered
    val isRegistered: Flow<Boolean> = context.dataStore.data
        .map { preferences ->
            preferences[IS_REGISTERED] ?: false
        }
    
    // Individual property flows
    val employeeId: Flow<String> = context.dataStore.data
        .map { preferences -> preferences[EMPLOYEE_ID] ?: "" }
    
    val employeeCode: Flow<String> = context.dataStore.data
        .map { preferences -> preferences[EMPLOYEE_CODE] ?: "" }
    
    val userName: Flow<String> = context.dataStore.data
        .map { preferences -> preferences[EMPLOYEE_NAME] ?: "" }
    
    val department: Flow<String> = context.dataStore.data
        .map { preferences -> preferences[DEPARTMENT] ?: "" }

    val animationsEnabled: Flow<Boolean> = context.dataStore.data
        .map { preferences -> preferences[ANIMATIONS_ENABLED] ?: true }

    // Get current user data
    val userData: Flow<UserData?> = context.dataStore.data
        .map { preferences ->
            if (preferences[IS_REGISTERED] == true) {
                UserData(
                    employeeId = preferences[EMPLOYEE_ID] ?: "",
                    employeeCode = preferences[EMPLOYEE_CODE] ?: "",
                    employeeName = preferences[EMPLOYEE_NAME] ?: "",
                    department = preferences[DEPARTMENT] ?: "",
                    faceId = preferences[FACE_ID],
                    registrationDate = preferences[REGISTRATION_DATE] ?: 0L
                )
            } else null
        }
    
    // Save user registration
    suspend fun saveUserRegistration(
        employeeId: String,
        employeeCode: String,
        employeeName: String,
        department: String,
        faceId: String? = null
    ) {
        context.dataStore.edit { preferences ->
            preferences[IS_REGISTERED] = true
            preferences[EMPLOYEE_ID] = employeeId
            preferences[EMPLOYEE_CODE] = employeeCode
            preferences[EMPLOYEE_NAME] = employeeName
            preferences[DEPARTMENT] = department
            faceId?.let { preferences[FACE_ID] = it }
            preferences[REGISTRATION_DATE] = System.currentTimeMillis()
        }
    }
    
    // Update last sync time
    suspend fun updateLastSync() {
        context.dataStore.edit { preferences ->
            preferences[LAST_SYNC] = System.currentTimeMillis()
        }
    }
    
    // Save user profile (alias for saveUserRegistration with different signature)
    suspend fun saveUserProfile(
        isRegistered: Boolean,
        employeeId: String,
        employeeCode: String,
        name: String,
        department: String,
        azureFaceId: String? = null
    ) {
        context.dataStore.edit { preferences ->
            preferences[IS_REGISTERED] = isRegistered
            preferences[EMPLOYEE_ID] = employeeId
            preferences[EMPLOYEE_CODE] = employeeCode
            preferences[EMPLOYEE_NAME] = name
            preferences[DEPARTMENT] = department
            azureFaceId?.let { preferences[FACE_ID] = it }
            preferences[REGISTRATION_DATE] = System.currentTimeMillis()
        }
    }
    
    // Clear user data (logout)
    suspend fun clearUserData() {
        context.dataStore.edit { preferences ->
            preferences.clear()
        }
    }
    
    // Clear user profile (alias for clearUserData)
    suspend fun clearUserProfile() {
        clearUserData()
    }

    // Toggle animations
    suspend fun setAnimationsEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[ANIMATIONS_ENABLED] = enabled
        }
    }
    
    // Get user for offline check
    suspend fun getOfflineUser(): UserData? {
        var userData: UserData? = null
        context.dataStore.data.collect { preferences ->
            if (preferences[IS_REGISTERED] == true) {
                userData = UserData(
                    employeeId = preferences[EMPLOYEE_ID] ?: "",
                    employeeCode = preferences[EMPLOYEE_CODE] ?: "",
                    employeeName = preferences[EMPLOYEE_NAME] ?: "",
                    department = preferences[DEPARTMENT] ?: "",
                    faceId = preferences[FACE_ID],
                    registrationDate = preferences[REGISTRATION_DATE] ?: 0L
                )
            }
        }
        return userData
    }
}

data class UserData(
    val employeeId: String,
    val employeeCode: String,
    val employeeName: String,
    val department: String,
    val faceId: String? = null,
    val registrationDate: Long
)