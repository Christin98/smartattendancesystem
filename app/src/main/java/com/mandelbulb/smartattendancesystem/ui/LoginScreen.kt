package com.mandelbulb.smartattendancesystem.ui

import android.util.Log
import android.widget.Toast
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.*
import com.mandelbulb.smartattendancesystem.ui.components.AppIcons
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.mandelbulb.smartattendancesystem.data.AppDatabase
import com.mandelbulb.smartattendancesystem.data.UserPreferences
import com.mandelbulb.smartattendancesystem.network.AzureFaceService
import com.mandelbulb.smartattendancesystem.network.PostgresApiService
import com.mandelbulb.smartattendancesystem.network.FaceRecognitionManager
import com.mandelbulb.smartattendancesystem.ui.components.AnimatedCard
import com.mandelbulb.smartattendancesystem.ui.components.AnimatedButton
import com.mandelbulb.smartattendancesystem.ui.components.AnimatedCheckmark
import com.mandelbulb.smartattendancesystem.ui.components.calculateStaggeredDelay
import com.mandelbulb.smartattendancesystem.util.BitmapUtils
import com.mandelbulb.smartattendancesystem.util.SimpleFaceDetector
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@Composable
fun LoginScreen(
    onLoginSuccess: () -> Unit,
    onNavigateToRegistration: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    
    var isProcessing by remember { mutableStateOf(false) }
    var statusMessage by remember { mutableStateOf("Position your face in the camera") }
    var showCamera by remember { mutableStateOf(true) }
    var livenessMessage by remember { mutableStateOf("") }
    
    val imageCapture = remember { ImageCapture.Builder().build() }
    val previewView = remember { PreviewView(context) }
    
    val faceDetector = remember { SimpleFaceDetector(context) }
    val userPreferences = remember { UserPreferences(context) }
    val database = remember { AppDatabase.getInstance(context) }
    val postgresApi = remember { PostgresApiService() }
    val azureService = remember {
        AzureFaceService(
            AzureFaceService.SUBSCRIPTION_KEY,
            AzureFaceService.AZURE_ENDPOINT
        )
    }

    var animationsEnabled by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        userPreferences.animationsEnabled.collect { enabled ->
            animationsEnabled = enabled
        }
    }
    
    LaunchedEffect(Unit) {
        try {
            val cameraProvider = ProcessCameraProvider.getInstance(context).get()
            val preview = Preview.Builder().build().also {
                it.surfaceProvider = previewView.surfaceProvider
            }
            
            cameraProvider.unbindAll()
            cameraProvider.bindToLifecycle(
                lifecycleOwner,
                CameraSelector.DEFAULT_FRONT_CAMERA,
                preview,
                imageCapture
            )
        } catch (e: Exception) {
            Log.e("LoginScreen", "Camera initialization failed", e)
            Toast.makeText(context, "Camera initialization failed", Toast.LENGTH_LONG).show()
        }
    }
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "Face Login",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(top = 32.dp)
        )
        
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
                    
                    // Status overlay
                    Card(
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .padding(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f)
                        )
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            if (isProcessing) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    strokeWidth = 2.dp
                                )
                            }
                            Column {
                                Text(
                                    text = statusMessage,
                                    style = MaterialTheme.typography.bodyLarge
                                )
                                if (livenessMessage.isNotEmpty()) {
                                    Text(
                                        text = livenessMessage,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.secondary
                                    )
                                }
                            }
                        }
                    }
                    
                    // Face guide overlay
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(48.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = AppIcons.UserCircle,
                            contentDescription = "Face guide",
                            modifier = Modifier.size(200.dp),
                            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                        )
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
                        text = "Login Successful!",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = statusMessage,
                        style = MaterialTheme.typography.bodyLarge,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            }
        }
        
        AnimatedButton(
            onClick = {
                if (!isProcessing) {
                    captureAndVerifyFace(
                        imageCapture = imageCapture,
                        context = context,
                        faceDetector = faceDetector,
                        database = database,
                        postgresApi = postgresApi,
                        azureService = azureService,
                        userPreferences = userPreferences,
                        onProcessing = { processing ->
                            isProcessing = processing
                        },
                        onSuccess = {
                            statusMessage = "Welcome back!"
                            livenessMessage = "Face and liveness verified ✓"
                            showCamera = false
                            scope.launch {
                                kotlinx.coroutines.delay(1500)
                                onLoginSuccess()
                            }
                        },
                        onLivenessUpdate = { message ->
                            livenessMessage = message
                        },
                        onError = { error ->
                            statusMessage = error
                            livenessMessage = ""
                            scope.launch {
                                kotlinx.coroutines.delay(3000)
                                statusMessage = "Position your face in the camera"
                                livenessMessage = ""
                            }
                        }
                    )
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            enabled = !isProcessing && showCamera,
            animationsEnabled = animationsEnabled
        ) {
            if (isProcessing) {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onPrimary
                )
            } else {
                Icon(AppIcons.FaceRecognition, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Verify Face")
            }
        }
        
        TextButton(
            onClick = onNavigateToRegistration,
            enabled = !isProcessing
        ) {
            Text("New user? Register first")
        }
    }
}

