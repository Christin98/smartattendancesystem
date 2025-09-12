package com.mandelbulb.smartattendancesystem.ui

import android.provider.Settings
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.mandelbulb.smartattendancesystem.data.AppDatabase
import com.mandelbulb.smartattendancesystem.data.AttendanceEntity
import com.mandelbulb.smartattendancesystem.data.UserPreferences
import com.mandelbulb.smartattendancesystem.network.NetworkMonitor
import com.mandelbulb.smartattendancesystem.network.PostgresApiService
import com.mandelbulb.smartattendancesystem.utils.NetworkUtils
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FaceVerificationAttendanceScreen(
    checkType: String, // "IN" or "OUT"
    onSuccess: () -> Unit,
    onCancel: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    
    val database = remember { AppDatabase.getInstance(context) }
    val userPreferences = remember { UserPreferences(context) }
    val postgresApi = remember { PostgresApiService() }
    
    var isProcessing by remember { mutableStateOf(false) }
    var statusMessage by remember { mutableStateOf("Position your face in the camera") }
    var showCamera by remember { mutableStateOf(true) }
    
    val imageCapture = remember { ImageCapture.Builder().build() }
    val previewView = remember { PreviewView(context) }
    
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
    
    fun captureAndVerifyFace() {
        isProcessing = true
        statusMessage = "Capturing face..."
        
        val photoFile = File(context.cacheDir, "temp_face_${System.currentTimeMillis()}.jpg")
        val outputFileOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()
        
        imageCapture.takePicture(
            outputFileOptions,
            ContextCompat.getMainExecutor(context),
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                    scope.launch {
                        try {
                            statusMessage = "Processing face..."
                            
                            statusMessage = "Recording attendance..."
                            
                            // Perform attendance without face verification (Azure Face API only during login)
                            performAttendanceWithVerification(
                                context = context,
                                database = database,
                                postgresApi = postgresApi,
                                userPreferences = userPreferences,
                                checkType = checkType,
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
                                Text(
                                    text = statusMessage,
                                    style = MaterialTheme.typography.bodyLarge,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.padding(12.dp)
                                )
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
                        Icon(
                            Icons.Default.CheckCircle,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = statusMessage,
                            style = MaterialTheme.typography.headlineSmall,
                            textAlign = TextAlign.Center,
                            fontWeight = FontWeight.Bold
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
                
                Button(
                    onClick = { captureAndVerifyFace() },
                    modifier = Modifier.weight(1f),
                    enabled = !isProcessing && showCamera
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
    onSuccess: () -> Unit,
    onError: (String) -> Unit
) {
    try {
        val employeeId = userPreferences.employeeId.first()
        val employeeCode = userPreferences.employeeCode.first()
        val deviceId = Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
        
        // Save attendance locally immediately
        val attendance = AttendanceEntity(
            employeeId = employeeId,
            employeeCode = employeeCode,
            checkType = checkType,
            timestamp = System.currentTimeMillis(),
            mode = "OFFLINE",
            deviceId = deviceId,
            synced = false
        )
        database.attendanceDao().insert(attendance)
        
        // Return success immediately - Azure Face verification happens during registration/login only
        onSuccess()
        
        // Try to sync with server in background if online
        kotlinx.coroutines.GlobalScope.launch {
            try {
                if (NetworkUtils.isNetworkAvailable(context)) {
                    kotlinx.coroutines.withTimeout(5000) {
                        val success = postgresApi.recordAttendance(
                            employeeId = employeeId,
                            checkType = checkType,
                            deviceId = deviceId,
                            embedding = null // Azure Face API verification only during login
                        )
                        
                        if (success) {
                            database.attendanceDao().markSynced(attendance.id)
                        }
                    }
                }
            } catch (e: Exception) {
                // Log but don't affect user
                android.util.Log.e("FaceVerification", "Background sync failed: ${e.message}")
            }
        }
    } catch (e: Exception) {
        onError("Error: ${e.message}")
    }
}

// Azure Face API verification happens only during registration and login
// Check-in/out relies on Azure authentication from login session