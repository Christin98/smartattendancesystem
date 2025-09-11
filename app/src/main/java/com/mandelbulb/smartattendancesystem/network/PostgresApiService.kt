package com.mandelbulb.smartattendancesystem.network

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException

class PostgresApiService {
    private val client = OkHttpClient()
    private val TAG = "PostgresApiService"
    
    companion object {
        // TODO: Replace with your actual API endpoint
        const val API_BASE_URL = "https://691d08503752.ngrok-free.app/api"
        const val API_KEY = "apifacekey"
    }
    
    data class Employee(
        val employeeId: String,
        val employeeCode: String,
        val name: String,
        val department: String,
        val faceId: String? = null,
        val embedding: FloatArray? = null,
        val registrationDate: Long,
        val isActive: Boolean = true
    )
    
    data class AttendanceRecord(
        val id: String,
        val employeeId: String,
        val checkType: String, // "IN" or "OUT"
        val timestamp: Long,
        val location: String? = null,
        val deviceId: String,
        val syncStatus: String = "SYNCED"
    )
    
    /**
     * Check if employee exists by face embedding
     */
    suspend fun findEmployeeByEmbedding(embedding: FloatArray): Employee? = withContext(Dispatchers.IO) {
        val url = "$API_BASE_URL/employees/find-by-embedding"
        
        val json = JSONObject().apply {
            val embeddingArray = JSONArray()
            embedding.forEach { embeddingArray.put(it) }
            put("embedding", embeddingArray)
            put("threshold", 0.95)
        }
        
        val requestBody = json.toString().toRequestBody("application/json".toMediaType())
        
        val request = Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer $API_KEY")
            .addHeader("Content-Type", "application/json")
            .post(requestBody)
            .build()
        
        try {
            val response = client.newCall(request).execute()
            if (response.isSuccessful) {
                val responseBody = response.body?.string()
                responseBody?.let { parseEmployeeResponse(it) }
            } else {
                Log.e(TAG, "Find employee failed: ${response.code}")
                null
            }
        } catch (e: IOException) {
            Log.e(TAG, "Network error finding employee " + e.message, e)
            null
        }
    }
    
    /**
     * Register new employee
     */
    suspend fun registerEmployee(
        employeeCode: String,
        name: String,
        department: String,
        embedding: FloatArray,
        faceId: String? = null
    ): Employee? = withContext(Dispatchers.IO) {
        val url = "$API_BASE_URL/employees/register"
        
        val json = JSONObject().apply {
            put("employeeCode", employeeCode)
            put("name", name)
            put("department", department)
            faceId?.let { put("faceId", it) }
            
            val embeddingArray = JSONArray()
            embedding.forEach { embeddingArray.put(it) }
            put("embedding", embeddingArray)
        }
        
        val requestBody = json.toString().toRequestBody("application/json".toMediaType())
        
        val request = Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer $API_KEY")
            .addHeader("Content-Type", "application/json")
            .post(requestBody)
            .build()
        
        try {
            val response = client.newCall(request).execute()
            if (response.isSuccessful) {
                val responseBody = response.body?.string()
                responseBody?.let { parseEmployeeResponse(it) }
            } else {
                Log.e(TAG, "Register employee failed: ${response.code} - ${response.body?.string()}")
                null
            }
        } catch (e: IOException) {
            Log.e(TAG, "Network error registering employee", e)
            null
        }
    }
    
