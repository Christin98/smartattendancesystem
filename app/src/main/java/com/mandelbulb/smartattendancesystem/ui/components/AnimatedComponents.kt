package com.mandelbulb.smartattendancesystem.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun AnimatedDigitalClock(
    hours: Int,
    minutes: Int,
    seconds: Int,
    modifier: Modifier = Modifier,
    fontSize: TextUnit = 48.sp,
    fontWeight: FontWeight = FontWeight.Bold,
    color: Color = MaterialTheme.colorScheme.onSurface,
    animationsEnabled: Boolean = true
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        AnimatedDigit(
            value = hours / 10,
            fontSize = fontSize,
            fontWeight = fontWeight,
            color = color,
            animationsEnabled = animationsEnabled
        )
        AnimatedDigit(
            value = hours % 10,
            fontSize = fontSize,
            fontWeight = fontWeight,
            color = color,
            animationsEnabled = animationsEnabled
        )

        PulsingColon(
            fontSize = fontSize,
            color = color,
            animationsEnabled = animationsEnabled
        )

        AnimatedDigit(
            value = minutes / 10,
            fontSize = fontSize,
            fontWeight = fontWeight,
            color = color,
            animationsEnabled = animationsEnabled
        )
        AnimatedDigit(
            value = minutes % 10,
            fontSize = fontSize,
            fontWeight = fontWeight,
            color = color,
            animationsEnabled = animationsEnabled
        )

        PulsingColon(
            fontSize = fontSize,
            color = color,
            animationsEnabled = animationsEnabled
        )

        AnimatedDigit(
            value = seconds / 10,
            fontSize = fontSize,
            fontWeight = fontWeight,
            color = color,
            animationsEnabled = animationsEnabled
        )
        AnimatedDigit(
            value = seconds % 10,
            fontSize = fontSize,
            fontWeight = fontWeight,
            color = color,
            animationsEnabled = animationsEnabled
        )
    }
}

@Composable
fun AnimatedDigit(
    value: Int,
    fontSize: TextUnit,
    fontWeight: FontWeight,
    color: Color,
    animationsEnabled: Boolean
) {
    var oldValue by remember { mutableStateOf(value) }
    var animationDirection by remember { mutableStateOf(1) }

    LaunchedEffect(value) {
        animationDirection = if (value > oldValue) 1 else -1
        oldValue = value
    }

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier.width((fontSize.value * 0.6).dp)
    ) {
        if (animationsEnabled) {
            AnimatedContent(
                targetState = value,
                transitionSpec = {
                    val enterTransition = slideInVertically(
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioMediumBouncy,
                            stiffness = Spring.StiffnessLow
                        ),
                        initialOffsetY = { height -> height * animationDirection }
                    ) + fadeIn(animationSpec = tween(300))

                    val exitTransition = slideOutVertically(
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioNoBouncy,
                            stiffness = Spring.StiffnessMedium
                        ),
                        targetOffsetY = { height -> -height * animationDirection }
                    ) + fadeOut(animationSpec = tween(200))

                    enterTransition togetherWith exitTransition using (
                        SizeTransform(clip = false)
                    )
                },
                label = "digit_animation"
            ) { digit ->
                Text(
                    text = digit.toString(),
                    fontSize = fontSize,
                    fontWeight = fontWeight,
                    color = color,
                    fontFamily = FontFamily.Monospace,
                    textAlign = TextAlign.Center
                )
            }
        } else {
            Text(
                text = value.toString(),
                fontSize = fontSize,
                fontWeight = fontWeight,
                color = color,
                fontFamily = FontFamily.Monospace,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
fun PulsingColon(
    fontSize: TextUnit,
    color: Color,
    animationsEnabled: Boolean
) {
    val infiniteTransition = rememberInfiniteTransition(label = "colon_pulse")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 0.3f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000),
            repeatMode = RepeatMode.Reverse
        ),
        label = "colon_alpha"
    )

    Text(
        text = ":",
        fontSize = fontSize,
        fontWeight = FontWeight.Bold,
        color = color,
        fontFamily = FontFamily.Monospace,
        modifier = Modifier
            .alpha(if (animationsEnabled) alpha else 1f)
            .padding(horizontal = 2.dp)
    )
}

@Composable
fun AnimatedCard(
    onClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    animationsEnabled: Boolean = true,
    delayMillis: Int = 0,
    content: @Composable ColumnScope.() -> Unit
) {
    var isVisible by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        if (animationsEnabled) {
            delay(delayMillis.toLong())
        }
        isVisible = true
    }

    val scale by animateFloatAsState(
        targetValue = if (isVisible) 1f else 0.8f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "card_scale"
    )

    val alpha by animateFloatAsState(
        targetValue = if (isVisible) 1f else 0f,
        animationSpec = tween(400),
        label = "card_alpha"
    )

    Card(
        onClick = onClick ?: {},
        modifier = modifier
            .scale(if (animationsEnabled) scale else 1f)
            .alpha(if (animationsEnabled) alpha else 1f),
        elevation = CardDefaults.cardElevation(
            defaultElevation = 4.dp,
            pressedElevation = 8.dp
        ),
        content = content
    )
}

