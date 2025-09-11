package com.mandelbulb.smartattendancesystem.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val LightColors = lightColorScheme(
    primary = RedPrimary,
    secondary = LightBlue,
    background = Background,
    onPrimary = androidx.compose.ui.graphics.Color.White
)

@Composable
fun SmartAttendanceSystemTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = LightColors,
        typography = Typography,
        content = content
    )
}
