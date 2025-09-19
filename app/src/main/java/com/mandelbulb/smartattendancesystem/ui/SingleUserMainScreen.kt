package com.mandelbulb.smartattendancesystem.ui

import android.provider.Settings
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mandelbulb.smartattendancesystem.data.AppDatabase
import com.mandelbulb.smartattendancesystem.data.AttendanceEntity
import com.mandelbulb.smartattendancesystem.data.UserPreferences
import com.mandelbulb.smartattendancesystem.network.PostgresApiService
import com.mandelbulb.smartattendancesystem.sync.SyncManager
import com.mandelbulb.smartattendancesystem.ui.components.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SingleUserMainScreen(
    onNavigateToSettings: () -> Unit,
    onNavigateToSync: () -> Unit = {},
    onLogout: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    
    val database = remember { AppDatabase.getInstance(context) }
    val userPreferences = remember { UserPreferences(context) }
    val postgresApi = remember { PostgresApiService() }
    val syncManager = remember { SyncManager(context) }
    
    var userName by remember { mutableStateOf("") }
    var userCode by remember { mutableStateOf("") }
    var userDepartment by remember { mutableStateOf("") }
    var isOnline by remember { mutableStateOf(true) }
    val syncStatus by syncManager.syncStatus.collectAsState()
    val networkStatus by syncManager.networkStatus.collectAsState()
    var pendingRecordsCount by remember { mutableIntStateOf(0) }
    var lastCheckIn by remember { mutableStateOf<AttendanceEntity?>(null) }
    var lastCheckOut by remember { mutableStateOf<AttendanceEntity?>(null) }
    var todayAttendance by remember { mutableStateOf<List<AttendanceEntity>>(emptyList()) }
    var isProcessing by remember { mutableStateOf(false) }
    var showLogoutDialog by remember { mutableStateOf(false) }
    var showFaceVerification by remember { mutableStateOf(false) }
    var verificationCheckType by remember { mutableStateOf("") }
    var refreshTrigger by remember { mutableIntStateOf(0) }
    var totalWorkingHours by remember { mutableStateOf("00:00:00") }
    var animationsEnabled by remember { mutableStateOf(true) }
    
    val dateFormatter = remember { SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()) }
    val timeFormatter = remember { SimpleDateFormat("HH:mm:ss", Locale.getDefault()) }
    val today = dateFormatter.format(Date())

    // Function to calculate total working hours
    fun calculateTotalHours(attendance: List<AttendanceEntity>): String {
        val sortedRecords = attendance.sortedBy { it.timestamp }
        var totalMillis = 0L
        var lastCheckInTime: Long? = null

        for (record in sortedRecords) {
            when (record.checkType) {
                "IN" -> {
                    lastCheckInTime = record.timestamp
                }
                "OUT" -> {
                    if (lastCheckInTime != null) {
                        totalMillis += record.timestamp - lastCheckInTime
                        lastCheckInTime = null
                    }
                }
            }
        }

        // If currently checked in, add time until now
        if (lastCheckInTime != null) {
            totalMillis += System.currentTimeMillis() - lastCheckInTime
        }

        // Convert milliseconds to HH:MM:SS format
        val hours = totalMillis / (1000 * 60 * 60)
        val minutes = (totalMillis / (1000 * 60)) % 60
        val seconds = (totalMillis / 1000) % 60
        return String.format("%02d:%02d:%02d", hours, minutes, seconds)
    }
    
    LaunchedEffect(refreshTrigger) {
        userName = userPreferences.userName.first()
        userCode = userPreferences.employeeCode.first()
        userDepartment = userPreferences.department.first()
        animationsEnabled = userPreferences.animationsEnabled.first()

        // Fetch fresh data from database
        val freshAttendance = database.attendanceDao().getAttendanceForDate(today)
        todayAttendance = freshAttendance

        // Get the most recent check-in and check-out
        val sortedAttendance = freshAttendance.sortedBy { it.timestamp }
        lastCheckIn = sortedAttendance.lastOrNull { it.checkType == "IN" }
        lastCheckOut = sortedAttendance.lastOrNull { it.checkType == "OUT" }

        // Calculate total working hours
        totalWorkingHours = calculateTotalHours(freshAttendance)

        // Update pending records count
        pendingRecordsCount = syncManager.getPendingRecordsCount()
    }
    
    LaunchedEffect(Unit) {
        database.attendanceDao().getAllAttendanceFlow().collect { records ->
            val todayRecords = records.filter {
                dateFormatter.format(Date(it.timestamp)) == today
            }
            todayAttendance = todayRecords

            // Get the most recent check-in and check-out
            val sortedRecords = todayRecords.sortedBy { it.timestamp }
            lastCheckIn = sortedRecords.lastOrNull { it.checkType == "IN" }
            lastCheckOut = sortedRecords.lastOrNull { it.checkType == "OUT" }

            // Calculate total working hours
            totalWorkingHours = calculateTotalHours(todayRecords)
        }
    }

    // Update network status based on sync manager
    LaunchedEffect(networkStatus) {
        isOnline = networkStatus == SyncManager.NetworkStatus.ONLINE
    }

    // Auto-update working hours every second if currently checked in
    LaunchedEffect(lastCheckIn, lastCheckOut) {
        val isCurrentlyWorking = lastCheckIn != null &&
            (lastCheckOut == null || lastCheckIn!!.timestamp > lastCheckOut!!.timestamp)

        if (isCurrentlyWorking) {
            while (true) {
                kotlinx.coroutines.delay(1000) // Update every second
                totalWorkingHours = calculateTotalHours(todayAttendance)
            }
        }
    }

    // Update pending records count when sync status changes
    LaunchedEffect(syncStatus) {
        if (!syncStatus.isSyncing) {
            pendingRecordsCount = syncManager.getPendingRecordsCount()
        }
    }

    // Cleanup sync manager
    DisposableEffect(Unit) {
        onDispose {
            syncManager.cleanup()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Smart Attendance") },
                actions = {
                    // Sync status indicator
                    IconButton(onClick = onNavigateToSync) {
                        BadgedBox(
                            badge = {
                                if (pendingRecordsCount > 0) {
                                    Badge {
                                        Text("$pendingRecordsCount")
                                    }
                                }
                            }
                        ) {
                            Icon(
                                Icons.Default.Refresh,
                                contentDescription = "Sync Status",
                                tint = when {
                                    syncStatus.isSyncing -> MaterialTheme.colorScheme.primary
                                    pendingRecordsCount > 0 -> MaterialTheme.colorScheme.error
                                    else -> MaterialTheme.colorScheme.onSurface
                                }
                            )
                        }
                    }
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
            // User Profile Card
            AnimatedCard(
                modifier = Modifier.fillMaxWidth(),
                animationsEnabled = animationsEnabled,
                delayMillis = 0
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

            // Sync Status Banner
            if (syncStatus.isSyncing || pendingRecordsCount > 0) {
                AnimatedCard(
                    modifier = Modifier.fillMaxWidth(),
                    animationsEnabled = animationsEnabled,
                    delayMillis = 50
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                if (syncStatus.isSyncing)
                                    MaterialTheme.colorScheme.primaryContainer
                                else MaterialTheme.colorScheme.errorContainer
                            )
                    ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            if (syncStatus.isSyncing) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            } else {
                                Icon(
                                    Icons.Default.Info,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                    tint = MaterialTheme.colorScheme.error
                                )
                            }

                            Column {
                                Text(
                                    text = if (syncStatus.isSyncing) "Syncing..." else "Pending Sync",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    text = if (syncStatus.isSyncing)
                                        syncStatus.message
                                    else "$pendingRecordsCount records waiting to sync",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontSize = 10.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        TextButton(
                            onClick = onNavigateToSync,
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = "View",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                    }
                }
            }

            // Check-in/Check-out Status Cards
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                AnimatedCard(
                    modifier = Modifier.weight(1f),
                    animationsEnabled = animationsEnabled,
                    delayMillis = 100
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                if (lastCheckIn != null && lastCheckOut == null)
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
                }

                AnimatedCard(
                    modifier = Modifier.weight(1f),
                    animationsEnabled = animationsEnabled,
                    delayMillis = 150
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                if (lastCheckOut != null)
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
            }

            // Total Working Hours Card
            AnimatedCard(
                modifier = Modifier.fillMaxWidth(),
                animationsEnabled = animationsEnabled,
                delayMillis = 200
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.tertiaryContainer)
                ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            Icons.Default.DateRange,
                            contentDescription = null,
                            modifier = Modifier.size(28.dp),
                            tint = MaterialTheme.colorScheme.onTertiaryContainer
                        )

                        Column {
                            Text(
                                text = "Total Working Hours",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.8f)
                            )

                            if (animationsEnabled) {
                                // Parse HH:MM:SS and display with animation
                                val timeParts = totalWorkingHours.split(":")
                                val hours = timeParts.getOrNull(0)?.toIntOrNull() ?: 0
                                val minutes = timeParts.getOrNull(1)?.toIntOrNull() ?: 0
                                val seconds = timeParts.getOrNull(2)?.toIntOrNull() ?: 0

                                AnimatedDigitalClock(
                                    hours = hours,
                                    minutes = minutes,
                                    seconds = seconds,
                                    fontSize = 24.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                                    animationsEnabled = true
                                )
                            } else {
                                Text(
                                    text = totalWorkingHours,
                                    style = MaterialTheme.typography.headlineMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onTertiaryContainer
                                )
                            }
                        }
                    }

                    // Status indicator for currently working
                    if (lastCheckIn != null && (lastCheckOut == null || lastCheckIn!!.timestamp > lastCheckOut!!.timestamp)) {
                        Badge(
                            containerColor = MaterialTheme.colorScheme.primary
                        ) {
                            Text("WORKING", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Check-in button logic:
                // Enable if: No check-in today OR last check-out is after last check-in
                val canCheckIn = lastCheckIn == null ||
                    (lastCheckOut != null && lastCheckOut!!.timestamp > lastCheckIn!!.timestamp)

                // Check-out button logic:
                // Enable if: Has checked in AND (no check-out OR last check-in is after last check-out)
                val canCheckOut = lastCheckIn != null &&
                    (lastCheckOut == null || lastCheckIn!!.timestamp > lastCheckOut!!.timestamp)

                AnimatedButton(
                    onClick = {
                        verificationCheckType = "IN"
                        showFaceVerification = true
                    },
                    modifier = Modifier.weight(1f),
                    enabled = !isProcessing && !showFaceVerification && canCheckIn,
                    animationsEnabled = animationsEnabled
                ) {
                    Icon(Icons.Default.ArrowForward, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("CHECK IN")
                }

                AnimatedButton(
                    onClick = {
                        verificationCheckType = "OUT"
                        showFaceVerification = true
                    },
                    modifier = Modifier.weight(1f),
                    enabled = !isProcessing && !showFaceVerification && canCheckOut,
                    animationsEnabled = animationsEnabled
                ) {
                    Icon(Icons.Default.ArrowBack, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("CHECK OUT")
                }
            }
            
            if (isProcessing) {
                AnimatedCard(
                    modifier = Modifier.fillMaxWidth(),
                    animationsEnabled = animationsEnabled,
                    delayMillis = 0
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
            
            // Today's Activity Card
            AnimatedCard(
                modifier = Modifier.fillMaxWidth(),
                animationsEnabled = animationsEnabled,
                delayMillis = 300
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
                                val locationAddress = try {
                                    record.location?.let {
                                        val json = JSONObject(it)
                                        json.optString("address", null)
                                            ?: "Lat: %.4f, Lon: %.4f".format(
                                                json.getDouble("lat"),
                                                json.getDouble("lon")
                                            )
                                    }
                                } catch (e: Exception) {
                                    null
                                }
                                Column(
                                    modifier = Modifier.fillMaxWidth()
                                ) {
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

                                    // Display location if available
                                    locationAddress?.let { address ->
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(start = 28.dp, top = 4.dp),
                                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(
                                                Icons.Default.LocationOn,
                                                contentDescription = "Location",
                                                modifier = Modifier.size(14.dp),
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                            Text(
                                                text = address,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                maxLines = 1,
                                                modifier = Modifier.weight(1f)
                                            )
                                        }
                                    }
                                }

                                if (record != todayAttendance.sortedByDescending { it.timestamp }.last()) {
                                    HorizontalDivider(
                                        modifier = Modifier.padding(vertical = 8.dp),
                                        thickness = 0.5.dp
                                    )
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

                // Immediately update the UI state
                scope.launch {
                    // Small delay to ensure database write is complete
                    kotlinx.coroutines.delay(200)

                    // Force refresh of attendance data
                    val freshAttendance = database.attendanceDao().getAttendanceForDate(today)
                    todayAttendance = freshAttendance

                    // Update last check-in and check-out
                    val sortedAttendance = freshAttendance.sortedBy { it.timestamp }
                    lastCheckIn = sortedAttendance.lastOrNull { it.checkType == "IN" }
                    lastCheckOut = sortedAttendance.lastOrNull { it.checkType == "OUT" }

                    // Calculate total working hours
                    totalWorkingHours = calculateTotalHours(freshAttendance)

                    // Also trigger the refresh for other UI elements
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