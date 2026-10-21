package com.fgxyx.cutgif.ui.editor

import android.os.Build
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector
import com.fgxyx.cutgif.R
import com.fgxyx.cutgif.data.GifShare
import com.fgxyx.cutgif.ui.theme.CutGifDimens
import com.fgxyx.cutgif.ui.theme.CutGifShape
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

@androidx.annotation.OptIn(UnstableApi::class)
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorScreen(
    viewModel: EditorViewModel,
    onExit: () -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    var showDiscardDialog by rememberSaveable { mutableStateOf(false) }
    var previewDecodeFailed by remember { mutableStateOf(false) }
    val info = state.videoInfo
    val latestStartMs by rememberUpdatedState(state.startMs)
    val latestEndMs by rememberUpdatedState(state.endMs)

    val player = remember {
        val isEmulator = Build.HARDWARE.contains("ranchu", ignoreCase = true) ||
            Build.PRODUCT.contains("sdk_gphone", ignoreCase = true)
        val renderersFactory = DefaultRenderersFactory(context)
            .setEnableDecoderFallback(true)
            .setMediaCodecSelector(
                if (isEmulator) MediaCodecSelector.PREFER_SOFTWARE else MediaCodecSelector.DEFAULT,
            )
        ExoPlayer.Builder(context, renderersFactory).build().apply {
            volume = 0f
            repeatMode = ExoPlayer.REPEAT_MODE_OFF
        }
    }

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) {
                previewDecodeFailed = true
            }
        }
        player.addListener(listener)
        onDispose {
            player.removeListener(listener)
            player.pause()
            player.release()
        }
    }

    LifecycleEventEffect(Lifecycle.Event.ON_STOP) {
        viewModel.pause()
    }

    LaunchedEffect(info?.uri) {
        previewDecodeFailed = false
        if (info == null) {
            player.stop()
            player.clearMediaItems()
        } else {
            player.setMediaItem(MediaItem.fromUri(info.uri))
            player.prepare()
            player.playWhenReady = false
            player.seekTo(state.startMs)
        }
    }

    LaunchedEffect(state.isPlaying, info?.uri) {
        if (info == null) return@LaunchedEffect
        if (state.isPlaying) player.play() else player.pause()
    }

    LaunchedEffect(state.speed) {
        player.setPlaybackSpeed(state.speed.coerceIn(0.25f, 4f))
    }

    LaunchedEffect(Unit) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is EditorEffect.SeekTo -> player.seekTo(effect.positionMs)
                is EditorEffect.Message -> snackbarHostState.showSnackbar(
                    context.getString(effect.messageRes),
                )
            }
        }
    }

    LaunchedEffect(state.isPlaying, info?.uri) {
        if (!state.isPlaying || info == null) return@LaunchedEffect
        while (isActive) {
            val position = player.currentPosition
            if (position >= latestEndMs - 8L) {
                player.seekTo(latestStartMs)
                player.play()
                viewModel.onPlayheadPosition(latestStartMs)
            } else {
                viewModel.onPlayheadPosition(position)
            }
            delay(32L)
        }
    }

    fun requestExit() {
        if (state.isExporting) return
        viewModel.pause()
        if (state.videoInfo == null) {
            onExit()
        } else {
            showDiscardDialog = true
        }
    }

    fun shareGif(uri: android.net.Uri) {
        try {
            GifShare.share(context, uri)
        } catch (_: Throwable) {
            Toast.makeText(
                context,
                context.getString(R.string.share_failed),
                Toast.LENGTH_SHORT,
            ).show()
        }
    }

    BackHandler { requestExit() }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.editor_title),
                        style = MaterialTheme.typography.titleLarge,
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = ::requestExit,
                        enabled = !state.isExporting,
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = stringResource(R.string.close_editor),
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = { viewModel.openSheet(EditorSheet.Export) },
                        enabled = state.canEdit && !state.isExporting,
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
        snackbarHost = { SnackbarHost(snackbarHostState) },
        contentWindowInsets = WindowInsets.safeDrawing.only(
            WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom,
        ),
    ) { contentPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding),
        ) {
            when {
                state.isLoading -> LoadingState()
                state.loadFailed || (info == null) -> LoadFailedState(
                    onRetry = viewModel::retryLoad,
                    onBack = onExit,
                    showRetry = true,
                )
                else -> EditorContent(
                    state = state,
                    player = player,
                    previewDecodeFailed = previewDecodeFailed,
                    onCropChange = viewModel::updateCrop,
                    onResetCrop = viewModel::resetCrop,
                    onTrimDragStart = viewModel::beginTrimDrag,
                    onTrimDragBy = viewModel::dragTrimBy,
                    onPanBy = viewModel::panTimeline,
                    onTimelineDragEnd = viewModel::endTimelineDrag,
                    onSelectPointer = viewModel::selectPointer,
                    onNudge = viewModel::nudgeSelectedPointer,
                    onTogglePlayback = viewModel::togglePlayback,
                    onOpenAspect = { viewModel.openSheet(EditorSheet.Aspect) },
                    onOpenSpeed = { viewModel.openSheet(EditorSheet.Speed) },
                )
            }
        }
    }

    if (showDiscardDialog) {
        AlertDialog(
            onDismissRequest = { showDiscardDialog = false },
            shape = CutGifShape.Dialog,
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            title = {
                Text(
                    text = stringResource(R.string.discard_title),
                    style = MaterialTheme.typography.headlineSmall,
                )
            },
            text = {
                Text(
                    text = stringResource(R.string.discard_message),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDiscardDialog = false
                        viewModel.pause()
                        onExit()
                    },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error,
                    ),
                ) {
                    Text(stringResource(R.string.discard_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDiscardDialog = false }) {
                    Text(stringResource(R.string.discard_cancel))
                }
            },
        )
    }

    val sheet = state.sheet
    if (sheet != null) {
        when (sheet) {
            EditorSheet.Aspect -> AspectSheet(
                current = state.cropRatio,
                onSelect = viewModel::selectCropRatio,
                onDismiss = viewModel::dismissSheet,
            )
            EditorSheet.Speed -> SpeedSheet(
                speed = state.speed,
                options = viewModel.speedOptions,
                onSpeedChange = viewModel::setSpeed,
                onDismiss = viewModel::dismissSheet,
            )
            EditorSheet.Export -> ExportSheet(
                info = info ?: return,
                crop = state.cropRect,
                startMs = state.startMs,
                endMs = state.endMs,
                config = state.exportConfig,
                exportState = state.exportState,
                onConfigChange = viewModel::updateExportConfig,
                onStartExport = viewModel::startExport,
                onCancelExport = viewModel::cancelExport,
                onDismiss = viewModel::dismissSheet,
                onShare = ::shareGif,
            )
        }
    }
}

