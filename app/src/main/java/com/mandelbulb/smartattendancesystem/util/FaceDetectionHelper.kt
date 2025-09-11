package com.mandelbulb.smartattendancesystem.util

import android.graphics.Bitmap
import android.util.Log
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.Face
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

object FaceDetectionHelper {
    
    private const val TAG = "FaceDetectionHelper"
    
    // Try different detector configurations
    enum class DetectorMode {
        FAST_SIMPLE,
        ACCURATE_FULL,
        BALANCED
    }
    
    suspend fun detectFaces(
        bitmap: Bitmap,
        mode: DetectorMode = DetectorMode.BALANCED
    ): List<Face> = suspendCancellableCoroutine { continuation ->
        Log.d(TAG, "detectFaces called with mode: $mode, bitmap size: ${bitmap.width}x${bitmap.height}")
        
        val options = when (mode) {
            DetectorMode.FAST_SIMPLE -> {
                // Fast mode with minimal features
                FaceDetectorOptions.Builder()
                    .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
                    .setMinFaceSize(0.1f)
                    .build()
            }
            DetectorMode.ACCURATE_FULL -> {
                // Accurate mode with all features - don't use LANDMARK_MODE_ALL as it's causing issues
                FaceDetectorOptions.Builder()
                    .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE)
                    .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL)
                    .setMinFaceSize(0.15f)
                    .enableTracking()
                    .build()
            }
            DetectorMode.BALANCED -> {
                // Balanced mode - best for most cases
                FaceDetectorOptions.Builder()
                    .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE)
                    .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL)
                    .setMinFaceSize(0.1f)
                    .enableTracking()
                    .build()
            }
        }
        
        val detector = FaceDetection.getClient(options)
        val inputImage = InputImage.fromBitmap(bitmap, 0)
        
        Log.d(TAG, "Starting face detection with ML Kit...")
        
        detector.process(inputImage)
            .addOnSuccessListener { faces ->
                Log.d(TAG, "ML Kit SUCCESS: Detected ${faces.size} face(s) in mode $mode")
                
                faces.forEach { face ->
                    Log.d(TAG, "Face bounds: ${face.boundingBox}")
                    Log.d(TAG, "Tracking ID: ${face.trackingId}")
                    
                    face.leftEyeOpenProbability?.let {
                        Log.d(TAG, "Left eye open: ${(it * 100).toInt()}%")
                    }
                    face.rightEyeOpenProbability?.let {
                        Log.d(TAG, "Right eye open: ${(it * 100).toInt()}%")
                    }
                    face.smilingProbability?.let {
                        Log.d(TAG, "Smiling: ${(it * 100).toInt()}%")
                    }
                }
                
                detector.close()
                continuation.resume(faces)
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "ML Kit FAILURE: Face detection failed with error: ${e.message}", e)
                detector.close()
                continuation.resume(emptyList())
            }
            .addOnCompleteListener { task ->
                Log.d(TAG, "ML Kit detection task completed. Success: ${task.isSuccessful}")
            }
    }
    
    suspend fun detectWithFallback(bitmap: Bitmap): List<Face> {
        // Try balanced mode first
        var faces = detectFaces(bitmap, DetectorMode.BALANCED)
        
        // If no faces found, try fast mode
        if (faces.isEmpty()) {
            Log.d(TAG, "No faces with balanced mode, trying fast mode")
            faces = detectFaces(bitmap, DetectorMode.FAST_SIMPLE)
        }
        
        // If still no faces, try accurate mode
        if (faces.isEmpty()) {
            Log.d(TAG, "No faces with fast mode, trying accurate mode")
            faces = detectFaces(bitmap, DetectorMode.ACCURATE_FULL)
        }
        
        return faces
    }
    
    fun calculateLivenessScore(face: Face): Float {
        val leftEye = face.leftEyeOpenProbability ?: 0.5f
        val rightEye = face.rightEyeOpenProbability ?: 0.5f
        val smile = face.smilingProbability ?: 0.5f
        
        // Simple liveness score based on available data
        var score = 0.5f // Base score
        
        // Add points for having eye data
        if (face.leftEyeOpenProbability != null || face.rightEyeOpenProbability != null) {
            score += 0.2f
            
            // Natural eye state (not too closed, not too wide)
            val avgEye = (leftEye + rightEye) / 2f
            if (avgEye in 0.2f..0.9f) {
                score += 0.2f
            }
        }
        
        // Add points for having smile data
        if (face.smilingProbability != null) {
            score += 0.1f
        }
        
        // Add points for having tracking ID (means face is being tracked)
        if (face.trackingId != null) {
            score += 0.1f
        }
        
        // Add points for reasonable face size
        val faceWidth = face.boundingBox.width()
        val faceHeight = face.boundingBox.height()
        if (faceWidth > 100 && faceHeight > 100) {
            score += 0.1f
        }
        
        return score.coerceIn(0f, 1f)
    }
}