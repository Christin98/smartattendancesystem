package com.mandelbulb.smartattendancesystem.ml

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.support.common.FileUtil
import org.tensorflow.lite.support.common.ops.NormalizeOp
import org.tensorflow.lite.support.image.ImageProcessor
import org.tensorflow.lite.support.image.TensorImage
import org.tensorflow.lite.support.image.ops.ResizeOp
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.sqrt

class FaceNetModel(private val context: Context) {
    companion object {
        private const val TAG = "FaceNetModel"
        private const val MODEL_FILE = "facenet.tflite"
        private const val INPUT_SIZE = 160
        private const val OUTPUT_SIZE = 128
        const val SIMILARITY_THRESHOLD = 0.75f // Increased for better security - prevent false positives
        private const val L2_NORM_THRESHOLD = 0.8f // Decreased for stricter matching
    }

    private var interpreter: Interpreter? = null

    private val imageProcessor = ImageProcessor.Builder()
        .add(ResizeOp(INPUT_SIZE, INPUT_SIZE, ResizeOp.ResizeMethod.BILINEAR))
        .add(NormalizeOp(127.5f, 128f)) // Normalize to [-1, 1]
        .build()

    private var isModelLoaded = false

    init {
        try {
            loadModel()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load FaceNet model", e)
        }
    }

    private fun loadModel() {
        try {
            val modelBuffer = FileUtil.loadMappedFile(context, MODEL_FILE)
            val options = Interpreter.Options().apply {
                setNumThreads(4)
            }
            interpreter = Interpreter(modelBuffer, options)
            isModelLoaded = true
            Log.d(TAG, "FaceNet model loaded successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Error loading FaceNet model", e)
            throw e
        }
    }

    fun generateEmbedding(bitmap: Bitmap): FloatArray? {
        return try {
            // Ensure model is loaded
            if (!isModelLoaded) {
                loadModel()
            }

            val interpreter = this.interpreter ?: run {
                Log.e(TAG, "Interpreter not initialized")
                return null
            }

            // Ensure bitmap is in correct format (ARGB_8888)
            val processedBitmap = if (bitmap.config != Bitmap.Config.ARGB_8888) {
                bitmap.copy(Bitmap.Config.ARGB_8888, false)
            } else {
                bitmap
            }

            // Preprocess the image
            val tensorImage = TensorImage.fromBitmap(processedBitmap)
            val processedImage = imageProcessor.process(tensorImage)

            // Prepare output buffer
            val output = Array(1) { FloatArray(OUTPUT_SIZE) }

            // Run inference
            interpreter.run(processedImage.buffer, output)

            // Normalize the embedding (L2 normalization)
            val embedding = output[0]
            normalizeEmbedding(embedding)

            Log.d(TAG, "✓ Successfully generated embedding of size ${embedding.size}")
            embedding
        } catch (e: Exception) {
            Log.e(TAG, "Failed to generate embedding", e)
            null
        }
    }

    private fun normalizeEmbedding(embedding: FloatArray) {
        var sum = 0f
        for (value in embedding) {
            sum += value * value
        }
        val norm = sqrt(sum)
        if (norm > 0) {
            for (i in embedding.indices) {
                embedding[i] /= norm
            }
        }
    }

    fun calculateSimilarity(embedding1: FloatArray, embedding2: FloatArray): Float {
        if (embedding1.size != embedding2.size) {
            Log.e(TAG, "Embedding size mismatch: ${embedding1.size} vs ${embedding2.size}")
            return 0f
        }

        // Calculate cosine similarity
        var dotProduct = 0f
        for (i in embedding1.indices) {
            dotProduct += embedding1[i] * embedding2[i]
        }

        // Since embeddings are normalized, dotProduct is the cosine similarity
        // Convert to 0-1 range for easier interpretation
        val similarity = (dotProduct + 1f) / 2f
        return similarity
    }

    fun calculateL2Distance(embedding1: FloatArray, embedding2: FloatArray): Float {
        if (embedding1.size != embedding2.size) {
            Log.e(TAG, "Embedding size mismatch: ${embedding1.size} vs ${embedding2.size}")
            return Float.MAX_VALUE
        }

        var sum = 0f
        for (i in embedding1.indices) {
            val diff = embedding1[i] - embedding2[i]
            sum += diff * diff
        }
        return sqrt(sum)
    }

    fun compareEmbeddings(embedding1: FloatArray, embedding2: FloatArray): Boolean {
        // Use both cosine similarity and L2 distance for better accuracy
        val similarity = calculateSimilarity(embedding1, embedding2)
        val l2Distance = calculateL2Distance(embedding1, embedding2)

        Log.d(TAG, "Face comparison - Similarity: $similarity (threshold: $SIMILARITY_THRESHOLD), L2 Distance: $l2Distance (threshold: $L2_NORM_THRESHOLD)")

        // Face matches ONLY if BOTH similarity is high AND L2 distance is low
        // This prevents false positives by requiring both metrics to pass
        return similarity >= SIMILARITY_THRESHOLD && l2Distance <= L2_NORM_THRESHOLD
    }

    fun close() {
        interpreter?.close()
        interpreter = null
    }
}