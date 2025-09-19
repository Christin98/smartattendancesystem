package com.mandelbulb.smartattendancesystem.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mandelbulb.smartattendancesystem.data.UserPreferences
import com.mandelbulb.smartattendancesystem.sync.SyncManager
import com.mandelbulb.smartattendancesystem.ui.components.AnimatedCard
import com.mandelbulb.smartattendancesystem.ui.components.AnimatedButton
import com.mandelbulb.smartattendancesystem.ui.components.calculateStaggeredDelay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SyncScreen(
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val syncManager = remember { SyncManager(context) }
    val syncStatus by syncManager.syncStatus.collectAsState()
    val networkStatus by syncManager.networkStatus.collectAsState()
    val userPreferences = remember { UserPreferences(context) }

    var pendingRecordsCount by remember { mutableStateOf(0) }
    var isManualSyncing by remember { mutableStateOf(false) }
    var animationsEnabled by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        userPreferences.animationsEnabled.collect { enabled ->
            animationsEnabled = enabled
        }
    }

    // Animation for sync icon rotation
    val infiniteTransition = rememberInfiniteTransition(label = "sync_rotation")
    val rotationAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "sync_rotation"
    )

    LaunchedEffect(Unit) {
        pendingRecordsCount = syncManager.getPendingRecordsCount()
    }

    // Update pending records count when sync status changes
    LaunchedEffect(syncStatus) {
        if (!syncStatus.isSyncing) {
            pendingRecordsCount = syncManager.getPendingRecordsCount()
            isManualSyncing = false
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            syncManager.cleanup()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Sync Status") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
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
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Network Status Card
            AnimatedCard(
                modifier = Modifier.fillMaxWidth(),
                animationsEnabled = animationsEnabled,
                delayMillis = calculateStaggeredDelay(0)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Network Status",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = when (networkStatus) {
                                SyncManager.NetworkStatus.ONLINE -> "Connected"
                                SyncManager.NetworkStatus.OFFLINE -> "Disconnected"
                                else -> "Unknown"
                            },
                            style = MaterialTheme.typography.bodyLarge,
                            color = when (networkStatus) {
                                SyncManager.NetworkStatus.ONLINE -> MaterialTheme.colorScheme.primary
                                SyncManager.NetworkStatus.OFFLINE -> MaterialTheme.colorScheme.error
                                else -> MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        )
                    }

                    Icon(
                        imageVector = when (networkStatus) {
                            SyncManager.NetworkStatus.ONLINE -> Icons.Default.CheckCircle
                            SyncManager.NetworkStatus.OFFLINE -> Icons.Default.Close
                            else -> Icons.Default.Info
                        },
                        contentDescription = null,
                        modifier = Modifier.size(32.dp),
                        tint = when (networkStatus) {
                            SyncManager.NetworkStatus.ONLINE -> MaterialTheme.colorScheme.primary
                            SyncManager.NetworkStatus.OFFLINE -> MaterialTheme.colorScheme.error
                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                }
            }

            // Sync Status Card
            AnimatedCard(
                modifier = Modifier.fillMaxWidth(),
                animationsEnabled = animationsEnabled,
                delayMillis = calculateStaggeredDelay(1)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Sync Status",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )

                        if (syncStatus.isSyncing || isManualSyncing) {
                            Icon(
                                Icons.Default.Refresh,
                                contentDescription = "Syncing",
                                modifier = Modifier
                                    .size(24.dp)
                                    .rotate(rotationAngle),
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }

                    // Progress bar
                    if (syncStatus.isSyncing) {
                        LinearProgressIndicator(
                            progress = { syncStatus.progress },
                            modifier = Modifier.fillMaxWidth(),
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }

                    // Status message
                    Text(
                        text = syncStatus.message,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (syncStatus.isSyncing)
                            MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurface
                    )

                    // Last sync time
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Last sync:",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = syncManager.getFormattedLastSyncTime(),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Statistics Cards
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Pending Records
                AnimatedCard(
                    modifier = Modifier.weight(1f),
                    animationsEnabled = animationsEnabled,
                    delayMillis = calculateStaggeredDelay(2)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = pendingRecordsCount.toString(),
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            color = if (pendingRecordsCount > 0)
                                MaterialTheme.colorScheme.onErrorContainer
                            else MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Text(
                            text = "Pending",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (pendingRecordsCount > 0)
                                MaterialTheme.colorScheme.onErrorContainer
                            else MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }

                // Synced Records (current session)
                AnimatedCard(
                    modifier = Modifier.weight(1f),
                    animationsEnabled = animationsEnabled,
                    delayMillis = calculateStaggeredDelay(3)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = syncStatus.syncedRecords.toString(),
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                        Text(
                            text = "Synced",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                }

                // Failed Records (current session)
                AnimatedCard(
                    modifier = Modifier.weight(1f),
                    animationsEnabled = animationsEnabled,
                    delayMillis = calculateStaggeredDelay(4)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = syncStatus.failedRecords.toString(),
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            color = if (syncStatus.failedRecords > 0)
                                MaterialTheme.colorScheme.onErrorContainer
                            else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "Failed",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (syncStatus.failedRecords > 0)
                                MaterialTheme.colorScheme.onErrorContainer
                            else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Manual Sync Button
            AnimatedButton(
                onClick = {
                    if (!syncStatus.isSyncing) {
                        isManualSyncing = true
                        scope.launch {
                            syncManager.syncNow("Manual sync from UI")
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = networkStatus == SyncManager.NetworkStatus.ONLINE && !syncStatus.isSyncing,
                animationsEnabled = animationsEnabled
            ) {
                if (syncStatus.isSyncing || isManualSyncing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Syncing...")
                } else {
                    Icon(Icons.Default.Refresh, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Sync Now")
                }
            }

            // Auto-sync info
            AnimatedCard(
                modifier = Modifier.fillMaxWidth(),
                animationsEnabled = animationsEnabled,
                delayMillis = calculateStaggeredDelay(5)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            Icons.Default.Info,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "Auto-Sync Information",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Text(
                        text = "• Data syncs automatically every 30 seconds when connected to network",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Text(
                        text = "• Attendance records are immediately synced when network becomes available",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Text(
                        text = "• All data is stored locally and will sync when connection is restored",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Connection status details
            if (networkStatus == SyncManager.NetworkStatus.OFFLINE) {
                AnimatedCard(
                    modifier = Modifier.fillMaxWidth(),
                    animationsEnabled = animationsEnabled,
                    delayMillis = calculateStaggeredDelay(6)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp),
                                tint = MaterialTheme.colorScheme.onErrorContainer
                            )
                            Text(
                                text = "Offline Mode",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }

                        Text(
                            text = "You are currently offline. Attendance data will be stored locally and synced automatically when connection is restored.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
            }
        }
    }
}