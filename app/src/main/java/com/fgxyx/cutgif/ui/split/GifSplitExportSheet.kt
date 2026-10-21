package com.fgxyx.cutgif.ui.split

import android.graphics.ImageDecoder
import android.graphics.drawable.AnimatedImageDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import android.widget.ImageView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.fgxyx.cutgif.R
import com.fgxyx.cutgif.data.ColorQuality
import com.fgxyx.cutgif.data.GifSplitResult
import com.fgxyx.cutgif.data.GifSplitScale
import com.fgxyx.cutgif.data.formatFileSize
import com.fgxyx.cutgif.ui.theme.CutGifDimens
import com.fgxyx.cutgif.ui.theme.CutGifShape
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.max
import kotlin.math.roundToInt

@Composable
fun GifSplitExportSheet(
    state: GifSplitUiState,
    onQuality: (ColorQuality) -> Unit,
    onScale: (GifSplitScale) -> Unit,
    onStartExport: () -> Unit,
    onCancelExport: () -> Unit,
    onDone: () -> Unit,
    onShare: (android.net.Uri) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(
                start = CutGifDimens.ExportContentPadding,
                end = CutGifDimens.ExportContentPadding,
                bottom = CutGifDimens.Space24,
            ),
    ) {
        when (val export = state.exportState) {
            GifSplitExportState.Idle -> ExportOptions(
                state = state,
                onQuality = onQuality,
                onScale = onScale,
                onStartExport = onStartExport,
            )
            is GifSplitExportState.InProgress -> ExportProgress(
                export = export,
                onCancel = onCancelExport,
            )
            is GifSplitExportState.Success -> ExportSuccess(
                result = export.result,
                onShare = onShare,
                onDone = onDone,
            )
            is GifSplitExportState.Failed -> ExportFailed(
                message = export.message,
                onRetry = onStartExport,
                onDone = onDone,
            )
        }
    }
}

