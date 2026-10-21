package com.fgxyx.cutgif.ui.editor

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.systemGestureExclusion
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntSize
import com.fgxyx.cutgif.R
import com.fgxyx.cutgif.data.CropRectN
import com.fgxyx.cutgif.data.VideoInfo
import com.fgxyx.cutgif.ui.theme.CutGifDimens
import kotlin.math.abs
import kotlin.math.min

private enum class CropDragMode {
    None,
    Move,
    TopLeft,
    TopRight,
    BottomLeft,
    BottomRight,
}

@Composable
fun CropOverlay(
    info: VideoInfo,
    crop: CropRectN,
    ratio: CropRatio,
    onCropChange: (CropRectN) -> Unit,
    onResetCrop: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val density = LocalDensity.current
    val haptic = LocalHapticFeedback.current
    var overlaySize by remember { mutableStateOf(IntSize.Zero) }
    val currentCrop by rememberUpdatedState(crop)
    val currentRatio by rememberUpdatedState(ratio)
    val ratioLabel = stringResource(ratio.labelRes)
    val resetDescription = stringResource(R.string.reset_crop)

    val videoRect = remember(overlaySize, info.width, info.height) {
        fitVideoRect(
            container = Size(overlaySize.width.toFloat(), overlaySize.height.toFloat()),
            videoAspect = info.aspectRatio,
        )
    }

    Box(
        modifier = modifier
            .systemGestureExclusion()
            .onSizeChanged { overlaySize = it },
    ) {
        if (overlaySize.width > 0 && overlaySize.height > 0 && !videoRect.isEmpty) {
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(videoRect, info.aspectRatio) {
                        var mode = CropDragMode.None
                        var startCrop = CropRectN.Full
                        var totalDrag = Offset.Zero
                        val minSizePx = with(density) { CutGifDimens.Space32.toPx() }

                        detectDragGestures(
                            onDragStart = { position ->
                                startCrop = currentCrop
                                totalDrag = Offset.Zero
                                val cropPx = cropToRect(startCrop, videoRect)
                                mode = hitTestCrop(
                                    position = position,
                                    cropRect = cropPx,
                                    touchRadius = minSizePx,
                                )
                                if (mode != CropDragMode.None) {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                }
                            },
                            onDrag = { change, dragAmount ->
                                if (mode == CropDragMode.None) return@detectDragGestures
                                change.consume()
                                totalDrag += dragAmount
                                val dx = totalDrag.x / videoRect.width
                                val dy = totalDrag.y / videoRect.height
                                val proposed = when (mode) {
                                    CropDragMode.Move -> startCrop.translated(dx, dy)
                                    CropDragMode.TopLeft -> resizeCrop(
                                        start = startCrop,
                                        corner = mode,
                                        dx = dx,
                                        dy = dy,
                                        pixelRatio = currentRatio.pixelRatio,
                                        videoAspect = info.aspectRatio,
                                        minWidthNorm = minSizePx / videoRect.width,
                                        minHeightNorm = minSizePx / videoRect.height,
                                    )
                                    CropDragMode.TopRight -> resizeCrop(
                                        start = startCrop,
                                        corner = mode,
                                        dx = dx,
                                        dy = dy,
                                        pixelRatio = currentRatio.pixelRatio,
                                        videoAspect = info.aspectRatio,
                                        minWidthNorm = minSizePx / videoRect.width,
                                        minHeightNorm = minSizePx / videoRect.height,
                                    )
                                    CropDragMode.BottomLeft -> resizeCrop(
                                        start = startCrop,
                                        corner = mode,
                                        dx = dx,
                                        dy = dy,
                                        pixelRatio = currentRatio.pixelRatio,
                                        videoAspect = info.aspectRatio,
                                        minWidthNorm = minSizePx / videoRect.width,
                                        minHeightNorm = minSizePx / videoRect.height,
                                    )
                                    CropDragMode.BottomRight -> resizeCrop(
                                        start = startCrop,
                                        corner = mode,
                                        dx = dx,
                                        dy = dy,
                                        pixelRatio = currentRatio.pixelRatio,
                                        videoAspect = info.aspectRatio,
                                        minWidthNorm = minSizePx / videoRect.width,
                                        minHeightNorm = minSizePx / videoRect.height,
                                    )
                                    CropDragMode.None -> startCrop
                                }
                                onCropChange(proposed)
                            },
                            onDragEnd = { mode = CropDragMode.None },
                            onDragCancel = { mode = CropDragMode.None },
                        )
                    },
            ) {
                val cropRect = cropToRect(crop, videoRect)
                val scrim = scheme.scrim.copy(alpha = 0.56f)

                drawRect(
                    color = scrim,
                    topLeft = Offset(videoRect.left, videoRect.top),
                    size = Size(videoRect.width, cropRect.top - videoRect.top),
                )
                drawRect(
                    color = scrim,
                    topLeft = Offset(videoRect.left, cropRect.bottom),
                    size = Size(videoRect.width, videoRect.bottom - cropRect.bottom),
                )
                drawRect(
                    color = scrim,
                    topLeft = Offset(videoRect.left, cropRect.top),
                    size = Size(cropRect.left - videoRect.left, cropRect.height),
                )
                drawRect(
                    color = scrim,
                    topLeft = Offset(cropRect.right, cropRect.top),
                    size = Size(videoRect.right - cropRect.right, cropRect.height),
                )

                val border = CutGifDimens.CropStroke.toPx()
                drawRect(
                    color = scheme.primary,
                    topLeft = cropRect.topLeft,
                    size = cropRect.size,
                    style = Stroke(width = border),
                )

                val gridColor = scheme.onPrimary.copy(alpha = 0.48f)
                for (i in 1..2) {
                    val x = cropRect.left + cropRect.width * i / 3f
                    val y = cropRect.top + cropRect.height * i / 3f
                    drawLine(
                        color = gridColor,
                        start = Offset(x, cropRect.top),
                        end = Offset(x, cropRect.bottom),
                        strokeWidth = CutGifDimens.Hairline.toPx(),
                    )
                    drawLine(
                        color = gridColor,
                        start = Offset(cropRect.left, y),
                        end = Offset(cropRect.right, y),
                        strokeWidth = CutGifDimens.Hairline.toPx(),
                    )
                }

                val corner = with(density) { CutGifDimens.Space24.toPx() }
                val cornerStroke = with(density) { CutGifDimens.CropCornerStroke.toPx() }
                val topLeft = cropRect.topLeft
                val topRight = Offset(cropRect.right, cropRect.top)
                val bottomLeft = Offset(cropRect.left, cropRect.bottom)
                val bottomRight = Offset(cropRect.right, cropRect.bottom)
                drawLine(scheme.primary, topLeft, topLeft + Offset(corner, 0f), cornerStroke, StrokeCap.Round)
                drawLine(scheme.primary, topLeft, topLeft + Offset(0f, corner), cornerStroke, StrokeCap.Round)
                drawLine(scheme.primary, topRight, topRight - Offset(corner, 0f), cornerStroke, StrokeCap.Round)
                drawLine(scheme.primary, topRight, topRight + Offset(0f, corner), cornerStroke, StrokeCap.Round)
                drawLine(scheme.primary, bottomLeft, bottomLeft + Offset(corner, 0f), cornerStroke, StrokeCap.Round)
                drawLine(scheme.primary, bottomLeft, bottomLeft - Offset(0f, corner), cornerStroke, StrokeCap.Round)
                drawLine(scheme.primary, bottomRight, bottomRight - Offset(corner, 0f), cornerStroke, StrokeCap.Round)
                drawLine(scheme.primary, bottomRight, bottomRight - Offset(0f, corner), cornerStroke, StrokeCap.Round)
            }
        }

        Row(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(CutGifDimens.Space12),
            horizontalArrangement = Arrangement.spacedBy(CutGifDimens.Space8),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                shape = CircleShape,
                color = scheme.surfaceContainerHighest.copy(alpha = 0.92f),
                contentColor = scheme.onSurface,
            ) {
                Row(
                    modifier = Modifier.padding(
                        horizontal = CutGifDimens.Space12,
                        vertical = CutGifDimens.Space4,
                    ),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = ratioLabel,
                        style = MaterialTheme.typography.labelMedium,
                    )
                    Spacer(Modifier.width(CutGifDimens.Space4))
                    Text(
                        text = stringResource(R.string.crop_hint),
                        style = MaterialTheme.typography.labelSmall,
                        color = scheme.onSurfaceVariant,
                    )
                }
            }
        }

        FilledTonalIconButton(
            onClick = onResetCrop,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(CutGifDimens.Space12)
                .size(CutGifDimens.Space40),
        ) {
            Icon(
                imageVector = Icons.Rounded.Refresh,
                contentDescription = resetDescription,
                modifier = Modifier.size(CutGifDimens.IconCompact),
            )
        }
    }
}

