package com.mandelbulb.smartattendancesystem.ui

import android.graphics.Bitmap
import android.util.Log
import android.widget.Toast
import androidx.annotation.OptIn
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.mandelbulb.smartattendancesystem.data.AppDatabase
import com.mandelbulb.smartattendancesystem.data.UserPreferences
import com.mandelbulb.smartattendancesystem.data.UserProfileEntity
import com.mandelbulb.smartattendancesystem.ml.LivenessDetector
import com.mandelbulb.smartattendancesystem.network.AzureFaceService
import com.mandelbulb.smartattendancesystem.network.PostgresApiService
import com.mandelbulb.smartattendancesystem.network.FaceRecognitionManager
import com.mandelbulb.smartattendancesystem.network.OfflineFaceNetService
import com.mandelbulb.smartattendancesystem.ui.components.AnimatedCard
import com.mandelbulb.smartattendancesystem.ui.components.AnimatedButton
import com.mandelbulb.smartattendancesystem.ui.components.AnimatedCheckmark
import com.mandelbulb.smartattendancesystem.ui.components.calculateStaggeredDelay
import com.mandelbulb.smartattendancesystem.util.BitmapUtils
import com.mandelbulb.smartattendancesystem.util.SimpleFaceDetector
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

@OptIn(ExperimentalGetImage::class)
@Composable
fun RegistrationScreen(
    onRegistrationComplete: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val lifecycleOwner = LocalLifecycleOwner.current
    
    var employeeName by remember { mutableStateOf("") }
    var employeeCode by remember { mutableStateOf("") }
    var department by remember { mutableStateOf("") }
    var capturedFace by remember { mutableStateOf<Bitmap?>(null) }
    var isProcessing by remember { mutableStateOf(false) }
    var registrationStep by remember { mutableStateOf(RegistrationStep.DETAILS) }
    var livenessScore by remember { mutableStateOf(0f) }
    var showLivenessInstructions by remember { mutableStateOf(false) }
    
    val simpleFaceDetector = remember { SimpleFaceDetector(context) }
    val livenessDetector = remember { LivenessDetector() }
    val userPreferences = remember { UserPreferences(context) }
    val database = remember { AppDatabase.getInstance(context) }
    val postgresApi = remember { PostgresApiService() }
    val azureService = remember { 
        AzureFaceService(
            AzureFaceService.SUBSCRIPTION_KEY,
            AzureFaceService.AZURE_ENDPOINT
        )
    }
    
    var imageCapture by remember { mutableStateOf<ImageCapture?>(null) }
    var previewView by remember { mutableStateOf<PreviewView?>(null) }
    var cameraProvider by remember { mutableStateOf<ProcessCameraProvider?>(null) }
    var mlKitFaceDetector by remember { mutableStateOf<com.google.mlkit.vision.face.FaceDetector?>(null) }
    var animationsEnabled by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        userPreferences.animationsEnabled.collect { enabled ->
            animationsEnabled = enabled
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            // Clean up camera resources
            cameraProvider?.unbindAll()
            // Clean up face detector
            mlKitFaceDetector?.close()
            Log.d("RegistrationScreen", "Cleaned up camera and face detector resources")
        }
    }
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Employee Registration",
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(vertical = 24.dp)
        )
        
        when (registrationStep) {
            RegistrationStep.DETAILS -> {
                AnimatedCard(
                    modifier = Modifier.fillMaxWidth(),
                    animationsEnabled = animationsEnabled,
                    delayMillis = calculateStaggeredDelay(0)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        OutlinedTextField(
                            value = employeeCode,
                            onValueChange = { employeeCode = it },
                            label = { Text("Employee Code") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            leadingIcon = {
                                Icon(Icons.Default.Person, contentDescription = null)
                            }
                        )
                        
                        OutlinedTextField(
                            value = employeeName,
                            onValueChange = { employeeName = it },
                            label = { Text("Full Name") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            leadingIcon = {
                                Icon(Icons.Default.AccountCircle, contentDescription = null)
                            }
                        )
                        
                        OutlinedTextField(
                            value = department,
                            onValueChange = { department = it },
                            label = { Text("Department") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            leadingIcon = {
                                Icon(Icons.Default.Home, contentDescription = null)
                            }
                        )
                        
                        AnimatedButton(
                            onClick = {
                                if (employeeCode.isNotBlank() &&
                                    employeeName.isNotBlank() &&
                                    department.isNotBlank()) {
                                    registrationStep = RegistrationStep.FACE_CAPTURE
                                } else {
                                    Toast.makeText(context, "Please fill all fields", Toast.LENGTH_SHORT).show()
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = employeeCode.isNotBlank() &&
                                     employeeName.isNotBlank() &&
                                     department.isNotBlank(),
                            animationsEnabled = animationsEnabled
                        ) {
                            Text("Next - Capture Face")
                            Spacer(modifier = Modifier.width(8.dp))
                            Icon(Icons.Default.ArrowForward, contentDescription = null)
                        }
                    }
                }
            }
            
            RegistrationStep.FACE_CAPTURE -> {
                AnimatedCard(
                    modifier = Modifier.fillMaxWidth(),
                    animationsEnabled = animationsEnabled,
                    delayMillis = calculateStaggeredDelay(0)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Text(
                            text = "Face Capture",
                            style = MaterialTheme.typography.headlineSmall
                        )
                        
                        if (capturedFace == null) {
                            // Show camera preview
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(400.dp)
                            ) {
                                AndroidView(
                                    factory = { ctx ->
                                        PreviewView(ctx).also { pv ->
                                            previewView = pv
                                            
                                            val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
                                            cameraProviderFuture.addListener({
                                                val provider = cameraProviderFuture.get()
                                                cameraProvider = provider
                                                
                                                val preview = Preview.Builder()
                                                    .build()
                                                    .also {
                                                        it.surfaceProvider = pv.surfaceProvider
                                                    }
                                                
                                                val capture = ImageCapture.Builder()
                                                    .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
                                                    .build()
                                                imageCapture = capture
                                                
                                                // Set up ML Kit face detector
                                                val options = FaceDetectorOptions.Builder()
                                                    .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE)
                                                    .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL)
                                                    .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL)
                                                    .setMinFaceSize(0.15f)
                                                    .enableTracking()
                                                    .build()
                                                
                                                mlKitFaceDetector = FaceDetection.getClient(options)
                                                
                                                val imageAnalysis = ImageAnalysis.Builder()
                                                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                                                    .build()
                                                
                                                imageAnalysis.setAnalyzer(ContextCompat.getMainExecutor(ctx)) { imageProxy ->
                                                    val mediaImage = imageProxy.image
                                                    if (mediaImage != null) {
                                                        val image = InputImage.fromMediaImage(
                                                            mediaImage,
                                                            imageProxy.imageInfo.rotationDegrees
                                                        )
                                                        
                                                        mlKitFaceDetector?.process(image)
                                                            ?.addOnSuccessListener { faces ->
                                                                if (faces.isNotEmpty()) {
                                                                    val face = faces[0]
                                                                    // Calculate liveness score based on face attributes
                                                                    val smileProb = face.smilingProbability ?: 0f
                                                                    val leftEyeOpenProb = face.leftEyeOpenProbability ?: 0f
                                                                    val rightEyeOpenProb = face.rightEyeOpenProbability ?: 0f
                                                                    
                                                                    livenessScore = (leftEyeOpenProb + rightEyeOpenProb) / 2f
                                                                }
                                                            }
                                                            ?.addOnCompleteListener {
                                                                imageProxy.close()
                                                            }
                                                    } else {
                                                        imageProxy.close()
                                                    }
                                                }
                                                
                                                try {
                                                    provider.unbindAll()
                                                    provider.bindToLifecycle(
                                                        lifecycleOwner,
                                                        CameraSelector.DEFAULT_FRONT_CAMERA,
                                                        preview,
                                                        capture,
                                                        imageAnalysis
                                                    )
                                                } catch (e: Exception) {
                                                    Log.e("RegistrationScreen", "Camera binding failed", e)
                                                }
                                            }, ContextCompat.getMainExecutor(ctx))
                                        }
                                    },
                                    modifier = Modifier.fillMaxSize()
                                )
                                
                                // Liveness indicator
                                if (livenessScore > 0) {
                                    Card(
                                        modifier = Modifier
                                            .align(Alignment.TopEnd)
                                            .padding(16.dp),
                                        colors = CardDefaults.cardColors(
                                            containerColor = if (livenessScore > 0.5f) 
                                                MaterialTheme.colorScheme.primary 
                                            else 
                                                MaterialTheme.colorScheme.error
                                        )
                                    ) {
                                        Text(
                                            text = "Liveness: ${(livenessScore * 100).toInt()}%",
                                            modifier = Modifier.padding(8.dp),
                                            color = MaterialTheme.colorScheme.onPrimary
                                        )
                                    }
                                }
                            }
                            
                            // Liveness instructions button
                            TextButton(onClick = { showLivenessInstructions = true }) {
                                Icon(Icons.Default.Info, contentDescription = null)
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Liveness Tips")
                            }
                            
                            AnimatedButton(
                                onClick = {
                                    val capture = imageCapture
                                    if (capture != null) {
                                        val photoFile = File(
                                            context.cacheDir,
                                            "face_${System.currentTimeMillis()}.jpg"
                                        )
                                        
                                        val outputFileOptions = ImageCapture.OutputFileOptions
                                            .Builder(photoFile)
                                            .build()
                                        
                                        capture.takePicture(
                                            outputFileOptions,
                                            ContextCompat.getMainExecutor(context),
                                            object : ImageCapture.OnImageSavedCallback {
                                                override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                                                    // Use BitmapUtils to properly handle rotation
                                                    val bitmap = BitmapUtils.loadAndRotateBitmap(photoFile.absolutePath)
                                                    
                                                    if (bitmap != null) {
                                                        // Check face with ML Kit detector
                                                        val inputImage = InputImage.fromBitmap(bitmap, 0)
                                                        mlKitFaceDetector?.process(inputImage)
                                                            ?.addOnSuccessListener { faces ->
                                                                if (faces.isNotEmpty()) {
                                                                    capturedFace = bitmap
                                                                    
                                                                    // Clean up camera
                                                                    cameraProvider?.unbindAll()
                                                                    
                                                                    Toast.makeText(context, "Face captured successfully", Toast.LENGTH_SHORT).show()
                                                                } else {
                                                                    Toast.makeText(context, "No face detected. Please try again.", Toast.LENGTH_SHORT).show()
                                                                }
                                                                photoFile.delete()
                                                            }
                                                            ?.addOnFailureListener { e ->
                                                                Log.e("RegistrationScreen", "Face detection failed", e)
                                                                Toast.makeText(context, "Face detection failed", Toast.LENGTH_SHORT).show()
                                                                photoFile.delete()
                                                            }
                                                    } else {
                                                        Toast.makeText(context, "Failed to process image", Toast.LENGTH_SHORT).show()
                                                        photoFile.delete()
                                                    }
                                                }
                                                
                                                override fun onError(exception: ImageCaptureException) {
                                                    Log.e("RegistrationScreen", "Photo capture failed", exception)
                                                    Toast.makeText(context, "Photo capture failed", Toast.LENGTH_SHORT).show()
                                                }
                                            }
                                        )
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(),
                                enabled = livenessScore > 0.5f,
                                animationsEnabled = animationsEnabled
                            ) {
                                Icon(Icons.Default.Face, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Capture Face")
                            }
                        } else {
                            // Show captured face
                            Image(
                                bitmap = capturedFace!!.asImageBitmap(),
                                contentDescription = "Captured face",
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(300.dp)
                            )
                            
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedButton(
                                    onClick = { 
                                        capturedFace = null
                                        livenessScore = 0f
                                    },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(Icons.Default.Refresh, contentDescription = null)
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Retake")
                                }
                                
                                AnimatedButton(
                                    onClick = {
                                        scope.launch {
                                            registerEmployee(
                                                name = employeeName,
                                                code = employeeCode,
                                                department = department,
                                                faceBitmap = capturedFace!!,
                                                context = context,
                                                postgresApi = postgresApi,
                                                azureService = azureService,
                                                database = database,
                                                userPreferences = userPreferences,
                                                onProcessing = { isProcessing = it },
                                                onSuccess = {
                                                    registrationStep = RegistrationStep.COMPLETE
                                                },
                                                onError = { error ->
                                                    Toast.makeText(context, error, Toast.LENGTH_LONG).show()
                                                    // Allow retry
                                                    capturedFace = null
                                                    livenessScore = 0f
                                                }
                                            )
                                        }
                                    },
                                    modifier = Modifier.weight(1f),
                                    enabled = !isProcessing,
                                    animationsEnabled = animationsEnabled
                                ) {
                                    if (isProcessing) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(16.dp),
                                            strokeWidth = 2.dp
                                        )
                                    } else {
                                        Icon(Icons.Default.Check, contentDescription = null)
                                    }
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(if (isProcessing) "Registering..." else "Register")
                                }
                            }
                        }
                        
                        OutlinedButton(
                            onClick = { registrationStep = RegistrationStep.DETAILS },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.ArrowBack, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Back to Details")
                        }
                    }
                }
            }
            
            RegistrationStep.COMPLETE -> {
                AnimatedCard(
                    modifier = Modifier.fillMaxWidth(),
                    animationsEnabled = animationsEnabled,
                    delayMillis = calculateStaggeredDelay(0)
                ) {
                    Column(
                        modifier = Modifier.padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        AnimatedCheckmark(
                            isVisible = true,
                            modifier = Modifier,
                            animationsEnabled = animationsEnabled
                        )
                        
                        Text(
                            text = "Registration Successful!",
                            style = MaterialTheme.typography.headlineSmall,
                            textAlign = TextAlign.Center
                        )
                        
                        Text(
                            text = "Employee $employeeName has been registered successfully with Azure Face API.",
                            style = MaterialTheme.typography.bodyLarge,
                            textAlign = TextAlign.Center
                        )
                        
                        AnimatedButton(
                            onClick = onRegistrationComplete,
                            modifier = Modifier.fillMaxWidth(),
                            animationsEnabled = animationsEnabled
                        ) {
                            Text("Continue to Dashboard")
                            Spacer(modifier = Modifier.width(8.dp))
                            Icon(Icons.Default.ArrowForward, contentDescription = null)
                        }
                    }
                }
            }
        }
    }
    
    // Liveness instructions dialog
    if (showLivenessInstructions) {
        AlertDialog(
            onDismissRequest = { showLivenessInstructions = false },
            title = { Text("Liveness Detection Tips") },
            text = {
                Text(
                    "For better liveness detection:\n" +
                    "• Ensure good lighting\n" +
                    "• Keep your eyes open\n" +
                    "• Face the camera directly\n" +
                    "• Remove sunglasses or masks\n" +
                    "• Stay still during capture"
                )
            },
            confirmButton = {
                TextButton(onClick = { showLivenessInstructions = false }) {
                    Text("Got it")
                }
            }
        )
    }
}

