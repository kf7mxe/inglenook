package com.kf7mxe.inglenook.cache

import com.lightningkite.kiteui.Blob
import com.lightningkite.kiteui.fetch
import com.lightningkite.kiteui.models.ImageRaw
import com.lightningkite.kiteui.models.ImageSource
import com.lightningkite.kiteui.views.AndroidAppContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

private fun getCacheDir(): File {
    val dir = File(AndroidAppContext.applicationCtx.cacheDir, "imageCache")
    if (!dir.exists()) dir.mkdirs()
    return dir
}

actual suspend fun fetchAndPersistImage(url: String, key: String): ImageSource? {
    return withContext(Dispatchers.IO) {
        try {
            val response = fetch(url)
            val blob = response.blob()
            val file = File(getCacheDir(), key)
            FileOutputStream(file).use { fos ->
                fos.write(blob.data)
                fos.flush()
            }
            ImageRaw(blob)
        } catch (e: Exception) {
            null
        }
    }
}

actual suspend fun loadPersistedImage(key: String): ImageSource? {
    return withContext(Dispatchers.IO) {
        try {
            val file = File(getCacheDir(), key)
            if (file.exists()) {
                val byteArray = FileInputStream(file).use { it.readBytes() }
                ImageRaw(Blob(byteArray, "image/jpeg"))
            } else null
        } catch (e: Exception) {
            null
        }
    }
}

actual suspend fun clearPersistedImageCache() {
    withContext(Dispatchers.IO) {
        val dir = File(AndroidAppContext.applicationCtx.cacheDir, "imageCache")
        if (dir.exists()) dir.deleteRecursively()
    }
}
