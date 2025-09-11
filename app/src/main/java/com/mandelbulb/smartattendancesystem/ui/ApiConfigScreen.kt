package com.mandelbulb.smartattendancesystem.ui

import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.mandelbulb.smartattendancesystem.data.AppDatabase
import com.mandelbulb.smartattendancesystem.data.AppSettingsEntity
import com.mandelbulb.smartattendancesystem.sync.AttendanceSyncService
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ApiConfigScreen(
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val database = remember { AppDatabase.getInstance(context) }
    
    var apiBaseUrl by remember { mutableStateOf("") }
    var azureEndpoint by remember { mutableStateOf("") }
    var azureSubscriptionKey by remember { mutableStateOf("") }
    var autoSync by remember { mutableStateOf(true) }
    var offlineMode by remember { mutableStateOf(false) }
    var showApiKey by remember { mutableStateOf(false) }
    var showAzureKey by remember { mutableStateOf(false) }
    var isSaving by remember { mutableStateOf(false) }
    var syncStatus by remember { mutableStateOf<String?>(null) }
    
    LaunchedEffect(Unit) {
        scope.launch {
            val settings = database.appSettingsDao().getSettings()
            settings?.let {
                apiBaseUrl = it.apiBaseUrl
                azureEndpoint = it.azureEndpoint
                azureSubscriptionKey = it.azureSubscriptionKey
                autoSync = it.autoSync
                offlineMode = it.offlineMode
            }
        }
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("API Configuration") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    TextButton(
                        onClick = {
                            scope.launch {
                                isSaving = true
                                val settings = AppSettingsEntity(
                                    id = 1,
                                    apiBaseUrl = apiBaseUrl,
                                    azureEndpoint = azureEndpoint,
                                    azureSubscriptionKey = azureSubscriptionKey,
                                    autoSync = autoSync,
                                    offlineMode = offlineMode,
                                    lastUpdated = System.currentTimeMillis()
                                )
                                database.appSettingsDao().insert(settings)
                                Toast.makeText(context, "Settings saved", Toast.LENGTH_SHORT).show()
                                isSaving = false
                            }
                        },
                        enabled = !isSaving
                    ) {
                        if (isSaving) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp
                            )
                        } else {
                            Text("SAVE")
                        }
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Backend API Settings",
                        style = MaterialTheme.typography.titleMedium
                    )
                    
                    OutlinedTextField(
                        value = apiBaseUrl,
                        onValueChange = { apiBaseUrl = it },
                        label = { Text("API Base URL") },
                        placeholder = { Text("https://your-api.azurewebsites.net/api") },
                        leadingIcon = {
                            Icon(Icons.Default.Home, contentDescription = null)
                        },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    
                    Text(
                        text = "Enter the base URL of your PostgreSQL API backend",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            
            Card(
                modifier = Modifier.fillMaxWidth(),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Azure Face API Settings",
                        style = MaterialTheme.typography.titleMedium
                    )
                    
                    OutlinedTextField(
                        value = azureEndpoint,
                        onValueChange = { azureEndpoint = it },
                        label = { Text("Azure Endpoint") },
                        placeholder = { Text("https://your-resource.cognitiveservices.azure.com") },
                        leadingIcon = {
                            Icon(Icons.Default.Info, contentDescription = null)
                        },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    
                    OutlinedTextField(
                        value = azureSubscriptionKey,
                        onValueChange = { azureSubscriptionKey = it },
                        label = { Text("Subscription Key") },
                        leadingIcon = {
                            Icon(Icons.Default.Lock, contentDescription = null)
                        },
                        trailingIcon = {
                            IconButton(onClick = { showAzureKey = !showAzureKey }) {
                                Icon(
                                    imageVector = if (showAzureKey) 
                                        Icons.Default.Done 
                                    else Icons.Default.Add,
                                    contentDescription = if (showAzureKey) 
                                        "Hide key" 
                                    else "Show key"
                                )
                            }
                        },
                        visualTransformation = if (showAzureKey) 
                            VisualTransformation.None 
                        else PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    
                    Text(
                        text = "Azure Face API credentials for online face verification",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            
            Card(
                modifier = Modifier.fillMaxWidth(),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Sync Settings",
                        style = MaterialTheme.typography.titleMedium
                    )
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Auto Sync",
                                style = MaterialTheme.typography.bodyLarge
                            )
                            Text(
                                text = "Automatically sync attendance when online",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = autoSync,
                            onCheckedChange = { autoSync = it }
                        )
                    }
                    
                    HorizontalDivider()
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Offline Mode",
                                style = MaterialTheme.typography.bodyLarge
                            )
                            Text(
                                text = "Force offline mode even when connected",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = offlineMode,
                            onCheckedChange = { offlineMode = it }
                        )
                    }
                }
            }
            
            Card(
                modifier = Modifier.fillMaxWidth(),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Manual Sync",
                        style = MaterialTheme.typography.titleMedium
                    )
                    
                    Button(
                        onClick = {
                            scope.launch {
                                syncStatus = "Syncing..."
                                val syncService = AttendanceSyncService(context)
                                val result = syncService.syncPendingAttendance()
                                syncStatus = if (result) {
                                    "Sync completed successfully"
                                } else {
                                    "Sync failed. Please check your connection."
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Sync Now")
                    }
                    
                    syncStatus?.let {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = if (it.contains("success"))
                                    MaterialTheme.colorScheme.primaryContainer
                                else MaterialTheme.colorScheme.errorContainer
                            )
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = if (it.contains("success"))
                                        Icons.Default.CheckCircle
                                    else Icons.Default.Close,
                                    contentDescription = null,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = it,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                        }
                    }
                    
                    OutlinedButton(
                        onClick = {
                            scope.launch {
                                val unsyncedCount = database.attendanceDao().getUnsynced().size
                                syncStatus = "Unsynced records: $unsyncedCount"
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Info, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Check Sync Status")
                    }
                }
            }
        }
    }
}