package com.fgxyx.cutgif.ui.editor

import android.os.SystemClock
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.systemGestureExclusion
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import com.fgxyx.cutgif.R
import com.fgxyx.cutgif.data.VideoFrames
import com.fgxyx.cutgif.data.VideoInfo
import com.fgxyx.cutgif.ui.theme.CutGifDimens
import com.fgxyx.cutgif.ui.theme.CutGifShape
import com.fgxyx.cutgif.ui.util.formatClock
import com.fgxyx.cutgif.ui.util.formatTime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.roundToLong

@Composable
fun FilmstripSection(
    info: VideoInfo,
    startMs: Long,
    endMs: Long,
    windowStartMs: Long,
    playheadMs: Long,
    onTrimDragStart: (TrimPointer) -> Unit,
    onTrimDragBy: (TrimPointer, Long) -> Unit,
    onPanBy: (Long) -> Unit,
    onDragEnd: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(CutGifDimens.Space24),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.trim_clip),
                style = MaterialTheme.typography.titleSmall,
                color = scheme.onSurface,
            )
            Spacer(Modifier.weight(1f))
            Text(
                text = "${formatTime(endMs - startMs)} / ${formatTime(info.durationMs)}",
                style = MaterialTheme.typography.labelMedium,
                color = scheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(CutGifDimens.Space8))
        FilmstripTrack(
            info = info,
            startMs = startMs,
            endMs = endMs,
            windowStartMs = windowStartMs,
            playheadMs = playheadMs,
            onTrimDragStart = onTrimDragStart,
            onTrimDragBy = onTrimDragBy,
            onPanBy = onPanBy,
            onDragEnd = onDragEnd,
        )
    }
}

private enum class FilmstripDragMode {
    Start,
    End,
    Pan,
}

