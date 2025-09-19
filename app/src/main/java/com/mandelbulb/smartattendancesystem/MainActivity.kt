package com.mandelbulb.smartattendancesystem

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.compose.material3.Surface
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import com.mandelbulb.smartattendancesystem.data.UserPreferences
import com.mandelbulb.smartattendancesystem.sync.SyncManager
import com.mandelbulb.smartattendancesystem.ui.*
import com.mandelbulb.smartattendancesystem.ui.theme.SmartAttendanceSystemTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

class MainActivity : ComponentActivity() {

    private lateinit var syncManager: SyncManager

    private val cameraPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (!granted) {
            Toast.makeText(this, "Camera permission required", Toast.LENGTH_SHORT).show()
        }
    }

    private val locationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val fineLocationGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] ?: false
        val coarseLocationGranted = permissions[Manifest.permission.ACCESS_COARSE_LOCATION] ?: false

        if (!fineLocationGranted && !coarseLocationGranted) {
            Toast.makeText(this, "Location permission helps track attendance location", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Initialize SyncManager for background sync
        syncManager = SyncManager(this)

        // request camera permission if not granted
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }

        // request location permissions if not granted
        val locationPermissions = arrayOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
        if (locationPermissions.any {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }) {
            locationPermissionLauncher.launch(locationPermissions)
        }

        val userPreferences = UserPreferences(this)
        val isRegistered = runBlocking { userPreferences.isRegistered.first() }

        setContent {
            SmartAttendanceSystemTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    var currentScreen by remember {
                        mutableStateOf("splash")
                    }

                    when (currentScreen) {
                        "splash" -> {
                            SplashScreen(
                                onSplashComplete = {
                                    currentScreen = if (isRegistered) "main" else "login"
                                }
                            )
                        }
                        "login" -> {
                            LoginScreen(
                                onLoginSuccess = { currentScreen = "main" },
                                onNavigateToRegistration = { currentScreen = "registration" }
                            )
                        }
                        "registration" -> {
                            RegistrationScreen(
                                onRegistrationComplete = { currentScreen = "main" }
                            )
                        }
                        "main" -> {
                            SingleUserMainScreen(
                                onNavigateToSettings = { currentScreen = "settings" },
                                onNavigateToSync = { currentScreen = "sync" },
                                onLogout = { currentScreen = "login" }
                            )
                        }
                        "settings" -> {
                            SettingsScreen(
                                onNavigateBack = { currentScreen = "main" },
                                onNavigateToApiConfig = { currentScreen = "api_config" },
                                onNavigateToSync = { currentScreen = "sync" }
                            )
                        }
                        "api_config" -> {
                            ApiConfigScreen(
                                onBack = { currentScreen = "settings" }
                            )
                        }
                        "sync" -> {
                            SyncScreen(
                                onNavigateBack = { currentScreen = "main" }
                            )
                        }
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // Clean up sync manager
        syncManager.stopPeriodicSync()
    }
}
