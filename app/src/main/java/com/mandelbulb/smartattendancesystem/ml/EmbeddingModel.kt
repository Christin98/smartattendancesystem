package com.mandelbulb.smartattendancesystem.ml

import android.graphics.Bitmap
import kotlin.math.sqrt
import kotlin.math.abs
import androidx.core.graphics.scale
import androidx.core.graphics.get
import java.security.MessageDigest

class EmbeddingModel(private val context: android.content.Context? = null) {
    
    fun generateEmbedding(faceBitmap: Bitmap): FloatArray {
        return getEmbedding(faceBitmap)
    }
    
    fun getEmbedding(faceBitmap: Bitmap): FloatArray {
        // Use higher resolution for better face discrimination
        val targetWidth = 64
        val targetHeight = 64
        val resized = faceBitmap.scale(targetWidth, targetHeight)
        
        // Create a more complex embedding with multiple features
        val features = mutableListOf<Float>()
        
        // 1. Extract color histogram features for each color channel
        val rHistogram = IntArray(16)
        val gHistogram = IntArray(16)
        val bHistogram = IntArray(16)
        
        // 2. Extract spatial features (divide image into regions)
        val regions = 4 // 4x4 grid = 16 regions
        val regionFeatures = Array(regions * regions) { FloatArray(3) }
        
        for (y in 0 until targetHeight) {
            for (x in 0 until targetWidth) {
                val pixel = resized[x, y]
                val r = (pixel shr 16) and 0xff
                val g = (pixel shr 8) and 0xff
                val b = pixel and 0xff
                
                // Update histograms
                rHistogram[r / 16]++
                gHistogram[g / 16]++
                bHistogram[b / 16]++
                
                // Update region features
                val regionX = (x * regions) / targetWidth
                val regionY = (y * regions) / targetHeight
                val regionIdx = regionY * regions + regionX
                regionFeatures[regionIdx][0] += r / 255f
                regionFeatures[regionIdx][1] += g / 255f
                regionFeatures[regionIdx][2] += b / 255f
            }
        }
        
        // Add histogram features (normalized)
        val pixelCount = targetWidth * targetHeight
        for (i in 0 until 16) {
            features.add(rHistogram[i].toFloat() / pixelCount)
            features.add(gHistogram[i].toFloat() / pixelCount)
            features.add(bHistogram[i].toFloat() / pixelCount)
        }
        
        // Add normalized region features
        val pixelsPerRegion = pixelCount / (regions * regions)
        for (region in regionFeatures) {
            features.add(region[0] / pixelsPerRegion)
            features.add(region[1] / pixelsPerRegion)
            features.add(region[2] / pixelsPerRegion)
        }
        
        // 3. Add edge detection features (simple gradient)
        for (y in 1 until targetHeight - 1) {
            for (x in 1 until targetWidth - 1 step 4) {
                val center = resized[x, y]
                val left = resized[x - 1, y]
                val right = resized[x + 1, y]
                val top = resized[x, y - 1]
                val bottom = resized[x, y + 1]
                
                val horizontalGrad = abs(((right shr 16) and 0xff) - ((left shr 16) and 0xff)) / 255f
                val verticalGrad = abs(((bottom shr 16) and 0xff) - ((top shr 16) and 0xff)) / 255f
                
                features.add(horizontalGrad)
                features.add(verticalGrad)
            }
        }
        
        // 4. Add unique face signature based on key facial points
        // Sample pixels from typical face feature locations
        val facePoints = listOf(
            // Eye region
            Pair(targetWidth * 0.3f, targetHeight * 0.35f),
            Pair(targetWidth * 0.7f, targetHeight * 0.35f),
            // Nose region
            Pair(targetWidth * 0.5f, targetHeight * 0.5f),
            // Mouth region
            Pair(targetWidth * 0.5f, targetHeight * 0.65f),
            // Cheek regions
            Pair(targetWidth * 0.25f, targetHeight * 0.5f),
            Pair(targetWidth * 0.75f, targetHeight * 0.5f)
        )
        
        for ((fx, fy) in facePoints) {
            val x = fx.toInt().coerceIn(0, targetWidth - 1)
            val y = fy.toInt().coerceIn(0, targetHeight - 1)
            val pixel = resized[x, y]
            features.add(((pixel shr 16) and 0xff) / 255f)
            features.add(((pixel shr 8) and 0xff) / 255f)
            features.add((pixel and 0xff) / 255f)
        }
        
        // Convert to fixed-size array and normalize
        val embedding = features.take(256).toFloatArray() // Fixed size of 256 features
        
        // Pad with zeros if needed
        if (embedding.size < 256) {
            return FloatArray(256) { i ->
                if (i < embedding.size) embedding[i] else 0f
            }
        }
        
        // L2 normalization for better similarity comparison
        var sum = 0.0
        for (v in embedding) sum += v * v
        val norm = sqrt(sum)
        if (norm > 0) {
            for (i in embedding.indices) {
                embedding[i] = (embedding[i] / norm).toFloat()
            }
        }
        
        return embedding
    }
}
