package com.fgxyx.cutgif.data

import kotlin.math.max
import kotlin.math.min

/** Crop bounds in 0..1 coordinates of the oriented video frame. */
data class CropRectN(
    val left: Float = 0f,
    val top: Float = 0f,
    val right: Float = 1f,
    val bottom: Float = 1f,
) {
    val width: Float get() = (right - left).coerceAtLeast(0f)
    val height: Float get() = (bottom - top).coerceAtLeast(0f)
    val centerX: Float get() = (left + right) / 2f
    val centerY: Float get() = (top + bottom) / 2f

    fun clamped(): CropRectN {
        val left = left.coerceIn(0f, 0.95f)
        val top = top.coerceIn(0f, 0.95f)
        val right = right.coerceIn(left + 0.05f, 1f)
        val bottom = bottom.coerceIn(top + 0.05f, 1f)
        return CropRectN(left, top, right, bottom)
    }

    fun translated(dx: Float, dy: Float): CropRectN {
        val width = width
        val height = height
        val newLeft = (left + dx).coerceIn(0f, 1f - width)
        val newTop = (top + dy).coerceIn(0f, 1f - height)
        return CropRectN(newLeft, newTop, newLeft + width, newTop + height)
    }

    companion object {
        val Full = CropRectN()
    }
}

fun CropRectN.withMinimumSize(minSize: Float): CropRectN {
    val width = max(width, minSize)
    val height = max(height, minSize)
    return CropRectN(
        left = left,
        top = top,
        right = min(1f, left + width),
        bottom = min(1f, top + height),
    )
}