private suspend fun registerEmployee(
    name: String,
    code: String,
    department: String,
    faceBitmap: Bitmap,
    context: android.content.Context,
    postgresApi: PostgresApiService,
    azureService: AzureFaceService,
    database: AppDatabase,
    userPreferences: UserPreferences,
    onProcessing: (Boolean) -> Unit,
    onSuccess: () -> Unit,
    onError: (String) -> Unit
) {
    withContext(Dispatchers.Main) { onProcessing(true) }
    
    try {
        Log.d("Registration", "Starting face registration for $name")

        // Generate a unique employee ID upfront for consistency
        val employeeId = UUID.randomUUID().toString().lowercase() // Ensure lowercase for PostgreSQL compatibility

        // Use FaceRecognitionManager for dual enrollment (Azure + FaceNet)
        val faceRecognitionManager = FaceRecognitionManager(context)

        // Enroll face with both online and offline systems
        val enrollmentResult = faceRecognitionManager.enrollFaceWithEmbedding(
            bitmap = faceBitmap,
            personId = employeeId,  // Use consistent ID everywhere
            personName = name
        )

        if (!enrollmentResult.success) {
            throw Exception(enrollmentResult.message.ifEmpty { "Face enrollment failed" })
        }

        // Get the embedding from FaceNet (for offline support)
        val embedding = enrollmentResult.embedding ?: FloatArray(0)
        val azureFaceId = enrollmentResult.azureFaceId

        Log.d("Registration", "Face enrolled successfully - Embedding size: ${embedding.size}, Azure Face ID: $azureFaceId")

        // Try to register with backend (include FaceNet embeddings for cross-mode support)
        Log.d("Registration", "Sending to backend - EmployeeID: $employeeId, EmployeeCode: $code")
        val newEmployee = try {
            postgresApi.registerEmployee(
                employeeCode = code,
                name = name,
                department = department,
                embedding = embedding,  // Include FaceNet embedding for offline support
                faceId = azureFaceId,  // Include Azure face ID if available
                employeeId = employeeId  // Send our locally generated ID to backend
            )
        } catch (e: Exception) {
            Log.w("Registration", "Failed to register with backend (offline mode?): ${e.message}")
            null  // Continue with local registration
        }

        // Log what we got back from backend
        if (newEmployee != null) {
            Log.d("Registration", "Backend returned - EmployeeID: ${newEmployee.employeeId}, Our ID: $employeeId")
            if (newEmployee.employeeId != employeeId) {
                Log.w("Registration", "WARNING: Backend returned different ID! Backend: ${newEmployee.employeeId}, Local: $employeeId")
            }
        } else {
            Log.d("Registration", "Backend registration failed, using local ID: $employeeId")
        }

            // Save locally regardless of backend registration success
            // Backend sync will happen later through SyncManager
            if (true) {  // Always save locally
                // Convert embedding to ByteArray for storage
                val embeddingBytes = if (embedding.isNotEmpty()) {
                    val buffer = java.nio.ByteBuffer.allocate(embedding.size * 4)
                    embedding.forEach { buffer.putFloat(it) }
                    buffer.array()
                } else {
                    ByteArray(0)
                }

                // Save to local database with our consistent employeeId
                val userProfile = UserProfileEntity(
                    employeeId = employeeId,  // Use employeeId as primary key
                    employeeCode = code,
                    name = name,
                    department = department,
                    embedding = embeddingBytes,  // Store FaceNet embedding
                    faceId = azureFaceId,  // Store Azure face ID if available
                    registrationDate = System.currentTimeMillis(),
                    lastSync = if (newEmployee != null) System.currentTimeMillis() else 0L,
                    isCurrentUser = true  // Mark this user as the current user
                )

                // Clear any existing current user and insert the new one
                database.userProfileDao().clearCurrentUser()
                database.userProfileDao().insert(userProfile)

                // Save to preferences with our consistent employeeId
                userPreferences.saveUserProfile(
                    isRegistered = true,
                    employeeId = employeeId,  // Use our locally generated ID consistently
                    employeeCode = code,
                    name = name,
                    department = department,
                    azureFaceId = azureFaceId  // Store Azure face ID if available
                )
                
                withContext(Dispatchers.Main) {
                    onProcessing(false)
                    onSuccess()
                }
            } else {
                // This shouldn't happen since we always save locally now
                withContext(Dispatchers.Main) {
                    onProcessing(false)
                    onError("Failed to save employee locally")
                }
            }
    } catch (e: Exception) {
        Log.e("Registration", "Error", e)
        withContext(Dispatchers.Main) {
            onProcessing(false)
            onError("Registration failed: ${e.message}")
        }
    }
}

enum class RegistrationStep {
    DETAILS,
    FACE_CAPTURE,
    COMPLETE
}