package com.fgxyx.cutgif.data

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.core.content.FileProvider
import com.fgxyx.cutgif.gif.GifEncoder
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.FilterOutputStream
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.coroutines.coroutineContext

class GifExportRepository(private val context: Context) {

    suspend fun export(
        request: ExportRequest,
        onProgress: (frame: Int, total: Int, percent: Int) -> Unit,
    ): ExportResult = withContext(Dispatchers.Default) {
        val exportStartedAt = System.currentTimeMillis()
        val versionName = runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        }.getOrNull() ?: "unknown"
        val outputSize = request.config.outputSize(request.info, request.crop)
        Log.i(
            TAG,
            "EXPORT_START version=$versionName sdk=${Build.VERSION.SDK_INT} " +
                "abis=${Build.SUPPORTED_ABIS.joinToString()} " +
                "range=${request.startMs}-${request.endMs} " +
                "fps=${request.config.frameRate.value} " +
                "mime=${request.info.mimeType} " +
                "output=${outputSize.width}x${outputSize.height} " +
                "ffmpeg=${runCatching { com.arthenica.ffmpegkit.FFmpegKitConfig.getFFmpegVersion() }
                    .getOrElse { error -> "load-error:${error.javaClass.simpleName}:${error.message}" }}",
        )
        var tempGif: File? = null
        try {
            val exporter = FfmpegGifExporter(context)
            val file = exporter.export(request, onProgress)
            tempGif = file
            val frameCount = GifFrameCounter.count(file)
            check(frameCount > 0) { "FFmpeg output contains no frames" }
            val fileName = "CutGIF_${
                SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
            }.gif"
            val destination = createDestination(fileName)
            var copied = false
            try {
                destination.output.use { output ->
                    file.inputStream().use { input -> input.copyTo(output) }
                }
                val size = file.length()
                destination.finalize()
                copied = true
                Log.i(
                    TAG,
                    "EXPORT_DONE engine=ffmpeg frames=$frameCount size=$size " +
                        "range=${request.startMs}-${request.endMs} " +
                        "output=${outputSize.width}x${outputSize.height} " +
                        "elapsedMs=${System.currentTimeMillis() - exportStartedAt}",
                )
                ExportResult(
                    uri = destination.uri,
                    fileName = fileName,
                    displayPath = destination.displayPath,
                    sizeBytes = size,
                    width = outputSize.width,
                    height = outputSize.height,
                    frameCount = frameCount,
                )
            } finally {
                if (!copied) destination.delete()
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Throwable) {
            Log.e(
                TAG,
                "FFMPEG_FALLBACK engine=legacy " +
                    "reason=${error.javaClass.name}:${error.message} " +
                    "elapsedMs=${System.currentTimeMillis() - exportStartedAt}",
                error,
            )
            exportLegacy(request, onProgress)
        } finally {
            tempGif?.delete()
        }
    }

    private suspend fun exportLegacy(
        request: ExportRequest,
        onProgress: (frame: Int, total: Int, percent: Int) -> Unit,
    ): ExportResult = withContext(Dispatchers.Default) {
        val outputSize = request.config.outputSize(request.info, request.crop)
        val frameRate = request.config.frameRate.value
        val frameCount = max(
            1,
            (((request.endMs - request.startMs).coerceAtLeast(1L) / 1000.0) * frameRate)
                .roundToInt(),
        )
        val fileName = "CutGIF_${
            SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        }.gif"
        val destination = createDestination(fileName)
        var encoder: GifEncoder? = null
        var encodedFrameCount: Int? = null
        var completed = false
        try {
            val counting = CountingOutputStream(destination.output)
            val useIndexedFrames = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P &&
                request.info.hasUsableFrameCount()
            Log.i(
                TAG,
                "start durationMs=${request.info.durationMs} sourceFrames=${request.info.frameCount} " +
                    "source=${request.info.width}x${request.info.height} " +
                    "rotation=${request.info.rotationDegrees} " +
                    "range=${request.startMs}-${request.endMs} targetFrames=$frameCount " +
                    "fps=$frameRate output=${outputSize.width}x${outputSize.height} " +
                    "indexed=$useIndexedFrames",
            )
            val workerCount = if (useIndexedFrames) {
                1
            } else {
                min(4, Runtime.getRuntime().availableProcessors().coerceAtLeast(1))
            }
            val retrievers = mutableListOf<MediaMetadataRetriever>()
            try {
                repeat(workerCount) {
                    runCatching {
                        MediaMetadataRetriever().apply {
                            setDataSource(context, request.info.uri)
                        }
                    }.onSuccess(retrievers::add)
                }
                if (retrievers.isEmpty()) {
                    error("Unable to open video decoder")
                }
                val paletteRetriever = retrievers.first()

                // Build one palette from representative frames. Reusing it for
                // every frame is much faster and produces a smaller, visually
                // stable animation than a fresh local table per frame.
                val sampledPixels = mutableListOf<IntArray>()
                for (fraction in listOf(0.2, 0.5, 0.8)) {
                    coroutineContext.ensureActive()
                    val sampleTime = request.startMs +
                        ((request.endMs - request.startMs) * fraction).toLong()
                    val sample = retrieveFrame(
                        retriever = paletteRetriever,
                        request = request,
                        outputSize = outputSize,
                        timeMs = sampleTime,
                        option = MediaMetadataRetriever.OPTION_CLOSEST_SYNC,
                    ) ?: continue
                    try {
                        sampledPixels += sample.toPixels()
                    } finally {
                        sample.recycle()
                    }
                }
                if (sampledPixels.isEmpty()) {
                    error("Unable to decode video frames")
                }
                val palette = com.fgxyx.cutgif.gif.ColorQuantizer.buildPaletteFromFrames(
                    frames = sampledPixels,
                    maxColors = request.config.colorQuality.paletteSize,
                    samplesPerFrame = 8_000,
                )
                sampledPixels.clear()

                val gifEncoder = GifEncoder(
                    output = counting,
                    width = outputSize.width,
                    height = outputSize.height,
                    loopForever = true,
                    globalPalette = palette,
                )
                encoder = gifEncoder
                val delay = (100.0 / frameRate).roundToInt().coerceAtLeast(1)
                val stepMs = 1000.0 / frameRate
                val framesBefore = gifEncoder.framesWritten

                if (useIndexedFrames) {
                    encodeIndexedFrames(
                        retriever = paletteRetriever,
                        request = request,
                        outputSize = outputSize,
                        frameCount = frameCount,
                        stepMs = stepMs,
                        delay = delay,
                        encoder = gifEncoder,
                        onProgress = onProgress,
                    )
                } else {
                    encodeTimedFrames(
                        retrievers = retrievers,
                        request = request,
                        outputSize = outputSize,
                        frameCount = frameCount,
                        stepMs = stepMs,
                        delay = delay,
                        encoder = gifEncoder,
                        onProgress = onProgress,
                    )
                }
                val actualFrames = gifEncoder.framesWritten - framesBefore
                if (actualFrames <= 0) {
                    error("No GIF frames were encoded")
                }
                if (actualFrames != frameCount) {
                    Log.e(
                        TAG,
                        "frame count mismatch: requested=$frameCount encoded=$actualFrames",
                    )
                }
                encodedFrameCount = actualFrames
            } finally {
                retrievers.forEach { retriever -> runCatching { retriever.release() } }
            }

            encoder.finish()
            counting.flush()
            val size = counting.count
            encoder.close()
            Log.i(
                TAG,
                "done encoded=${encodedFrameCount ?: frameCount} requested=$frameCount " +
                    "size=$size output=${outputSize.width}x${outputSize.height}",
            )
            destination.finalize()
            completed = true
            ExportResult(
                uri = destination.uri,
                fileName = fileName,
                displayPath = destination.displayPath,
                sizeBytes = size,
                width = outputSize.width,
                height = outputSize.height,
                frameCount = encodedFrameCount ?: frameCount,
                usedFallback = true,
            )
        } finally {
            if (!completed) {
                runCatching { encoder?.close() }
                destination.delete()
            }
        }
    }

    /**
     * Fast path for API 28+: decode a run of consecutive source frames in one
     * call, then choose the output samples from that run. Frame batching avoids
     * one seek/decode setup per exported frame and is the difference between a
     * multi-minute and a few-second export for long clips.
     */
    @RequiresApi(Build.VERSION_CODES.P)
    private suspend fun encodeIndexedFrames(
        retriever: MediaMetadataRetriever,
        request: ExportRequest,
        outputSize: ExportSize,
        frameCount: Int,
        stepMs: Double,
        delay: Int,
        encoder: GifEncoder,
        onProgress: (frame: Int, total: Int, percent: Int) -> Unit,
    ) {
        val info = request.info
        val sourceFrameCount = info.frameCount.coerceAtLeast(1)
        val durationMs = info.durationMs.coerceAtLeast(1L)
        val sourcePixelCount = max(
            info.rawWidth.toLong() * info.rawHeight,
            info.width.toLong() * info.height,
        ).coerceAtLeast(1L)
        val sourceBytesPerFrame = sourcePixelCount * 4L
        val maxSpanFrames = (48L * 1024L * 1024L / sourceBytesPerFrame)
            .toInt()
            .coerceIn(1, 30)
        val maxOutputsPerBatch = 30
        var previousPixels: IntArray? = null
        var lastPercent = -1
        var outputStart = 0
        var batches = 0
        var fallbackBatches = 0
        var getFramesFailures = 0
        var sourceFramesReturned = 0

        while (outputStart < frameCount) {
            coroutineContext.ensureActive()
            var sourceStart = sourceIndexForTime(
                request.startMs + outputStart * stepMs,
                durationMs,
                sourceFrameCount,
            )
            var sourceEnd = sourceStart
            var outputEnd = outputStart + 1
            while (outputEnd < frameCount) {
                val nextSource = sourceIndexForTime(
                    request.startMs + outputEnd * stepMs,
                    durationMs,
                    sourceFrameCount,
                )
                val nextStart = min(sourceStart, nextSource)
                val nextEnd = max(sourceEnd, nextSource)
                if (nextEnd - nextStart + 1 > maxSpanFrames ||
                    outputEnd - outputStart >= maxOutputsPerBatch
                ) {
                    break
                }
                sourceStart = nextStart
                sourceEnd = nextEnd
                outputEnd++
            }

            val span = sourceEnd - sourceStart + 1
            val sourceFramesResult = runCatching {
                retriever.getFramesAtIndex(sourceStart, span)
            }
            sourceFramesResult.exceptionOrNull()?.let { error ->
                getFramesFailures++
                if (getFramesFailures <= 3) {
                    Log.w(
                        TAG,
                        "getFramesAtIndex failed start=$sourceStart span=$span " +
                            "($getFramesFailures)",
                        error,
                    )
                }
            }
            val sourceFrames = sourceFramesResult.getOrNull()

            if (sourceFrames.isNullOrEmpty()) {
                fallbackBatches++
                // Some devices/videos do not support frame-index batching.
                // Degrade to precise time seeks for this batch.
                for (index in outputStart until outputEnd) {
                    coroutineContext.ensureActive()
                    val scaled = retrieveFrame(
                        retriever = retriever,
                        request = request,
                        outputSize = outputSize,
                        timeMs = (request.startMs + index * stepMs).toLong(),
                        option = MediaMetadataRetriever.OPTION_CLOSEST,
                    ) ?: error("Unable to decode frame $index")
                    try {
                        val pixels = scaled.toPixels()
                        encoder.addFrame(
                            argb = pixels,
                            delayCentiseconds = delay,
                            maxColors = request.config.colorQuality.paletteSize,
                            ditherStrength = request.config.clarity.ditherStrength,
                            previousFrame = previousPixels,
                        )
                        previousPixels = pixels
                    } finally {
                        scaled.recycle()
                    }
                    val percent = (((index + 1) * 100.0) / frameCount)
                        .roundToInt()
                        .coerceIn(0, 100)
                    if (percent != lastPercent || index == frameCount - 1) {
                        lastPercent = percent
                        onProgress(index + 1, frameCount, percent)
                    }
                }
                outputStart = outputEnd
                continue
            }

            batches++
            sourceFramesReturned += sourceFrames.size
            val orientedFrames = ArrayList<Bitmap>(sourceFrames.size)
            for (raw in sourceFrames) {
                orientedFrames += VideoFrames.orientBitmap(
                    frame = raw,
                    rawWidth = info.rawWidth,
                    rawHeight = info.rawHeight,
                    rotationDegrees = info.rotationDegrees,
                )
            }

            try {
                for (index in outputStart until outputEnd) {
                    coroutineContext.ensureActive()
                    val sourceIndex = sourceIndexForTime(
                        request.startMs + index * stepMs,
                        durationMs,
                        sourceFrameCount,
                    )
                    val sourceFrame = orientedFrames.getOrNull(sourceIndex - sourceStart)
                    if (sourceFrame == null) {
                        val scaled = retrieveFrame(
                            retriever = retriever,
                            request = request,
                            outputSize = outputSize,
                            timeMs = (request.startMs + index * stepMs).toLong(),
                            option = MediaMetadataRetriever.OPTION_CLOSEST,
                        ) ?: error("Unable to decode frame $index")
                        try {
                            val pixels = scaled.toPixels()
                            encoder.addFrame(
                                argb = pixels,
                                delayCentiseconds = delay,
                                maxColors = request.config.colorQuality.paletteSize,
                                ditherStrength = request.config.clarity.ditherStrength,
                                previousFrame = previousPixels,
                            )
                            previousPixels = pixels
                        } finally {
                            scaled.recycle()
                        }
                    } else {
                        val scaled = cropAndScale(
                            frame = sourceFrame,
                            crop = request.crop,
                            targetWidth = outputSize.width,
                            targetHeight = outputSize.height,
                            filter = request.config.clarity != Clarity.LOW,
                            recycleSource = false,
                        )
                        try {
                            val pixels = scaled.toPixels()
                            encoder.addFrame(
                                argb = pixels,
                                delayCentiseconds = delay,
                                maxColors = request.config.colorQuality.paletteSize,
                                ditherStrength = request.config.clarity.ditherStrength,
                                previousFrame = previousPixels,
                            )
                            previousPixels = pixels
                        } finally {
                            if (scaled !== sourceFrame) {
                                scaled.recycle()
                            }
                        }
                    }
                    val percent = (((index + 1) * 100.0) / frameCount)
                        .roundToInt()
                        .coerceIn(0, 100)
                    if (percent != lastPercent || index == frameCount - 1) {
                        lastPercent = percent
                        onProgress(index + 1, frameCount, percent)
                    }
                }
            } finally {
                val recycled = java.util.IdentityHashMap<Bitmap, Boolean>()
                orientedFrames.forEach { frame ->
                    if (recycled.put(frame, true) == null) {
                        frame.recycle()
                    }
                }
            }
            outputStart = outputEnd
        }
        Log.i(
            TAG,
            "indexed done outputFrames=${encoder.framesWritten} batches=$batches " +
                "fallbackBatches=$fallbackBatches failures=$getFramesFailures " +
                "sourceFramesReturned=$sourceFramesReturned " +
                "sourceFrames=$sourceFrameCount maxSpan=$maxSpanFrames",
        )
    }

    private suspend fun encodeTimedFrames(
        retrievers: List<MediaMetadataRetriever>,
        request: ExportRequest,
        outputSize: ExportSize,
        frameCount: Int,
        stepMs: Double,
        delay: Int,
        encoder: GifEncoder,
        onProgress: (frame: Int, total: Int, percent: Int) -> Unit,
    ) {
        val batchBudgetBytes = 32L * 1024L * 1024L
        val bytesPerFrame = outputSize.width.toLong() * outputSize.height * 4L
        val maxBatchFrames = (batchBudgetBytes / bytesPerFrame)
            .toInt()
            .coerceAtLeast(1)
        val activeReaders = min(retrievers.size, maxBatchFrames)
        val batchSize = min(retrievers.size * 4, maxBatchFrames)
            .coerceAtLeast(activeReaders)
        var lastPercent = -1
        var batchStart = 0
        var previousPixels: IntArray? = null

        while (batchStart < frameCount) {
            val batchEnd = min(batchStart + batchSize, frameCount)
            val indices = batchStart until batchEnd
            val chunkSize = ceil((batchEnd - batchStart) / activeReaders.toDouble())
                .toInt()
                .coerceAtLeast(1)
            val chunks = indices.chunked(chunkSize)

            val decoded = coroutineScope {
                chunks.mapIndexed { workerIndex, chunk ->
                    async(Dispatchers.IO) {
                        val retriever = retrievers[workerIndex % activeReaders]
                        val result = ArrayList<Pair<Int, Bitmap>>(chunk.size)
                        try {
                            for (index in chunk) {
                                coroutineContext.ensureActive()
                                val timeMs = (request.startMs + index * stepMs).toLong()
                                    .coerceAtMost(
                                        (request.endMs - 1).coerceAtLeast(request.startMs),
                                    )
                                val frame = retrieveFrame(
                                    retriever = retriever,
                                    request = request,
                                    outputSize = outputSize,
                                    timeMs = timeMs,
                                    option = MediaMetadataRetriever.OPTION_CLOSEST,
                                ) ?: error("Unable to decode frame $index")
                                result += index to frame
                            }
                        } catch (error: Throwable) {
                            result.forEach { (_, bitmap) -> bitmap.recycle() }
                            throw error
                        }
                        result
                    }
                }.awaitAll()
            }

            for ((index, bitmap) in decoded.flatten().sortedBy { it.first }) {
                coroutineContext.ensureActive()
                try {
                    val pixels = bitmap.toPixels()
                    encoder.addFrame(
                        argb = pixels,
                        delayCentiseconds = delay,
                        maxColors = request.config.colorQuality.paletteSize,
                        ditherStrength = request.config.clarity.ditherStrength,
                        previousFrame = previousPixels,
                    )
                    previousPixels = pixels
                } finally {
                    bitmap.recycle()
                }
                val percent = (((index + 1) * 100.0) / frameCount)
                    .roundToInt()
                    .coerceIn(0, 100)
                if (percent != lastPercent || index == frameCount - 1) {
                    lastPercent = percent
                    onProgress(index + 1, frameCount, percent)
                }
            }
            batchStart = batchEnd
        }
        Log.i(
            TAG,
            "timed done outputFrames=${encoder.framesWritten} " +
                "workers=$activeReaders batch=$batchSize",
        )
    }

    private fun sourceIndexForTime(
        timeMs: Double,
        durationMs: Long,
        sourceFrameCount: Int,
    ): Int {
        return ((timeMs / durationMs) * sourceFrameCount)
            .roundToInt()
            .coerceIn(0, sourceFrameCount - 1)
    }

    /**
     * Some 30 fps MP4s report a bogus METADATA_KEY_VIDEO_FRAME_COUNT (for
     * example 3). Indexed batching then samples only those three frames.
     * Reject implausible metadata and use precise time seeks instead.
     */
    private fun VideoInfo.hasUsableFrameCount(): Boolean {
        if (frameCount <= 1 || durationMs <= 0L) return false
        val averageFps = frameCount / (durationMs / 1000.0)
        return averageFps in 1.0..240.0
    }

    private companion object {
        const val TAG = "CutGifExport"
    }

    private fun retrieveFrame(
        retriever: MediaMetadataRetriever,
        request: ExportRequest,
        outputSize: ExportSize,
        timeMs: Long,
        option: Int,
    ): Bitmap? {
        val frame = VideoFrames.orientedFrame(
            retriever = retriever,
            timeUs = timeMs * 1_000L,
            option = option,
            rawWidth = request.info.rawWidth,
            rawHeight = request.info.rawHeight,
            rotationDegrees = request.info.rotationDegrees,
        ) ?: return null
        return cropAndScale(
            frame = frame,
            crop = request.crop,
            targetWidth = outputSize.width,
            targetHeight = outputSize.height,
            filter = request.config.clarity != Clarity.LOW,
        )
    }

    private fun Bitmap.toPixels(): IntArray {
        val pixels = IntArray(width * height)
        getPixels(pixels, 0, width, 0, 0, width, height)
        return pixels
    }

    private fun cropAndScale(
        frame: Bitmap,
        crop: CropRectN,
        targetWidth: Int,
        targetHeight: Int,
        filter: Boolean,
        recycleSource: Boolean = true,
    ): Bitmap {
        val safeCrop = crop.clamped()
        val left = (safeCrop.left * frame.width).roundToInt().coerceIn(0, frame.width - 1)
        val top = (safeCrop.top * frame.height).roundToInt().coerceIn(0, frame.height - 1)
        val width = (safeCrop.width * frame.width).roundToInt()
            .coerceIn(1, frame.width - left)
        val height = (safeCrop.height * frame.height).roundToInt()
            .coerceIn(1, frame.height - top)
        val cropped = if (left == 0 && top == 0 && width == frame.width && height == frame.height) {
            frame
        } else {
            Bitmap.createBitmap(frame, left, top, width, height)
        }
        if (recycleSource && cropped !== frame) frame.recycle()
        if (cropped.width == targetWidth && cropped.height == targetHeight) {
            return cropped
        }
        val scaled = Bitmap.createScaledBitmap(cropped, targetWidth, targetHeight, filter)
        if (scaled !== cropped) cropped.recycle()
        return scaled
    }

    private fun createDestination(fileName: String): Destination {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val relativePath = "${Environment.DIRECTORY_PICTURES}/CutGIF"
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
                put(MediaStore.Images.Media.MIME_TYPE, "image/gif")
                put(MediaStore.Images.Media.RELATIVE_PATH, relativePath)
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
            val resolver = context.contentResolver
            val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                ?: error("Unable to create MediaStore entry")
            val output = resolver.openOutputStream(uri)
                ?: run {
                    resolver.delete(uri, null, null)
                    error("Unable to open MediaStore output")
                }
            return Destination(
                output = output,
                uri = uri,
                displayPath = "$relativePath/$fileName",
                onFinalize = {
                    val ready = ContentValues().apply {
                        put(MediaStore.Images.Media.IS_PENDING, 0)
                    }
                    resolver.update(uri, ready, null, null)
                },
                onDelete = {
                    runCatching { resolver.delete(uri, null, null) }
                },
            )
        }

        val pictures = context.getExternalFilesDir(Environment.DIRECTORY_PICTURES)
            ?: File(context.filesDir, "Pictures")
        val directory = File(pictures, "CutGIF").apply { mkdirs() }
        val file = File(directory, fileName)
        val output = FileOutputStream(file)
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file,
        )
        return Destination(
            output = output,
            uri = uri,
            displayPath = file.absolutePath,
            onFinalize = {},
            onDelete = { runCatching { file.delete() } },
        )
    }

    private data class Destination(
        val output: OutputStream,
        val uri: Uri,
        val displayPath: String,
        val onFinalize: () -> Unit,
        val onDelete: () -> Unit,
    ) {
        fun finalize() = onFinalize()
        fun delete() {
            runCatching { output.close() }
            onDelete()
        }
    }

    private class CountingOutputStream(output: OutputStream) : FilterOutputStream(output) {
        var count: Long = 0L
            private set

        override fun write(b: Int) {
            out.write(b)
            count++
        }

        override fun write(b: ByteArray, off: Int, len: Int) {
            out.write(b, off, len)
            count += len
        }

        override fun close() {
            out.close()
        }
    }
}

object GifShare {
    fun share(context: Context, uri: Uri) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "image/gif"
            putExtra(Intent.EXTRA_STREAM, uri)
            clipData = android.content.ClipData.newRawUri("CutGIF", uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, null))
    }
}

fun formatFileSize(bytes: Long): String {
    if (bytes < 1_000L) return "$bytes B"
    val kb = bytes / 1_000.0
    if (kb < 1_000.0) return String.format(Locale.US, "%.1f KB", kb)
    val mb = bytes / 1_000_000.0
    if (mb < 1_000.0) return String.format(Locale.US, "%.1f MB", mb)
    return String.format(Locale.US, "%.2f GB", bytes / 1_000_000_000.0)
}
