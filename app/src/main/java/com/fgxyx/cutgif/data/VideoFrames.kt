package com.fgxyx.cutgif.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.provider.OpenableColumns
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.roundToInt

object VideoFrames {

    suspend fun loadInfo(context: Context, uri: Uri): VideoInfo = withContext(Dispatchers.IO) {
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(context, uri)
            val retrieverDuration = retriever
                .extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull() ?: 0L
            // Some containers/store entries report a truncated duration in
            // MediaMetadataRetriever. The extractor track duration is often
            // correct, so use the longer value to avoid silently trimming the
            // timeline (and the filmstrip) in half.
            val extractorDuration = videoTrackDurationMs(context, uri) ?: 0L
            val duration = max(retrieverDuration, extractorDuration)
            val rawWidth = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)
                ?.toIntOrNull() ?: 0
            val rawHeight = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)
                ?.toIntOrNull() ?: 0
            val rotation = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)
                ?.toIntOrNull() ?: 0
            val containerMime = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_MIMETYPE)
            val codecMime = videoTrackMimeType(context, uri) ?: containerMime
            val frameCount = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_FRAME_COUNT)
                    ?.toIntOrNull() ?: 0
            } else {
                0
            }

            if (duration <= 0L) {
                error("Video duration is unavailable")
            }

            val frame = retriever.getFrameAtTime(0L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
            val width: Int
            val height: Int
            when {
                rawWidth > 0 && rawHeight > 0 -> {
                    if (rotation == 90 || rotation == 270) {
                        width = rawHeight
                        height = rawWidth
                    } else {
                        width = rawWidth
                        height = rawHeight
                    }
                }
                frame != null -> {
                    width = frame.width
                    height = frame.height
                }
                else -> error("Video dimensions are unavailable")
            }
            frame?.recycle()

            VideoInfo(
                uri = uri,
                displayName = queryDisplayName(context, uri),
                durationMs = duration,
                width = max(1, width),
                height = max(1, height),
                rawWidth = rawWidth,
                rawHeight = rawHeight,
                rotationDegrees = ((rotation % 360) + 360) % 360,
                frameCount = frameCount,
                mimeType = codecMime,
            )
        } finally {
            runCatching { retriever.release() }
        }
    }

    suspend fun loadThumbnails(        context: Context,
        info: VideoInfo,
        count: Int,
        targetWidth: Int,
        targetHeight: Int,
        onFrame: suspend (index: Int, bitmap: Bitmap) -> Unit,
    ) = withContext(Dispatchers.IO) {
        if (count <= 0) return@withContext
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(context, info.uri)
            for (index in 0 until count) {
                coroutineContext.ensureActive()
                val timeMs = if (count == 1) {
                    0L
                } else {
                    (info.durationMs.toDouble() * index / count).toLong()
                }
                val frame = orientedFrame(
                    retriever = retriever,
                    timeUs = timeMs * 1_000L,
                    option = MediaMetadataRetriever.OPTION_CLOSEST_SYNC,
                    rawWidth = info.rawWidth,
                    rawHeight = info.rawHeight,
                    rotationDegrees = info.rotationDegrees,
                ) ?: continue
                val thumb = centerCrop(frame, targetWidth, targetHeight)
                if (thumb !== frame) frame.recycle()
                onFrame(index, thumb)
            }
        } finally {
            runCatching { retriever.release() }
        }
    }

    suspend fun loadIntervalThumbnails(
        context: Context,
        info: VideoInfo,
        intervalMs: Long,
        maxIntervals: Int,
        targetWidth: Int,
        targetHeight: Int,
        onFrame: suspend (index: Int, bitmap: Bitmap) -> Unit,
    ) = withContext(Dispatchers.IO) {
        if (intervalMs <= 0L) return@withContext
        val count = ceil(info.durationMs.toDouble() / intervalMs)
            .toInt()
            .coerceIn(1, maxIntervals)
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(context, info.uri)
            for (index in 0 until count) {
                coroutineContext.ensureActive()
                // Use the middle of each fixed interval as its poster frame.
                val timeMs = (index * intervalMs + intervalMs / 2L)
                    .coerceIn(0L, (info.durationMs - 1L).coerceAtLeast(0L))
                val frame = orientedFrame(
                    retriever = retriever,
                    timeUs = timeMs * 1_000L,
                    option = MediaMetadataRetriever.OPTION_CLOSEST_SYNC,
                    rawWidth = info.rawWidth,
                    rawHeight = info.rawHeight,
                    rotationDegrees = info.rotationDegrees,
                ) ?: continue
                val thumb = centerCrop(frame, targetWidth, targetHeight)
                if (thumb !== frame) frame.recycle()
                onFrame(index, thumb)
            }
        } finally {
            runCatching { retriever.release() }
        }
    }

    fun orientedFrame(
        retriever: MediaMetadataRetriever,
        timeUs: Long,
        option: Int,
        rawWidth: Int,
        rawHeight: Int,
        rotationDegrees: Int,
    ): Bitmap? {
        val frame = retriever.getFrameAtTime(timeUs, option) ?: return null
        return orientBitmap(frame, rawWidth, rawHeight, rotationDegrees)
    }

    fun orientBitmap(
        frame: Bitmap,
        rawWidth: Int,
        rawHeight: Int,
        rotationDegrees: Int,
    ): Bitmap {
        if (rotationDegrees == 0) return frame

        val alreadyRotated = rawWidth > 0 && rawHeight > 0 &&
            rawWidth != rawHeight &&
            frame.width == rawHeight && frame.height == rawWidth
        if (alreadyRotated && (rotationDegrees == 90 || rotationDegrees == 270)) {
            return frame
        }

        val matrix = Matrix().apply { postRotate(rotationDegrees.toFloat()) }
        return try {
            Bitmap.createBitmap(frame, 0, 0, frame.width, frame.height, matrix, true)
                .also { rotated -> if (rotated !== frame) frame.recycle() }
        } catch (_: Throwable) {
            frame
        }
    }

    fun centerCrop(source: Bitmap, targetWidth: Int, targetHeight: Int): Bitmap {
        val safeTargetWidth = max(1, targetWidth)
        val safeTargetHeight = max(1, targetHeight)
        val scale = max(
            safeTargetWidth.toFloat() / source.width,
            safeTargetHeight.toFloat() / source.height,
        )
        val scaledWidth = (source.width * scale).roundToInt().coerceAtLeast(safeTargetWidth)
        val scaledHeight = (source.height * scale).roundToInt().coerceAtLeast(safeTargetHeight)
        val left = ((scaledWidth - safeTargetWidth) / 2f).roundToInt()
        val top = ((scaledHeight - safeTargetHeight) / 2f).roundToInt()
        val matrix = Matrix().apply {
            postScale(scale, scale)
            postTranslate(-left.toFloat(), -top.toFloat())
        }
        return try {
            Bitmap.createBitmap(source, 0, 0, source.width, source.height, matrix, true)
        } catch (_: Throwable) {
            Bitmap.createScaledBitmap(source, safeTargetWidth, safeTargetHeight, true)
        }
    }

    private fun queryDisplayName(context: Context, uri: Uri): String {
        val projection = arrayOf(OpenableColumns.DISPLAY_NAME)
        runCatching {
            context.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (index >= 0) {
                        cursor.getString(index)?.takeIf { it.isNotBlank() }?.let { return it }
                    }
                }
            }
        }
        return uri.lastPathSegment?.substringAfterLast('/')?.takeIf { it.isNotBlank() } ?: "video.mp4"
    }

    private fun videoTrackMimeType(context: Context, uri: Uri): String? {
        val extractor = MediaExtractor()
        return try {
            extractor.setDataSource(context, uri, null)
            for (track in 0 until extractor.trackCount) {
                val mime = extractor.getTrackFormat(track)
                    .getString(MediaFormat.KEY_MIME)
                    ?: continue
                if (mime.startsWith("video/")) return mime
            }
            null
        } catch (_: Throwable) {
            null
        } finally {
            runCatching { extractor.release() }
        }
    }

    private fun videoTrackDurationMs(context: Context, uri: Uri): Long? {
        val extractor = MediaExtractor()
        return try {
            extractor.setDataSource(context, uri, null)
            var durationUs = -1L
            for (track in 0 until extractor.trackCount) {
                val format = extractor.getTrackFormat(track)
                val mime = format.getString(MediaFormat.KEY_MIME) ?: continue
                if (!mime.startsWith("video/")) continue
                if (format.containsKey(MediaFormat.KEY_DURATION)) {
                    durationUs = max(durationUs, format.getLong(MediaFormat.KEY_DURATION))
                }
            }
            if (durationUs > 0L) durationUs / 1_000L else null
        } catch (_: Throwable) {
            null
        } finally {
            runCatching { extractor.release() }
        }
    }
}
