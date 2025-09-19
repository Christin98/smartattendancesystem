package com.mandelbulb.smartattendancesystem.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mandelbulb.smartattendancesystem.BuildConfig
import com.mandelbulb.smartattendancesystem.data.AppDatabase
import com.mandelbulb.smartattendancesystem.data.AppSettingsEntity
import com.mandelbulb.smartattendancesystem.data.UserPreferences
import com.mandelbulb.smartattendancesystem.sync.SyncManager
import com.mandelbulb.smartattendancesystem.ui.components.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit,
    onNavigateToApiConfig: () -> Unit = {},
    onNavigateToSync: () -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val userPreferences = remember { UserPreferences(context) }
    val database = remember { AppDatabase.getInstance(context) }

    var animationsEnabled by remember { mutableStateOf(true) }
    var userName by remember { mutableStateOf("") }
    var employeeCode by remember { mutableStateOf("") }
    var department by remember { mutableStateOf("") }
    var autoSync by remember { mutableStateOf(true) }
    var syncStatus by remember { mutableStateOf<String?>(null) }
    var unsyncedCount by remember { mutableStateOf(0) }

    LaunchedEffect(Unit) {
        animationsEnabled = userPreferences.animationsEnabled.first()
        userName = userPreferences.userName.first()
        employeeCode = userPreferences.employeeCode.first()
        department = userPreferences.department.first()

        // Load sync settings
        val settings = database.appSettingsDao().getSettings()
        settings?.let {
            autoSync = it.autoSync
        }

        // Check unsynced count
        unsyncedCount = database.attendanceDao().getUnsynced().size
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    TypingText(
                        text = "Settings",
                        fontSize = 20.sp,
                        animationsEnabled = animationsEnabled,
                        typingSpeed = 30L
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(vertical = 16.dp)
        ) {
            // User Profile Section
            item {
                AnimatedCard(
                    animationsEnabled = animationsEnabled,
                    delayMillis = 0
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Surface(
                                modifier = Modifier.size(48.dp),
                                shape = MaterialTheme.shapes.medium,
                                color = MaterialTheme.colorScheme.primaryContainer
                            ) {
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier.fillMaxSize()
                                ) {
                                    Icon(
                                        Icons.Default.Person,
                                        contentDescription = null,
                                        modifier = Modifier.size(32.dp),
                                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                }
                            }

                            Column {
                                Text(
                                    text = userName,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = employeeCode,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = department,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }

            // Animations Settings
            item {
                AnimatedCard(
                    animationsEnabled = animationsEnabled,
                    delayMillis = 100
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "Display Settings",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )

                        HorizontalDivider()

                        // Animation Toggle with Live Preview
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (animationsEnabled) {
                                    PulsingIcon(
                                        icon = {
                                            Icon(
                                                Icons.Default.PlayArrow,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.primary
                                            )
                                        },
                                        animationsEnabled = true
                                    )
                                } else {
                                    Icon(
                                        Icons.Default.PlayArrow,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }

                                Column {
                                    Text(
                                        text = "Animations",
                                        style = MaterialTheme.typography.bodyLarge
                                    )
                                    Text(
                                        text = if (animationsEnabled) "Enhanced visual effects" else "Simple mode",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            Switch(
                                checked = animationsEnabled,
                                onCheckedChange = { enabled ->
                                    scope.launch {
                                        animationsEnabled = enabled
                                        userPreferences.setAnimationsEnabled(enabled)
                                    }
                                }
                            )
                        }

                        // Animation Preview
                        if (animationsEnabled) {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                                )
                            ) {
                                Column(
                                    modifier = Modifier.padding(12.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text(
                                        text = "Preview",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )

                                    Spacer(modifier = Modifier.height(8.dp))

                                    // Show sample animated clock
                                    AnimatedDigitalClock(
                                        hours = 8,
                                        minutes = 30,
                                        seconds = 45,
                                        fontSize = 24.sp,
                                        animationsEnabled = true
                                    )

                                    Spacer(modifier = Modifier.height(8.dp))

                                    // Sample animated counter
                                    var counter by remember { mutableStateOf(0) }
                                    LaunchedEffect(Unit) {
                                        while (true) {
                                            kotlinx.coroutines.delay(3000)
                                            counter = (counter + 1) % 100
                                        }
                                    }

                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "Counter:",
                                            style = MaterialTheme.typography.bodySmall
                                        )
                                        AnimatedCounter(
                                            count = counter,
                                            fontSize = 18.sp,
                                            animationsEnabled = true
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Quick Actions Section
            item {
                AnimatedCard(
                    animationsEnabled = animationsEnabled,
                    delayMillis = 200
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "Quick Actions",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )

                        HorizontalDivider()

                        SettingsMenuItem(
                            icon = Icons.Default.Settings,
                            title = "API Configuration",
                            subtitle = "Configure backend settings",
                            onClick = onNavigateToApiConfig,
                            animationsEnabled = animationsEnabled
                        )

                        SettingsMenuItem(
                            icon = Icons.Default.Refresh,
                            title = "Sync Management",
                            subtitle = "View and manage pending syncs",
                            onClick = onNavigateToSync,
                            animationsEnabled = animationsEnabled
                        )

                        SettingsMenuItem(
                            icon = Icons.Default.Delete,
                            title = "Clear Cache",
                            subtitle = "Free up storage space",
                            onClick = {
                                // TODO: Implement cache clearing
                            },
                            animationsEnabled = animationsEnabled
                        )
                    }
                }
            }

            // Sync Settings Section
            item {
                AnimatedCard(
                    animationsEnabled = animationsEnabled,
                    delayMillis = 250
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "Sync Management",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )

                        HorizontalDivider()

                        // Auto Sync Toggle
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
                                onCheckedChange = { enabled ->
                                    scope.launch {
                                        autoSync = enabled
                                        val settings = database.appSettingsDao().getSettings() ?: AppSettingsEntity(
                                            id = 1,
                                            apiBaseUrl = "",
                                            azureEndpoint = "",
                                            azureSubscriptionKey = "",
                                            autoSync = enabled,
                                            offlineMode = false,
                                            lastUpdated = System.currentTimeMillis()
                                        )
                                        database.appSettingsDao().insert(settings.copy(autoSync = enabled))
                                    }
                                }
                            )
                        }

                        // Sync Status
                        if (unsyncedCount > 0) {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.errorContainer
                                )
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        Icons.Default.Info,
                                        contentDescription = null,
                                        modifier = Modifier.size(20.dp),
                                        tint = MaterialTheme.colorScheme.error
                                    )
                                    Text(
                                        text = "$unsyncedCount unsynced records",
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                }
                            }
                        }

                        // Manual Sync Button
                        Button(
                            onClick = {
                                scope.launch {
                                    syncStatus = "Syncing..."
                                    val syncManager = SyncManager(context)
                                    val result = syncManager.syncNow("Manual sync from settings")
                                    syncStatus = if (result) {
                                        "Sync completed successfully"
                                    } else {
                                        "Sync failed. Please check your connection."
                                    }
                                    // Update unsynced count
                                    unsyncedCount = database.attendanceDao().getUnsynced().size
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Sync Now")
                        }

                        // Sync result message
                        syncStatus?.let {
                            AnimatedCard(
                                animationsEnabled = animationsEnabled,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = if (it.contains("success"))
                                            Icons.Default.Check
                                        else Icons.Default.Close,
                                        contentDescription = null,
                                        modifier = Modifier.size(20.dp),
                                        tint = if (it.contains("success"))
                                            MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.error
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = it,
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // About Section
            item {
                AnimatedCard(
                    animationsEnabled = animationsEnabled,
                    delayMillis = 300
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "About",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )

                        HorizontalDivider()

                        // App Version
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "App Version",
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Text(
                                text = "v${BuildConfig.VERSION_NAME}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        // Build Number with Animation
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Build Number",
                                style = MaterialTheme.typography.bodyMedium
                            )
                            AnimatedCounter(
                                count = BuildConfig.VERSION_CODE,
                                fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                animationsEnabled = animationsEnabled
                            )
                        }

                        // Build Type
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Build Type",
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Text(
                                text = if (BuildConfig.DEBUG) "Debug" else "Release",
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (BuildConfig.DEBUG)
                                    MaterialTheme.colorScheme.error
                                else MaterialTheme.colorScheme.primary
                            )
                        }

                        // Build Time
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Build Time",
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Text(
                                text = formatBuildTime(BuildConfig.BUILD_TIME),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // Animation Demo Section (if enabled)
            if (animationsEnabled) {
                item {
                    AnimatedCard(
                        animationsEnabled = true,
                        delayMillis = 400
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text(
                                text = "Animation Showcase",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )

                            HorizontalDivider()

                            // Progress Bar Demo
                            var progress by remember { mutableStateOf(0f) }
                            LaunchedEffect(Unit) {
                                while (true) {
                                    progress = 0f
                                    while (progress < 1f) {
                                        progress += 0.01f
                                        kotlinx.coroutines.delay(50)
                                    }
                                    kotlinx.coroutines.delay(1000)
                                }
                            }

                            Column {
                                Text(
                                    text = "Progress Animation",
                                    style = MaterialTheme.typography.bodySmall
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                AnimatedProgressBar(
                                    progress = progress,
                                    animationsEnabled = true
                                )
                            }

                            // Checkmark Demo
                            var showCheckmark by remember { mutableStateOf(false) }
                            LaunchedEffect(Unit) {
                                while (true) {
                                    kotlinx.coroutines.delay(2000)
                                    showCheckmark = !showCheckmark
                                }
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Success Animation",
                                    style = MaterialTheme.typography.bodySmall
                                )
                                AnimatedCheckmark(
                                    isVisible = showCheckmark,
                                    animationsEnabled = true
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SettingsMenuItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    animationsEnabled: Boolean
) {
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )

            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Icon(
                Icons.Default.ArrowForward,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

// Helper function to format build time
private fun formatBuildTime(buildTime: String): String {
    // Build time is already in the correct format from BuildConfig
    return buildTime
}