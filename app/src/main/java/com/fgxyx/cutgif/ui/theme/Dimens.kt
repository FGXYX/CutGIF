package com.fgxyx.cutgif.ui.theme

import androidx.compose.ui.unit.dp

/**
 * Design tokens for the 8dp grid. Values explicitly required by the product
 * spec that fall outside the grid (3dp joined gap, 44dp micro buttons) are
 * kept here as named tokens rather than being scattered across the UI.
 */
object CutGifDimens {
    val Zero = 0.dp
    val Hairline = 1.dp
    val Stroke = 2.dp
    val CropStroke = 2.dp
    val CropCornerStroke = 4.dp

    val Space2 = 2.dp
    val Space4 = 4.dp
    val Space8 = 8.dp
    val Space12 = 12.dp
    val Space16 = 16.dp
    val Space24 = 24.dp
    val Space32 = 32.dp
    val Space40 = 40.dp
    val Space48 = 48.dp
    val Space56 = 56.dp
    val Space64 = 64.dp

    val ConnectedGap = 3.dp
    val ConnectedInnerCorner = 8.dp

    val ActionButtonHeight = 56.dp
    val ToolbarButtonHeight = 48.dp
    val MicroButtonSize = 44.dp
    val TouchTarget = 48.dp
    val TopAppBarHeight = 64.dp

    val FilmstripHeight = 96.dp
    val FilmstripFrameHeight = 64.dp
    val RulerHeight = 32.dp
    val TrimHandleWidth = 8.dp
    val TrimHandleTouchWidth = 32.dp
    val PlayheadWidth = 2.dp

    val PreviewCorner = 20.dp
    val CardCorner = 20.dp
    val PanelCorner = 28.dp
    val SheetCorner = 28.dp

    val HomeCardMediaHeight = 168.dp
    val ActionMaxWidth = 380.dp
    val PreviewMinHeight = 240.dp
    val ChipGap = 8.dp
    val ExportContentPadding = 24.dp
    val CropHandleTouchSize = 32.dp
    val TimeDisplayMinWidth = 64.dp
    val AspectSegmentWidth = 72.dp
    val PlaySegmentWidth = 96.dp
    val SpeedSegmentWidth = 104.dp

    val IconSmall = 16.dp
    val MicroIconSize = 16.dp
    val IconCompact = 24.dp
    val IconLarge = 24.dp
    val IconHero = 32.dp

    const val FilmstripIntervalMs = 10_000L
    const val FilmstripIntervalCount = 6
    const val FilmstripMaxIntervals = 120
    val FilmstripCellGap = 3.dp
    val RulerLabelSpacing = 64.dp
    val GripHalfWidth = 2.dp
    val RatioGlyphCorner = 4.dp
    val RatioGlyphStroke = 2.dp
    val RatioGlyphDot = 2.dp
    val RatioGlyphDotInset = 4.dp
    val GifPreviewHeight = 184.dp
}