@Composable
private fun FilmstripTrack(
    info: VideoInfo,
    startMs: Long,
    endMs: Long,
    windowStartMs: Long,
    playheadMs: Long,
    onTrimDragStart: (TrimPointer) -> Unit,
    onTrimDragBy: (TrimPointer, Long) -> Unit,
    onPanBy: (Long) -> Unit,
    onDragEnd: () -> Unit,
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val scheme = MaterialTheme.colorScheme
    val haptic = LocalHapticFeedback.current
    val textMeasurer = rememberTextMeasurer()
    val labelStyle = MaterialTheme.typography.labelSmall.copy(color = scheme.onSurfaceVariant)
    val stripDescription = stringResource(R.string.trim_clip)
    val durationMs = info.durationMs.coerceAtLeast(1L)
    val windowDurationMs = min(EditorViewModel.MAX_RANGE_MS, durationMs)
    val usesTenSecondCells = windowDurationMs >=
        CutGifDimens.FilmstripIntervalMs * CutGifDimens.FilmstripIntervalCount
    val cellDurationMs = if (usesTenSecondCells) {
        CutGifDimens.FilmstripIntervalMs
    } else {
        (windowDurationMs / CutGifDimens.FilmstripIntervalCount.toDouble())
            .roundToLong()
            .coerceAtLeast(1L)
    }
    val intervalCount = if (usesTenSecondCells) {
        ceil(durationMs.toDouble() / CutGifDimens.FilmstripIntervalMs)
            .toInt()
            .coerceIn(1, CutGifDimens.FilmstripMaxIntervals)
    } else {
        CutGifDimens.FilmstripIntervalCount
    }
    val safeWindowStart = windowStartMs.coerceIn(
        0L,
        (durationMs - windowDurationMs).coerceAtLeast(0L),
    )
    var localWindowStart by remember { mutableLongStateOf(safeWindowStart) }
    var localStartMs by remember { mutableLongStateOf(startMs) }
    var localEndMs by remember { mutableLongStateOf(endMs) }
    var localPlayheadMs by remember { mutableLongStateOf(playheadMs) }
    var isDragging by remember { mutableStateOf(false) }
    var committedWindowStart by remember { mutableLongStateOf(safeWindowStart) }
    var committedStartMs by remember { mutableLongStateOf(startMs) }
    var committedEndMs by remember { mutableLongStateOf(endMs) }
    var lastCommitAt by remember { mutableLongStateOf(0L) }

    LaunchedEffect(windowStartMs, startMs, endMs, playheadMs) {
        if (!isDragging) {
            localWindowStart = windowStartMs.coerceIn(
                0L,
                (durationMs - windowDurationMs).coerceAtLeast(0L),
            )
            localStartMs = startMs
            localEndMs = endMs
            localPlayheadMs = playheadMs
            committedWindowStart = localWindowStart
            committedStartMs = startMs
            committedEndMs = endMs
        }
    }

    val startFraction = ((localStartMs - localWindowStart).toFloat() / windowDurationMs)
        .coerceIn(0f, 1f)
    val endFraction = ((localEndMs - localWindowStart).toFloat() / windowDurationMs)
        .coerceIn(0f, 1f)
    val playheadFraction = ((localPlayheadMs - localWindowStart).toFloat() / windowDurationMs)
        .coerceIn(0f, 1f)
    val latestStartFraction by rememberUpdatedState(startFraction)
    val latestEndFraction by rememberUpdatedState(endFraction)
    val latestWindowStartParam by rememberUpdatedState(windowStartMs)
    val latestStartParam by rememberUpdatedState(startMs)
    val latestEndParam by rememberUpdatedState(endMs)
    val latestPlayheadParam by rememberUpdatedState(playheadMs)
    val latestTrimStart by rememberUpdatedState(onTrimDragStart)
    val latestTrimBy by rememberUpdatedState(onTrimDragBy)
    val latestPanBy by rememberUpdatedState(onPanBy)
    val latestDragEnd by rememberUpdatedState(onDragEnd)

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .height(CutGifDimens.FilmstripHeight)
            .clip(CutGifShape.Filmstrip)
            .background(scheme.surfaceContainerHigh),
        contentAlignment = Alignment.TopCenter,
    ) {
        val viewportWidthPx = with(density) { maxWidth.toPx() }
        val gapPx = with(density) { CutGifDimens.FilmstripCellGap.toPx() }
        val cellCount = CutGifDimens.FilmstripIntervalCount
        val stepPx = viewportWidthPx / cellCount
        val cellWidthPx = (stepPx - gapPx).coerceAtLeast(1f)
        val contentWidthPx = stepPx * cellCount
        val filmHeightPx = with(density) { CutGifDimens.FilmstripFrameHeight.toPx() }
        val handleHalfPx = with(density) { CutGifDimens.TrimHandleWidth.toPx() / 2f }
        val touchRadiusPx = with(density) { CutGifDimens.CropHandleTouchSize.toPx() }
        val targetWidthPx = cellWidthPx.roundToInt().coerceAtLeast(24)
        val targetHeightPx = filmHeightPx.roundToInt().coerceAtLeast(24)

        var thumbnails by remember(info.uri) {
            mutableStateOf<List<ImageBitmap?>>(emptyList())
        }
        val labelLayouts = remember(
            cellDurationMs,
            durationMs,
            thumbnails.size,
            labelStyle,
            textMeasurer,
        ) {
            if (thumbnails.isEmpty()) {
                emptyList()
            } else {
                (0..thumbnails.size).map { index ->
                    val timeMs = if (index == thumbnails.size) {
                        durationMs
                    } else {
                        (index * cellDurationMs).coerceAtMost(durationMs)
                    }
                    textMeasurer.measure(formatClock(timeMs), labelStyle)
                }
            }
        }

        LaunchedEffect(
            info.uri,
            intervalCount,
            cellDurationMs,
            targetWidthPx,
            targetHeightPx,
        ) {
            thumbnails = List(intervalCount) { null }
            VideoFrames.loadIntervalThumbnails(
                context = context,
                info = info,
                intervalMs = cellDurationMs,
                maxIntervals = if (usesTenSecondCells) {
                    CutGifDimens.FilmstripMaxIntervals
                } else {
                    CutGifDimens.FilmstripIntervalCount
                },
                targetWidth = targetWidthPx,
                targetHeight = targetHeightPx,
            ) { index, bitmap ->
                withContext(Dispatchers.Main) {
                    if (index in thumbnails.indices) {
                        thumbnails = thumbnails.toMutableList().also { list ->
                            list[index] = bitmap.asImageBitmap()
                        }
                    }
                }
            }
        }

        Box(
            modifier = Modifier
                .width(with(density) { contentWidthPx.toDp() })
                .fillMaxHeight()
                .clipToBounds()
                .systemGestureExclusion()
                .semantics { contentDescription = stripDescription }
                .pointerInput(windowDurationMs, contentWidthPx) {
                    var mode = FilmstripDragMode.Pan
                    val maxWindowStart = (durationMs - windowDurationMs).coerceAtLeast(0L)

                    fun commitPan(force: Boolean) {
                        val now = SystemClock.uptimeMillis()
                        if (!force && now - lastCommitAt < 80L) return
                        val delta = localWindowStart - committedWindowStart
                        if (delta != 0L) {
                            latestPanBy(delta)
                            committedWindowStart = localWindowStart
                            committedStartMs = localStartMs
                            committedEndMs = localEndMs
                        }
                        lastCommitAt = now
                    }

                    fun commitTrim(force: Boolean) {
                        val now = SystemClock.uptimeMillis()
                        if (!force && now - lastCommitAt < 80L) return
                        val startDelta = localStartMs - committedStartMs
                        val endDelta = localEndMs - committedEndMs
                        if (mode == FilmstripDragMode.Start && startDelta != 0L) {
                            latestTrimBy(TrimPointer.Start, startDelta)
                        }
                        if (mode == FilmstripDragMode.End && endDelta != 0L) {
                            latestTrimBy(TrimPointer.End, endDelta)
                        }
                        committedStartMs = localStartMs
                        committedEndMs = localEndMs
                        lastCommitAt = now
                    }

                    fun finishDrag() {
                        if (!isDragging) return
                        when (mode) {
                            FilmstripDragMode.Pan -> commitPan(force = true)
                            FilmstripDragMode.Start, FilmstripDragMode.End -> commitTrim(force = true)
                        }
                        latestDragEnd()
                        isDragging = false
                    }

                    detectDragGestures(
                        onDragStart = { position ->
                            isDragging = true
                            localWindowStart = latestWindowStartParam.coerceIn(0L, maxWindowStart)
                            localStartMs = latestStartParam
                            localEndMs = latestEndParam
                            localPlayheadMs = latestPlayheadParam
                            committedWindowStart = localWindowStart
                            committedStartMs = localStartMs
                            committedEndMs = localEndMs
                            lastCommitAt = SystemClock.uptimeMillis()
                            val startX = latestStartFraction * contentWidthPx
                            val endX = latestEndFraction * contentWidthPx
                            val startDistance = abs(position.x - startX)
                            val endDistance = abs(position.x - endX)
                            mode = when {
                                startDistance <= touchRadiusPx && endDistance <= touchRadiusPx -> {
                                    if (startDistance <= endDistance) {
                                        FilmstripDragMode.Start
                                    } else {
                                        FilmstripDragMode.End
                                    }
                                }
                                startDistance <= touchRadiusPx -> FilmstripDragMode.Start
                                endDistance <= touchRadiusPx -> FilmstripDragMode.End
                                else -> FilmstripDragMode.Pan
                            }
                            if (mode != FilmstripDragMode.Pan) {
                                latestTrimStart(
                                    if (mode == FilmstripDragMode.Start) {
                                        TrimPointer.Start
                                    } else {
                                        TrimPointer.End
                                    },
                                )
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            }
                        },
                        onDragEnd = { finishDrag() },
                        onDragCancel = { finishDrag() },
                        onDrag = { change, amount ->
                            change.consume()
                            val deltaMs =
                                (amount.x / contentWidthPx * windowDurationMs).roundToLong()
                            when (mode) {
                                FilmstripDragMode.Start -> {
                                    val windowStart = localWindowStart
                                    val windowEnd = windowStart + windowDurationMs
                                    val lower = maxOf(
                                        0L,
                                        localEndMs - EditorViewModel.MAX_RANGE_MS,
                                        windowStart,
                                    )
                                    val upper = localEndMs - EditorViewModel.MIN_RANGE_MS
                                    localStartMs = (localStartMs + deltaMs)
                                        .coerceIn(lower, upper.coerceAtLeast(lower))
                                    localPlayheadMs = localStartMs
                                    commitTrim(force = false)
                                }
                                FilmstripDragMode.End -> {
                                    val lower = localStartMs + EditorViewModel.MIN_RANGE_MS
                                    val upper = minOf(
                                        durationMs,
                                        localStartMs + EditorViewModel.MAX_RANGE_MS,
                                        localWindowStart + windowDurationMs,
                                    )
                                    localEndMs = (localEndMs + deltaMs)
                                        .coerceIn(lower, upper.coerceAtLeast(lower))
                                    localPlayheadMs = localStartMs
                                    commitTrim(force = false)
                                }
                                FilmstripDragMode.Pan -> {
                                    val newWindow = (localWindowStart - deltaMs)
                                        .coerceIn(0L, maxWindowStart)
                                    val actual = newWindow - localWindowStart
                                    if (actual != 0L) {
                                        localWindowStart = newWindow
                                        localStartMs = (localStartMs + actual)
                                            .coerceIn(0L, durationMs)
                                        localEndMs = (localEndMs + actual)
                                            .coerceIn(0L, durationMs)
                                        localPlayheadMs = localStartMs
                                        commitPan(force = false)
                                    }
                                }
                            }
                        },
                    )
                },
        ) {
            Canvas(Modifier.fillMaxSize()) {
                drawFilmstripContent(
                    cellCount = cellCount,
                    cellWidthPx = cellWidthPx,
                    gapPx = gapPx,
                    filmHeightPx = filmHeightPx,
                    windowStartMs = localWindowStart,
                    windowDurationMs = windowDurationMs,
                    durationMs = durationMs,
                    cellDurationMs = cellDurationMs,
                    thumbnails = thumbnails,
                    labelLayouts = labelLayouts,
                    startFraction = startFraction,
                    endFraction = endFraction,
                    playheadFraction = playheadFraction,
                    scheme = scheme,
                    handleHalfPx = handleHalfPx,
                )
            }
        }
    }
}

