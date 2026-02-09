package com.kf7mxe.inglenook.ebook

import com.lightningkite.kiteui.reactive.PersistentProperty
import kotlinx.serialization.Serializable

@Serializable
enum class ReaderTheme(val displayName: String) {
    Light("Light"),
    Dark("Dark"),
    Sepia("Sepia")
}

@Serializable
data class ReadingPreferences(
    val fontSize: Float = 1.0f,       // Scale factor (0.5 - 3.0)
    val theme: ReaderTheme = ReaderTheme.Light,
    val scrollMode: Boolean = false   // false = paginated, true = scroll
)

val persistedReadingPreferences = PersistentProperty("readingPrefs", ReadingPreferences())
