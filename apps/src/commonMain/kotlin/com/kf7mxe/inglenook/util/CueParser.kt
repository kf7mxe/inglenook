package com.kf7mxe.inglenook.util

import com.kf7mxe.inglenook.Chapter

/**
 * Parser for .cue files to extract chapter information.
 *
 * CUE files use the following format:
 * ```
 * TRACK 01 AUDIO
 *   TITLE "Chapter 1"
 *   INDEX 01 00:00:00
 * TRACK 02 AUDIO
 *   TITLE "Chapter 2"
 *   INDEX 01 05:30:15
 * ```
 *
 * The INDEX timestamp format is MM:SS:FF where:
 * - MM = minutes
 * - SS = seconds
 * - FF = frames (75 frames = 1 second)
 */
object CueParser {

    /**
     * Parse a .cue file content and return a list of chapters.
     */
    fun parse(cueContent: String): List<Chapter> {
        val chapters = mutableListOf<Chapter>()
        var currentTitle: String? = null
        var currentTrackNumber = 0

        val lines = cueContent.lines()

        for (line in lines) {
            val trimmedLine = line.trim()

            when {
                // Parse TRACK line - e.g., "TRACK 01 AUDIO"
                trimmedLine.startsWith("TRACK ", ignoreCase = true) -> {
                    val parts = trimmedLine.split(" ")
                    if (parts.size >= 2) {
                        currentTrackNumber = parts[1].toIntOrNull() ?: (currentTrackNumber + 1)
                    }
                    currentTitle = null // Reset title for new track
                }

                // Parse TITLE line - e.g., 'TITLE "Chapter Name"'
                trimmedLine.startsWith("TITLE ", ignoreCase = true) -> {
                    currentTitle = extractQuotedString(trimmedLine.substring(6))
                }

                // Parse INDEX 01 line - e.g., "INDEX 01 05:30:15"
                trimmedLine.startsWith("INDEX 01 ", ignoreCase = true) -> {
                    val timeString = trimmedLine.substring(9).trim()
                    val positionTicks = parseTimestamp(timeString)

                    if (positionTicks != null) {
                        val chapterName = currentTitle ?: "Chapter $currentTrackNumber"
                        chapters.add(Chapter(
                            name = chapterName,
                            startPositionTicks = positionTicks,
                            imageId = null
                        ))
                    }
                }
            }
        }

        return chapters.sortedBy { it.startPositionTicks }
    }

    /**
     * Extract a string value from quotes.
     * Handles both "double quotes" and 'single quotes'.
     */
    private fun extractQuotedString(input: String): String {
        val trimmed = input.trim()
        return when {
            trimmed.startsWith("\"") && trimmed.endsWith("\"") ->
                trimmed.substring(1, trimmed.length - 1)
            trimmed.startsWith("'") && trimmed.endsWith("'") ->
                trimmed.substring(1, trimmed.length - 1)
            else -> trimmed
        }
    }

    /**
     * Parse a CUE timestamp (MM:SS:FF) to ticks.
     * FF = frames where 75 frames = 1 second
     *
     * @return Position in ticks (10,000 ticks = 1ms), or null if parsing fails
     */
    private fun parseTimestamp(timestamp: String): Long? {
        val parts = timestamp.split(":")
        if (parts.size != 3) return null

        val minutes = parts[0].toIntOrNull() ?: return null
        val seconds = parts[1].toIntOrNull() ?: return null
        val frames = parts[2].toIntOrNull() ?: return null

        // Convert to total milliseconds
        // 75 frames = 1 second, so each frame = 1000/75 ms ≈ 13.33ms
        val totalMs = (minutes * 60 * 1000L) +
                      (seconds * 1000L) +
                      (frames * 1000L / 75)

        // Convert to ticks (10,000 ticks = 1ms)
        return totalMs * 10_000
    }
}