private fun fitVideoRect(container: Size, videoAspect: Float): Rect {
    if (container.width <= 0f || container.height <= 0f || videoAspect <= 0f) return Rect.Zero
    var width = container.width
    var height = width / videoAspect
    if (height > container.height) {
        height = container.height
        width = height * videoAspect
    }
    val left = (container.width - width) / 2f
    val top = (container.height - height) / 2f
    return Rect(left, top, left + width, top + height)
}

private fun cropToRect(crop: CropRectN, videoRect: Rect): Rect {
    return Rect(
        left = videoRect.left + crop.left * videoRect.width,
        top = videoRect.top + crop.top * videoRect.height,
        right = videoRect.left + crop.right * videoRect.width,
        bottom = videoRect.top + crop.bottom * videoRect.height,
    )
}

private fun hitTestCrop(position: Offset, cropRect: Rect, touchRadius: Float): CropDragMode {
    val corners = listOf(
        CropDragMode.TopLeft to cropRect.topLeft,
        CropDragMode.TopRight to Offset(cropRect.right, cropRect.top),
        CropDragMode.BottomLeft to Offset(cropRect.left, cropRect.bottom),
        CropDragMode.BottomRight to cropRect.bottomRight,
    )
    val nearest = corners.minByOrNull { (_, corner) ->
        (position - corner).getDistance()
    }
    if (nearest != null && (position - nearest.second).getDistance() <= touchRadius) {
        return nearest.first
    }
    return if (cropRect.contains(position)) CropDragMode.Move else CropDragMode.None
}

