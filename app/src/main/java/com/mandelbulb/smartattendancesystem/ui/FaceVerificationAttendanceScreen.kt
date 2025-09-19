package com.mandelbulb.smartattendancesystem.ui

import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.mandelbulb.smartattendancesystem.data.AppDatabase
import com.mandelbulb.smartattendancesystem.data.AttendanceEntity
import com.mandelbulb.smartattendancesystem.data.UserPreferences
import com.mandelbulb.smartattendancesystem.network.NetworkMonitor
import com.mandelbulb.smartattendancesystem.network.PostgresApiService
import com.mandelbulb.smartattendancesystem.network.FaceRecognitionManager
import com.mandelbulb.smartattendancesystem.ui.components.AnimatedCard
import com.mandelbulb.smartattendancesystem.ui.components.AnimatedButton
import com.mandelbulb.smartattendancesystem.ui.components.AnimatedCheckmark
import com.mandelbulb.smartattendancesystem.ui.components.calculateStaggeredDelay
import com.mandelbulb.smartattendancesystem.utils.NetworkUtils
import com.mandelbulb.smartattendancesystem.util.LocationService
import com.mandelbulb.smartattendancesystem.util.BitmapUtils
import android.graphics.BitmapFactory
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FaceVerificationAttendanceScreen(
    checkType: String, // "IN" or "OUT"
    onSuccess: () -> Unit,
    onCancel: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    
    val database = remember { AppDatabase.getInstance(context) }
    val userPreferences = remember { UserPreferences(context) }
    val postgresApi = remember { PostgresApiService() }
    val faceRecognitionManager = remember { FaceRecognitionManager(context) }
    val locationService = remember { LocationService(context) }
    
    var isProcessing by remember { mutableStateOf(false) }
    var statusMessage by remember { mutableStateOf("Position your face in the camera") }
    var showCamera by remember { mutableStateOf(true) }
    var livenessStatus by remember { mutableStateOf("") }
    var captureCount by remember { mutableStateOf(0) }
    val capturedFrames = remember { mutableListOf<android.graphics.Bitmap>() }
    
    val imageCapture = remember { ImageCapture.Builder().build() }
    val previewView = remember { PreviewView(context) }
    var animationsEnabled by remember { mutableStateOf(true) }

    fun captureMultipleFrames() {
        if (captureCount < 3) {
            val photoFile = File(context.cacheDir, "temp_face_${System.currentTimeMillis()}.jpg")
            val outputFileOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()

            imageCapture.takePicture(
                outputFileOptions,
                ContextCompat.getMainExecutor(context),
                object : ImageCapture.OnImageSavedCallback {
                    override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                        scope.launch {
                            try {
                                val bitmap = BitmapUtils.loadAndRotateBitmap(photoFile.absolutePath)
                                if (bitmap != null) {
                                    capturedFrames.add(bitmap)
                                    captureCount++
                                    statusMessage = "Capturing frame ${captureCount}/3..."

                                    if (captureCount < 3) {
                                        // Wait a bit and capture next frame
                                        delay(300)
                                        captureMultipleFrames()
                                    } else {
                                        // All frames captured, proceed with verification
                                        try {
                                            statusMessage = "Processing captured frames..."

                                            // Get employee ID from preferences
                                            val employeeId = userPreferences.employeeId.first()

                                            statusMessage = "Performing enhanced liveness detection..."
                                            livenessStatus = "Analyzing multiple frames for anti-spoofing..."

                                            // Use multi-frame verification for better liveness detection
                                            Log.d("FaceVerification", "Verifying with ${capturedFrames.size} frames")
                                            val verificationResult = faceRecognitionManager.verifyFaceMultiFrame(
                                                capturedFrames.toList(),
                                                employeeId,
                                                requireLiveness = true
                                            )
                                            Log.d("FaceVerification", "Multi-frame result - Match: ${verificationResult.isIdentical}, Confidence: ${verificationResult.confidence}, Liveness: ${verificationResult.isLive}")

                                            // Update liveness status
                                            livenessStatus = when {
                                                verificationResult.isLive && verificationResult.livenessConfidence >= 0.95f ->
                                                    "✓ Liveness verified - High confidence (${(verificationResult.livenessConfidence * 100).toInt()}%)"
                                                verificationResult.isLive ->
                                                    "✓ Liveness verified (${(verificationResult.livenessConfidence * 100).toInt()}%)"
                                                else ->
                                                    "❌ Liveness check failed - Possible spoofing detected"
                                            }

                                            if (verificationResult.isIdentical && verificationResult.isLive) {
                                                // Face and liveness verified successfully - record attendance
                                                statusMessage = "✓ Face and liveness verified! Recording attendance..."

                                                performAttendanceWithVerification(
                                                    context = context,
                                                    database = database,
                                                    postgresApi = postgresApi,
                                                    userPreferences = userPreferences,
                                                    checkType = checkType,
                                                    locationService = locationService,
                                                    onSuccess = {
                                                        statusMessage = "✓ ${if (checkType == "IN") "Checked in" else "Checked out"} successfully!"
                                                        showCamera = false
                                                        scope.launch {
                                                            delay(1500)
                                                            onSuccess()
                                                        }
                                                    },
                                                    onError = { error ->
                                                        statusMessage = error
                                                        isProcessing = false
                                                        scope.launch {
                                                            delay(3000)
                                                            statusMessage = "Try again - position your face in the camera"
                                                        }
                                                    }
                                                )
                                            } else if (!verificationResult.isLive) {
                                                // Liveness check failed - likely spoofing attempt
                                                statusMessage = "❌ ${verificationResult.message}"
                                                livenessStatus = "⚠️ Spoofing attempt blocked (Confidence: ${(verificationResult.livenessConfidence * 100).toInt()}%)"
                                                isProcessing = false

                                                // Reset for retry
                                                captureCount = 0
                                                capturedFrames.clear()

                                                // Show error for 4 seconds then reset
                                                scope.launch {
                                                    delay(4000)
                                                    statusMessage = "Please use your real face - no photos or videos allowed"
                                                    livenessStatus = ""
                                                }
                                            } else {
                                                // Face not recognized - deny attendance
                                                statusMessage = "❌ Face not recognized! Access denied."
                                                isProcessing = false

                                                // Reset for retry
                                                captureCount = 0
                                                capturedFrames.clear()

                                                // Show error for 3 seconds then reset
                                                scope.launch {
                                                    delay(3000)
                                                    statusMessage = "Try again - position your face in the camera"
                                                }
                                            }
                                        } catch (e: Exception) {
                                            Log.e("FaceVerification", "Verification error", e)
                                            statusMessage = "Verification failed: ${e.message}"
                                            isProcessing = false
                                            captureCount = 0
                                            capturedFrames.clear()
                                        }
                                    }
                                }
                            } finally {
                                photoFile.delete()
                            }
                        }
                    }

                    override fun onError(exception: ImageCaptureException) {
                        statusMessage = "Capture failed: ${exception.message}"
                        isProcessing = false
                    }
                }
            )
        }
    }

    fun verifyWithMultipleFrames() {
        scope.launch {
            try {
                statusMessage = "Processing captured frames..."

                // Get employee ID from preferences
                val employeeId = userPreferences.employeeId.first()

                statusMessage = "Performing enhanced liveness detection..."
                livenessStatus = "Analyzing multiple frames for anti-spoofing..."

                // Use multi-frame verification for better liveness detection
                Log.d("FaceVerification", "Verifying with ${capturedFrames.size} frames")
                val verificationResult = faceRecognitionManager.verifyFaceMultiFrame(
                    capturedFrames.toList(),
                    employeeId,
                    requireLiveness = true
                )
                Log.d("FaceVerification", "Multi-frame result - Match: ${verificationResult.isIdentical}, Confidence: ${verificationResult.confidence}, Liveness: ${verificationResult.isLive}")

                // Update liveness status
                livenessStatus = when {
                    verificationResult.isLive && verificationResult.livenessConfidence >= 0.95f ->
                        "✓ Liveness verified - High confidence (${(verificationResult.livenessConfidence * 100).toInt()}%)"
                    verificationResult.isLive ->
                        "✓ Liveness verified (${(verificationResult.livenessConfidence * 100).toInt()}%)"
                    else ->
                        "❌ Liveness check failed - Possible spoofing detected"
                }

                            if (verificationResult.isIdentical && verificationResult.isLive) {
                                // Face and liveness verified successfully - record attendance
                                statusMessage = "✓ Face and liveness verified! Recording attendance..."

                                performAttendanceWithVerification(
                                    context = context,
                                    database = database,
                                    postgresApi = postgresApi,
                                    userPreferences = userPreferences,
                                    checkType = checkType,
                                    locationService = locationService,
                                    onSuccess = {
                                        statusMessage = "✓ ${if (checkType == "IN") "Checked in" else "Checked out"} successfully!"
                                        showCamera = false
                                        scope.launch {
                                            delay(1500)
                                            onSuccess()
                                        }
                                    },
                                    onError = { error ->
                                        statusMessage = error
                                        isProcessing = false
                                        scope.launch {
                                            delay(3000)
                                            statusMessage = "Try again - position your face in the camera"
                                        }
                                    }
                                )
                } else if (!verificationResult.isLive) {
                    // Liveness check failed - likely spoofing attempt
                    statusMessage = "❌ ${verificationResult.message}"
                    livenessStatus = "⚠️ Spoofing attempt blocked (Confidence: ${(verificationResult.livenessConfidence * 100).toInt()}%)"
                    isProcessing = false

                    // Reset for retry
                    captureCount = 0
                    capturedFrames.clear()

                    // Show error for 4 seconds then reset
                    scope.launch {
                        delay(4000)
                        statusMessage = "Please use your real face - no photos or videos allowed"
                        livenessStatus = ""
                    }
                } else {
                    // Face not recognized - deny attendance
                    statusMessage = "❌ Face not recognized! Access denied."
                    isProcessing = false

                    // Reset for retry
                    captureCount = 0
                    capturedFrames.clear()

                    // Show error for 3 seconds then reset
                    scope.launch {
                        delay(3000)
                        statusMessage = "Try again - position your face in the camera"
                    }
                }
            } catch (e: Exception) {
                Log.e("FaceVerification", "Verification error", e)
                statusMessage = "Verification failed: ${e.message}"
                isProcessing = false
                captureCount = 0
                capturedFrames.clear()
            }
        }
    }

    fun captureAndVerifyFace() {
        isProcessing = true
        statusMessage = "Starting enhanced liveness detection..."
        captureCount = 0
        capturedFrames.clear()

        // Start capturing multiple frames
        captureMultipleFrames()
    }

    LaunchedEffect(Unit) {
        userPreferences.animationsEnabled.collect { enabled ->
            animationsEnabled = enabled
        }
    }

    LaunchedEffect(Unit) {
        val cameraProvider = ProcessCameraProvider.getInstance(context).get()
        val preview = Preview.Builder().build().also {
            it.surfaceProvider = previewView.surfaceProvider
        }

        try {
            cameraProvider.unbindAll()
            cameraProvider.bindToLifecycle(
                lifecycleOwner,
                CameraSelector.DEFAULT_FRONT_CAMERA,
                preview,
                imageCapture
            )
        } catch (e: Exception) {
            Toast.makeText(context, "Camera initialization failed", Toast.LENGTH_SHORT).show()
            onCancel()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Face Verification - Check ${checkType}") },
                navigationIcon = {
                    IconButton(onClick = onCancel) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            AnimatedCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                animationsEnabled = animationsEnabled,
                delayMillis = calculateStaggeredDelay(0)
            ) {
                if (showCamera) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        AndroidView(
                            factory = { previewView },
                            modifier = Modifier.fillMaxSize()
                        )
                        
                        // Overlay with face guide
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.SpaceBetween
                        ) {
                            Card(
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f)
                                ),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier.padding(12.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text(
                                        text = statusMessage,
                                        style = MaterialTheme.typography.bodyLarge,
                                        textAlign = TextAlign.Center
                                    )
                                    if (livenessStatus.isNotEmpty()) {
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Text(
                                            text = livenessStatus,
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = when {
                                                livenessStatus.contains("✓") -> MaterialTheme.colorScheme.primary
                                                livenessStatus.contains("❌") || livenessStatus.contains("⚠️") -> MaterialTheme.colorScheme.error
                                                else -> MaterialTheme.colorScheme.onSurface
                                            },
                                            textAlign = TextAlign.Center,
                                            fontWeight = androidx.compose.ui.text.font.FontWeight.Medium
                                        )
                                    }
                                    if (captureCount > 0) {
                                        Spacer(modifier = Modifier.height(4.dp))
                                        LinearProgressIndicator(
                                            progress = { captureCount / 3f },
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(4.dp),
                                        )
                                    }
                                }
                            }
                            
                            if (isProcessing) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(48.dp),
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                } else {
                    // Success state
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        AnimatedCheckmark(
                            isVisible = true,
                            modifier = Modifier,
                            animationsEnabled = animationsEnabled
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = statusMessage,
                            style = MaterialTheme.typography.headlineSmall,
                            textAlign = TextAlign.Center,
                            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                        )
                    }
                }
            }
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onCancel,
                    modifier = Modifier.weight(1f),
                    enabled = !isProcessing
                ) {
                    Text("Cancel")
                }
                
                AnimatedButton(
                    onClick = { captureAndVerifyFace() },
                    modifier = Modifier.weight(1f),
                    enabled = !isProcessing && showCamera,
                    animationsEnabled = animationsEnabled
                ) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Verify & Check ${checkType}")
                }
            }
        }
    }
}

