package com.mandelbulb.smartattendancesystem.network

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import com.mandelbulb.smartattendancesystem.data.UserPreferences
import com.mandelbulb.smartattendancesystem.ml.LivenessDetectionService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

class FaceRecognitionManager(private val context: Context) {
    companion object {
        private const val TAG = "FaceRecognitionManager"
    }

    private val azureFaceService = AzureFaceService(
        subscriptionKey = AzureFaceService.SUBSCRIPTION_KEY,
        endpoint = AzureFaceService.AZURE_ENDPOINT
    )
    private val offlineFaceService = OfflineFaceNetService(context)
    private val userPreferences = UserPreferences(context)
    private val networkMonitor = NetworkMonitor(context)
    private val livenessDetectionService = LivenessDetectionService(context)

    data class EnrollmentResult(
        val success: Boolean,
        val embedding: FloatArray? = null,
        val azureFaceId: String? = null,
        val message: String = ""
    )

    data class VerificationResult(
        val isIdentical: Boolean,
        val confidence: Float = 0f,
        val message: String = "",
        val isLive: Boolean = true,
        val livenessConfidence: Float = 0f
    )

    suspend fun enrollFaceWithEmbedding(
        bitmap: Bitmap,
        personId: String,
        personName: String,
        requireLiveness: Boolean = true
    ): EnrollmentResult {
        return withContext(Dispatchers.IO) {
            try {
                // Perform liveness detection first if required
                if (requireLiveness) {
                    val livenessResult = livenessDetectionService.detectLiveness(bitmap)
                    if (!livenessResult.isLive) {
                        Log.w(TAG, "Liveness check failed during enrollment: ${livenessResult.message}")
                        return@withContext EnrollmentResult(
                            false,
                            null,
                            null,
                            "Liveness check failed: ${livenessResult.message}"
                        )
                    }
                    Log.d(TAG, "Liveness check passed with confidence: ${livenessResult.confidence}")
                }

                val isOnline = networkMonitor.isOnline()

                Log.d(TAG, "Enrolling face - Online: $isOnline, Person: $personName, ID: $personId")

                val result = if (isOnline) {
                    // Try online mode with Azure (includes automatic fallback to offline)
                    enrollFaceOnlineWithEmbedding(bitmap, personId, personName)
                } else {
                    // Offline mode with FaceNet only
                    enrollFaceOfflineWithEmbedding(bitmap, personId, personName)
                }

                // Ensure we always have at least offline enrollment
                if (!result.success && !offlineFaceService.hasEmbedding(personId)) {
                    Log.w(TAG, "Primary enrollment failed, ensuring offline enrollment exists")
                    val offlineResult = enrollFaceOfflineWithEmbedding(bitmap, personId, personName)
                    if (offlineResult.success) {
                        return@withContext EnrollmentResult(
                            true,
                            offlineResult.embedding,
                            null,
                            "Face enrolled offline successfully"
                        )
                    }
                }

                result
            } catch (e: Exception) {
                Log.e(TAG, "Face enrollment failed", e)
                // Last resort: try offline enrollment
                try {
                    val offlineResult = enrollFaceOfflineWithEmbedding(bitmap, personId, personName)
                    if (offlineResult.success) {
                        return@withContext EnrollmentResult(
                            true,
                            offlineResult.embedding,
                            null,
                            "Face enrolled offline (fallback)"
                        )
                    }
                } catch (offlineError: Exception) {
                    Log.e(TAG, "Offline enrollment also failed", offlineError)
                }
                EnrollmentResult(false, null, null, "Enrollment failed: ${e.message}")
            }
        }
    }

