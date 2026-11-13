package com.craxiom.networksurvey.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * 全局排版定义。
 *
 * ⚠️ 这里的 TextStyle **不要硬编码 color**：一旦写死（例如 Color.White），
 * 所有引用这些样式的文字都会变成固定色 —— 浅色主题/切换主题色时白字压在浅底上几乎看不见，
 * 表现为「字体没有深色适配」。
 *
 * 保持 Color.Unspecified，文字颜色由 LocalContentColor（通常来自 colorScheme.onSurface）
 * 决定，从而自动跟随深浅色模式与用户选择的主题色。
 */
val customTypography = Typography(
    titleLarge = TextStyle(
        fontWeight = FontWeight.Bold,
        fontSize = 36.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.5.sp,
        color = Color.Unspecified
    ),
    titleMedium = TextStyle(
        fontWeight = FontWeight.Bold,
        fontSize = 24.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.5.sp,
        color = Color.Unspecified
    ),
    bodyMedium = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize = 18.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.5.sp,
        color = Color.Unspecified
    ),
    labelMedium = TextStyle(
        fontWeight = FontWeight.Bold,
        fontSize = 18.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.5.sp,
        color = Color.Unspecified
    ),
)