private fun resizeCrop(
    start: CropRectN,
    corner: CropDragMode,
    dx: Float,
    dy: Float,
    pixelRatio: Float?,
    videoAspect: Float,
    minWidthNorm: Float,
    minHeightNorm: Float,
): CropRectN {
    val draggingLeft = corner == CropDragMode.TopLeft || corner == CropDragMode.BottomLeft
    val draggingTop = corner == CropDragMode.TopLeft || corner == CropDragMode.TopRight

    val initialX = if (draggingLeft) start.left else start.right
    val initialY = if (draggingTop) start.top else start.bottom
    val targetX = (initialX + dx).coerceIn(0f, 1f)
    val targetY = (initialY + dy).coerceIn(0f, 1f)

    if (pixelRatio == null) {
        var left = start.left
        var top = start.top
        var right = start.right
        var bottom = start.bottom
        when (corner) {
            CropDragMode.TopLeft -> {
                left = targetX.coerceIn(0f, start.right - minWidthNorm)
                top = targetY.coerceIn(0f, start.bottom - minHeightNorm)
            }
            CropDragMode.TopRight -> {
                right = targetX.coerceIn(start.left + minWidthNorm, 1f)
                top = targetY.coerceIn(0f, start.bottom - minHeightNorm)
            }
            CropDragMode.BottomLeft -> {
                left = targetX.coerceIn(0f, start.right - minWidthNorm)
                bottom = targetY.coerceIn(start.top + minHeightNorm, 1f)
            }
            CropDragMode.BottomRight -> {
                right = targetX.coerceIn(start.left + minWidthNorm, 1f)
                bottom = targetY.coerceIn(start.top + minHeightNorm, 1f)
            }
            else -> Unit
        }
        return CropRectN(left, top, right, bottom).clamped()
    }

    val heightPerWidth = (videoAspect / pixelRatio).coerceAtLeast(0.0001f)
    val anchorX = if (draggingLeft) start.right else start.left
    val anchorY = if (draggingTop) start.bottom else start.top
    val rawWidth = abs(targetX - anchorX)
    val rawHeight = abs(targetY - anchorY)
    var width = maxOf(rawWidth, rawHeight / heightPerWidth)
    val minWidth = maxOf(minWidthNorm, minHeightNorm / heightPerWidth)
    val availableWidth = if (draggingLeft) anchorX else 1f - anchorX
    val availableHeight = if (draggingTop) anchorY else 1f - anchorY
    val maxWidth = min(availableWidth, availableHeight / heightPerWidth)
    width = width.coerceIn(min(minWidth, maxWidth), maxWidth.coerceAtLeast(0.001f))
    val height = width * heightPerWidth

    val left = if (draggingLeft) anchorX - width else anchorX
    val right = if (draggingLeft) anchorX else anchorX + width
    val top = if (draggingTop) anchorY - height else anchorY
    val bottom = if (draggingTop) anchorY else anchorY + height
    return CropRectN(left, top, right, bottom).clamped()
}
