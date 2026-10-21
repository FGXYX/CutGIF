package com.fgxyx.cutgif.ui.editor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBackIos
import androidx.compose.material.icons.rounded.ArrowForwardIos
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.fgxyx.cutgif.R
import com.fgxyx.cutgif.ui.components.ConnectedButtonGroup
import com.fgxyx.cutgif.ui.components.ConnectedSegment
import com.fgxyx.cutgif.ui.components.ConnectedStyle
import com.fgxyx.cutgif.ui.components.pressScale
import com.fgxyx.cutgif.ui.theme.CutGifDimens
import com.fgxyx.cutgif.ui.util.formatSpeed
import com.fgxyx.cutgif.ui.util.formatTime
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.runtime.remember

@Composable
fun TrimMicroAdjustRow(
    startMs: Long,
    endMs: Long,
    playheadMs: Long,
    isPlaying: Boolean,
    selectedPointer: TrimPointer,
    onSelectPointer: (TrimPointer) -> Unit,
    onNudge: (Long) -> Unit,
    onTogglePlayback: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val nudgeBackLabel = stringResource(R.string.nudge_back)
    val nudgeForwardLabel = stringResource(R.string.nudge_forward)
    val startLabel = stringResource(R.string.select_start)
    val playheadLabel = stringResource(R.string.select_playhead)
    val endLabel = stringResource(R.string.select_end)
    val interactionLeft = remember { MutableInteractionSource() }
    val interactionRight = remember { MutableInteractionSource() }

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FilledTonalIconButton(
            onClick = { onNudge(-EditorViewModel.NUDGE_MS) },
            modifier = Modifier
                .size(CutGifDimens.MicroButtonSize)
                .pressScale(interactionLeft),
            interactionSource = interactionLeft,
        ) {
            Icon(
                imageVector = Icons.Rounded.ArrowBackIos,
                contentDescription = nudgeBackLabel,
                modifier = Modifier.size(CutGifDimens.MicroIconSize),
            )
        }
        Spacer(Modifier.width(CutGifDimens.Space8))
        ConnectedButtonGroup {
            ConnectedSegment(
                text = formatTime(startMs),
                onClick = { onSelectPointer(TrimPointer.Start) },
                index = 0,
                count = 3,
                style = ConnectedStyle.Surface,
                selected = selectedPointer == TrimPointer.Start,
                minWidth = CutGifDimens.TimeDisplayMinWidth,
                height = CutGifDimens.MicroButtonSize,
                horizontalPadding = CutGifDimens.Space8,
                contentDescription = startLabel,
            )
            ConnectedSegment(
                text = formatTime(playheadMs),
                onClick = onTogglePlayback,
                index = 1,
                count = 3,
                style = ConnectedStyle.Tonal,
                minWidth = CutGifDimens.TimeDisplayMinWidth,
                height = CutGifDimens.MicroButtonSize,
                horizontalPadding = CutGifDimens.Space8,
                icon = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                contentDescription = playheadLabel,
            )
            ConnectedSegment(
                text = formatTime(endMs),
                onClick = { onSelectPointer(TrimPointer.End) },
                index = 2,
                count = 3,
                style = ConnectedStyle.Surface,
                selected = selectedPointer == TrimPointer.End,
                minWidth = CutGifDimens.TimeDisplayMinWidth,
                height = CutGifDimens.MicroButtonSize,
                horizontalPadding = CutGifDimens.Space8,
                contentDescription = endLabel,
            )
        }
        Spacer(Modifier.width(CutGifDimens.Space8))
        FilledTonalIconButton(
            onClick = { onNudge(EditorViewModel.NUDGE_MS) },
            modifier = Modifier
                .size(CutGifDimens.MicroButtonSize)
                .pressScale(interactionRight),
            interactionSource = interactionRight,
        ) {
            Icon(
                imageVector = Icons.Rounded.ArrowForwardIos,
                contentDescription = nudgeForwardLabel,
                modifier = Modifier.size(CutGifDimens.MicroIconSize),
            )
        }
    }
}

@Composable
fun EditorToolbar(
    isPlaying: Boolean,
    speed: Float,
    enabled: Boolean,
    onOpenAspect: () -> Unit,
    onTogglePlayback: () -> Unit,
    onOpenSpeed: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center,
    ) {
        ConnectedButtonGroup {
            ConnectedSegment(
                text = stringResource(R.string.aspect),
                onClick = onOpenAspect,
                index = 0,
                count = 3,
                style = ConnectedStyle.Filled,
                enabled = enabled,
                minWidth = CutGifDimens.AspectSegmentWidth,
            )
            ConnectedSegment(
                text = if (isPlaying) {
                    stringResource(R.string.pause)
                } else {
                    stringResource(R.string.play)
                },
                onClick = onTogglePlayback,
                index = 1,
                count = 3,
                style = ConnectedStyle.Tonal,
                enabled = enabled,
                icon = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                minWidth = CutGifDimens.PlaySegmentWidth,
            )
            ConnectedSegment(
                text = "${stringResource(R.string.speed)} ${
                    stringResource(R.string.speed_value, formatSpeed(speed))
                }",
                onClick = onOpenSpeed,
                index = 2,
                count = 3,
                style = ConnectedStyle.Filled,
                enabled = enabled,
                minWidth = CutGifDimens.SpeedSegmentWidth,
            )
        }
    }
}
