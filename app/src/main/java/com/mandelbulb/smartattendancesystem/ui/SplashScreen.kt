package com.mandelbulb.smartattendancesystem.ui

import androidx.compose.animation.animateColor
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import com.mandelbulb.smartattendancesystem.ui.components.AppIcons
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlin.math.cos
import kotlin.math.sin
import android.content.pm.PackageInfo
import android.os.Build
import java.text.SimpleDateFormat
import java.util.*
import com.mandelbulb.smartattendancesystem.BuildConfig

@Composable
fun SplashScreen(
    onSplashComplete: () -> Unit
) {
    val context = LocalContext.current
    var loadingProgress by remember { mutableFloatStateOf(0f) }
    var isLoading by remember { mutableStateOf(true) }

    // Get version info
    val packageInfo = remember {
        try {
            context.packageManager.getPackageInfo(context.packageName, 0)
        } catch (e: Exception) {
            null
        }
    }

    val versionName = packageInfo?.versionName ?: "1.0.0"
    val versionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        packageInfo?.longVersionCode?.toString() ?: "1"
    } else {
        @Suppress("DEPRECATION")
        packageInfo?.versionCode?.toString() ?: "1"
    }

    // Build time from BuildConfig
    val buildTime = BuildConfig.BUILD_TIME

    // Animation states
    val infiniteTransition = rememberInfiniteTransition(label = "splash")

    // Logo scale animation
    val logoScale by animateFloatAsState(
        targetValue = if (isLoading) 1f else 1.2f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "logo_scale"
    )

    // Logo rotation for the outer ring
    val logoRotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(3000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "logo_rotation"
    )

    // Pulsing effect for the center icon
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.9f,
        targetValue = 1.1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )

    // Color animation
    val animatedColor by infiniteTransition.animateColor(
        initialValue = MaterialTheme.colorScheme.primary,
        targetValue = MaterialTheme.colorScheme.tertiary,
        animationSpec = infiniteRepeatable(
            animation = tween(2000),
            repeatMode = RepeatMode.Reverse
        ),
        label = "color"
    )

    // Text fade in animation
    val textAlpha by animateFloatAsState(
        targetValue = if (isLoading) 1f else 0f,
        animationSpec = tween(500),
        label = "text_alpha"
    )

    // Loading dots animation
    val dot1Alpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "dot1"
    )

    val dot2Alpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, delayMillis = 200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "dot2"
    )

    val dot3Alpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, delayMillis = 400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "dot3"
    )

    // Simulate loading progress
    LaunchedEffect(Unit) {
        while (loadingProgress < 1f) {
            delay(30)
            loadingProgress += 0.02f
        }
        delay(500)
        isLoading = false
        delay(800)
        onSplashComplete()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.background,
                        MaterialTheme.colorScheme.surface
                    )
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(32.dp)
        ) {
            // Animated Logo Container
            Box(
                modifier = Modifier
                    .size(200.dp)
                    .scale(logoScale),
                contentAlignment = Alignment.Center
            ) {
                // Animated circular rings
                Canvas(
                    modifier = Modifier
                        .fillMaxSize()
                        .rotate(logoRotation)
                ) {
                    drawAnimatedRings(animatedColor)
                }

                // Reverse rotating ring
                Canvas(
                    modifier = Modifier
                        .fillMaxSize()
                        .rotate(-logoRotation * 0.5f)
                ) {
                    drawOuterRing(animatedColor.copy(alpha = 0.3f))
                }

                // Center icon with pulse effect - Using Material Extended Fingerprint
                Icon(
                    imageVector = AppIcons.Fingerprint,
                    contentDescription = "Logo",
                    modifier = Modifier
                        .size(80.dp)
                        .scale(pulseScale),
                    tint = animatedColor
                )

                // Orbiting dots
                OrbitingDots(
                    rotation = logoRotation * 2,
                    color = animatedColor
                )
            }

            Spacer(modifier = Modifier.height(48.dp))

            // App Title with fade animation
            Text(
                text = "Smart Attendance",
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.alpha(textAlpha)
            )

            Text(
                text = "System",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Light,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.alpha(textAlpha)
            )

            Spacer(modifier = Modifier.height(48.dp))

            // Loading indicator
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.alpha(textAlpha)
            ) {
                // Progress bar
                LinearProgressIndicator(
                    progress = { loadingProgress },
                    modifier = Modifier
                        .fillMaxWidth(0.6f)
                        .height(4.dp),
                    color = animatedColor,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Loading text with animated dots
                Row(
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Initializing",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.width(4.dp))

                    // Animated dots
                    Text(
                        text = "•",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.alpha(dot1Alpha)
                    )
                    Text(
                        text = "•",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.alpha(dot2Alpha)
                    )
                    Text(
                        text = "•",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.alpha(dot3Alpha)
                    )
                }
            }

            Spacer(modifier = Modifier.height(64.dp))

            // Version info
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.alpha(textAlpha * 0.7f)
            ) {
                Text(
                    text = "Version $versionName",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "Build $versionCode",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
                Text(
                    text = buildTime,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "Powered by FaceNet & Azure AI",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
                    fontWeight = FontWeight.Medium
                )
            }
        }

        // Bottom wave animation
        WaveAnimation(
            modifier = Modifier
                .fillMaxWidth()
                .height(120.dp)
                .align(Alignment.BottomCenter),
            color = animatedColor.copy(alpha = 0.1f)
        )
    }
}

