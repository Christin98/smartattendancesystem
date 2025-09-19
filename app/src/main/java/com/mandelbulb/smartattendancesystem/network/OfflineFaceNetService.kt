package com.mandelbulb.smartattendancesystem.network

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import com.mandelbulb.smartattendancesystem.ml.FaceNetModel
import com.mandelbulb.smartattendancesystem.util.BitmapUtils
import android.graphics.Matrix
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.tasks.await
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.ObjectInputStream
import java.io.ObjectOutputStream

class OfflineFaceNetService(private val context: Context) {
    companion object {
        private const val TAG = "OfflineFaceNetService"
        private const val EMBEDDINGS_DIR = "face_embeddings"
        private const val FACE_SIZE = 160
    }

    private val faceNetModel = FaceNetModel(context)

    private val faceDetector by lazy {
        val options = FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE) // Better accuracy
            .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_NONE)
            .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_NONE)
            .setMinFaceSize(0.1f) // Lower threshold for better detection
            .enableTracking() // Enable face tracking for better detection
            .build()
        FaceDetection.getClient(options)
    }

    private val embeddingsDir by lazy {
        File(context.filesDir, EMBEDDINGS_DIR).apply {
            if (!exists()) mkdirs()
        }
    }

    suspend fun enrollFace(bitmap: Bitmap, personId: String, personName: String): EnrollmentResult {
        return withContext(Dispatchers.IO) {
            try {
                Log.d(TAG, "Starting face enrollment for: $personName ($personId)")

                // Detect face (will try both orientations)
                val faces = detectFacesSimple(bitmap)
                if (faces.isEmpty()) {
                    Log.e(TAG, "No face detected during enrollment")
                    return@withContext EnrollmentResult(false, null, "No face detected")
                }

                // Get the largest face
                val face = faces.maxByOrNull { it.width * it.height }!!

                // Determine which rotation was successful
                val rotationUsed = findSuccessfulRotation(bitmap, faces)
                val processedBitmap = if (rotationUsed == 0f) bitmap else rotateBitmap(bitmap, rotationUsed)

                // Crop face from image
                val faceBitmap = cropFace(processedBitmap, face)

                // Generate embedding
                val embedding = faceNetModel.generateEmbedding(faceBitmap)
                if (embedding == null) {
                    Log.e(TAG, "Failed to generate embedding")
                    return@withContext EnrollmentResult(false, null, "Failed to process face")
                }

                // Save embedding to file
                saveEmbedding(personId, embedding)

                Log.d(TAG, "✓ Successfully enrolled face for $personName")
                EnrollmentResult(true, embedding, "Face enrolled successfully")

            } catch (e: Exception) {
                Log.e(TAG, "Face enrollment failed", e)
                EnrollmentResult(false, null, "Enrollment failed: ${e.message}")
            }
        }
    }

    suspend fun verifyFace(bitmap: Bitmap, personId: String): VerificationResult {
        return withContext(Dispatchers.IO) {
            try {
                Log.d(TAG, "Starting face verification for person: $personId")

                // Detect face (will try both orientations)
                val faces = detectFacesSimple(bitmap)
                if (faces.isEmpty()) {
                    Log.e(TAG, "No face detected during verification")
                    return@withContext VerificationResult(
                        isIdentical = false,
                        confidence = 0f,
                        message = "No face detected"
                    )
                }

                // Get the largest face
                val face = faces.maxByOrNull { it.width * it.height }!!

                // Determine which rotation was successful
                val rotationUsed = findSuccessfulRotation(bitmap, faces)
                val processedBitmap = if (rotationUsed == 0f) bitmap else rotateBitmap(bitmap, rotationUsed)

                // Crop face from image
                val faceBitmap = cropFace(processedBitmap, face)

                // Generate embedding for current face
                val currentEmbedding = faceNetModel.generateEmbedding(faceBitmap)
                if (currentEmbedding == null) {
                    Log.e(TAG, "Failed to generate embedding for verification")
                    return@withContext VerificationResult(
                        isIdentical = false,
                        confidence = 0f,
                        message = "Failed to process face"
                    )
                }

                // Load stored embedding
                val storedEmbedding = loadEmbedding(personId)
                if (storedEmbedding == null) {
                    Log.e(TAG, "No embedding found for person: $personId")
                    return@withContext VerificationResult(
                        isIdentical = false,
                        confidence = 0f,
                        message = "Person not enrolled"
                    )
                }

                // Compare embeddings using both similarity and L2 distance
                val similarity = faceNetModel.calculateSimilarity(currentEmbedding, storedEmbedding)
                val l2Distance = faceNetModel.calculateL2Distance(currentEmbedding, storedEmbedding)
                val isMatch = faceNetModel.compareEmbeddings(currentEmbedding, storedEmbedding)

                // CRITICAL SECURITY CHECK: Ensure this face best matches the expected person
                // Check against all stored embeddings to ensure this is the closest match
                val allEmbeddings = getAllStoredEmbeddings()
                if (allEmbeddings.size > 1) { // Only check if there are multiple people enrolled
                    var bestMatchPersonId: String? = null
                    var bestSimilarity = 0f

                    allEmbeddings.forEach { (storedPersonId, embedding) ->
                        val testSimilarity = faceNetModel.calculateSimilarity(currentEmbedding, embedding)
                        if (testSimilarity > bestSimilarity) {
                            bestSimilarity = testSimilarity
                            bestMatchPersonId = storedPersonId
                        }
                    }

                    // If the best match is NOT the expected person, reject
                    if (bestMatchPersonId != personId) {
                        Log.w(TAG, "Face best matches person $bestMatchPersonId, not $personId. Rejecting.")
                        return@withContext VerificationResult(
                            isIdentical = false,
                            confidence = similarity,
                            message = "Face does not match this user"
                        )
                    }
                }

                Log.d(TAG, "Face verification result - Similarity: $similarity, L2 Distance: $l2Distance, Match: $isMatch")

                VerificationResult(
                    isIdentical = isMatch,
                    confidence = similarity,
                    message = if (isMatch) "Face verified (Similarity: ${String.format("%.2f", similarity)})" else "Face not recognized"
                )

            } catch (e: Exception) {
                Log.e(TAG, "Face verification failed", e)
                VerificationResult(
                    isIdentical = false,
                    confidence = 0f,
                    message = "Verification failed: ${e.message}"
                )
            }
        }
    }

    suspend fun detectFaces(bitmap: Bitmap): List<FaceRect> {
        return detectFacesSimple(bitmap)
    }

    // New simplified face detection that tries both orientations
    private suspend fun detectFacesSimple(bitmap: Bitmap): List<FaceRect> {
        try {
            // Try multiple orientations for better detection
            val orientations = listOf(0f, 90f, 180f, 270f)

            for (rotation in orientations) {
                val testBitmap = if (rotation == 0f) bitmap else rotateBitmap(bitmap, rotation)
                val faces = detectFacesInBitmap(testBitmap)

                if (faces.isNotEmpty()) {
                    Log.d(TAG, "Detected ${faces.size} face(s) at $rotation° rotation")
                    return faces
                }
            }

            // No faces found in any orientation
            Log.w(TAG, "No faces detected in any orientation (tried ${orientations.size} rotations)")
            return emptyList()
        } catch (e: Exception) {
            Log.e(TAG, "Face detection failed", e)
            return emptyList()
        }
    }

    // Helper function to find which rotation was successful
    private suspend fun findSuccessfulRotation(bitmap: Bitmap, detectedFaces: List<FaceRect>): Float {
        val orientations = listOf(0f, 90f, 180f, 270f)

        for (rotation in orientations) {
            val testBitmap = if (rotation == 0f) bitmap else rotateBitmap(bitmap, rotation)
            val faces = detectFacesInBitmap(testBitmap)

            // Check if the detected faces match
            if (faces.size == detectedFaces.size && faces.isNotEmpty()) {
                // Simple comparison - could be enhanced
                if (faces.first().width == detectedFaces.first().width) {
                    return rotation
                }
            }
        }

        // Default to no rotation if uncertain
        return 0f
    }

    // Helper to detect faces in a single bitmap without rotation
    private suspend fun detectFacesInBitmap(bitmap: Bitmap): List<FaceRect> {
        return try {
            val image = InputImage.fromBitmap(bitmap, 0)
            val detectedFaces = faceDetector.process(image).await()

            detectedFaces.map { face ->
                FaceRect(
                    left = face.boundingBox.left,
                    top = face.boundingBox.top,
                    width = face.boundingBox.width(),
                    height = face.boundingBox.height()
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error detecting faces in bitmap", e)
            emptyList()
        }
    }

    private fun rotateBitmap(bitmap: Bitmap, degrees: Float): Bitmap {
        return if (degrees != 0f) {
            val matrix = Matrix().apply {
                postRotate(degrees)
            }
            Bitmap.createBitmap(
                bitmap,
                0,
                0,
                bitmap.width,
                bitmap.height,
                matrix,
                true
            )
        } else {
            bitmap
        }
    }

    private fun cropFace(bitmap: Bitmap, face: FaceRect): Bitmap {
        // Add some padding around the face for better context
        val paddingRatio = 0.3f // Increased padding for better face context
        val padding = (face.width * paddingRatio).toInt()

        val left = (face.left - padding).coerceAtLeast(0)
        val top = (face.top - padding).coerceAtLeast(0)
        val right = (face.left + face.width + padding).coerceAtMost(bitmap.width)
        val bottom = (face.top + face.height + padding).coerceAtMost(bitmap.height)

        val width = right - left
        val height = bottom - top

        // Create cropped bitmap
        val croppedBitmap = Bitmap.createBitmap(bitmap, left, top, width, height)

        // Resize to square aspect ratio for FaceNet
        val size = maxOf(width, height)
        val squareBitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(squareBitmap)
        val paint = android.graphics.Paint().apply {
            isFilterBitmap = true
            isAntiAlias = true
        }

        // Center the face in the square bitmap
        val xOffset = (size - width) / 2f
        val yOffset = (size - height) / 2f
        canvas.drawBitmap(croppedBitmap, xOffset, yOffset, paint)

        return squareBitmap
    }

    private fun saveEmbedding(personId: String, embedding: FloatArray) {
        try {
            val file = File(embeddingsDir, "$personId.face")
            ObjectOutputStream(FileOutputStream(file)).use { out ->
                out.writeObject(embedding)
            }
            Log.d(TAG, "Saved embedding to: ${file.absolutePath}")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save embedding", e)
            throw e
        }
    }

    private fun loadEmbedding(personId: String): FloatArray? {
        return try {
            val file = File(embeddingsDir, "$personId.face")
            if (!file.exists()) {
                Log.w(TAG, "Embedding file not found: ${file.absolutePath}")
                return null
            }

            ObjectInputStream(FileInputStream(file)).use { input ->
                input.readObject() as FloatArray
            }.also {
                Log.d(TAG, "Loaded embedding from: ${file.absolutePath}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load embedding", e)
            null
        }
    }

    fun deleteEmbedding(personId: String): Boolean {
        return try {
            val file = File(embeddingsDir, "$personId.face")
            if (file.exists()) {
                file.delete()
            } else {
                true
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to delete embedding", e)
            false
        }
    }

    fun hasEmbedding(personId: String): Boolean {
        val file = File(embeddingsDir, "$personId.face")
        return file.exists()
    }

    fun getAllStoredEmbeddings(): Map<String, FloatArray> {
        val embeddings = mutableMapOf<String, FloatArray>()
        try {
            embeddingsDir.listFiles()?.forEach { file ->
                if (file.extension == "face") {
                    val personId = file.nameWithoutExtension
                    loadEmbedding(personId)?.let { embedding ->
                        embeddings[personId] = embedding
                    }
                }
            }
            Log.d(TAG, "Loaded ${embeddings.size} stored embeddings")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load all embeddings", e)
        }
        return embeddings
    }

    suspend fun findBestMatch(bitmap: Bitmap): Pair<String, Float>? {
        return withContext(Dispatchers.IO) {
            try {
                // Detect face in the image
                val faces = detectFacesSimple(bitmap)
                if (faces.isEmpty()) {
                    Log.e(TAG, "No face detected for matching")
                    return@withContext null
                }

                // Get the largest face
                val face = faces.maxByOrNull { it.width * it.height }!!

                // Determine which rotation was successful
                val rotationUsed = findSuccessfulRotation(bitmap, faces)
                val processedBitmap = if (rotationUsed == 0f) bitmap else rotateBitmap(bitmap, rotationUsed)

                // Crop and generate embedding
                val faceBitmap = cropFace(processedBitmap, face)
                val queryEmbedding = faceNetModel.generateEmbedding(faceBitmap) ?: return@withContext null

                // Find best match among all stored embeddings
                val allEmbeddings = getAllStoredEmbeddings()
                var bestMatch: Pair<String, Float>? = null
                var highestSimilarity = 0f

                allEmbeddings.forEach { (personId, storedEmbedding) ->
                    val similarity = faceNetModel.calculateSimilarity(queryEmbedding, storedEmbedding)
                    if (similarity > highestSimilarity) {
                        highestSimilarity = similarity
                        bestMatch = Pair(personId, similarity)
                    }
                }

                Log.d(TAG, "Best match: ${bestMatch?.first} with similarity: ${bestMatch?.second}")
                bestMatch
            } catch (e: Exception) {
                Log.e(TAG, "Failed to find best match", e)
                null
            }
        }
    }

    fun cleanup() {
        try {
            faceDetector.close()
            faceNetModel.close()
        } catch (e: Exception) {
            Log.e(TAG, "Cleanup error", e)
        }
    }

    data class FaceRect(
        val left: Int,
        val top: Int,
        val width: Int,
        val height: Int
    )

    data class EnrollmentResult(
        val success: Boolean,
        val embedding: FloatArray?,
        val message: String = ""
    )

    data class VerificationResult(
        val isIdentical: Boolean,
        val confidence: Float,
        val message: String = ""
    )
}