package com.fgxyx.cutgif.ui.editor

import android.annotation.SuppressLint
import android.view.LayoutInflater
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.fgxyx.cutgif.R
import com.fgxyx.cutgif.data.CropRectN
import com.fgxyx.cutgif.data.VideoInfo
import com.fgxyx.cutgif.ui.theme.CutGifDimens
import com.fgxyx.cutgif.ui.theme.CutGifShape
import com.fgxyx.cutgif.ui.util.formatTime

@SuppressLint("InflateParams")
@androidx.annotation.OptIn(UnstableApi::class)
@Composable
fun VideoPreviewArea(
    info: VideoInfo,
    player: ExoPlayer,
    crop: CropRectN,
    ratio: CropRatio,
    playheadMs: Long,
    previewDecodeFailed: Boolean,
    onCropChange: (CropRectN) -> Unit,
    onResetCrop: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val playerViewRef = remember { PlayerViewRef() }
    val scheme = MaterialTheme.colorScheme

    Box(
        modifier = modifier
            .clip(CutGifShape.Preview)
            .then(Modifier.fillMaxSize()),
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = scheme.surfaceContainerHigh,
        ) {
            AndroidView(
                factory = { ctx ->
                    val view = LayoutInflater.from(ctx)
                        .inflate(R.layout.view_video_player, null, false) as PlayerView
                    view.player = player
                    view.useController = false
                    view.resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                    view.setShutterBackgroundColor(android.graphics.Color.TRANSPARENT)
                    view.setKeepContentOnPlayerReset(true)
                    view.setShowBuffering(PlayerView.SHOW_BUFFERING_NEVER)
                    playerViewRef.view = view
                    view
                },
                update = { view ->
                    if (view.player !== player) view.player = player
                },
                modifier = Modifier.fillMaxSize(),
            )
        }

        CropOverlay(
            info = info,
            crop = crop,
            ratio = ratio,
            onCropChange = onCropChange,
            onResetCrop = onResetCrop,
            modifier = Modifier.fillMaxSize(),
        )

        if (previewDecodeFailed) {
            Surface(
                shape = CutGifShape.Card,
                color = scheme.surfaceContainerHighest.copy(alpha = 0.96f),
                contentColor = scheme.onSurface,
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(CutGifDimens.Space24),
            ) {
                Column(
                    modifier = Modifier.padding(CutGifDimens.Space16),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Icon(
                        imageVector = Icons.Rounded.ErrorOutline,
                        contentDescription = null,
                        tint = scheme.error,
                    )
                    Spacer(Modifier.height(CutGifDimens.Space8))
                    Text(
                        text = stringResource(R.string.preview_decode_failed),
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }

        Surface(
            shape = CircleShape,
            color = scheme.surfaceContainerHighest.copy(alpha = 0.92f),
            contentColor = scheme.onSurface,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(CutGifDimens.Space12),
        ) {
            Text(
                text = "${formatTime(playheadMs)} / ${formatTime(info.durationMs)}",
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.padding(
                    horizontal = CutGifDimens.Space12,
                    vertical = CutGifDimens.Space4,
                ),
            )
        }

        Surface(
            shape = CircleShape,
            color = scheme.surfaceContainerHighest.copy(alpha = 0.92f),
            contentColor = scheme.onSurfaceVariant,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(CutGifDimens.Space12),
        ) {
            Text(
                text = stringResource(R.string.preview),
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.padding(
                    horizontal = CutGifDimens.Space12,
                    vertical = CutGifDimens.Space4,
                ),
            )
        }
    }

    DisposableEffect(player) {
        onDispose {
            playerViewRef.view?.player = null
            playerViewRef.view = null
        }
    }
}

private class PlayerViewRef {
    var view: PlayerView? = null
}
