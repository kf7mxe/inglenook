package com.kf7mxe.inglenook


import com.juul.indexeddb.Database
import com.juul.indexeddb.openDatabase

import kotlinx.browser.window
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonBuilder
import kotlinx.serialization.json.encodeToDynamic
import com.juul.indexeddb.Key
import com.lightningkite.kiteui.Blob
import com.lightningkite.kiteui.FileReference
import com.lightningkite.kiteui.fetch
import com.lightningkite.kiteui.models.ImageLocal
import com.lightningkite.kiteui.models.ImageRaw
import com.lightningkite.kiteui.models.ImageRemote
import com.lightningkite.kiteui.models.ImageResource
import com.lightningkite.kiteui.models.ImageSource
import com.lightningkite.kiteui.models.ImageVector
import com.lightningkite.kiteui.models.VideoLocal
import com.lightningkite.kiteui.models.VideoRaw
import com.lightningkite.kiteui.models.VideoRemote
import com.lightningkite.kiteui.models.VideoResource
import com.lightningkite.kiteui.models.VideoSource
import kotlinx.serialization.json.decodeFromDynamic
import org.khronos.webgl.Uint8Array
import org.khronos.webgl.set
import org.w3c.dom.url.URL
import org.khronos.webgl.get

var database: Database? = null

val databaseName = "Recipe_data.json"

//@OptIn(ExperimentalSerializationApi::class)
//actual suspend inline fun <reified T :HasId<ID>, ID> upsertLocalStorage(model: T) {
//    val className = T::class.simpleName ?: "Unknown"
//
//    try {
//        database = database ?: getDatabase()
//        database?.writeTransaction(className) {
//            val store = objectStore(className)
//            val modelToDynamic = Json.encodeToDynamic(model)
//            store.put(modelToDynamic, Key(model._id.toString()))
//        }
//
//    } catch (exception: Exception) {
//        println("Error ${exception.message}")
//    }
//}

//@OptIn(ExperimentalSerializationApi::class)
//actual suspend inline fun <reified T : HasId<ID>, ID> detailLocalStorage(id: ID): T? {
//    val className = T::class.simpleName ?: "Unknown"
//    try {
//        database = database ?: getDatabase()
//        val result = database?.transaction(className) {
//            val store = objectStore(className).get(Key(id.toString()))
//            Json.decodeFromDynamic<T>(store)
//        }
//        return result
//    } catch (exception: Exception) {
//        println("Error ${exception.message}")
//        return null
//    }
//}

//@OptIn(ExperimentalSerializationApi::class)
//actual suspend inline fun <reified T : HasId<ID>, ID> queryByIdsLocalStorage(ids: List<ID>): List<T>? {
//    val className = T::class.simpleName ?: "Unknown"
//    try {
//        database = database ?: getDatabase()
//        val result = database?.transaction(className) {
//            val store = objectStore(className).getAll()
//            Json.decodeFromDynamic<List<T>>(store).filter { it._id in ids }
//        }
//        return result
//    } catch (exception: Exception) {
//        println("Error ${exception.message}")
//        return null
//    }
//}

//@OptIn(ExperimentalSerializationApi::class)
//actual suspend inline fun <reified T> queryAllLocalStorage(): List<T> {
//    val className = T::class.simpleName ?: "Unknown"
//    try {
//        database = database ?: getDatabase()
//        val result = database?.transaction(className) {
//            val store = objectStore(className).getAll()
//            Json.decodeFromDynamic<List<T>>(store)
//        }
//        return result ?: emptyList()
//    } catch (exception: Exception) {
//        println("Error ${exception.message}")
//        return emptyList()
//    }
//}

//actual suspend inline fun <reified T : HasId<ID>, ID> deleteByIdLocalStorage(id: ID) {
//    val className = T::class.simpleName ?: "Unknown"
//    try {
//        database = database ?: getDatabase()
//        database?.writeTransaction(className) {
//            val store = objectStore(className)
//            store.delete(Key(id.toString()))
//        }
//    } catch (exception: Exception) {
//        println("Error ${exception.message}")
//    }
//}

//actual suspend inline fun <reified T> deleteAllLocalStorage() {
//    val className = T::class.simpleName ?: "Unknown"
//    try {
//        database = database ?: getDatabase()
//        database?.writeTransaction(className) {
//            val store = objectStore(className)
//            store.clear()
//        }
//    } catch (exception: Exception) {
//        println("Error ${exception.message}")
//    }
//}

