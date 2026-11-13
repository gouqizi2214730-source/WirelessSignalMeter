package com.craxiom.networksurvey.ui.dashboard

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.craxiom.networksurvey.R

/**
 * KernelSU 3.2.5 现代大圆角质感卡片规范
 */
object DashboardCardDefaults {
    // KernelSU 3.2.5 规范：24dp 大圆角 + 16dp 屏幕水平边距，与分段列表、液态玻璃设计语言统一
    val shape = RoundedCornerShape(24.dp)
    val smallShape = RoundedCornerShape(12.dp)
    val defaultElevation = 0.dp // 现代扁平 Tonal 质感，不使用厚重阴影
    val margin = 16.dp

    /**
     * 卡片主背景色：取当前主题层次化的 surfaceContainerLow
     */
    @Composable
    fun cardContainerColor(): Color = MaterialTheme.colorScheme.surfaceContainerLow

    /**
     * 卡片正文主文字色
     */
    @Composable
    fun textPrimary(): Color = MaterialTheme.colorScheme.onSurface

    /**
     * 卡片次要文字色
     */
    @Composable
    fun textSecondary(): Color = MaterialTheme.colorScheme.onSurfaceVariant

    /**
     * 卡片细边框/分隔线颜色
     */
    @Composable
    fun outlineVariant(): Color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
}

/**
 * KernelSU 风格的卡片标题头：
 * 采用小巧圆形的图标容器胶囊 + 加粗标题 + 现代右侧操作区，与卡片浑然一体
 */
@Composable
fun DashboardCardHeader(
    @DrawableRes icon: Int,
    title: String,
    modifier: Modifier = Modifier,
    iconTint: Color? = null,
    iconBgColor: Color? = null,
    action: (@Composable () -> Unit)? = null,
    onHelpClick: (() -> Unit)? = null,
) {
    val primary = MaterialTheme.colorScheme.primary
    val effectiveIconTint = iconTint ?: primary
    val effectiveIconBg = iconBgColor ?: MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // 圆形彩色图标徽章容器（KernelSU 经典设计）
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(effectiveIconBg),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = painterResource(id = icon),
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = effectiveIconTint,
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium.copy(
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 0.2.sp
            ),
            color = DashboardCardDefaults.textPrimary(),
            modifier = Modifier.weight(1f),
        )

        if (action != null) {
            action()
        }

        if (onHelpClick != null) {
            IconButton(
                onClick = onHelpClick,
                modifier = Modifier.size(32.dp),
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_help),
                    contentDescription = "Help",
                    modifier = Modifier.size(19.dp),
                    tint = DashboardCardDefaults.textSecondary(),
                )
            }
        }
    }
}
