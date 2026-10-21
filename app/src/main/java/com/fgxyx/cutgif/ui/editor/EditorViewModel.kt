package com.fgxyx.cutgif.ui.editor

import android.app.Application
import android.net.Uri
import androidx.annotation.StringRes
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.fgxyx.cutgif.R
import com.fgxyx.cutgif.data.CropRectN
import com.fgxyx.cutgif.data.ExportConfig
import com.fgxyx.cutgif.data.ExportRequest
import com.fgxyx.cutgif.data.ExportState
import com.fgxyx.cutgif.data.GifExportRepository
import com.fgxyx.cutgif.data.VideoFrames
import com.fgxyx.cutgif.data.VideoInfo
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.math.roundToLong

enum class TrimPointer {
    Start,
    End,
}

enum class CropRatio(
    @StringRes val labelRes: Int,
    val pixelRatio: Float?,
) {
    Free(R.string.ratio_free, null),
    Square(R.string.ratio_1_1, 1f),
    Widescreen(R.string.ratio_16_9, 16f / 9f),
    Portrait(R.string.ratio_9_16, 9f / 16f),
    Classic(R.string.ratio_4_3, 4f / 3f),
}

enum class EditorSheet {
    Aspect,
    Speed,
    Export,
}

sealed interface EditorEffect {
    data class SeekTo(val positionMs: Long) : EditorEffect
    data class Message(@StringRes val messageRes: Int) : EditorEffect
}

data class EditorUiState(
    val videoInfo: VideoInfo? = null,
    val isLoading: Boolean = false,
    val loadFailed: Boolean = false,
    val isPlaying: Boolean = false,
    val playheadMs: Long = 0L,
    val startMs: Long = 0L,
    val endMs: Long = 0L,
    val windowStartMs: Long = 0L,
    val selectedPointer: TrimPointer = TrimPointer.Start,
    val cropRatio: CropRatio = CropRatio.Free,
    val cropRect: CropRectN = CropRectN.Full,
    val speed: Float = 1f,
    val sheet: EditorSheet? = null,
    val exportConfig: ExportConfig = ExportConfig(),
    val exportState: ExportState = ExportState.Idle,
) {
    val rangeMs: Long get() = (endMs - startMs).coerceAtLeast(0L)
    val canEdit: Boolean get() = videoInfo != null && !isLoading && !loadFailed
    val isExporting: Boolean get() = exportState is ExportState.InProgress
}

class EditorViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = GifExportRepository(application)
    private val _uiState = MutableStateFlow(EditorUiState())
    val uiState: StateFlow<EditorUiState> = _uiState.asStateFlow()

    private val effectsChannel = Channel<EditorEffect>(Channel.BUFFERED)
    val effects = effectsChannel.receiveAsFlow()

    private var loadJob: Job? = null
    private var exportJob: Job? = null
    private var lastUri: Uri? = null

    val speedOptions = listOf(0.25f, 0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f, 3f, 4f)

    fun loadVideo(uri: Uri) {
        lastUri = uri
        loadJob?.cancel()
        exportJob?.cancel()
        _uiState.value = EditorUiState(isLoading = true)
        loadJob = viewModelScope.launch {
            try {
                val info = VideoFrames.loadInfo(getApplication(), uri)
                if (info.durationMs < MIN_RANGE_MS) {
                    _uiState.update {
                        it.copy(isLoading = false, loadFailed = true, videoInfo = null)
                    }
                    return@launch
                }
                _uiState.value = EditorUiState(
                    videoInfo = info,
                    isLoading = false,
                    playheadMs = 0L,
                    startMs = 0L,
                    endMs = minOf(info.durationMs, MAX_RANGE_MS),
                    windowStartMs = 0L,
                    selectedPointer = TrimPointer.Start,
                    cropRatio = CropRatio.Free,
                    cropRect = CropRectN.Full,
                    speed = 1f,
                    exportConfig = ExportConfig(
                        customWidth = 360,
                        customHeight = (360 / info.aspectRatio).toInt().coerceAtLeast(32),
                    ),
                )
                effectsChannel.trySend(EditorEffect.SeekTo(0L))
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Throwable) {
                _uiState.update {
                    it.copy(isLoading = false, loadFailed = true, videoInfo = null)
                }
            }
        }
    }

    fun retryLoad() {
        lastUri?.let(::loadVideo)
    }

    fun togglePlayback() {
        val state = _uiState.value
        if (!state.canEdit || state.isExporting) return
        if (state.isPlaying) {
            _uiState.update { it.copy(isPlaying = false) }
            return
        }
        if (state.playheadMs >= state.endMs - 32L) {
            _uiState.update { it.copy(playheadMs = it.startMs) }
            effectsChannel.trySend(EditorEffect.SeekTo(state.startMs))
        }
        _uiState.update { it.copy(isPlaying = true) }
    }

    fun pause() {
        _uiState.update { it.copy(isPlaying = false) }
    }

    fun onPlayheadPosition(positionMs: Long) {
        if (!_uiState.value.isPlaying) return
        _uiState.update {
            it.copy(playheadMs = positionMs.coerceIn(it.startMs, it.endMs))
        }
    }

    fun loopPlayhead() {
        _uiState.update { it.copy(playheadMs = it.startMs) }
        effectsChannel.trySend(EditorEffect.SeekTo(_uiState.value.startMs))
    }

    fun selectPointer(pointer: TrimPointer) {
        _uiState.update { it.copy(selectedPointer = pointer) }
    }

    fun beginTrimDrag(pointer: TrimPointer) {
        _uiState.update {
            it.copy(selectedPointer = pointer, isPlaying = false)
        }
    }

    fun dragTrimBy(pointer: TrimPointer, deltaMs: Long) {
        val target = when (pointer) {
            TrimPointer.Start -> _uiState.value.startMs + deltaMs
            TrimPointer.End -> _uiState.value.endMs + deltaMs
        }
        updateTrim(pointer, target, seek = false)
    }

    /** Seek once when the gesture ends instead of on every drag frame. */
    fun endTimelineDrag() {
        effectsChannel.trySend(EditorEffect.SeekTo(_uiState.value.startMs))
    }

    fun nudgeSelectedPointer(deltaMs: Long) {
        val state = _uiState.value
        val target = when (state.selectedPointer) {
            TrimPointer.Start -> state.startMs + deltaMs
            TrimPointer.End -> state.endMs + deltaMs
        }
        updateTrim(state.selectedPointer, target)
    }

    private fun updateTrim(
        pointer: TrimPointer,
        requestedMs: Long,
        seek: Boolean = true,
    ) {
        val state = _uiState.value
        val duration = state.videoInfo?.durationMs ?: return
        val windowDuration = minOf(MAX_RANGE_MS, duration)
        val windowStart = state.windowStartMs.coerceIn(0L, (duration - windowDuration).coerceAtLeast(0L))
        val windowEnd = windowStart + windowDuration
        when (pointer) {
            TrimPointer.Start -> {
                val lower = maxOf(0L, state.endMs - MAX_RANGE_MS, windowStart)
                val upper = state.endMs - MIN_RANGE_MS
                val start = requestedMs.coerceIn(lower, upper.coerceAtLeast(lower))
                _uiState.update {
                    it.copy(
                        startMs = start,
                        playheadMs = start,
                        selectedPointer = TrimPointer.Start,
                        isPlaying = false,
                    )
                }
            }

            TrimPointer.End -> {
                val lower = state.startMs + MIN_RANGE_MS
                val upper = minOf(
                    duration,
                    state.startMs + MAX_RANGE_MS,
                    windowEnd,
                )
                val end = requestedMs.coerceIn(lower, upper.coerceAtLeast(lower))
                _uiState.update {
                    it.copy(
                        endMs = end,
                        playheadMs = it.startMs,
                        selectedPointer = TrimPointer.End,
                        isPlaying = false,
                    )
                }
            }
        }
        if (seek) {
            effectsChannel.trySend(EditorEffect.SeekTo(_uiState.value.startMs))
        }
    }

    /**
     * Moves the 60-second timeline window and the selected clip together.
     * The clip length is preserved, so both range handles remain visible while
     * the visible start/end timestamps change.
     */
    fun panTimeline(deltaMs: Long) {
        val state = _uiState.value
        val duration = state.videoInfo?.durationMs ?: return
        val windowDuration = minOf(MAX_RANGE_MS, duration)
        if (duration <= windowDuration) return
        val windowStart = state.windowStartMs
            .coerceIn(0L, (duration - windowDuration).coerceAtLeast(0L))
        val minDelta = maxOf(-state.startMs, -windowStart)
        val maxDelta = minOf(
            duration - state.endMs,
            duration - windowStart - windowDuration,
        )
        if (minDelta > maxDelta) return
        val applied = deltaMs.coerceIn(minDelta, maxDelta)
        if (applied == 0L) return
        val newStart = state.startMs + applied
        _uiState.update {
            it.copy(
                startMs = newStart,
                endMs = it.endMs + applied,
                windowStartMs = windowStart + applied,
                playheadMs = newStart,
                isPlaying = false,
            )
        }
    }

    fun setSpeed(speed: Float) {
        _uiState.update { it.copy(speed = speed.coerceIn(0.25f, 4f)) }
    }

    fun updateCrop(crop: CropRectN) {
        _uiState.update {
            it.copy(
                cropRect = crop.clamped(),
                isPlaying = false,
            )
        }
    }

    fun selectCropRatio(ratio: CropRatio) {
        val state = _uiState.value
        val info = state.videoInfo ?: return
        if (ratio == CropRatio.Free) {
            _uiState.update { it.copy(cropRatio = ratio) }
            return
        }
        val desired = ratio.pixelRatio ?: return
        val normalizedRatio = desired / info.aspectRatio
        val crop = if (normalizedRatio >= 1f) {
            val width = 1f
            val height = (width / normalizedRatio).coerceIn(0.05f, 1f)
            val top = (1f - height) / 2f
            CropRectN(left = 0f, top = top, right = width, bottom = top + height)
        } else {
            val height = 1f
            val width = (height * normalizedRatio).coerceIn(0.05f, 1f)
            val left = (1f - width) / 2f
            CropRectN(left = left, top = 0f, right = left + width, bottom = height)
        }
        _uiState.update { it.copy(cropRatio = ratio, cropRect = crop, isPlaying = false) }
    }

    fun resetCrop() {
        val state = _uiState.value
        if (state.cropRatio == CropRatio.Free) {
            _uiState.update { it.copy(cropRect = CropRectN.Full) }
        } else {
            selectCropRatio(state.cropRatio)
        }
    }

    fun openSheet(sheet: EditorSheet) {
        _uiState.update { it.copy(sheet = sheet, isPlaying = false) }
    }

    fun dismissSheet() {
        if (_uiState.value.isExporting) return
        _uiState.update {
            it.copy(
                sheet = null,
                exportState = if (it.exportState is ExportState.Success ||
                    it.exportState is ExportState.Failed
                ) {
                    ExportState.Idle
                } else {
                    it.exportState
                },
            )
        }
    }

    fun updateExportConfig(config: ExportConfig) {
        _uiState.update { it.copy(exportConfig = config) }
    }

    fun startExport() {
        val state = _uiState.value
        val info = state.videoInfo ?: return
        if (state.isExporting) return
        val config = state.exportConfig
        if (config.resolution == com.fgxyx.cutgif.data.ExportResolution.CUSTOM &&
            !config.customSizeValid
        ) {
            return
        }
        if (state.endMs <= state.startMs) return
        val totalFrames = ((state.rangeMs / 1000.0) * config.frameRate.value)
            .roundToLong().coerceAtLeast(1L).toInt()
        _uiState.update {
            it.copy(
                isPlaying = false,
                exportState = ExportState.InProgress(0, totalFrames, 0),
            )
        }
        val request = ExportRequest(
            info = info,
            startMs = state.startMs,
            endMs = state.endMs,
            crop = state.cropRect,
            config = config,
        )
        exportJob = viewModelScope.launch {
            try {
                val result = repository.export(request) { frame, total, percent ->
                    _uiState.update {
                        if (it.exportState is ExportState.InProgress) {
                            it.copy(exportState = ExportState.InProgress(frame, total, percent))
                        } else {
                            it
                        }
                    }
                }
                _uiState.update { it.copy(exportState = ExportState.Success(result)) }
            } catch (cancelled: CancellationException) {
                _uiState.update { it.copy(exportState = ExportState.Idle) }
                effectsChannel.trySend(EditorEffect.Message(R.string.export_cancelled))
                throw cancelled
            } catch (error: Throwable) {
                _uiState.update {
                    it.copy(exportState = ExportState.Failed(error.message))
                }
            }
        }
    }

    fun cancelExport() {
        exportJob?.cancel()
    }

    fun clearCompletedExport() {
        _uiState.update {
            it.copy(
                exportState = if (it.exportState is ExportState.Success ||
                    it.exportState is ExportState.Failed
                ) {
                    ExportState.Idle
                } else {
                    it.exportState
                },
            )
        }
    }

    override fun onCleared() {
        exportJob?.cancel()
        loadJob?.cancel()
        super.onCleared()
    }

    companion object {
        const val MIN_RANGE_MS = 100L
        const val NUDGE_MS = 100L
        const val MAX_RANGE_MS = 60_000L
    }
}