@Composable
fun OrbitingDots(
    rotation: Float,
    color: Color
) {
    Canvas(modifier = Modifier.fillMaxSize()) {
        val centerX = size.width / 2
        val centerY = size.height / 2
        val radius = size.minDimension / 3

        for (i in 0..2) {
            val angle = rotation + (i * 120f)
            val x = centerX + radius * cos(Math.toRadians(angle.toDouble())).toFloat()
            val y = centerY + radius * sin(Math.toRadians(angle.toDouble())).toFloat()

            drawCircle(
                color = color,
                radius = 8f,
                center = Offset(x, y)
            )
        }
    }
}

@Composable
fun WaveAnimation(
    modifier: Modifier = Modifier,
    color: Color
) {
    val infiniteTransition = rememberInfiniteTransition(label = "wave")
    val waveShift by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(3000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "wave_shift"
    )

    Canvas(modifier = modifier) {
        val width = size.width
        val height = size.height

        val path = Path().apply {
            moveTo(0f, height)

            for (x in 0..width.toInt() step 5) {
                val y = height * 0.5f +
                    sin(Math.toRadians((x + waveShift).toDouble())).toFloat() * height * 0.3f
                lineTo(x.toFloat(), y)
            }

            lineTo(width, height)
            close()
        }

        drawPath(
            path = path,
            brush = Brush.verticalGradient(
                colors = listOf(
                    color,
                    color.copy(alpha = 0f)
                )
            )
        )
    }
}

private fun DrawScope.drawAnimatedRings(color: Color) {
    val strokeWidth = 3.dp.toPx()

    // Inner ring
    drawArc(
        color = color,
        startAngle = 0f,
        sweepAngle = 270f,
        useCenter = false,
        topLeft = Offset(size.width * 0.2f, size.height * 0.2f),
        size = Size(size.width * 0.6f, size.height * 0.6f),
        style = Stroke(strokeWidth, cap = StrokeCap.Round)
    )

    // Middle ring
    drawArc(
        color = color.copy(alpha = 0.6f),
        startAngle = 90f,
        sweepAngle = 200f,
        useCenter = false,
        topLeft = Offset(size.width * 0.15f, size.height * 0.15f),
        size = Size(size.width * 0.7f, size.height * 0.7f),
        style = Stroke(strokeWidth, cap = StrokeCap.Round)
    )
}

private fun DrawScope.drawOuterRing(color: Color) {
    val strokeWidth = 2.dp.toPx()

    drawArc(
        color = color,
        startAngle = 45f,
        sweepAngle = 180f,
        useCenter = false,
        topLeft = Offset(size.width * 0.05f, size.height * 0.05f),
        size = Size(size.width * 0.9f, size.height * 0.9f),
        style = Stroke(strokeWidth, cap = StrokeCap.Round, pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f))
    )
}