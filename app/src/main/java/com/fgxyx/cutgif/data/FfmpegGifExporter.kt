package com.fgxyx.cutgif.data

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.util.Log
import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.FFmpegSession
import com.arthenica.ffmpegkit.ReturnCode
import com.fgxyx.cutgif.gif.ColorQuantizer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.Locale
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.resume
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * FFmpeg-based GIF export.
 *
 * The previous MediaMetadataRetriever path had to guess source frame indices
 * from duration and metadata frame count. That breaks on common 30 fps clips,
 * and per-frame seeks are slow. FFmpeg's fps/palettegen/paletteuse filters
 * decode the stream once, resample to the exact target frame rate, and encode
 * the GIF natively.
 */
class FfmpegGifExporter(private val context: Context) {

    suspend fun export(
        request: ExportRequest,
        onProgress: (frame: Int, total: Int, percent: Int) -> Unit,
    ): File {
        val outputSize = request.config.outputSize(request.info, request.crop)
        val input = File(context.cacheDir, "cutgif_ffmpeg_in_${System.currentTimeMillis()}.video")
        val palette = File(context.cacheDir, "cutgif_ffmpeg_palette_${System.currentTimeMillis()}.png")
        val output = File(context.cacheDir, "cutgif_ffmpeg_out_${System.currentTimeMillis()}.gif")
        val paletteSize = request.config.colorQuality.paletteSize.coerceIn(2, 256)
        val rangeMs = (request.endMs - request.startMs).coerceAtLeast(1L)
        val frameRate = request.config.frameRate.value
        val estimatedFrames = max(1, ((rangeMs / 1000.0) * frameRate).roundToInt())
        onProgress(0, estimatedFrames, 2)
        withContext(Dispatchers.IO) {
            context.contentResolver.openInputStream(request.info.uri)?.use { source ->
                input.outputStream().use { target -> source.copyTo(target) }
            } ?: error("Unable to open source video")
        }
        onProgress(0, estimatedFrames, 5)
        Log.i(TAG, "ffmpeg input file bytes=${input.length()}")
        try {
            try {
                val localPalette = runCatching {
                    createPaletteFromAndroid(request, outputSize, palette, paletteSize)
                }.getOrElse { error ->
                    Log.e(
                        TAG,
                        "android palette failed reason=${error.javaClass.name}:${error.message}",
                        error,
                    )
                    false
                }
                if (!localPalette) {
                    Log.i(TAG, "ffmpeg palette fallback=ffmpeg")
                    runPalette(request, outputSize, input, palette, paletteSize)
                }
                onProgress(0, estimatedFrames, 10)
                runFfmpeg(request, outputSize, input, palette, output, onProgress)
            } catch (twoPassError: Throwable) {
                if (twoPassError is kotlinx.coroutines.CancellationException) throw twoPassError
                Log.e(
                    TAG,
                    "ffmpeg two-pass failed; trying one-pass " +
                        "reason=${twoPassError.javaClass.name}:${twoPassError.message}",
                    twoPassError,
                )
                palette.delete()
                output.delete()
                runOnePass(request, outputSize, input, output, paletteSize, onProgress)
            }
            check(output.isFile && output.length() > 0L) {
                "FFmpeg produced an empty GIF"
            }
            return output
        } catch (error: Throwable) {
            output.delete()
            throw error
        } finally {
            input.delete()
            palette.delete()
        }
    }

