package com.kf7mxe.inglenook.storage

import com.kf7mxe.inglenook.storage.getFileByteArray
import com.kf7mxe.inglenook.storage.saveFile
import com.kf7mxe.inglenook.ModelTableVersionContainer
import com.lightningkite.kiteui.reactive.*
import com.lightningkite.reactive.context.*
import com.lightningkite.reactive.core.*
import com.lightningkite.reactive.extensions.*
import com.lightningkite.reactive.lensing.*
import com.lightningkite.services.database.Condition
import com.lightningkite.services.database.HasId
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

class ModelOfflineStoreApi<T, ID, UID>(
    val serializer: KSerializer<T>,
    private val className: String,
    private val filterCondition: Condition<T>,
) where T : HasId<ID>,
        ID : Comparable<ID>,
        UID : Comparable<UID>
{

    val items = Signal(emptyList<T>())
    private val loadMutex = Mutex()
    private var isLoaded = false
    private val localStorageFileName = "${className}_data.json"

    // A dedicated scope for background tasks that won't be cancelled with the UI
    val backgroundScope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    companion object {
    }

    init {
        // Launch a coroutine to pre-load data on initialization
        CoroutineScope(Dispatchers.Default).launch {
            ensureLoaded()
        }
    }

    /**
     * Ensures data is loaded. It prioritizes loading local data for immediate UI display,
     * then triggers a background sync with the remote source.
     */
    @PublishedApi
    internal suspend fun ensureLoaded() {
        if (isLoaded) return
        loadMutex.withLock {
            if (isLoaded) return@withLock

            val localData = loadFromLocalFile()
            if (localData != null) {
                // --- Offline First Path ---
                // 1. Immediately display local data
                items.value = localData
                isLoaded = true
                // 2. Launch background sync
                    persistItems()

            } else {
                // --- First Time / No Cache Path ---
                // No local data, fetch from remote before declaring loaded
                if(className=="GenericIngredient") return
                persistItems()
                isLoaded = true
            }
        }
    }




    /**
     * Deserializes and returns the list of items from the local JSON file.
     */
    private suspend fun loadFromLocalFile(): List<T>? {
        return try {
            getFileByteArray(localStorageFileName)?.let { byteArray ->
                val decodedString = byteArray.decodeToString()
                val container = Json.decodeFromString(ModelTableVersionContainer.serializer(), decodedString)
                Json.decodeFromString(ListSerializer(serializer), container.table)
            }
        } catch (e: Exception) {
            // Log error if needed
            println("Error loading from local file: ${e.message}")
            null
        }
    }


    /**
     * Serializes the current list of items and saves it to a local file.
     */
    suspend fun persistItems() {
        val jsonStringTable = Json.encodeToString(ListSerializer(serializer), items.value)
        val container = ModelTableVersionContainer(table = jsonStringTable)
        val jsonContainerString = Json.encodeToString(ModelTableVersionContainer.serializer(), container)
        saveFile(jsonContainerString.encodeToByteArray(), localStorageFileName)
    }

    suspend fun isEmpty(): Boolean {
        ensureLoaded()
        return items.value.isEmpty()
    }

    suspend fun detail(id: ID): T? {
        ensureLoaded()
        return items.value.find { it._id == id }
    }

    suspend inline fun upsert(model: T) {
        ensureLoaded()


        val updatedList = items.value.toMutableList()
        val existingIndex = updatedList.indexOfFirst { it._id == model._id }
        if (existingIndex != -1) {
            updatedList[existingIndex] = model
        } else {
            updatedList.add(model)
        }
        items.value = updatedList

        persistItems()
    }

    suspend inline fun deleteById(id: ID) {
        ensureLoaded()
        items.value = items.value.filter { it._id != id }
        persistItems()

    }

    suspend inline fun <reified T> deleteAll() {
//        deleteAllLocalStorage<T>()

        items.value = emptyList()
        persistItems()
    }
}