package com.fgxyx.cutgif.ui.split

import android.app.Application
import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.fgxyx.cutgif.data.GifFrameRenderer
import com.fgxyx.cutgif.data.GifMetadata
import com.fgxyx.cutgif.data.GifMetadataReader
import com.fgxyx.cutgif.data.GifSplitExporter
import com.fgxyx.cutgif.data.GifSplitResult
import com.fgxyx.cutgif.data.GifSplitScale
import com.fgxyx.cutgif.data.ColorQuality
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

enum class GifFramePointer { Start, End }

sealed interface GifSplitExportState {
    data object Idle : GifSplitExportState
    data class InProgress(
        val done: Int,
        val total: Int,
        val percent: Int,
    ) : GifSplitExportState

    data class Success(val result: GifSplitResult) : GifSplitExportState
    data class Failed(val message: String?) : GifSplitExportState
}

data class GifSplitUiState(
    val isLoading: Boolean = true,
    val loadFailed: Boolean = false,
    val errorMessage: String? = null,
    val metadata: GifMetadata? = null,
    val startFrame: Int = 0,
    val endFrame: Int = 0,
    val playheadFrame: Int = 0,
    val selectedPointer: GifFramePointer = GifFramePointer.Start,
    val previewBitmap: ImageBitmap? = null,
    val thumbnails: List<ImageBitmap?> = emptyList(),
    val isPlaying: Boolean = false,
    val quality: ColorQuality = ColorQuality.HIGH,
    val scale: GifSplitScale = GifSplitScale.FULL,
    val exportState: GifSplitExportState = GifSplitExportState.Idle,
) {
    val canEdit: Boolean
        get() = metadata != null && exportState !is GifSplitExportState.InProgress
    val isExporting: Boolean
        get() = exportState is GifSplitExportState.InProgress
}

class GifSplitViewModel(application: Application) : AndroidViewModel(application) {

    private val context = application.applicationContext
    private var sourceFile: File? = null
    private var renderer: GifFrameRenderer? = null
    private val renderMutex = Mutex()
    private var previewJob: Job? = null
    private var playJob: Job? = null
    private var exportJob: Job? = null
    private var thumbnailJob: Job? = null
    private var previewGeneration = 0

    private val _uiState = kotlinx.coroutines.flow.MutableStateFlow(GifSplitUiState())
    val uiState: kotlinx.coroutines.flow.StateFlow<GifSplitUiState> = _uiState

