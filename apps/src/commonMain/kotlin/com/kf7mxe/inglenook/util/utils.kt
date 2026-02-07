package com.kf7mxe.inglenook.util

fun getFileExtensionFromMimeType(mimeType: String): String {
    when(mimeType) {
        "image/jpeg" -> return "jpg"
        "image/png" -> return "png"
        "video/mp4" -> return "mp4"
        "video/webm" -> return "webm"
        "video/ogg" -> return "ogg"
        else -> return ""
    }
}