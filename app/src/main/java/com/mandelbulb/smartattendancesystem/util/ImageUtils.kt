package com.mandelbulb.smartattendancesystem.util

import android.graphics.Bitmap
import android.graphics.Matrix
import androidx.exifinterface.media.ExifInterface
import android.util.Log
import java.io.File

object ImageUtils {
    private const val TAG = "ImageUtils"
    
    /**
     * Rotate bitmap based on EXIF orientation
     */
    fun rotateBitmapIfNeeded(bitmap: Bitmap, imagePath: String): Bitmap {
        return try {
            val exif = ExifInterface(imagePath)
            val orientation = exif.getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL
            )
            
            val rotationDegrees = when (orientation) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90
                ExifInterface.ORIENTATION_ROTATE_180 -> 180
                ExifInterface.ORIENTATION_ROTATE_270 -> 270
                else -> 0
            }
            
            Log.d(TAG, "Image orientation: $orientation, rotation needed: $rotationDegrees degrees")
            
            if (rotationDegrees != 0) {
                val matrix = Matrix()
                matrix.postRotate(rotationDegrees.toFloat())
                val rotated = Bitmap.createBitmap(
                    bitmap,
                    0,
                    0,
                    bitmap.width,
                    bitmap.height,
                    matrix,
                    true
                )
                Log.d(TAG, "Rotated image from ${bitmap.width}x${bitmap.height} to ${rotated.width}x${rotated.height}")
                rotated
            } else {
                bitmap
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to read EXIF orientation", e)
            bitmap
        }
    }
    
    /**
     * Rotate bitmap for front camera (mirror and rotate)
     */
    fun rotateBitmapForFrontCamera(bitmap: Bitmap, rotationDegrees: Int = 270): Bitmap {
        val matrix = Matrix()
        // Rotate 270 degrees for front camera (typical for Android)
        matrix.postRotate(rotationDegrees.toFloat())
        // Mirror horizontally for front camera
        matrix.postScale(-1f, 1f)
        
        val rotated = Bitmap.createBitmap(
            bitmap,
            0,
            0,
            bitmap.width,
            bitmap.height,
            matrix,
            true
        )
        
        Log.d(TAG, "Front camera rotation applied: ${rotationDegrees}° and mirrored. Size: ${rotated.width}x${rotated.height}")
        return rotated
    }
    
    /**
     * Try different rotations to find faces
     */
    suspend fun tryRotationsForFaceDetection(
        bitmap: Bitmap,
        imagePath: String,
        context: android.content.Context
    ): Pair<Bitmap, com.google.mlkit.vision.face.Face?> {
        Log.d(TAG, "Trying different rotations for face detection...")
        
        val detector = SimpleFaceDetector(context)
        
        // First try with EXIF orientation
        var rotatedBitmap = rotateBitmapIfNeeded(bitmap, imagePath)
        var face = detector.detectFaceSuspend(rotatedBitmap)
        if (face != null) {
            Log.d(TAG, "Face found with EXIF rotation")
            return Pair(rotatedBitmap, face)
        }
        
        // Try common rotations
        val rotations = listOf(0, 90, 180, 270)
        for (rotation in rotations) {
            Log.d(TAG, "Trying rotation: $rotation degrees")
            val matrix = Matrix()
            matrix.postRotate(rotation.toFloat())
            rotatedBitmap = Bitmap.createBitmap(
                bitmap,
                0,
                0,
                bitmap.width,
                bitmap.height,
                matrix,
                true
            )
            
            face = detector.detectFaceSuspend(rotatedBitmap)
            if (face != null) {
                Log.d(TAG, "Face found with rotation: $rotation degrees")
                return Pair(rotatedBitmap, face)
            }
        }
        
        // Try front camera specific rotation
        Log.d(TAG, "Trying front camera rotation (270° + mirror)")
        rotatedBitmap = rotateBitmapForFrontCamera(bitmap)
        face = detector.detectFaceSuspend(rotatedBitmap)
        if (face != null) {
            Log.d(TAG, "Face found with front camera rotation")
            return Pair(rotatedBitmap, face)
        }
        
        Log.d(TAG, "No face found after trying all rotations")
        return Pair(bitmap, null)
    }
}