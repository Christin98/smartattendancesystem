package com.mandelbulb.smartattendancesystem.util

import android.graphics.*
import android.media.Image
import android.util.Log
import androidx.exifinterface.media.ExifInterface
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import androidx.camera.core.ImageProxy
import android.graphics.Bitmap
import android.graphics.ImageFormat
import android.graphics.Rect
import android.graphics.YuvImage
import androidx.annotation.OptIn
import androidx.camera.core.ExperimentalGetImage

object BitmapUtils {
    /**
     * Load bitmap from file and apply EXIF rotation if needed
     */
    fun loadAndRotateBitmap(imagePath: String): Bitmap? {
        return try {
            val bitmap = BitmapFactory.decodeFile(imagePath)
            if (bitmap == null) {
                Log.e("BitmapUtils", "Failed to decode bitmap from path: $imagePath")
                return null
            }
            
            val exif = ExifInterface(imagePath)
            val orientation = exif.getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL
            )
            
            val rotationDegrees = when (orientation) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90
                ExifInterface.ORIENTATION_ROTATE_180 -> 180
                ExifInterface.ORIENTATION_ROTATE_270 -> 270
                else -> 0
            }
            
            Log.d("BitmapUtils", "Image orientation: $orientation, rotation: $rotationDegrees degrees")
            
            if (rotationDegrees != 0) {
                val matrix = Matrix()
                matrix.postRotate(rotationDegrees.toFloat())
                Bitmap.createBitmap(
                    bitmap,
                    0,
                    0,
                    bitmap.width,
                    bitmap.height,
                    matrix,
                    true
                )
            } else {
                bitmap
            }
        } catch (e: Exception) {
            Log.e("BitmapUtils", "Error loading/rotating bitmap", e)
            null
        }
    }
    
    /** Convert ImageProxy (YUV_420_888) to Bitmap synchronously and safely. */
    @OptIn(ExperimentalGetImage::class)
    fun imageProxyToBitmap(imageProxy: ImageProxy): Bitmap {
    val image = imageProxy.image
        ?: throw IllegalArgumentException("ImageProxy has no image")

    // Convert to NV21 byte array
    val nv21 = imageProxyToNv21(imageProxy)

    // Convert to JPEG then decode to Bitmap
    val yuvImage = YuvImage(nv21, ImageFormat.NV21, image.width, image.height, null)
    val out = ByteArrayOutputStream()
    yuvImage.compressToJpeg(Rect(0, 0, image.width, image.height), 80, out)
    val bytes = out.toByteArray()
    return BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
}

/** Convert ImageProxy YUV planes to NV21 byte array */
@OptIn(ExperimentalGetImage::class)
fun imageProxyToNv21(imageProxy: ImageProxy): ByteArray {
    val image = imageProxy.image ?: throw IllegalArgumentException("ImageProxy has no image")
    val width = image.width
    val height = image.height

    val yPlane = image.planes[0]
    val uPlane = image.planes[1]
    val vPlane = image.planes[2]

    val yBuffer = yPlane.buffer
    val uBuffer = uPlane.buffer
    val vBuffer = vPlane.buffer

    val yRowStride = yPlane.rowStride
    val uvRowStride = vPlane.rowStride
    val uvPixelStride = vPlane.pixelStride

    val nv21 = ByteArray(width * height * 3 / 2)
    var pos = 0

    // Y
    yBuffer.rewind()
    for (row in 0 until height) {
        val yRowStart = row * yRowStride
        yBuffer.position(yRowStart)
        yBuffer.get(nv21, pos, width)
        pos += width
    }

    // UV (NV21 -> VU interleaved)
    val chromaHeight = height / 2
    val chromaWidth = width / 2
    vBuffer.rewind()
    uBuffer.rewind()

    for (row in 0 until chromaHeight) {
        val vRowStart = row * uvRowStride
        val uRowStart = row * uvRowStride
        var col = 0
        while (col < chromaWidth) {
            val vIndex = vRowStart + col * uvPixelStride
            val uIndex = uRowStart + col * uvPixelStride
            nv21[pos++] = vBuffer.get(vIndex)
            nv21[pos++] = uBuffer.get(uIndex)
            col++
        }
    }
    return nv21
}

fun cropToBounds(src: Bitmap, left: Int, top: Int, width: Int, height: Int): Bitmap {
    val l = left.coerceAtLeast(0)
    val t = top.coerceAtLeast(0)
    val w = if (l + width > src.width) src.width - l else width
    val h = if (t + height > src.height) src.height - t else height
    return Bitmap.createBitmap(src, l, t, w, h)
}

fun saveThumbnail(bitmap: Bitmap, destFile: File, size: Int = 64): String {
    val thumb = Bitmap.createScaledBitmap(bitmap, size, size, true)
    FileOutputStream(destFile).use { out ->
        thumb.compress(Bitmap.CompressFormat.JPEG, 60, out)
    }
    return destFile.absolutePath
}

fun floatArrayToByteArray(arr: FloatArray): ByteArray {
    val bb = ByteBuffer.allocate(arr.size * 4).order(ByteOrder.LITTLE_ENDIAN)
    for (f in arr) bb.putFloat(f)
    return bb.array()
}

fun byteArrayToFloatArray(bytes: ByteArray?): FloatArray? {
    if (bytes == null) return null
    val fb = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
    val fa = FloatArray(bytes.size / 4)
    for (i in fa.indices) fa[i] = fb.getFloat(i * 4)
    return fa
}

/**
 * Convert Image (YUV_420_888 from CameraX ImageAnalysis) to Bitmap.
 */
fun yuvToBitmap(image: Image, rotationDegrees: Int = 0): Bitmap {
    val nv21 = yuv420ToNv21(image)
    val yuvImage = YuvImage(nv21, ImageFormat.NV21, image.width, image.height, null)
    val out = ByteArrayOutputStream()
    yuvImage.compressToJpeg(Rect(0, 0, image.width, image.height), 80, out)
    val bytes = out.toByteArray()
    var bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
    if (rotationDegrees != 0) {
        val matrix = Matrix()
        matrix.postRotate(rotationDegrees.toFloat())
        bmp = Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, matrix, true)
    }
    return bmp
}

private fun yuv420ToNv21(image: Image): ByteArray {
    val width = image.width
    val height = image.height
    val ySize = width * height
    val uvSize = width * height / 4

    val nv21 = ByteArray(ySize + uvSize * 2)
    val yBuffer = image.planes[0].buffer // Y
    val uBuffer = image.planes[1].buffer // U
    val vBuffer = image.planes[2].buffer // V

    var rowStride = image.planes[0].rowStride
    var pos = 0

    // Y channel
    for (row in 0 until height) {
        yBuffer.position(row * rowStride)
        yBuffer.get(nv21, pos, width)
        pos += width
    }

    // UV channels interleaved (NV21 format)
    rowStride = image.planes[2].rowStride
    val pixelStride = image.planes[2].pixelStride

    val v = ByteArray(vBuffer.remaining())
    vBuffer.get(v)
    val u = ByteArray(uBuffer.remaining())
    uBuffer.get(u)

    var offset = ySize
    for (row in 0 until height / 2) {
        var col = 0
        while (col < width) {
            val vuIndex = row * rowStride + col * pixelStride
            nv21[offset++] = v[vuIndex]
            nv21[offset++] = u[vuIndex]
            col += 2
        }
    }
    return nv21
}
} // End of BitmapUtils object
