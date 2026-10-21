package com.fgxyx.cutgif.data

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import com.fgxyx.cutgif.gif.ColorQuantizer
import com.fgxyx.cutgif.gif.GifEncoder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.coroutines.coroutineContext

enum class GifSplitScale(val percent: Int) {
    FULL(100),
    P75(75),
    P50(50),
    P25(25),
}

data class GifSplitResult(
    val uri: Uri,
    val folderPath: String,
    val width: Int,
    val height: Int,
    val frameCount: Int,
    val fileSizeBytes: Long,
    val quality: ColorQuality,
)

object GifSplitExporter {

    /**
     * Re-encodes the selected frame range as a looping GIF. The original
     * per-frame delays are preserved; transparent source pixels are composited
     * onto white so the output remains a standard opaque GIF.
     */
    suspend fun exportGif(
        context: Context,
        sourceFile: File,
        metadata: GifMetadata,
        startFrame: Int,
        endFrame: Int,
        scale: GifSplitScale,
        quality: ColorQuality,
        onProgress: (done: Int, total: Int, percent: Int) -> Unit,
    ): GifSplitResult = withContext(Dispatchers.Default) {
        val start = startFrame.coerceIn(0, metadata.frameCount - 1)
        val end = endFrame.coerceIn(start, metadata.frameCount - 1)
        val total = end - start + 1
        val width = max(1, (metadata.width * scale.percent / 100f).roundToInt())
        val height = max(1, (metadata.height * scale.percent / 100f).roundToInt())
        val renderer = GifFrameRenderer(sourceFile, metadata)
        val tempFile = File(context.cacheDir, "split_export_${System.currentTimeMillis()}.gif")
        try {
            val sampleFrames = listOf(start, start + (end - start) / 2, end)
                .distinct()
                .map { index ->
                    val bitmap = renderer.renderFrame(
                        index = index,
                        targetWidth = width,
                        targetHeight = height,
                        centerCrop = false,
                        opaqueBackground = true,
                    )
                    try {
                        bitmap.toPixels()
                    } finally {
                        bitmap.recycle()
                    }
                }
            val palette = ColorQuantizer.buildPaletteFromFrames(
                frames = sampleFrames,
                maxColors = quality.paletteSize,
                samplesPerFrame = 8_000,
            )
            FileOutputStream(tempFile).use { output ->
                GifEncoder(
                    output = output,
                    width = width,
                    height = height,
                    loopForever = true,
                    globalPalette = palette,
                ).use { encoder ->
                    var previousPixels: IntArray? = null
                    for (offset in 0 until total) {
                        coroutineContext.ensureActive()
                        val frameIndex = start + offset
                        val bitmap = renderer.renderFrame(
                            index = frameIndex,
                            targetWidth = width,
                            targetHeight = height,
                            centerCrop = false,
                            opaqueBackground = true,
                        )
                        try {
                            val pixels = bitmap.toPixels()
                            val delayCentiseconds = (metadata.frameDelaysMs[frameIndex] / 10f)
                                .roundToInt()
                                .coerceIn(1, 65_535)
                            encoder.addFrame(
                                argb = pixels,
                                delayCentiseconds = delayCentiseconds,
                                maxColors = quality.paletteSize,
                                ditherStrength = ditherStrength(quality),
                                previousFrame = previousPixels,
                            )
                            previousPixels = pixels
                        } finally {
                            bitmap.recycle()
                        }
                        val done = offset + 1
                        val percent = (5 + 95.0 * done / total).roundToInt().coerceIn(5, 100)
                        onProgress(done, total, percent)
                    }
                }
            }
            val saved = saveGif(context, tempFile)
            GifSplitResult(
                uri = saved.uri,
                folderPath = saved.folderPath,
                width = width,
                height = height,
                frameCount = total,
                fileSizeBytes = tempFile.length(),
                quality = quality,
            )
        } finally {
            tempFile.delete()
        }
    }

    private fun ditherStrength(quality: ColorQuality): Int = when (quality) {
        ColorQuality.LOW -> 0
        ColorQuality.MEDIUM -> 4
        ColorQuality.HIGH -> 8
        ColorQuality.HIGHEST -> 12
    }

    private fun saveGif(context: Context, source: File): SavedGif {
        val fileName = "CutGIF_${
            SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        }.gif"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val relativePath = "Pictures/CutGIF"
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
                put(MediaStore.Images.Media.MIME_TYPE, "image/gif")
                put(MediaStore.Images.Media.RELATIVE_PATH, relativePath)
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
            val resolver = context.contentResolver
            val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                ?: error("Unable to create MediaStore entry")
            try {
                resolver.openOutputStream(uri)?.use { output ->
                    source.inputStream().use { input -> input.copyTo(output) }
                } ?: error("Unable to open MediaStore output")
                val ready = ContentValues().apply {
                    put(MediaStore.Images.Media.IS_PENDING, 0)
                }
                resolver.update(uri, ready, null, null)
                return SavedGif(uri, "$relativePath/$fileName")
            } catch (error: Throwable) {
                runCatching { resolver.delete(uri, null, null) }
                throw error
            }
        }
        val pictures = context.getExternalFilesDir(Environment.DIRECTORY_PICTURES)
            ?: File(context.filesDir, "Pictures")
        val directory = File(pictures, "CutGIF").apply { mkdirs() }
        val file = File(directory, fileName)
        source.inputStream().use { input ->
            FileOutputStream(file).use { output -> input.copyTo(output) }
        }
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file,
        )
        return SavedGif(uri, file.absolutePath)
    }

    private fun Bitmap.toPixels(): IntArray {
        val pixels = IntArray(width * height)
        getPixels(pixels, 0, width, 0, 0, width, height)
        return pixels
    }

    private data class SavedGif(val uri: Uri, val folderPath: String)
}
