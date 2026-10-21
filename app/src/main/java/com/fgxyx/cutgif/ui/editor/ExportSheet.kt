package com.fgxyx.cutgif.ui.editor

import android.graphics.ImageDecoder
import android.graphics.drawable.AnimatedImageDrawable
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Build
import android.widget.ImageView
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.GifBox
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.viewinterop.AndroidView
import com.fgxyx.cutgif.R
import com.fgxyx.cutgif.data.Clarity
import com.fgxyx.cutgif.data.ColorQuality
import com.fgxyx.cutgif.data.ExportConfig
import com.fgxyx.cutgif.data.ExportFrameRate
import com.fgxyx.cutgif.data.ExportResolution
import com.fgxyx.cutgif.data.ExportState
import com.fgxyx.cutgif.data.VideoInfo
import com.fgxyx.cutgif.data.formatFileSize
import com.fgxyx.cutgif.ui.components.pressScale
import com.fgxyx.cutgif.ui.theme.CutGifDimens
import com.fgxyx.cutgif.ui.theme.CutGifShape
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.roundToLong

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ExportSheet(
    info: VideoInfo,
    crop: com.fgxyx.cutgif.data.CropRectN,
    startMs: Long,
    endMs: Long,
    config: ExportConfig,
    exportState: ExportState,
    onConfigChange: (ExportConfig) -> Unit,
    onStartExport: () -> Unit,
    onCancelExport: () -> Unit,
    onDismiss: () -> Unit,
    onShare: (android.net.Uri) -> Unit,
) {
    val isExporting = exportState is ExportState.InProgress
    val exportingState = rememberUpdatedState(isExporting)
    val sheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = true,
        confirmValueChange = { value ->
            value != SheetValue.Hidden || !exportingState.value
        },
    )
    val outputSize = config.outputSize(info, crop)
    val startInteraction = remember { MutableInteractionSource() }
    val shareInteraction = remember { MutableInteractionSource() }
    val estimatedFrames = (
        ((endMs - startMs).coerceAtLeast(1L) / 1000.0) * config.frameRate.value
        ).roundToLong().coerceAtLeast(1L)

    ModalBottomSheet(
        onDismissRequest = {
            if (!isExporting) onDismiss()
        },
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = MaterialTheme.colorScheme.onSurface,
        shape = CutGifShape.Panel,
        dragHandle = { androidx.compose.material3.BottomSheetDefaults.DragHandle() },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .imePadding()
                .navigationBarsPadding()
                .padding(horizontal = CutGifDimens.ExportContentPadding)
                .padding(bottom = CutGifDimens.Space24),
        ) {
            when (exportState) {
                is ExportState.Success -> {
                    ExportSuccessContent(
                        result = exportState.result,
                        shareInteraction = shareInteraction,
                        onShare = { onShare(exportState.result.uri) },
                        onDone = onDismiss,
                    )
                }

                else -> {
                    SheetHeader(
                        title = stringResource(R.string.export_sheet_title),
                        subtitle = stringResource(R.string.export_sheet_subtitle),
                        onDismiss = if (isExporting) null else onDismiss,
                    )
                    Spacer(Modifier.height(CutGifDimens.Space24))

                    SectionLabel(stringResource(R.string.resolution))
                    Spacer(Modifier.height(CutGifDimens.Space8))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(CutGifDimens.ChipGap),
                        verticalArrangement = Arrangement.spacedBy(CutGifDimens.ChipGap),
                    ) {
                        ExportResolution.entries.forEach { resolution ->
                            ExportChoiceChip(
                                label = stringResource(resolution.labelRes),
                                selected = config.resolution == resolution,
                                enabled = !isExporting,
                                onClick = {
                                    if (resolution == ExportResolution.CUSTOM &&
                                        config.resolution != ExportResolution.CUSTOM
                                    ) {
                                        onConfigChange(
                                            config.copy(
                                                resolution = resolution,
                                                customWidth = outputSize.width,
                                                customHeight = outputSize.height,
                                            ),
                                        )
                                    } else {
                                        onConfigChange(config.copy(resolution = resolution))
                                    }
                                },
                            )
                        }
                    }

                    if (config.resolution == ExportResolution.CUSTOM) {
                        Spacer(Modifier.height(CutGifDimens.Space12))
                        CustomSizeFields(
                            config = config,
                            enabled = !isExporting,
                            onConfigChange = onConfigChange,
                        )
                    }

                    Spacer(Modifier.height(CutGifDimens.Space24))
                    SectionLabel(stringResource(R.string.clarity))
                    Spacer(Modifier.height(CutGifDimens.Space8))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(CutGifDimens.ChipGap),
                        verticalArrangement = Arrangement.spacedBy(CutGifDimens.ChipGap),
                    ) {
                        Clarity.entries.forEach { clarity ->
                            ExportChoiceChip(
                                label = stringResource(clarity.labelRes),
                                selected = config.clarity == clarity,
                                enabled = !isExporting,
                                onClick = { onConfigChange(config.copy(clarity = clarity)) },
                            )
                        }
                    }

                    Spacer(Modifier.height(CutGifDimens.Space24))
                    SectionLabel(stringResource(R.string.color_quality))
                    Spacer(Modifier.height(CutGifDimens.Space8))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(CutGifDimens.ChipGap),
                        verticalArrangement = Arrangement.spacedBy(CutGifDimens.ChipGap),
                    ) {
                        ColorQuality.entries.forEach { quality ->
                            ExportChoiceChip(
                                label = stringResource(quality.labelRes),
                                selected = config.colorQuality == quality,
                                enabled = !isExporting,
                                onClick = {
                                    onConfigChange(config.copy(colorQuality = quality))
                                },
                            )
                        }
                    }

                    Spacer(Modifier.height(CutGifDimens.Space24))
                    SectionLabel(stringResource(R.string.frame_rate))
                    Spacer(Modifier.height(CutGifDimens.Space8))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(CutGifDimens.ChipGap),
                        verticalArrangement = Arrangement.spacedBy(CutGifDimens.ChipGap),
                    ) {
                        ExportFrameRate.entries.forEach { frameRate ->
                            ExportChoiceChip(
                                label = frameRate.value.toString(),
                                selected = config.frameRate == frameRate,
                                enabled = !isExporting,
                                onClick = {
                                    onConfigChange(config.copy(frameRate = frameRate))
                                },
                            )
                        }
                    }

                    Spacer(Modifier.height(CutGifDimens.Space24))
                    ExportSummary(
                        width = outputSize.width,
                        height = outputSize.height,
                        frames = estimatedFrames.toInt(),
                        fps = config.frameRate.value,
                    )
                    if (estimatedFrames > 600L) {
                        Spacer(Modifier.height(CutGifDimens.Space8))
                        Text(
                            text = stringResource(R.string.long_export_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    if (exportState is ExportState.Failed) {
                        Spacer(Modifier.height(CutGifDimens.Space16))
                        ExportErrorBanner()
                    }

                    Spacer(Modifier.height(CutGifDimens.Space24))
                    if (exportState is ExportState.InProgress) {
                        ExportProgressBlock(
                            state = exportState,
                            onCancelExport = onCancelExport,
                        )
                    } else {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            TextButton(
                                onClick = onDismiss,
                                modifier = Modifier.height(CutGifDimens.ActionButtonHeight),
                            ) {
                                Text(
                                    text = stringResource(
                                        if (exportState is ExportState.Failed) {
                                            R.string.close
                                        } else {
                                            R.string.cancel
                                        },
                                    ),
                                    style = MaterialTheme.typography.labelLarge,
                                )
                            }
                            Spacer(Modifier.width(CutGifDimens.Space8))
                            Button(
                                onClick = onStartExport,
                                enabled = config.resolution != ExportResolution.CUSTOM ||
                                    config.customSizeValid,
                                modifier = Modifier
                                    .weight(1f)
                                    .height(CutGifDimens.ActionButtonHeight)
                                    .pressScale(startInteraction),
                                shape = CutGifShape.Button,
                                interactionSource = startInteraction,
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Download,
                                    contentDescription = null,
                                    modifier = Modifier.size(CutGifDimens.IconCompact),
                                )
                                Spacer(Modifier.width(CutGifDimens.Space8))
                                Text(
                                    text = stringResource(R.string.start_export),
                                    style = MaterialTheme.typography.labelLarge,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ExportSuccessContent(
    result: com.fgxyx.cutgif.data.ExportResult,
    shareInteraction: MutableInteractionSource,
    onShare: () -> Unit,
    onDone: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(CutGifDimens.Space16))
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier.size(CutGifDimens.Space64),
        ) {
            androidx.compose.foundation.layout.Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Rounded.CheckCircle,
                    contentDescription = null,
                    modifier = Modifier.size(CutGifDimens.Space32),
                )
            }
        }
        Spacer(Modifier.height(CutGifDimens.Space16))
        Text(
            text = stringResource(R.string.export_complete),
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(CutGifDimens.Space8))
        Text(
            text = stringResource(R.string.export_complete_hint),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(CutGifDimens.Space16))
        GifAnimationPreview(uri = result.uri)
        Spacer(Modifier.height(CutGifDimens.Space16))
        Surface(
            shape = CutGifShape.Card,
            color = MaterialTheme.colorScheme.surfaceContainer,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(Modifier.padding(CutGifDimens.Space16)) {
                Text(
                    text = result.fileName,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.height(CutGifDimens.Space12))
                Row(verticalAlignment = Alignment.Top) {
                    Icon(
                        imageVector = Icons.Rounded.Folder,
                        contentDescription = null,
                        modifier = Modifier.size(CutGifDimens.IconSmall),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.width(CutGifDimens.Space8))
                    Column {
                        Text(
                            text = stringResource(R.string.save_path),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text = result.displayPath,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
                Spacer(Modifier.height(CutGifDimens.Space12))
                Row(Modifier.fillMaxWidth()) {
                    MetaItem(
                        label = stringResource(R.string.file_size),
                        value = formatFileSize(result.sizeBytes),
                        modifier = Modifier.weight(1f),
                    )
                    MetaItem(
                        label = stringResource(R.string.resolution),
                        value = stringResource(R.string.dimension_format, result.width, result.height),
                        modifier = Modifier.weight(1f),
                    )
                    MetaItem(
                        label = stringResource(R.string.frame_count),
                        value = stringResource(R.string.frames_short, result.frameCount),
                        modifier = Modifier.weight(1f),
                    )
                }
                if (result.usedFallback) {
                    Spacer(Modifier.height(CutGifDimens.Space12))
                    Text(
                        text = stringResource(R.string.export_fallback_warning),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }
        Spacer(Modifier.height(CutGifDimens.Space24))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(
                onClick = onDone,
                modifier = Modifier.height(CutGifDimens.ActionButtonHeight),
            ) {
                Text(
                    text = stringResource(R.string.done),
                    style = MaterialTheme.typography.labelLarge,
                )
            }
            Spacer(Modifier.width(CutGifDimens.Space8))
            Button(
                onClick = onShare,
                modifier = Modifier
                    .weight(1f)
                    .height(CutGifDimens.ActionButtonHeight)
                    .pressScale(shareInteraction),
                shape = CutGifShape.Button,
                interactionSource = shareInteraction,
            ) {
                Icon(
                    imageVector = Icons.Rounded.Share,
                    contentDescription = null,
                    modifier = Modifier.size(CutGifDimens.IconCompact),
                )
                Spacer(Modifier.width(CutGifDimens.Space8))
                Text(
                    text = stringResource(R.string.share),
                    style = MaterialTheme.typography.labelLarge,
                )
            }
        }
    }
}

@Composable
private fun GifAnimationPreview(uri: Uri) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return
    val context = LocalContext.current
    var drawable by remember(uri) { mutableStateOf<Drawable?>(null) }
    var failed by remember(uri) { mutableStateOf(false) }

    LaunchedEffect(uri) {
        val loaded = withContext(Dispatchers.IO) {
            runCatching {
                val source = ImageDecoder.createSource(context.contentResolver, uri)
                ImageDecoder.decodeDrawable(source) { decoder, info, _ ->
                    val maxDimension = maxOf(info.size.width, info.size.height).coerceAtLeast(1)
                    if (maxDimension > 720) {
                        val scale = 720f / maxDimension
                        decoder.setTargetSize(
                            (info.size.width * scale).toInt().coerceAtLeast(1),
                            (info.size.height * scale).toInt().coerceAtLeast(1),
                        )
                    }
                    decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                }
            }.getOrNull()
        }
        drawable = loaded
        failed = loaded == null
        (loaded as? AnimatedImageDrawable)?.apply {
            repeatCount = AnimatedImageDrawable.REPEAT_INFINITE
            start()
        }
    }
    DisposableEffect(drawable) {
        onDispose { (drawable as? AnimatedImageDrawable)?.stop() }
    }

    Surface(
        shape = CutGifShape.Card,
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier
            .fillMaxWidth()
            .height(CutGifDimens.GifPreviewHeight),
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            when {
                failed -> Text(
                    text = stringResource(R.string.gif_preview_unavailable),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(CutGifDimens.Space16),
                )

                drawable == null -> CircularProgressIndicator(
                    color = MaterialTheme.colorScheme.primary,
                )

                else -> AndroidView(
                    factory = { ctx ->
                        ImageView(ctx).apply {
                            scaleType = ImageView.ScaleType.FIT_CENTER
                            contentDescription = context.getString(R.string.gif_preview_description)
                        }
                    },
                    update = { view ->
                        if (view.drawable !== drawable) {
                            view.setImageDrawable(drawable)
                        }
                    },
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}

@Composable
private fun MetaItem(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(CutGifDimens.Space4))
        Text(
            text = value,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun ExportProgressBlock(
    state: ExportState.InProgress,
    onCancelExport: () -> Unit,
) {
    Surface(
        shape = CutGifShape.Card,
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(CutGifDimens.Space16)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(R.string.exporting),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.weight(1f))
                Text(
                    text = stringResource(R.string.export_progress, state.percent),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Spacer(Modifier.height(CutGifDimens.Space12))
            LinearProgressIndicator(
                progress = { state.percent / 100f },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(CutGifDimens.Space8),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
            )
            Spacer(Modifier.height(CutGifDimens.Space8))
            Text(
                text = stringResource(
                    R.string.export_progress_detail,
                    state.frame,
                    state.totalFrames,
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(CutGifDimens.Space12))
            TextButton(
                onClick = onCancelExport,
                modifier = Modifier.align(Alignment.End),
            ) {
                Text(
                    text = stringResource(R.string.cancel_export),
                    style = MaterialTheme.typography.labelLarge,
                )
            }
        }
    }
}

@Composable
private fun ExportErrorBanner() {
    Surface(
        shape = CutGifShape.Card,
        color = MaterialTheme.colorScheme.errorContainer,
        contentColor = MaterialTheme.colorScheme.onErrorContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(CutGifDimens.Space16),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Rounded.ErrorOutline,
                contentDescription = null,
                modifier = Modifier.size(CutGifDimens.IconCompact),
            )
            Spacer(Modifier.width(CutGifDimens.Space8))
            Column {
                Text(
                    text = stringResource(R.string.export_failed),
                    style = MaterialTheme.typography.labelLarge,
                )
                Text(
                    text = stringResource(R.string.export_failed_hint),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

@Composable
private fun ExportSummary(
    width: Int,
    height: Int,
    frames: Int,
    fps: Int,
) {
    Surface(
        shape = CutGifShape.Card,
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(CutGifDimens.Space16),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Rounded.GifBox,
                contentDescription = null,
                modifier = Modifier.size(CutGifDimens.Space32),
            )
            Spacer(Modifier.width(CutGifDimens.Space12))
            Column {
                Text(
                    text = stringResource(R.string.dimension_format, width, height),
                    style = MaterialTheme.typography.titleMedium,
                )
                Spacer(Modifier.height(CutGifDimens.Space2))
                Text(
                    text = stringResource(R.string.export_summary_meta, frames, fps),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.72f),
                )
            }
        }
    }
}

@Composable
private fun CustomSizeFields(
    config: ExportConfig,
    enabled: Boolean,
    onConfigChange: (ExportConfig) -> Unit,
) {
    var widthText by remember { mutableStateOf(config.customWidth.toString()) }
    var heightText by remember { mutableStateOf(config.customHeight.toString()) }
    LaunchedEffect(config.resolution) {
        widthText = config.customWidth.toString()
        heightText = config.customHeight.toString()
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OutlinedTextField(
            value = widthText,
            onValueChange = { text ->
                widthText = text.filter(Char::isDigit).take(4)
                widthText.toIntOrNull()?.let { value ->
                    onConfigChange(config.copy(customWidth = value))
                }
            },
            enabled = enabled,
            label = { Text(stringResource(R.string.custom_width)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.weight(1f),
            shape = CutGifShape.Card,
        )
        Text(
            text = "×",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = CutGifDimens.Space12),
        )
        OutlinedTextField(
            value = heightText,
            onValueChange = { text ->
                heightText = text.filter(Char::isDigit).take(4)
                heightText.toIntOrNull()?.let { value ->
                    onConfigChange(config.copy(customHeight = value))
                }
            },
            enabled = enabled,
            label = { Text(stringResource(R.string.custom_height)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.weight(1f),
            shape = CutGifShape.Card,
        )
    }
    if (!config.customSizeValid) {
        Spacer(Modifier.height(CutGifDimens.Space8))
        Text(
            text = stringResource(R.string.custom_size_invalid),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
        )
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurface,
    )
}

@Composable
private fun ExportChoiceChip(
    label: String,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        enabled = enabled,
        label = {
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
            )
        },
        shape = CircleShape,
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
        ),
    )
}
