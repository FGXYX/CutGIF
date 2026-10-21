package com.fgxyx.cutgif.ui.components

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import com.fgxyx.cutgif.ui.theme.CutGifDimens

enum class ConnectedStyle {
    Filled,
    Tonal,
    Surface,
}

fun connectedShape(index: Int, count: Int, height: Dp): Shape {
    if (count <= 1) return CircleShape
    val outer = height / 2
    val inner = CutGifDimens.ConnectedInnerCorner
    return when (index) {
        0 -> RoundedCornerShape(
            topStart = outer,
            bottomStart = outer,
            topEnd = inner,
            bottomEnd = inner,
        )
        count - 1 -> RoundedCornerShape(
            topStart = inner,
            bottomStart = inner,
            topEnd = outer,
            bottomEnd = outer,
        )
        else -> RoundedCornerShape(inner)
    }
}

@Composable
fun ConnectedSegment(
    text: String,
    onClick: () -> Unit,
    index: Int,
    count: Int,
    modifier: Modifier = Modifier,
    style: ConnectedStyle = ConnectedStyle.Filled,
    selected: Boolean = false,
    enabled: Boolean = true,
    icon: ImageVector? = null,
    contentDescription: String? = null,
    minWidth: Dp = CutGifDimens.Zero,
    height: Dp = CutGifDimens.ToolbarButtonHeight,
    horizontalPadding: Dp = CutGifDimens.Space16,
) {
    val shape = connectedShape(index, count, height)
    val scheme = MaterialTheme.colorScheme
    val container: Color
    val content: Color
    if (selected) {
        container = scheme.primaryContainer
        content = scheme.onPrimaryContainer
    } else {
        when (style) {
            ConnectedStyle.Filled -> {
                container = scheme.primary
                content = scheme.onPrimary
            }
            ConnectedStyle.Tonal -> {
                container = scheme.secondaryContainer
                content = scheme.onSecondaryContainer
            }
            ConnectedStyle.Surface -> {
                container = scheme.surfaceContainerHigh
                content = scheme.onSurface
            }
        }
    }
    val interactionSource = remember { MutableInteractionSource() }
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = shape,
        color = if (enabled) container else scheme.surfaceContainerHigh,
        contentColor = if (enabled) content else scheme.onSurfaceVariant.copy(alpha = 0.45f),
        modifier = modifier
            .height(height)
            .defaultMinSize(minWidth = minWidth)
            .pressScale(interactionSource)
            .semantics {
                role = Role.Button
                if (contentDescription != null) this.contentDescription = contentDescription
            },
        interactionSource = interactionSource,
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = horizontalPadding)
                .height(height),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(CutGifDimens.IconCompact),
                )
                androidx.compose.foundation.layout.Spacer(Modifier.size(CutGifDimens.Space8))
            }
            Text(
                text = text,
                style = MaterialTheme.typography.labelLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
fun ConnectedButtonGroup(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(CutGifDimens.ConnectedGap),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        content()
    }
}