@Composable
fun AnimatedButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    animationsEnabled: Boolean = true,
    content: @Composable RowScope.() -> Unit
) {
    var isPressed by remember { mutableStateOf(false) }

    val scale by animateFloatAsState(
        targetValue = when {
            !enabled -> 1f
            isPressed && animationsEnabled -> 0.95f
            else -> 1f
        },
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessHigh
        ),
        label = "button_scale"
    )

    Button(
        onClick = {
            if (animationsEnabled) {
                isPressed = true
            }
            onClick()
        },
        modifier = modifier.scale(scale),
        enabled = enabled,
        content = content
    )

    if (animationsEnabled) {
        LaunchedEffect(isPressed) {
            if (isPressed) {
                delay(100)
                isPressed = false
            }
        }
    }
}

@Composable
fun ShimmeringEffect(
    modifier: Modifier = Modifier,
    animationsEnabled: Boolean = true
) {
    if (!animationsEnabled) return

    val infiniteTransition = rememberInfiniteTransition(label = "shimmer")
    val translateX by infiniteTransition.animateFloat(
        initialValue = -300f,
        targetValue = 300f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmer_translate"
    )

    Box(
        modifier = modifier
            .graphicsLayer {
                this.translationX = translateX
            }
            .background(
                brush = androidx.compose.ui.graphics.Brush.horizontalGradient(
                    colors = listOf(
                        Color.Transparent,
                        Color.White.copy(alpha = 0.3f),
                        Color.Transparent
                    )
                )
            )
    )
}

@Composable
fun PulsingIcon(
    icon: @Composable () -> Unit,
    animationsEnabled: Boolean = true,
    tint: Color = MaterialTheme.colorScheme.primary
) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val scale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.2f,
        animationSpec = infiniteRepeatable(
            animation = tween(600),
            repeatMode = RepeatMode.Reverse
        ),
        label = "icon_pulse"
    )

    Box(
        modifier = Modifier
            .scale(if (animationsEnabled) scale else 1f)
    ) {
        icon()
    }
}

@Composable
fun AnimatedCheckmark(
    isVisible: Boolean,
    modifier: Modifier = Modifier,
    animationsEnabled: Boolean = true
) {
    AnimatedVisibility(
        visible = isVisible,
        enter = if (animationsEnabled) {
            scaleIn(
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessLow
                )
            ) + fadeIn()
        } else EnterTransition.None,
        exit = if (animationsEnabled) {
            scaleOut(animationSpec = tween(200)) + fadeOut()
        } else ExitTransition.None,
        modifier = modifier
    ) {
        Surface(
            modifier = Modifier.size(48.dp),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primary
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.fillMaxSize()
            ) {
                Text(
                    text = "✓",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimary
                )
            }
        }
    }
}

@Composable
fun TypingText(
    text: String,
    modifier: Modifier = Modifier,
    animationsEnabled: Boolean = true,
    typingSpeed: Long = 50L,
    fontSize: TextUnit = 16.sp,
    color: Color = MaterialTheme.colorScheme.onSurface
) {
    var displayedText by remember { mutableStateOf("") }

    LaunchedEffect(text) {
        if (animationsEnabled) {
            displayedText = ""
            text.forEach { char ->
                displayedText += char
                delay(typingSpeed)
            }
        } else {
            displayedText = text
        }
    }

    Text(
        text = displayedText,
        modifier = modifier,
        fontSize = fontSize,
        color = color
    )
}

@Composable
fun AnimatedProgressBar(
    progress: Float,
    modifier: Modifier = Modifier,
    animationsEnabled: Boolean = true
) {
    val animatedProgress by animateFloatAsState(
        targetValue = progress,
        animationSpec = if (animationsEnabled) {
            spring(
                dampingRatio = Spring.DampingRatioNoBouncy,
                stiffness = Spring.StiffnessLow
            )
        } else {
            snap()
        },
        label = "progress"
    )

    LinearProgressIndicator(
        progress = { animatedProgress },
        modifier = modifier
            .fillMaxWidth()
            .height(8.dp)
            .clip(RoundedCornerShape(4.dp))
    )
}

@OptIn(ExperimentalAnimationApi::class)
@Composable
fun AnimatedCounter(
    count: Int,
    modifier: Modifier = Modifier,
    animationsEnabled: Boolean = true,
    fontSize: TextUnit = 24.sp,
    fontWeight: FontWeight = FontWeight.Bold,
    color: Color = MaterialTheme.colorScheme.onSurface
) {
    AnimatedContent(
        targetState = count,
        transitionSpec = {
            if (animationsEnabled) {
                if (targetState > initialState) {
                    (slideInVertically { height -> height } + fadeIn()) togetherWith
                            (slideOutVertically { height -> -height } + fadeOut())
                } else {
                    (slideInVertically { height -> -height } + fadeIn()) togetherWith
                            (slideOutVertically { height -> height } + fadeOut())
                }.using(SizeTransform(clip = false))
            } else {
                EnterTransition.None togetherWith ExitTransition.None
            }
        },
        modifier = modifier,
        label = "counter"
    ) { targetCount ->
        Text(
            text = targetCount.toString(),
            fontSize = fontSize,
            fontWeight = fontWeight,
            color = color
        )
    }
}