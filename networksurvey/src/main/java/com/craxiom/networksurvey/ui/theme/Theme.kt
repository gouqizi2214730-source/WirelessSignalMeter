package com.craxiom.networksurvey.ui.theme

import android.os.Build
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import com.craxiom.networksurvey.util.ThemePref
import top.yukonga.miuix.kmp.theme.Colors
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.darkColorScheme as miuixDarkColorScheme
import top.yukonga.miuix.kmp.theme.lightColorScheme as miuixLightColorScheme
import com.materialkolor.PaletteStyle
import com.materialkolor.dynamiccolor.ColorSpec
import com.materialkolor.rememberDynamicColorScheme

private val lightScheme = lightColorScheme(
    primary = primaryLight,
    onPrimary = onPrimaryLight,
    primaryContainer = primaryContainerLight,
    onPrimaryContainer = onPrimaryContainerLight,
    secondary = secondaryLight,
    onSecondary = onSecondaryLight,
    secondaryContainer = secondaryContainerLight,
    onSecondaryContainer = onSecondaryContainerLight,
    tertiary = tertiaryLight,
    onTertiary = onTertiaryLight,
    tertiaryContainer = tertiaryContainerLight,
    onTertiaryContainer = onTertiaryContainerLight,
    error = errorLight,
    onError = onErrorLight,
    errorContainer = errorContainerLight,
    onErrorContainer = onErrorContainerLight,
    background = backgroundLight,
    onBackground = onBackgroundLight,
    surface = surfaceLight,
    onSurface = onSurfaceLight,
    surfaceVariant = surfaceVariantLight,
    onSurfaceVariant = onSurfaceVariantLight,
    outline = outlineLight,
    outlineVariant = outlineVariantLight,
    scrim = scrimLight,
    inverseSurface = inverseSurfaceLight,
    inverseOnSurface = inverseOnSurfaceLight,
    inversePrimary = inversePrimaryLight,
    surfaceDim = surfaceDimLight,
    surfaceBright = surfaceBrightLight,
    surfaceContainerLowest = surfaceContainerLowestLight,
    surfaceContainerLow = surfaceContainerLowLight,
    surfaceContainer = surfaceContainerLight,
    surfaceContainerHigh = surfaceContainerHighLight,
    surfaceContainerHighest = surfaceContainerHighestLight,
)

private val darkScheme = darkColorScheme(
    primary = primaryDark,
    onPrimary = onPrimaryDark,
    primaryContainer = primaryContainerDark,
    onPrimaryContainer = onPrimaryContainerDark,
    secondary = secondaryDark,
    onSecondary = onSecondaryDark,
    secondaryContainer = secondaryContainerDark, // Bottom Nav Bar Selection
    onSecondaryContainer = onSecondaryContainerDark,
    tertiary = tertiaryDark,
    onTertiary = onTertiaryDark,
    tertiaryContainer = tertiaryContainerDark,
    onTertiaryContainer = onTertiaryContainerDark,
    error = errorDark,
    onError = onErrorDark,
    errorContainer = errorContainerDark,
    onErrorContainer = onErrorContainerDark,
    background = backgroundDark,
    onBackground = onBackgroundDark,
    surface = surfaceDark,
    onSurface = onSurfaceDark,
    surfaceVariant = surfaceVariantDark,
    onSurfaceVariant = onSurfaceVariantDark,
    outline = outlineDark,
    outlineVariant = outlineVariantDark,
    scrim = scrimDark,
    inverseSurface = inverseSurfaceDark,
    inverseOnSurface = inverseOnSurfaceDark,
    inversePrimary = inversePrimaryDark,
    surfaceDim = surfaceDimDark,
    surfaceBright = surfaceBrightDark,
    surfaceContainerLowest = surfaceContainerLowestDark,
    surfaceContainerLow = surfaceContainerLowDark, // Card Background
    surfaceContainer = surfaceContainerDark, // Bottom Nav Bar
    surfaceContainerHigh = surfaceContainerHighDark,
    surfaceContainerHighest = surfaceContainerHighestDark,
)

