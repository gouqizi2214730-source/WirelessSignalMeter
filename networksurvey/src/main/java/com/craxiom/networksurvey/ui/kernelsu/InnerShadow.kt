package com.craxiom.networksurvey.ui.kernelsu

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 内阴影修饰符
 */
fun Modifier.innerShadow(
    shape: Shape,
    color: Color = Color.Black.copy(alpha = 0.1f),
    blur: Dp = 4.dp,
    strokeWidth: Dp = 1.dp
): Modifier = this.drawWithCache {
    val outline = shape.createOutline(size, layoutDirection, this)
    onDrawWithContent {
        drawContent()
        drawOutline(
            outline = outline,
            color = color,
            style = Stroke(width = strokeWidth.toPx())
        )
    }
}
