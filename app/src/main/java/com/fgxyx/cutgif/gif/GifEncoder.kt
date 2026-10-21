package com.fgxyx.cutgif.gif

import java.io.Closeable
import java.io.OutputStream
import kotlin.math.ceil
import kotlin.math.log2
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * A small, dependency-free GIF89a encoder.
 *
 * Export uses one adaptive global colour table shared by every frame. That
 * keeps colours stable, avoids duplicating a table 60 times per second, and
 * lets the nearest-colour cache warm up across the whole animation.
 */
class GifEncoder(
    private val output: OutputStream,
    val width: Int,
    val height: Int,
    loopForever: Boolean = true,
    globalPalette: IntArray? = null,
) : Closeable {
    private val sharedPalette: IntArray? = globalPalette
        ?.takeIf { it.isNotEmpty() }
        ?.copyOf()
        ?.let { palette -> palette.copyOf(palette.size.coerceAtMost(255)) }
    private val sharedDepth: Int =
        if (sharedPalette != null) tableDepth(sharedPalette.size, reserveTransparent = true) else 0
    private val sharedTransparentIndex: Int =
        if (sharedPalette != null) (1 shl sharedDepth) - 1 else 0
    private val sharedMapper: ColorQuantizer.PaletteMapper? =
        sharedPalette?.let(ColorQuantizer::mapper)
    private var finished = false
    var framesWritten: Int = 0
        private set

    init {
        require(width in 1..65535 && height in 1..65535) { "Unsupported GIF dimensions" }
        writeHeader(loopForever)
    }

    /**
     * @param argb row-major pixels. Alpha is ignored.
     * @param delayCentiseconds GIF frame delay in 1/100 second units.
     * @param previousFrame when supplied, unchanged pixels are encoded with
     * the GIF transparent index so the previous frame shows through. This
     * drastically shrinks static backgrounds and keeps large exports healthy.
     */
    fun addFrame(
        argb: IntArray,
        delayCentiseconds: Int,
        maxColors: Int,
        ditherStrength: Int,
        previousFrame: IntArray? = null,
    ) {
        check(!finished) { "Encoder is already finished" }
        require(argb.size == width * height) { "Unexpected frame size" }
        require(previousFrame == null || previousFrame.size == argb.size) {
            "Unexpected previous frame size"
        }

        val useTransparency = sharedPalette != null && previousFrame != null
        val palette: IntArray
        val indices: ByteArray
        val depth: Int
        val hasLocalTable: Boolean
        if (sharedPalette != null && sharedMapper != null) {
            palette = sharedPalette
            indices = sharedMapper.map(
                pixels = argb,
                imageWidth = width,
                ditherStrength = ditherStrength.coerceIn(0, 64),
            )
            if (useTransparency) {
                val transparent = sharedTransparentIndex.toByte()
                for (index in indices.indices) {
                    if (argb[index] == previousFrame!![index]) {
                        indices[index] = transparent
                    }
                }
            }
            depth = sharedDepth
            hasLocalTable = false
        } else {
            val frame = ColorQuantizer.quantize(
                pixels = argb,
                imageWidth = width,
                maxColors = maxColors.coerceIn(2, 256),
                ditherStrength = ditherStrength.coerceIn(0, 64),
            )
            palette = frame.palette
            indices = frame.indices
            depth = tableDepth(palette.size)
            hasLocalTable = true
        }
        val tableSize = 1 shl depth

        // Graphic control extension.
        writeByte(0x21)
        writeByte(0xF9)
        writeByte(0x04)
        writeByte(0x04 or if (useTransparency) 0x01 else 0x00)
        writeShort(delayCentiseconds.coerceIn(1, 65535))
        writeByte(if (useTransparency) sharedTransparentIndex else 0)
        writeByte(0)

        // Image descriptor.
        writeByte(0x2C)
        writeShort(0)
        writeShort(0)
        writeShort(width)
        writeShort(height)
        writeByte(if (hasLocalTable) 0x80 or (depth - 1) else 0)

        if (hasLocalTable) {
            writeColorTable(palette, tableSize)
        }

        LzwWriter(output, depth).use { writer ->
            writer.write(indices)
        }
        framesWritten++
    }

    fun finish() {
        if (finished) return
        finished = true
        writeByte(0x3B)
        output.flush()
    }

    override fun close() {
        finish()
        output.close()
    }

    private fun writeHeader(loop: Boolean) {
        output.write("GIF89a".toByteArray(Charsets.US_ASCII))
        writeShort(width)
        writeShort(height)
        val hasGlobal = sharedPalette != null
        writeByte(
            if (hasGlobal) {
                0x80 or 0x70 or (sharedDepth - 1)
            } else {
                0x70
            },
        )
        writeByte(0) // background colour index
        writeByte(0) // pixel aspect ratio
        if (hasGlobal && sharedPalette != null) {
            writeColorTable(sharedPalette, 1 shl sharedDepth)
        }
        if (loop) {
            writeByte(0x21)
            writeByte(0xFF)
            writeByte(11)
            output.write("NETSCAPE2.0".toByteArray(Charsets.US_ASCII))
            writeByte(3)
            writeByte(1)
            writeShort(0)
            writeByte(0)
        }
    }

    private fun writeColorTable(palette: IntArray, tableSize: Int) {
        for (index in 0 until tableSize) {
            val color = palette.getOrElse(index) {
                if (palette.isEmpty()) 0 else palette.last()
            }
            writeByte((color shr 16) and 0xFF)
            writeByte((color shr 8) and 0xFF)
            writeByte(color and 0xFF)
        }
    }

    private fun writeByte(value: Int) = output.write(value and 0xFF)

    private fun writeShort(value: Int) {
        writeByte(value and 0xFF)
        writeByte((value shr 8) and 0xFF)
    }

    private fun tableDepth(paletteSize: Int, reserveTransparent: Boolean = false): Int {
        var depth = max(2, ceil(log2(paletteSize.coerceAtLeast(2).toDouble())).roundToInt())
        if (reserveTransparent && (1 shl depth) <= paletteSize) {
            depth++
        }
        return depth.coerceIn(2, 8)
    }

    private class LzwWriter(
        private val output: OutputStream,
        private val minCodeSize: Int,
    ) : AutoCloseable {
        private val clearCode = 1 shl minCodeSize
        private val endCode = clearCode + 1
        private var codeSize = minCodeSize + 1
        private var nextCode = endCode + 1
        private val dictionary = HashMap<Int, Int>(5003)
        private val block = ByteArray(255)
        private var blockSize = 0
        private var bitBuffer = 0
        private var bitCount = 0

        init {
            writeByte(minCodeSize)
            emit(clearCode, codeSize)
        }

        fun write(indices: ByteArray) {
            if (indices.isEmpty()) return
            var current = indices[0].toInt() and 0xFF
            for (i in 1 until indices.size) {
                val next = indices[i].toInt() and 0xFF
                val key = (current shl 8) or next
                val existing = dictionary[key]
                if (existing != null) {
                    current = existing
                    continue
                }

                emit(current, codeSize)
                if (nextCode < 4096) {
                    dictionary[key] = nextCode
                    nextCode++
                    if (nextCode > (1 shl codeSize) && codeSize < 12) {
                        codeSize++
                    }
                } else {
                    emit(clearCode, codeSize)
                    resetDictionary()
                }
                current = next
            }
            emit(current, codeSize)
            emit(endCode, codeSize)
            flushBits()
        }

        override fun close() {
            if (blockSize > 0) flushBlock()
            writeByte(0)
        }

        private fun resetDictionary() {
            dictionary.clear()
            codeSize = minCodeSize + 1
            nextCode = endCode + 1
        }

        private fun emit(code: Int, size: Int) {
            bitBuffer = bitBuffer or (code shl bitCount)
            bitCount += size
            while (bitCount >= 8) {
                writeCompressedByte(bitBuffer and 0xFF)
                bitBuffer = bitBuffer ushr 8
                bitCount -= 8
            }
        }

        private fun flushBits() {
            if (bitCount > 0) {
                writeCompressedByte(bitBuffer and 0xFF)
                bitBuffer = 0
                bitCount = 0
            }
        }

        private fun writeCompressedByte(value: Int) {
            block[blockSize++] = value.toByte()
            if (blockSize == 255) flushBlock()
        }

        private fun flushBlock() {
            if (blockSize == 0) return
            output.write(blockSize)
            output.write(block, 0, blockSize)
            blockSize = 0
        }

        private fun writeByte(value: Int) {
            output.write(value and 0xFF)
        }
    }
}