    /**
     * Record attendance (check-in/out) with optional face verification
     */
    suspend fun recordAttendance(
        employeeId: String,
        checkType: String,
        deviceId: String,
        location: String? = null,
        embedding: FloatArray? = null
    ): Boolean = withContext(Dispatchers.IO) {
        val url = "$API_BASE_URL/attendance/record"
        
        val json = JSONObject().apply {
            put("employeeId", employeeId)
            put("checkType", checkType)
            put("timestamp", System.currentTimeMillis())
            put("deviceId", deviceId)
            location?.let { put("location", it) }
            
            // Include embedding for face verification if provided
            embedding?.let {
                val embeddingArray = JSONArray()
                it.forEach { value -> embeddingArray.put(value) }
                put("embedding", embeddingArray)
            }
        }
        
        val requestBody = json.toString().toRequestBody("application/json".toMediaType())
        
        val request = Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer $API_KEY")
            .addHeader("Content-Type", "application/json")
            .post(requestBody)
            .build()
        
        try {
            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                val errorBody = response.body?.string()
                Log.e(TAG, "Attendance record failed: ${response.code} - $errorBody")
                
                // Handle different error codes
                when (response.code) {
                    403 -> {
                        // Face verification failure
                        if (errorBody?.contains("Face verification failed") == true) {
                            throw IOException("Face verification failed. Please try again.")
                        } else {
                            throw IOException("Access denied: $errorBody")
                        }
                    }
                    409 -> {
                        // Duplicate check-in/out
                        throw IOException("Duplicate check-in/out detected. Please wait 5 minutes before trying again.")
                    }
                    404 -> {
                        // Employee not found
                        throw IOException("Employee not found")
                    }
                    else -> {
                        // Other errors
                        throw IOException("Error: ${response.code} - ${errorBody ?: "Unknown error"}")
                    }
                }
            }
            response.isSuccessful
        } catch (e: IOException) {
            Log.e(TAG, "Network error recording attendance", e)
            throw e
        }
    }
    
    /**
     * Sync offline attendance records
     */
    suspend fun syncAttendanceRecords(records: List<AttendanceRecord>): Boolean = withContext(Dispatchers.IO) {
        val url = "$API_BASE_URL/attendance/sync"
        
        val jsonArray = JSONArray()
        records.forEach { record ->
            val json = JSONObject().apply {
                put("id", record.id)
                put("employeeId", record.employeeId)
                put("checkType", record.checkType)
                put("timestamp", record.timestamp)
                put("deviceId", record.deviceId)
                record.location?.let { put("location", it) }
            }
            jsonArray.put(json)
        }
        
        val requestBody = jsonArray.toString().toRequestBody("application/json".toMediaType())
        
        val request = Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer $API_KEY")
            .addHeader("Content-Type", "application/json")
            .post(requestBody)
            .build()
        
        try {
            val response = client.newCall(request).execute()
            response.isSuccessful
        } catch (e: IOException) {
            Log.e(TAG, "Network error syncing attendance", e)
            false
        }
    }
    
    /**
     * Get employee attendance history
     */
    suspend fun getAttendanceHistory(employeeId: String, days: Int = 30): List<AttendanceRecord> = 
        withContext(Dispatchers.IO) {
            val url = "$API_BASE_URL/attendance/history?employeeId=$employeeId&days=$days"
            
            val request = Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer $API_KEY")
                .get()
                .build()
            
            try {
                val response = client.newCall(request).execute()
                if (response.isSuccessful) {
                    val responseBody = response.body?.string()
                    responseBody?.let { parseAttendanceRecords(it) } ?: emptyList()
                } else {
                    Log.e(TAG, "Get attendance history failed: ${response.code}")
                    emptyList()
                }
            } catch (e: IOException) {
                Log.e(TAG, "Network error getting attendance history", e)
                emptyList()
            }
        }
    
    private fun parseEmployeeResponse(json: String): Employee? {
        return try {
            val jsonObj = JSONObject(json)
            Employee(
                employeeId = jsonObj.getString("employeeId"),
                employeeCode = jsonObj.getString("employeeCode"),
                name = jsonObj.getString("name"),
                department = jsonObj.getString("department"),
                faceId = jsonObj.optString("faceId", null),
                embedding = parseEmbedding(jsonObj.optJSONArray("embedding")),
                registrationDate = jsonObj.getLong("registrationDate"),
                isActive = jsonObj.optBoolean("isActive", true)
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing employee response", e)
            null
        }
    }
    
    private fun parseEmbedding(jsonArray: JSONArray?): FloatArray? {
        return jsonArray?.let {
            FloatArray(it.length()) { i ->
                it.getDouble(i).toFloat()
            }
        }
    }
    
    private fun parseAttendanceRecords(json: String): List<AttendanceRecord> {
        val records = mutableListOf<AttendanceRecord>()
        try {
            val jsonArray = JSONArray(json)
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                records.add(
                    AttendanceRecord(
                        id = obj.getString("id"),
                        employeeId = obj.getString("employeeId"),
                        checkType = obj.getString("checkType"),
                        timestamp = obj.getLong("timestamp"),
                        location = obj.optString("location", null),
                        deviceId = obj.getString("deviceId"),
                        syncStatus = obj.optString("syncStatus", "SYNCED")
                    )
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing attendance records", e)
        }
        return records
    }
}