    private suspend fun enrollFaceOnlineWithEmbedding(
        bitmap: Bitmap,
        personId: String,
        personName: String
    ): EnrollmentResult {
        return try {
            // First ensure person group exists
            azureFaceService.createPersonGroupIfNeeded()

            // Create person in Azure first
            val azurePersonId = azureFaceService.createPerson(
                name = personName,
                userData = personId // Store our ID as user data
            )

            if (azurePersonId != null) {
                // Add face to the person
                val azureFaceId = azureFaceService.addFaceToPerson(azurePersonId, bitmap)

                if (azureFaceId != null) {
                    // Train the person group
                    azureFaceService.trainPersonGroup()

                    // Also enroll offline for cross-mode compatibility
                    val offlineResult = enrollFaceOfflineWithEmbedding(bitmap, personId, personName)

                    if (!offlineResult.success) {
                        Log.w(TAG, "Online enrollment succeeded but offline enrollment failed")
                    }

                    Log.d(TAG, "✓ Face enrolled online with Azure (Person ID: $personId, Azure ID: $azurePersonId, Face ID: $azureFaceId)")
                    EnrollmentResult(true, offlineResult.embedding, azureFaceId, "Face enrolled successfully online")
                } else {
                    Log.e(TAG, "Azure face add failed, falling back to offline")
                    // Fallback to offline enrollment
                    enrollFaceOfflineWithEmbedding(bitmap, personId, personName)
                }
            } else {
                Log.e(TAG, "Azure person creation failed, falling back to offline")
                // Fallback to offline enrollment
                enrollFaceOfflineWithEmbedding(bitmap, personId, personName)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Online enrollment error, falling back to offline", e)
            // Fallback to offline enrollment
            enrollFaceOfflineWithEmbedding(bitmap, personId, personName)
        }
    }

    private suspend fun enrollFaceOfflineWithEmbedding(
        bitmap: Bitmap,
        personId: String,
        personName: String
    ): EnrollmentResult {
        val result = offlineFaceService.enrollFace(bitmap, personId, personName)

        return if (result.success) {
            Log.d(TAG, "✓ Face enrolled offline with FaceNet (Person ID: $personId)")
            EnrollmentResult(true, result.embedding, null, "Face enrolled successfully offline")
        } else {
            Log.e(TAG, "Offline face enrollment failed: ${result.message}")
            EnrollmentResult(false, null, null, result.message)
        }
    }

    suspend fun verifyFace(bitmap: Bitmap, personId: String, requireLiveness: Boolean = true): VerificationResult {
        return withContext(Dispatchers.IO) {
            try {
                // Perform liveness detection first if required
                var livenessConfidence = 1.0f
                var isLive = true

                if (requireLiveness) {
                    val livenessResult = livenessDetectionService.detectLiveness(bitmap)
                    isLive = livenessResult.isLive
                    livenessConfidence = livenessResult.confidence

                    if (!isLive) {
                        Log.w(TAG, "Liveness check failed: ${livenessResult.message}")
                        return@withContext VerificationResult(
                            isIdentical = false,
                            confidence = 0f,
                            message = "Liveness check failed: ${livenessResult.message}",
                            isLive = false,
                            livenessConfidence = livenessConfidence
                        )
                    }
                    Log.d(TAG, "Liveness check passed with confidence: $livenessConfidence")
                }

                val isOnline = networkMonitor.isOnline()

                Log.d(TAG, "Verifying face - Online: $isOnline, Person ID: $personId")

                val result = if (isOnline) {
                    // Try online first, fallback to offline
                    verifyFaceOnline(bitmap, personId)
                } else {
                    // Offline verification
                    verifyFaceOffline(bitmap, personId)
                }

                // Add liveness info to result
                result.copy(
                    isLive = isLive,
                    livenessConfidence = livenessConfidence,
                    message = if (result.isIdentical && isLive) {
                        "Face verified successfully with liveness check"
                    } else result.message
                )
            } catch (e: Exception) {
                Log.e(TAG, "Face verification failed", e)
                VerificationResult(false, 0f, "Verification failed: ${e.message}", true, 0f)
            }
        }
    }

    private suspend fun verifyFaceOnline(bitmap: Bitmap, personId: String): VerificationResult {
        return try {
            // Detect face with Azure
            val detectedFaces = azureFaceService.detectFace(bitmap)

            if (detectedFaces.isEmpty()) {
                Log.e(TAG, "No face detected by Azure")
                // Try offline verification as fallback
                return verifyFaceOffline(bitmap, personId)
            }

            // Use the first detected face
            val faceId = detectedFaces.first().faceId

            // Identify face with Azure (Azure uses identify, not verify for person groups)
            val azureResult = azureFaceService.identifyFace(faceId)

            if (azureResult != null && azureResult.isIdentical) {
                // CRITICAL: Check if the identified person matches the expected personId
                // Azure returns the personId it found, we need to verify it matches our expected person
                val identifiedPersonData = azureResult.personId // This is the Azure personId

                // Since we store our employeeId as userData during enrollment,
                // we need to verify the face belongs to the correct person
                // For now, we'll rely on offline verification for accurate person matching
                Log.d(TAG, "Azure identified a person, but need to verify it's the right one. Using offline verification.")

                // Use offline verification to ensure it's the right person
                return verifyFaceOffline(bitmap, personId)
            } else {
                Log.w(TAG, "Azure verification failed or no match, trying offline")
                // Fallback to offline verification
                verifyFaceOffline(bitmap, personId)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Online verification error, falling back to offline", e)
            // Fallback to offline verification
            verifyFaceOffline(bitmap, personId)
        }
    }

    private suspend fun verifyFaceOffline(bitmap: Bitmap, personId: String): VerificationResult {
        val result = offlineFaceService.verifyFace(bitmap, personId)

        Log.d(TAG, "Offline verification result - Match: ${result.isIdentical}, Confidence: ${result.confidence}")

        return VerificationResult(
            isIdentical = result.isIdentical,
            confidence = result.confidence,
            message = result.message,
            isLive = true,
            livenessConfidence = 1.0f
        )
    }

    suspend fun detectFaces(bitmap: Bitmap): Int {
        return try {
            val faces = offlineFaceService.detectFaces(bitmap)
            faces.size
        } catch (e: Exception) {
            Log.e(TAG, "Face detection failed", e)
            0
        }
    }

    fun hasOfflineEmbedding(personId: String): Boolean {
        return offlineFaceService.hasEmbedding(personId)
    }

    data class IdentificationResult(
        val personId: String?,
        val confidence: Float,
        val message: String = ""
    )

    suspend fun identifyFace(bitmap: Bitmap): IdentificationResult? {
        return withContext(Dispatchers.IO) {
            try {
                Log.d(TAG, "Starting face identification")

                // Use offline service to identify the face
                val result = offlineFaceService.identifyFace(bitmap)

                if (result != null) {
                    Log.d(TAG, "Face identified as: ${result.personId} with confidence: ${result.confidence}")
                    IdentificationResult(
                        personId = result.personId,
                        confidence = result.confidence,
                        message = "Face identified successfully"
                    )
                } else {
                    Log.w(TAG, "No face could be identified")
                    null
                }
            } catch (e: Exception) {
                Log.e(TAG, "Face identification failed", e)
                null
            }
        }
    }

    suspend fun verifyFaceMultiFrame(frames: List<Bitmap>, personId: String, requireLiveness: Boolean = true): VerificationResult {
        return withContext(Dispatchers.IO) {
            try {
                if (frames.isEmpty()) {
                    return@withContext VerificationResult(
                        isIdentical = false,
                        confidence = 0f,
                        message = "No frames provided for verification",
                        isLive = false,
                        livenessConfidence = 0f
                    )
                }

                // Use multi-frame liveness detection for better accuracy
                if (requireLiveness && frames.size >= 3) {
                    val livenessResult = livenessDetectionService.detectLivenessMultiFrame(frames)

                    if (!livenessResult.isLive) {
                        Log.w(TAG, "Multi-frame liveness check failed: ${livenessResult.message}")
                        return@withContext VerificationResult(
                            isIdentical = false,
                            confidence = 0f,
                            message = livenessResult.message,
                            isLive = false,
                            livenessConfidence = livenessResult.confidence
                        )
                    }

                    Log.d(TAG, "Multi-frame liveness check passed with confidence: ${livenessResult.confidence}")

                    // Use the middle frame for face verification
                    val verificationFrame = frames[frames.size / 2]
                    val verificationResult = verifyFace(verificationFrame, personId, false) // Liveness already checked

                    return@withContext verificationResult.copy(
                        isLive = true,
                        livenessConfidence = livenessResult.confidence,
                        message = if (verificationResult.isIdentical) {
                            "Face verified successfully with enhanced liveness check"
                        } else verificationResult.message
                    )
                } else {
                    // Fall back to single frame verification
                    return@withContext verifyFace(frames.firstOrNull() ?: return@withContext VerificationResult(
                        false, 0f, "No valid frame for verification", false, 0f
                    ), personId, requireLiveness)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Multi-frame face verification failed", e)
                VerificationResult(false, 0f, "Verification failed: ${e.message}", false, 0f)
            }
        }
    }

    fun cleanup() {
        try {
            offlineFaceService.cleanup()
            livenessDetectionService.close()
        } catch (e: Exception) {
            Log.e(TAG, "Cleanup error", e)
        }
    }
}