    fun loadGif(uri: Uri) {
        playJob?.cancel()
        previewJob?.cancel()
        exportJob?.cancel()
        thumbnailJob?.cancel()
        sourceFile?.delete()
        sourceFile = null
        renderer = null
        _uiState.value = GifSplitUiState(isLoading = true)
        viewModelScope.launch {
            try {
                val file = withContext(Dispatchers.IO) {
                    val target = File(
                        context.cacheDir,
                        "gif_split_${System.currentTimeMillis()}.gif",
                    )
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        target.outputStream().use { output -> input.copyTo(output) }
                    } ?: error("閺冪姵纭堕幍鎾崇磻 GIF")
                    target
                }
                sourceFile = file
                val metadata = withContext(Dispatchers.IO) { GifMetadataReader.read(file) }
                val frameRenderer = withContext(Dispatchers.IO) {
                    GifFrameRenderer(file, metadata)
                }
                renderer = frameRenderer
                val last = metadata.frameCount - 1
                _uiState.value = GifSplitUiState(
                    isLoading = false,
                    metadata = metadata,
                    startFrame = 0,
                    endFrame = last,
                    playheadFrame = 0,
                    selectedPointer = GifFramePointer.Start,
                    thumbnails = List((metadata.frameCount + 9) / 10) { null },
                )
                renderPreview(0)
                loadThumbnails(metadata, frameRenderer)
            } catch (error: Throwable) {
                sourceFile?.delete()
                sourceFile = null
                renderer = null
                _uiState.value = GifSplitUiState(
                    isLoading = false,
                    loadFailed = true,
                    errorMessage = error.message,
                )
            }
        }
    }

    fun retryLoad() {
        // The screen returns to the home picker on failure; kept as a no-op hook
        // for symmetry with the video editor.
    }

    private fun loadThumbnails(metadata: GifMetadata, frameRenderer: GifFrameRenderer) {
        thumbnailJob?.cancel()
        thumbnailJob = viewModelScope.launch {
            val count = (metadata.frameCount + 9) / 10
            val loaded = MutableList<ImageBitmap?>(count) { null }
            for (index in 0 until count) {
                ensureActive()
                val frame = (index * 10).coerceAtMost(metadata.frameCount - 1)
                val bitmap = withContext(Dispatchers.IO) {
                    renderMutex.withLock {
                        frameRenderer.renderFrame(
                            index = frame,
                            targetWidth = 180,
                            targetHeight = 180,
                            centerCrop = true,
                            opaqueBackground = false,
                        )
                    }
                }
                loaded[index] = bitmap.asImageBitmap()
                // Batch the state updates to keep scrolling smooth while loading.
                if (index == count - 1 || index % 5 == 4) {
                    _uiState.value = _uiState.value.copy(thumbnails = loaded.toList())
                }
            }
        }
    }

    fun seekToFrame(frame: Int) {
        val state = _uiState.value
        val metadata = state.metadata ?: return
        pause()
        val target = frame.coerceIn(0, metadata.frameCount - 1)
        _uiState.value = state.copy(playheadFrame = target, isPlaying = false)
        renderPreview(target)
    }

    fun selectPointer(pointer: GifFramePointer) {
        _uiState.value = _uiState.value.copy(selectedPointer = pointer)
    }

    fun dragFramePointer(pointer: GifFramePointer, deltaFrames: Int) {
        if (deltaFrames == 0) return
        val state = _uiState.value
        val metadata = state.metadata ?: return
        pause()
        when (pointer) {
            GifFramePointer.Start -> {
                val start = (state.startFrame + deltaFrames)
                    .coerceIn(0, (state.endFrame - 1).coerceAtLeast(0))
                _uiState.value = state.copy(
                    startFrame = start,
                    playheadFrame = start,
                    selectedPointer = pointer,
                    isPlaying = false,
                )
                renderPreview(start)
            }
            GifFramePointer.End -> {
                val end = (state.endFrame + deltaFrames)
                    .coerceIn(
                        (state.startFrame + 1).coerceAtMost(metadata.frameCount - 1),
                        metadata.frameCount - 1,
                    )
                _uiState.value = state.copy(
                    endFrame = end,
                    playheadFrame = end,
                    selectedPointer = pointer,
                    isPlaying = false,
                )
                renderPreview(end)
            }
        }
    }

    fun nudgeSelectedPointer(delta: Int) {
        val pointer = _uiState.value.selectedPointer
        dragFramePointer(pointer, delta)
    }

    fun togglePlayback() {
        val state = _uiState.value
        val metadata = state.metadata ?: return
        if (state.isPlaying) {
            pause()
            return
        }
        _uiState.value = state.copy(isPlaying = true)
        playJob?.cancel()
        playJob = viewModelScope.launch {
            while (isActive) {
                val current = _uiState.value
                if (!current.isPlaying) break
                val frame = current.playheadFrame.coerceIn(0, metadata.frameCount - 1)
                val delayMs = metadata.frameDelaysMs[frame].coerceIn(20, 1_000)
                delay(delayMs.toLong())
                if (!_uiState.value.isPlaying) break
                val next = if (frame >= current.endFrame) {
                    current.startFrame
                } else {
                    (frame + 1).coerceAtMost(current.endFrame)
                }
                _uiState.value = _uiState.value.copy(playheadFrame = next)
                renderPreview(next)
            }
        }
    }

    fun pause() {
        playJob?.cancel()
        playJob = null
        if (_uiState.value.isPlaying) {
            _uiState.value = _uiState.value.copy(isPlaying = false)
        }
    }

    private fun renderPreview(frame: Int) {
        val state = _uiState.value
        val metadata = state.metadata ?: return
        val frameRenderer = renderer ?: return
        previewGeneration++
        val generation = previewGeneration
        previewJob?.cancel()
        previewJob = viewModelScope.launch {
            val scale = min(1f, 720f / max(metadata.width, metadata.height))
            val targetWidth = max(1, (metadata.width * scale).roundToInt())
            val targetHeight = max(1, (metadata.height * scale).roundToInt())
            val bitmap = withContext(Dispatchers.IO) {
                renderMutex.withLock {
                    frameRenderer.renderFrame(
                        index = frame,
                        targetWidth = targetWidth,
                        targetHeight = targetHeight,
                        centerCrop = false,
                        opaqueBackground = false,
                    )
                }
            }
            if (generation == previewGeneration) {
                _uiState.value = _uiState.value.copy(
                    previewBitmap = bitmap.asImageBitmap(),
                    playheadFrame = frame.coerceIn(0, metadata.frameCount - 1),
                )
            } else {
                bitmap.recycle()
            }
        }
    }

    fun setQuality(quality: ColorQuality) {
        _uiState.value = _uiState.value.copy(quality = quality)
    }

    fun setScale(scale: GifSplitScale) {
        _uiState.value = _uiState.value.copy(scale = scale)
    }

    fun startExport() {
        val state = _uiState.value
        val metadata = state.metadata ?: return
        val file = sourceFile ?: return
        if (state.isExporting) return
        pause()
        _uiState.value = state.copy(exportState = GifSplitExportState.InProgress(0, 1, 0))
        exportJob = viewModelScope.launch {
            try {
                val result = GifSplitExporter.exportGif(
                    context = context,
                    sourceFile = file,
                    metadata = metadata,
                    startFrame = state.startFrame,
                    endFrame = state.endFrame,
                    scale = state.scale,
                    quality = state.quality,
                ) { done, total, percent ->
                    _uiState.value = _uiState.value.copy(
                        exportState = GifSplitExportState.InProgress(done, total, percent),
                    )
                }
                _uiState.value = _uiState.value.copy(
                    exportState = GifSplitExportState.Success(result),
                )
            } catch (error: Throwable) {
                if (error is kotlinx.coroutines.CancellationException) {
                    _uiState.value = _uiState.value.copy(
                        exportState = GifSplitExportState.Idle,
                    )
                } else {
                    _uiState.value = _uiState.value.copy(
                        exportState = GifSplitExportState.Failed(error.message),
                    )
                }
            }
        }
    }

    fun cancelExport() {
        exportJob?.cancel()
        exportJob = null
        _uiState.value = _uiState.value.copy(exportState = GifSplitExportState.Idle)
    }

    fun dismissExportState() {
        _uiState.value = _uiState.value.copy(exportState = GifSplitExportState.Idle)
    }

    override fun onCleared() {
        playJob?.cancel()
        previewJob?.cancel()
        exportJob?.cancel()
        thumbnailJob?.cancel()
        sourceFile?.delete()
        sourceFile = null
        renderer = null
        super.onCleared()
    }
}
