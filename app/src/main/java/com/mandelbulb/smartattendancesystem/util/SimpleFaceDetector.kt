package com.mandelbulb.smartattendancesystem.util

import android.graphics.Bitmap
import android.util.Log
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.Face
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

class SimpleFaceDetector(private val context: android.content.Context) {
    private val TAG = "SimpleFaceDetector"
    
    /**
     * Detect face with callback for non-suspend usage
     */
    fun detectFace(bitmap: Bitmap, callback: (List<Face>) -> Unit) {
        val options = FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
            .build()
            
        val detector = FaceDetection.getClient(options)
        val image = InputImage.fromBitmap(bitmap, 0)
        
        detector.process(image)
            .addOnSuccessListener { faces ->
                detector.close()
                callback(faces)
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Detection failed: ${e.message}", e)
                detector.close()
                callback(emptyList())
            }
    }
    
    /**
     * Simplest possible face detection - just find faces, no features
     */
    suspend fun detectFaceSuspend(bitmap: Bitmap): Face? = suspendCancellableCoroutine { continuation ->
        Log.d(TAG, "Starting simple face detection...")
        
        // Simplest possible options - just detect faces
        val options = FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
            .build()
            
        val detector = FaceDetection.getClient(options)
        val image = InputImage.fromBitmap(bitmap, 0)
        
        Log.d(TAG, "Processing image of size ${bitmap.width}x${bitmap.height}")
        
        detector.process(image)
            .addOnSuccessListener { faces ->
                Log.d(TAG, "Detection successful: found ${faces.size} faces")
                detector.close()
                
                if (faces.isNotEmpty()) {
                    val face = faces[0]
                    Log.d(TAG, "First face bounds: ${face.boundingBox}")
                    continuation.resume(face)
                } else {
                    Log.d(TAG, "No faces found")
                    continuation.resume(null)
                }
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Detection failed: ${e.message}", e)
                detector.close()
                continuation.resume(null)
            }
    }
    
    /**
     * Try multiple times with different settings
     */
    suspend fun detectWithRetry(bitmap: Bitmap): Face? {
        Log.d(TAG, "Attempting detection with retry...")
        
        // Try simple detection first
        var face = detectFaceSuspend(bitmap)
        if (face != null) return face
        
        // Try with classification enabled
        Log.d(TAG, "Simple detection failed, trying with classification...")
        face = detectWithClassification(bitmap)
        if (face != null) return face
        
        // Try with smaller min face size
        Log.d(TAG, "Classification detection failed, trying with smaller face size...")
        face = detectWithSmallFaceSize(bitmap)
        
        return face
    }
    
    private suspend fun detectWithClassification(bitmap: Bitmap): Face? = 
        suspendCancellableCoroutine { continuation ->
            val options = FaceDetectorOptions.Builder()
                .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL)
                .build()
                
            val detector = FaceDetection.getClient(options)
            val image = InputImage.fromBitmap(bitmap, 0)
            
            detector.process(image)
                .addOnSuccessListener { faces ->
                    detector.close()
                    continuation.resume(faces.firstOrNull())
                }
                .addOnFailureListener {
                    detector.close()
                    continuation.resume(null)
                }
        }
    
    private suspend fun detectWithSmallFaceSize(bitmap: Bitmap): Face? = 
        suspendCancellableCoroutine { continuation ->
            val options = FaceDetectorOptions.Builder()
                .setMinFaceSize(0.05f) // Very small face size
                .build()
                
            val detector = FaceDetection.getClient(options)
            val image = InputImage.fromBitmap(bitmap, 0)
            
            detector.process(image)
                .addOnSuccessListener { faces ->
                    detector.close()
                    continuation.resume(faces.firstOrNull())
                }
                .addOnFailureListener {
                    detector.close()
                    continuation.resume(null)
                }
        }
}