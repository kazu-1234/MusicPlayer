package com.example.musicplayer

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import java.util.Locale

// アルバムアートキャッシュ
object AlbumArtCache {
    private val maxMemory = (Runtime.getRuntime().maxMemory() / 1024).toInt()
    private val cacheSize = maxMemory / 8 // メモリの1/8を使用
    private val memoryCache = object : LruCache<String, Bitmap>(cacheSize) {
        override fun sizeOf(key: String, bitmap: Bitmap): Int {
            return bitmap.byteCount / 1024 // KB単位
        }
    }

    fun get(key: String): Bitmap? = memoryCache.get(key)
    fun put(key: String, bitmap: Bitmap) {
        if (get(key) == null) {
            memoryCache.put(key, bitmap)
        }
    }
}

// 画像リサイズ用ユーティリティ
fun calculateInSampleSize(options: BitmapFactory.Options, reqWidth: Int, reqHeight: Int): Int {
    val (height: Int, width: Int) = options.run { outHeight to outWidth }
    var inSampleSize = 1

    if (height > reqHeight || width > reqWidth) {
        val halfHeight: Int = height / 2
        val halfWidth: Int = width / 2

        while (inSampleSize * 2 <= 64 && (halfHeight / inSampleSize) >= reqHeight && (halfWidth / inSampleSize) >= reqWidth) {
            inSampleSize *= 2
        }
    }
    return inSampleSize
}

// 汎用フォーマッター (必要に応じて使用)
fun formatDuration(durationMs: Int): String {
    val minutes = (durationMs / 1000) / 60
    val seconds = (durationMs / 1000) % 60
    return String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds)
}

fun formatDuration(durationMs: Long): String = formatDuration(durationMs.toInt())