private fun DrawScope.drawFilmstripContent(
    cellCount: Int,
    cellWidthPx: Float,
    gapPx: Float,
    filmHeightPx: Float,
    windowStartMs: Long,
    windowDurationMs: Long,
    durationMs: Long,
    cellDurationMs: Long,
    thumbnails: List<ImageBitmap?>,
    labelLayouts: List<androidx.compose.ui.text.TextLayoutResult>,
    startFraction: Float,
    endFraction: Float,
    playheadFraction: Float,
    scheme: ColorScheme,
    handleHalfPx: Float,
) {
    val contentWidth = cellWidthPx * cellCount + gapPx * cellCount
    val intervalMs = cellDurationMs
    val stepPx = contentWidth / cellCount
    val firstVisible = floor(windowStartMs.toDouble() / intervalMs).toInt() - 1
    val lastVisible = ceil(
        (windowStartMs + windowDurationMs).toDouble() / intervalMs,
    ).toInt() + 1
    val firstCell = firstVisible.coerceAtLeast(0)
    val lastCell = lastVisible.coerceAtMost(thumbnails.lastIndex)

    if (lastCell >= firstCell) {
        for (index in firstCell..lastCell) {
            val left = ((index * intervalMs - windowStartMs).toDouble() /
                windowDurationMs * contentWidth).toFloat()
            drawRect(
                color = if (index % 2 == 0) {
                    scheme.surfaceContainerHighest
                } else {
                    scheme.surfaceContainer
                },
                topLeft = Offset(left, 0f),
                size = Size(cellWidthPx, filmHeightPx),
            )
            val image = thumbnails.getOrNull(index)
            if (image != null) {
                drawImage(
                    image = image,
                    srcOffset = IntOffset.Zero,
                    srcSize = IntSize(image.width, image.height),
                    dstOffset = IntOffset(left.roundToInt(), 0),
                    dstSize = IntSize(
                        cellWidthPx.roundToInt().coerceAtLeast(1),
                        filmHeightPx.roundToInt().coerceAtLeast(1),
                    ),
                    filterQuality = FilterQuality.Low,
                )
            } else {
                drawCircle(
                    color = scheme.onSurfaceVariant.copy(alpha = 0.22f),
                    radius = CutGifDimens.Space4.toPx(),
                    center = Offset(left + cellWidthPx / 2f, filmHeightPx / 2f),
                )
            }
        }
    }

    val tickHeight = CutGifDimens.Space4.toPx()
    val hairline = CutGifDimens.Hairline.toPx()
    drawLine(
        color = scheme.outlineVariant,
        start = Offset(0f, filmHeightPx),
        end = Offset(contentWidth, filmHeightPx),
        strokeWidth = hairline,
    )
    val labelTop = filmHeightPx + tickHeight + hairline
    val firstLabel = firstVisible.coerceAtLeast(0)
    val lastLabel = lastVisible.coerceAtMost(thumbnails.size)
    if (lastLabel >= firstLabel) {
        for (index in firstLabel..lastLabel) {
            val timeMs = if (index == thumbnails.size) {
                durationMs
            } else {
                (index * intervalMs).coerceIn(0L, durationMs)
            }
            val x = ((timeMs - windowStartMs).toDouble() /
                windowDurationMs * contentWidth).toFloat()
            drawLine(
                color = scheme.outline,
                start = Offset(x, filmHeightPx),
                end = Offset(x, filmHeightPx + tickHeight),
                strokeWidth = hairline,
            )
            val measured = labelLayouts.getOrNull(index) ?: continue
            val textWidth = measured.size.width.toFloat()
            if (x >= 0f && x <= contentWidth) {
                val edgeInset = handleHalfPx + CutGifDimens.Space4.toPx()
                val labelX = when {
                    x <= textWidth / 2f -> edgeInset
                    x >= contentWidth - textWidth / 2f -> contentWidth - textWidth - edgeInset
                    else -> x - textWidth / 2f
                }
                drawText(
                    textLayoutResult = measured,
                    topLeft = Offset(labelX, labelTop),
                )
            }
        }
    }

    val startX = startFraction * contentWidth
    val endX = endFraction * contentWidth
    val playX = playheadFraction * contentWidth
    val scrim = scheme.scrim.copy(alpha = 0.38f)
    val handleWidth = CutGifDimens.TrimHandleWidth.toPx()
    val handleRadius = handleHalfPx

    drawRect(
        color = scrim,
        size = Size(startX.coerceAtLeast(0f), filmHeightPx),
    )
    drawRect(
        color = scrim,
        topLeft = Offset(endX, 0f),
        size = Size((contentWidth - endX).coerceAtLeast(0f), filmHeightPx),
    )
    drawRect(
        color = scheme.primary.copy(alpha = 0.1f),
        topLeft = Offset(startX, 0f),
        size = Size((endX - startX).coerceAtLeast(0f), filmHeightPx),
    )
    drawLine(
        color = scheme.primary.copy(alpha = 0.8f),
        start = Offset(startX, 0f),
        end = Offset(endX, 0f),
        strokeWidth = CutGifDimens.Stroke.toPx(),
    )
    drawLine(
        color = scheme.primary.copy(alpha = 0.8f),
        start = Offset(startX, filmHeightPx),
        end = Offset(endX, filmHeightPx),
        strokeWidth = CutGifDimens.Stroke.toPx(),
    )

    drawRoundRect(
        color = scheme.primary,
        topLeft = Offset(startX - handleRadius, 0f),
        size = Size(handleWidth, size.height),
        cornerRadius = CornerRadius(handleRadius, handleRadius),
    )
    drawRoundRect(
        color = scheme.primary,
        topLeft = Offset(endX - handleRadius, 0f),
        size = Size(handleWidth, size.height),
        cornerRadius = CornerRadius(handleRadius, handleRadius),
    )

    val gripY1 = size.height * 0.44f
    val gripY2 = size.height * 0.52f
    val gripHalf = CutGifDimens.GripHalfWidth.toPx()
    for (x in listOf(startX, endX)) {
        drawLine(
            color = scheme.onPrimary,
            start = Offset(x - gripHalf, gripY1),
            end = Offset(x + gripHalf, gripY1),
            strokeWidth = CutGifDimens.Stroke.toPx(),
            cap = StrokeCap.Round,
        )
        drawLine(
            color = scheme.onPrimary,
            start = Offset(x - gripHalf, gripY2),
            end = Offset(x + gripHalf, gripY2),
            strokeWidth = CutGifDimens.Stroke.toPx(),
            cap = StrokeCap.Round,
        )
    }

    drawLine(
        color = scheme.tertiary,
        start = Offset(playX, 0f),
        end = Offset(playX, size.height),
        strokeWidth = CutGifDimens.PlayheadWidth.toPx(),
    )
    drawCircle(
        color = scheme.tertiary,
        radius = CutGifDimens.Space4.toPx(),
        center = Offset(playX, CutGifDimens.Space4.toPx()),
    )
}
