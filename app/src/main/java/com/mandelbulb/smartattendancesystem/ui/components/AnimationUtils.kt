package com.mandelbulb.smartattendancesystem.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer

// Standard animation specs used throughout the app
object AnimationDefaults {
    val springSpec = spring<Float>(
        dampingRatio = Spring.DampingRatioMediumBouncy,
        stiffness = Spring.StiffnessLow
    )

    val tweenSpec = tween<Float>(
        durationMillis = 400,
        easing = FastOutSlowInEasing
    )

    val fadeInSpec = fadeIn(
        animationSpec = tween(300)
    )

    val fadeOutSpec = fadeOut(
        animationSpec = tween(200)
    )

    val scaleInSpec = scaleIn(
        initialScale = 0.9f,
        animationSpec = tween(400)
    )

    val scaleOutSpec = scaleOut(
        targetScale = 0.9f,
        animationSpec = tween(200)
    )

    val slideInSpec = slideInVertically(
        initialOffsetY = { it / 20 },
        animationSpec = tween(400)
    )

    val slideOutSpec = slideOutVertically(
        targetOffsetY = { -it / 20 },
        animationSpec = tween(200)
    )
}

// Consistent entrance animation for cards
@Composable
fun animatedCardEnterTransition(animationsEnabled: Boolean): EnterTransition {
    return if (animationsEnabled) {
        AnimationDefaults.fadeInSpec + AnimationDefaults.scaleInSpec + AnimationDefaults.slideInSpec
    } else {
        EnterTransition.None
    }
}

// Consistent exit animation for cards
@Composable
fun animatedCardExitTransition(animationsEnabled: Boolean): ExitTransition {
    return if (animationsEnabled) {
        AnimationDefaults.fadeOutSpec + AnimationDefaults.scaleOutSpec + AnimationDefaults.slideOutSpec
    } else {
        ExitTransition.None
    }
}

// Modifier for consistent hover/press effects
fun Modifier.animatedPress(
    isPressed: Boolean,
    animationsEnabled: Boolean
): Modifier {
    return if (animationsEnabled && isPressed) {
        this.then(
            Modifier.graphicsLayer {
                scaleX = 0.95f
                scaleY = 0.95f
            }
        )
    } else {
        this
    }
}

// Staggered animation delay calculator
fun calculateStaggeredDelay(index: Int, baseDelay: Int = 50): Int {
    return index * baseDelay
}