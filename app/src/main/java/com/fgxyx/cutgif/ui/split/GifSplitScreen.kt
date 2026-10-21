package com.fgxyx.cutgif.ui.split

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fgxyx.cutgif.R
import com.fgxyx.cutgif.data.GifMetadata
import com.fgxyx.cutgif.data.GifShare
import com.fgxyx.cutgif.ui.theme.CutGifDimens
import com.fgxyx.cutgif.ui.theme.CutGifShape
import kotlinx.coroutines.launch
import kotlin.math.max
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GifSplitScreen(
    viewModel: GifSplitViewModel,
    onExit: () -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var showExportSheet by rememberSaveable { mutableStateOf(false) }

    BackHandler(enabled = !state.isExporting) { onExit() }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.split_title),
                        style = MaterialTheme.typography.titleLarge,
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = onExit,
                        enabled = !state.isExporting,
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = stringResource(R.string.close),
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = { showExportSheet = true },
                        enabled = state.canEdit,
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Download,
                            contentDescription = stringResource(R.string.export),
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                    navigationIconContentColor = MaterialTheme.colorScheme.onSurface,
                    actionIconContentColor = MaterialTheme.colorScheme.onSurface,
                ),
            )
        },
        contentWindowInsets = WindowInsets.safeDrawing.only(
            WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom,
        ),
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            when {
                state.isLoading -> LoadingState()
                state.loadFailed || state.metadata == null -> LoadFailedState(onBack = onExit)
                else -> GifSplitContent(
                    state = state,
                    onSelectPointer = viewModel::selectPointer,
                    onDragPointer = viewModel::dragFramePointer,
                    onNudge = viewModel::nudgeSelectedPointer,
                    onTogglePlayback = viewModel::togglePlayback,
                    onSeek = viewModel::seekToFrame,
                    onExport = { showExportSheet = true },
                )
            }
        }
    }

    if (showExportSheet && state.metadata != null) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = {
                if (!state.isExporting) {
                    showExportSheet = false
                    viewModel.dismissExportState()
                }
            },
            sheetState = sheetState,
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            shape = CutGifShape.Sheet,
        ) {
            GifSplitExportSheet(
                state = state,
                onQuality = viewModel::setQuality,
                onScale = viewModel::setScale,
                onStartExport = viewModel::startExport,
                onCancelExport = viewModel::cancelExport,
                onDone = {
                    showExportSheet = false
                    viewModel.dismissExportState()
                },
                onShare = { uri ->
                    try {
                        GifShare.share(context, uri)
                    } catch (_: Throwable) {
                        Toast.makeText(
                            context,
                            context.getString(R.string.share_failed),
                            Toast.LENGTH_SHORT,
                        ).show()
                    }
                },
            )
        }
    }
}

@Composable
private fun GifSplitContent(
    state: GifSplitUiState,
    onSelectPointer: (GifFramePointer) -> Unit,
    onDragPointer: (GifFramePointer, Int) -> Unit,
    onNudge: (Int) -> Unit,
    onTogglePlayback: () -> Unit,
    onSeek: (Int) -> Unit,
    onExport: () -> Unit,
) {
    val metadata = state.metadata ?: return
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = CutGifDimens.Space24)
            .padding(top = CutGifDimens.Space8, bottom = CutGifDimens.Space24),
    ) {
        GifPreviewCard(
            state = state,
            onTogglePlayback = onTogglePlayback,
        )
        Spacer(Modifier.height(CutGifDimens.Space24))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Text(
                    text = stringResource(R.string.split_frame_axis),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = stringResource(R.string.split_frame_hint),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.weight(1f))
            Text(
                text = stringResource(
                    R.string.split_selected_range,
                    state.endFrame - state.startFrame + 1,
                    metadata.frameCount,
                ),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(CutGifDimens.Space8))
        GifFrameStrip(
            metadata = metadata,
            thumbnails = state.thumbnails,
            startFrame = state.startFrame,
            endFrame = state.endFrame,
            playheadFrame = state.playheadFrame,
            selectedPointer = state.selectedPointer,
            onSelectPointer = onSelectPointer,
            onDragPointer = onDragPointer,
            onSeek = onSeek,
        )
        Spacer(Modifier.height(CutGifDimens.Space8))
        GifRangeOverview(
            frameCount = metadata.frameCount,
            startFrame = state.startFrame,
            endFrame = state.endFrame,
            playheadFrame = state.playheadFrame,
        )
        Spacer(Modifier.height(CutGifDimens.Space16))
        FrameControls(
            state = state,
            onSelectPointer = onSelectPointer,
            onNudge = onNudge,
            onTogglePlayback = onTogglePlayback,
        )
        Spacer(Modifier.height(CutGifDimens.Space16))
        Button(
            onClick = onExport,
            modifier = Modifier
                .fillMaxWidth()
                .height(CutGifDimens.ActionButtonHeight),
            shape = CutGifShape.Button,
        ) {
            Text(
                text = stringResource(
                    R.string.split_export_button,
                    state.endFrame - state.startFrame + 1,
                ),
                style = MaterialTheme.typography.labelLarge,
            )        }
    }
}

