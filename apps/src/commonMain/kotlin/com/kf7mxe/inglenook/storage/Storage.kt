package com.kf7mxe.inglenook.storage

import com.lightningkite.kiteui.FileReference
import com.lightningkite.kiteui.models.ImageSource
import com.lightningkite.kiteui.models.VideoSource
import kotlinx.serialization.ExperimentalSerializationApi

const val databaseVersion = 2


expect suspend fun getFileByteArray(fileName:String): ByteArray?
expect suspend fun saveFile(byteArray: ByteArray,fileName:String)



expect suspend fun saveImageToStorage(directoryName: String,fileName:String, image: ImageSource,fileExtension: String)
expect suspend fun deleteImageFromStorage(path:String)

//expect inline suspend fun <reified T :HasId<ID>, ID> upsertLocalStorage(model:T)
//expect inline suspend fun <reified T: HasId<ID>, ID> detailLocalStorage(id: ID): T?
//expect inline suspend fun <reified T: HasId<ID>, ID> deleteByIdLocalStorage(id: ID)
//expect inline suspend fun <reified T: HasId<ID>, ID> queryByIdsLocalStorage(ids: List<ID>): List<T>?
//expect inline suspend fun <reified T> queryAllLocalStorage() : List<T>
//expect inline suspend fun <reified T> deleteAllLocalStorage()
//expect suspend fun saveVideoToStorage(
//    directoryName: String,
//    fileName: String,
//    video: VideoSource,
//    fileExtension: String
//)
//
//expect suspend fun readVideoFromStorage(directoryName: String, fileName: String): VideoSource?
expect suspend fun readImageFromStorage(directoryName: String, fileName: String): ImageSource?
//expect suspend fun exportRecipes()
//expect suspend fun importRecipes(file: FileReference)
//
//expect suspend fun saveImageToStorage(directoryName: String,fileName:String, image: ImageSource,fileExtension: String)
