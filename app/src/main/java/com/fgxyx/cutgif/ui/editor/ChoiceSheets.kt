package com.fgxyx.cutgif.ui.editor

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import com.fgxyx.cutgif.R
import com.fgxyx.cutgif.ui.components.pressScale
import com.fgxyx.cutgif.ui.theme.CutGifDimens
import com.fgxyx.cutgif.ui.theme.CutGifShape
import com.fgxyx.cutgif.ui.util.formatSpeed
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AspectSheet(
    current: CropRatio,
    onSelect: (CropRatio) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = MaterialTheme.colorScheme.onSurface,
        shape = CutGifShape.Panel,
        dragHandle = { BottomSheetDefaults.DragHandle() },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = CutGifDimens.ExportContentPadding)
                .padding(bottom = CutGifDimens.Space24),
        ) {
            SheetHeader(
                title = stringResource(R.string.sheet_aspect_title),
                subtitle = stringResource(R.string.sheet_aspect_hint),
                onDismiss = onDismiss,
            )
            Spacer(Modifier.height(CutGifDimens.Space16))
            CropRatio.entries.forEach { ratio ->
                RatioRow(
                    ratio = ratio,
                    selected = ratio == current,
                    onClick = {
                        onSelect(ratio)
                        onDismiss()
                    },
                )
                Spacer(Modifier.height(CutGifDimens.Space8))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SpeedSheet(
    speed: Float,
    options: List<Float>,
    onSpeedChange: (Float) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val index = options.indexOfFirst { it == speed }.coerceAtLeast(0)
    val doneInteraction = remember { MutableInteractionSource() }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = MaterialTheme.colorScheme.onSurface,
        shape = CutGifShape.Panel,
        dragHandle = { BottomSheetDefaults.DragHandle() },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = CutGifDimens.ExportContentPadding)
                .padding(bottom = CutGifDimens.Space24),
        ) {
            SheetHeader(
                title = stringResource(R.string.sheet_speed_title),
                subtitle = stringResource(R.string.sheet_speed_hint),
                onDismiss = onDismiss,
            )
            Spacer(Modifier.height(CutGifDimens.Space24))
            Surface(
                shape = CutGifShape.Card,
                color = MaterialTheme.colorScheme.surfaceContainer,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    modifier = Modifier.padding(CutGifDimens.Space24),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = stringResource(
                            R.string.speed_value,
                            formatSpeed(options[index]),
                        ),
                        style = MaterialTheme.typography.displaySmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(Modifier.height(CutGifDimens.Space16))
                    Slider(
                        value = index.toFloat(),
                        onValueChange = { value ->
                            options.getOrNull(value.roundToInt())?.let(onSpeedChange)
                        },
                        valueRange = 0f..(options.lastIndex.toFloat()),
                        steps = (options.size - 2).coerceAtLeast(0),
                    )
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(
                            text = stringResource(R.string.speed_value, formatSpeed(options.first())),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text = stringResource(R.string.speed_value, formatSpeed(options.last())),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            Spacer(Modifier.height(CutGifDimens.Space24))
            Button(
                onClick = onDismiss,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(CutGifDimens.ActionButtonHeight)
                    .pressScale(doneInteraction),
                shape = CutGifShape.Button,
                interactionSource = doneInteraction,
            ) {
                Text(
                    text = stringResource(R.string.done),
                    style = MaterialTheme.typography.labelLarge,
                )
            }
        }
    }
}

@Composable
fun SheetHeader(
    title: String,
    subtitle: String?,
    onDismiss: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            if (subtitle != null) {
                Spacer(Modifier.height(CutGifDimens.Space4))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (onDismiss != null) {
            Spacer(Modifier.width(CutGifDimens.Space8))
            IconButton(onClick = onDismiss) {
                Icon(
                    imageVector = Icons.Rounded.Close,
                    contentDescription = stringResource(R.string.close),
                )
            }
        }
    }
}

@Composable
private fun RatioRow(
    ratio: CropRatio,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(CutGifDimens.Space16),
        color = if (selected) scheme.primaryContainer else scheme.surfaceContainer,
        contentColor = if (selected) scheme.onPrimaryContainer else scheme.onSurface,
        modifier = Modifier
            .fillMaxWidth()
            .height(CutGifDimens.Space56),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = CutGifDimens.Space16),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RatioGlyph(
                ratio = ratio,
                color = if (selected) scheme.onPrimaryContainer else scheme.onSurfaceVariant,
            )
            Spacer(Modifier.width(CutGifDimens.Space16))
            Text(
                text = stringResource(ratio.labelRes),
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f),
            )
            if (selected) {
                Icon(
                    imageVector = Icons.Rounded.Check,
                    contentDescription = null,
                    tint = scheme.primary,
                )
            }
        }
    }
}

@Composable
private fun RatioGlyph(ratio: CropRatio, color: androidx.compose.ui.graphics.Color) {
    Canvas(Modifier.size(CutGifDimens.Space32)) {
        val maxDimension = size.minDimension
        val pixelRatio = ratio.pixelRatio ?: 1.6f
        val width: Float
        val height: Float
        if (pixelRatio >= 1f) {
            width = maxDimension
            height = maxDimension / pixelRatio
        } else {
            height = maxDimension
            width = maxDimension * pixelRatio
        }
        val left = (size.width - width) / 2f
        val top = (size.height - height) / 2f
        drawRoundRect(
            color = color,
            topLeft = Offset(left, top),
            size = Size(width, height),
            cornerRadius = CornerRadius(
                CutGifDimens.RatioGlyphCorner.toPx(),
                CutGifDimens.RatioGlyphCorner.toPx(),
            ),
            style = Stroke(width = CutGifDimens.RatioGlyphStroke.toPx()),
        )
        if (ratio == CropRatio.Free) {
            drawCircle(
                color = color,
                radius = CutGifDimens.RatioGlyphDot.toPx(),
                center = Offset(
                    size.width - CutGifDimens.RatioGlyphDotInset.toPx(),
                    CutGifDimens.RatioGlyphDotInset.toPx(),
                ),
            )
        }
    }
}
