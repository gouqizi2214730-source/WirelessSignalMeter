package com.craxiom.networksurvey.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Error
import androidx.compose.material.icons.rounded.Warning
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * KernelSU 风格 StatusCard。
 *
 * 设计参考：KernelSU Manager 首页状态卡——大色块 + 右下角大图标 + 多级文字 + 状态标签。
 * 三态：
 * - 正常（绿色）：secondaryContainer 背景 + CheckCircle 大图标
 * - 警告（琥珀色）：tertiaryContainer 背景 + Warning 大图标
 * - 异常（红色）：errorContainer 背景 + Error 大图标
 *
 * @param status 状态类型
 * @param title 主标题（如 "工作中" / "无服务"）
 * @param subtitle 副标题（如版本号/制式信息）
 * @param tags 状态标签列表（如 "5G" / "LTE"）
 * @param onClick 点击回调（可选）
 */

enum class StatusCardType {
    NORMAL,   // 正常（绿色）
    WARNING,  // 警告（琥珀色）
    ERROR     // 异常（红色）
}

@Composable
fun StatusCard(
    status: StatusCardType,
    title: String,
    subtitle: String? = null,
    tags: List<String> = emptyList(),
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null
) {
    val (containerColor, iconColor, icon) = when (status) {
        StatusCardType.NORMAL -> Triple(
            MaterialTheme.colorScheme.secondaryContainer,
            MaterialTheme.colorScheme.onSecondaryContainer,
            Icons.Rounded.CheckCircle
        )
        StatusCardType.WARNING -> Triple(
            MaterialTheme.colorScheme.tertiaryContainer,
            MaterialTheme.colorScheme.onTertiaryContainer,
            Icons.Rounded.Warning
        )
        StatusCardType.ERROR -> Triple(
            MaterialTheme.colorScheme.errorContainer,
            MaterialTheme.colorScheme.onErrorContainer,
            Icons.Rounded.Error
        )
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        colors = CardDefaults.defaultColors(color = containerColor),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
        ) {
            // 左侧文字区
            Column(
                modifier = Modifier.align(Alignment.CenterStart)
            ) {
                Text(
                    text = title,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSecondaryContainer.copy(
                        alpha = if (status == StatusCardType.NORMAL) 1f else 0.9f
                    )
                )
                if (subtitle != null) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = subtitle,
                        fontSize = 15.sp,
                        color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f)
                    )
                }
                if (tags.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        tags.forEach { tag ->
                            StatusTag(text = tag)
                        }
                    }
                }
            }

            // 右下角大图标（装饰性，偏移定位）
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconColor,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .offset(x = 8.dp, y = 8.dp)
                    .size(80.dp)
            )
        }
    }
}

/**
 * 状态标签（小药丸）。
 */
@Composable
fun StatusTag(
    text: String,
    modifier: Modifier = Modifier
) {
    Surface(modifier = modifier,
        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
    ) {
        Text(
            text = text,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
        )
    }
}

/**
 * KernelSU 风格 InfoCard：逐行展示键值信息。
 * 每行：24dp 图标 + 标题 + 值。
 */
@Composable
fun InfoCard(
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainerHigh,
    content: @Composable () -> Unit
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.defaultColors(color = containerColor),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            content()
        }
    }
}

/**
 * InfoCard 中的一行信息。
 */
@Composable
fun InfoRow(
    icon: ImageVector? = null,
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(16.dp))
        }
        Text(
            text = label,
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = value,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}
