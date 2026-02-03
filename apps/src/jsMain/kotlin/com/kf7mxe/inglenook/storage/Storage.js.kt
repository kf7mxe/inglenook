package com.kf7mxe.inglenook.storage

import com.juul.indexeddb.Key
import com.kf7mxe.inglenook.database
import com.kf7mxe.inglenook.databaseName
import com.kf7mxe.inglenook.getDatabase
import org.khronos.webgl.Uint8Array
import org.khronos.webgl.get

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