@Composable
private fun EditorContent(
    state: EditorUiState,
    player: ExoPlayer,
    previewDecodeFailed: Boolean,
    onCropChange: (com.fgxyx.cutgif.data.CropRectN) -> Unit,
    onResetCrop: () -> Unit,
    onTrimDragStart: (TrimPointer) -> Unit,
    onTrimDragBy: (TrimPointer, Long) -> Unit,
    onPanBy: (Long) -> Unit,
    onTimelineDragEnd: () -> Unit,
    onSelectPointer: (TrimPointer) -> Unit,
    onNudge: (Long) -> Unit,
    onTogglePlayback: () -> Unit,
    onOpenAspect: () -> Unit,
    onOpenSpeed: () -> Unit,
) {
    val info = state.videoInfo ?: return
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = CutGifDimens.Space24),
    ) {
        Spacer(Modifier.height(CutGifDimens.Space8))
        VideoPreviewArea(
            info = info,
            player = player,
            crop = state.cropRect,
            ratio = state.cropRatio,
            playheadMs = state.playheadMs,
            onCropChange = onCropChange,
            onResetCrop = onResetCrop,
            previewDecodeFailed = previewDecodeFailed,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .heightIn(min = CutGifDimens.PreviewMinHeight),
        )
        Spacer(Modifier.height(CutGifDimens.Space8))
        FilmstripSection(
            info = info,
            startMs = state.startMs,
            endMs = state.endMs,
            windowStartMs = state.windowStartMs,
            playheadMs = state.playheadMs,
            onTrimDragStart = onTrimDragStart,
            onTrimDragBy = onTrimDragBy,
            onPanBy = onPanBy,
            onDragEnd = onTimelineDragEnd,
        )
        Spacer(Modifier.height(CutGifDimens.Space8))
        TrimMicroAdjustRow(
            startMs = state.startMs,
            endMs = state.endMs,
            playheadMs = state.playheadMs,
            isPlaying = state.isPlaying,
            selectedPointer = state.selectedPointer,
            onSelectPointer = onSelectPointer,
            onNudge = onNudge,
            onTogglePlayback = onTogglePlayback,
        )
        Spacer(Modifier.height(CutGifDimens.Space8))
        EditorToolbar(
            isPlaying = state.isPlaying,
            speed = state.speed,
            enabled = state.canEdit,
            onOpenAspect = onOpenAspect,
            onTogglePlayback = onTogglePlayback,
            onOpenSpeed = onOpenSpeed,
        )
        Spacer(Modifier.height(CutGifDimens.Space8))
    }
}

@Composable
private fun LoadingState() {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(CutGifDimens.Space16))
        Text(
            text = stringResource(R.string.loading_video),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun LoadFailedState(
    onRetry: () -> Unit,
    onBack: () -> Unit,
    showRetry: Boolean,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(CutGifDimens.Space24),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = Icons.Rounded.ErrorOutline,
            contentDescription = null,
            modifier = Modifier.size(CutGifDimens.Space48),
            tint = MaterialTheme.colorScheme.error,
        )
        Spacer(Modifier.height(CutGifDimens.Space16))
        Text(
            text = stringResource(R.string.video_load_failed),
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(CutGifDimens.Space8))
        Text(
            text = stringResource(R.string.video_load_failed_hint),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(CutGifDimens.Space24))
        Button(
            onClick = onBack,
            shape = CutGifShape.Button,
            modifier = Modifier.height(CutGifDimens.ActionButtonHeight),
        ) {
            Text(
                text = stringResource(R.string.back_home),
                style = MaterialTheme.typography.labelLarge,
            )
        }
        if (showRetry) {
            Spacer(Modifier.height(CutGifDimens.Space8))
            TextButton(onClick = onRetry) {
                Icon(
                    imageVector = Icons.Rounded.Refresh,
                    contentDescription = null,
                    modifier = Modifier.size(CutGifDimens.IconSmall),
                )
                Spacer(Modifier.width(CutGifDimens.Space8))
                Text(
                    text = stringResource(R.string.retry),
                    style = MaterialTheme.typography.labelLarge,
                )
            }
        }
    }
}
