package com.example.facerecognizer.caching

import android.graphics.Bitmap
import androidx.collection.LruCache

class MyLRUCache(maxSize: Int): LruCache<String, Bitmap>(maxSize) {
    private var onBitMapAddedListener: OnBitmapAddedListener?= null

    fun setOnBitmapAddedListener(listener: OnBitmapAddedListener) {
        this.onBitMapAddedListener = listener
    }
    override fun sizeOf(key: String, value: Bitmap): Int {
        return value.byteCount
    }

    // Add an item to the cache
    fun addBitmapToMemoryCache(key: String, bitmap: Bitmap) {
        if (getBitmapFromMemoryCache(key) == null) {
            put(key, bitmap)
            onBitMapAddedListener?.onBitmapAdded(key, bitmap)
        }
    }

    // Retrieve an item from the cache
    private fun getBitmapFromMemoryCache(key: String): Bitmap? {
        return get(key)
    }
    // Remove an item from the cache
    fun removeBitmapFromMemoryCache(key: String) {
        remove(key)
    }
}
interface OnBitmapAddedListener {
    fun onBitmapAdded(key: String, bitmap: Bitmap)
}