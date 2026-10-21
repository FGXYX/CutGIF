package com.fgxyx.cutgif.gif

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import com.fgxyx.cutgif.data.GifFrameCounter
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO

class GifEncoderTest {

    @Test
    fun writesReadableAnimatedGifWithSharedPalette() {
        val width = 16
        val height = 16
        val palette = intArrayOf(0x000000, 0xFF0000, 0x00FF00, 0x0000FF, 0xFFFFFF)
        val output = ByteArrayOutputStream()

        GifEncoder(
            output = output,
            width = width,
            height = height,
            globalPalette = palette,
        ).use { encoder ->
            encoder.addFrame(frame(width, height, 0x000000), 7, 256, 0)
            encoder.addFrame(frame(width, height, 0xFF0000), 7, 256, 0)
            encoder.addFrame(frame(width, height, 0x00FF00), 7, 256, 0)
        }

        val bytes = output.toByteArray()
        assertEquals("GIF89a", String(bytes.copyOfRange(0, 6), Charsets.US_ASCII))
        assertEquals(0x3B, bytes.last().toInt() and 0xFF)
        assertEquals(3, GifFrameCounter.count(ByteArrayInputStream(bytes)))

        val input = ImageIO.createImageInputStream(ByteArrayInputStream(bytes))
        val reader = ImageIO.getImageReaders(input).next()
        try {
            reader.input = input
            assertEquals(3, reader.getNumImages(true))
            assertEquals(width, reader.getWidth(0))
            assertEquals(height, reader.getHeight(0))
            val image = reader.read(0) as BufferedImage
            assertEquals(width, image.width)
            assertEquals(height, image.height)
        } finally {
            reader.dispose()
            input.close()
        }
    }

    @Test
    fun quantizerProducesPaletteWithinRequestedSize() {
        val pixels = IntArray(64 * 64) { index ->
            val r = (index * 3) and 0xFF
            val g = (index * 5) and 0xFF
            val b = (index * 7) and 0xFF
            (0xFF shl 24) or (r shl 16) or (g shl 8) or b
        }
        val palette = ColorQuantizer.buildPaletteFromFrames(listOf(pixels), 32)
        assertTrue(palette.isNotEmpty())
        assertTrue(palette.size <= 32)

        val indices = ColorQuantizer.mapper(palette).map(pixels, 64, 0)
        assertEquals(pixels.size, indices.size)
        assertTrue(indices.all { (it.toInt() and 0xFF) < palette.size })
    }

    @Test
    fun unchangedPixelsUseTransparencyAndRemainReadable() {
        val width = 8
        val height = 8
        val first = IntArray(width * height) { 0xFFFF0000.toInt() }
        val second = first.copyOf()
        val output = ByteArrayOutputStream()

        GifEncoder(
            output = output,
            width = width,
            height = height,
            globalPalette = intArrayOf(0xFF0000, 0x00FF00, 0xFFFFFF),
        ).use { encoder ->
            encoder.addFrame(first, 7, 255, 0)
            encoder.addFrame(second, 7, 255, 0, previousFrame = first)
        }

        val bytes = output.toByteArray()
        val input = ImageIO.createImageInputStream(ByteArrayInputStream(bytes))
        val reader = ImageIO.getImageReaders(input).next()
        try {
            reader.input = input
            assertEquals(2, reader.getNumImages(true))
            val firstImage = reader.read(0) as BufferedImage
            val secondImage = reader.read(1) as BufferedImage
            val composite = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)
            val canvas = composite.createGraphics()
            canvas.drawImage(firstImage, 0, 0, null)
            canvas.drawImage(secondImage, 0, 0, null)
            canvas.dispose()
            assertEquals(0xFFFF0000.toInt(), composite.getRGB(4, 4))
        } finally {
            reader.dispose()
            input.close()
        }
    }

    private fun frame(width: Int, height: Int, color: Int): IntArray {
        return IntArray(width * height) { color or (0xFF shl 24) }
    }
}
