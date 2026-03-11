package com.kf7mxe.inglenook.util

import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.sqrt

data class ImageData(val pixels: FloatArray, val width: Int, val height: Int)

data class RgbColor(val r: Float, val g: Float, val b: Float) {
    fun luminance(): Float = 0.299f * r + 0.587f * g + 0.114f * b

    fun saturation(): Float {
        val max = maxOf(r, g, b)
        val min = minOf(r, g, b)
        return if (max == 0f) 0f else (max - min) / max
    }

    fun toLab(): LabColor {
        // sRGB -> linear RGB
        fun linearize(c: Float): Float =
            if (c <= 0.04045f) c / 12.92f else ((c + 0.055f) / 1.055f).pow(2.4f)

        val lr = linearize(r)
        val lg = linearize(g)
        val lb = linearize(b)

        // Linear RGB -> XYZ (D65)
        var x = 0.4124564f * lr + 0.3575761f * lg + 0.1804375f * lb
        var y = 0.2126729f * lr + 0.7151522f * lg + 0.0721750f * lb
        var z = 0.0193339f * lr + 0.1191920f * lg + 0.9503041f * lb

        // Normalize to D65 white point
        x /= 0.95047f
        y /= 1.00000f
        z /= 1.08883f

        fun f(t: Float): Float =
            if (t > 0.008856f) t.pow(1f / 3f) else (7.787f * t + 16f / 116f)

        val fx = f(x)
        val fy = f(y)
        val fz = f(z)

        return LabColor(
            l = 116f * fy - 16f,
            a = 500f * (fx - fy),
            b = 200f * (fy - fz)
        )
    }

    fun toHexString(): String {
        val ri = (r.coerceIn(0f, 1f) * 255f).toInt()
        val gi = (g.coerceIn(0f, 1f) * 255f).toInt()
        val bi = (b.coerceIn(0f, 1f) * 255f).toInt()
        return "#" + ((ri shl 16) or (gi shl 8) or bi).toString(16).padStart(6, '0')
    }
}

data class LabColor(val l: Float, val a: Float, val b: Float) {
    fun distanceTo(other: LabColor): Float {
        val dl = l - other.l
        val da = a - other.a
        val db = b - other.b
        return sqrt(dl * dl + da * da + db * db)
    }
}

class ColorQuantizer(
    private val targetColors: Int,
    private val maxIterations: Int = 50
) {
    fun quantize(pixels: FloatArray, width: Int, height: Int): List<RgbColor> {
        require(pixels.size == width * height * 3) { "Pixel array size mismatch" }

        val totalPixels = width * height
        val maxSamples = 4096
        val step = maxOf(1, totalPixels / maxSamples)

        val sampledColors = ArrayList<RgbColor>(minOf(totalPixels, maxSamples))
        val sampledLabs = ArrayList<LabColor>(minOf(totalPixels, maxSamples))

        for (i in 0 until totalPixels step step) {
            val idx = i * 3
            if (idx + 2 < pixels.size) {
                val color = RgbColor(pixels[idx], pixels[idx + 1], pixels[idx + 2])
                sampledColors.add(color)
                sampledLabs.add(color.toLab())
            }
        }

        val centroids = sampledColors.shuffled().take(targetColors).toMutableList()
        while (centroids.size < targetColors) {
            centroids.add(RgbColor(0.5f, 0.5f, 0.5f))
        }

        repeat(maxIterations) {
            val clusterSums = Array(targetColors) { FloatArray(3) }
            val clusterCounts = IntArray(targetColors)
            val centroidLabs = centroids.map { it.toLab() }

            sampledLabs.forEachIndexed { index, lab ->
                var minDist = Float.MAX_VALUE
                var closestCentroid = 0

                for (centroidIdx in 0 until targetColors) {
                    val dist = lab.distanceTo(centroidLabs[centroidIdx])
                    if (dist < minDist) {
                        minDist = dist
                        closestCentroid = centroidIdx
                    }
                }

                val color = sampledColors[index]
                clusterSums[closestCentroid][0] += color.r
                clusterSums[closestCentroid][1] += color.g
                clusterSums[closestCentroid][2] += color.b
                clusterCounts[closestCentroid]++
            }

            var changed = false
            for (i in 0 until targetColors) {
                if (clusterCounts[i] > 0) {
                    val newCentroid = RgbColor(
                        clusterSums[i][0] / clusterCounts[i],
                        clusterSums[i][1] / clusterCounts[i],
                        clusterSums[i][2] / clusterCounts[i]
                    )
                    val diff = abs(newCentroid.r - centroids[i].r) +
                            abs(newCentroid.g - centroids[i].g) +
                            abs(newCentroid.b - centroids[i].b)
                    if (diff > 0.001f) {
                        centroids[i] = newCentroid
                        changed = true
                    }
                }
            }

            if (!changed) return@repeat
        }

        return centroids.sortedBy { it.luminance() }
    }

    private fun abs(v: Float): Float = if (v < 0) -v else v
}

fun extractDominantColors(
    pixels: FloatArray,
    width: Int,
    height: Int,
    colorCount: Int
): List<RgbColor> {
    val quantizer = ColorQuantizer(colorCount)
    return quantizer.quantize(pixels, width, height)
}

/**
 * Vibrancy score that considers both saturation and brightness.
 * Very dark or very light colors score low even if technically saturated.
 */
private fun RgbColor.vibrancy(): Float {
    val lum = luminance()
    // Penalize colors that are too dark or too light to look vibrant
    val brightnessFactor = 1f - (2f * abs(lum - 0.5f)).coerceIn(0f, 1f)
    return saturation() * (0.3f + 0.7f * brightnessFactor)
}

/**
 * Assigns extracted colors to theme roles.
 * Returns Triple(backgroundColor, primaryColor, accentColor) as hex strings.
 */
fun assignThemeColors(colors: List<RgbColor>): Triple<String, String, String> {
    if (colors.isEmpty()) return Triple("#1a1a2e", "#6366f1", "#3b3b4d")

    // Colors are sorted by luminance (darkest first)
    val backgroundIdx = 0
    val background = colors[backgroundIdx]

    // Primary = most vibrant color, excluding the background
    val candidateIndices = colors.indices.filter { it != backgroundIdx }
    val primaryIdx = if (candidateIndices.isNotEmpty()) {
        candidateIndices.maxByOrNull { colors[it].vibrancy() } ?: candidateIndices.first()
    } else {
        colors.lastIndex
    }
    val primary = colors[primaryIdx]

    // Outline/accent = pick from remaining colors (not background, not primary)
    val usedIndices = setOf(backgroundIdx, primaryIdx)
    val remainingIndices = colors.indices.filter { it !in usedIndices }

    val outline = if (remainingIndices.isNotEmpty()) {
        // Pick the second most vibrant from remaining
        val outlineIdx = remainingIndices.maxByOrNull { colors[it].vibrancy() } ?: remainingIndices.first()
        colors[outlineIdx]
    } else {
        // Fallback: lighten the background slightly
        RgbColor(
            (background.r + 0.15f).coerceIn(0f, 1f),
            (background.g + 0.15f).coerceIn(0f, 1f),
            (background.b + 0.15f).coerceIn(0f, 1f)
        )
    }

    return Triple(background.toHexString(), primary.toHexString(), outline.toHexString())
}