@Composable
private fun GifPreviewCard(
    state: GifSplitUiState,
    onTogglePlayback: () -> Unit,
) {
    val metadata = state.metadata ?: return
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(300.dp),
        shape = CutGifShape.Card,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Box(Modifier.fillMaxSize()) {
            val preview = state.previewBitmap
            if (preview != null) {
                Image(
                    bitmap = preview,
                    contentDescription = stringResource(R.string.split_preview),
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(CutGifDimens.Space12)
                        .clip(RoundedCornerShape(CutGifDimens.Space12)),
                    contentScale = ContentScale.Fit,
                )
            } else {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            }
            Surface(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(CutGifDimens.Space12),
                shape = CutGifShape.Button,
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
            ) {
                Text(
                    text = "GIF",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(
                        horizontal = CutGifDimens.Space12,
                        vertical = CutGifDimens.Space4,
                    ),
                )
            }
            Row(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .padding(CutGifDimens.Space12),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Surface(
                    shape = CutGifShape.Button,
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
                ) {
                    Text(
                        text = stringResource(
                            R.string.split_current_frame,
                            state.playheadFrame + 1,
                            metadata.frameCount,
                        ),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(
                            horizontal = CutGifDimens.Space12,
                            vertical = CutGifDimens.Space8,
                        ),
                    )
                }
                Spacer(Modifier.weight(1f))
                Surface(
                    onClick = onTogglePlayback,
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(CutGifDimens.MicroButtonSize),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = if (state.isPlaying) {
                                Icons.Rounded.Pause
                            } else {
                                Icons.Rounded.PlayArrow
                            },
                            contentDescription = stringResource(
                                if (state.isPlaying) R.string.pause else R.string.play,
                            ),
                            modifier = Modifier.size(CutGifDimens.IconCompact),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun GifFrameStrip(
    metadata: GifMetadata,
    thumbnails: List<androidx.compose.ui.graphics.ImageBitmap?>,
    startFrame: Int,
    endFrame: Int,
    playheadFrame: Int,
    selectedPointer: GifFramePointer,
    onSelectPointer: (GifFramePointer) -> Unit,
    onDragPointer: (GifFramePointer, Int) -> Unit,
    onSeek: (Int) -> Unit,
) {
    val density = LocalDensity.current
    val scrollState = rememberScrollState()
    val maxFrame = (metadata.frameCount - 1).coerceAtLeast(1)
    val scrimColor = MaterialTheme.colorScheme.scrim
    val primaryColor = MaterialTheme.colorScheme.primary
    val tertiaryColor = MaterialTheme.colorScheme.tertiary

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .height(108.dp)
            .clip(CutGifShape.Filmstrip)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        val cellWidth = 64.dp
        val gap = CutGifDimens.ConnectedGap
        val cellCount = thumbnails.size.coerceAtLeast(1)
        val naturalContentWidth = cellWidth * cellCount + gap * (cellCount - 1)
        val contentWidth = maxOf(naturalContentWidth, maxWidth)
        val hasExtraSpace = contentWidth > naturalContentWidth
        val viewportWidthPx = with(density) { maxWidth.toPx() }
        val contentWidthPx = with(density) { contentWidth.toPx() }
        val startFraction = startFrame.toFloat() / maxFrame
        val endFraction = endFrame.toFloat() / maxFrame
        val playheadFraction = playheadFrame.toFloat() / maxFrame
        val handleTouchPx = with(density) { CutGifDimens.CropHandleTouchSize.toPx() }

        LaunchedEffect(selectedPointer) {
            val target = if (selectedPointer == GifFramePointer.Start) {
                startFraction
            } else {
                endFraction
            }
            val targetX = target * contentWidthPx
            val maxScroll = (contentWidthPx - viewportWidthPx).coerceAtLeast(0f)
            scrollState.animateScrollTo(
                (targetX - viewportWidthPx / 2f).roundToInt()
                    .coerceIn(0, maxScroll.roundToInt()),
            )
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .horizontalScroll(scrollState),
        ) {
            Box(
                modifier = Modifier
                    .width(contentWidth)
                    .fillMaxHeight(),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(top = CutGifDimens.Space4),
                    horizontalArrangement = if (hasExtraSpace) {
                        Arrangement.SpaceBetween
                    } else {
                        Arrangement.spacedBy(gap)
                    },
                ) {
                    thumbnails.forEachIndexed { index, image ->
                        Column(
                            modifier = Modifier
                                .width(cellWidth)
                                .clickable { onSeek(index * 10) },
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(cellWidth)
                                    .clip(RoundedCornerShape(CutGifDimens.Space8))
                                    .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                                contentAlignment = Alignment.Center,
                            ) {
                                if (image != null) {
                                    Image(
                                        bitmap = image,
                                        contentDescription = null,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize(),
                                    )
                                } else {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(CutGifDimens.Space16),
                                        strokeWidth = 2.dp,
                                    )
                                }
                            }
                            Text(
                                text = stringResource(
                                    R.string.split_frame_unit,
                                    index * 10 + 1,
                                ),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }

                Canvas(Modifier.fillMaxSize()) {
                    val startX = startFraction * size.width
                    val endX = endFraction * size.width
                    val scrim = scrimColor.copy(alpha = 0.34f)
                    drawRect(
                        color = scrim,
                        size = Size(startX.coerceAtLeast(0f), size.height),
                    )
                    drawRect(
                        color = scrim,
                        topLeft = Offset(endX, 0f),
                        size = Size((size.width - endX).coerceAtLeast(0f), size.height),
                    )
                    drawRect(
                        color = primaryColor.copy(alpha = 0.1f),
                        topLeft = Offset(startX, 0f),
                        size = Size((endX - startX).coerceAtLeast(0f), size.height),
                    )
                    drawLine(
                        color = primaryColor,
                        start = Offset(startX, 0f),
                        end = Offset(startX, size.height),
                        strokeWidth = CutGifDimens.Stroke.toPx(),
                    )
                    drawLine(
                        color = primaryColor,
                        start = Offset(endX, 0f),
                        end = Offset(endX, size.height),
                        strokeWidth = CutGifDimens.Stroke.toPx(),
                    )
                    drawLine(
                        color = tertiaryColor,
                        start = Offset(playheadFraction * size.width, 0f),
                        end = Offset(playheadFraction * size.width, size.height),
                        strokeWidth = CutGifDimens.PlayheadWidth.toPx(),
                    )
                }

                FrameHandle(
                    fraction = startFraction,
                    contentWidthPx = contentWidthPx,
                    touchWidthPx = handleTouchPx,
                    pointer = GifFramePointer.Start,
                    frameCount = metadata.frameCount,
                    onSelectPointer = onSelectPointer,
                    onDragPointer = onDragPointer,
                )
                FrameHandle(
                    fraction = endFraction,
                    contentWidthPx = contentWidthPx,
                    touchWidthPx = handleTouchPx,
                    pointer = GifFramePointer.End,
                    frameCount = metadata.frameCount,
                    onSelectPointer = onSelectPointer,
                    onDragPointer = onDragPointer,
                )
            }
        }
    }
}

@Composable
private fun FrameHandle(
    fraction: Float,
    contentWidthPx: Float,
    touchWidthPx: Float,
    pointer: GifFramePointer,
    frameCount: Int,
    onSelectPointer: (GifFramePointer) -> Unit,
    onDragPointer: (GifFramePointer, Int) -> Unit,
) {
    val density = LocalDensity.current
    val touchWidth = with(density) { touchWidthPx.toDp() }
    val handleWidth = CutGifDimens.TrimHandleWidth
    Box(
        modifier = Modifier
            .offset {
                IntOffset(
                    (fraction * contentWidthPx - touchWidthPx / 2f).roundToInt(),
                    0,
                )
            }
            .width(touchWidth)
            .fillMaxHeight()
            .pointerInput(pointer, frameCount, contentWidthPx) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    onSelectPointer(pointer)
                    down.consume()
                    var lastX = down.position.x
                    var accumulated = 0f
                    val maxFrame = (frameCount - 1).coerceAtLeast(1)
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (!change.pressed) break
                        val deltaX = change.position.x - lastX
                        lastX = change.position.x
                        accumulated += deltaX / contentWidthPx * maxFrame
                        val wholeFrames = accumulated.toInt()
                        if (wholeFrames != 0) {
                            accumulated -= wholeFrames
                            onDragPointer(pointer, wholeFrames)
                        }
                        change.consume()
                    }
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .width(handleWidth)
                .fillMaxHeight()
                .clip(RoundedCornerShape(handleWidth / 2))
                .background(MaterialTheme.colorScheme.primary),
        )
    }
}

@Composable
private fun GifRangeOverview(
    frameCount: Int,
    startFrame: Int,
    endFrame: Int,
    playheadFrame: Int,
) {
    val maxFrame = (frameCount - 1).coerceAtLeast(1)
    val primaryColor = MaterialTheme.colorScheme.primary
    val tertiaryColor = MaterialTheme.colorScheme.tertiary
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(8.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHighest),
    ) {
        val startX = startFrame.toFloat() / maxFrame * size.width
        val endX = endFrame.toFloat() / maxFrame * size.width
        drawRect(
            color = primaryColor.copy(alpha = 0.25f),
            topLeft = Offset(startX, 0f),
            size = Size((endX - startX).coerceAtLeast(0f), size.height),
        )
        drawLine(
            color = primaryColor,
            start = Offset(startX, 0f),
            end = Offset(startX, size.height),
            strokeWidth = CutGifDimens.Stroke.toPx(),
        )
        drawLine(
            color = primaryColor,
            start = Offset(endX, 0f),
            end = Offset(endX, size.height),
            strokeWidth = CutGifDimens.Stroke.toPx(),
        )
        drawLine(
            color = tertiaryColor,
            start = Offset(playheadFrame.toFloat() / maxFrame * size.width, 0f),
            end = Offset(playheadFrame.toFloat() / maxFrame * size.width, size.height),
            strokeWidth = CutGifDimens.Stroke.toPx(),
        )
    }
}

