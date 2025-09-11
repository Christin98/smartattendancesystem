package com.mandelbulb.smartattendancesystem.data

import androidx.room.TypeConverter
import java.nio.ByteBuffer
import java.nio.ByteOrder

class Converters {
    @TypeConverter
    fun floatArrayToByteArray(arr: FloatArray?): ByteArray? {
        if (arr == null) return null
        val bb = ByteBuffer.allocate(arr.size * 4).order(ByteOrder.LITTLE_ENDIAN)
        for (f in arr) bb.putFloat(f)
        return bb.array()
    }

    @TypeConverter
    fun byteArrayToFloatArray(bytes: ByteArray?): FloatArray? {
        if (bytes == null) return null
        val fb = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        val fa = FloatArray(bytes.size / 4)
        for (i in fa.indices) fa[i] = fb.getFloat(i * 4)
        return fa
    }
}
