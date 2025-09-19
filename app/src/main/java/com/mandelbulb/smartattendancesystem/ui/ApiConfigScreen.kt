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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.mandelbulb.smartattendancesystem.BuildConfig
import com.mandelbulb.smartattendancesystem.data.AppDatabase
import com.mandelbulb.smartattendancesystem.data.AppSettingsEntity
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
                    // Only show Save button in debug builds
                    if (BuildConfig.DEBUG) {
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
            // Show info card for release builds
            if (!BuildConfig.DEBUG) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.tertiaryContainer
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            Icons.Default.Info,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onTertiaryContainer,
                            modifier = Modifier.size(24.dp)
                        )
                        Column {
                            Text(
                                text = "Production Build",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onTertiaryContainer,
                                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                            )
                            Text(
                                text = "Advanced settings are hidden in production builds for security.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.8f)
                            )
                        }
                    }
                }
            }
            // Only show Backend API Settings in debug builds
            if (BuildConfig.DEBUG) {
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
            }
            
            // Only show Azure Face API Settings in debug builds
            if (BuildConfig.DEBUG) {
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
                                            Icons.Default.CheckCircle
                                        else Icons.Default.AddCircle,
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
            }
        }
    }
}