package com.fgxyx.cutgif.ui.theme

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

val CutGifShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

object CutGifShape {
    val Button = CircleShape
    val Card = RoundedCornerShape(20.dp)
    val Panel = RoundedCornerShape(28.dp)
    val Dialog = RoundedCornerShape(28.dp)
    val Preview = RoundedCornerShape(20.dp)
    val Filmstrip = RoundedCornerShape(20.dp)
    val Sheet = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
}