// ============================================================================
// 自定义主题主色（关闭 Monet 后可手动选择；SEED_* 与 ThemePref.SEED_* 对应）
// 基于默认 lightScheme/darkScheme 用 .copy() 只覆盖 primary 相关主色槽，
// 避免重复定义整张 ColorScheme 表，也保证 secondary/tertiary/surface 整体协调。
// ============================================================================
private val blueLightScheme = lightScheme.copy(
    primary = bluePrimaryLight, onPrimary = blueOnPrimaryLight,
    primaryContainer = bluePrimaryContainerLight, onPrimaryContainer = blueOnPrimaryContainerLight,
    secondary = blueSecondaryLight, onSecondary = blueOnSecondaryLight,
    secondaryContainer = blueSecondaryContainerLight, onSecondaryContainer = blueOnSecondaryContainerLight,
    tertiary = blueTertiaryLight, onTertiary = blueOnTertiaryLight,
    tertiaryContainer = blueTertiaryContainerLight, onTertiaryContainer = blueOnTertiaryContainerLight,
    background = blueBackgroundLight, onBackground = blueOnBackgroundLight,
    surface = blueSurfaceLight, onSurface = blueOnBackgroundLight,
    surfaceVariant = blueSurfaceVariantLight, onSurfaceVariant = blueOnBackgroundLight,
    surfaceContainer = blueSurfaceContainerLight, surfaceContainerLow = blueSurfaceContainerLowLight,
    surfaceContainerLowest = blueSurfaceContainerLowLight, surfaceContainerHigh = blueSurfaceContainerLight,
    surfaceContainerHighest = blueSurfaceContainerLight, surfaceDim = blueSurfaceContainerLight,
    surfaceBright = blueBackgroundLight,
)
private val blueDarkScheme = darkScheme.copy(
    primary = bluePrimaryDark, onPrimary = blueOnPrimaryDark,
    primaryContainer = bluePrimaryContainerDark, onPrimaryContainer = blueOnPrimaryContainerDark,
    secondary = blueSecondaryDark, onSecondary = blueOnSecondaryDark,
    secondaryContainer = blueSecondaryContainerDark, onSecondaryContainer = blueOnSecondaryContainerDark,
    tertiary = blueTertiaryDark, onTertiary = blueOnTertiaryDark,
    tertiaryContainer = blueTertiaryContainerDark, onTertiaryContainer = blueOnTertiaryContainerDark,
    background = blueBackgroundDark, onBackground = blueOnBackgroundDark,
    surface = blueSurfaceDark, onSurface = blueOnBackgroundDark,
    surfaceVariant = blueSurfaceVariantDark, onSurfaceVariant = blueOnBackgroundDark,
    surfaceContainer = blueSurfaceContainerDark, surfaceContainerLow = blueSurfaceContainerLowDark,
    surfaceContainerLowest = blueSurfaceContainerLowDark, surfaceContainerHigh = blueSurfaceContainerDark,
    surfaceContainerHighest = blueSurfaceContainerDark, surfaceDim = blueSurfaceContainerDark,
    surfaceBright = blueSurfaceContainerLowDark,
)