suspend fun getDatabase(): Database {
    println("databaseVersion ${databaseVersion}")
    val database = openDatabase("foodecision", databaseVersion) { database, oldVersion, newVersion ->
        if (oldVersion < 1) {
            val users = database.createObjectStore("User_data.json")
            val recipes = database.createObjectStore("Recipe_data.json")
            val mealPlans = database.createObjectStore("MealPlan_data.json")
            val genericIngredient_offline = database.createObjectStore("GenericIngredient_offline.json")
            val storeProduct_offline = database.createObjectStore("StoreProduct_offline.jso")
            val userIngredientPrefernceFileName = database.createObjectStore("UserIngredientPreference_offline.json")
            val images = database.createObjectStore("images")
            val blurred_images = database.createObjectStore("blurred_images")
            val blurredImageCache = database.createObjectStore("blurredImageCache")
            val thumbnails = database.createObjectStore("thumbnails")
            val thumbnailCache = database.createObjectStore("thumbnailCache")
        } else {
            println("old version less than 1")
        }
    }
    println("open database")
    return database
}

//actual suspend fun saveImageToStorage(
//    directoryName: String,
//    fileName: String,
//    image: ImageSource,
//    fileExtension: String
//) {
//    database = database ?: getDatabase()
//    when (image) {
//        null -> return
//        is ImageRemote -> {
//            fetch(image.url).let { response ->
//                response.blob().let {
//                    database?.writeTransaction("images") {
//                        val store = objectStore("images")
//                        store.put(it, Key("${directoryName}/${fileName}.${fileExtension}"))
//                    }
//                }
//            }
//        }
//
//        is ImageRaw -> {
//
//        }
//
//        is ImageResource -> {
//
//        }
//
//        is ImageLocal -> {
////            images
//            fetch(URL.Companion.createObjectURL(image.file)).let { response ->
//                println("image response ${response.status}")
//                response.blob().let {
//                    println("it ${it.size}")
//                    database?.writeTransaction("images") {
//                        val store = objectStore("images")
//                        println("save image")
//                        println("image size ${fileExtension}")
//                        println(it)
//                        store.put(it, Key("${directoryName}/${fileName}.${fileExtension}"))
//                    }
//                    Unit
//                }
//            }
//
//        }
//
//        is ImageVector -> {
//
//        }
//
//        else -> {}
//    }
//}
//
//actual suspend fun saveVideoToStorage(
//    directoryName: String,
//    fileName: String,
//    video: VideoSource,
//    fileExtension: String
//) {
//    try {
//        database = database ?: getDatabase()
//        when (video) {
//            null -> return
//            is VideoRemote -> {
//
//            }
//
//            is VideoRaw -> {
//
//            }
//
//            is VideoResource -> {
//
//            }
//
//            is VideoLocal -> {
////            videos
//                fetch(URL.Companion.createObjectURL(video.file)).let { response ->
//                    val videoBlob = response.blob().let {
//                        database?.writeTransaction("videos") {
//                            val store = objectStore("videos")
//                            store.add(it, Key("${directoryName}/${fileName}"))
//                        }
//                        Unit
//                    }
//                }
//            }
//        }
//    } catch (exception: Exception) {
//        println("exception ${exception.message}")
//    }
//}
//
//actual suspend fun readImageFromStorage(directoryName: String, fileName: String): ImageSource? {
//    return try {
//        database = database ?: getDatabase()
//        val image = database?.transaction("images") {
//            val result = objectStore("images").get(Key("${directoryName}/${fileName}"))
//            if (result != undefined) {
//                result as? Blob
//            } else {
//                null
//            }
//        }
//        image?.let { ImageRaw(it) }
//    } catch (exception: Exception) {
//        println("exception ${exception.message}")
//        null
//    }
//}
//
//actual suspend fun readVideoFromStorage(directoryName: String, fileName: String): VideoSource? {
//    try {
//        database = database ?: getDatabase()
//        val video = database?.transaction("videos") {
//            val result = objectStore("videos").get(Key("${directoryName}/${fileName}"))
//            result as Blob
//        }
//        return video?.let { VideoRaw(it) }
//    } catch (exception: Exception) {
//        println("exception ${exception.message}")
//        return null
//    }
//}
//
//
//actual suspend fun getFileByteArray(fileName: String): ByteArray? {
//    database = database ?: getDatabase()
//    return try {
//        database?.transaction(databaseName) {
//            val result = objectStore(databaseName).get(Key(fileName))
//            if (result != undefined) {
//                val uint8Array = result as Uint8Array
//                ByteArray(uint8Array.length) { uint8Array[it] }
//            } else {
//                null
//            }
//        }
//    } catch (e: Exception) {
//        null
//    }
//}
//
//actual suspend fun saveFile(byteArray: ByteArray, fileName: String) {
//    database = database ?: getDatabase()
//    database?.writeTransaction(databaseName) {
//        val store = objectStore(databaseName)
//        val uint8Array = Uint8Array(byteArray.size)
//        uint8Array.set(byteArray.toTypedArray(), 0)
//        store.put(uint8Array, Key(fileName))
//    }
//}
//
//
//actual suspend fun importRecipes(file: FileReference) {
//}
//
//actual suspend fun deleteImageFromStorage(path: String) {
//    database = database ?: getDatabase()
//    database?.writeTransaction(path) {
//        val store = objectStore(path)
//        store.delete(Key(path))
//    }
//}