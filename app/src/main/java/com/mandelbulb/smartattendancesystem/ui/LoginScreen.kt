package com.mandelbulb.smartattendancesystem.ui

import android.graphics.Bitmap
import android.util.Log
import android.widget.Toast
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
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
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.mandelbulb.smartattendancesystem.data.AppDatabase
import com.mandelbulb.smartattendancesystem.data.UserPreferences
import com.mandelbulb.smartattendancesystem.ml.EmbeddingModel
import com.mandelbulb.smartattendancesystem.network.AzureFaceService
import com.mandelbulb.smartattendancesystem.network.PostgresApiService
import com.mandelbulb.smartattendancesystem.util.BitmapUtils
import com.mandelbulb.smartattendancesystem.util.MathUtils
import com.mandelbulb.smartattendancesystem.util.SimpleFaceDetector
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.ByteBuffer

@Composable
fun LoginScreen(
    onLoginSuccess: () -> Unit,
    onNavigateToRegistration: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    
    var isProcessing by remember { mutableStateOf(false) }
    var showCamera by remember { mutableStateOf(false) }
    var capturedFace by remember { mutableStateOf<Bitmap?>(null) }
    var loginError by remember { mutableStateOf<String?>(null) }
    
    val faceDetector = remember { SimpleFaceDetector(context) }
    val embeddingModel = remember { EmbeddingModel(context) }
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
    
    LaunchedEffect(Unit) {
        val isRegistered = userPreferences.isRegistered.first()
        if (isRegistered) {
            onLoginSuccess()
        }
    }
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    imageVector = Icons.Default.Lock,
                    contentDescription = null,
                    modifier = Modifier
                        .size(64.dp)
                        .padding(bottom = 16.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                
                Text(
                    text = "Smart Attendance System",
                    style = MaterialTheme.typography.headlineSmall,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                
                Text(
                    text = "Login with Face Recognition",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(bottom = 24.dp)
                )
                
                if (showCamera) {
                    AndroidView(
                        factory = { ctx ->
                            PreviewView(ctx).also { previewView ->
                                previewView.implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                                
                                val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
                                cameraProviderFuture.addListener({
                                    val cameraProvider = cameraProviderFuture.get()
                                    
                                    val preview = Preview.Builder().build().also {
                                        it.surfaceProvider = previewView.surfaceProvider
                                    }
                                    
                                    imageCapture = ImageCapture.Builder()
                                        .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                                        .build()
                                    
                                    try {
                                        cameraProvider.unbindAll()
                                        cameraProvider.bindToLifecycle(
                                            lifecycleOwner,
                                            CameraSelector.DEFAULT_FRONT_CAMERA,
                                            preview,
                                            imageCapture
                                        )
                                    } catch (e: Exception) {
                                        Log.e("LoginScreen", "Camera binding failed", e)
                                    }
                                }, ContextCompat.getMainExecutor(ctx))
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(300.dp)
                            .padding(bottom = 16.dp)
                    )
                    
                    if (isProcessing) {
                        CircularProgressIndicator(
                            modifier = Modifier.padding(16.dp)
                        )
                        Text(
                            text = "Verifying identity...",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    } else {
                        Button(
                            onClick = {
                                imageCapture?.let { capture ->
                                    captureAndVerifyFace(
                                        imageCapture = capture,
                                        context = context,
                                        faceDetector = faceDetector,
                                        embeddingModel = embeddingModel,
                                        database = database,
                                        postgresApi = postgresApi,
                                        azureService = azureService,
                                        userPreferences = userPreferences,
                                        onProcessing = { isProcessing = it },
                                        onSuccess = {
                                            Toast.makeText(context, "Login successful!", Toast.LENGTH_SHORT).show()
                                            onLoginSuccess()
                                        },
                                        onError = { error ->
                                            loginError = error
                                            showCamera = false
                                        }
                                    )
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Capture & Verify")
                        }
                        
                        OutlinedButton(
                            onClick = { showCamera = false },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Cancel")
                        }
                    }
                } else {
                    if (loginError != null) {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 16.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer
                            )
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Default.Close,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = loginError!!,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onErrorContainer
                                )
                            }
                        }
                    }
                    
                    Button(
                        onClick = { 
                            loginError = null
                            showCamera = true 
                        },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !isProcessing
                    ) {
                        Icon(Icons.Default.Face, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Login with Face")
                    }
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    HorizontalDivider()
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    OutlinedButton(
                        onClick = onNavigateToRegistration,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("New Registration")
                    }
                }
            }
        }
    }
}

@OptIn(DelicateCoroutinesApi::class)
private fun captureAndVerifyFace(
    imageCapture: ImageCapture,
    context: android.content.Context,
    faceDetector: SimpleFaceDetector,
    embeddingModel: EmbeddingModel,
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
                        
                        val embedding = withContext(Dispatchers.Default) {
                            embeddingModel.generateEmbedding(bitmap)
                        }
                        
                        try {
                            val employee = postgresApi.findEmployeeByEmbedding(embedding)
                            
                            if (employee != null) {
                                val userProfile = com.mandelbulb.smartattendancesystem.data.UserProfileEntity(
                                    id = 1,
                                    employeeId = employee.employeeId,
                                    employeeCode = employee.employeeCode,
                                    name = employee.name,
                                    department = employee.department,
                                    embedding = floatArrayToByteArray(embedding),
                                    faceId = employee.faceId,
                                    registrationDate = employee.registrationDate,
                                    lastSync = System.currentTimeMillis()
                                )
                                
                                database.userProfileDao().insert(userProfile)
                                
                                userPreferences.saveUserProfile(
                                    isRegistered = true,
                                    employeeId = employee.employeeId,
                                    employeeCode = employee.employeeCode,
                                    name = employee.name,
                                    department = employee.department,
                                    azureFaceId = employee.faceId
                                )
                                
                                withContext(Dispatchers.Main) {
                                    onSuccess()
                                    onProcessing(false)
                                }
                            } else {
                                withContext(Dispatchers.Main) {
                                    onError("Face not recognized. Please register first.")
                                    onProcessing(false)
                                }
                            }
                        } catch (networkError: Exception) {
                            Log.e("LoginScreen", "Network error, trying offline", networkError)
                            
                            val localProfile = database.userProfileDao().getUserProfile()
                            if (localProfile != null) {
                                val localEmbedding = byteArrayToFloatArray(localProfile.embedding)
                                val similarity = MathUtils.cosineSimilarity(embedding, localEmbedding)
                                
                                if (similarity > 0.85) {
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
                                        onError("Face verification failed (Offline mode)")
                                        onProcessing(false)
                                    }
                                }
                            } else {
                                withContext(Dispatchers.Main) {
                                    onError("No offline profile found. Please connect to internet.")
                                    onProcessing(false)
                                }
                            }
                        }
                    } catch (e: Exception) {
                        Log.e("LoginScreen", "Verification error", e)
                        withContext(Dispatchers.Main) {
                            onError("Verification failed: ${e.message}")
                            onProcessing(false)
                        }
                    } finally {
                        photoFile.delete()
                    }
                }
            }
            
            override fun onError(exception: ImageCaptureException) {
                onError("Capture failed: ${exception.message}")
                onProcessing(false)
                photoFile.delete()
            }
        }
    )
}

private fun floatArrayToByteArray(floatArray: FloatArray): ByteArray {
    val buffer = ByteBuffer.allocate(floatArray.size * 4)
    floatArray.forEach { buffer.putFloat(it) }
    return buffer.array()
}

private fun byteArrayToFloatArray(byteArray: ByteArray): FloatArray {
    val buffer = ByteBuffer.wrap(byteArray)
    return FloatArray(byteArray.size / 4) { buffer.float }
}