private val greenLightScheme = lightScheme.copy(
    primary = greenPrimaryLight, onPrimary = greenOnPrimaryLight,
    primaryContainer = greenPrimaryContainerLight, onPrimaryContainer = greenOnPrimaryContainerLight,
    secondary = greenSecondaryLight, onSecondary = greenOnSecondaryLight,
    secondaryContainer = greenSecondaryContainerLight, onSecondaryContainer = greenOnSecondaryContainerLight,
    tertiary = greenTertiaryLight, onTertiary = greenOnTertiaryLight,
    tertiaryContainer = greenTertiaryContainerLight, onTertiaryContainer = greenOnTertiaryContainerLight,
    background = greenBackgroundLight, onBackground = greenOnBackgroundLight,
    surface = greenSurfaceLight, onSurface = greenOnBackgroundLight,
    surfaceVariant = greenSurfaceVariantLight, onSurfaceVariant = greenOnBackgroundLight,
    surfaceContainer = greenSurfaceContainerLight, surfaceContainerLow = greenSurfaceContainerLowLight,
    surfaceContainerLowest = greenSurfaceContainerLowLight, surfaceContainerHigh = greenSurfaceContainerLight,
    surfaceContainerHighest = greenSurfaceContainerLight, surfaceDim = greenSurfaceContainerLight,
    surfaceBright = greenBackgroundLight,
)
private val greenDarkScheme = darkScheme.copy(
    primary = greenPrimaryDark, onPrimary = greenOnPrimaryDark,
    primaryContainer = greenPrimaryContainerDark, onPrimaryContainer = greenOnPrimaryContainerDark,
    secondary = greenSecondaryDark, onSecondary = greenOnSecondaryDark,
    secondaryContainer = greenSecondaryContainerDark, onSecondaryContainer = greenOnSecondaryContainerDark,
    tertiary = greenTertiaryDark, onTertiary = greenOnTertiaryDark,
    tertiaryContainer = greenTertiaryContainerDark, onTertiaryContainer = greenOnTertiaryContainerDark,
    background = greenBackgroundDark, onBackground = greenOnBackgroundDark,
    surface = greenSurfaceDark, onSurface = greenOnBackgroundDark,
    surfaceVariant = greenSurfaceVariantDark, onSurfaceVariant = greenOnBackgroundDark,
    surfaceContainer = greenSurfaceContainerDark, surfaceContainerLow = greenSurfaceContainerLowDark,
    surfaceContainerLowest = greenSurfaceContainerLowDark, surfaceContainerHigh = greenSurfaceContainerDark,
    surfaceContainerHighest = greenSurfaceContainerDark, surfaceDim = greenSurfaceContainerDark,
    surfaceBright = greenSurfaceContainerLowDark,
)

private val purpleLightScheme = lightScheme.copy(
    primary = purplePrimaryLight, onPrimary = purpleOnPrimaryLight,
    primaryContainer = purplePrimaryContainerLight, onPrimaryContainer = purpleOnPrimaryContainerLight,
    secondary = purpleSecondaryLight, onSecondary = purpleOnSecondaryLight,
    secondaryContainer = purpleSecondaryContainerLight, onSecondaryContainer = purpleOnSecondaryContainerLight,
    tertiary = purpleTertiaryLight, onTertiary = purpleOnTertiaryLight,
    tertiaryContainer = purpleTertiaryContainerLight, onTertiaryContainer = purpleOnTertiaryContainerLight,
    background = purpleBackgroundLight, onBackground = purpleOnBackgroundLight,
    surface = purpleSurfaceLight, onSurface = purpleOnBackgroundLight,
    surfaceVariant = purpleSurfaceVariantLight, onSurfaceVariant = purpleOnBackgroundLight,
    surfaceContainer = purpleSurfaceContainerLight, surfaceContainerLow = purpleSurfaceContainerLowLight,
    surfaceContainerLowest = purpleSurfaceContainerLowLight, surfaceContainerHigh = purpleSurfaceContainerLight,
    surfaceContainerHighest = purpleSurfaceContainerLight, surfaceDim = purpleSurfaceContainerLight,
    surfaceBright = purpleBackgroundLight,
)
private val purpleDarkScheme = darkScheme.copy(
    primary = purplePrimaryDark, onPrimary = purpleOnPrimaryDark,
    primaryContainer = purplePrimaryContainerDark, onPrimaryContainer = purpleOnPrimaryContainerDark,
    secondary = purpleSecondaryDark, onSecondary = purpleOnSecondaryDark,
    secondaryContainer = purpleSecondaryContainerDark, onSecondaryContainer = purpleOnSecondaryContainerDark,
    tertiary = purpleTertiaryDark, onTertiary = purpleOnTertiaryDark,
    tertiaryContainer = purpleTertiaryContainerDark, onTertiaryContainer = purpleOnTertiaryContainerDark,
    background = purpleBackgroundDark, onBackground = purpleOnBackgroundDark,
    surface = purpleSurfaceDark, onSurface = purpleOnBackgroundDark,
    surfaceVariant = purpleSurfaceVariantDark, onSurfaceVariant = purpleOnBackgroundDark,
    surfaceContainer = purpleSurfaceContainerDark, surfaceContainerLow = purpleSurfaceContainerLowDark,
    surfaceContainerLowest = purpleSurfaceContainerLowDark, surfaceContainerHigh = purpleSurfaceContainerDark,
    surfaceContainerHighest = purpleSurfaceContainerDark, surfaceDim = purpleSurfaceContainerDark,
    surfaceBright = purpleSurfaceContainerLowDark,
)

