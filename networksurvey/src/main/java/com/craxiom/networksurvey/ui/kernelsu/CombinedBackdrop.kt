package com.craxiom.networksurvey.ui.kernelsu

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.unit.Density
import top.yukonga.miuix.kmp.blur.Backdrop

/**
 * 合并两层 Backdrop，用于气泡多重折射
 */
class CombinedBackdrop(
    val first: Backdrop,
    val second: Backdrop
) : Backdrop {
    override val isCoordinatesDependent: Boolean =
        first.isCoordinatesDependent || second.isCoordinatesDependent

    override fun DrawScope.drawBackdrop(
        density: Density,
        coordinates: LayoutCoordinates?,
        layerBlock: (GraphicsLayerScope.() -> Unit)?,
        downscaleFactor: Int
    ) {
        with(first) {
            drawBackdrop(density, coordinates, layerBlock, downscaleFactor)
        }
        with(second) {
            drawBackdrop(density, coordinates, layerBlock, downscaleFactor)
        }
    }
}

@Composable
fun rememberCombinedBackdrop(
    first: Backdrop,
    second: Backdrop
): Backdrop = remember(first, second) {
    CombinedBackdrop(first, second)
}
