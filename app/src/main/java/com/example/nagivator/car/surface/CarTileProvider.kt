package com.example.nagivator.car.surface

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import com.example.nagivator.data.repository.MapColorScheme
import com.example.nagivator.ui.map.MapStyleHelper
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
    private val memoryCache = object : LruCache<String, Bitmap>(96) {
        override fun sizeOf(key: String, value: Bitmap): Int = 1
    }

    private val inFlightRequests = ConcurrentHashMap.newKeySet<String>()

    fun getTile(
        zoom: Int,
        x: Int,
        y: Int,
        isDark: Boolean = false,
        scheme: MapColorScheme = MapColorScheme.GRAPHITE
    ): Bitmap? {
        val rawKey = "${zoom}_${x}_$y"
        val cacheKey = "${rawKey}_${if (isDark) scheme.name else "DAY"}"

        // 1. Check Memory Cache (already styled)
        memoryCache.get(cacheKey)?.let { return it }

        // 2. Check Disk Cache or Queue Download (transform off the render thread)
        val diskFile = File(cacheDir, "$rawKey.png")
        if (diskFile.exists() && diskFile.length() > 0) {
            if (!isDark) {
                val bitmap = BitmapFactory.decodeFile(diskFile.absolutePath)
                if (bitmap != null) {
                    memoryCache.put(cacheKey, bitmap)
                    return bitmap
                }
            } else if (inFlightRequests.add(cacheKey)) {
                scope.launch {
                    try {
                        val rawBitmap = BitmapFactory.decodeFile(diskFile.absolutePath)
                        if (rawBitmap != null) {
                            val styled = MapStyleHelper.transformNightBitmap(rawBitmap, scheme)
                            rawBitmap.recycle()
                            memoryCache.put(cacheKey, styled)
                            onTileAvailable()
                        }
                    } catch (_: Exception) {
                        // Ignore decode failure
                    } finally {
                        inFlightRequests.remove(cacheKey)
                    }
                }
            }
            return null
        }

        // 3. Queue Download
        if (inFlightRequests.add(cacheKey)) {
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
                            val tempFile = File(cacheDir, "$rawKey.tmp")
                            FileOutputStream(tempFile).use { it.write(bytes) }
                            tempFile.renameTo(diskFile)

                            val rawBitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                            if (rawBitmap != null) {
                                val finalBitmap = if (isDark) {
                                    MapStyleHelper.transformNightBitmap(rawBitmap, scheme).also {
                                        rawBitmap.recycle()
                                    }
                                } else {
                                    rawBitmap
                                }
                                memoryCache.put(cacheKey, finalBitmap)
                                onTileAvailable()
                            }
                        }
                    }
                } catch (_: Exception) {
                    // Ignore network failure, will retry next frame
                } finally {
                    inFlightRequests.remove(cacheKey)
                }
            }
        }

        return null
    }

    fun clear() {
        memoryCache.evictAll()
    }
}