private val orangeLightScheme = lightScheme.copy(
    primary = orangePrimaryLight, onPrimary = orangeOnPrimaryLight,
    primaryContainer = orangePrimaryContainerLight, onPrimaryContainer = orangeOnPrimaryContainerLight,
    secondary = orangeSecondaryLight, onSecondary = orangeOnSecondaryLight,
    secondaryContainer = orangeSecondaryContainerLight, onSecondaryContainer = orangeOnSecondaryContainerLight,
    tertiary = orangeTertiaryLight, onTertiary = orangeOnTertiaryLight,
    tertiaryContainer = orangeTertiaryContainerLight, onTertiaryContainer = orangeOnTertiaryContainerLight,
    background = orangeBackgroundLight, onBackground = orangeOnBackgroundLight,
    surface = orangeSurfaceLight, onSurface = orangeOnBackgroundLight,
    surfaceVariant = orangeSurfaceVariantLight, onSurfaceVariant = orangeOnBackgroundLight,
    surfaceContainer = orangeSurfaceContainerLight, surfaceContainerLow = orangeSurfaceContainerLowLight,
    surfaceContainerLowest = orangeSurfaceContainerLowLight, surfaceContainerHigh = orangeSurfaceContainerLight,
    surfaceContainerHighest = orangeSurfaceContainerLight, surfaceDim = orangeSurfaceContainerLight,
    surfaceBright = orangeBackgroundLight,
)
private val orangeDarkScheme = darkScheme.copy(
    primary = orangePrimaryDark, onPrimary = orangeOnPrimaryDark,
    primaryContainer = orangePrimaryContainerDark, onPrimaryContainer = orangeOnPrimaryContainerDark,
    secondary = orangeSecondaryDark, onSecondary = orangeOnSecondaryDark,
    secondaryContainer = orangeSecondaryContainerDark, onSecondaryContainer = orangeOnSecondaryContainerDark,
    tertiary = orangeTertiaryDark, onTertiary = orangeOnTertiaryDark,
    tertiaryContainer = orangeTertiaryContainerDark, onTertiaryContainer = orangeOnTertiaryContainerDark,
    background = orangeBackgroundDark, onBackground = orangeOnBackgroundDark,
    surface = orangeSurfaceDark, onSurface = orangeOnBackgroundDark,
    surfaceVariant = orangeSurfaceVariantDark, onSurfaceVariant = orangeOnBackgroundDark,
    surfaceContainer = orangeSurfaceContainerDark, surfaceContainerLow = orangeSurfaceContainerLowDark,
    surfaceContainerLowest = orangeSurfaceContainerLowDark, surfaceContainerHigh = orangeSurfaceContainerDark,
    surfaceContainerHighest = orangeSurfaceContainerDark, surfaceDim = orangeSurfaceContainerDark,
    surfaceBright = orangeSurfaceContainerLowDark,
)

// --- Seed 5: 红色 ---
private val redLightScheme = lightScheme.copy(
    primary = redPrimaryLight, onPrimary = redOnPrimaryLight,
    primaryContainer = redPrimaryContainerLight, onPrimaryContainer = redOnPrimaryContainerLight,
    secondary = redSecondaryLight, onSecondary = redOnSecondaryLight,
    secondaryContainer = redSecondaryContainerLight, onSecondaryContainer = redOnSecondaryContainerLight,
    tertiary = redTertiaryLight, onTertiary = redOnTertiaryLight,
    tertiaryContainer = redTertiaryContainerLight, onTertiaryContainer = redOnTertiaryContainerLight,
    background = redBackgroundLight, onBackground = redOnBackgroundLight,
    surface = redSurfaceLight, onSurface = redOnBackgroundLight,
    surfaceVariant = redSurfaceVariantLight, onSurfaceVariant = redOnBackgroundLight,
    surfaceContainer = redSurfaceContainerLight, surfaceContainerLow = redSurfaceContainerLowLight,
    surfaceContainerLowest = redSurfaceContainerLowLight, surfaceContainerHigh = redSurfaceContainerLight,
    surfaceContainerHighest = redSurfaceContainerLight, surfaceDim = redSurfaceContainerLight,
    surfaceBright = redBackgroundLight,
)
private val redDarkScheme = darkScheme.copy(
    primary = redPrimaryDark, onPrimary = redOnPrimaryDark,
    primaryContainer = redPrimaryContainerDark, onPrimaryContainer = redOnPrimaryContainerDark,
    secondary = redSecondaryDark, onSecondary = redOnSecondaryDark,
    secondaryContainer = redSecondaryContainerDark, onSecondaryContainer = redOnSecondaryContainerDark,
    tertiary = redTertiaryDark, onTertiary = redOnTertiaryDark,
    tertiaryContainer = redTertiaryContainerDark, onTertiaryContainer = redOnTertiaryContainerDark,
    background = redBackgroundDark, onBackground = redOnBackgroundDark,
    surface = redSurfaceDark, onSurface = redOnBackgroundDark,
    surfaceVariant = redSurfaceVariantDark, onSurfaceVariant = redOnBackgroundDark,
    surfaceContainer = redSurfaceContainerDark, surfaceContainerLow = redSurfaceContainerLowDark,
    surfaceContainerLowest = redSurfaceContainerLowDark, surfaceContainerHigh = redSurfaceContainerDark,
    surfaceContainerHighest = redSurfaceContainerDark, surfaceDim = redSurfaceContainerDark,
    surfaceBright = redSurfaceContainerLowDark,
)

