package com.harry.launcher.data.cache

import android.content.ComponentCallbacks2
import android.util.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap

class IconCache(maxBytes: Int = defaultCacheSize()) {

    private val cache = object : LruCache<String, ImageBitmap>(maxBytes) {
        override fun sizeOf(key: String, value: ImageBitmap): Int {
            return value.asAndroidBitmap().byteCount
        }
    }

    fun get(key: String): ImageBitmap? = synchronized(cache) {
        cache.get(key)
    }

    fun put(key: String, bitmap: ImageBitmap) = synchronized(cache) {
        cache.put(key, bitmap)
    }

    fun remove(key: String) = synchronized(cache) {
        cache.remove(key)
    }

    fun clear() = synchronized(cache) {
        cache.evictAll()
    }

    fun trimMemory(level: Int) = synchronized(cache) {
        when {
            level >= ComponentCallbacks2.TRIM_MEMORY_COMPLETE -> cache.evictAll()
            level >= ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL -> cache.trimToSize(cache.maxSize() / 4)
            level >= ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN -> cache.trimToSize(cache.maxSize() / 2)
        }
    }

    companion object {
        private fun defaultCacheSize(): Int {
            // Allocate 1/8th of available runtime heap to bitmap caching
            val maxMemory = Runtime.getRuntime().maxMemory()
            return (maxMemory / 8).coerceAtMost(64L * 1024 * 1024).toInt()
        }
    }
}
