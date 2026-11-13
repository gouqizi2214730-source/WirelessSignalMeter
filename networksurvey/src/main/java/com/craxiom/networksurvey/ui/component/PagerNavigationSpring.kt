package com.craxiom.networksurvey.ui.component

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.spring
import androidx.compose.foundation.MutatePriority
import androidx.compose.foundation.pager.PagerState
import androidx.compose.ui.unit.IntOffset
import kotlinx.coroutines.coroutineScope
import kotlin.math.abs

/**
 * KernelSU 同款翻页弹簧动画参数。
 *
 * 参照 KernelSU `PagerNavigationSpring.kt`：spring 被用于底部导航 Tab 翻页与页面切换。
 * 本项目用 Navigation Compose NavHost 承载页面，KernelSU 的 PagerState.springAnimateToPage
 * 不适用，因此把同一套 spring 参数（stiffness/dampingRatio/visibilityThreshold）应用
 * 到 NavHost 的 slide/fade 过渡动画上，得到一致的弹性翻页观感。
 */
internal val PagerNavigationSpringSpec: SpringSpec<IntOffset> = spring(
    stiffness = 322.2f,
    dampingRatio = 32.31f / (2f * kotlin.math.sqrt(322.2f)),
    visibilityThreshold = IntOffset(1, 1),
)

/**
 * KernelSU 原版 Float 弹簧参数（`PagerState.springAnimateToPage` 用）。
 *
 * KernelSU `PagerNavigationSpringSpec` 本身是 `SpringSpec<Float>`（visibilityThreshold=0.5f）。
 * 本项目同文件还保留了一个 `SpringSpec<IntOffset>` 版本给 NavHost 的 slide 转场用，
 * 因此这里新增一个与原版完全一致的 Float 版，供 Pager 弹簧翻页使用。
 */
internal val PagerSpringSpec: SpringSpec<Float> = spring(
    stiffness = 322.2f,
    dampingRatio = 32.31f / (2f * kotlin.math.sqrt(322.2f)),
    visibilityThreshold = 0.5f,
)

/**
 * KernelSU `PagerState.springAnimateToPage` 同款实现。
 *
 * 以 PagerNavigationSpring 弹簧参数平滑地滑到目标页，实现「弹性吸附」翻页手感。
 * 点击底部导航时使用，与手势滑动共用同一套 Pager 滚动管线。
 */
internal suspend fun PagerState.pagerAnimateToPage(target: Int) {
    if (target !in 0 until pageCount) return
    var shouldSnapToTarget = false
    scroll(MutatePriority.UserInput) {
        val pageSize = layoutInfo.pageSize + layoutInfo.pageSpacing
        val distance = target - currentPage - currentPageOffsetFraction
        val scrollPixels = distance * pageSize
        if (abs(scrollPixels) <= 0.5f) return@scroll

        var consumedScroll = 0f
        var skipScroll = false
        Animatable(0f).animateTo(
            targetValue = scrollPixels,
            animationSpec = PagerSpringSpec,
        ) {
            if (skipScroll) return@animateTo

            val delta = value - consumedScroll
            if (abs(delta) > 0.5f) {
                val consumed = scrollBy(delta)
                consumedScroll += consumed
                if (abs(delta - consumed) > 0.1f) {
                    shouldSnapToTarget = true
                    skipScroll = true
                }
            } else {
                consumedScroll = value
            }

            if (abs(velocity) < 0.1f && abs(scrollPixels - consumedScroll) < 1.0f) {
                skipScroll = true
            }
        }

        val remaining = scrollPixels - consumedScroll
        if (abs(remaining) > 0.5f) {
            scrollBy(remaining)
        }
    }

    if (shouldSnapToTarget || currentPage != target) {
        scrollToPage(target)
    }
}