// --- Seed 6: 粉色 ---
private val pinkLightScheme = lightScheme.copy(
    primary = pinkPrimaryLight, onPrimary = pinkOnPrimaryLight,
    primaryContainer = pinkPrimaryContainerLight, onPrimaryContainer = pinkOnPrimaryContainerLight,
    secondary = pinkSecondaryLight, onSecondary = pinkOnSecondaryLight,
    secondaryContainer = pinkSecondaryContainerLight, onSecondaryContainer = pinkOnSecondaryContainerLight,
    tertiary = pinkTertiaryLight, onTertiary = pinkOnTertiaryLight,
    tertiaryContainer = pinkTertiaryContainerLight, onTertiaryContainer = pinkOnTertiaryContainerLight,
    background = pinkBackgroundLight, onBackground = pinkOnBackgroundLight,
    surface = pinkSurfaceLight, onSurface = pinkOnBackgroundLight,
    surfaceVariant = pinkSurfaceVariantLight, onSurfaceVariant = pinkOnBackgroundLight,
    surfaceContainer = pinkSurfaceContainerLight, surfaceContainerLow = pinkSurfaceContainerLowLight,
    surfaceContainerLowest = pinkSurfaceContainerLowLight, surfaceContainerHigh = pinkSurfaceContainerLight,
    surfaceContainerHighest = pinkSurfaceContainerLight, surfaceDim = pinkSurfaceContainerLight,
    surfaceBright = pinkBackgroundLight,
)
private val pinkDarkScheme = darkScheme.copy(
    primary = pinkPrimaryDark, onPrimary = pinkOnPrimaryDark,
    primaryContainer = pinkPrimaryContainerDark, onPrimaryContainer = pinkOnPrimaryContainerDark,
    secondary = pinkSecondaryDark, onSecondary = pinkOnSecondaryDark,
    secondaryContainer = pinkSecondaryContainerDark, onSecondaryContainer = pinkOnSecondaryContainerDark,
    tertiary = pinkTertiaryDark, onTertiary = pinkOnTertiaryDark,
    tertiaryContainer = pinkTertiaryContainerDark, onTertiaryContainer = pinkOnTertiaryContainerDark,
    background = pinkBackgroundDark, onBackground = pinkOnBackgroundDark,
    surface = pinkSurfaceDark, onSurface = pinkOnBackgroundDark,
    surfaceVariant = pinkSurfaceVariantDark, onSurfaceVariant = pinkOnBackgroundDark,
    surfaceContainer = pinkSurfaceContainerDark, surfaceContainerLow = pinkSurfaceContainerLowDark,
    surfaceContainerLowest = pinkSurfaceContainerLowDark, surfaceContainerHigh = pinkSurfaceContainerDark,
    surfaceContainerHighest = pinkSurfaceContainerDark, surfaceDim = pinkSurfaceContainerDark,
    surfaceBright = pinkSurfaceContainerLowDark,
)