    private suspend fun createPaletteFromAndroid(
        request: ExportRequest,
        outputSize: ExportSize,
        paletteFile: File,
        maxColors: Int,
    ): Boolean = withContext(Dispatchers.IO) {
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(context, request.info.uri)
            val rangeMs = (request.endMs - request.startMs).coerceAtLeast(1L)
            val frames = mutableListOf<IntArray>()
            for (fraction in listOf(0.2, 0.5, 0.8)) {
                val timeUs = (request.startMs + (rangeMs * fraction).toLong()) * 1_000L
                val raw = retriever.getFrameAtTime(
                    timeUs,
                    MediaMetadataRetriever.OPTION_CLOSEST_SYNC,
                ) ?: continue
                val oriented = VideoFrames.orientBitmap(
                    frame = raw,
                    rawWidth = request.info.rawWidth,
                    rawHeight = request.info.rawHeight,
                    rotationDegrees = request.info.rotationDegrees,
                )
                val crop = request.crop.clamped()
                val left = (crop.left * oriented.width).roundToInt()
                    .coerceIn(0, oriented.width - 1)
                val top = (crop.top * oriented.height).roundToInt()
                    .coerceIn(0, oriented.height - 1)
                val width = (crop.width * oriented.width).roundToInt()
                    .coerceIn(1, oriented.width - left)
                val height = (crop.height * oriented.height).roundToInt()
                    .coerceIn(1, oriented.height - top)
                val cropped = if (left == 0 && top == 0 &&
                    width == oriented.width && height == oriented.height
                ) {
                    oriented
                } else {
                    Bitmap.createBitmap(oriented, left, top, width, height)
                }
                if (cropped !== oriented) oriented.recycle()
                val scaled = if (cropped.width == outputSize.width &&
                    cropped.height == outputSize.height
                ) {
                    cropped
                } else {
                    Bitmap.createScaledBitmap(
                        cropped,
                        outputSize.width,
                        outputSize.height,
                        true,
                    )
                }
                if (scaled !== cropped) cropped.recycle()
                try {
                    val pixels = IntArray(scaled.width * scaled.height)
                    scaled.getPixels(pixels, 0, scaled.width, 0, 0, scaled.width, scaled.height)
                    frames += pixels
                } finally {
                    scaled.recycle()
                }
            }
            if (frames.isEmpty()) return@withContext false
            val colors = ColorQuantizer.buildPaletteFromFrames(
                frames = frames,
                maxColors = maxColors,
                samplesPerFrame = 8_000,
            )
            if (colors.isEmpty()) return@withContext false
            val side = 16 // paletteuse requires exactly 256 palette pixels
            val bitmap = Bitmap.createBitmap(side, side, Bitmap.Config.ARGB_8888)
            val pixels = IntArray(side * side) { colors.last() or 0xFF000000.toInt() }
            colors.forEachIndexed { index, color ->
                pixels[index] = color or 0xFF000000.toInt()
            }
            bitmap.setPixels(pixels, 0, side, 0, 0, side, side)
            try {
                FileOutputStream(paletteFile).use { output ->
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)
                }
            } finally {
                bitmap.recycle()
            }
            Log.i(TAG, "android palette done colors=${colors.size} size=${paletteFile.length()}")
            paletteFile.isFile && paletteFile.length() > 0L
        } finally {
            runCatching { retriever.release() }
        }
    }

    private suspend fun runPalette(
        request: ExportRequest,
        outputSize: ExportSize,
        input: File,
        palette: File,
        paletteSize: Int,
    ) = withContext(Dispatchers.IO) {
        val rangeMs = (request.endMs - request.startMs).coerceAtLeast(1L)
        val videoFilter = buildVideoFilter(request, outputSize, sampleFps = 1)
        val command = buildString {
            append("-y -hide_banner -nostdin -loglevel error ")
            append("-ss ").append(seconds(request.startMs / 1000.0)).append(' ')
            append(codecOption(request))
            append("-i ").append(quote(input.absolutePath)).append(' ')
            append("-t ").append(seconds(rangeMs / 1000.0)).append(' ')
            append("-an -vf ").append(
                quote("$videoFilter,palettegen=max_colors=$paletteSize:stats_mode=diff"),
            ).append(' ')
            append(quote(palette.absolutePath))
        }
        Log.i(TAG, "ffmpeg palette command=$command")
        val session = FFmpegKit.execute(command)
        if (!ReturnCode.isSuccess(session.returnCode)) {
            val logs = runCatching { session.allLogsAsString }.getOrNull().orEmpty()
            Log.e(TAG, "ffmpeg palette failed log=${logs.takeLast(2_000)}")
            error("FFmpeg palette generation failed")
        }
        check(palette.isFile && palette.length() > 0L) {
            "FFmpeg produced an empty palette"
        }
        Log.i(TAG, "ffmpeg palette done size=${palette.length()}")
    }

    private suspend fun runFfmpeg(
        request: ExportRequest,
        outputSize: ExportSize,
        input: File,
        palette: File,
        output: File,
        onProgress: (frame: Int, total: Int, percent: Int) -> Unit,
    ) {
        val frameRate = request.config.frameRate.value
        val rangeMs = (request.endMs - request.startMs).coerceAtLeast(1L)
        val estimatedFrames = max(1, ((rangeMs / 1000.0) * frameRate).roundToInt())
        val videoFilter = buildVideoFilter(request, outputSize, sampleFps = frameRate)
        val filter = "[0:v]$videoFilter[frames];" +
            "[frames][1:v]paletteuse=dither=${dither(request)}:diff_mode=rectangle[out]"
        val command = buildString {
            append("-y -hide_banner -nostdin -loglevel error ")
            append("-ss ").append(seconds(request.startMs / 1000.0)).append(' ')
            append(codecOption(request))
            append("-i ").append(quote(input.absolutePath)).append(' ')
            append("-i ").append(quote(palette.absolutePath)).append(' ')
            append("-t ").append(seconds(rangeMs / 1000.0)).append(' ')
            append("-filter_complex ").append(quote(filter)).append(' ')
            append("-map [out] -loop 0 -f gif ")
            append(quote(output.absolutePath))
        }
        executeGifCommand(
            engine = "two-pass",
            command = command,
            output = output,
            outputSize = outputSize,
            estimatedFrames = estimatedFrames,
            rangeMs = rangeMs,
            onProgress = onProgress,
        )
    }

    /**
     * Compatibility path for devices where palettegen cannot consume a separate
     * palette image. Slower because palettegen scans every frame, but keeps the
     * export inside FFmpeg instead of silently dropping to the old JVM encoder.
     */
    private suspend fun runOnePass(
        request: ExportRequest,
        outputSize: ExportSize,
        input: File,
        output: File,
        paletteSize: Int,
        onProgress: (frame: Int, total: Int, percent: Int) -> Unit,
    ) {
        val frameRate = request.config.frameRate.value
        val rangeMs = (request.endMs - request.startMs).coerceAtLeast(1L)
        val estimatedFrames = max(1, ((rangeMs / 1000.0) * frameRate).roundToInt())
        val videoFilter = buildVideoFilter(request, outputSize, sampleFps = frameRate)
        val filter = "[0:v]$videoFilter,split[a][b];" +
            "[a]palettegen=max_colors=$paletteSize:stats_mode=diff[p];" +
            "[b][p]paletteuse=dither=${dither(request)}:diff_mode=rectangle[out]"
        val command = buildString {
            append("-y -hide_banner -nostdin -loglevel error ")
            append("-ss ").append(seconds(request.startMs / 1000.0)).append(' ')
            append(codecOption(request))
            append("-i ").append(quote(input.absolutePath)).append(' ')
            append("-t ").append(seconds(rangeMs / 1000.0)).append(' ')
            append("-filter_complex ").append(quote(filter)).append(' ')
            append("-map [out] -loop 0 -f gif ")
            append(quote(output.absolutePath))
        }
        executeGifCommand(
            engine = "one-pass",
            command = command,
            output = output,
            outputSize = outputSize,
            estimatedFrames = estimatedFrames,
            rangeMs = rangeMs,
            onProgress = onProgress,
        )
    }

    private suspend fun executeGifCommand(
        engine: String,
        command: String,
        output: File,
        outputSize: ExportSize,
        estimatedFrames: Int,
        rangeMs: Long,
        onProgress: (frame: Int, total: Int, percent: Int) -> Unit,
    ) {
        Log.i(TAG, "ffmpeg engine=$engine command=$command")
        val startPercent = 10
        val reportedPercent = AtomicInteger(startPercent)
        fun report(percent: Int, frame: Int) {
            val value = percent.coerceIn(0, 95)
            val previous = reportedPercent.get()
            if (value > previous && reportedPercent.compareAndSet(previous, value)) {
                onProgress(frame, estimatedFrames, value)
            }
        }
        onProgress(0, estimatedFrames, startPercent)
        coroutineScope {
            val startedAt = System.currentTimeMillis()
            val expectedMs = max(3_000L, rangeMs * 2L)
            val heartbeat = launch {
                while (isActive) {
                    delay(500L)
                    val elapsed = System.currentTimeMillis() - startedAt
                    val percent = startPercent + (
                        (elapsed.toDouble() / expectedMs) * (95 - startPercent)
                        ).toInt()
                    val frame = (percent / 100.0 * estimatedFrames).roundToInt()
                    report(percent, frame)
                }
            }
            try {
                val session = suspendCancellableCoroutine<FFmpegSession> { continuation ->
                    var running: FFmpegSession? = null
                    running = FFmpegKit.executeAsync(
                        command,
                        { completed ->
                            if (continuation.isActive) continuation.resume(completed)
                        },
                        null,
                    ) { statistics ->
                        val sourceFrames = statistics.videoFrameNumber.toInt()
                        val rawPercent = if (sourceFrames > 0) {
                            (sourceFrames * 100.0 / estimatedFrames).roundToInt()
                        } else {
                            (statistics.time * 100.0 / rangeMs).roundToInt()
                        }.coerceIn(0, 100)
                        val percent = startPercent +
                            ((95 - startPercent) * rawPercent / 100)
                        val frame = (percent / 100.0 * estimatedFrames).roundToInt()
                        report(percent, frame)
                    }
                    continuation.invokeOnCancellation {
                        running?.let { FFmpegKit.cancel(it.sessionId) }
                    }
                }
                if (!ReturnCode.isSuccess(session.returnCode)) {
                    val logs = runCatching { session.allLogsAsString }.getOrNull().orEmpty()
                    Log.e(
                        TAG,
                        "ffmpeg engine=$engine failed code=${session.returnCode} " +
                            "log=${logs.takeLast(3_000)}",
                    )
                    error("FFmpeg $engine export failed (${session.returnCode})")
                }
                check(output.isFile && output.length() > 0L) {
                    "FFmpeg $engine produced an empty GIF"
                }
                reportedPercent.set(100)
                onProgress(estimatedFrames, estimatedFrames, 100)
                Log.i(
                    TAG,
                    "ffmpeg engine=$engine done frames=$estimatedFrames " +
                        "size=${output.length()} " +
                        "output=${outputSize.width}x${outputSize.height}",
                )
            } finally {
                heartbeat.cancel()
            }
        }
    }

    private fun dither(request: ExportRequest): String = when (request.config.clarity) {
        Clarity.LOW -> "none"
        Clarity.MEDIUM -> "bayer:bayer_scale=4"
        Clarity.HIGH -> "bayer:bayer_scale=3"
        Clarity.HIGHEST -> "sierra2_4a"
    }

    private fun buildVideoFilter(
        request: ExportRequest,
        outputSize: ExportSize,
        sampleFps: Int,
    ): String {
        val info = request.info
        val crop = request.crop.clamped()
        var cropX = (crop.left * info.width).roundToInt().coerceIn(0, info.width - 2)
        var cropY = (crop.top * info.height).roundToInt().coerceIn(0, info.height - 2)
        var cropW = (crop.width * info.width).roundToInt()
            .coerceIn(2, info.width - cropX)
        var cropH = (crop.height * info.height).roundToInt()
            .coerceIn(2, info.height - cropY)
        if (cropW % 2 != 0) cropW--
        if (cropH % 2 != 0) cropH--
        cropX = cropX.coerceAtMost(info.width - cropW)
        cropY = cropY.coerceAtMost(info.height - cropH)

        val filters = mutableListOf("fps=$sampleFps")
        if (cropX != 0 || cropY != 0 || cropW != info.width || cropH != info.height) {
            filters += "crop=$cropW:$cropH:$cropX:$cropY"
        }
        if (cropW != outputSize.width || cropH != outputSize.height) {
            filters += "scale=${outputSize.width}:${outputSize.height}:flags=lanczos"
        }
        filters += "setsar=1"
        return filters.joinToString(",")
    }

    private fun seconds(value: Double): String =
        String.format(Locale.US, "%.3f", value)

    private fun codecOption(request: ExportRequest): String {
        val mime = request.info.mimeType.orEmpty().lowercase(Locale.US)
        return if (mime.contains("av01") || mime.contains("av1")) {
            "-c:v libdav1d "
        } else {
            ""
        }
    }

    private fun quote(value: String): String =
        "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\""

    private companion object {
        const val TAG = "CutGifExport"
    }
}