@Composable
private fun FrameControls(
    state: GifSplitUiState,
    onSelectPointer: (GifFramePointer) -> Unit,
    onNudge: (Int) -> Unit,
    onTogglePlayback: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = { onNudge(-1) }) {
            Icon(
                imageVector = Icons.Rounded.ChevronLeft,
                contentDescription = stringResource(R.string.split_nudge_back),
            )
        }
        FrameChip(
            label = stringResource(R.string.split_start_frame, state.startFrame + 1),
            selected = state.selectedPointer == GifFramePointer.Start,
            onClick = { onSelectPointer(GifFramePointer.Start) },
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = onTogglePlayback) {
            Icon(
                imageVector = if (state.isPlaying) {
                    Icons.Rounded.Pause
                } else {
                    Icons.Rounded.PlayArrow
                },
                contentDescription = stringResource(
                    if (state.isPlaying) R.string.pause else R.string.play,
                ),
            )
        }
        FrameChip(
            label = stringResource(R.string.split_end_frame, state.endFrame + 1),
            selected = state.selectedPointer == GifFramePointer.End,
            onClick = { onSelectPointer(GifFramePointer.End) },
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = { onNudge(1) }) {
            Icon(
                imageVector = Icons.Rounded.ChevronRight,
                contentDescription = stringResource(R.string.split_nudge_forward),
            )
        }
    }
}

@Composable
private fun FrameChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        modifier = modifier
            .padding(horizontal = CutGifDimens.Space4)
            .height(CutGifDimens.MicroButtonSize),
        shape = CutGifShape.Button,
        color = if (selected) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceContainerHigh
        },
        contentColor = if (selected) {
            MaterialTheme.colorScheme.onPrimaryContainer
        } else {
            MaterialTheme.colorScheme.onSurface
        },
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun LoadingState() {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        CircularProgressIndicator()
        Spacer(Modifier.height(CutGifDimens.Space16))
        Text(
            text = stringResource(R.string.split_loading),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun LoadFailedState(onBack: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(CutGifDimens.Space24),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = stringResource(R.string.split_load_failed),
            style = MaterialTheme.typography.titleMedium,
        )
        Spacer(Modifier.height(CutGifDimens.Space8))
        Text(
            text = stringResource(R.string.split_load_failed_hint),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(CutGifDimens.Space24))
        Button(onClick = onBack, shape = CutGifShape.Button) {
            Text(text = stringResource(R.string.back_home))
        }
    }
}
