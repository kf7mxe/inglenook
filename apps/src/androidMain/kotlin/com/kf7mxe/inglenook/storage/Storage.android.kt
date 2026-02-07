package com.kf7mxe.inglenook.storage

import android.os.Build
import android.os.FileUtils.copy
import android.webkit.MimeTypeMap
import androidx.annotation.RequiresApi
import com.lightningkite.kiteui.Blob
import com.lightningkite.kiteui.client
import com.lightningkite.kiteui.models.ImageLocal
import com.lightningkite.kiteui.models.ImageRaw
import com.lightningkite.kiteui.models.ImageRemote
import com.lightningkite.kiteui.models.ImageResource
import com.lightningkite.kiteui.models.ImageSource
import com.lightningkite.kiteui.models.ImageVector
import com.lightningkite.kiteui.views.AndroidAppContext
import io.ktor.client.call.body
import io.ktor.client.request.prepareGet
import io.ktor.http.isSuccess
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.readAvailable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream


actual suspend fun getFileByteArray(fileName: String): ByteArray? {
    println("DEBUG getFileByteArray")
    val file = File(AndroidAppContext.applicationCtx.filesDir, fileName)
    if(!file.exists()) return null
    return file.readBytes()
}

actual suspend fun saveFile(byteArray: ByteArray, fileName: String) {
    val file = File(AndroidAppContext.applicationCtx.filesDir, fileName)
    if(!file.exists()) file.createNewFile()
    file.writeBytes(byteArray)
}


@RequiresApi(Build.VERSION_CODES.Q)
actual suspend fun saveImageToStorage(
    directoryName: String,
    fileName: String,
    image: ImageSource,
    fileExtension: String
) {
    println("DEBUG remoteSync saveImageToStorage image ${image}")
    when (image) {
        null -> return
        is ImageRemote -> {
            client.prepareGet(image.url)

            val directory = File(AndroidAppContext.applicationCtx.filesDir, directoryName)
            if (!directory.exists()) {
                directory.mkdirs()
            }
            println("After make directory")
            val savedImageFile = File(directory, "$fileName.$fileExtension")
            println("After create image file")
            try {
                client.prepareGet(image.url).execute { response ->
                    if (response.status.isSuccess()) {
                        val channel: ByteReadChannel = response.body()
                        FileOutputStream(savedImageFile).use { outputStream ->
                            val buffer = ByteArray(8192) // 8KB buffer
                            while (!channel.isClosedForRead) {
                                val bytesRead = channel.readAvailable(buffer, 0, buffer.size)
                                if (bytesRead > 0) {
                                    outputStream.write(buffer, 0, bytesRead)
                                }
                            }
                            println("DEBUG saveImageToStorage done")
                            outputStream.close()
                        }

                        true
                    } else {
                        false
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                false
            } finally {
//                client.close()
            }



//            val imageToSave = fetch(image.url)



//            withContext(Dispatchers.IO) {
//                savedImageFile.createNewFile()
//                try {
//                    val oStream = FileOutputStream(savedImageFile)
//                    oStream.wrt
//                    oStream.flush()
//                } catch (e: Exception) {
//                    e.printStackTrace()
//                }
//                return@withContext true
//            }
        }

        is ImageRaw -> {

        }

        is ImageResource -> {

        }

        is ImageLocal -> {
            println("DEBUG remoteSync saveImageToStorage ${fileExtension}")
//            images
            println("file extension is $fileExtension")
            val directory = File(AndroidAppContext.applicationCtx.filesDir, directoryName)
            if (!directory.exists()) {
                directory.mkdirs()
            }
            val savedImageFile = File(directory, "$fileName.$fileExtension")
            withContext(Dispatchers.IO) {
                savedImageFile.createNewFile()
                try {
                    val oStream = FileOutputStream(savedImageFile)
                    val inputStream =
                        AndroidAppContext.applicationCtx.contentResolver.openInputStream(image.file.uri)
                    inputStream?.let {
                        copy(inputStream, oStream)
                    }
                    oStream.flush()
                } catch (e: Exception) {
                    e.printStackTrace()
                }
                return@withContext true
            }
        }

        is ImageVector -> {

        }

        else -> {}
    }
    return
}

actual suspend fun deleteImageFromStorage(path:String) {
    val fileToDelete = File(AndroidAppContext.applicationCtx.filesDir, path)
    fileToDelete.delete()

}

actual suspend fun readImageFromStorage(directoryName: String, fileName: String): ImageSource? {
    val directory = File(AndroidAppContext.applicationCtx.filesDir, directoryName)
    val file = File(directory, fileName)
    if (file.exists()) {
        val fileType = MimeTypeMap.getSingleton().getMimeTypeFromExtension(file.extension)
        try {
            println("start of try")
            val byteArray = withContext(Dispatchers.IO) {
                FileInputStream(file).use {
                    it.readBytes()
                }
            }
            println("Load byteArray")
            val blob = fileType?.let { Blob(byteArray, it) }
            println("create Blob")
            return blob?.let { ImageRaw(it) }
        } catch (e: Exception) {
            println("error reading image from storage")
            e.printStackTrace()
        }
    }
    return null
}