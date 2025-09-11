package com.mandelbulb.smartattendancesystem.ui

import android.graphics.Bitmap
import android.util.Log
import android.widget.Toast
import androidx.camera.core.CameraSelector
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
import com.mandelbulb.smartattendancesystem.ml.EmbeddingModel
import com.mandelbulb.smartattendancesystem.ml.LivenessDetector
import com.mandelbulb.smartattendancesystem.network.AzureFaceService
import com.mandelbulb.smartattendancesystem.network.PostgresApiService
import com.mandelbulb.smartattendancesystem.util.BitmapUtils
import com.mandelbulb.smartattendancesystem.util.SimpleFaceDetector
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

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
    val embeddingModel = remember { EmbeddingModel(context) }
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
            modifier = Modifier.padding(bottom = 24.dp)
        )
        
        when (registrationStep) {
            RegistrationStep.DETAILS -> {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        OutlinedTextField(
                            value = employeeName,
                            onValueChange = { employeeName = it },
                            label = { Text("Full Name") },
                            leadingIcon = { Icon(Icons.Default.Person, contentDescription = null) },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                        
                        OutlinedTextField(
                            value = employeeCode,
                            onValueChange = { employeeCode = it },
                            label = { Text("Employee Code") },
                            leadingIcon = { Icon(Icons.Default.Star, contentDescription = null) },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                        
                        OutlinedTextField(
                            value = department,
                            onValueChange = { department = it },
                            label = { Text("Department") },
                            leadingIcon = { Icon(Icons.Default.Home, contentDescription = null) },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                        
                        Button(
                            onClick = {
                                if (employeeName.isNotBlank() && 
                                    employeeCode.isNotBlank() && 
                                    department.isNotBlank()) {
                                    registrationStep = RegistrationStep.FACE_CAPTURE
                                    showLivenessInstructions = true
                                } else {
                                    Toast.makeText(context, "Please fill all fields", Toast.LENGTH_SHORT).show()
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Next: Capture Face")
                            Spacer(modifier = Modifier.width(8.dp))
                            Icon(Icons.Default.ArrowForward, contentDescription = null)
                        }
                    }
                }
            }
            
            RegistrationStep.FACE_CAPTURE -> {
                if (showLivenessInstructions) {
                    AlertDialog(
                        onDismissRequest = { showLivenessInstructions = false },
                        title = { Text("Liveness Check Instructions") },
                        text = {
                            Text(
                                "For security, we need to verify you're a real person:\n\n" +
                                "1. Position your face in the frame\n" +
                                "2. Blink your eyes naturally\n" +
                                "3. Smile briefly\n" +
                                "4. Turn your head slightly left and right\n\n" +
                                "The liveness score will increase as you perform these actions."
                            )
                        },
                        confirmButton = {
                            TextButton(onClick = { showLivenessInstructions = false }) {
                                Text("Got it")
                            }
                        }
                    )
                }
                
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        if (capturedFace == null) {
                            AndroidView(
                                factory = { ctx ->
                                    PreviewView(ctx).also { pv ->
                                        previewView = pv
                                        pv.implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                                        
                                        val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
                                        cameraProviderFuture.addListener({
                                            val provider = cameraProviderFuture.get()
                                            cameraProvider = provider
                                            
                                            val preview = Preview.Builder().build().also {
                                                it.setSurfaceProvider(pv.surfaceProvider)
                                            }
                                            
                                            imageCapture = ImageCapture.Builder()
                                                .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                                                .build()
                                            
                                            // Create a simple face detector for liveness
                                            val faceDetectorOptions = FaceDetectorOptions.Builder()
                                                .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL)
                                                .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
                                                .setMinFaceSize(0.15f)
                                                .build()
                                            
                                            val faceDetectorForLiveness = FaceDetection.getClient(faceDetectorOptions)
                                            mlKitFaceDetector = faceDetectorForLiveness
                                            
                                            // Add ImageAnalysis for liveness detection
                                            val imageAnalyzer = ImageAnalysis.Builder()
                                                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                                                .setTargetResolution(android.util.Size(640, 480))
                                                .build()
                                                .also { analysis ->
                                                    var isProcessing = false
                                                    var frameCount = 0
                                                    
                                                    analysis.setAnalyzer(
                                                        ContextCompat.getMainExecutor(ctx),
                                                        { imageProxy ->
                                                            frameCount++
                                                            // Process every 30th frame to reduce load significantly
                                                            if (!isProcessing && frameCount % 30 == 0) {
                                                                isProcessing = true
                                                                
                                                                val mediaImage = imageProxy.image
                                                                if (mediaImage != null) {
                                                                    val image = InputImage.fromMediaImage(
                                                                        mediaImage, 
                                                                        imageProxy.imageInfo.rotationDegrees
                                                                    )
                                                                    
                                                                    faceDetectorForLiveness.process(image)
                                                                        .addOnSuccessListener { faces ->
                                                                            try {
                                                                                if (faces.isNotEmpty()) {
                                                                                    val face = faces[0]
                                                                                    
                                                                                    // Simple liveness scoring
                                                                                    var score = 0.2f // Base score for face detected
                                                                                    
                                                                                    // Add score for smile
                                                                                    face.smilingProbability?.let {
                                                                                        if (it > 0.3f) score += 0.2f
                                                                                    }
                                                                                    
                                                                                    // Add score for eyes open
                                                                                    face.leftEyeOpenProbability?.let { left ->
                                                                                        face.rightEyeOpenProbability?.let { right ->
                                                                                            if (left > 0.5f && right > 0.5f) {
                                                                                                score += 0.3f
                                                                                            }
                                                                                        }
                                                                                    }
                                                                                    
                                                                                    // Add score for head movement
                                                                                    face.headEulerAngleY?.let { yaw ->
                                                                                        if (kotlin.math.abs(yaw) > 5f) {
                                                                                            score += 0.3f
                                                                                        }
                                                                                    }
                                                                                    
                                                                                    // Update score with smoothing
                                                                                    livenessScore = (livenessScore * 0.7f + score * 0.3f).coerceIn(0f, 1f)
                                                                                    
                                                                                    Log.d("RegistrationScreen", "Liveness score: $livenessScore (smile: ${face.smilingProbability}, eyes: ${face.leftEyeOpenProbability}/${face.rightEyeOpenProbability})")
                                                                                } else {
                                                                                    // Gradually decrease score if no face
                                                                                    livenessScore = (livenessScore * 0.9f).coerceIn(0f, 1f)
                                                                                }
                                                                            } catch (e: Exception) {
                                                                                Log.e("RegistrationScreen", "Error processing face", e)
                                                                            } finally {
                                                                                isProcessing = false
                                                                                imageProxy.close()
                                                                            }
                                                                        }
                                                                        .addOnFailureListener { e ->
                                                                            Log.e("RegistrationScreen", "Face detection failed", e)
                                                                            isProcessing = false
                                                                            imageProxy.close()
                                                                        }
                                                                        .addOnCompleteListener {
                                                                            // Ensure cleanup happens
                                                                        }
                                                                } else {
                                                                    isProcessing = false
                                                                    imageProxy.close()
                                                                }
                                                            } else {
                                                                // Close immediately if not processing
                                                                imageProxy.close()
                                                            }
                                                        }
                                                    )
                                                }
                                            
                                            try {
                                                provider.unbindAll()
                                                provider.bindToLifecycle(
                                                    lifecycleOwner,
                                                    CameraSelector.DEFAULT_FRONT_CAMERA,
                                                    preview,
                                                    imageCapture,
                                                    imageAnalyzer
                                                )
                                            } catch (e: Exception) {
                                                Log.e("RegistrationScreen", "Camera binding failed", e)
                                            }
                                        }, ContextCompat.getMainExecutor(ctx))
                                    }
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(400.dp)
                            )
                            
                            LinearProgressIndicator(
                                progress = livenessScore,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 8.dp)
                            )
                            
                            Text(
                                text = "Liveness Score: ${(livenessScore * 100).toInt()}%",
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(bottom = 4.dp)
                            )
                            
                            Text(
                                text = when {
                                    livenessScore < 0.1f -> "Position your face in the frame"
                                    livenessScore < 0.3f -> "Try smiling or blinking"
                                    livenessScore < 0.5f -> "Turn your head slightly"
                                    livenessScore < 0.7f -> "Good! Keep moving naturally"
                                    else -> "Perfect! Ready to capture"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(bottom = 8.dp)
                            )
                            
                            Button(
                                onClick = {
                                    captureAndProcessFace(
                                        imageCapture = imageCapture,
                                        context = context,
                                        faceDetector = simpleFaceDetector,
                                        onSuccess = { bitmap ->
                                            capturedFace = bitmap
                                            registrationStep = RegistrationStep.CONFIRMATION
                                        },
                                        onError = { error ->
                                            Toast.makeText(context, error, Toast.LENGTH_SHORT).show()
                                        }
                                    )
                                },
                                modifier = Modifier.fillMaxWidth(),
                                enabled = true // Always enabled for testing
                            ) {
                                Icon(Icons.Default.Add, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Capture Face")
                            }
                        } else {
                            Image(
                                bitmap = capturedFace!!.asImageBitmap(),
                                contentDescription = "Captured Face",
                                modifier = Modifier
                                    .size(200.dp)
                                    .padding(bottom = 16.dp)
                            )
                        }
                        
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            OutlinedButton(
                                onClick = {
                                    registrationStep = RegistrationStep.DETAILS
                                    capturedFace = null
                                }
                            ) {
                                Icon(Icons.Default.ArrowBack, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Back")
                            }
                            
                            if (capturedFace != null) {
                                Button(
                                    onClick = {
                                        capturedFace = null
                                        livenessScore = 0f
                                    }
                                ) {
                                    Text("Retake")
                                }
                            }
                        }
                    }
                }
            }
            
            RegistrationStep.CONFIRMATION -> {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "Confirm Registration",
                            style = MaterialTheme.typography.headlineSmall,
                            modifier = Modifier.padding(bottom = 16.dp)
                        )
                        
                        capturedFace?.let {
                            Image(
                                bitmap = it.asImageBitmap(),
                                contentDescription = "Face",
                                modifier = Modifier
                                    .size(150.dp)
                                    .padding(bottom = 16.dp)
                            )
                        }
                        
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            InfoRow("Name:", employeeName)
                            InfoRow("Code:", employeeCode)
                            InfoRow("Department:", department)
                        }
                        
                        Spacer(modifier = Modifier.height(24.dp))
                        
                        if (isProcessing) {
                            CircularProgressIndicator()
                            Text(
                                text = "Registering...",
                                modifier = Modifier.padding(top = 8.dp)
                            )
                        } else {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                OutlinedButton(
                                    onClick = {
                                        registrationStep = RegistrationStep.FACE_CAPTURE
                                        capturedFace = null
                                    }
                                ) {
                                    Text("Retake Photo")
                                }
                                
                                Button(
                                    onClick = {
                                        scope.launch {
                                            registerEmployee(
                                                name = employeeName,
                                                code = employeeCode,
                                                department = department,
                                                faceBitmap = capturedFace!!,
                                                context = context,
                                                embeddingModel = embeddingModel,
                                                postgresApi = postgresApi,
                                                azureService = azureService,
                                                database = database,
                                                userPreferences = userPreferences,
                                                onProcessing = { isProcessing = it },
                                                onSuccess = {
                                                    Toast.makeText(context, "Registration successful!", Toast.LENGTH_SHORT).show()
                                                    onRegistrationComplete()
                                                },
                                                onError = { error ->
                                                    Toast.makeText(context, error, Toast.LENGTH_LONG).show()
                                                }
                                            )
                                        }
                                    }
                                ) {
                                    Icon(Icons.Default.Check, contentDescription = null)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Complete Registration")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

private enum class RegistrationStep {
    DETAILS, FACE_CAPTURE, CONFIRMATION
}

private fun captureAndProcessFace(
    imageCapture: ImageCapture?,
    context: android.content.Context,
    faceDetector: SimpleFaceDetector,
    onSuccess: (Bitmap) -> Unit,
    onError: (String) -> Unit
) {
    if (imageCapture == null) {
        onError("Camera not ready")
        return
    }
    
    val photoFile = File(context.cacheDir, "temp_face_${System.currentTimeMillis()}.jpg")
    val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()
    
    imageCapture.takePicture(
        outputOptions,
        ContextCompat.getMainExecutor(context),
        object : ImageCapture.OnImageSavedCallback {
            override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                val bitmap = BitmapUtils.loadAndRotateBitmap(photoFile.absolutePath)
                if (bitmap != null) {
                    faceDetector.detectFace(bitmap) { faces ->
                        if (faces.isNotEmpty()) {
                            onSuccess(bitmap)
                        } else {
                            onError("No face detected. Please try again.")
                        }
                        photoFile.delete()
                    }
                } else {
                    onError("Failed to process image")
                    photoFile.delete()
                }
            }
            
            override fun onError(exception: ImageCaptureException) {
                onError("Capture failed: ${exception.message}")
                photoFile.delete()
            }
        }
    )
}

private suspend fun registerEmployee(
    name: String,
    code: String,
    department: String,
    faceBitmap: Bitmap,
    context: android.content.Context,
    embeddingModel: EmbeddingModel,
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
        val embedding = withContext(Dispatchers.Default) {
            embeddingModel.generateEmbedding(faceBitmap)
        }
        
        val existingEmployee = postgresApi.findEmployeeByEmbedding(embedding)
        
        if (existingEmployee != null) {
            if (existingEmployee.employeeCode == code) {
                val userProfile = UserProfileEntity(
                    id = 1,
                    employeeId = existingEmployee.employeeId,
                    employeeCode = existingEmployee.employeeCode,
                    name = existingEmployee.name,
                    department = existingEmployee.department,
                    embedding = embedding.toByteArray(),
                    faceId = existingEmployee.faceId,
                    registrationDate = existingEmployee.registrationDate,
                    lastSync = System.currentTimeMillis()
                )
                
                database.userProfileDao().insert(userProfile)
                
                userPreferences.saveUserProfile(
                    isRegistered = true,
                    employeeId = existingEmployee.employeeId,
                    employeeCode = existingEmployee.employeeCode,
                    name = existingEmployee.name,
                    department = existingEmployee.department,
                    azureFaceId = existingEmployee.faceId
                )
                
                // Save face embedding for offline verification
                userPreferences.saveFaceEmbedding(embedding)
                
                withContext(Dispatchers.Main) {
                    onProcessing(false)
                    onSuccess()
                }
            } else {
                withContext(Dispatchers.Main) {
                    onProcessing(false)
                    onError("Face already registered with different employee code")
                }
            }
        } else {
            val detectedFaces = azureService.detectFace(faceBitmap)
            var azureFaceId: String? = null
            
            if (detectedFaces.isNotEmpty()) {
                val personId = azureService.createPerson(
                    name = name,
                    userData = code
                )
                
                if (personId != null) {
                    azureFaceId = azureService.addFaceToPerson(personId, faceBitmap)
                    azureService.trainPersonGroup()
                }
            }
            
            val newEmployee = postgresApi.registerEmployee(
                employeeCode = code,
                name = name,
                department = department,
                embedding = embedding,
                faceId = azureFaceId
            )
            
            if (newEmployee != null) {
                val userProfile = UserProfileEntity(
                    id = 1,
                    employeeId = newEmployee.employeeId,
                    employeeCode = code,
                    name = name,
                    department = department,
                    embedding = embedding.toByteArray(),
                    faceId = azureFaceId,
                    registrationDate = System.currentTimeMillis(),
                    lastSync = System.currentTimeMillis()
                )
                
                database.userProfileDao().insert(userProfile)
                
                userPreferences.saveUserProfile(
                    isRegistered = true,
                    employeeId = newEmployee.employeeId,
                    employeeCode = code,
                    name = name,
                    department = department,
                    azureFaceId = azureFaceId
                )
                
                // Save face embedding for offline verification
                userPreferences.saveFaceEmbedding(embedding)
                
                withContext(Dispatchers.Main) {
                    onProcessing(false)
                    onSuccess()
                }
            } else {
                withContext(Dispatchers.Main) {
                    onProcessing(false)
                    onError("Failed to register with server")
                }
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

private fun FloatArray.toByteArray(): ByteArray {
    val buffer = java.nio.ByteBuffer.allocate(size * 4)
    forEach { buffer.putFloat(it) }
    return buffer.array()
}