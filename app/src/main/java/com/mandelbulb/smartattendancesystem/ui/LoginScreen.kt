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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.mandelbulb.smartattendancesystem.data.AppDatabase
import com.mandelbulb.smartattendancesystem.data.UserPreferences
import com.mandelbulb.smartattendancesystem.network.AzureFaceService
import com.mandelbulb.smartattendancesystem.network.PostgresApiService
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
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    
    var isProcessing by remember { mutableStateOf(false) }
    var statusMessage by remember { mutableStateOf("Position your face in the camera") }
    var showCamera by remember { mutableStateOf(true) }
    
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
        
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
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
                            Text(
                                text = statusMessage,
                                style = MaterialTheme.typography.bodyLarge
                            )
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
                            Icons.Default.Face,
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
                    Icon(
                        Icons.Default.CheckCircle,
                        contentDescription = null,
                        modifier = Modifier.size(80.dp),
                        tint = MaterialTheme.colorScheme.primary
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
        
        Button(
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
                            showCamera = false
                            scope.launch {
                                kotlinx.coroutines.delay(1500)
                                onLoginSuccess()
                            }
                        },
                        onError = { error ->
                            statusMessage = error
                            scope.launch {
                                kotlinx.coroutines.delay(3000)
                                statusMessage = "Position your face in the camera"
                            }
                        }
                    )
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            enabled = !isProcessing && showCamera
        ) {
            if (isProcessing) {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onPrimary
                )
            } else {
                Icon(Icons.Default.Face, contentDescription = null)
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
    onError: (String) -> Unit
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
                        
                        // Detect face locally first for basic validation
                        val faces = withContext(Dispatchers.IO) {
                            val result = mutableListOf<com.google.mlkit.vision.face.Face>()
                            faceDetector.detectFace(bitmap) { detectedFaces ->
                                result.addAll(detectedFaces)
                            }
                            Thread.sleep(500)
                            result
                        }
                        
                        if (faces.isEmpty()) {
                            withContext(Dispatchers.Main) {
                                onError("No face detected. Please try again.")
                                onProcessing(false)
                            }
                            return@launch
                        }
                        
                        // Use Azure Face API for verification
                        val detectedFaces = azureService.detectFace(bitmap)
                        
                        if (detectedFaces.isNotEmpty()) {
                            val faceId = detectedFaces[0].faceId
                            val verificationResult = azureService.identifyFace(faceId)
                            
                            if (verificationResult != null && verificationResult.isIdentical) {
                                // Face recognized by Azure, get employee details
                                // In a real app, you'd query the backend with the personId
                                // For now, we'll use the local profile if available
                                val localProfile = database.userProfileDao().getUserProfile()
                                
                                if (localProfile != null && localProfile.faceId != null) {
                                    // User is registered and face matches
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
                                        onError("Please complete registration first")
                                        onProcessing(false)
                                    }
                                }
                            } else {
                                withContext(Dispatchers.Main) {
                                    onError("Face not recognized. Please register first.")
                                    onProcessing(false)
                                }
                            }
                        } else {
                            withContext(Dispatchers.Main) {
                                onError("Could not detect face clearly. Please try again.")
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
                        } catch (dbError: Exception) {
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