package com.mandelbulb.smartattendancesystem.ui

import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.mandelbulb.smartattendancesystem.data.AppDatabase
import com.mandelbulb.smartattendancesystem.data.AttendanceEntity
import com.mandelbulb.smartattendancesystem.data.UserPreferences
import com.mandelbulb.smartattendancesystem.network.NetworkMonitor
import com.mandelbulb.smartattendancesystem.network.PostgresApiService
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SingleUserMainScreen(
    onNavigateToSettings: () -> Unit,
    onLogout: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    
    val database = remember { AppDatabase.getInstance(context) }
    val userPreferences = remember { UserPreferences(context) }
    val postgresApi = remember { PostgresApiService() }
    
    var userName by remember { mutableStateOf("") }
    var userCode by remember { mutableStateOf("") }
    var userDepartment by remember { mutableStateOf("") }
    var isOnline by remember { mutableStateOf(true) }
    var lastCheckIn by remember { mutableStateOf<AttendanceEntity?>(null) }
    var lastCheckOut by remember { mutableStateOf<AttendanceEntity?>(null) }
    var todayAttendance by remember { mutableStateOf<List<AttendanceEntity>>(emptyList()) }
    var isProcessing by remember { mutableStateOf(false) }
    var showLogoutDialog by remember { mutableStateOf(false) }
    var showFaceVerification by remember { mutableStateOf(false) }
    var verificationCheckType by remember { mutableStateOf("") }
    var refreshTrigger by remember { mutableStateOf(0) }
    
    val dateFormatter = remember { SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()) }
    val timeFormatter = remember { SimpleDateFormat("HH:mm:ss", Locale.getDefault()) }
    val today = dateFormatter.format(Date())
    
    LaunchedEffect(refreshTrigger) {
        userName = userPreferences.userName.first()
        userCode = userPreferences.employeeCode.first()
        userDepartment = userPreferences.department.first()
        
        // Fetch fresh data from database
        val freshAttendance = database.attendanceDao().getAttendanceForDate(today)
        todayAttendance = freshAttendance
        
        // Update last check-in and check-out based on fresh data
        lastCheckIn = freshAttendance.lastOrNull { it.checkType == "IN" }
        lastCheckOut = freshAttendance.lastOrNull { it.checkType == "OUT" }
    }
    
    LaunchedEffect(Unit) {
        database.attendanceDao().getAllAttendanceFlow().collect { records ->
            val todayRecords = records.filter { 
                dateFormatter.format(Date(it.timestamp)) == today 
            }
            todayAttendance = todayRecords
            lastCheckIn = todayRecords.lastOrNull { it.checkType == "IN" }
            lastCheckOut = todayRecords.lastOrNull { it.checkType == "OUT" }
        }
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Smart Attendance") },
                actions = {
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings")
                    }
                    IconButton(onClick = { showLogoutDialog = true }) {
                        Icon(Icons.Default.ExitToApp, contentDescription = "Logout")
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
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = userName,
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = userCode,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        
                        Badge(
                            containerColor = if (isOnline) 
                                MaterialTheme.colorScheme.primary 
                            else MaterialTheme.colorScheme.secondary
                        ) {
                            Text(if (isOnline) "ONLINE" else "OFFLINE")
                        }
                    }
                    
                    HorizontalDivider()
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Department:",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = userDepartment,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Date:",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = today,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Card(
                    modifier = Modifier.weight(1f),
                    colors = CardDefaults.cardColors(
                        containerColor = if (lastCheckIn != null && lastCheckOut == null)
                            MaterialTheme.colorScheme.primaryContainer
                        else MaterialTheme.colorScheme.surface
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            Icons.Default.ArrowForward,
                            contentDescription = null,
                            modifier = Modifier.size(24.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "Check In",
                            style = MaterialTheme.typography.labelMedium,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                        Text(
                            text = lastCheckIn?.let { 
                                timeFormatter.format(Date(it.timestamp)) 
                            } ?: "--:--",
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                
                Card(
                    modifier = Modifier.weight(1f),
                    colors = CardDefaults.cardColors(
                        containerColor = if (lastCheckOut != null)
                            MaterialTheme.colorScheme.secondaryContainer
                        else MaterialTheme.colorScheme.surface
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            Icons.Default.ArrowBack,
                            contentDescription = null,
                            modifier = Modifier.size(24.dp),
                            tint = MaterialTheme.colorScheme.secondary
                        )
                        Text(
                            text = "Check Out",
                            style = MaterialTheme.typography.labelMedium,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                        Text(
                            text = lastCheckOut?.let { 
                                timeFormatter.format(Date(it.timestamp)) 
                            } ?: "--:--",
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = {
                        verificationCheckType = "IN"
                        showFaceVerification = true
                    },
                    modifier = Modifier.weight(1f),
                    enabled = !isProcessing && (lastCheckIn == null || (lastCheckOut != null && lastCheckOut!!.timestamp > lastCheckIn!!.timestamp))
                ) {
                    Icon(Icons.Default.ArrowForward, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("CHECK IN")
                }
                
                Button(
                    onClick = {
                        verificationCheckType = "OUT"
                        showFaceVerification = true
                    },
                    modifier = Modifier.weight(1f),
                    enabled = !isProcessing && lastCheckIn != null && (lastCheckOut == null || lastCheckIn!!.timestamp > lastCheckOut!!.timestamp)
                ) {
                    Icon(Icons.Default.ArrowBack, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("CHECK OUT")
                }
            }
            
            if (isProcessing) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text("Processing attendance...")
                    }
                }
            }
            
            Card(
                modifier = Modifier.fillMaxWidth(),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(
                        text = "Today's Activity",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    
                    if (todayAttendance.isEmpty()) {
                        Text(
                            text = "No activity recorded today",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 16.dp)
                        )
                    } else {
                        LazyColumn(
                            modifier = Modifier.heightIn(max = 200.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(todayAttendance.sortedByDescending { it.timestamp }) { record ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = if (record.checkType == "IN") 
                                                Icons.Default.ArrowForward else Icons.Default.ArrowBack,
                                            contentDescription = null,
                                            modifier = Modifier.size(20.dp),
                                            tint = if (record.checkType == "IN")
                                                MaterialTheme.colorScheme.primary
                                            else MaterialTheme.colorScheme.secondary
                                        )
                                        Text(
                                            text = timeFormatter.format(Date(record.timestamp)),
                                            style = MaterialTheme.typography.bodyMedium
                                        )
                                    }
                                    
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Badge {
                                            Text(record.checkType)
                                        }
                                        if (!record.synced) {
                                            Icon(
                                                Icons.Default.Info,
                                                contentDescription = "Not synced",
                                                modifier = Modifier.size(16.dp),
                                                tint = MaterialTheme.colorScheme.error
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    
    // Show face verification screen
    if (showFaceVerification) {
        FaceVerificationAttendanceScreen(
            checkType = verificationCheckType,
            onSuccess = {
                showFaceVerification = false
                Toast.makeText(
                    context, 
                    "${if (verificationCheckType == "IN") "Checked in" else "Checked out"} successfully with face verification", 
                    Toast.LENGTH_SHORT
                ).show()
                // Trigger refresh of attendance data with delay
                scope.launch {
                    // Give database time to update
                    kotlinx.coroutines.delay(500)
                    refreshTrigger++
                }
            },
            onCancel = {
                showFaceVerification = false
            }
        )
    }
    
    if (showLogoutDialog) {
        AlertDialog(
            onDismissRequest = { showLogoutDialog = false },
            title = { Text("Logout") },
            text = { Text("Are you sure you want to logout?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        scope.launch {
                            userPreferences.clearUserProfile()
                            onLogout()
                        }
                    }
                ) {
                    Text("Logout")
                }
            },
            dismissButton = {
                TextButton(onClick = { showLogoutDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

private suspend fun performCheckIn(
    context: android.content.Context,
    database: AppDatabase,
    postgresApi: PostgresApiService,
    userPreferences: UserPreferences,
    isOnline: Boolean,
    onProcessing: (Boolean) -> Unit,
    onSuccess: () -> Unit,
    onError: (String) -> Unit
) {
    onProcessing(true)
    
    try {
        val employeeId = userPreferences.employeeId.first()
        val employeeCode = userPreferences.employeeCode.first()
        val deviceId = Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
        
        val attendance = AttendanceEntity(
            employeeId = employeeId,
            employeeCode = employeeCode,
            checkType = "IN",
            timestamp = System.currentTimeMillis(),
            mode = if (isOnline) "ONLINE" else "OFFLINE",
            deviceId = deviceId,
            synced = false
        )
        
        database.attendanceDao().insert(attendance)
        
        if (isOnline) {
            val success = postgresApi.recordAttendance(
                employeeId = employeeId,
                checkType = "IN",
                deviceId = deviceId
            )
            
            if (success) {
                database.attendanceDao().markSynced(attendance.id)
            }
        }
        
        onSuccess()
    } catch (e: Exception) {
        onError("Check-in failed: ${e.message}")
    } finally {
        onProcessing(false)
    }
}

private suspend fun performCheckOut(
    context: android.content.Context,
    database: AppDatabase,
    postgresApi: PostgresApiService,
    userPreferences: UserPreferences,
    isOnline: Boolean,
    onProcessing: (Boolean) -> Unit,
    onSuccess: () -> Unit,
    onError: (String) -> Unit
) {
    onProcessing(true)
    
    try {
        val employeeId = userPreferences.employeeId.first()
        val employeeCode = userPreferences.employeeCode.first()
        val deviceId = Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
        
        val attendance = AttendanceEntity(
            employeeId = employeeId,
            employeeCode = employeeCode,
            checkType = "OUT",
            timestamp = System.currentTimeMillis(),
            mode = if (isOnline) "ONLINE" else "OFFLINE",
            deviceId = deviceId,
            synced = false
        )
        
        database.attendanceDao().insert(attendance)
        
        if (isOnline) {
            val success = postgresApi.recordAttendance(
                employeeId = employeeId,
                checkType = "OUT",
                deviceId = deviceId
            )
            
            if (success) {
                database.attendanceDao().markSynced(attendance.id)
            }
        }
        
        onSuccess()
    } catch (e: Exception) {
        onError("Check-out failed: ${e.message}")
    } finally {
        onProcessing(false)
    }
}