package com.example.nagivator.car.surface

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

class CarTileProvider(
    context: Context,
    private val onTileAvailable: () -> Unit
) {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val client = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    private val cacheDir = File(context.cacheDir, "car_tiles").apply { mkdirs() }
    private val memoryCache = object : LruCache<String, Bitmap>(64) {
        override fun sizeOf(key: String, value: Bitmap): Int = 1
    }

    private val inFlightRequests = ConcurrentHashMap.newKeySet<String>()

    fun getTile(zoom: Int, x: Int, y: Int): Bitmap? {
        val key = "${zoom}_${x}_$y"
        
        // 1. Check Memory Cache
        memoryCache.get(key)?.let { return it }

        // 2. Check Disk Cache
        val diskFile = File(cacheDir, "$key.png")
        if (diskFile.exists() && diskFile.length() > 0) {
            val bitmap = BitmapFactory.decodeFile(diskFile.absolutePath)
            if (bitmap != null) {
                memoryCache.put(key, bitmap)
                return bitmap
            }
        }

        // 3. Queue Download
        if (inFlightRequests.add(key)) {
            scope.launch {
                try {
                    val url = "https://tile.openstreetmap.de/$zoom/$x/$y.png"
                    val request = Request.Builder()
                        .url(url)
                        .header("User-Agent", "Nagivator/1.0 (Android Auto App)")
                        .build()
                    val response = client.newCall(request).execute()
                    if (response.isSuccessful) {
                        val bytes = response.body()?.bytes()
                        if (bytes != null && bytes.isNotEmpty()) {
                            val tempFile = File(cacheDir, "$key.tmp")
                            FileOutputStream(tempFile).use { it.write(bytes) }
                            tempFile.renameTo(diskFile)

                            val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                            if (bitmap != null) {
                                memoryCache.put(key, bitmap)
                                onTileAvailable()
                            }
                        }
                    }
                } catch (_: Exception) {
                    // Ignore network failure, will retry next frame
                } finally {
                    inFlightRequests.remove(key)
                }
            }
        }

        return null
    }

    fun clear() {
        memoryCache.evictAll()
    }
}
