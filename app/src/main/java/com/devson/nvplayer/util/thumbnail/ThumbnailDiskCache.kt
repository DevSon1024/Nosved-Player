package com.devson.nvplayer.util.thumbnail

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.File
import java.io.FileOutputStream

class ThumbnailDiskCache(context: Context) {
    private val cacheDir = File(context.cacheDir, "video_thumbs").apply {
        if (!exists()) mkdirs()
    }

    fun get(key: ThumbnailKey): Bitmap? {
        val file = File(cacheDir, key.toCacheFileName())
        if (!file.exists()) return null
        return try {
            BitmapFactory.decodeFile(file.absolutePath)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun put(key: ThumbnailKey, bitmap: Bitmap) {
        val file = File(cacheDir, key.toCacheFileName())
        try {
            FileOutputStream(file).use { out ->
                bitmap.compress(Bitmap.CompressFormat.WEBP, 80, out)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun clear() {
        try {
            cacheDir.deleteRecursively()
            cacheDir.mkdirs()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