private suspend fun performAttendanceWithVerification(
    context: android.content.Context,
    database: AppDatabase,
    postgresApi: PostgresApiService,
    userPreferences: UserPreferences,
    checkType: String,
    locationService: LocationService,
    onSuccess: () -> Unit,
    onError: (String) -> Unit
) {
    try {
        val employeeId = userPreferences.employeeId.first()
        val employeeCode = userPreferences.employeeCode.first()
        val deviceId = Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
        
        // Check network status
        val isOnline = NetworkUtils.isNetworkAvailable(context)
        val currentTimestamp = System.currentTimeMillis()

        // Get current location
        val location = withContext(Dispatchers.IO) {
            locationService.getCurrentLocation()
        }
        val locationString = location?.toJsonString()

        Log.d("Attendance", "Recording attendance - Mode: ${if (isOnline) "ONLINE" else "OFFLINE"}, Timestamp: $currentTimestamp, Date: ${java.util.Date(currentTimestamp)}, Location: $locationString")

        if (isOnline) {
            // ONLINE MODE: Try to sync immediately
            try {
                val success = withContext(Dispatchers.IO) {
                    postgresApi.recordAttendance(
                        employeeId = employeeId,
                        checkType = checkType,
                        deviceId = deviceId,
                        timestamp = currentTimestamp,
                        embedding = null // Face verification already done during login
                    )
                }

                if (success) {
                    // Save locally as synced
                    val attendance = AttendanceEntity(
                        employeeId = employeeId,
                        employeeCode = employeeCode,
                        checkType = checkType,
                        timestamp = currentTimestamp,
                        mode = "ONLINE",
                        location = locationString,
                        deviceId = deviceId,
                        synced = true,
                        syncedAt = currentTimestamp
                    )
                    database.attendanceDao().insert(attendance)
                    onSuccess()
                    Log.d("Attendance", "✓ Attendance recorded online successfully")
                } else {
                    throw Exception("Server returned failure")
                }
            } catch (e: Exception) {
                // Online sync failed, save offline
                Log.e("Attendance", "Online sync failed, saving offline: ${e.message}")
                saveOfflineAttendance(employeeId, employeeCode, checkType, currentTimestamp, deviceId, locationString, database)
                onSuccess() // Still show success to user
            }
        } else {
            // OFFLINE MODE: Save locally for later sync
            saveOfflineAttendance(employeeId, employeeCode, checkType, currentTimestamp, deviceId, locationString, database)
            onSuccess()
            Log.d("Attendance", "✓ Attendance saved offline, will sync when online")
        }
    } catch (e: Exception) {
        onError("Error: ${e.message}")
    }
}

private suspend fun saveOfflineAttendance(
    employeeId: String,
    employeeCode: String,
    checkType: String,
    timestamp: Long,
    deviceId: String,
    location: String?,
    database: AppDatabase
) {
    val attendance = AttendanceEntity(
        employeeId = employeeId,
        employeeCode = employeeCode,
        checkType = checkType,
        timestamp = timestamp,
        mode = "OFFLINE",
        location = location,
        deviceId = deviceId,
        synced = false,
        syncedAt = null
    )
    database.attendanceDao().insert(attendance)
}

// Azure Face API verification happens only during registration and login
// Check-in/out relies on Azure authentication from login session