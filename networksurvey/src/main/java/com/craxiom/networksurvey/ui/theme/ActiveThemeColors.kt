package com.craxiom.networksurvey.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * 全局当前生效主题色桥。
 *
 * 蜂窝页的 SIM 卡切换（TabLayout）等原生 View 无法直接读取 Compose 的 [androidx.compose.material3.MaterialTheme]
 * colorScheme，因此 NsTheme 每回重组会把当前生效配色（ARGB int）写到这里，原生 Fragment 即可据此同步主题色，
 * 保证「主题色全局生效」（而不只是 Compose 页面内生效）。
 *
 * 注意：Compose [Color] 是 value class，不能直接作为 @JvmField，因此这里统一存 ARGB Int。
 */
object ActiveThemeColors {
    @JvmField
    var primary: Int = 0
    @JvmField
    var onPrimary: Int = 0
    @JvmField
    var primaryContainer: Int = 0
    @JvmField
    var onPrimaryContainer: Int = 0
    @JvmField
    var secondaryContainer: Int = 0
    @JvmField
    var onSecondaryContainer: Int = 0
    @JvmField
    var surface: Int = 0
    @JvmField
    var surfaceContainer: Int = 0
    @JvmField
    var surfaceContainerLow: Int = 0
    @JvmField
    var surfaceContainerHigh: Int = 0
    @JvmField
    var onSurface: Int = 0
    @JvmField
    var onSurfaceVariant: Int = 0
    @JvmField
    var background: Int = 0

    /** 主题是否已初始化（NsTheme 至少重组过一次）。 */
    @JvmStatic
    fun isInitialized(): Boolean = primary != 0

    /** Compose [Color] 转 ARGB int（用于原生 View 的 setColor）。 */
    @JvmStatic
    fun toArgb(color: Color): Int = (color.value.toLong() and 0xFFFFFFFFL).toInt()

    /** 当前主题主色（ARGB）。 */
    @JvmStatic
    fun primaryArgb(): Int = primary

    /** 当前主题主容器色（ARGB）。 */
    @JvmStatic
    fun primaryContainerArgb(): Int = primaryContainer

    /** 当前主题次要文字色（ARGB）。 */
    @JvmStatic
    fun onSurfaceVariantArgb(): Int = onSurfaceVariant

    /** 当前主题页面底色（ARGB）。 */
    @JvmStatic
    fun surfaceContainerLowArgb(): Int = surfaceContainerLow

    /** 当前主题主文字色（ARGB）。 */
    @JvmStatic
    fun onSurfaceArgb(): Int = onSurface
}