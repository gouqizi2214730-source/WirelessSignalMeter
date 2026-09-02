package com.craxiom.networksurvey.ui.kernelsu

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.collectLatest
import top.yukonga.miuix.kmp.blur.Backdrop
import top.yukonga.miuix.kmp.blur.blur
import top.yukonga.miuix.kmp.blur.drawBackdrop

/**
 * KernelSU 风格液态玻璃悬浮底栏
 */
@Composable
fun FloatingBottomBar(
    modifier: Modifier = Modifier,
    selectedIndex: () -> Int,
    onSelected: (Int) -> Unit,
    backdrop: Backdrop? = null,
    tabsCount: Int = 3,
    isBlurEnabled: Boolean = true,
    accentColor: Color = MaterialTheme.colorScheme.primary,
    surfaceContainer: Color = MaterialTheme.colorScheme.surfaceContainer,
    pagerOffsetFraction: (() -> Float)? = null,
    onBubbleProgress: ((Float) -> Unit)? = null,
    content: @Composable RowScope.() -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    val configuration = LocalConfiguration.current
    val screenWidth = configuration.screenWidthDp.dp
    val barWidth = (screenWidth - 48.dp).coerceAtMost(360.dp)
    val tabWidth = barWidth / tabsCount

    var currentIndex by remember { mutableIntStateOf(selectedIndex()) }

    val dampedDragAnimation = remember(tabsCount) {
        DampedDragAnimation(
            animationScope = coroutineScope,
            initialValue = selectedIndex().toFloat(),
            valueRange = 0f..(tabsCount - 1).toFloat(),
            onDragStarted = {},
            onDragStopped = { _ -> },
            onDrag = { dragAmount -> }
        )
    }

    LaunchedEffect(selectedIndex()) {
        val target = selectedIndex()
        if (target != currentIndex) {
            currentIndex = target
            dampedDragAnimation.animateToValue(target.toFloat())
        }
    }

    LaunchedEffect(dampedDragAnimation) {
        snapshotFlow { dampedDragAnimation.value }.collectLatest { value ->
            onBubbleProgress?.invoke(value)
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(bottom = 12.dp),
        contentAlignment = Alignment.BottomCenter
    ) {
        // 底栏背景药丸
        Box(
            modifier = Modifier
                .width(barWidth)
                .height(64.dp)
                .clip(CircleShape)
                .then(
                    if (isBlurEnabled && backdrop != null) {
                        Modifier.drawBackdrop(
                            backdrop = backdrop,
                            shape = { CircleShape },
                            effects = {
                                blur(25f)
                                vibrancy()
                            }
                        )
                    } else {
                        Modifier.background(surfaceContainer.copy(alpha = 0.85f))
                    }
                )
                .innerShadow(CircleShape, Color.White.copy(alpha = 0.2f), 2.dp, 1.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            // 滑动指示器气泡（水滴）
            val bubbleOffset = tabWidth * dampedDragAnimation.value
            Box(
                modifier = Modifier
                    .offset(x = bubbleOffset)
                    .width(tabWidth)
                    .height(48.dp)
                    .padding(horizontal = 4.dp)
                    .scale(
                        scaleX = dampedDragAnimation.scaleX,
                        scaleY = dampedDragAnimation.scaleY
                    )
                    .clip(CircleShape)
                    .background(accentColor.copy(alpha = 0.22f))
                    .then(dampedDragAnimation.modifier)
            )

            // Tab 图标与文字内容区
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                content()
            }
        }
    }
}

@Composable
fun FloatingBottomBarItem(
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    content: @Composable () -> Unit
) {
    Box(
        modifier = modifier
            .height(64.dp)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            content()
        }
    }
}
