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
import com.mandelbulb.smartattendancesystem.ui.*
import com.mandelbulb.smartattendancesystem.ui.theme.SmartAttendanceSystemTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

class MainActivity : ComponentActivity() {

    private val permissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (!granted) {
            Toast.makeText(this, "Camera permission required", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // request camera permission if not granted
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }

        val userPreferences = UserPreferences(this)
        val isRegistered = runBlocking { userPreferences.isRegistered.first() }
        
        setContent {
            SmartAttendanceSystemTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    var currentScreen by remember { 
                        mutableStateOf(
                            if (isRegistered) "main" else "login"
                        )
                    }
                    
                    when (currentScreen) {
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
                                onLogout = { currentScreen = "login" }
                            )
                        }
                        "settings" -> {
                            ApiConfigScreen(
                                onBack = { currentScreen = "main" }
                            )
                        }
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
    }
}
