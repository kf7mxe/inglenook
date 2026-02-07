package com.kf7mxe.inglenook.storage

import com.juul.indexeddb.Key
import com.kf7mxe.inglenook.database
import com.kf7mxe.inglenook.databaseName
import com.kf7mxe.inglenook.getDatabase
import com.lightningkite.kiteui.Blob
import com.lightningkite.kiteui.fetch
import com.lightningkite.kiteui.models.ImageLocal
import com.lightningkite.kiteui.models.ImageRaw
import com.lightningkite.kiteui.models.ImageRemote
import com.lightningkite.kiteui.models.ImageResource
import com.lightningkite.kiteui.models.ImageSource
import com.lightningkite.kiteui.models.ImageVector
import org.khronos.webgl.Uint8Array
import org.khronos.webgl.get
import org.w3c.dom.url.URL

actual suspend fun getFileByteArray(fileName: String): ByteArray? {
    database = database ?: getDatabase()
    return try {
        database?.transaction(databaseName) {
            val result = objectStore(databaseName).get(Key(fileName))
            if (result != undefined) {
                val uint8Array = result as Uint8Array
                ByteArray(uint8Array.length) { uint8Array[it] }
            } else {
                null
            }
        }
    } catch (e: Exception) {
        null
    }
}

actual suspend fun saveFile(byteArray: ByteArray, fileName: String) {
    database = database ?: getDatabase()
    database?.writeTransaction(databaseName) {
        val store = objectStore(databaseName)
        val uint8Array = Uint8Array(byteArray.size)
        uint8Array.set(byteArray.toTypedArray(), 0)
        store.put(uint8Array, Key(fileName))
    }
}


fun getCacheFileName(localPath: String): String {
    println("DEBUG localPath: $localPath")
    return localPath
//    val isUrl = localPath.contains("http:") || localPath.contains("https:")
//    return if (isUrl){
//        localPath.split("/").last().split(".").first() + ".jpg"
//    }
//    else {
//        println("DEBUG localPath2 ${localPath} ")
//        val splitDirectoryAndFileName = localPath.split("/")
//        println("DEBUG getCacheFileName 1 ${splitDirectoryAndFileName[0]}")
//        val directoryName = splitDirectoryAndFileName[0]
//        println("DEBUG getCacheFileName 2 ${splitDirectoryAndFileName[1]}")
//        val fileName = splitDirectoryAndFileName[1]
//        println("DEBUG getCacheFileName 3 ${splitDirectoryAndFileName[2]}")
//        "${fileName}"
//    }
}

actual suspend fun saveImageToStorage(
    directoryName: String,
    fileName: String,
    image: ImageSource,
    fileExtension: String
) {
   database = database ?: getDatabase()
    when (image) {
        null -> return
        is ImageRemote -> {
            fetch(image.url).let { response ->
                response.blob().let {
                    database?.writeTransaction("images") {
                        val store = objectStore("images")
                        store.put(it, Key("${directoryName}/${fileName}.${fileExtension}"))
                    }
                }
            }
        }

        is ImageRaw -> {

        }

        is ImageResource -> {

        }

        is ImageLocal -> {
//            images
            fetch(URL.Companion.createObjectURL(image.file)).let { response ->
                println("image response ${response.status}")
                response.blob().let {
                    println("it ${it.size}")
                    database?.writeTransaction("images") {
                        val store = objectStore("images")
                        println("save image")
                        println("image size ${fileExtension}")
                        println(it)
                        store.put(it, Key("${directoryName}/${fileName}.${fileExtension}"))
                    }
                    Unit
                }
            }

        }

        is ImageVector -> {

        }

        else -> {}
    }
}

actual suspend fun deleteImageFromStorage(path: String) {
    database = database ?: getDatabase()
    database?.writeTransaction(path) {
        val store = objectStore(path)
        store.delete(Key(path))
    }
}

actual suspend fun readImageFromStorage(directoryName: String, fileName: String): ImageSource? {
    return try {
        println("DEBUG read from storage")
        database = database ?: getDatabase()
        val image = database?.transaction("images") {
            val result = objectStore("images").get(Key("${directoryName}/${fileName}"))
            if (result != undefined) {
                result as? Blob
            } else {
                null
            }
        }
        println("DEBUG read from storage done")
        image?.let { ImageRaw(it) }
    } catch (exception: Exception) {
        println("exception ${exception.message}")
        null
    }
}