package com.fgxyx.cutgif.data

import java.io.BufferedInputStream
import java.io.DataInputStream
import java.io.File
import java.io.InputStream

/**
 * Minimal GIF89a structure walker used to verify that the exported file really
 * contains the expected number of image frames. Decoders do not expose an image
 * count on Android, and trusting the encoder's counter would hide truncated
 * files.
 */
object GifFrameCounter {
    fun count(file: File): Int {
        file.inputStream().use { return count(it) }
    }

    fun count(input: InputStream): Int {
        val data = DataInputStream(BufferedInputStream(input))
        val header = ByteArray(6)
        data.readFully(header)
        require(String(header, Charsets.US_ASCII) == "GIF89a" ||
            String(header, Charsets.US_ASCII) == "GIF87a") {
            "Not a GIF file"
        }

        val screen = ByteArray(7)
        data.readFully(screen)
        val packed = screen[4].toInt() and 0xFF
        if (packed and 0x80 != 0) {
            skipFully(data, 3L * (1 shl ((packed and 0x07) + 1)))
        }

        var frames = 0
        while (true) {
            when (val marker = data.read()) {
                -1, 0x3B -> return frames
                0x21 -> {
                    data.read()
                    skipSubBlocks(data)
                }
                0x2C -> {
                    frames++
                    skipFully(data, 8L)
                    val imagePacked = data.read()
                    if (imagePacked < 0) return frames
                    if (imagePacked and 0x80 != 0) {
                        skipFully(data, 3L * (1 shl ((imagePacked and 0x07) + 1)))
                    }
                    data.read() // LZW minimum code size
                    skipSubBlocks(data)
                }
                else -> return frames
            }
        }
    }

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
