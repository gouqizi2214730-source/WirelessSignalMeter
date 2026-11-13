package com.craxiom.networksurvey.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * Colors for the redesigned Wi-Fi list and Network Details screens.
 *
 * Scoped to Wi-Fi screens; cellular keeps [com.craxiom.networksurvey.util.ColorUtils]
 * with its own 5-bucket ramp so neither screen drifts visually.
 */
object WifiTokens {

    /**
     * 原来的固定青蓝 #03A9F4 不跟随自定义主题色。改为 @Composable 读
     * MaterialTheme.colorScheme.primary，使 Wi-Fi/PLMN/Watchlist 模块的选中项、
     * 光标色、强调色全部自动跟随用户在设置页选择的自定义主题色（蓝/绿/紫/橙）。
     */
    val SsidAccent: Color
        @Composable get() = MaterialTheme.colorScheme.primary

    val SsidSoft: Color
        @Composable get() = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)

    val PasspointBlue = Color(0xFF8FA0F0)

    val HiddenSsid = Color(0xFF90A4AE)

    // Neutral text + surface tokens：随当前主题深浅自适应
    // 浅色模式用深色文字，深色模式用浅色文字，避免在浅色模式下泛白看不清
    val Ink: Color
        @Composable get() = MaterialTheme.colorScheme.onSurface

    val InkDim: Color
        @Composable get() = MaterialTheme.colorScheme.onSurfaceVariant

    val InkMuted: Color
        @Composable get() = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f)

    val InkFaint: Color
        @Composable get() = MaterialTheme.colorScheme.outline

    val Band24 = Color(0xFFF2C94C)
    val Band5 = Color(0xFF56CCF2)
    val Band6 = Color(0xFFBB6BD9)

    val SignalStrong = Color(0xFF388E3C)
    val SignalGood = Color(0xFFFBC02D)
    val SignalFair = Color(0xFFF57C00)
    val SignalWeak = Color(0xFFD32F2F)
    val SignalVeryWeak = Color(0xFFD50000)

    val SecurityMint = Color(0xFF77D8B0)
    val SecurityEnterprise = Color(0xFF6FCFE0)
    val SecurityOpen = Color(0xFFE88A6C)
}
