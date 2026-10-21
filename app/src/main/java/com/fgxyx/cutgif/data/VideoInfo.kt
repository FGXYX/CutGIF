package com.fgxyx.cutgif.data

import android.net.Uri

data class VideoInfo(
    val uri: Uri,
    val displayName: String,
    val durationMs: Long,
    val width: Int,
    val height: Int,
    val rawWidth: Int,
    val rawHeight: Int,
    val rotationDegrees: Int,
    val frameCount: Int = 0,
    val mimeType: String? = null,
) {
    val aspectRatio: Float
        get() = if (height == 0) 1f else width.toFloat() / height.toFloat()
}