@OptIn(DelicateCoroutinesApi::class)
private fun captureAndVerifyFace(
    imageCapture: ImageCapture,
    context: android.content.Context,
    faceDetector: SimpleFaceDetector,
    database: AppDatabase,
    postgresApi: PostgresApiService,
    azureService: AzureFaceService,
    userPreferences: UserPreferences,
    onProcessing: (Boolean) -> Unit,
    onSuccess: () -> Unit,
    onError: (String) -> Unit,
    onLivenessUpdate: (String) -> Unit = {}
) {
    val photoFile = File(context.cacheDir, "temp_login_${System.currentTimeMillis()}.jpg")
    val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()
    
    imageCapture.takePicture(
        outputOptions,
        ContextCompat.getMainExecutor(context),
        object : ImageCapture.OnImageSavedCallback {
            override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                kotlinx.coroutines.GlobalScope.launch {
                    try {
                        onProcessing(true)
                        
                        val bitmap = BitmapUtils.loadAndRotateBitmap(photoFile.absolutePath)
                        if (bitmap == null) {
                            withContext(Dispatchers.Main) {
                                onError("Failed to process image")
                                onProcessing(false)
                            }
                            return@launch
                        }
                        
                        // Use FaceRecognitionManager for unified face detection and verification
                        val faceRecognitionManager = FaceRecognitionManager(context)

                        // First check if any faces are detected
                        val faceCount = faceRecognitionManager.detectFaces(bitmap)
                        Log.d("LoginScreen", "Detected $faceCount face(s)")

                        if (faceCount == 0) {
                            withContext(Dispatchers.Main) {
                                onError("No face detected. Please try again.")
                                onProcessing(false)
                            }
                            photoFile.delete()
                            return@launch
                        }

                        // Get stored user profile to verify against
                        val userProfile = database.userProfileDao().getUserProfile()

                        if (userProfile == null) {
                            withContext(Dispatchers.Main) {
                                onError("No user registered. Please register first.")
                                onProcessing(false)
                            }
                            photoFile.delete()
                            return@launch
                        }

                        // Update UI to show liveness check
                        withContext(Dispatchers.Main) {
                            onLivenessUpdate("Identifying user...")
                        }

                        // First, identify who this person is
                        val identificationResult = faceRecognitionManager.identifyFace(bitmap)

                        if (identificationResult == null || identificationResult.personId.isNullOrEmpty()) {
                            withContext(Dispatchers.Main) {
                                onError("Face not recognized. Please register first.")
                                onProcessing(false)
                            }
                            photoFile.delete()
                            return@launch
                        }

                        Log.d("LoginScreen", "Identified user: ${identificationResult.personId} with confidence: ${identificationResult.confidence}")

                        // Now verify this is a real person (liveness check)
                        withContext(Dispatchers.Main) {
                            onLivenessUpdate("Performing liveness check...")
                        }

                        val verificationResult = faceRecognitionManager.verifyFace(
                            bitmap = bitmap,
                            personId = identificationResult.personId, // Use the identified person, not the stored one
                            requireLiveness = true
                        )

                        Log.d("LoginScreen", "Verification result: ${verificationResult.isIdentical}, confidence: ${verificationResult.confidence}, liveness: ${verificationResult.isLive}")

                        if (!verificationResult.isLive) {
                            // Liveness check failed
                            withContext(Dispatchers.Main) {
                                onLivenessUpdate("❌ Liveness check failed (${(verificationResult.livenessConfidence * 100).toInt()}% confidence)")
                                onError("Spoofing attempt detected! Please use your real face.")
                                onProcessing(false)
                            }
                        } else if (verificationResult.isIdentical) {
                            // Update liveness status
                            withContext(Dispatchers.Main) {
                                onLivenessUpdate("✓ Liveness verified (${(verificationResult.livenessConfidence * 100).toInt()}% confidence)")
                            }

                            // Load the identified user's profile from database
                            val identifiedUserProfile = database.userProfileDao().getUserProfileByEmployeeId(identificationResult.personId)

                            if (identifiedUserProfile != null) {
                                // Set this user as the current user in database
                                database.userProfileDao().clearCurrentUser()
                                database.userProfileDao().setCurrentUser(identifiedUserProfile.employeeId)

                                // Face verified successfully - save the correct user's profile
                                userPreferences.saveUserProfile(
                                    isRegistered = true,
                                    employeeId = identifiedUserProfile.employeeId,
                                    employeeCode = identifiedUserProfile.employeeCode,
                                    name = identifiedUserProfile.name,
                                    department = identifiedUserProfile.department,
                                    azureFaceId = identifiedUserProfile.faceId
                                )

                                Log.d("LoginScreen", "Logged in as: ${identifiedUserProfile.name} (${identifiedUserProfile.employeeId})")
                            } else {
                                // User identified by face but not found in database - this shouldn't happen
                                Log.e("LoginScreen", "User ${identificationResult.personId} identified by face but not found in database")
                                withContext(Dispatchers.Main) {
                                    onError("User profile not found. Please register again.")
                                    onProcessing(false)
                                }
                                photoFile.delete()
                                return@launch
                            }

                            withContext(Dispatchers.Main) {
                                onSuccess()
                                onProcessing(false)
                            }
                        } else {
                            withContext(Dispatchers.Main) {
                                val message = "Face not recognized. Please try again."
                                onError(message)
                                onProcessing(false)
                            }
                        }
                    } catch (e: Exception) {
                        Log.e("LoginScreen", "Error during face verification", e)
                        
                        // If Azure Face API fails, check if user has local profile (for demo purposes)
                        try {
                            val localProfile = database.userProfileDao().getUserProfile()
                            if (localProfile != null) {
                                // Allow login with local profile for demo
                                userPreferences.saveUserProfile(
                                    isRegistered = true,
                                    employeeId = localProfile.employeeId,
                                    employeeCode = localProfile.employeeCode,
                                    name = localProfile.name,
                                    department = localProfile.department,
                                    azureFaceId = localProfile.faceId
                                )
                                
                                withContext(Dispatchers.Main) {
                                    onSuccess()
                                    onProcessing(false)
                                }
                            } else {
                                withContext(Dispatchers.Main) {
                                    onError("Azure Face API error. Please check your connection.")
                                    onProcessing(false)
                                }
                            }
                        } catch (_: Exception) {
                            withContext(Dispatchers.Main) {
                                onError("Login failed: ${e.message}")
                                onProcessing(false)
                            }
                        }
                    } finally {
                        photoFile.delete()
                    }
                }
            }
            
            override fun onError(exception: ImageCaptureException) {
                Log.e("LoginScreen", "Photo capture failed", exception)
                onError("Camera capture failed: ${exception.message}")
                onProcessing(false)
            }
        }
    )
}