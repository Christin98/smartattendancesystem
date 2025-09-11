package com.mandelbulb.smartattendancesystem.ml

import kotlin.math.pow
import kotlin.math.sqrt

class LivenessDetector {

    companion object {
        private const val MIN_EYE_THRESHOLD = 0.1f
        private const val MAX_EYE_THRESHOLD = 0.95f
        private const val MAX_HEAD_ANGLE = 45f
        private const val SMILE_WEIGHT = 0.15f
        private const val EYE_WEIGHT = 0.35f
        private const val POSE_WEIGHT = 0.25f
        private const val BASE_SCORE_WEIGHT = 0.25f
    }

    private val recentScores = mutableListOf<Float>()
    private val maxHistorySize = 10

    /**
     * Assess liveness based on multiple factors
     * Returns a score between 0.0 (not live) and 1.0 (highly live)
     */
    fun assessLiveness(
        leftEyeProb: Float,
        rightEyeProb: Float,
        smileProb: Float,
        headEulerY: Float,
        headEulerZ: Float,
        headEulerX: Float
    ): Float {
        var livenessScore = 0f

        // 1. Eye openness check - more lenient
        val eyeScore = when {
            leftEyeProb < 0 && rightEyeProb < 0 -> 0.7f // No eye data, give benefit of doubt
            leftEyeProb < 0 || rightEyeProb < 0 -> {
                // One eye has data
                val validEye = if (leftEyeProb >= 0) leftEyeProb else rightEyeProb
                if (validEye > MIN_EYE_THRESHOLD) 0.8f else 0.5f
            }
            else -> {
                val avgEyeProb = (leftEyeProb + rightEyeProb) / 2f
                when {
                    avgEyeProb in MIN_EYE_THRESHOLD..MAX_EYE_THRESHOLD -> 1f
                    avgEyeProb > MAX_EYE_THRESHOLD -> 0.85f
                    else -> 0.6f
                }
            }
        }

        // 2. Smile analysis - more lenient
        val smileScore = when {
            smileProb < 0 -> 0.7f // No smile data, neutral score
            smileProb in 0.05f..0.9f -> 1f // Wider natural range
            else -> 0.7f
        }

        // 3. Head pose variation - more lenient
        val poseScore = calculatePoseScore(headEulerY, headEulerZ, headEulerX)

        // 4. Base score to ensure minimum liveness
        val baseScore = 1f

        // Weighted combination with base score
        livenessScore = (eyeScore * EYE_WEIGHT +
                smileScore * SMILE_WEIGHT +
                poseScore * POSE_WEIGHT +
                baseScore * BASE_SCORE_WEIGHT)

        // More lenient final score
        return livenessScore.coerceIn(0f, 1f)
    }

    private fun calculatePoseScore(eulerY: Float, eulerZ: Float, eulerX: Float): Float {
        val totalRotation = sqrt(eulerY.pow(2) + eulerZ.pow(2) + eulerX.pow(2))

        return when {
            totalRotation > MAX_HEAD_ANGLE -> 0.7f // Still acceptable
            totalRotation > 10f -> 1f // Good natural movement
            totalRotation > 3f -> 0.9f // Some movement
            else -> 0.8f // Still face is also acceptable
        }
    }

    private fun calculateTemporalScore(currentEyeProb: Float): Float {
        recentScores.add(currentEyeProb)

        if (recentScores.size > maxHistorySize) {
            recentScores.removeAt(0)
        }

        if (recentScores.size < 3) return 0.5f

        // Calculate variance (natural blinking should show variation)
        val mean = recentScores.average()
        val variance = recentScores.map { (it - mean).pow(2) }.average()
        val stdDev = sqrt(variance.toFloat())

        return when {
            stdDev > 0.2f -> 1f // Good variation
            stdDev > 0.1f -> 0.8f // Some variation
            else -> 0.4f // Too consistent (might be fake)
        }
    }

    /**
     * Advanced liveness check using multiple frames
     */
    fun checkBlinkPattern(eyeStates: List<Pair<Float, Float>>): Float {
        if (eyeStates.size < 5) return 0.5f

        var blinkCount = 0
        var wasOpen = true

        eyeStates.forEach { (left, right) ->
            val avgEye = (left + right) / 2f
            val isOpen = avgEye > 0.5f

            if (wasOpen && !isOpen) {
                blinkCount++
            }
            wasOpen = isOpen
        }

        // Natural blink rate is about 15-20 per minute
        // In a 5-10 second capture window, expect 1-3 blinks
        return when (blinkCount) {
            in 1..3 -> 1f
            0 -> 0.3f // No blinking (suspicious)
            else -> 0.6f // Too many blinks
        }
    }

    /**
     * Check for spoofing indicators
     */
    fun detectSpoofingIndicators(
        faceWidth: Int,
        faceHeight: Int,
        imageWidth: Int,
        imageHeight: Int,
        trackingId: Int?
    ): Float {
        var antiSpoofScore = 1f

        // Face size consistency - more lenient
        val faceArea = faceWidth * faceHeight
        val imageArea = imageWidth * imageHeight
        val faceRatio = faceArea.toFloat() / imageArea.toFloat()

        if (faceRatio < 0.02f || faceRatio > 0.9f) {
            antiSpoofScore -= 0.2f // Very unusual face size
        } else if (faceRatio < 0.05f || faceRatio > 0.8f) {
            antiSpoofScore -= 0.1f // Somewhat unusual
        }

        // Tracking stability - more lenient
        if (trackingId == null) {
            antiSpoofScore -= 0.1f // Slight penalty for no tracking
        }

        return antiSpoofScore.coerceIn(0.5f, 1f) // Minimum 0.5 score
    }
}