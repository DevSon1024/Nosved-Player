package com.devson.nvplayer.util.thumbnail

import android.graphics.Bitmap
import android.util.LruCache

object ThumbnailMemoryCache {
    private val maxMemory = (Runtime.getRuntime().maxMemory() / 1024).toInt()
    private val cacheSize = maxMemory / 8

    private val cache = object : LruCache<ThumbnailKey, Bitmap>(cacheSize) {
        override fun sizeOf(key: ThumbnailKey, value: Bitmap): Int {
            return value.byteCount / 1024
        }
    }

    fun get(key: ThumbnailKey): Bitmap? {
        return cache.get(key)
    }

    fun put(key: ThumbnailKey, bitmap: Bitmap) {
        cache.put(key, bitmap)
    }

    fun clear() {
        cache.evictAll()
    }
}
