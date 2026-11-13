package com.craxiom.networksurvey.ui.component.material

import androidx.compose.foundation.layout.padding
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * KernelSU 3.2.5 椋庢牸鐨?TonalCard 鎵佸钩纾佽创鍗＄墖銆? * 鍏锋湁浼橀泤鐨勫眰绾ц壊锛坰urfaceColorAtElevation / surfaceContainerLow锛夊拰鐜颁唬 20dp 澶у渾瑙掋€? */
@Composable
fun TonalCard(
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.surfaceColorAtElevation(1.5.dp),
    cornerRadius: androidx.compose.ui.unit.Dp = 20.dp,
    onClick: () -> Unit,
    content: @Composable () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = modifier,
        colors = CardDefaults.defaultColors(color = containerColor),
        cornerRadius = cornerRadius
    ) {
        content()
    }
}

@Composable
fun TonalCard(
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.surfaceColorAtElevation(1.5.dp),
    cornerRadius: androidx.compose.ui.unit.Dp = 20.dp,
    content: @Composable () -> Unit
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.defaultColors(color = containerColor),
        cornerRadius = cornerRadius
    ) {
        content()
    }
}