// --- Seed 7: 青色 ---
private val cyanLightScheme = lightScheme.copy(
    primary = cyanPrimaryLight, onPrimary = cyanOnPrimaryLight,
    primaryContainer = cyanPrimaryContainerLight, onPrimaryContainer = cyanOnPrimaryContainerLight,
    secondary = cyanSecondaryLight, onSecondary = cyanOnSecondaryLight,
    secondaryContainer = cyanSecondaryContainerLight, onSecondaryContainer = cyanOnSecondaryContainerLight,
    tertiary = cyanTertiaryLight, onTertiary = cyanOnTertiaryLight,
    tertiaryContainer = cyanTertiaryContainerLight, onTertiaryContainer = cyanOnTertiaryContainerLight,
    background = cyanBackgroundLight, onBackground = cyanOnBackgroundLight,
    surface = cyanSurfaceLight, onSurface = cyanOnBackgroundLight,
    surfaceVariant = cyanSurfaceVariantLight, onSurfaceVariant = cyanOnBackgroundLight,
    surfaceContainer = cyanSurfaceContainerLight, surfaceContainerLow = cyanSurfaceContainerLowLight,
    surfaceContainerLowest = cyanSurfaceContainerLowLight, surfaceContainerHigh = cyanSurfaceContainerLight,
    surfaceContainerHighest = cyanSurfaceContainerLight, surfaceDim = cyanSurfaceContainerLight,
    surfaceBright = cyanBackgroundLight,
)
private val cyanDarkScheme = darkScheme.copy(
    primary = cyanPrimaryDark, onPrimary = cyanOnPrimaryDark,
    primaryContainer = cyanPrimaryContainerDark, onPrimaryContainer = cyanOnPrimaryContainerDark,
    secondary = cyanSecondaryDark, onSecondary = cyanOnSecondaryDark,
    secondaryContainer = cyanSecondaryContainerDark, onSecondaryContainer = cyanOnSecondaryContainerDark,
    tertiary = cyanTertiaryDark, onTertiary = cyanOnTertiaryDark,
    tertiaryContainer = cyanTertiaryContainerDark, onTertiaryContainer = cyanOnTertiaryContainerDark,
    background = cyanBackgroundDark, onBackground = cyanOnBackgroundDark,
    surface = cyanSurfaceDark, onSurface = cyanOnBackgroundDark,
    surfaceVariant = cyanSurfaceVariantDark, onSurfaceVariant = cyanOnBackgroundDark,
    surfaceContainer = cyanSurfaceContainerDark, surfaceContainerLow = cyanSurfaceContainerLowDark,
    surfaceContainerLowest = cyanSurfaceContainerLowDark, surfaceContainerHigh = cyanSurfaceContainerDark,
    surfaceContainerHighest = cyanSurfaceContainerDark, surfaceDim = cyanSurfaceContainerDark,
    surfaceBright = cyanSurfaceContainerLowDark,
)

