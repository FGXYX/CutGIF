package com.fgxyx.cutgif.gif

import kotlin.math.max

data class QuantizedFrame(
    val palette: IntArray,
    val indices: ByteArray,
)

/**
 * Median-cut colour quantizer with a reusable 15-bit nearest-colour cache.
 * A palette can be built once from representative frames and then reused for
 * every GIF frame, which keeps exports fast and makes the GIF colour table
 * stable from first to last frame.
 */
object ColorQuantizer {

    private val bayer4 = intArrayOf(
        0, 8, 2, 10,
        12, 4, 14, 6,
        3, 11, 1, 9,
        15, 7, 13, 5,
    )

    fun quantize(
        pixels: IntArray,
        imageWidth: Int,
        maxColors: Int,
        ditherStrength: Int,
    ): QuantizedFrame {
        val palette = buildPaletteFromFrames(listOf(pixels), maxColors)
        val indices = PaletteMapper(palette).map(pixels, imageWidth, ditherStrength)
        return QuantizedFrame(palette, indices)
    }

    fun buildPaletteFromFrames(
        frames: List<IntArray>,
        maxColors: Int,
        samplesPerFrame: Int = 12_000,
    ): IntArray {
        require(frames.isNotEmpty()) { "At least one frame is required" }
        val histogram = HashMap<Int, Int>(8192)
        for (pixels in frames) {
            val stride = max(1, pixels.size / samplesPerFrame)
            var index = 0
            while (index < pixels.size) {
                val color = pixels[index] and 0xFFFFFF
                histogram[color] = (histogram[color] ?: 0) + 1
                index += stride
            }
        }
        if (histogram.isEmpty()) return intArrayOf(0)
        return buildPalette(histogram, maxColors.coerceIn(2, 256))
    }

    fun mapper(palette: IntArray): PaletteMapper = PaletteMapper(palette)

    private fun buildPalette(histogram: Map<Int, Int>, maxColors: Int): IntArray {
        data class Box(
            val colors: MutableList<Int>,
            var weight: Long,
        )

        val boxes = mutableListOf(
            Box(
                colors = histogram.keys.toMutableList(),
                weight = histogram.values.sumOf { it.toLong() },
            ),
        )

        fun channel(color: Int, channel: Int): Int = when (channel) {
            0 -> (color shr 16) and 0xFF
            1 -> (color shr 8) and 0xFF
            else -> color and 0xFF
        }

        while (boxes.size < maxColors) {
            var bestIndex = -1
            var bestScore = -1L
            var bestChannel = 0
            for (i in boxes.indices) {
                val box = boxes[i]
                if (box.colors.size < 2) continue
                for (c in 0..2) {
                    var minValue = 255
                    var maxValue = 0
                    for (color in box.colors) {
                        val value = channel(color, c)
                        if (value < minValue) minValue = value
                        if (value > maxValue) maxValue = value
                    }
                    val score = (maxValue - minValue).toLong() * box.weight
                    if (score > bestScore) {
                        bestScore = score
                        bestIndex = i
                        bestChannel = c
                    }
                }
            }
            if (bestIndex < 0) break

            val box = boxes.removeAt(bestIndex)
            val sorted = box.colors.sortedWith(
                compareBy<Int> { channel(it, bestChannel) }
                    .thenBy { channel(it, (bestChannel + 1) % 3) }
                    .thenBy { channel(it, (bestChannel + 2) % 3) },
            )
            var accumulated = 0L
            var splitAt = sorted.size / 2
            for (i in sorted.indices) {
                accumulated += histogram[sorted[i]] ?: 0
                if (accumulated >= box.weight / 2 && i in 1 until sorted.lastIndex) {
                    splitAt = i + 1
                    break
                }
            }
            splitAt = splitAt.coerceIn(1, sorted.size - 1)
            val leftColors = sorted.subList(0, splitAt).toMutableList()
            val rightColors = sorted.subList(splitAt, sorted.size).toMutableList()
            val leftWeight = leftColors.sumOf { (histogram[it] ?: 0).toLong() }
            val rightWeight = rightColors.sumOf { (histogram[it] ?: 0).toLong() }
            boxes.add(Box(leftColors, leftWeight))
            boxes.add(Box(rightColors, rightWeight))
        }

        val palette = IntArray(boxes.size)
        for ((i, box) in boxes.withIndex()) {
            var r = 0.0
            var g = 0.0
            var b = 0.0
            var total = 0L
            for (color in box.colors) {
                val weight = (histogram[color] ?: 0).toLong()
                r += ((color shr 16) and 0xFF) * weight
                g += ((color shr 8) and 0xFF) * weight
                b += (color and 0xFF) * weight
                total += weight
            }
            palette[i] = if (total > 0) {
                ((r / total).toInt() shl 16) or ((g / total).toInt() shl 8) or (b / total).toInt()
            } else {
                box.colors.first() and 0xFFFFFF
            }
        }
        return palette
    }

    class PaletteMapper(private val palette: IntArray) {
        private val cache = IntArray(1 shl 15) { -1 }

        fun map(
            pixels: IntArray,
            imageWidth: Int,
            ditherStrength: Int,
        ): ByteArray {
            val indices = ByteArray(pixels.size)
            val rowLength = imageWidth.coerceAtLeast(1)
            var x = 0
            var y = 0
            for (i in pixels.indices) {
                val pixel = pixels[i]
                var r = (pixel shr 16) and 0xFF
                var g = (pixel shr 8) and 0xFF
                var b = pixel and 0xFF

                if (ditherStrength > 0) {
                    val threshold = bayer4[((y and 3) shl 2) or (x and 3)] - 8
                    val offset = threshold * ditherStrength / 8
                    r = (r + offset).coerceIn(0, 255)
                    g = (g + offset).coerceIn(0, 255)
                    b = (b + offset).coerceIn(0, 255)
                }

                val key = ((r shr 3) shl 10) or ((g shr 3) shl 5) or (b shr 3)
                var nearest = cache[key]
                if (nearest < 0) {
                    nearest = nearestColor(palette, r, g, b)
                    cache[key] = nearest
                }
                indices[i] = nearest.toByte()

                x++
                if (x >= rowLength) {
                    x = 0
                    y++
                }
            }
            return indices
        }
    }

    private fun nearestColor(palette: IntArray, r: Int, g: Int, b: Int): Int {
        var best = 0
        var bestDistance = Int.MAX_VALUE
        for (i in palette.indices) {
            val color = palette[i]
            val dr = r - ((color shr 16) and 0xFF)
            val dg = g - ((color shr 8) and 0xFF)
            val db = b - (color and 0xFF)
            val distance = dr * dr * 3 + dg * dg * 4 + db * db * 2
            if (distance < bestDistance) {
                bestDistance = distance
                best = i
            }
        }
        return best
    }
}
