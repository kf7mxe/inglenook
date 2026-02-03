package com.kf7mxe.inglenook.storage

import com.lightningkite.kiteui.views.AndroidAppContext
import java.io.File


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
