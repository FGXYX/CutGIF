package com.fgxyx.cutgif.data

import java.io.BufferedInputStream
import java.io.DataInputStream
import java.io.File
import java.io.InputStream

data class GifMetadata(
    val width: Int,
    val height: Int,
    val frameDelaysMs: IntArray,
) {
    val frameCount: Int get() = frameDelaysMs.size
    val durationMs: Long get() = frameDelaysMs.sumOf { it.toLong() }
}

object GifMetadataReader {

    fun read(file: File): GifMetadata = file.inputStream().use(::read)

    fun read(input: InputStream): GifMetadata {
        val data = DataInputStream(BufferedInputStream(input))
        val header = ByteArray(6)
        data.readFully(header)
        val signature = String(header, Charsets.US_ASCII)
        require(signature == "GIF89a" || signature == "GIF87a") { "Not a GIF file" }

        val screen = ByteArray(7)
        data.readFully(screen)
        val width = littleEndian(screen, 0)
        val height = littleEndian(screen, 2)
        val packed = screen[4].toInt() and 0xFF
        if (packed and 0x80 != 0) {
            skipFully(data, 3L * (1 shl ((packed and 0x07) + 1)))
        }

        val delays = ArrayList<Int>()
        var pendingDelayMs = 100
        while (true) {
            when (val marker = data.read()) {
                -1, 0x3B -> break
                0x21 -> {
                    val label = data.read()
                    if (label < 0) break
                    if (label == 0xF9) {
                        val length = data.read()
                        if (length <= 0) continue
                        val block = ByteArray(length)
                        data.readFully(block)
                        if (length >= 4) {
                            val centiseconds = (block[1].toInt() and 0xFF) or
                                ((block[2].toInt() and 0xFF) shl 8)
                            pendingDelayMs = if (centiseconds <= 0) {
                                100
                            } else {
                                (centiseconds * 10).coerceIn(10, 60_000)
                            }
                        }
                        skipSubBlocks(data)
                    } else {
                        skipSubBlocks(data)
                    }
                }
                0x2C -> {
                    delays += pendingDelayMs
                    pendingDelayMs = 100
                    skipFully(data, 8L)
                    val imagePacked = data.read()
                    if (imagePacked < 0) break
                    if (imagePacked and 0x80 != 0) {
                        skipFully(data, 3L * (1 shl ((imagePacked and 0x07) + 1)))
                    }
                    data.read() // LZW minimum code size
                    skipSubBlocks(data)
                }
                else -> break
            }
        }
        require(delays.isNotEmpty()) { "GIF contains no frames" }
        return GifMetadata(
            width = width.coerceAtLeast(1),
            height = height.coerceAtLeast(1),
            frameDelaysMs = delays.toIntArray(),
        )
    }

    private fun littleEndian(bytes: ByteArray, offset: Int): Int =
        (bytes[offset].toInt() and 0xFF) or ((bytes[offset + 1].toInt() and 0xFF) shl 8)

    private fun skipSubBlocks(data: DataInputStream) {
        while (true) {
            val length = data.read()
            if (length <= 0) return
            skipFully(data, length.toLong())
        }
    }

    private fun skipFully(data: DataInputStream, count: Long) {
        var remaining = count
        while (remaining > 0) {
            val skipped = data.skip(remaining)
            if (skipped <= 0) {
                if (data.read() < 0) return
                remaining--
            } else {
                remaining -= skipped
            }
        }
    }
}
