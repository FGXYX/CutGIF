package com.fgxyx.cutgif.data

import android.net.Uri
import androidx.annotation.StringRes
import com.fgxyx.cutgif.R
import kotlin.math.max
import kotlin.math.roundToInt

enum class ExportResolution(
    @StringRes val labelRes: Int,
    val shortSide: Int?,
) {
    P144(R.string.resolution_144, 144),
    P240(R.string.resolution_240, 240),
    P360(R.string.resolution_360, 360),
    CUSTOM(R.string.resolution_custom, null),
}

enum class Clarity(
    @StringRes val labelRes: Int,
    val ditherStrength: Int,
) {
    LOW(R.string.clarity_low, 0),
    MEDIUM(R.string.clarity_medium, 4),
    HIGH(R.string.clarity_high, 8),
    HIGHEST(R.string.clarity_highest, 12),
}

enum class ColorQuality(
    @StringRes val labelRes: Int,
    val paletteSize: Int,
) {
    LOW(R.string.quality_low, 48),
    MEDIUM(R.string.quality_medium, 96),
    HIGH(R.string.quality_high, 160),
    HIGHEST(R.string.quality_highest, 256),
}

enum class ExportFrameRate(val value: Int) {
    FPS_5(5),
    FPS_10(10),
    FPS_15(15),
    FPS_25(25),
    FPS_30(30),
    FPS_50(50),
    FPS_60(60),
}

data class ExportConfig(
    val resolution: ExportResolution = ExportResolution.P360,
    val clarity: Clarity = Clarity.HIGH,
    val colorQuality: ColorQuality = ColorQuality.HIGH,
    val frameRate: ExportFrameRate = ExportFrameRate.FPS_15,
    val customWidth: Int = 360,
    val customHeight: Int = 640,
) {
    val customSizeValid: Boolean
        get() = customWidth in 32..1080 &&
            customHeight in 32..1080 &&
            customWidth % 2 == 0 &&
            customHeight % 2 == 0

    fun outputSize(info: VideoInfo, crop: CropRectN): ExportSize {
        if (resolution == ExportResolution.CUSTOM) {
            return ExportSize(customWidth, customHeight)
        }
        val displayAspect = (crop.width * info.width) / (crop.height * info.height).coerceAtLeast(0.0001f)
        val shortSide = resolution.shortSide ?: 360
        return if (displayAspect >= 1f) {
            val height = shortSide
            val width = (height * displayAspect).roundToInt().coerceIn(2, 1080)
            ExportSize(even(width), even(height))
        } else {
            val width = shortSide
            val height = (width / displayAspect).roundToInt().coerceIn(2, 1080)
            ExportSize(even(width), even(height))
        }
    }

    private fun even(value: Int): Int = max(2, value - (value % 2))
}

data class ExportSize(
    val width: Int,
    val height: Int,
)

data class ExportRequest(
    val info: VideoInfo,
    val startMs: Long,
    val endMs: Long,
    val crop: CropRectN,
    val config: ExportConfig,
)

data class ExportResult(
    val uri: Uri,
    val fileName: String,
    val displayPath: String,
    val sizeBytes: Long,
    val width: Int,
    val height: Int,
    val frameCount: Int,
    val usedFallback: Boolean = false,
)

sealed interface ExportState {
    data object Idle : ExportState

    data class InProgress(
        val frame: Int,
        val totalFrames: Int,
        val percent: Int,
    ) : ExportState

    data class Success(
        val result: ExportResult,
    ) : ExportState

    data class Failed(
        val message: String?,
    ) : ExportState
}