@Composable
private fun ExportOptions(
    state: GifSplitUiState,
    onQuality: (ColorQuality) -> Unit,
    onScale: (GifSplitScale) -> Unit,
    onStartExport: () -> Unit,
) {
    val metadata = state.metadata ?: return
    Text(
        text = stringResource(R.string.split_sheet_title),
        style = MaterialTheme.typography.headlineSmall,
    )
    Spacer(Modifier.height(CutGifDimens.Space8))
    Text(
        text = stringResource(R.string.split_sheet_subtitle),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(Modifier.height(CutGifDimens.Space24))
    Text(
        text = stringResource(R.string.split_quality),
        style = MaterialTheme.typography.titleSmall,
    )
    Spacer(Modifier.height(CutGifDimens.Space8))
    Row(horizontalArrangement = Arrangement.spacedBy(CutGifDimens.Space8)) {
        ColorQuality.entries.forEach { quality ->
            ChoiceChip(
                text = stringResource(quality.labelRes),
                selected = state.quality == quality,
                onClick = { onQuality(quality) },
                modifier = Modifier.weight(1f),
            )
        }
    }
    Spacer(Modifier.height(CutGifDimens.Space24))
    Text(
        text = stringResource(R.string.split_scale),
        style = MaterialTheme.typography.titleSmall,
    )
    Spacer(Modifier.height(CutGifDimens.Space8))
    Row(horizontalArrangement = Arrangement.spacedBy(CutGifDimens.Space8)) {
        GifSplitScale.entries.forEach { scale ->
            ChoiceChip(
                text = scaleLabel(scale),
                selected = state.scale == scale,
                onClick = { onScale(scale) },
                modifier = Modifier.weight(1f),
            )
        }
    }
    Spacer(Modifier.height(CutGifDimens.Space8))
    Text(
        text = stringResource(R.string.split_transparency_hint),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(Modifier.height(CutGifDimens.Space24))
    Surface(
        shape = CutGifShape.Card,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(CutGifDimens.Space16),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(
                    R.string.split_export_summary,
                    state.endFrame - state.startFrame + 1,
                    scaledWidth(metadata.width, state.scale),
                    scaledHeight(metadata.height, state.scale),
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
    Spacer(Modifier.height(CutGifDimens.Space24))
    Button(
        onClick = onStartExport,
        modifier = Modifier
            .fillMaxWidth()
            .height(CutGifDimens.ActionButtonHeight),
        shape = CutGifShape.Button,
    ) {
        Text(
            text = stringResource(R.string.start_export),
            style = MaterialTheme.typography.labelLarge,
        )
    }
}

@Composable
private fun ExportProgress(
    export: GifSplitExportState.InProgress,
    onCancel: () -> Unit,
) {
    Text(
        text = stringResource(R.string.exporting),
        style = MaterialTheme.typography.headlineSmall,
    )
    Spacer(Modifier.height(CutGifDimens.Space24))
    LinearProgressIndicator(
        progress = { export.percent / 100f },
        modifier = Modifier
            .fillMaxWidth()
            .height(8.dp),
    )
    Spacer(Modifier.height(CutGifDimens.Space12))
    Text(
        text = stringResource(R.string.export_progress, export.percent),
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary,
    )
    Spacer(Modifier.height(CutGifDimens.Space4))
    Text(
        text = stringResource(R.string.split_exporting, export.done, export.total),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(Modifier.height(CutGifDimens.Space16))
    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
        TextButton(onClick = onCancel) {
            Text(text = stringResource(R.string.cancel_export))
        }
    }
}

@Composable
private fun ExportSuccess(
    result: GifSplitResult,
    onShare: (android.net.Uri) -> Unit,
    onDone: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        GifAnimationPreview(result.uri)
        Spacer(Modifier.height(CutGifDimens.Space16))
        Box(
            modifier = Modifier
                .size(64.dp)
                .background(
                    MaterialTheme.colorScheme.primaryContainer,
                    CircleShape,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Rounded.Check,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(CutGifDimens.IconHero),
            )
        }
        Spacer(Modifier.height(CutGifDimens.Space12))
        Text(
            text = stringResource(R.string.split_export_complete),
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(CutGifDimens.Space8))
        Text(
            text = stringResource(
                R.string.split_export_complete_hint,
                result.frameCount,
            ),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(CutGifDimens.Space4))
        Text(
            text = stringResource(
                R.string.split_export_meta,
                formatFileSize(result.fileSizeBytes),
                result.width,
                result.height,
            ),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(CutGifDimens.Space16))
        Surface(
            shape = CutGifShape.Card,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(modifier = Modifier.padding(CutGifDimens.Space16)) {
                Text(
                    text = stringResource(R.string.save_path),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(CutGifDimens.Space4))
                Text(
                    text = result.folderPath,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
        Spacer(Modifier.height(CutGifDimens.Space24))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(CutGifDimens.Space12),
        ) {
            TextButton(
                onClick = { onShare(result.uri) },
                modifier = Modifier.weight(1f),
            ) {
                Icon(
                    imageVector = Icons.Rounded.Share,
                    contentDescription = null,
                    modifier = Modifier.size(CutGifDimens.IconSmall),
                )
                Spacer(Modifier.size(CutGifDimens.Space4))
                Text(text = stringResource(R.string.split_share))
            }
            Button(
                onClick = onDone,
                modifier = Modifier
                    .weight(1f)
                    .height(CutGifDimens.ActionButtonHeight),
                shape = CutGifShape.Button,
            ) {
                Text(text = stringResource(R.string.done))
            }
        }
    }
}

@Composable
private fun GifAnimationPreview(uri: android.net.Uri) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return
    val context = androidx.compose.ui.platform.LocalContext.current
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
                )

                drawable == null -> CircularProgressIndicator(
                    color = MaterialTheme.colorScheme.primary,
                )

                else -> AndroidView(
                    factory = { ctx ->
                        ImageView(ctx).apply {
                            scaleType = ImageView.ScaleType.FIT_CENTER
                            contentDescription = context.getString(
                                R.string.gif_preview_description,
                            )
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
private fun ExportFailed(
    message: String?,
    onRetry: () -> Unit,
    onDone: () -> Unit,
) {
    Text(
        text = stringResource(R.string.export_failed),
        style = MaterialTheme.typography.headlineSmall,
    )
    Spacer(Modifier.height(CutGifDimens.Space8))
    Text(
        text = message ?: stringResource(R.string.split_export_failed_hint),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(Modifier.height(CutGifDimens.Space24))
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(CutGifDimens.Space12),
    ) {
        TextButton(
            onClick = onDone,
            modifier = Modifier.weight(1f),
        ) {
            Text(text = stringResource(R.string.done))
        }
        Button(
            onClick = onRetry,
            modifier = Modifier
                .weight(1f)
                .height(CutGifDimens.ActionButtonHeight),
            shape = CutGifShape.Button,
        ) {
            Text(text = stringResource(R.string.retry))
        }
    }
}

@Composable
private fun ChoiceChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        modifier = modifier.height(CutGifDimens.MicroButtonSize),
        shape = CutGifShape.Button,
        color = if (selected) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.surfaceContainerHigh
        },
        contentColor = if (selected) {
            MaterialTheme.colorScheme.onPrimary
        } else {
            MaterialTheme.colorScheme.onSurface
        },
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = text,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun scaleLabel(scale: GifSplitScale): String = stringResource(
    when (scale) {
        GifSplitScale.FULL -> R.string.split_scale_full
        GifSplitScale.P75 -> R.string.split_scale_75
        GifSplitScale.P50 -> R.string.split_scale_50
        GifSplitScale.P25 -> R.string.split_scale_25
    },
)

private fun scaledWidth(width: Int, scale: GifSplitScale): Int =
    max(1, (width * scale.percent / 100f).roundToInt())

private fun scaledHeight(height: Int, scale: GifSplitScale): Int =
    max(1, (height * scale.percent / 100f).roundToInt())
