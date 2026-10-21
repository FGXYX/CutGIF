package com.fgxyx.cutgif.data

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Movie
import java.io.File
import kotlin.math.max
import kotlin.math.min

/**
 * Frame-accurate GIF rendering backed by Android's Movie decoder. Movie is
 * deprecated but remains the only platform API that can seek to an arbitrary
 * GIF frame without decoding every frame into a Drawable.
 */
@Suppress("DEPRECATION")
class GifFrameRenderer(
    file: File,
    private val metadata: GifMetadata,
) {
    private val movie: Movie = Movie.decodeFile(file.absolutePath)
        ?: error("Unable to decode GIF")
    private val startTimesMs = IntArray(metadata.frameCount).also { starts ->
        var time = 0
        for (index in starts.indices) {
            starts[index] = time
            time += metadata.frameDelaysMs[index]
        }
    }

    fun renderFrame(
        index: Int,
        targetWidth: Int,
        targetHeight: Int,
        centerCrop: Boolean,
        opaqueBackground: Boolean,
    ): Bitmap {
        val safeIndex = index.coerceIn(0, metadata.frameCount - 1)
        val sourceWidth = max(1, movie.width())
        val sourceHeight = max(1, movie.height())
        val safeTargetWidth = max(1, targetWidth)
        val safeTargetHeight = max(1, targetHeight)
        val bitmap = Bitmap.createBitmap(
            safeTargetWidth,
            safeTargetHeight,
            Bitmap.Config.ARGB_8888,
        )
        val canvas = Canvas(bitmap)
        if (opaqueBackground) {
            canvas.drawColor(Color.WHITE)
        }
        val scale = if (centerCrop) {
            max(
                safeTargetWidth.toFloat() / sourceWidth,
                safeTargetHeight.toFloat() / sourceHeight,
            )
        } else {
            min(
                safeTargetWidth.toFloat() / sourceWidth,
                safeTargetHeight.toFloat() / sourceHeight,
            )
        }
        val drawWidth = sourceWidth * scale
        val drawHeight = sourceHeight * scale
        val left = (safeTargetWidth - drawWidth) / 2f
        val top = (safeTargetHeight - drawHeight) / 2f
        val timeMs = (startTimesMs[safeIndex] + metadata.frameDelaysMs[safeIndex] / 2)
            .coerceAtMost(max(0, movie.duration() - 1))
        movie.setTime(timeMs)
        canvas.save()
        canvas.translate(left, top)
        canvas.scale(scale, scale)
        movie.draw(canvas, 0f, 0f)
        canvas.restore()
        return bitmap
    }
}
