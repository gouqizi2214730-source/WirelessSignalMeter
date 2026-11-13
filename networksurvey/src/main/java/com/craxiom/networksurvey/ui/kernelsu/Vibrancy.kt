// Adapted from Kyant0/AndroidLiquidGlass — https://github.com/Kyant0/AndroidLiquidGlass (Apache 2.0).
// Mirrored from compose-miuix-ui example / KernelSU 3.2.5.

package com.craxiom.networksurvey.ui.kernelsu

import top.yukonga.miuix.kmp.blur.BackdropEffectScope
import top.yukonga.miuix.kmp.blur.colorControls

/**
 * 色彩增强效果（Vibrancy）
 *
 * 素材玻璃上叠一层对比度/饱和度增强，让背景颜色更加鲜艳通透。
 */
fun BackdropEffectScope.vibrancy() {
    colorControls(
        brightness = 0f,
        contrast = 1f,
        saturation = 1.5f,
    )
}
