package com.mandelbulb.smartattendancesystem.network

import android.graphics.Bitmap
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.IOException
import androidx.core.graphics.scale

class AzureFaceService(
    private val subscriptionKey: String,
    private val endpoint: String
) {
    private val client = OkHttpClient()
    private val TAG = "AzureFaceService"
    
    // TODO: Replace with your actual Azure Face API details
    companion object {
        const val PERSON_GROUP_ID = "employees"
        const val AZURE_ENDPOINT = "https://ajayfaceapi02.cognitiveservices.azure.com"
        const val SUBSCRIPTION_KEY = "6H1Dui0GcElHDTG3RUOxZ8mKuQNw5WPtkqHvEjHZjjyY5XhMiVWsJQQJ99BFACGhslBXJ3w3AAAKACOGIxPl"
    }
    
    data class FaceVerificationResult(
        val isIdentical: Boolean,
        val confidence: Double,
        val personId: String? = null,
        val personName: String? = null
    )
    
    data class DetectedFace(
        val faceId: String,
        val faceRectangle: FaceRectangle,
        val faceLandmarks: Map<String, Point>? = null,
        val faceAttributes: FaceAttributes? = null
    )
    
    data class FaceRectangle(
        val top: Int,
        val left: Int,
        val width: Int,
        val height: Int
    )
    
    data class Point(val x: Double, val y: Double)
    
    data class FaceAttributes(
        val age: Double? = null,
        val gender: String? = null,
        val smile: Double? = null,
        val glasses: String? = null
    )
    
    /**
     * Detect faces in an image
     */
    suspend fun detectFace(bitmap: Bitmap): List<DetectedFace> = withContext(Dispatchers.IO) {
        val url = "$endpoint/face/v1.0/detect?returnFaceId=true&returnFaceLandmarks=true"
        
        // Resize bitmap if necessary to keep under 6MB
        val resizedBitmap = resizeBitmapIfNeeded(bitmap)
        
        // Convert bitmap to byte array with compression
        val stream = ByteArrayOutputStream()
        var quality = 95
        resizedBitmap.compress(Bitmap.CompressFormat.JPEG, quality, stream)
        
        // If still too large, compress more
        while (stream.toByteArray().size > 5 * 1024 * 1024 && quality > 30) { // 5MB to be safe
            stream.reset()
            quality -= 10
            resizedBitmap.compress(Bitmap.CompressFormat.JPEG, quality, stream)
        }
        
        val byteArray = stream.toByteArray()
        Log.d(TAG, "Image size for Azure: ${byteArray.size / 1024}KB, quality: $quality")
        
        val requestBody = byteArray.toRequestBody("application/octet-stream".toMediaType())
        
        val request = Request.Builder()
            .url(url)
            .addHeader("Ocp-Apim-Subscription-Key", subscriptionKey)
            .addHeader("Content-Type", "application/octet-stream")
            .post(requestBody)
            .build()
        
        try {
            val response = client.newCall(request).execute()
            if (response.isSuccessful) {
                val responseBody = response.body?.string() ?: "[]"
                Log.d(TAG, "Face detection response: $responseBody")
                val faces = parseFaceDetectionResponse(responseBody)
                Log.d(TAG, "Detected ${faces.size} face(s)")
                faces
            } else {
                val errorBody = response.body?.string()
                Log.e(TAG, "Face detection failed: ${response.code} - $errorBody")
                emptyList()
            }
        } catch (e: IOException) {
            Log.e(TAG, "Network error during face detection", e)
            emptyList()
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected error during face detection", e)
            emptyList()
        }
    }
    
    /**
     * Identify a face against a person group
     */
    suspend fun identifyFace(faceId: String): FaceVerificationResult? = withContext(Dispatchers.IO) {
        val url = "$endpoint/face/v1.0/identify"
        
        val json = JSONObject().apply {
            put("personGroupId", PERSON_GROUP_ID)
            put("faceIds", JSONArray().put(faceId))
            put("maxNumOfCandidatesReturned", 1)
            put("confidenceThreshold", 0.7)
        }
        
        val requestBody = json.toString().toRequestBody("application/json".toMediaType())
        
        val request = Request.Builder()
            .url(url)
            .addHeader("Ocp-Apim-Subscription-Key", subscriptionKey)
            .addHeader("Content-Type", "application/json")
            .post(requestBody)
            .build()
        
        try {
            val response = client.newCall(request).execute()
            if (response.isSuccessful) {
                val responseBody = response.body?.string() ?: "[]"
                parseIdentifyResponse(responseBody)
            } else {
                Log.e(TAG, "Face identification failed: ${response.code}")
                null
            }
        } catch (e: IOException) {
            Log.e(TAG, "Network error during face identification", e)
            null
        }
    }
    
    /**
     * Create a new person in the person group
     */
    suspend fun createPerson(name: String, userData: String): String? = withContext(Dispatchers.IO) {
        val url = "$endpoint/face/v1.0/persongroups/$PERSON_GROUP_ID/persons"
        
        val json = JSONObject().apply {
            put("name", name)
            put("userData", userData)
        }
        
        val requestBody = json.toString().toRequestBody("application/json".toMediaType())
        
        val request = Request.Builder()
            .url(url)
            .addHeader("Ocp-Apim-Subscription-Key", subscriptionKey)
            .addHeader("Content-Type", "application/json")
            .post(requestBody)
            .build()
        
        try {
            val response = client.newCall(request).execute()
            if (response.isSuccessful) {
                val responseBody = response.body?.string() ?: "{}"
                val jsonResponse = JSONObject(responseBody)
                jsonResponse.getString("personId")
            } else {
                Log.e(TAG, "Create person failed: ${response.code}")
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error creating person", e)
            null
        }
    }
    
    /**
     * Add a face to a person
     */
    suspend fun addFaceToPerson(personId: String, bitmap: Bitmap): String? = withContext(Dispatchers.IO) {
        val url = "$endpoint/face/v1.0/persongroups/$PERSON_GROUP_ID/persons/$personId/persistedFaces"
        
        // Resize bitmap if necessary to keep under 6MB
        val resizedBitmap = resizeBitmapIfNeeded(bitmap)
        
        // Convert bitmap to byte array with compression
        val stream = ByteArrayOutputStream()
        var quality = 95
        resizedBitmap.compress(Bitmap.CompressFormat.JPEG, quality, stream)
        
        // If still too large, compress more
        while (stream.toByteArray().size > 5 * 1024 * 1024 && quality > 30) { // 5MB to be safe
            stream.reset()
            quality -= 10
            resizedBitmap.compress(Bitmap.CompressFormat.JPEG, quality, stream)
        }
        
        val byteArray = stream.toByteArray()
        
        val requestBody = byteArray.toRequestBody("application/octet-stream".toMediaType())
        
        val request = Request.Builder()
            .url(url)
            .addHeader("Ocp-Apim-Subscription-Key", subscriptionKey)
            .addHeader("Content-Type", "application/octet-stream")
            .post(requestBody)
            .build()
        
        try {
            val response = client.newCall(request).execute()
            if (response.isSuccessful) {
                val responseBody = response.body?.string() ?: "{}"
                val jsonResponse = JSONObject(responseBody)
                jsonResponse.getString("persistedFaceId")
            } else {
                Log.e(TAG, "Add face failed: ${response.code}")
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error adding face", e)
            null
        }
    }
    
    /**
     * Create person group if it doesn't exist
     */
    suspend fun createPersonGroupIfNeeded(): Boolean = withContext(Dispatchers.IO) {
        val url = "$endpoint/face/v1.0/persongroups/$PERSON_GROUP_ID"
        
        // First check if it exists
        val checkRequest = Request.Builder()
            .url(url)
            .addHeader("Ocp-Apim-Subscription-Key", subscriptionKey)
            .get()
            .build()
        
        try {
            val checkResponse = client.newCall(checkRequest).execute()
            if (checkResponse.code == 404) {
                // Person group doesn't exist, create it
                Log.d(TAG, "Creating person group: $PERSON_GROUP_ID")
                
                val json = JSONObject().apply {
                    put("name", "Employees")
                    put("userData", "Employee face recognition group")
                }
                
                val createRequest = Request.Builder()
                    .url(url)
                    .addHeader("Ocp-Apim-Subscription-Key", subscriptionKey)
                    .addHeader("Content-Type", "application/json")
                    .put(json.toString().toRequestBody("application/json".toMediaType()))
                    .build()
                
                val createResponse = client.newCall(createRequest).execute()
                if (createResponse.isSuccessful) {
                    Log.d(TAG, "Person group created successfully")
                    true
                } else {
                    Log.e(TAG, "Failed to create person group: ${createResponse.code}")
                    false
                }
            } else if (checkResponse.isSuccessful) {
                Log.d(TAG, "Person group already exists")
                true
            } else {
                Log.e(TAG, "Error checking person group: ${checkResponse.code}")
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error with person group", e)
            false
        }
    }
    
    /**
     * Train the person group
     */
    suspend fun trainPersonGroup(): Boolean = withContext(Dispatchers.IO) {
        val url = "$endpoint/face/v1.0/persongroups/$PERSON_GROUP_ID/train"
        
        val request = Request.Builder()
            .url(url)
            .addHeader("Ocp-Apim-Subscription-Key", subscriptionKey)
            .post("".toRequestBody(null))
            .build()
        
        try {
            val response = client.newCall(request).execute()
            response.isSuccessful
        } catch (e: Exception) {
            Log.e(TAG, "Error training person group", e)
            false
        }
    }
    
    private fun parseFaceDetectionResponse(json: String): List<DetectedFace> {
        val faces = mutableListOf<DetectedFace>()
        try {
            val jsonArray = JSONArray(json)
            for (i in 0 until jsonArray.length()) {
                val faceObj = jsonArray.getJSONObject(i)
                val rectObj = faceObj.getJSONObject("faceRectangle")
                
                faces.add(DetectedFace(
                    faceId = faceObj.getString("faceId"),
                    faceRectangle = FaceRectangle(
                        top = rectObj.getInt("top"),
                        left = rectObj.getInt("left"),
                        width = rectObj.getInt("width"),
                        height = rectObj.getInt("height")
                    )
                ))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing face detection response", e)
        }
        return faces
    }
    
    private fun parseIdentifyResponse(json: String): FaceVerificationResult? {
        try {
            val jsonArray = JSONArray(json)
            if (jsonArray.length() > 0) {
                val result = jsonArray.getJSONObject(0)
                val candidates = result.getJSONArray("candidates")
                
                if (candidates.length() > 0) {
                    val candidate = candidates.getJSONObject(0)
                    return FaceVerificationResult(
                        isIdentical = true,
                        confidence = candidate.getDouble("confidence"),
                        personId = candidate.getString("personId")
                    )
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing identify response", e)
        }
        return FaceVerificationResult(isIdentical = false, confidence = 0.0)
    }
    
    /**
     * Resize bitmap if it's too large
     */
    private fun resizeBitmapIfNeeded(bitmap: Bitmap): Bitmap {
        val maxDimension = 1920 // Max width or height
        
        if (bitmap.width <= maxDimension && bitmap.height <= maxDimension) {
            return bitmap
        }
        
        val ratio = if (bitmap.width > bitmap.height) {
            maxDimension.toFloat() / bitmap.width
        } else {
            maxDimension.toFloat() / bitmap.height
        }
        
        val newWidth = (bitmap.width * ratio).toInt()
        val newHeight = (bitmap.height * ratio).toInt()
        
        return bitmap.scale(newWidth, newHeight)
    }
}