@Composable
fun NsTheme(
    darkTheme: Boolean = when (ThemePref.get(LocalContext.current)) {
        ThemePref.MODE_LIGHT -> false
        ThemePref.MODE_DARK -> true
        else -> isSystemInDarkTheme()
    },
    // Material You（Monet）动态取色：Android 12+ 且用户在设置中开启时，从系统壁纸提取主色生成配色；
    // 低版本或用户关闭时回退到用户自定义主题色（materialkolor 生成）+ PaletteStyle/ColorSpec。
    // 说明：dynamicLightColorScheme/dynamicDarkColorScheme 每次重组都会重新读取系统动态色，
    //      因此深浅切换 / 壁纸更换后无需手动刷新（重组即生效）。
    dynamicColor: Boolean = ThemePref.isMonet(LocalContext.current) && ThemePref.isDynamicColorSupported(),
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    // 读取主题版本号：ThemePref.apply() 递增此值 → NsTheme 重组 → Monet/Seed/AMOLED 切换即时生效
    val themeVersion = ThemePref.currentThemeVersion
    val amoled = ThemePref.isAmoled(context) && darkTheme
val baseScheme: ColorScheme = if (dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    } else {
        // 关闭 Monet：按用户选择的自定义主题主色（theme_seed）生成动态配色。
        // 集成 materialkolor：PaletteStyle + ColorSpec 决定配色风格（参照 KernelSU ThemeExt）。
        // 默认静态色板（lightScheme/darkScheme 及 8 个 Seed 色板）保留作为低版本兜底。
        val seedIndex = ThemePref.getSeed(context)
        val styleName = ThemePref.getPaletteStyle(context)
        val specName = ThemePref.getColorSpec(context)
        val paletteStyle = try {
            PaletteStyle.valueOf(styleName)
        } catch (_: Exception) {
            PaletteStyle.TonalSpot
        }
        val colorSpec = try {
            ColorSpec.SpecVersion.valueOf(specName)
        } catch (_: Exception) {
            ColorSpec.SpecVersion.SPEC_2021
        }
        // 用 materialkolor 从用户种子色动态生成完整配色（参照 KernelSU rememberKernelSUColorScheme）
        rememberDynamicColorScheme(
            seedColor = seedColorFor(seedIndex),
            isDark = darkTheme,
            isAmoled = amoled,
            style = paletteStyle,
            specVersion = colorSpec.effectiveFor(paletteStyle),
        )
    }

    // AMOLED 纯黑模式：将 background/surface 系列全替换为 Color.Black
    val colorScheme = if (amoled) baseScheme.copy(
        background = Color.Black,
        surface = Color.Black,
        surfaceDim = Color.Black,
        surfaceContainerLowest = Color.Black,
        surfaceContainerLow = Color.Black,
        surfaceContainer = Color.Black,
        surfaceContainerHigh = Color.Black,
        surfaceContainerHighest = Color.Black,
        surfaceBright = Color(0xFF111111),
    ) else baseScheme

    // 颜色直接生效，无动画延迟（用户要求实时切换）
    val animatedScheme = colorScheme

    // 把当前生效配色写入全局桥，供原生 View（蜂窝页 SIM TabLayout 等）跟随主题色
    ActiveThemeColors.primary = ActiveThemeColors.toArgb(animatedScheme.primary)
    ActiveThemeColors.onPrimary = ActiveThemeColors.toArgb(animatedScheme.onPrimary)
    ActiveThemeColors.primaryContainer = ActiveThemeColors.toArgb(animatedScheme.primaryContainer)
    ActiveThemeColors.onPrimaryContainer = ActiveThemeColors.toArgb(animatedScheme.onPrimaryContainer)
    ActiveThemeColors.secondaryContainer = ActiveThemeColors.toArgb(animatedScheme.secondaryContainer)
    ActiveThemeColors.onSecondaryContainer = ActiveThemeColors.toArgb(animatedScheme.onSecondaryContainer)
    ActiveThemeColors.surface = ActiveThemeColors.toArgb(animatedScheme.surface)
    ActiveThemeColors.surfaceContainer = ActiveThemeColors.toArgb(animatedScheme.surfaceContainer)
    ActiveThemeColors.surfaceContainerLow = ActiveThemeColors.toArgb(animatedScheme.surfaceContainerLow)
    ActiveThemeColors.surfaceContainerHigh = ActiveThemeColors.toArgb(animatedScheme.surfaceContainerHigh)
    ActiveThemeColors.onSurface = ActiveThemeColors.toArgb(animatedScheme.onSurface)
    ActiveThemeColors.onSurfaceVariant = ActiveThemeColors.toArgb(animatedScheme.onSurfaceVariant)
    ActiveThemeColors.background = ActiveThemeColors.toArgb(animatedScheme.background)

    // 系统栏（状态栏/导航栏）跟随当前主题 surface，保证全局生效
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? androidx.appcompat.app.AppCompatActivity)?.window
            window?.apply {
                val surfaceArgb = ActiveThemeColors.toArgb(animatedScheme.surface)
                statusBarColor = surfaceArgb
                navigationBarColor = surfaceArgb
            }
        }
    }

    // 构建 Miuix 配色：跟随当前 Material 配色（深浅色 + 用户主色 + AMOLED），
    // 让 Miuix 组件（TopAppBar/Card/Switch 等）的默认文字与背景色正确适配深色模式。
    // 关键槽位从 animatedScheme 覆盖，确保与 MaterialTheme 视觉一致。
    val miuixColors = if (darkTheme) {
        miuixDarkColorScheme().copy(
            primary = animatedScheme.primary,
            onPrimary = animatedScheme.onPrimary,
            primaryContainer = animatedScheme.primaryContainer,
            onPrimaryContainer = animatedScheme.onPrimaryContainer,
            secondary = animatedScheme.secondary,
            onSecondary = animatedScheme.onSecondary,
            secondaryContainer = animatedScheme.secondaryContainer,
            onSecondaryContainer = animatedScheme.onSecondaryContainer,
            background = animatedScheme.background,
            onBackground = animatedScheme.onBackground,
            surface = animatedScheme.surface,
            onSurface = animatedScheme.onSurface,
            surfaceVariant = animatedScheme.surfaceVariant,
            surfaceContainer = animatedScheme.surfaceContainer,
            surfaceContainerHigh = animatedScheme.surfaceContainerHigh,
            surfaceContainerHighest = animatedScheme.surfaceContainerHighest,
            outline = animatedScheme.outline,
        )
    } else {
        miuixLightColorScheme().copy(
            primary = animatedScheme.primary,
            onPrimary = animatedScheme.onPrimary,
            primaryContainer = animatedScheme.primaryContainer,
            onPrimaryContainer = animatedScheme.onPrimaryContainer,
            secondary = animatedScheme.secondary,
            onSecondary = animatedScheme.onSecondary,
            secondaryContainer = animatedScheme.secondaryContainer,
            onSecondaryContainer = animatedScheme.onSecondaryContainer,
            background = animatedScheme.background,
            onBackground = animatedScheme.onBackground,
            surface = animatedScheme.surface,
            onSurface = animatedScheme.onSurface,
            surfaceVariant = animatedScheme.surfaceVariant,
            surfaceContainer = animatedScheme.surfaceContainer,
            surfaceContainerHigh = animatedScheme.surfaceContainerHigh,
            surfaceContainerHighest = animatedScheme.surfaceContainerHighest,
            outline = animatedScheme.outline,
        )
    }

    MiuixTheme(colors = miuixColors) {
        MaterialTheme(
            colorScheme = animatedScheme,
            typography = AppTypography,
            content = content
        )
    }
}

