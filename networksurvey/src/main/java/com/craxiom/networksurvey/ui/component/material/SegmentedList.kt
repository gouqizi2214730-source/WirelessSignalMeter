package com.craxiom.networksurvey.ui.component.material

import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * KernelSU 3.2.5 标志性「分段列表」（M3 Expressive Segmented List）手工实现。
 *
 * 视觉规则（与 KernelSU 一致）：
 * - 多行连体卡片，行与行之间仅 2dp 间隙，露出底色形成分段缝；
 * - 首行顶部两侧为大圆角、末行底部两侧为大圆角，中间衔接处为小圆角；
 * - 行背景取 surfaceColorAtElevation(1.dp)，与所在卡片形成微妙色阶层次。
 *
 * 说明：工程 material3 版本为 1.4.0，尚无 SegmentedListItem 表达式组件，
 * 且构建走 --offline 不能新增依赖，故按相同视觉规范手工实现。
 */
object SegmentedDefaults {
    /** 首末行外圆角 */
    val outerCorner = 20.dp
    /** 中间衔接处小圆角 */
    val innerCorner = 6.dp
    /** 行间分段缝 */
    val gap = 2.dp

    /** 行背景：比所在卡片高一档的色调 */
    @Composable
    fun rowContainerColor() = MaterialTheme.colorScheme.surfaceColorAtElevation(1.dp)

    /**
     * 计算分段列表中第 [index] 行（共 [count] 行）的连接圆角形状。
     */
    fun segmentedShape(index: Int, count: Int): Shape {
        val isFirst = index == 0
        val isLast = index == count - 1
        return androidx.compose.foundation.shape.RoundedCornerShape(
            topStart = if (isFirst) outerCorner else innerCorner,
            topEnd = if (isFirst) outerCorner else innerCorner,
            bottomStart = if (isLast) outerCorner else innerCorner,
            bottomEnd = if (isLast) outerCorner else innerCorner,
        )
    }
}

/**
 * 分段列表容器：把若干行按 KernelSU 规范连成一体。
 *
 * @param title 可选的小节标题（primary 色小字，KernelSU 设置页风格）。
 */
@Composable
fun SegmentedColumn(
    modifier: Modifier = Modifier,
    title: String? = null,
    content: List<@Composable () -> Unit>,
) {
    if (content.isEmpty()) return

    Column(modifier = modifier.fillMaxWidth()) {
        if (!title.isNullOrEmpty()) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
            )
        }
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(SegmentedDefaults.gap)
        ) {
            content.forEachIndexed { index, itemContent ->
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(SegmentedDefaults.segmentedShape(index, content.size))
                        .background(SegmentedDefaults.rowContainerColor())
                ) {
                    itemContent()
                }
            }
        }
    }
}

/**
 * 分段列表标准行：整行可点击 + 可选标题/副标题 + 前后置内容插槽。
 * 直接放进 [SegmentedColumn] 的 content 列表即可自动获得连接圆角。
 */
@Composable
fun SegmentedRow(
    modifier: Modifier = Modifier,
    headline: String,
    supporting: String? = null,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
    onClick: (() -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .then(
                if (onClick != null) {
                    Modifier.clickable(onClick = onClick)
                } else {
                    Modifier
                }
            )
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leading != null) {
            leading()
            RowSpacer(width = 14.dp)
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = headline,
                style = MaterialTheme.typography.bodyLarge.copy(
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium
                ),
                color = MaterialTheme.colorScheme.onSurface,
            )
            if (!supporting.isNullOrEmpty()) {
                Text(
                    text = supporting,
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                    color = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
        if (trailing != null) {
            RowSpacer(width = 12.dp)
            trailing()
        }
    }
}

@Composable
private fun RowSpacer(width: androidx.compose.ui.unit.Dp) {
    androidx.compose.foundation.layout.Spacer(modifier = Modifier.width(width))
}
