package com.mandelbulb.smartattendancesystem.ml

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.support.common.ops.NormalizeOp
import org.tensorflow.lite.support.image.ImageProcessor
import org.tensorflow.lite.support.image.TensorImage
import org.tensorflow.lite.support.image.ops.ResizeOp
import org.tensorflow.lite.support.tensorbuffer.TensorBuffer
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel

class LivenessDetectionService(private val context: Context) {
    companion object {
        private const val TAG = "LivenessDetection"
        private const val MODEL_NAME = "spoof_model_scale_2_7.tflite"
        private const val INPUT_SIZE = 80  // Changed from 224 to 80 based on model requirements
        private const val LIVENESS_THRESHOLD = 0.85f // Increased threshold for stricter verification
        private const val PIXEL_SIZE = 3 // RGB
        private const val NUM_BYTES_PER_CHANNEL = 4 // Float32 = 4 bytes
        private const val MIN_FACE_SIZE = 100 // Minimum face size in pixels
        private const val MAX_FACE_SIZE = 800 // Maximum face size to prevent too close/far faces
    }

    private var interpreter: Interpreter? = null
    private var inputSize = INPUT_SIZE

    private val imageProcessor: ImageProcessor
        get() = ImageProcessor.Builder()
            .add(ResizeOp(inputSize, inputSize, ResizeOp.ResizeMethod.BILINEAR))
            .add(NormalizeOp(0f, 255f)) // Normalize to [0, 1]
            .build()

    init {
        try {
            val model = loadModelFile()
            val options = Interpreter.Options()
            options.setNumThreads(4)
            interpreter = Interpreter(model, options)

            // Get actual input shape from the model
            interpreter?.let { interp ->
                val inputTensor = interp.getInputTensor(0)
                val inputShape = inputTensor.shape()

                // inputShape is typically [batch_size, height, width, channels]
                if (inputShape.size >= 3) {
                    inputSize = inputShape[1] // Assuming square input
                    Log.d(TAG, "Model input shape: ${inputShape.contentToString()}, using size: $inputSize")
                }

                // Calculate expected bytes
                val expectedBytes = inputShape.fold(1) { acc, dim -> acc * dim } * NUM_BYTES_PER_CHANNEL
                Log.d(TAG, "Model expects $expectedBytes bytes for input")
            }

            Log.d(TAG, "Liveness detection model loaded successfully with input size: $inputSize")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load liveness model: ${e.message}")
        }
    }

