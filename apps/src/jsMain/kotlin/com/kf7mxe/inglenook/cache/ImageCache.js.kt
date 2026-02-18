package com.kf7mxe.inglenook.cache

import com.juul.indexeddb.Key
import com.kf7mxe.inglenook.database
import com.kf7mxe.inglenook.getDatabase
import com.lightningkite.kiteui.Blob
import com.lightningkite.kiteui.fetch
import com.lightningkite.kiteui.models.ImageRaw
import com.lightningkite.kiteui.models.ImageSource

actual suspend fun fetchAndPersistImage(url: String, key: String): ImageSource? {
    return try {
        database = database ?: getDatabase()
        val response = fetch(url)
        val blob = response.blob()
        database?.writeTransaction("imageCache") {
            val store = objectStore("imageCache")
            store.put(blob, Key(key))
        }
        ImageRaw(blob)
    } catch (e: Exception) {
        null
    }
}

actual suspend fun loadPersistedImage(key: String): ImageSource? {
    return try {
        database = database ?: getDatabase()
        val image = database?.transaction("imageCache") {
            val result = objectStore("imageCache").get(Key(key))
            if (result != undefined) result as Blob
            else null
        }
        image?.let { ImageRaw(it) }
    } catch (e: Exception) {
        null
    }
}

actual suspend fun clearPersistedImageCache() {
    try {
        database = database ?: getDatabase()
        database?.writeTransaction("imageCache") {
            objectStore("imageCache").clear()
        }
    } catch (_: Exception) {}
}