// 颜色切换动画函数已移除（用户要求实时切换，无延迟）
// 保留函数签名以兼容潜在引用，但直接返回原 scheme
@Composable
private fun animateColorScheme(scheme: ColorScheme): ColorScheme = scheme

// ============================================================================
// materialkolor 辅助：Seed 色 → Color + SPEC_2025 兼容
// ============================================================================

/**
 * 把 ThemePref.SEED_* 索引映射为 materialkolor 需要的种子色（Color）。
 * 与 ColorPaletteScreen 的 8 色色板顺序保持一致：
 * 0=青蓝(默认) 1=蓝 2=绿 3=紫 4=橙 5=红 6=粉 7=青
 */
private fun seedColorFor(seedIndex: Int): Color = when (seedIndex) {
    ThemePref.SEED_BLUE -> Color(0xFF1565C0)
    ThemePref.SEED_GREEN -> Color(0xFF2E7D32)
    ThemePref.SEED_PURPLE -> Color(0xFF7B1FA2)
    ThemePref.SEED_ORANGE -> Color(0xFFE65100)
    ThemePref.SEED_RED -> Color(0xFFC62828)
    ThemePref.SEED_PINK -> Color(0xFFAD3259)
    ThemePref.SEED_CYAN -> Color(0xFF00696D)
    else -> Color(0xFF0F6681) // 默认青蓝色
}

/** 仅 TonalSpot/Neutral/Vibrant/Expressive 支持 SPEC_2025（与 KernelSU 一致）。 */
private val PaletteStyle.supportsSpec2025: Boolean
    get() = this == PaletteStyle.TonalSpot ||
        this == PaletteStyle.Neutral ||
        this == PaletteStyle.Vibrant ||
        this == PaletteStyle.Expressive

/** SPEC_2025 与不支持它的风格组合时自动回退到 SPEC_2021（KernelSU 一致）。 */
private fun ColorSpec.SpecVersion.effectiveFor(style: PaletteStyle): ColorSpec.SpecVersion =
    if (this == ColorSpec.SpecVersion.SPEC_2025 && !style.supportsSpec2025) {
        ColorSpec.SpecVersion.SPEC_2021
    } else {
        this
    }