    private fun loadModelFile(): MappedByteBuffer {
        val fileDescriptor = context.assets.openFd(MODEL_NAME)
        val inputStream = FileInputStream(fileDescriptor.fileDescriptor)
        val fileChannel = inputStream.channel
        val startOffset = fileDescriptor.startOffset
        val declaredLength = fileDescriptor.declaredLength
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength)
    }

    data class LivenessResult(
        val isLive: Boolean,
        val confidence: Float,
        val message: String
    )

    /**
     * Performs liveness detection on a face image
     * Returns LivenessResult with detection status and confidence score
     */
    fun detectLiveness(faceBitmap: Bitmap): LivenessResult {
        return try {
            if (interpreter == null) {
                Log.e(TAG, "Interpreter not initialized")
                return LivenessResult(false, 0f, "Liveness detection not available")
            }

            // Preprocess the image
            val tensorImage = TensorImage.fromBitmap(faceBitmap)
            val processedImage = imageProcessor.process(tensorImage)

            // Process and resize the image
            val resizedBitmap = Bitmap.createScaledBitmap(faceBitmap, inputSize, inputSize, true)
            val inputBuffer = convertBitmapToByteBuffer(resizedBitmap)

            // Get output tensor shape
            val outputTensor = interpreter?.getOutputTensor(0)
            val outputShape = outputTensor?.shape() ?: intArrayOf(1, 2)
            val outputSize = outputShape.last()

            Log.d(TAG, "Output shape: ${outputShape.contentToString()}, size: $outputSize")

            // Prepare output buffer based on actual output shape
            val outputBuffer = Array(1) { FloatArray(outputSize) }

            // Run inference
            interpreter?.run(inputBuffer, outputBuffer)

            // Handle different output formats
            val realProb: Float
            val isLive: Boolean

            if (outputSize == 1) {
                // Single output: probability of being real (0-1)
                realProb = outputBuffer[0][0]
                isLive = realProb >= LIVENESS_THRESHOLD
                Log.d(TAG, "Liveness score (single output): $realProb")
            } else if (outputSize >= 2) {
                // Binary classification: [fake_prob, real_prob]
                val fakeProbability = outputBuffer[0][0]
                val realProbability = outputBuffer[0][1]

                // Apply softmax to get proper probabilities
                val (fakeProb, realProbSoftmax) = applySoftmax(fakeProbability, realProbability)
                realProb = realProbSoftmax
                isLive = realProb >= LIVENESS_THRESHOLD
                Log.d(TAG, "Liveness scores - Fake: $fakeProb, Real: $realProb")
            } else {
                Log.e(TAG, "Unexpected output size: $outputSize")
                return LivenessResult(false, 0f, "Model output format not supported")
            }

            val message = when {
                realProb >= 0.95f -> "Face verified as real (high confidence)"
                realProb >= LIVENESS_THRESHOLD -> "Face verified as real"
                realProb >= 0.7f -> "Liveness check uncertain, please try again"
                realProb >= 0.5f -> "Possible spoofing detected, move closer and try again"
                else -> "⚠️ Spoofing detected! Please use your real face, not a photo or video"
            }

            LivenessResult(
                isLive = isLive,
                confidence = realProb,
                message = message
            )

        } catch (e: Exception) {
            Log.e(TAG, "Error during liveness detection: ${e.message}", e)
            LivenessResult(false, 0f, "Liveness detection failed")
        }
    }

    /**
     * Performs multi-frame liveness detection for better accuracy
     * Takes multiple frames and aggregates results
     */
    fun detectLivenessMultiFrame(frames: List<Bitmap>): LivenessResult {
        if (frames.isEmpty()) {
            return LivenessResult(false, 0f, "No frames provided")
        }

        if (frames.size < 3) {
            return LivenessResult(false, 0f, "Need at least 3 frames for reliable detection")
        }

        val results = frames.map { detectLiveness(it) }
        val averageConfidence = results.map { it.confidence }.average().toFloat()
        val liveFrames = results.count { it.isLive }
        val totalFrames = results.size

        // Check for consistency - all frames should have similar confidence
        val confidences = results.map { it.confidence }
        val minConfidence = confidences.minOrNull() ?: 0f
        val maxConfidence = confidences.maxOrNull() ?: 0f
        val confidenceVariance = maxConfidence - minConfidence

        // High variance might indicate spoofing (e.g., screen reflections)
        val consistencyCheck = confidenceVariance < 0.3f

        // Require at least 80% of frames to be detected as live AND consistency
        val isLive = liveFrames >= (totalFrames * 0.8).toInt() &&
                     averageConfidence >= LIVENESS_THRESHOLD &&
                     consistencyCheck

        val message = when {
            !consistencyCheck -> "⚠️ Inconsistent readings detected - possible spoofing"
            isLive && averageConfidence >= 0.95f -> "Face verified as real (high confidence)"
            isLive -> "Face verified as real"
            averageConfidence >= 0.7f -> "Liveness check uncertain, please try again"
            liveFrames == 0 -> "⚠️ No live frames detected - ensure proper lighting"
            else -> "⚠️ Spoofing detected! Please use your real face, not a photo or video"
        }

        Log.d(TAG, "Multi-frame liveness: $liveFrames/$totalFrames frames live, avg confidence: $averageConfidence")

        return LivenessResult(
            isLive = isLive,
            confidence = averageConfidence,
            message = message
        )
    }

    private fun convertBitmapToByteBuffer(bitmap: Bitmap): ByteBuffer {
        // Ensure bitmap is the correct size
        val resizedBitmap = if (bitmap.width != inputSize || bitmap.height != inputSize) {
            Bitmap.createScaledBitmap(bitmap, inputSize, inputSize, true)
        } else {
            bitmap
        }

        val byteBuffer = ByteBuffer.allocateDirect(NUM_BYTES_PER_CHANNEL * inputSize * inputSize * PIXEL_SIZE)
        byteBuffer.order(ByteOrder.nativeOrder())

        val intValues = IntArray(inputSize * inputSize)
        resizedBitmap.getPixels(intValues, 0, resizedBitmap.width, 0, 0, resizedBitmap.width, resizedBitmap.height)

        var pixel = 0
        for (i in 0 until inputSize) {
            for (j in 0 until inputSize) {
                val value = intValues[pixel++]

                // Extract RGB values and normalize to [0, 1]
                byteBuffer.putFloat(((value shr 16) and 0xFF) / 255.0f)  // R
                byteBuffer.putFloat(((value shr 8) and 0xFF) / 255.0f)   // G
                byteBuffer.putFloat((value and 0xFF) / 255.0f)           // B
            }
        }

        return byteBuffer
    }

    private fun applySoftmax(score1: Float, score2: Float): Pair<Float, Float> {
        val exp1 = kotlin.math.exp(score1)
        val exp2 = kotlin.math.exp(score2)
        val sum = exp1 + exp2
        return Pair(exp1 / sum, exp2 / sum)
    }

    /**
     * Additional checks for detecting common spoofing attempts
     */
    fun performAdditionalChecks(bitmap: Bitmap): Map<String, Boolean> {
        val checks = mutableMapOf<String, Boolean>()

        // Check face size - too small or too large might indicate spoofing
        val faceSize = minOf(bitmap.width, bitmap.height)
        checks["size_check"] = faceSize in MIN_FACE_SIZE..MAX_FACE_SIZE

        // Check for texture patterns common in printed photos
        checks["texture_check"] = checkTexturePattern(bitmap)

        // Check for reflections that might indicate screen/photo
        checks["reflection_check"] = checkForReflections(bitmap)

        // Check image quality metrics
        checks["quality_check"] = checkImageQuality(bitmap)

        // Check for moire patterns (common in screen captures)
        checks["moire_check"] = !hasMoirePattern(bitmap)

        return checks
    }

    private fun checkTexturePattern(bitmap: Bitmap): Boolean {
        // Check for unnatural texture patterns
        // Photos often have different texture characteristics
        val pixels = IntArray(100)
        val sampleX = bitmap.width / 2
        val sampleY = bitmap.height / 2
        val sampleSize = minOf(50, bitmap.width / 4, bitmap.height / 4)

        if (sampleX + sampleSize > bitmap.width || sampleY + sampleSize > bitmap.height) {
            return true
        }

        bitmap.getPixels(pixels, 0, 10, sampleX, sampleY, 10, 10)

        // Calculate variance in pixel values
        val variance = pixels.map { pixel ->
            val r = (pixel shr 16) and 0xFF
            val g = (pixel shr 8) and 0xFF
            val b = pixel and 0xFF
            (r + g + b) / 3
        }.let { values ->
            val mean = values.average()
            values.map { (it - mean) * (it - mean) }.average()
        }

        // Low variance might indicate printed photo
        return variance > 100
    }

    private fun checkForReflections(bitmap: Bitmap): Boolean {
        // Check for bright spots that might indicate screen reflections
        var brightPixels = 0
        val samplePoints = 100
        val threshold = 250 // Very bright pixels

        for (i in 0 until samplePoints) {
            val x = (Math.random() * bitmap.width).toInt()
            val y = (Math.random() * bitmap.height).toInt()
            val pixel = bitmap.getPixel(x, y)

            val r = (pixel shr 16) and 0xFF
            val g = (pixel shr 8) and 0xFF
            val b = pixel and 0xFF

            if (r > threshold && g > threshold && b > threshold) {
                brightPixels++
            }
        }

        // Too many bright pixels might indicate screen reflection
        return brightPixels < 10
    }

    private fun checkImageQuality(bitmap: Bitmap): Boolean {
        // Check if image has sufficient quality (not too blurry)
        // Calculate edge strength as a measure of sharpness
        val width = minOf(bitmap.width, 100)
        val height = minOf(bitmap.height, 100)
        var edgeStrength = 0.0

        for (x in 1 until width - 1) {
            for (y in 1 until height - 1) {
                val pixel = bitmap.getPixel(x, y)
                val pixelRight = bitmap.getPixel(x + 1, y)
                val pixelBottom = bitmap.getPixel(x, y + 1)

                val gray = ((pixel shr 16) and 0xFF) * 0.3 +
                          ((pixel shr 8) and 0xFF) * 0.59 +
                          (pixel and 0xFF) * 0.11

                val grayRight = ((pixelRight shr 16) and 0xFF) * 0.3 +
                               ((pixelRight shr 8) and 0xFF) * 0.59 +
                               (pixelRight and 0xFF) * 0.11

                val grayBottom = ((pixelBottom shr 16) and 0xFF) * 0.3 +
                                ((pixelBottom shr 8) and 0xFF) * 0.59 +
                                (pixelBottom and 0xFF) * 0.11

                edgeStrength += Math.abs(gray - grayRight) + Math.abs(gray - grayBottom)
            }
        }

        val avgEdgeStrength = edgeStrength / ((width - 2) * (height - 2))
        // Low edge strength indicates blur (possible video/photo)
        return avgEdgeStrength > 5.0
    }

    private fun hasMoirePattern(bitmap: Bitmap): Boolean {
        // Check for moire patterns common in photos of screens
        // Sample diagonal lines for repeating patterns
        val samples = minOf(bitmap.width, bitmap.height, 50)
        val pixels = mutableListOf<Int>()

        for (i in 0 until samples) {
            val x = (i * bitmap.width) / samples
            val y = (i * bitmap.height) / samples
            pixels.add(bitmap.getPixel(x, y))
        }

        // Check for repeating patterns
        var patternScore = 0
        for (i in 2 until pixels.size - 2) {
            val curr = pixels[i] and 0xFF
            val prev = pixels[i - 1] and 0xFF
            val next = pixels[i + 1] and 0xFF

            // Look for alternating bright/dark pattern
            if ((curr > prev + 20 && curr > next + 20) ||
                (curr < prev - 20 && curr < next - 20)) {
                patternScore++
            }
        }

        // High pattern score indicates possible moire
        return patternScore > samples / 4
    }

    fun close() {
        try {
            interpreter?.close()
            interpreter = null
            Log.d(TAG, "Liveness detection service closed")
        } catch (e: Exception) {
            Log.e(TAG, "Error closing liveness detection: ${e.message}")
        }
    }
}